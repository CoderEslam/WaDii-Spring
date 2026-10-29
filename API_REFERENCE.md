# WaDii API Reference

Generated from the implementation on branch `feature/marketplace-rearchitecture` (controllers, DTO records,
`SecurityConfig`, `@PreAuthorize` rules, services, scheduled jobs). Domain model: `docs/DOMAIN_MODEL.md`.

- Base URL: `http://<host>:8080`. New API under **`/api/v1`**.
- Kept at the root for existing clients: `/auth/register`, `/auth/login` (aliases of `/api/v1/auth/*`),
  `/users/{file}` (alias of public files), and the **deprecated** legacy endpoints of section 22.
- All timestamps are ISO-8601 UTC instants (`2026-10-29T10:00:00Z`). Dates (`requestedDate`) are `YYYY-MM-DD`.
  Working hours are local times in the platform zone `Africa/Cairo`.
- Money is a decimal with 2 places (`"amount": 300.00`), currency `EGP`. Never floating point on the server.

---

## 1. Conventions

### 1.1 Response envelope

Every JSON response (success and error) uses the same envelope. Null fields are omitted.

```json
{ "success": true, "message": "Order created", "data": { }, "statusCode": 201, "timestamp": "2026-10-29T10:00:00Z" }
```

```json
{ "success": false, "code": "OFFER_EXPIRED", "message": "This offer has expired.", "statusCode": 409,
  "timestamp": "2026-10-29T10:00:00Z", "details": { "field": "reason" } }
```

`statusCode` mirrors the HTTP status. `details` appears for `VALIDATION_FAILED` (field -> message).
File downloads return the raw file (not the envelope).

### 1.2 HTTP status codes

| Status | Used for |
|---|---|
| 200 | Success |
| 201 | Resource created |
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, file type/content errors |
| 401 | Missing, invalid, expired or revoked token; wrong credentials |
| 403 | Authenticated but not allowed (role, admin permission, ownership) |
| 404 | Resource not found / unknown route |
| 405 | Method not allowed |
| 409 | Conflicts with the current state (wrong status, duplicates, already accepted, expired) |
| 413 | Upload too large |
| 422 | Business rule refused (offer expiry window, provider not eligible, policy violation, outside working hours) |
| 500 | Unexpected server error (`INTERNAL_ERROR`, no internals leaked) |

Authorization failures are never HTTP 200. Every 403 is written to the audit log (`AUTHORIZATION_FAILURE`).

### 1.3 Error codes

| Code | HTTP | Meaning |
|---|---|---|
| VALIDATION_FAILED | 400 | Invalid input (`details` lists fields) |
| MALFORMED_REQUEST | 400 | Unreadable body / wrong parameter type / missing part |
| UNAUTHENTICATED | 401 | No token on a protected route |
| INVALID_CREDENTIALS | 401 | Wrong email or password (same answer for unknown email) |
| TOKEN_EXPIRED | 401 | JWT expired |
| TOKEN_INVALID | 401 | JWT malformed, bad signature, or revoked (token version changed) |
| ACCESS_DENIED | 403 | Not allowed |
| ACCOUNT_SUSPENDED | 403 | Account status SUSPENDED / BANNED |
| ADMIN_REGISTRATION_FORBIDDEN | 403 | `userType: 2` on public registration |
| NOT_FOUND | 404 | Generic not found |
| METHOD_NOT_ALLOWED | 405 | |
| CONFLICT | 409 | Generic conflict / data integrity violation |
| CONCURRENT_MODIFICATION | 409 | Optimistic-lock failure, retry |
| PAYLOAD_TOO_LARGE | 413 | Upload above limit |
| INTERNAL_ERROR | 500 | |
| EMAIL_ALREADY_USED | 409 | |
| FILE_REQUIRED / FILE_TYPE_NOT_ALLOWED / FILE_CONTENT_MISMATCH | 400 | Upload validation |
| PROVIDER_NOT_FOUND | 404 | |
| PROVIDER_NOT_ELIGIBLE | 422 | Provider not ACTIVE + VERIFIED + accepting orders |
| PROVIDER_SERVICES_MISMATCH | 422 | Selected provider does not offer every requested service |
| PROVIDER_ALREADY_EXISTS | 409 | |
| PAYMENT_ACCOUNT_MISSING | 422 | Provider has no receiving account for the method |
| ORDER_NOT_FOUND | 404 | |
| ORDER_INVALID_STATE | 409 | Action not allowed in the current order status |
| ORDER_DEADLINE_PASSED | 409 | Order response deadline passed |
| ORDER_NOT_FULLY_PAID | 409 | Start / completion needs everything paid |
| SELF_ORDER_FORBIDDEN | 422 | Ordering from your own provider profile |
| RESPONSE_NOT_FOUND | 404 | |
| RESPONSE_ALREADY_EXISTS | 409 | One offer per provider per order |
| RESPONSE_NOT_TARGETED | 403 | Provider was not invited (or declined / invitation expired) |
| RESPONSE_NOT_PENDING | 409 | Only PENDING offers can change |
| OFFER_EXPIRED | 409 | `now >= expiresAt` |
| RESPONSE_ALREADY_ACCEPTED | 409 | Order already has an accepted offer |
| RESPONSE_ORDER_MISMATCH | 400 | Offer does not belong to that order |
| INVALID_EXPIRY | 422 | `expiresAt` outside [now+1h, now+7d] |
| APPOINTMENT_NOT_FOUND | 404 | |
| APPOINTMENT_OUTSIDE_WORKING_HOURS | 422 | |
| APPOINTMENT_IN_PAST | 422 | |
| APPOINTMENT_INVALID_STATE | 409 | |
| NO_SHOW_TOO_EARLY | 409 | Before appointment start + grace minutes |
| POLICY_VIOLATES_PLATFORM_RULES | 422 | Provider policy weaker than platform rules |
| PAYMENT_NOT_FOUND | 404 | |
| PAYMENT_INVALID_STATE | 409 | |
| PAYMENT_METHOD_NOT_SUPPORTED | 422 | CARD / WALLET / OTHER are reserved |
| PAYMENT_METHOD_REQUIRED | 409 | Select a method first |
| PAYMENT_NOTHING_DUE | 409 | |
| PAYMENT_PROOF_LIMIT | 409 | More than `wadii.payment.max-proofs-per-payment` proofs |
| PAYMENT_PROOF_NOT_ALLOWED | 422 | Proofs only for VODAFONE_CASH / INSTAPAY |
| REFUND_NOT_FOUND | 404 | |
| REFUND_AMOUNT_INVALID | 422 | More than the refundable amount |
| REFUND_INVALID_STATE | 409 | No confirmed payment, wrong status, refund window over |
| CHARGE_NOT_FOUND / CHARGE_INVALID_STATE | 404 / 409 | Additional charges |
| INVOICE_NOT_FOUND | 404 | Order not completed yet |
| DISPUTE_NOT_FOUND / DISPUTE_INVALID_STATE / DISPUTE_ALREADY_OPEN | 404 / 409 / 409 | |
| WARRANTY_NOT_FOUND / WARRANTY_EXPIRED / WARRANTY_CLAIM_INVALID_STATE | 404 / 409 / 409 | |
| RETURN_NOT_FOUND / RETURN_INVALID_STATE / RETURN_WINDOW_CLOSED | 404 / 409 / 409 | |
| RATING_NOT_ALLOWED / RATING_ALREADY_EXISTS | 409 | |
| ALREADY_FOLLOWING / OFFER_ALREADY_SAVED | 409 | |
| CONVERSATION_NOT_FOUND | 404 | |

### 1.4 Pagination

List endpoints take `?page=0&size=20` (page from 0; size 1..100, default 20) and return:

```json
{ "items": [ ], "page": 0, "size": 20, "totalItems": 57, "totalPages": 3 }
```

Paginated: orders, responses, notifications, providers, promotional offers, saved offers, following, payments,
refunds, disputes, audit logs, fraud flags, advertisements, ratings, conversations, messages, timeline, admin users.
Bounded lists that are not paged: catalog reference data, a provider's branches/links/payment accounts/documents,
an order's attachments/charges/claims/returns/policy snapshots, policy version history.

### 1.5 Authentication

`Authorization: Bearer <jwt>` on every route except the public ones (1.7).

- JWT HS256, subject = user id, claims `email`, `role`, `tv` (token version). Lifetime `security.jwt.expiration-time` (24 h).
- On each request the user is reloaded: unknown user, changed `tv`, or a non-ACTIVE account -> 401/403.
- `tv` is incremented by: password change, logout-all, admin-role change, account suspension, USER -> PROVIDER application.
- Identity is **only** taken from the JWT. `userId`, `providerId`, `ownerId`, `role` in a request body are ignored.

### 1.6 Roles and admin permissions

| Role | Ordinal (`userType`) | Notes |
|---|---|---|
| USER | 0 | Customer |
| PROVIDER | 1 | Has a provider profile. May also order as a customer (not from itself). |
| ADMIN | 2 | Never via public registration. Holds admin permissions below. |

| Admin permission | Spring authority | Grants |
|---|---|---|
| SUPER_ADMIN | ADMIN_SUPER | Everything (implies the three others), admin accounts, admin roles |
| SUPPORT_ADMIN | ADMIN_SUPPORT | Users, providers (verification, suspension), orders, non-financial disputes, chats, documents |
| CONTENT_ADMIN | ADMIN_CONTENT | Catalog, advertisements, rating moderation |
| FINANCE_ADMIN | ADMIN_FINANCE | Payments, refunds, payment/refund disputes, financial outcomes |

### 1.7 Public routes (no token)

`POST /api/v1/auth/register`, `POST /api/v1/auth/login` (+ `/auth/*` aliases), `GET /api/v1/catalog/**`,
`GET /api/v1/files/public/{key}` and `GET /users/{key}`, `GET /api/v1/advertisements/live`,
`POST /api/v1/advertisements/{id}/impression|click`, WebSocket handshakes `/ws/**`, `/ws-native/**`.

### 1.8 Uploads

`multipart/form-data`, field `file`. Extension, declared MIME type and file signature (magic bytes) must agree.
The original filename is discarded; files are stored as `<uuid>.<ext>`.

| Category | Allowed | Max | Visibility |
|---|---|---|---|
| Profile / background image | jpg, png, webp, gif | 5 MB | Public: `GET /api/v1/files/public/{key}` |
| Provider document | jpg, png, webp, pdf | 10 MB | Owner + SUPPORT admin |
| Order attachment | jpg, png, webp, pdf | 10 MB | Customer, invited providers, accepted provider, SUPPORT |
| Payment proof | jpg, png, webp, pdf | 10 MB | Customer, accepted provider, SUPPORT / FINANCE |
| Dispute evidence | jpg, png, webp, pdf, mp4, webm, mp3, ogg, m4a | 10 MB | Dispute parties, SUPPORT / FINANCE |
| Chat media | jpg, png, webp, mp3, ogg, m4a | 10 MB | Conversation participants, SUPPORT |

Private files are streamed only through the authorized endpoints listed per module, with
`Cache-Control: private, no-store` and `X-Content-Type-Options: nosniff`. The public file route cannot serve them.

---

## 2. Auth — `/api/v1/auth` (alias `/auth`)

| Method | Path | Auth | Body | Response |
|---|---|---|---|---|
| POST | `/register` | public | RegisterRequest | 201 AuthResponse |
| POST | `/login` | public | LoginRequest | 200 AuthResponse |
| POST | `/change-password` | any | `{currentPassword, newPassword}` (8..72) | 200; all tokens revoked |
| POST | `/logout-all` | any | – | 200; all tokens revoked, FCM token cleared |

```json
// RegisterRequest
{ "firstName": "Mona", "lastName": "Ali", "email": "mona@example.com", "password": "min 8 chars",
  "phone": "+2010...", "cityId": 3, "userType": 0, "providerName": "required when userType=1", "fcmToken": "optional" }
// LoginRequest
{ "email": "mona@example.com", "password": "...", "fcmToken": "optional (stored for push)" }
// AuthResponse
{ "token": "<jwt>", "tokenType": "Bearer", "expiresIn": 86400, "user": UserView, "providerId": 12 }
```

- `userType` 2 -> 403 `ADMIN_REGISTRATION_FORBIDDEN`. Duplicate email (case-insensitive) -> 409 `EMAIL_ALREADY_USED`.
- `userType` 1 creates the user **and** a provider profile with verification `PENDING` (cannot receive orders until verified).
- Passwords: BCrypt (cost 12), never returned. Unknown email and wrong password give the same 401.

`UserView`: `{id, email, firstName, lastName, phone, image, backgroundImage, cityId, cityName, role, userType, status, adminRoles, createdAt}`.

## 3. Current user — `/api/v1/users/me`

| Method | Path | Body | Notes |
|---|---|---|---|
| GET | `/api/v1/users/me` | – | UserView of the caller |
| PATCH | `/api/v1/users/me` | `{firstName?, lastName?, phone?, cityId?, fcmToken?}` | partial update |
| POST | `/api/v1/users/me/image` | multipart `file` | public image, returns UserView |
| POST | `/api/v1/users/me/background-image` | multipart `file` | |
| GET | `/api/v1/users/me/following` | – | paged ProviderPublicView |
| GET | `/api/v1/users/me/saved-offers` | – | paged OfferView |

There is no endpoint taking another user's id.

## 4. Catalog — `/api/v1/catalog`

Reads are public; writes need **CONTENT_ADMIN**.

| Method | Path | Body / Response |
|---|---|---|
| GET | `/countries` | `[{id, name}]` |
| GET | `/countries/{countryId}/provinces` | `[{id, name}]` |
| GET | `/provinces/{provinceId}/cities` | `[{id, name}]` |
| GET | `/services?includeInactive=false` | `[{id, name, description, active}]` |
| GET | `/car-types` | `[{id, name}]` |
| GET | `/spare-parts` | `[{id, name}]` (suggestions) |
| GET | `/cancellation-reasons` | `[{id, text, appliesTo: CUSTOMER\|PROVIDER\|ANY}]` |
| POST | `/countries`, `/car-types`, `/spare-parts` | `{name}` -> 201 |
| POST | `/provinces` | `{name, parentId: countryId}` |
| POST | `/cities` | `{name, parentId: provinceId}` |
| POST | `/services` | `{name, description?, active?}` |
| PUT | `/services/{id}` | `{name, description?, active?}` (deactivate instead of delete) |
| POST | `/cancellation-reasons` | `{text, appliesTo, active?}` |

Reference data (Egypt, provinces, cities, 10 services, car brands, parts, reasons) is created by Flyway `V2`.

## 5. Providers — `/api/v1/providers`

### 5.1 Browse (any authenticated user)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/providers?serviceId=&q=&page=&size=` | ACTIVE + VERIFIED providers, sorted by rating |
| GET | `/api/v1/providers/{id}` | ProviderPublicView (BANNED -> 404) |
| GET | `/api/v1/providers/{providerId}/policies` | effective policies (provider version or platform default) |
| GET | `/api/v1/providers/{providerId}/ratings` | paged RatingView |
| POST / DELETE | `/api/v1/providers/{id}/follow` | follow / unfollow (unique per user+provider; not your own profile) |

`ProviderPublicView`: `{id, name, description, image, backgroundImage, verified, ratingAvg, ratingCount, followersCount,
acceptingOrders, services:[{id,name}], branches:[BranchView (active only)], links:[url]}`. Internal ranking and
reliability scores are never exposed here.

`BranchView`: `{id, name, address, latitude, longitude, phone, active, workingHours:[{dayOfWeek 1..7, opensAt "09:00", closesAt "17:00"}]}`.

### 5.2 Become a provider

| Method | Path | Auth | Body |
|---|---|---|---|
| POST | `/api/v1/providers/apply` | USER | `{name, description?}` -> 201 ProviderOwnView |

Switches the role to PROVIDER with verification `PENDING` and revokes the current token (log in again).

### 5.3 Own profile (PROVIDER)

| Method | Path | Body |
|---|---|---|
| GET | `/me` | ProviderOwnView |
| PATCH | `/me` | `{name?, description?, serviceRadiusKm? (1..500)}` |
| PUT | `/me/services` | `{serviceIds: [..]}` (replaces; active services only) |
| PUT | `/me/availability` | `{acceptingOrders: bool}` |
| PUT | `/me/links` | `{links: ["https://..."]}` (max 10, replaces) |
| POST | `/me/branches` | BranchRequest -> 201 BranchView |
| PATCH | `/me/branches/{branchId}` | BranchRequest (partial; `workingHours` replaces all hours) |
| DELETE | `/me/branches/{branchId}` | deactivates (branches are history, never deleted) |
| GET | `/me/payment-accounts` | `[PaymentAccountView]` |
| PUT | `/me/payment-accounts/{VODAFONE_CASH\|INSTAPAY}` | `{accountName, accountIdentifier, instructions?}` |
| DELETE | `/me/payment-accounts/{method}` | deactivates |
| POST | `/me/documents?type=` | multipart `file`; type `NATIONAL_ID_FRONT\|NATIONAL_ID_BACK\|TAX_CARD_FRONT\|TAX_CARD_BACK\|COMMERCIAL_REGISTER\|OTHER` |
| GET | `/me/documents` | `[DocumentView]` |
| GET | `/documents/{documentId}/file` | owner or SUPPORT admin only |
| GET | `/me/stats` | StatsView (own reliability metrics) |
| GET | `/me/orders?scope=OPEN\|ASSIGNED\|CLOSED\|ALL` | paged OrderView of orders the provider was invited to |
| GET | `/me/policies` | effective policies |
| GET | `/me/policies/{type}/versions` | version history (newest first) |
| POST | `/me/policies/{type}/versions` | publish a new version (section 13) |
| POST | `/me/offers`, PATCH/DELETE `/me/offers/{offerId}` | promotional offers (section 20) |

BranchRequest: `{name, address?, latitude?, longitude? (both or none), phone?, active?, workingHours?: [{dayOfWeek, opensAt, closesAt}]}`.
`closesAt` must be after `opensAt`; one entry per day. A branch without published hours is treated as "schedule unknown".

`ProviderOwnView`: adds `userId, status (ACTIVE|SUSPENDED|BANNED), verificationStatus (PENDING|VERIFIED|REJECTED|SUSPENDED),
statusReason, eligibleForOrders, serviceRadiusKm, verifiedAt, createdAt` and all branches.

Eligible for orders = user ACTIVE and provider ACTIVE and VERIFIED and `acceptingOrders`.

### 5.4 Admin — `/api/v1/admin/providers` (SUPPORT_ADMIN)

| Method | Path | Body |
|---|---|---|
| GET | `/?verificationStatus=&status=` | paged ProviderOwnView |
| GET | `/{id}` | ProviderOwnView |
| GET | `/{id}/documents` | `[DocumentView]` |
| POST | `/{id}/verification` | `{decision: VERIFIED\|REJECTED, reason?}` |
| POST | `/{id}/status` | `{status: ACTIVE\|SUSPENDED\|BANNED, reason?}` -> `{provider, withdrawnOffers, assignedOrdersNeedingAction}` |

Suspending / banning: verification becomes SUSPENDED, pending offers are withdrawn (CANCELED), open invitations
DECLINED, no new orders. Orders already ACCEPTED / IN_PROGRESS are **not** changed automatically;
`assignedOrdersNeedingAction` tells support how many to handle (cancel with refund via `POST /orders/{id}/cancel`).
Reactivating restores VERIFIED.

## 6. Orders — `/api/v1/orders`

### 6.1 Lifecycle

```
CREATED -> MATCHING -> QUOTING -> RESPONSES_AVAILABLE -> ACCEPTED -> IN_PROGRESS -> COMPLETED
MATCHING/QUOTING/RESPONSES_AVAILABLE -> EXPIRED  (response deadline, nothing accepted)
any non-terminal -> CANCELED  (rules in 6.4)
```

- MATCHING: no eligible provider yet; the rematch job retries every 10 min until the deadline.
- QUOTING: providers invited, no live offer. RESPONSES_AVAILABLE: at least one PENDING offer
  (falls back to QUOTING when the last pending offer expires / is withdrawn / declined).
- Response deadline: `createdAt + wadii.order.response-window-hours` (48 h). After it: no new offers, pending
  offers EXPIRED, order EXPIRED.
- ACCEPTED -> IN_PROGRESS only when fully paid (**V1 rule PAY_BEFORE_SERVICE**).
- IN_PROGRESS: the provider marks the job done; the customer confirms -> COMPLETED. If the customer does not respond,
  the job auto-completes after `wadii.order.auto-complete-hours` (72 h, 0 = off) provided nothing is outstanding.
- There is no PAID order status: payment is a separate domain (section 9).

### 6.2 Matching

- **Explicit provider** (`providerId` in the request): only that provider is targeted, after checking it is
  eligible (422 `PROVIDER_NOT_ELIGIBLE`), offers **all** requested services (422 `PROVIDER_SERVICES_MISMATCH`)
  and is not the customer's own profile (422 `SELF_ORDER_FORBIDDEN`). No other provider is notified.
- **Automatic**: eligible providers offering **all** requested services (AND, not OR), with an active branch that
  opens on the requested day and — when the order has coordinates — lies within the provider's `serviceRadiusKm`
  (default `wadii.order.default-service-radius-km` = 50). Ranked (rating, reliability, distance, completed orders,
  response speed) and capped at `wadii.order.max-targets` (20). Each target row stores the nearest branch and distance.
- Targets: `TARGETED -> VIEWED (provider opened the order) -> RESPONDED | DECLINED | EXPIRED`.

### 6.3 Endpoints

| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/api/v1/orders` | USER / PROVIDER (as customer) | CreateOrderRequest -> 201 OrderView |
| GET | `/api/v1/orders/mine?status=&page=&size=` | customer | own orders |
| GET | `/api/v1/orders/{id}` | customer, invited/accepted provider, SUPPORT | invited providers do not get customer identity; marks target VIEWED |
| PATCH | `/api/v1/orders/{id}` | customer | `{carDescription?, requestedDate?, address?, comment?}` only before any offer |
| POST | `/api/v1/orders/{id}/cancel` | customer / accepted provider / SUPPORT | `{reasonId?, reason?}` -> `{order, refundIds}` |
| GET | `/api/v1/orders/{id}/cancellation` | viewers | CancellationView |
| POST | `/api/v1/orders/{id}/decline-invitation` | invited provider | target -> DECLINED |
| POST | `/api/v1/orders/{id}/start` | accepted provider | ACCEPTED -> IN_PROGRESS; 409 `ORDER_NOT_FULLY_PAID` |
| POST | `/api/v1/orders/{id}/mark-done` | accepted provider | sets `providerCompletedAt` |
| POST | `/api/v1/orders/{id}/confirm-completion` | customer | -> COMPLETED; issues invoice + warranty |
| GET | `/api/v1/orders/{id}/timeline` | customer, accepted provider, SUPPORT | paged TimelineView |
| GET | `/api/v1/orders/{id}/policies` | viewers | immutable policy snapshots |
| POST | `/api/v1/orders/{id}/attachments` | customer | multipart `file` (max 10 per order) |
| GET | `/api/v1/orders/{id}/attachments` | viewers | `[AttachmentView]` |
| GET | `/api/v1/orders/{id}/attachments/{attachmentId}/file` | viewers | private file |
| GET | `/api/v1/admin/orders?status=` | SUPPORT_ADMIN | paged |

```json
// CreateOrderRequest (customer comes from the JWT)
{ "carTypeId": 1, "carDescription": "Toyota Corolla 2018", "requestedDate": "2026-11-01",
  "address": "Nasr City", "latitude": 30.0444, "longitude": 31.2357, "comment": "Noise when braking",
  "serviceIds": [1, 2], "spareParts": ["Brake pads"], "providerId": null }
```

`OrderView`: `{id, status, carTypeId, carTypeName, carDescription, requestedDate, address, latitude, longitude, comment,
services, spareParts:[{id,name}], selectedProviderId, acceptedProviderId, acceptedResponseId, currency,
responseDeadline, createdAt, acceptedAt, startedAt, providerCompletedAt, completedAt, canceledAt, expiredAt,
customer:{userId, firstName, lastName, image, phone} (customer / accepted provider / admin only),
relation: CUSTOMER|ACCEPTED_PROVIDER|TARGETED_PROVIDER|ADMIN, myTargetStatus, myDistanceKm}`.

`TimelineView`: `{event, actorRole: CUSTOMER|PROVIDER|ADMIN|SYSTEM, actorUserId, details, at}`. Events include
ORDER_CREATED, PROVIDERS_NOTIFIED, NO_PROVIDER_MATCHED, OFFER_SUBMITTED/UPDATED/WITHDRAWN/DECLINED/EXPIRED/ACCEPTED,
POLICY_SNAPSHOT_CREATED, APPOINTMENT_SCHEDULED/RESCHEDULED, NO_SHOW_REPORTED, PAYMENT_*, ADDITIONAL_CHARGE_*,
REFUND_*, SERVICE_STARTED, SERVICE_MARKED_DONE, SERVICE_COMPLETED, ORDER_CANCELED, ORDER_EXPIRED, DISPUTE_*,
WARRANTY_ISSUED, WARRANTY_CLAIMED, RETURN_REQUESTED, INVOICE_ISSUED, RATED.

### 6.4 Cancellation rules

| Who | Allowed when | Money |
|---|---|---|
| Customer | before IN_PROGRESS | Before acceptance nothing is paid. After acceptance: full refund, except a late cancellation (inside `freeCancellationHours` before the appointment) keeps the CANCELLATION snapshot `feePercent`. Refund is REQUESTED (provider processes it). |
| Accepted provider | ACCEPTED only; reason required | Full refund, created APPROVED |
| SUPPORT admin | any non-terminal; reason required | Full refund, created APPROVED |
| System (no-show) | see 8.3 | per no-show rules |

Always: `OrderCancellation` row (`canceledBy`, `actorRole`, reason, status at cancel), pending offers -> CANCELED,
open invitations -> EXPIRED, open appointment -> CANCELED (history row), active payment -> CANCELED,
timeline + audit + notifications. Nothing is deleted. An IN_PROGRESS order cannot be canceled by the customer:
open a dispute instead.

## 7. Offers (responses) — `/api/v1/responses`

| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/api/v1/responses` | invited, eligible PROVIDER | SubmitResponseRequest -> 201 ResponseView |
| PATCH | `/api/v1/responses/{id}` | author | only PENDING, unexpired, before the deadline |
| POST | `/api/v1/responses/{id}/withdraw` | author | PENDING -> CANCELED |
| POST | `/api/v1/responses/{id}/accept` | order customer | atomic acceptance -> AcceptResult |
| POST | `/api/v1/responses/{id}/decline` | order customer | PENDING -> DECLINED |
| GET | `/api/v1/responses/{id}` | author, order customer, SUPPORT | |
| GET | `/api/v1/responses/mine` | provider: offers sent; customer: offers received | paged |
| GET | `/api/v1/orders/{orderId}/responses?sort=` | order customer, SUPPORT | paged, sorted |
| GET | `/api/v1/orders/{orderId}/my-response` | PROVIDER | own offer on that order |

```json
// SubmitResponseRequest (provider from the JWT; any providerId/price in the body is ignored)
{ "orderId": 10, "description": "Oil + filter", "laborCost": 200.00, "partsCost": 0, "discount": 50.00,
  "partPrices": [{ "sparePartId": 33, "price": 150.00 }], "estimatedDurationMinutes": 90,
  "expiresAt": "2026-10-30T10:00:00Z", "branchId": 4, "proposedAppointmentAt": "2026-10-31T08:00:00Z" }
```

- `price = laborCost + partsCost - discount`, computed by the server, must be > 0. With `partPrices`, `partsCost` is
  their sum and every `sparePartId` must belong to the order.
- **Expiry**: `expiresAt` optional (default now + `wadii.offer.default-validity-hours` = 48 h). Must be between
  now + 1 h and now + 7 days (`min/max-validity-hours`), else 422 `INVALID_EXPIRY`. It is then capped at the order
  response deadline. Acceptance requires `now < expiresAt` (409 `OFFER_EXPIRED`).
- One offer per provider per order (`UNIQUE(order_id, provider_id)`, 409 `RESPONSE_ALREADY_EXISTS`).
- `proposedAppointmentAt` must be in the future and inside the branch working hours (422).
- Statuses: `PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELED`. Offers are never deleted.

`ResponseView`: `{id, orderId, provider:{id,name,image,ratingAvg,ratingCount,verified}, branch:{id,name,address,distanceKm},
laborCost, partsCost, discount, price, currency, description, estimatedDurationMinutes, proposedAppointmentAt,
expiresAt, status, statusChangedAt, partPrices:[{sparePartId,name,price}], createdAt, updatedAt}`.

Sort (`sort=`): `RECOMMENDED` (default; provider ranking 60% + relative price 40%), `LOWEST_PRICE`, `HIGHEST_RATED`
(Bayesian rating), `NEAREST`, `FASTEST_RESPONSE`. PENDING offers are listed first.

### 7.1 Atomic acceptance

In one transaction, holding a `SELECT ... FOR UPDATE` lock on the order row:
verify the caller owns the order -> order still accepts offers and deadline not passed -> offer belongs to the order
-> offer PENDING and `now < expiresAt` -> provider still eligible -> offer ACCEPTED -> every other PENDING offer
DECLINED (providers notified) -> open invitations EXPIRED -> `acceptedProviderId`, `acceptedResponseId`,
`acceptedAt` set (server only) -> order ACCEPTED -> policy snapshot -> conversation opened -> appointment created
(if the offer proposed a time) -> payment opened for the full price -> notifications -> commit.

The partial unique index `uq_response_one_accepted_per_order` (`UNIQUE(order_id) WHERE status='ACCEPTED'`)
guarantees **one order = at most one accepted offer** even outside the service layer. Concurrent accepts: one
returns 200, the others 409.

`AcceptResult`: `{accepted: ResponseView, orderId, paymentId, conversationId}`.

## 8. Appointments — `/api/v1/orders/{orderId}/appointment`

| Method | Path | Who | Body |
|---|---|---|---|
| GET | `` | customer, accepted provider, SUPPORT/FINANCE | AppointmentView |
| PUT | `` | customer or accepted provider (order ACCEPTED) | `{branchId?, startAt, reason?}` |
| POST | `/no-show` | customer or accepted provider | `{reason?}` |

### 8.1 Scheduling
First PUT creates the appointment (default 60 min, or the offer's estimated duration when created from the offer).
`startAt` must be in the future and fit inside the branch working hours of that day (platform time zone) ->
422 `APPOINTMENT_OUTSIDE_WORKING_HOURS` / `APPOINTMENT_IN_PAST`. The branch must belong to the accepted provider.

### 8.2 Rescheduling
A PUT on an existing appointment requires `reason`; status becomes RESCHEDULED and an `AppointmentChange`
(`oldStart, newStart, changedBy, reason, at`) is stored. The other party is notified.

### 8.3 No-show
Allowed after `scheduledStart + wadii.no-show.grace-minutes` (30), else 409 `NO_SHOW_TOO_EARLY`.
The customer reports `PROVIDER_NO_SHOW`; the provider reports `CUSTOMER_NO_SHOW`. With
`wadii.no-show.cancel-order=true` the order is canceled by SYSTEM: provider no-show -> full refund created APPROVED
(`refund-on-provider-no-show`); customer no-show -> refund minus the NO_SHOW snapshot `feePercent`.
The appointment and order are kept; the other party can dispute.

`AppointmentView`: `{id, orderId, branchId, branchName, scheduledStart, scheduledEnd, status: SCHEDULED|RESCHEDULED|COMPLETED|CANCELED|CUSTOMER_NO_SHOW|PROVIDER_NO_SHOW, noShowReportedBy, noShowReportedAt, history:[ChangeView]}`.

## 9. Payments

### 9.1 Model

- A `Payment` is a request to pay an amount due on an order. Created automatically on acceptance (full price) and
  whenever an approved additional charge leaves something outstanding. At most one active payment
  (`PENDING | PROOF_SUBMITTED | DISPUTED`) per order (partial unique index).
- `amountDue = accepted price + approved additional charges`; `outstanding = amountDue - confirmed payments`.
  The client never sends an amount.
- Status: `PENDING -> PROOF_SUBMITTED -> PROVIDER_CONFIRMED | REJECTED`; `DISPUTED`; `EXPIRED` (PENDING without
  proof after `wadii.payment.pending-ttl-hours` = 72 h); `CANCELED` (order canceled/expired or method changed after
  a proof); `REFUNDED` (fully refunded).
- Methods: `VODAFONE_CASH`, `INSTAPAY`, `CASH`. `CARD`, `WALLET`, `OTHER` are reserved (422).
- Payment status never changes the order status; the order only checks "fully paid" to start and complete.

### 9.2 Endpoints

| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/api/v1/orders/{orderId}/financials` | customer, accepted provider, SUPPORT/FINANCE | OrderFinancials |
| GET | `/api/v1/payments/{id}` | same | PaymentView |
| GET | `/api/v1/payments/mine` | provider: received; customer: own | paged |
| POST | `/api/v1/payments/{id}/method` | customer | `{method}` |
| POST | `/api/v1/payments/{id}/proofs` | customer | multipart `file` + `transactionReference?`, `senderIdentifier?`, `note?` |
| GET | `/api/v1/payment-proofs/{proofId}/file` | customer, accepted provider, SUPPORT/FINANCE | private file |
| POST | `/api/v1/payments/{id}/confirm` | accepted provider only | |
| POST | `/api/v1/payments/{id}/reject` | accepted provider only | `{reason}` |
| GET | `/api/v1/admin/payments?status=` | FINANCE_ADMIN | paged |

### 9.3 Flows

**Vodafone Cash / InstaPay**: customer selects the method -> the response contains `payee {accountName, accountIdentifier}`
copied from the provider's active receiving account (422 `PAYMENT_ACCOUNT_MISSING` if none) -> customer transfers
outside the app -> uploads a proof -> `PROOF_SUBMITTED` (provider notified; the proof is **not** a confirmation,
`amountPaid` stays 0) -> provider checks the real account -> `confirm` (PROVIDER_CONFIRMED, `amountPaid = amount`,
proofs ACCEPTED) or `reject` (REJECTED, proofs REJECTED, customer notified).
Several proofs per payment are allowed (up to 5), never overwritten.

**Cash**: select `CASH` -> customer pays in person -> accepted provider `confirm`s. No proofs.

**Method change**: while PENDING the method changes in place; after a proof (PROOF_SUBMITTED) the payment is CANCELED
and a new one is opened (old proofs kept); after REJECTED / EXPIRED a new payment is opened for the outstanding amount.

**Authorization**: JWT -> provider profile -> must equal the order's accepted provider -> payment of that order.
Another provider or the customer get 403.

**Fraud signals**: 3+ proofs on a payment, a transaction reference reused on another payment.

`PaymentView`: `{id, orderId, method, status, amount, currency, amountPaid, amountRefunded, remainingAmount,
payee:{accountName, accountIdentifier}, createdAt, methodSelectedAt, submittedAt, confirmedAt, confirmedBy,
rejectedAt, rejectionReason, disputedAt, closedAt, proofs:[ProofView]}`.
`OrderFinancials`: `{orderId, currency, acceptedPrice, approvedCharges, amountDue, paid, refunded, outstanding,
payments, charges, refunds}`.

## 10. Additional charges

| Method | Path | Who | Body |
|---|---|---|---|
| GET | `/api/v1/orders/{orderId}/charges` | parties, SUPPORT/FINANCE | `[ChargeView]` |
| POST | `/api/v1/orders/{orderId}/charges` | accepted provider (ACCEPTED / IN_PROGRESS) | `{amount, reason}` |
| POST | `/api/v1/charges/{id}/decision` | customer | `{approve: bool}` |
| POST | `/api/v1/charges/{id}/cancel` | accepted provider | withdraw a REQUESTED charge |

The accepted offer price never changes. Approval raises `amountDue` and opens a payment for the difference;
the customer can only confirm completion once nothing is outstanding.
`ChargeView`: `{id, orderId, amount, currency, reason, status: REQUESTED|APPROVED|REJECTED|CANCELED, requestedBy, decidedBy, decidedAt, createdAt}`.

## 11. Refunds

| Method | Path | Who | Body |
|---|---|---|---|
| POST | `/api/v1/orders/{orderId}/refunds` | customer | `{paymentId?, amount, reason}` |
| GET | `/api/v1/refunds/{id}` | parties, SUPPORT/FINANCE | RefundView |
| POST | `/api/v1/refunds/{id}/decision` | accepted provider or FINANCE | `{approve, note?}` (REQUESTED only) |
| POST | `/api/v1/refunds/{id}/complete` | accepted provider or FINANCE | money sent back (APPROVED only) |
| GET | `/api/v1/admin/refunds?status=` | FINANCE_ADMIN | paged |

- Only a confirmed payment can be refunded; partial refunds allowed; the sum of REQUESTED/APPROVED/PROCESSING/COMPLETED
  refunds can never exceed `amountPaid` (422 `REFUND_AMOUNT_INVALID`).
- After completion, a customer request must be inside the REFUND snapshot `periodDays`.
- Completing increases `payment.amountRefunded`; a fully refunded payment becomes REFUNDED.
- System-created refunds (`source`): CANCELLATION, NO_SHOW, DISPUTE, RETURN, ADMIN. Provider/admin/system ones are
  created APPROVED; customer requests start REQUESTED.
- Statuses: `REQUESTED, APPROVED, REJECTED, PROCESSING, COMPLETED, FAILED` (PROCESSING/FAILED reserved for gateways).
- Fraud signal: 3+ refund requests by one customer in 30 days.

## 12. Invoices

`GET /api/v1/orders/{orderId}/invoice` (parties, SUPPORT/FINANCE). Issued once at completion:
`subtotal = labor + parts`, `discount` (offer), `additionalCharges` (approved), `refunds` (completed so far),
`total = subtotal - discount + additionalCharges`, `paid`, `remaining`, `platformFee = total x wadii.commission.percent`,
`providerNet = total - refunds - platformFee`, `invoiceNumber = WD-<yyyymmdd>-<orderId>`.

## 13. Policies

Types: `RETURN, REFUND, CANCELLATION, WARRANTY, PAYMENT, SERVICE, NO_SHOW`.

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/policies/platform` | any | platform defaults |
| GET | `/api/v1/providers/{providerId}/policies` | any | effective policy per type |
| GET | `/api/v1/providers/me/policies` | PROVIDER | |
| GET | `/api/v1/providers/me/policies/{type}/versions` | PROVIDER | history |
| POST | `/api/v1/providers/me/policies/{type}/versions` | PROVIDER | `{terms, periodDays?, feePercent?, freeCancellationHours?}` |
| GET | `/api/v1/orders/{id}/policies` | order viewers | snapshots |

- Versions are append-only (`version_no` 1, 2, ...); the current pointer moves; nothing is overwritten.
- `periodDays`: return / refund window, warranty length. `feePercent`: cancellation or no-show fee.
  `freeCancellationHours`: free until N hours before the appointment.
- Platform rules (`wadii.platform-policy.*`): cancellation fee <= 50%, no-show fee <= 50%, warranty >= 0 days,
  return/refund window >= 0 days. A weaker provider version -> 422 `POLICY_VIOLATES_PLATFORM_RULES`.
- Precedence: legal/platform rules -> provider policy -> (product policy, reserved) -> order snapshot.
- On acceptance every type is snapshotted (`source` PROVIDER with version id/no, or PLATFORM default). Cancellation,
  no-show, refund windows, warranty and returns read the snapshot, never the live policy.

`PolicyTerms`: `{type, source, versionId, versionNo, terms, periodDays, feePercent, freeCancellationHours}`.

## 14. Disputes

Types `ORDER_DISPUTE, PAYMENT_DISPUTE, SERVICE_DISPUTE, PRODUCT_DISPUTE, REFUND_DISPUTE, WARRANTY_DISPUTE`;
statuses `OPEN, UNDER_REVIEW, WAITING_CUSTOMER, WAITING_PROVIDER, RESOLVED, REJECTED, CANCELED`.

| Method | Path | Who | Body |
|---|---|---|---|
| POST | `/api/v1/orders/{orderId}/disputes` | customer or accepted provider | `{type, reason, description?, paymentId? (required for PAYMENT_DISPUTE)}` |
| GET | `/api/v1/disputes/mine` | | paged |
| GET | `/api/v1/disputes/{id}` | parties, SUPPORT/FINANCE | DisputeView with evidence |
| POST | `/api/v1/disputes/{id}/evidence` | parties, SUPPORT/FINANCE | multipart/form: `kind`, `file?`, `messageId?`, `paymentProofId?`, `note?` |
| GET | `/api/v1/dispute-evidence/{evidenceId}/file` | parties, SUPPORT/FINANCE | private file |
| POST | `/api/v1/disputes/{id}/cancel` | opener, while OPEN | |
| GET | `/api/v1/admin/disputes?status=` | SUPPORT or FINANCE | paged (open ones by default) |
| POST | `/api/v1/admin/disputes/{id}/status` | owning team | `{status: UNDER_REVIEW\|WAITING_CUSTOMER\|WAITING_PROVIDER, note?}` |
| POST | `/api/v1/admin/disputes/{id}/resolve` | owning team | `{outcome, resolution, refundAmount?}` |
| POST | `/api/v1/admin/disputes/{id}/reject?resolution=` | owning team | dismiss |

- PAYMENT_DISPUTE is allowed on a PROOF_SUBMITTED or REJECTED payment; the payment becomes DISPUTED.
- PAYMENT and REFUND disputes belong to FINANCE admins; the others to SUPPORT admins (both notified on creation).
- Outcomes: `PAYMENT_CONFIRMED` / `PAYMENT_REJECTED` (act on the disputed payment), `REFUND_CUSTOMER`
  (creates an APPROVED refund of `refundAmount`), `IN_FAVOUR_OF_PROVIDER`, `IN_FAVOUR_OF_CUSTOMER`, `NO_ACTION`.
  Financial outcomes require FINANCE_ADMIN.
- Rejecting / canceling a payment dispute returns the payment to REJECTED / its previous status.
- Evidence kinds: `IMAGE, VIDEO, DOCUMENT, PAYMENT_PROOF, MESSAGE, INVOICE, SERVICE_EVIDENCE, TEXT`. Referenced messages
  and proofs must belong to the same order. One open dispute per type per order. Fraud signal: more than
  `wadii.fraud.max-disputes-per-30-days` (5) disputes in 30 days.

## 15. Warranty and returns

| Method | Path | Who | Body |
|---|---|---|---|
| GET | `/api/v1/orders/{orderId}/warranty` | parties | WarrantyView |
| GET | `/api/v1/orders/{orderId}/warranty/claims` | parties | `[ClaimView]` |
| POST | `/api/v1/orders/{orderId}/warranty/claims` | customer, while valid | `{description}` |
| POST | `/api/v1/warranty-claims/{claimId}/decision` | accepted provider | `{accept, response?}` |
| POST | `/api/v1/warranty-claims/{claimId}/resolve` | accepted provider | ACCEPTED -> RESOLVED |
| POST | `/api/v1/warranty-claims/{claimId}/escalate` | customer | opens a WARRANTY_DISPUTE |
| GET | `/api/v1/orders/{orderId}/returns` | parties | `[ReturnView]` |
| POST | `/api/v1/orders/{orderId}/returns` | customer, COMPLETED, inside RETURN window | `{description}` |
| POST | `/api/v1/returns/{returnId}/decision` | accepted provider | `{approve, note?}` |
| POST | `/api/v1/returns/{returnId}/received` | accepted provider | `{refundAmount?}` (optional separate refund) |
| POST | `/api/v1/returns/{returnId}/close` | accepted provider | |

- A warranty is issued at completion when the WARRANTY snapshot has `periodDays > 0`: `startDate` = completion day,
  `endDate = start + periodDays`, status ACTIVE -> EXPIRED (daily job 00:15).
- Return and refund are separate: return with refund, return without refund, refund without return.

## 16. Ratings

| Method | Path | Who |
|---|---|---|
| POST | `/api/v1/orders/{orderId}/rating` | customer of a COMPLETED order, once (`{score 1..5, comment?}`) |
| GET | `/api/v1/orders/{orderId}/rating` | order viewers |
| PATCH | `/api/v1/ratings/{id}` | author |
| DELETE | `/api/v1/ratings/{id}` | author, CONTENT or SUPPORT admin |
| GET | `/api/v1/providers/{providerId}/ratings` | any (paged) |

`UNIQUE(order_id, customer_id)`; the provider's `ratingAvg` / `ratingCount` are recomputed on every change.

## 17. Chat and calls

One conversation per order, opened on acceptance, between the customer and the accepted provider. SUPPORT admins
can read (not write). A user cannot reach another order's chat by changing ids (403).

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/conversations` | participant (paged, newest activity first) |
| GET | `/api/v1/orders/{orderId}/conversation` | participants, SUPPORT |
| GET | `/api/v1/conversations/{id}/messages` | participants, SUPPORT (paged, newest first) |
| POST | `/api/v1/conversations/{id}/messages` | participants: `{text}` (<= 4000) |
| POST | `/api/v1/conversations/{id}/media` | participants: multipart `file`, `caption?` |
| POST | `/api/v1/conversations/{id}/read` | participant: marks the other side's messages read |
| GET | `/api/v1/conversations/unread-count` | |
| GET | `/api/v1/messages/{messageId}/file` | participants, SUPPORT |
| POST | `/api/v1/conversations/{id}/calls?type=AUDIO\|VIDEO&vendor=AGORA\|LIVEKIT` | participant -> CallSession |
| POST | `/api/v1/calls/{callId}/accept?vendor=` | callee only -> CallSession |
| POST | `/api/v1/calls/{callId}/reject` | callee only |
| POST | `/api/v1/calls/{callId}/end` | caller or callee |
| POST | `/api/v1/calls/{callId}/token?vendor=` | caller or callee, while RINGING/ONGOING |

`MessageView`: `{id, conversationId, senderId, type: TEXT|IMAGE|AUDIO|SYSTEM, text, hasFile, contentType, createdAt, readAt}`.
`CallSession`: `{call:{id, conversationId, callerId, calleeId, callType, status, channelName, startedAt, answeredAt, endedAt, durationSeconds}, token:{vendor, token, channelName, uid, appId, url}}`.
The channel name is server-generated (`conv_<conversationId>`) and the token is always issued for the caller's own
uid (= user id). Agora tokens live 1 h, LiveKit 10 min. Tokens are never logged.

## 18. WebSocket (STOMP)

- Endpoints: `/ws` (SockJS, web) and `/ws-native` (plain WebSocket, mobile).
- Auth: `?token=<jwt>` on the handshake **or** `Authorization: Bearer <jwt>` header in the STOMP CONNECT frame.
- Clients may only SUBSCRIBE to their own `/user/queue/*`. SEND requires an authenticated session.

| Direction | Destination | Payload |
|---|---|---|
| subscribe | `/user/queue/notifications` | NotificationView (every persisted notification) |
| subscribe | `/user/queue/messages` | MessageView (both participants) |
| subscribe | `/user/queue/typing` | `{conversationId, userId, typing}` |
| subscribe | `/user/queue/read-receipts` | `{conversationId, readBy}` |
| subscribe | `/user/queue/calls` | `{event: CALL_INVITE\|CALL_ACCEPT\|CALL_REJECT\|CALL_END, call}` |
| subscribe | `/user/queue/errors` | `{code, message}` |
| send | `/app/chat.send` | `{conversationId, text}` |
| send | `/app/chat.typing` | `{conversationId, typing}` |
| send | `/app/chat.read` | `{conversationId}` |

The previous raw `/web-socket/{userId}` handler and user-to-user chat were removed (chat is per order now).
CALL_INVITE is also sent as an FCM data message (`event, callId, conversationId, callType, fromUserId`).

## 19. Notifications

Persisted inbox; FCM and WebSocket are only delivery channels (sent after the transaction commits; failures never
affect the business operation).

| Method | Path |
|---|---|
| GET | `/api/v1/notifications?unreadOnly=false&page=&size=` |
| GET | `/api/v1/notifications/unread-count` -> `{count}` |
| POST | `/api/v1/notifications/{id}/read` (own only) |
| POST | `/api/v1/notifications/read-all` |

`NotificationView`: `{id, type, title, body, orderId, data, read, readAt, createdAt}`. Types:
ORDER_CREATED, ORDER_TARGETED, NEW_RESPONSE, RESPONSE_ACCEPTED, RESPONSE_DECLINED, RESPONSE_EXPIRED, ORDER_EXPIRED,
ORDER_CANCELED, ORDER_STARTED, SERVICE_MARKED_DONE, ORDER_COMPLETED, APPOINTMENT_SCHEDULED, APPOINTMENT_RESCHEDULED,
NO_SHOW_REPORTED, PAYMENT_CREATED, PAYMENT_PROOF_SUBMITTED, PAYMENT_CONFIRMED, PAYMENT_REJECTED, PAYMENT_DISPUTED,
PAYMENT_EXPIRED, ADDITIONAL_CHARGE_REQUESTED, ADDITIONAL_CHARGE_DECIDED, REFUND_REQUESTED, REFUND_UPDATED,
DISPUTE_OPENED, DISPUTE_UPDATED, WARRANTY_CLAIM, RETURN_REQUEST, PROVIDER_APPROVED, PROVIDER_REJECTED,
PROVIDER_SUSPENDED, SYSTEM. (Chat messages are pushed but not stored in the inbox.)

Recipients: customer gets PAYMENT_PROOF_SUBMITTED / CONFIRMED / REJECTED / DISPUTED; provider gets
PAYMENT_PROOF_SUBMITTED / DISPUTED; FINANCE admins get PAYMENT_DISPUTED (SUPPORT admins get other dispute types).

## 20. Promotional offers, saved offers

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/offers?serviceId=&providerId=` | any; current offers with `saved` flag for the caller |
| POST | `/api/v1/providers/me/offers` | PROVIDER: `{title, description, endDate, serviceIds?}` |
| PATCH / DELETE | `/api/v1/providers/me/offers/{offerId}` | owner |
| POST / DELETE | `/api/v1/offers/{offerId}/save` | any (`UNIQUE(user_id, offer_id)`) |
| GET | `/api/v1/users/me/saved-offers` | own |

## 21. Advertisements

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/advertisements/live` | public; ACTIVE and inside [startAt, endAt), by priority |
| POST | `/api/v1/advertisements/{id}/impression` | public; counts + daily stats |
| POST | `/api/v1/advertisements/{id}/click` | public |
| GET | `/api/v1/admin/advertisements?status=` | CONTENT_ADMIN |
| POST | `/api/v1/admin/advertisements` | CONTENT_ADMIN: `{title, description?, imageUrl?, targetUrl?, advertiserName?, priority?, startAt?, endAt?}` |
| PUT | `/api/v1/admin/advertisements/{id}` | CONTENT_ADMIN |
| PUT | `/api/v1/admin/advertisements/{id}/status/{ACTIVE\|INACTIVE\|EXPIRED}` | CONTENT_ADMIN |
| DELETE | `/api/v1/admin/advertisements/{id}` | CONTENT_ADMIN |
| GET | `/api/v1/admin/advertisements/{id}/stats?days=30` | CONTENT_ADMIN: `[{day, impressions, clicks}]` |

The expiration job (every 15 min) moves ACTIVE ads past `endAt` to EXPIRED. INACTIVE ads were disabled manually and
are never touched by the job.

## 22. Admin accounts, audit, fraud

| Method | Path | Who | Body |
|---|---|---|---|
| GET | `/api/v1/admin/users?role=&q=` | SUPPORT_ADMIN | paged UserView |
| GET | `/api/v1/admin/users/{id}` | SUPPORT_ADMIN | |
| POST | `/api/v1/admin/users/admins` | SUPER_ADMIN | `{firstName, lastName?, email, password (>=12), adminRoles:[...]}` |
| PUT | `/api/v1/admin/users/{id}/admin-roles` | SUPER_ADMIN | `{adminRoles}` (revokes tokens; cannot drop own SUPER_ADMIN) |
| PUT | `/api/v1/admin/users/{id}/status` | SUPPORT_ADMIN (SUPER for admins) | `{status: ACTIVE\|SUSPENDED\|BANNED, reason?}` |
| GET | `/api/v1/admin/audit-logs?action=&entityType=&entityId=&actorUserId=` | SUPER / SUPPORT / FINANCE | paged |
| GET | `/api/v1/admin/fraud-flags?status=OPEN` | SUPPORT or FINANCE | paged |
| POST | `/api/v1/admin/fraud-flags/{id}/review?status=REVIEWED\|DISMISSED` | SUPPORT or FINANCE | |

The first SUPER_ADMIN is created at startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD` if no account has that email
(an existing non-admin account is never promoted).

Audited actions: LOGIN, LOGIN_FAILED, REGISTER, ROLE_CHANGE, ACCOUNT_STATUS_CHANGE, AUTHORIZATION_FAILURE,
PROVIDER_APPROVED/REJECTED/SUSPENDED/REACTIVATED, POLICY_VERSION_CREATED, ORDER_CREATED/CANCELED/EXPIRED/STARTED/COMPLETED,
RESPONSE_CREATED/UPDATED/WITHDRAWN/ACCEPTED/DECLINED/EXPIRED, APPOINTMENT_CHANGED, NO_SHOW_REPORTED, PAYMENT_CREATED,
PAYMENT_METHOD_SELECTED, PAYMENT_PROOF_UPLOADED, PAYMENT_CONFIRMED, PAYMENT_REJECTED, PAYMENT_DISPUTED,
PAYMENT_CANCELED, PAYMENT_EXPIRED, PAYMENT_REFUNDED, ADDITIONAL_CHARGE_REQUESTED/DECIDED, REFUND_CREATED/DECIDED/COMPLETED,
DISPUTE_OPENED/UPDATED/RESOLVED, INVOICE_ISSUED, ADMIN_ACTION. Each row: actor, actor role, entity, details, IP, time.

Fraud flags (review only, never auto-blocking in V1): REPEATED_PAYMENT_PROOFS, DUPLICATE_TRANSACTION_REFERENCE,
SUSPICIOUS_REFUNDS, EXCESSIVE_DISPUTES, EXCESSIVE_PROVIDER_CANCELLATIONS, REPEATED_AUTHORIZATION_FAILURES
(20+ per hour).

## 23. Deprecated legacy endpoints (spec section 83)

Kept at the root for the current WaDii-KMP client. They call the same services as the new API (same rules, JWT
identity, `userId` ignored) and return the new DTOs inside the envelope. Responses carry `Deprecation: true` and
`Link: <successor>; rel="successor-version"`.

| Legacy | Successor | Request |
|---|---|---|
| POST `/orders/insert` | POST `/api/v1/orders` | `{carModelYear, comment, date, latitude, longitude, address?, servicesIds, providerId?, spareParts:[{sparePartName}], carTypeId?}` |
| POST `/orders/cancel` | POST `/api/v1/orders/{id}/cancel` | `{orderId, reasonId?, reason?}` |
| GET `/responses/get-all-response-of-user?page=&size=` | GET `/api/v1/responses/mine` | returns a plain list (one page) |
| POST `/responses/insert` | POST `/api/v1/responses` | `{orderId, comment, laborCost?, discount?, sparePartsPrice:[{id, price}], estimatedDurationMinutes?, expiresAt?}` |
| POST `/responses/update` | PATCH `/api/v1/responses/{id}` | `{id, comment?, laborCost?, discount?, sparePartsPrice?, ...}` |
| POST `/responses/accept-response` | POST `/api/v1/responses/{id}/accept` | `{responseId}` (or `id`; optional `orderId` must match) |
| POST `/responses/cancel-response` | POST `/api/v1/responses/{id}/decline` | `{responseId}` |

All other old endpoints (`/users/show/{id}`, `/providers/update-all/{id}`, `/messages/*`, `/provider-requests/*`,
`/agora/*`, `/livekit/*`, `/presence/*`, `/rate/*`, base CRUD `/show-all`, etc.) were removed.

## 24. Scheduled jobs

| Job | Schedule | Effect |
|---|---|---|
| Offer expiry | every `wadii.jobs.expiration-interval-ms` (60 s) | PENDING offers with `expires_at <= now` -> EXPIRED; customer and provider notified; order falls back to QUOTING if no pending offer remains |
| Order deadline | same | awaiting orders past `response_deadline` -> EXPIRED, pending offers EXPIRED, invitations EXPIRED |
| Payment expiry | same | PENDING payments older than 72 h -> EXPIRED |
| Auto-complete | same (if `auto-complete-hours` > 0) | IN_PROGRESS orders marked done for 72 h and fully paid -> COMPLETED |
| Rematch | every `wadii.jobs.rematch-interval-ms` (10 min) | MATCHING orders retry matching |
| Reliability stats | every `wadii.jobs.stats-interval-ms` (1 h) | provider_stats / customer_stats recomputed; provider-cancellation fraud check |
| Warranty expiry | daily 00:15 | ACTIVE warranties past end date -> EXPIRED |
| Advertisement expiry | every 15 min | ACTIVE ads past `endAt` -> EXPIRED |

Every item is processed in its own transaction under the order row lock, so jobs and user actions cannot race.

## 25. Configuration (`wadii.*`)

| Property | Default |
|---|---|
| `wadii.timezone` / `wadii.currency` | `Africa/Cairo` / `EGP` |
| `wadii.order.response-window-hours` | 48 |
| `wadii.order.max-targets` | 20 |
| `wadii.order.default-service-radius-km` | 50 |
| `wadii.order.auto-complete-hours` | 72 (0 = disabled) |
| `wadii.offer.default-validity-hours` / `min-` / `max-` | 48 / 1 / 168 |
| `wadii.payment.pending-ttl-hours` / `max-proofs-per-payment` | 72 / 5 |
| `wadii.no-show.cancel-order` / `refund-on-provider-no-show` / `grace-minutes` | true / true / 30 |
| `wadii.commission.percent` | 0 |
| `wadii.platform-policy.max-cancellation-fee-percent` / `max-no-show-fee-percent` | 50 / 50 |
| `wadii.platform-policy.min-warranty-days` / `min-refund-window-days` | 0 / 0 |
| `wadii.fraud.max-disputes-per-30-days` / `max-provider-cancellations-per-30-days` / `max-auth-failures-per-hour` | 5 / 5 / 20 |
| `wadii.fcm.enabled` / `project-id` / `credentials-path` (`FCM_CREDENTIALS_PATH`) | true / wadii-kmp / `src/main/wadii.json` |
| `wadii.storage.root` | `uploads` |
