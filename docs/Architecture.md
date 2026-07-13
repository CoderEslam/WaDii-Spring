# WaDii Backend — Architecture & Database

WaDii is a Spring Boot backend for a service-provider marketplace app (think: car repair / spare-parts services). Users post orders, providers respond with quotes, and there's chat + audio/video calling between the two sides, plus a location hierarchy, offers/ads, and a provider-onboarding review flow.

See also: [API-Reference.md](API-Reference.md) for the full endpoint list, and `Location-APIs.md` for a deep dive on the country/province/city endpoints.

## Tech stack

- **Framework**: Spring Boot 3.4.3, Java 17
- **Build**: Gradle (`build.gradle` — no `pom.xml`)
- **Persistence**: Spring Data JPA / Hibernate against **PostgreSQL** (`ddl-auto=update`, no Flyway/Liquibase — schema is auto-migrated on boot)
- **Security**: Spring Security, stateless JWT bearer auth (`io.jsonwebtoken` / jjwt 0.11.5)
- **Realtime**: two parallel WebSocket stacks — raw `WebSocketHandler` and STOMP/SockJS
- **Calling**: Agora RTC (token minting) and LiveKit (self-signed JWT, no official SDK)
- **Push notifications**: Firebase Cloud Messaging (FCM v1 HTTP API)
- **Kotlin**: used for exactly one file (`utils/ResponseType.kt`) alongside the mostly-Java codebase
- **Deploy**: multi-stage `Dockerfile` (`gradle:8-jdk17` build → `eclipse-temurin:17-jre` runtime)

Dead/unused dependencies present in `build.gradle` worth knowing about so you don't go looking for code that uses them: MySQL driver, JetBrains Exposed ORM, `simple-java-mail` (SMTP is configured in properties but no `JavaMailSender` code exists anywhere), ModelMapper (bean is registered but nothing calls `.map()`).

## High-level request flow

```
Client (mobile/web)
   │  Authorization: Bearer <jwt>
   ▼
JwtFilter (OncePerRequestFilter)
   │  validates token, loads UserDetails, sets Authentication
   ▼
Controller (e.g. OrderController)
   │  manual role/ownership checks against authentication.getName() / getCredentials()
   ▼
Service layer (thin — mostly Auth/Agora/LiveKit/Notification services)
   │
   ▼
Repository (Spring Data JPA) ──▶ PostgreSQL
```

Most business logic lives directly in controllers rather than a service layer — there is no service class per entity. The generic base `ts/Controller<Entity, DTO, ID>` supplies the five standard CRUD routes (`show/{id}`, `insert`, `update`, `delete/{id}`, `show-all`); concrete controllers override these and add feature-specific endpoints.

## Response envelope

Nearly all endpoints return `com.doubleclick.wadii.utils.Response<T>`:

```json
{ "data": <payload or null>, "message": "...", "statusCode": 200, "timestamp": "..." }
```

`statusCode` mirrors `ResponseType` (`SUCCESS=200, ERROR=400, UNAUTHORIZED=401, FORBIDDEN=403, NOT_FOUND=404, INTERNAL_SERVER_ERROR=500`). **Many rejection paths return HTTP 200 with `data: null`** rather than a matching HTTP status — this is a real inconsistency in the codebase, not a documentation simplification. There is no global `@ControllerAdvice`, so unhandled exceptions fall through to Spring Boot's default error page/JSON.

## Security & auth

- **Mechanism**: stateless JWT bearer tokens, no server-side sessions (`SessionCreationPolicy.STATELESS`).
- **`SecurityConfig`** (`auth/config/SecurityConfig.java`): defines the `SecurityFilterChain`, wide-open CORS (`*` origins, all methods/headers, credentials allowed), CSRF disabled, `BCryptPasswordEncoder`. Public matchers: `/auth/**`, `/ws/**`, `/ws-native/**`, `/web-socket/**`, `/users/{filename:.+}`, and (buggy — missing leading slash, likely non-matching) `countries/**`, `provinces/**`, `cities/**`.
- **`JwtFilter`** (`auth/config/JwtFilter.java`): a second, independently maintained skip-list (paths starting with `/auth`, `/ws`, `/web-socket`, `/countries`, `/provinces`, `/cities`, plus a regex for image filenames under `/users/`). For everything else it requires `Authorization: Bearer <jwt>`, validates it, and sets a `UsernamePasswordAuthenticationToken` with the **numeric user id stored in the `credentials` field** — several controllers (`OffersController`, `SearchController`) read that back via `authentication.getCredentials()` instead of doing a repository lookup.
- **`JwtUtil`**: HS256, subject = email, jti = user id, expiration currently ~21.6 days (`security.jwt.expiration-time=1866240000` ms — the properties comment says "24 hours", which is stale/wrong). Signing key from env `JWT_SECRET_KEY`; the app **fails to start** if this isn't set (see `error_jwt.md` at repo root for the exact failure mode).
- **Roles are not wired into Spring Security's authority system at all.** `CustomUserDetailsService` builds a `UserDetails` with zero `GrantedAuthority`s. There is no `@PreAuthorize`/`@Secured` anywhere in the codebase. **Every role/ownership check (USER/PROVIDER/ADMIN) is hand-written inside controller methods**, typically:
  ```java
  Optional<User> user = userRepository.findByEmail(authentication.getName());
  if (user.get().getRole() != Role.ADMIN) { return Response.response(null, "...", ResponseType.SUCCESS); }
  ```
  This is why authorization is inconsistent across controllers — see the "Known issues" list in [API-Reference.md](API-Reference.md#known-issues--rough-edges-worth-knowing-before-integrating) for the specific endpoints that are missing checks entirely.
- **`Role` enum**: `USER(0), PROVIDER(1), ADMIN(2)`.
- **WebSocket auth**: `JwtHandshakeInterceptor` validates a JWT passed as `?token=` query param on the handshake (both the raw `/web-socket/{userId}` endpoint and the STOMP `/ws` endpoint), and cross-checks the path's `userId` against the token's user id when present.

## Realtime / WebSockets

Two parallel implementations coexist — worth knowing about if you're debugging chat/calls, since logic (message persistence, ChatContact upserts) is duplicated between them:

1. **Raw WebSocket** (`configuration/RawWebSocketConfig` → `websocket/ChatWebSocketHandler`), endpoint `ws://host/web-socket/{userId}?token=<jwt>`. This is the primary channel for the mobile client, per the design notes in `agora.md`/`livekit-backend.md`. It handles both chat messages and call-signaling events (`CALL_INVITE`/`CALL_ACCEPT`/`CALL_REJECT`/`CALL_END`) over the same socket, tracks presence, and falls back to an FCM push for `CALL_INVITE` if the callee has no open socket.
2. **STOMP over SockJS** (`configuration/WebSocketConfig`, endpoint `/ws`, broker prefixes `/queue`,`/topic`, app prefix `/app`), handled by `websocket/ChatWebSocketController` (`/app/chat.send`, `/app/chat.typing`, `/app/chat.read`) plus `websocket/PresenceEventListener`. This looks like a web-client-oriented or earlier implementation of the same chat feature.

There are also **two independent presence trackers**: `websocket/PresenceRegistry` (used by the raw handler + `GET /presence`) and `websocket/UserPresenceTracker` (driven by STOMP events, used by `GET /users/{id}/online`) — they can disagree about a user's online status depending on which socket stack the client used.

A manual test page exists at `src/main/resources/static/ws-test.html`.

## Calling (Agora + LiveKit)

Two calling backends exist side by side:

- **Agora** (`service/AgoraTokenService`, `controller/AgoraController`): mints RTC tokens via `RtcTokenBuilder2`, 100-hour TTL, backed by `agora.app-id`/`agora.app-certificate`. `POST /agora/token` validates the caller's uid and channel membership; `POST /agora/reject` relays a reject signal.
- **LiveKit** (`service/LiveKitTokenService`, `controller/LiveKitController`): self-signs HS256 JWTs directly (no official LiveKit SDK dependency), 10-minute TTL, `video` grant claim (`roomJoin, room, canPublish, canSubscribe, canPublishData`). `POST /livekit/token` does the same identity/room-membership validation as Agora.

Both services fail fast at startup (`@PostConstruct`) if their respective env vars are missing. The `Call`/`CallType`/`CallStatus` entities and `CallRepository` exist in the schema but are **currently unused** — no controller persists call records; call state lives only transiently in the WebSocket signaling layer.

## Push notifications (FCM)

`notification/NotificationService` sends pushes via the FCM v1 HTTP API, authenticating with a Google service-account credential expected at the hardcoded path `src/main/wadii.json` (project id `wadii-kmp`; not committed to the repo — must be provided out-of-band). Used for: new messages, provider-request accept/reject, response accept/cancel, and as a call-signaling fallback when the target has no open WebSocket.

## Database

**PostgreSQL**, schema managed by Hibernate `ddl-auto=update` (no migration tool). Datasource config comes entirely from environment variables (see [Configuration](#configuration--environment) below).

### Domain model overview

```
User ──1:1── Provider ──1:N── Branch ──1:N── WorkTime
  │              │
  │              ├──1:N── Links
  │              ├──1:N── Offer ──N:M── Service
  │              ├──1:N── Rate
  │              ├──1:N── Follower ──N:1── User
  │              └──1:N── Responses ──1:N── SparePartsPrice
  │
  ├──1:N── Order ──1:N── SpareParts
  │           ├──N:M── Service
  │           ├──1:N── Responses
  │           └──1:N── OrderCancel ──N:1── Reason
  │
  ├──1:N── SavedOffer ──N:1── Offer
  ├──1:N── Message ──N:1── User (fromUser/toUser)
  ├──1:N── ChatContact
  ├──1:N── UserNotification
  └──N:1── City ──N:1── Province ──N:1── Country
```

### Entities

All under `entities/` unless noted; `User` is under `auth/model/`. Primary keys are `Long id` with `GenerationType.IDENTITY` unless noted.

| Entity | Table | Key fields | Relationships |
|---|---|---|---|
| **User** | `users` | firstName, lastName, email (unique, not null), password, token, fcmToken, image, backgroundImage, phone, role | 1:N rates, 1:N following (Follower), 1:1 provider, 1:N orders, N:1 city |
| **Provider** | `providers` | rate (Double), followersCount (Long), name | 1:N branches, N:M services, 1:N rates, 1:N links, 1:N offers, 1:N followers, 1:1 user, 1:N responses |
| **Branch** | `branches` | name, address | N:1 provider, 1:N workTimes (cascade ALL, orphanRemoval) |
| **WorkTime** | `work_times` | startTime, closeTime, day | N:1 branch |
| **Links** | `links` | link | N:1 provider |
| **Offer** | `offers` | title, description, endDate | N:1 provider, N:M services (join table `offer_services`), transient `saved` (not persisted, computed per-request) |
| **SavedOffer** | `saved_offers` | — | N:1 user, N:1 offer |
| **Service** | `services` | name (unique) | N:M providers (`service_providers`), N:M orders (`services_orders`) |
| **Order** | `orders` | carModelYear, comment, date, latitude, longitude, status (default PENDING) | N:1 user, N:M services, 1:N spareParts (cascade ALL, orphanRemoval), 1:N responses (cascade ALL, orphanRemoval) |
| **OrderStatus** (enum) | — | `PENDING, CANCELED` | |
| **OrderCancel** | `order_cancels` | canceledAt (default now) | N:1 order, N:1 reason |
| **Reason** | `reasons` | reason | |
| **SpareParts** | `spare_parts` | sparePartName | N:1 order |
| **SparePartsPrice** | `spare_parts_price` | price | N:1 sparePart, N:1 response |
| **Responses** | `responses` | comment, latitude, longitude, responsesState | N:1 provider, N:1 order (`@JsonIgnore`), 1:N sparePartsPrices (cascade ALL, orphanRemoval) |
| **ResponsesState** (enum) | — | `PENDING, ACCEPT, CANCEL` | |
| **Rate** | `rates` | comment, rate (double) | N:1 provider, N:1 user |
| **Follower** | `followers` | composite key `FollowerId{userId, providerId}` | N:1 user, N:1 provider |
| **Role** (enum) | — | `USER(0), PROVIDER(1), ADMIN(2)` | |
| **Advertisement** | `advertisements` | title, description, imageUrl, targetUrl, advertiserName, status, priority, impressions, clicks, startDate/endDate/createdAt/updatedAt | `@PrePersist`/`@PreUpdate` manage timestamps + defaults |
| **AdvertisementStatus** (enum) | — | `ACTIVE, INACTIVE, EXPIRED` | |
| **Country** | `countries` | name (unique) | |
| **Province** | `provinces` | name (unique) | N:1 country |
| **City** | `cities` | name (unique) | N:1 province |
| **CarType** | `car_types` | name (unique) | (not currently referenced by Order — no FK link found) |
| **CommonSpareParts** | `common_spare_parts` | name | |
| **ProviderRequest** | `provider_requests` | name, frontIdImage, backIdImage, taxCardFront, taxCardBack, address, phoneNumber, requestedAt | N:M services, `@ElementCollection` links, N:1 user |
| **Message** | `messages` | text, type, createdAt, isRead (column `is_read`) | N:1 fromUser, N:1 toUser |
| **ChatContact** | `chat_contacts` | lastMessageAt, lastMessage, messageType — unique `(user_id, contact_id)` | N:1 user, N:1 contact |
| **UserNotification** | `user_notifications` | title, body, type, isRead, createdAt | N:1 user |
| **Call** | `calls` | callType, status (default RINGING), roomName, startedAt, endedAt, durationSeconds | N:1 caller, N:1 callee — **defined but unused**, no controller persists rows here |
| **CallType** (enum) | — | `AUDIO, VIDEO` | |
| **CallStatus** (enum) | — | `RINGING, ACCEPTED, REJECTED, MISSED, ENDED, CANCELLED` | |

### Notable data-model quirks

- **`Order.status`** only distinguishes `PENDING`/`CANCELED` — there's no explicit "completed"/"in progress" state; fulfillment is implicitly tracked through `Responses`/`ResponsesState` instead.
- **`Offer.saved`** is `@Transient` — it's populated at request time per authenticated user (via `SavedOfferRepository`) and never stored on the row itself.
- **`CarType`** exists as a lookup table but nothing currently links `Order` (or anything else) to it by foreign key — likely wired up client-side only, or a planned-but-unfinished feature.
- **`Follower`** uses a composite embedded key (`FollowerId`) rather than a surrogate id — enforces one follow relationship per (user, provider) pair at the DB level. A second, unused `Follow`/`FollowRepository` pair also exists in the codebase (not the one actually wired up — `FollowersRepository` is what `ProviderController` uses).

## Configuration & environment

Single config file: `src/main/resources/application.properties` (no per-environment profiles). Key settings:

- `server.port=8080`
- `spring.datasource.*` — PostgreSQL, driven entirely by env vars (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`)
- `spring.jpa.hibernate.ddl-auto=update` — schema auto-migrates on every boot, no Flyway/Liquibase
- `spring.jpa.show-sql=true` + `format_sql=true` — verbose SQL logging is on (consider disabling in production for noise/perf)
- `spring.servlet.multipart.max-file-size/max-request-size=5MB`
- `security.jwt.secret-key` / `security.jwt.expiration-time` — see [Security](#security--auth)
- `agora.app-id` / `agora.app-certificate`, `livekit.api-key` / `livekit.api-secret` / `livekit.url`
- `spring.mail.*` — SMTP config present but **no mail-sending code exists** in the app
- `spring.config.import=optional:file:.env[.properties]` — loads a root-level `.env` file (gitignored) as additional properties; that's where all the above secrets actually come from locally

Required environment variables: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET_KEY`, `SUPPORT_EMAIL`, `APP_PASSWORD`, `AGORA_APP_ID`, `AGORA_APP_CERTIFICATE`, `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`.

**File uploads**: written to `uploads/` relative to the process working directory (`System.getProperty("user.dir") + "/uploads"`), served back through `GET /users/{filename}`. This directory is gitignored/dockerignored — on a fresh deploy or container restart without a persistent volume, previously uploaded files are lost.

## Known gaps / things to fix eventually

- No global exception handler (`@ControllerAdvice`) — unhandled exceptions surface as default Spring Boot error responses instead of the app's `Response<T>` envelope.
- No Bean Validation (`@Valid`/`@NotNull` etc.) on DTOs — validation is ad hoc, per-field, inside controller methods (`isNotEmpty()` helpers).
- Role enforcement is 100% manual per-controller rather than declarative — easy to add a new endpoint and forget the check (see the gaps listed in [API-Reference.md](API-Reference.md#known-issues--rough-edges-worth-knowing-before-integrating)).
- Duplicated chat/presence logic across the raw-WebSocket and STOMP stacks.
- `ddl-auto=update` with no migration history makes schema changes hard to audit or roll back — worth moving to Flyway/Liquibase before this grows further.
