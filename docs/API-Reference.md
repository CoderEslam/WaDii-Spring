# WaDii API Reference

Base URL: `http://<host>:8080` — **note**: `api.prefix=/api/v1` is defined in `application.properties` but is not actually wired into any router/context-path. All routes below are mounted at the root as listed (e.g. `/users`, not `/api/v1/users`).

## Conventions

- **Envelope**: almost every response body is wrapped as:
  ```json
  { "data": <payload>, "message": "string", "statusCode": 200, "timestamp": "..." }
  ```
  (`com.doubleclick.wadii.utils.Response<T>`). `statusCode` values: `SUCCESS=200, ERROR=400, UNAUTHORIZED=401, FORBIDDEN=403, NOT_FOUND=404, INTERNAL_SERVER_ERROR=500`.
- **⚠️ Important gotcha**: many manual authorization/ownership checks in controllers return **HTTP 200** with `data: null` and a rejection message in `message`, instead of a 401/403 HTTP status. Clients must inspect the envelope body, not just the HTTP status code, to detect rejections. Endpoints that *do* return a real 403 are called out explicitly below.
- **Auth header**: `Authorization: Bearer <jwt>` on every endpoint except those listed under [Public endpoints](#public--unauthenticated-endpoints).
- **Auth model**: role/ownership checks (`USER` / `PROVIDER` / `ADMIN`) are done **manually inside controller methods**, not via `@PreAuthorize`. There is no Spring Security authority mapping — see [Architecture doc](Architecture.md#security--auth) for details.
- **Standard CRUD shape**: most controllers extend a generic base (`ts/Controller<Entity, DTO, ID>`) exposing five routes at a given base path: `GET /show/{id}`, `POST /insert`, `POST /update`, `DELETE /delete/{id}`, `GET /show-all`. These are listed once per controller below rather than re-explained.

## Public / unauthenticated endpoints

- `POST /auth/register`, `POST /auth/login`
- Everything under `/ws/**`, `/ws-native/**`, `/web-socket/**` (WebSocket handshakes, auth via `?token=` query param instead of header)
- `GET /users/{filename:.+}` (serving uploaded images)
- `countries/**`, `provinces/**`, `cities/**` matchers exist in `SecurityConfig` but are missing a leading slash, so they likely do **not** match real request paths (`/countries/...` etc.) — see `Location-APIs.md`. In practice, `Country`/`Province`/`City` GET endpoints currently require a valid JWT like everything else, despite the intent to make them public.

---

## Auth — `/auth`

| Verb | Path | Body | Description |
|---|---|---|---|
| POST | `/auth/register` | `AuthRequest` | Register a new user or provider. `userType`: `0`=USER, `1`=PROVIDER, `2`=ADMIN. Creates a `Provider` row too if `userType=1`. Returns `Response<User>` with a JWT in `user.token`. |
| POST | `/auth/login` | `AuthRequest` | Authenticate by email/password. Returns `Response<User>` with a fresh JWT; for PROVIDER users, the linked `Provider` is embedded in the response. |

`AuthRequest` fields: `firstName, lastName, email, password, phone, fcmToken, cityId, providerName, userType`.

---

## Advertisements — `/advertisements`

Standard CRUD (`insert`/`update`/`delete` require **ADMIN**).

| Verb | Path | Description |
|---|---|---|
| GET | `/advertisements/show/{id}` | Get by id |
| POST | `/advertisements/insert` | Create (ADMIN) |
| POST | `/advertisements/update` | Partial update, non-null fields only (ADMIN) |
| DELETE | `/advertisements/delete/{id}` | Delete (ADMIN) |
| GET | `/advertisements/show-all` | All, ordered by priority desc |
| GET | `/advertisements/by-status/{status}` | Filter by `AdvertisementStatus` (`ACTIVE`/`INACTIVE`/`EXPIRED`) |
| GET | `/advertisements/active` | Ads currently active (`startDate<=now<=endDate` and status ACTIVE) |
| GET | `/advertisements/by-advertiser/{advertiserName}` | Filter by advertiser name |
| POST | `/advertisements/track-impression/{id}` | Increment impression counter |
| POST | `/advertisements/track-click/{id}` | Increment click counter |

---

## Agora calling — `/agora`

| Verb | Path | Body | Description |
|---|---|---|---|
| POST | `/agora/token` | `AgoraTokenRequest{channelName, uid}` | Mints an Agora RTC token. Validates `uid` matches the authenticated user id and that the user belongs to `channelName` (parsed as `"a_b"`); returns **403** otherwise. |
| POST | `/agora/reject` | `CallSignal` | Relays a `CALL_REJECT` signal to the other party (via WebSocket, FCM fallback); `fromUserId` is overwritten server-side with the authenticated caller. |

---

## LiveKit calling — `/livekit`

| Verb | Path | Body | Description |
|---|---|---|---|
| POST | `/livekit/token` | `LiveKitTokenRequest{roomName, identity, participantName}` | Mints a self-signed LiveKit access JWT (10-min TTL). Validates `identity` matches the authenticated user id and channel membership (parsed `roomName` as `"a_b"`); returns **403** otherwise. Returns `LiveKitTokenResponse{url, token}`. |

---

## Branches — `/branches`

Standard CRUD (`insert`/`update`/`delete` require **PROVIDER**).

| Verb | Path | Description |
|---|---|---|
| GET | `/branches/by-provider/{providerId}` | List branches for a provider |

---

## Car types — `/car-types`

Standard CRUD only (`insert`/`update`/`delete` require **ADMIN**).

---

## Cities — `/cities`

Standard CRUD (`insert`/`update`/`delete` require **ADMIN**; validates name uniqueness and province existence).

| Verb | Path | Description |
|---|---|---|
| GET | `/cities/by-province/{provinceId}` | Cities within a province |

---

## Common spare parts — `/common-spare-parts`

Standard CRUD only (`insert`/`update`/`delete` require **ADMIN**).

---

## Countries — `/countries`

Standard CRUD only (`insert`/`update`/`delete` require **ADMIN**; validates name uniqueness).

---

## Links — `/links`

Standard CRUD (`insert`/`update`/`delete` require **PROVIDER**; validates provider existence and link uniqueness).

---

## Messages — `/messages`

Standard CRUD, with ownership caveats:
- `insert`: sends a message, upserts `ChatContact` for both directions, pushes over STOMP (`/queue/messages`) to both users, sends an FCM push if the recipient has an `fcmToken`.
- `update` / `delete`: only the message's sender may modify/delete (violation → HTTP 200 + `data: null`, not 403).

| Verb | Path | Description |
|---|---|---|
| GET | `/messages/sent` | Messages sent by the current user |
| GET | `/messages/received` | Messages received by the current user |
| GET | `/messages/conversation/{userId}?page=&size=` | Paginated conversation between current user and `userId` |
| GET | `/messages/chat-list` | `ChatContact` list, ordered by last message time |

---

## Offers — `/offers`

Standard CRUD (`insert`/`update`/`delete` require **PROVIDER**). List responses inject a transient `saved: boolean` flag per offer based on the current user's `SavedOffer`s.

| Verb | Path | Description |
|---|---|---|
| GET | `/offers/filter-by-service/{serviceId}` | Offers matching a service |

---

## Orders — `/orders`

Standard CRUD, with caveats:
- `insert`: creates the order plus nested `SpareParts` rows and links `Service`s. **No auth/ownership check** — the owning user is resolved purely from `orderDto.getUserId()` in the request body.
- `update`: replaces services/spare-parts if provided.
- `delete`: only the order's owning user may delete.

| Verb | Path | Description |
|---|---|---|
| GET | `/orders/show-all-order-of-user` | Orders belonging to the current user |
| GET | `/orders/show-all-order-of-provider` | Orders relevant to the current user's provider profile |
| POST | `/orders/cancel` | Body `OrderCancelRequest{orderId, reasonId}`; sets status `CANCELED`, records an `OrderCancel`. Only the owning user may cancel — returns real **403** if not. |

---

## Presence — `/presence`

| Verb | Path | Description |
|---|---|---|
| GET | `/presence?userIds=1,2,3` | `Map<Long,Boolean>` online status per user id |
| GET | `/presence/online` | `Set<Long>` of all currently online user ids |

---

## Providers — `/providers`

Standard CRUD, with caveats: `update` is unimplemented (always returns `null`); `insert` (links a `Provider` to an existing `User`) and `delete` have **no role check**.

| Verb | Path | Description |
|---|---|---|
| GET | `/providers/me` | Current user's provider profile |
| GET | `/providers/filter-by-service/{serviceId}` | Providers offering a given service |
| POST | `/providers/update-services` | Body `ServicesProviderDto{providerId, serviceIds}` — replaces provider↔service links |
| POST | `/providers/follow-provider/{id}` | Follow a provider (creates `Follower`, increments `followersCount`) |
| DELETE | `/providers/unfollow-provider/{id}` | Unfollow a provider |
| POST | `/providers/update-all/{id}` | Body `UpdateProviderDto` — bulk update: user basic info, services, branches+worktimes (upsert), links (upsert), offers (upsert), all in one call |
| GET | `/providers/{id}/followers` | List followers of a provider |

---

## Provider requests — `/provider-requests`

"Become a provider" onboarding flow with KYC file uploads.

| Verb | Path | Description |
|---|---|---|
| POST | `/provider-requests/request` (multipart) | Body `ProviderRequestDto` (name, userId, 4× `MultipartFile` id/tax images, address, phoneNumber, serviceIds, links). Saves files to `uploads/`. Rejects if a request already exists for the user or the user is already a provider. |
| GET | `/provider-requests/show-all` | All pending requests |
| POST | `/provider-requests/accept/{id}` | **ADMIN only.** Converts the request into a new `Provider`, promotes the user's role to `PROVIDER`, sends an FCM "accepted" notification, deletes the request. |
| POST | `/provider-requests/reject/{id}?reason=` | **ADMIN only.** Deletes the request, sends an FCM "rejected" notification with optional reason. |
| POST | `/provider-requests/put-it-user/{userId}` | Reverts a PROVIDER user's role back to USER. **No auth check.** |
| POST | `/provider-requests/put-it-provider/{userId}` | Reverts a USER back to PROVIDER if a `Provider` row already exists for them. **No auth check.** |

---

## Provinces — `/provinces`

Standard CRUD. **No role/auth checks at all** on insert/update/delete (unlike `Country`/`City`, this is a known gap — see `Location-APIs.md`).

| Verb | Path | Description |
|---|---|---|
| GET | `/provinces/by-country/{countryId}` | Provinces within a country |

---

## Rates — `/rate`

Standard CRUD, with caveats:
- `insert`: upserts a `Rate` for `(userId, providerId)`, recomputes and saves the provider's average rate.
- `update` / `delete`: only the rate's owning user may modify.

---

## Reasons — `/reasons`

Standard CRUD only. No role checks. (Cancellation reasons, used by `Order` cancellation.)

---

## Responses (provider quotes) — `/responses`

Note: entity class is `Responses` (plural) to avoid a naming clash with `utils.Response`.

Standard CRUD, with caveats:
- `insert` / `update`: require **PROVIDER**; creates/updates a quote against an `Order`, with nested `SparePartsPrice` line items.
- `delete`: only the owning provider's user may delete.

| Verb | Path | Description |
|---|---|---|
| GET | `/responses/get-all-response-of-user` | Responses to the current user's orders |
| POST | `/responses/accept-response` | Marks a response `ACCEPT`, notifies the provider (FCM + in-app), deletes sibling responses on the same order |
| POST | `/responses/cancel-response` | Deletes the response, notifies the provider (FCM + in-app) that it was rejected |

---

## Saved offers — `/saved-offers`

Standard CRUD, `update` unsupported (returns ERROR).

| Verb | Path | Description |
|---|---|---|
| GET | `/saved-offers/my-saved-offers` | Current user's saved offers |
| DELETE | `/saved-offers/remove/{offerId}` | Unsave by offer id |

---

## Search — `/search`

| Verb | Path | Description |
|---|---|---|
| GET | `/search?q=` | LIKE-based search across Offers (title/description), Services (name), Providers (name), Branches (name/address). Returns `SearchResultDto{offers, services, providers, branches}`; offers are flagged `saved` for the current user. |

---

## Services — `/services`

Standard CRUD only (`insert`/`update`/`delete` require **ADMIN**).

---

## Spare parts — `/spare-parts`

Standard CRUD only. No role checks.

---

## Users — `/users`

Standard CRUD, with caveats:
- `insert`: caller must be **ADMIN**; sets role from `userType` (0/1/2), immediately generates and persists a JWT on the new user.
- `update`: **no auth/ownership check** — target resolved purely from `userDto.getId()` in the body; updates firstName/lastName/fcmToken/phone (city update is currently commented out).
- `delete`: **no auth/ownership check**.

| Verb | Path | Description |
|---|---|---|
| GET | `/users/me` | Current user's profile |
| POST | `/users/upload-image` (multipart `file`) | Uploads and sets profile image |
| POST | `/users/upload-background-image` (multipart `file`) | Uploads and sets background image |
| GET | `/users/{id}/online` | Presence check for a user |
| GET | `/users/me/unread-count` | Unread message count for the current user |
| GET | `/users/{filename:.+}` | Serves an uploaded file — **public, no auth required** |

---

## Notifications — `/notifications`

Standard CRUD. No role/ownership checks on the base CRUD routes.

| Verb | Path | Description |
|---|---|---|
| GET | `/notifications/my` | Current user's notifications |
| GET | `/notifications/my/unread` | Current user's unread notifications |
| GET | `/notifications/my/unread-count` | Unread count |
| POST | `/notifications/mark-read/{id}` | Mark one as read |
| POST | `/notifications/mark-all-read` | Mark all as read |
| POST | `/notifications/test-send/{userId}` | Sends a dummy FCM push. **Debug endpoint, no auth check** — should be removed or locked down before production. |

---

## Work times — `/work-time`

Standard CRUD (`insert`/`update`/`delete` require **PROVIDER**).

| Verb | Path | Description |
|---|---|---|
| POST | `/work-time/get-all-work-time/{branchId}` | List work times for a branch (note: a read implemented as `POST`) |

---

## WebSocket endpoints

Not REST, but part of the API surface — see [Architecture doc](Architecture.md#realtime--websockets) for full detail.

| Protocol | Path | Auth |
|---|---|---|
| Raw WebSocket | `ws://host/web-socket/{userId}?token=<jwt>` | JWT in query param, validated by `JwtHandshakeInterceptor` |
| STOMP over SockJS | `ws://host/ws` (app prefix `/app`, broker `/queue`,`/topic`) | JWT in query param |

---

## Known issues / rough edges worth knowing before integrating

1. **Inconsistent error signaling**: rejected/forbidden operations frequently return HTTP 200 with `data: null` rather than a 4xx status — always check `message`/`data`, not just status code.
2. **`countries/provinces/cities` public matchers likely don't match** (missing leading slash) — treat those endpoints as requiring auth.
3. Several endpoints have **no ownership or role check** despite looking like they should (`Order.insert`, `Provider.insert`/`delete`, `User.update`/`delete`, `provider-requests/put-it-user`, `provider-requests/put-it-provider`, `notifications/test-send`). Don't rely on server-side authorization for these until fixed.
4. `api.prefix=/api/v1` in config is **not applied** — all routes are at the root path as documented above.
