# WaDii API Reference

Reference for the frontend. Base URL: `http://<host>:8080` (no path prefix). Updated for the security and order-flow rewrite described in `WaDii_Security_and_Order_Flow_Improvements.md` (phases P0, P1 and P2).

> **Breaking changes since the previous version** (frontend must adapt):
> - Failures now use real HTTP status codes (400/401/403/404/409/422/500). The old "HTTP 200 with `data: null`" responses are gone.
> - `password`, `token` and `fcmToken` are never part of a User object. Login and register add `token` to their own response.
> - `POST /auth/register` rejects `userType: 2` (admin). Admins come from the `ADMIN_EMAIL` / `ADMIN_PASSWORD` environment variables or from an existing admin.
> - `userId` / `providerId` in request bodies no longer decide who acts: the account always comes from the JWT (orders, ratings, responses, offers, branches, links, provider requests).
> - Response states are `PENDING | ACCEPTED | DECLINED | CANCELED`. Responses are never deleted. `accept-response` takes `{ "responseId": n }` (`id` still works).
> - Order statuses are `PENDING | RESPONSES_AVAILABLE | ACCEPTED | IN_PROGRESS | COMPLETED | CANCELED`. New endpoints: `POST /orders/start/{id}`, `POST /orders/complete/{id}`.
> - Chat, calls and presence work only between a customer and the provider of an accepted order. STOMP presence moved from `/topic/presence` to `/user/queue/presence`.
> - A rating needs the `orderId` of a completed order.
> - Provider ID/tax documents are private: fetch them with `GET /provider-requests/{id}/document/{type}` and a Bearer token.

## Conventions

### Response envelope
Every controller returns:

```json
{ "data": <T | null>, "message": "Done", "statusCode": 200, "timestamp": "2026-10-21T10:15:30.123" }
```

| HTTP | Meaning |
|---|---|
| 200 | Success |
| 400 | Validation error or malformed body |
| 401 | Missing/invalid credentials |
| 403 | Authenticated but not allowed (wrong role or not the owner) |
| 404 | Resource not found |
| 409 | Conflict with current state (duplicate, already accepted, order not open) |
| 422 | Business rule violation (provider does not offer the service, date in the past) |
| 500 | Unexpected server error (`"Internal server error"`) |

Errors keep the same envelope with `data: null` and an explanatory `message`. The one exception: the security layer (missing, expired or invalid token) answers with a plain `{"error": "..."}` body and status 401 or 403, not the envelope.

### Authentication
`POST /auth/login` or `POST /auth/register` returns the JWT in `data.token`. Send `Authorization: Bearer <token>` on every other request. Public (no token): `/auth/**`, `/ws/**`, `/web-socket/**`, `GET /countries/**`, `GET /provinces/**`, `GET /cities/**`, `GET /users/{filename}` (profile images only).

### Roles
`USER`, `PROVIDER`, `ADMIN`. The role is read from the database for the JWT's account, never from the request.

### Enums
- `AdvertisementStatus`: `ACTIVE | INACTIVE | EXPIRED`
- `OrderStatus`: `PENDING | RESPONSES_AVAILABLE | ACCEPTED | IN_PROGRESS | COMPLETED | CANCELED`
- `ResponsesState`: `PENDING | ACCEPTED | DECLINED | CANCELED`
- `CallType`: `AUDIO | VIDEO`, `CallStatus`: `RINGING | ACCEPTED | REJECTED | MISSED | ENDED | CANCELLED`

### Order lifecycle
1. Customer creates an order (optionally with `providerId`). The server invites the selected provider, or every active provider that offers **all** requested services, and notifies them (`NEW_ORDER`).
2. Invited providers send one response each. The order becomes `RESPONSES_AVAILABLE`.
3. The customer accepts exactly one response: it becomes `ACCEPTED`, all other pending responses `DECLINED`, the order `ACCEPTED` with `acceptedProviderId`.
4. Chat, calls and presence are now allowed between the two. The provider can start the job (`IN_PROGRESS`), the customer completes it (`COMPLETED`), then the customer can rate the provider.
5. The owner can cancel while the order is not `IN_PROGRESS` / `COMPLETED`; open and accepted responses become `CANCELED`.

### Notification types
`NEW_ORDER`, `NEW_RESPONSE`, `RESPONSE_ACCEPTED`, `RESPONSE_DECLINED`, `RESPONSE_REJECTED`, `ORDER_CANCELED`, `ORDER_STARTED`, `ORDER_COMPLETED`, `PROVIDER_ACCEPTED`, `PROVIDER_REJECTED`. Each is stored in the inbox first (with `orderId` when it is about an order) and then pushed by FCM.

### Advertisement lifecycle
`GET /advertisements/show-all` returns only ads that are `ACTIVE` and within their start/end date. A scheduler runs daily at 00:01 and sets ads to `ACTIVE` (today inside the range) or `EXPIRED`. Ads an admin sets to `INACTIVE` are never changed by the scheduler. Admins change status with `PUT /advertisements/status/{id}/{status}`.

---

## Shared notes: auth, users, catalog

### Envelope and errors
Every handled response (success or failure) is `{ "data", "message", "statusCode", "timestamp" }` and the HTTP status equals `statusCode`. On failure `data` is `null` (or `false` for some DELETE routes) and `message` explains why. Status codes used by these controllers:

| HTTP | When |
|---|---|
| 400 | Validation (missing/blank field, bad value, unknown FK id in body), malformed JSON, bad path/query type (e.g. unknown enum), missing `file` part |
| 401 | Missing/invalid/expired JWT, or wrong login credentials |
| 403 | Role or ownership check failed |
| 404 | Entity id not found |
| 409 | Duplicate (email, link) or a DB constraint violation (`"The request conflicts with existing data"`, e.g. deleting a row still referenced) |
| 413 | Upload larger than 5 MB (`"File is too large"`) |
| 422 | Defined in the handler, not used by the endpoints in this section |
| 500 | Unhandled error (`"Internal server error"`) |

The old "HTTP 200 with `data: null`" role quirk is gone: a failed role check is a real 403 (message e.g. `"You are not admin to do this action"`, `"You are not a provider to do this action"`).

Exception: failures raised by the security layer itself (no `Authorization` header, bad/expired token, route access denied before reaching a controller) are NOT in the envelope. They are plain `{ "error": "..." }` with HTTP 401 (or 403), e.g. `{"error":"There is no token in the header; it is required."}`, `{"error":"Token has expired, please login again."}`. Handle both shapes: read `statusCode`/`message` if present, else `error`.

### Auth
`Authorization: Bearer <token>` (token from `/auth/login` or `/auth/register`). Public: `/auth/**`, `/ws/**`, `/ws-native/**`, `/web-socket/**`, `GET /users/{filename}`, `GET /countries/**`, `GET /provinces/**`, `GET /cities/**`. Everything else needs a token. Roles come from the server (JWT subject = email); ids or roles sent by the client in a body are never trusted. A request is "admin only" / "provider only" when the endpoint says so.

### Base CRUD routes
Controllers extending the base class expose: `GET /show/{id}`, `POST /insert`, `POST /update`, `DELETE /delete/{id}`, `GET /show-all` under their base path. Update is `POST` with the `id` in the body.

### Shared entity shapes (JSON)
- **User** (returned by users endpoints and inside login/register):
  ```json
  { "id": 7, "firstName": "Ali", "lastName": "Hassan", "email": "ali@example.com",
    "image": "download.jpeg", "backgroundImage": "download.jpeg", "phone": "+201000000000",
    "role": "USER", "following": [], "provider": null, "orders": [],
    "city": { "id": 1, "name": "Nasr City", "province": { "id": 1, "name": "Cairo", "country": { "id": 1, "name": "Egypt" } } } }
  ```
  `password`, `token` and `fcmToken` are never present in User JSON (they are write-only/ignored). `rates` is not serialized. `role` is `"USER" | "PROVIDER" | "ADMIN"`. `image`/`backgroundImage` are file names; fetch via `GET /users/{filename}` (default `download.jpeg`). `provider` is non-null only on `/auth/login` for PROVIDER accounts. `login`/`register` responses are this object plus a `token` field (the JWT); no other endpoint returns the token.
- **Country** `{ id, name }`; **Province** `{ id, name, country }`; **City** `{ id, name, province }` (nested as above). Names are unique.
- **CarType** `{ id, name }`; **CommonSpareParts** `{ id, name }`; **Reason** `{ id, reason }`; **Service** `{ id, name, providers: [Provider...] }`; **SpareParts** `{ id, sparePartName, order }` (`order` is `null` for rows created via `/spare-parts/insert`).
- **Branch** `{ id, name, address, provider: {...provider without branches/services/rates/links/offers/followers/responses/user}, workTimes: [WorkTime...] }`.
- **WorkTime** `{ id, startTime, closeTime, day, branch }` (all strings; `branch` without its `workTimes`).
- **Links** `{ id, link }` (provider is not serialized).
- **Advertisement** `{ id, title, description, imageUrl, targetUrl, advertiserName, status, priority, impressions, clicks, startDate, endDate, createdAt, updatedAt }`; `status` is `ACTIVE | INACTIVE | EXPIRED`; dates are ISO local date-times without zone (`2026-10-21T10:00:00`).

### Uploads
Image uploads (`/users/upload-image`, `/users/upload-background-image`) are `multipart/form-data`, part name `file`, max 5 MB. Allowed: extension jpg/jpeg/png/gif/webp, `Content-Type` starting with `image/`, and the file's magic bytes must match an image (otherwise 400). The server stores it under a random name `<uuid>.<ext>` and returns that name in the user's `image`/`backgroundImage` (the original file name is discarded; do not build the name on the client).

### First admin
Admins cannot self-register. On startup the server creates one admin if env `ADMIN_EMAIL` and `ADMIN_PASSWORD` are set and no user with that email exists (existing non-admin accounts are not promoted). Further admins come from `POST /users/insert` with `userType: 2` (admin token).

---

## AuthController (/auth)
Public (no token).

### POST /auth/register
Auth: none. Body (`AuthRequest`, JSON):
| field | type | notes |
|---|---|---|
| firstName, lastName | string | optional |
| email | string | required, valid format, trimmed, must be unique |
| password | string | required, at least 6 chars; stored BCrypt-hashed |
| phone | string | optional |
| fcmToken | string | optional (null is stored as `""`) |
| cityId | number | required, must exist |
| userType | int | `0` = USER, `1` = PROVIDER. `2` is rejected with 403. Omitted = 0 |
| providerName | string | required (non-blank) when `userType` = 1; creates the Provider row (`rate` 0, `followersCount` 0) |

Example:
```json
{ "firstName":"Ali","lastName":"Hassan","email":"ali@example.com","password":"Secret123","phone":"+201000000000","fcmToken":"fcm-abc","cityId":1,"userType":0 }
```
Response 200: `data` = User plus `token`; `image`/`backgroundImage` = `"download.jpeg"`, `orders`/`following` = `[]`, `provider` = `null` (even for providers; call `/providers/...` or log in to get the provider profile). `message`: `"User registered successfully"` or `"provider registered successfully"`.
```json
{ "data": { "id": 7, "email": "ali@example.com", "role": "USER", "token": "eyJhbGciOi...", "orders": [], "following": [], "provider": null, "city": { "id": 1, "name": "Nasr City" } }, "message": "User registered successfully", "statusCode": 200, "timestamp": "2026-10-21T10:15:30.123" }
```
Errors:
- 403 `"Admin accounts cannot be created through registration"` (userType 2)
- 400 `"userType must be 0 (user) or 1 (provider)"`
- 400 `"A valid email is required"`
- 400 `"Password must be at least 6 characters"`
- 400 `"cityId is required"`
- 400 `"providerName is required for providers"`
- 400 `"this city id not exist with id = <cityId>"`
- 409 `"User already exists with this email = <email>"`

### POST /auth/login
Auth: none. Body: `{ "email": string, "password": string, "fcmToken": string? }`. A non-empty `fcmToken` replaces the stored one. A fresh JWT is issued on every login.
```json
{ "email": "ali@example.com", "password": "Secret123", "fcmToken": "fcm-abc" }
```
Response 200: `data` = User plus `token`. For PROVIDER accounts `data.provider` is the provider profile (`id`, `name`, `rate`, `followersCount`, ...); for others it is `null`. `message`: `"success"`.
Errors:
- 401 `"Invalid email or password"` (same message for unknown email and wrong password)

---

## UserController (/users)
Base CRUD plus extras. All routes need a token except `GET /users/{filename}`.

### GET /users/show/{id}
Auth: any logged-in user, but only for: their own id, an admin (any id), or a user connected to the caller through an accepted order (the other side of that order). Response: `data` = User, message `"Done"`.
Errors: 403 `"You are not allowed to view this user"` (also returned for ids that do not exist when the caller is not admin); 404 `"there is no user with this id : <id>"` (only reachable for self/admin).

### POST /users/insert
Auth: ADMIN only. Creates a USER or ADMIN (providers: `/providers/create-by-admin`). Body (`UserDto`):
| field | type | notes |
|---|---|---|
| email | string | required, unique |
| password | string | required, at least 6 chars; stored BCrypt-hashed |
| userType | number | required: `0` = USER, `2` = ADMIN (1 rejected) |
| firstName, lastName, phone | string | optional |
| cityId | number | optional, must exist if sent |

```json
{ "firstName":"Sara","lastName":"Admin","email":"sara@example.com","password":"Secret123","phone":"+201111111111","cityId":1,"userType":2 }
```
Response 200: `data` = User (`role` ADMIN or USER), message `"User saved successfully"`. No token is returned.
Errors: 403 not admin; 400 `"email is required"`, `"Password must be at least 6 characters"`, `"userType must be 0 (user) or 2 (admin); create providers with /providers/create-by-admin"`, `"this city id not exist with id = <id>"`; 409 `"User already exists with this email = <email>"`.

### POST /users/update
Auth: the user themself, or ADMIN for any user. Body (`UserDto`): `id` (required) plus any of `firstName`, `lastName`, `phone`, `cityId`, `fcmToken`. Only non-null fields change. `email`, `password`, `image` and role cannot be changed here. `fcmToken` is applied only when the caller updates their own account (silently ignored for an admin editing someone else).
```json
{ "id": 7, "firstName": "Ali", "phone": "+201000000001", "cityId": 2, "fcmToken": "fcm-new" }
```
Response 200: `data` = User, message `"User saved successfully"`.
Errors: 400 `"user id is required"`, `"this city id not exist with id = <id>"`; 403 `"You can only access your own account"`; 404 `"there is no user with this id : <id>"`.

### DELETE /users/delete/{id}
Auth: the user themself, or ADMIN. Response 200: `data` = `true`, message `"user deleted successfully"`.
Errors: 403 `"You can only access your own account"`; 404 `data=false`, `"there is no user with this id : <id>"`; 409 if the user is still referenced by other data.

### GET /users/show-all
Auth: ADMIN only. Response: `data` = `User[]` (all users), message `"All users"`. Error: 403.

### GET /users/me
Auth: any logged-in user. Response: `data` = caller's User, message `"Done"`. (No `token` field.)

### POST /users/upload-image
Auth: any logged-in user. `multipart/form-data`, part `file` (see Uploads). Sets the caller's `image` to a generated UUID name. Response: `data` = updated User, message `"File uploaded successfully"`.
Errors: 400 `"No file selected"`, `"Only jpg, jpeg, png, gif and webp images are allowed"`, `"The uploaded file must be an image"`, `"The uploaded file is not a valid image"`, or missing `file` part (`"Invalid request: ..."`); 413 too large; 500 `"Failed to save the uploaded file"`.

### POST /users/upload-background-image
Same as `/users/upload-image` but updates `backgroundImage`.

### GET /users/{id}/online
Auth: any logged-in user. Response: `data` = boolean, message `"Online status"`. Presence is only revealed for the caller's own id or users connected through an accepted order; otherwise `data` is `false` (never an error).

### GET /users/me/unread-count
Auth: any logged-in user. Response: `data` = number (unread chat messages for the caller), message `"Unread count"`.

### GET /users/{filename}
Auth: none. Streams a public image (profile/background) with its content type, `Content-Disposition: inline`, `X-Content-Type-Options: nosniff`. Not JSON on success. `filename` must contain a dot (e.g. `3f2a...c1.png`, `download.jpeg`).
Errors (enveloped): 404 `"File not found"` (also for legacy provider-document names of the form `<digits>_<name>`, which are never served here); 400 `"Invalid file name"`.

---

## AdvertisementController (/advertisements)
All routes need a token. Writes are ADMIN only; reads (including `track-*`) are open to any logged-in user.

### GET /advertisements/show/{id}
Response: `data` = Advertisement, `"Done"`. Error: 404 `"No advertisement found with id: <id>"`.

### POST /advertisements/insert
Auth: ADMIN. Body (`AdvertisementDto`):
| field | type | notes |
|---|---|---|
| title | string | required, non-blank |
| description, imageUrl, targetUrl, advertiserName | string | optional |
| status | `ACTIVE`/`INACTIVE`/`EXPIRED` | optional, defaults to `ACTIVE` |
| priority | integer | optional, must be >= 0 |
| startDate, endDate | ISO local date-time | optional; `startDate` must not be after `endDate` |

```json
{ "title":"Summer offer","description":"-20%","imageUrl":"banner.png","targetUrl":"https://example.com","advertiserName":"Acme","priority":5,"startDate":"2026-10-21T00:00:00","endDate":"2026-12-31T23:59:59" }
```
Response 200: `data` = Advertisement (`impressions` 0, `clicks` 0, `createdAt`/`updatedAt` set), message `"Advertisement created successfully"`.
Errors: 400 `"Advertisement title is required"`, `"startDate must not be after endDate"`, `"priority must be zero or greater"`; 403 `"You are not admin to do this action"`; 401 `"User is not exist"`.

### POST /advertisements/update
Auth: ADMIN. Body: `id` (required) plus any field above; only non-null fields change; the schedule rules are re-validated on the merged values. Response: `data` = Advertisement, `"Advertisement updated successfully"`.
Errors: 400 `"Advertisement id is required"`, schedule errors as above; 403; 404 `"No advertisement found with id: <id>"`.

### DELETE /advertisements/delete/{id}
Auth: ADMIN. Response: `data` = `true`, `"Advertisement deleted successfully"`. Errors: 403; 404 (`data=false`).

### GET /advertisements/show-all
Returns the currently active ads (same query as `/active`), message `"All advertisements"`.

### GET /advertisements/active
Response: `data` = `Advertisement[]` that are active now (status ACTIVE and within their date window), message `"Active advertisements"`.

### GET /advertisements/by-status/{status}
`status` in `ACTIVE | INACTIVE | EXPIRED`. Response: `data` = `Advertisement[]`, `"Advertisements by status"`. Error: 400 for an unknown status value.

### GET /advertisements/by-advertiser/{advertiserName}
Response: `data` = `Advertisement[]`, `"Advertisements by advertiser"`.

### PUT /advertisements/status/{id}/{status}
Auth: ADMIN. Response: `data` = Advertisement, `"Advertisement status updated"`. Errors: 400 bad status; 403; 404.

### POST /advertisements/track-impression/{id}
Increments `impressions`. Response: `data` = Advertisement, `"Impression tracked"`. Error: 404.

### POST /advertisements/track-click/{id}
Increments `clicks`. Response: `data` = Advertisement, `"Click tracked"`. Error: 404.

---

## BranchController (/branches)
Reads are open to any logged-in user. Writes are PROVIDER only and bound to the caller's own provider profile.

### GET /branches/show/{id}
Response: `data` = Branch, `"Done"`. Error: 404 `"there is no branch with this id : <id>"`.

### POST /branches/insert
Auth: PROVIDER. Body: `{ "name": string (required, non-blank), "address": string? }`. `providerId` in the body is ignored; the branch is created for the authenticated provider.
```json
{ "name": "Downtown branch", "address": "12 Tahrir St" }
```
Response 200: `data` = Branch, `"branch saved successfully"`.
Errors: 400 `"branch name is required"`; 403 `"You are not a provider to do this action"` / `"No provider profile found for this account"`.

### POST /branches/update
Auth: PROVIDER (owner). Body: `{ "id": number (required), "name"?: string, "address"?: string }`; non-null fields change. Response: `data` = Branch, `"branch updated successfully"`.
Errors: 400 `"branch id is required"`; 403 not a provider, or `"You can only manage your own branches"` (admins are NOT allowed here); 404 `"there is no branch with this id : <id>"`.

### DELETE /branches/delete/{id}
Auth: PROVIDER (owner). Response: `data` = `true`, `"branch deleted successfully"` (its work times are deleted too). Errors: 403 (not provider / not owner); 404 (`data=false`).

### GET /branches/show-all
Response: `data` = `Branch[]` (all providers), `"All branches"`.

### GET /branches/by-provider/{providerId}
Response: `data` = `Branch[]` of that provider (empty list if none), `"Branches for provider"`.

---

## CarTypesController (/car-types)
All routes need a token. Reads: any logged-in user. Writes: ADMIN only.

### GET /car-types/show/{id}
Response: `data` = CarType, `"Done"`. Error: 404 `"there is no car with this id : <id>"`.

### POST /car-types/insert
Auth: ADMIN. Body: `{ "name": string }` (required, non-blank, unique).
```json
{ "name": "Toyota Corolla" }
```
Response: `data` = CarType `{ "id": 4, "name": "Toyota Corolla" }`, `"Car saved successfully"`.
Errors: 400 `"name is empty"`; 403 `"You are not admin to do this action"`; 404 `"this name is exist <name>"` (duplicate is reported as 404, not 409).

### POST /car-types/update
Auth: ADMIN. Body: `{ "id": number, "name": string }` (name required). Response: `data` = CarType, `"Car updated successfully"`.
Errors: 400 `"name is empty"`; 403; 404 `"this car is not exist with id : <id>"`. (A duplicate name hits the unique constraint -> 409.)

### DELETE /car-types/delete/{id}
Auth: ADMIN. Response: `data` = `true`, `"car deleted successfully"`. Errors: 403; 404 (`data=false`); 409 if referenced.

### GET /car-types/show-all
Response: `data` = `CarType[]`, `"All cars"`.

---

## CityController (/cities)
`GET` routes are public (no token). Writes: ADMIN only.

### GET /cities/show/{id}
Response: `data` = City, `"Done"`. Error: 404 `"No city with id: <id>"`.

### POST /cities/insert
Auth: ADMIN. Body: `{ "name": string (required), "provinceId": number (required) }`.
```json
{ "name": "Nasr City", "provinceId": 1 }
```
Response: `data` = City `{ "id": 9, "name": "Nasr City", "province": {...} }`, `"City saved successfully"`.
Errors: 400 `"Name or provinceId is empty"`, `"City already exists: <name>"`; 403 `"You are not admin to do this action"`; 404 `"No province with id: <id>"`.

### POST /cities/update
Auth: ADMIN. Body: `{ "id", "name", "provinceId" }`, all required. Response: `data` = City, `"City updated successfully"`.
Errors: 400 `"Name or provinceId is empty"`; 403; 404 `"No city with id: <id>"` or `"No province with id: <id>"`.

### DELETE /cities/delete/{id}
Auth: ADMIN. Response: `data` = `true`, `"City deleted successfully"`. Errors: 403; 404 (`data=false`); 409 if users reference the city.

### GET /cities/show-all
Public. Response: `data` = `City[]`, `"All cities"`.

### GET /cities/by-province/{provinceId}
Public. Response: `data` = `City[]` (empty list if none), `"Cities by province"`. Use this to fill the city picker on the register form.

---

## CountryController (/countries)
`GET` routes are public. Writes: ADMIN only.

### GET /countries/show/{id}
Response: `data` = Country, `"Done"`. Error: 404 `"No country with id: <id>"`.

### POST /countries/insert
Auth: ADMIN. Body: `{ "name": string }` (required, unique). Response: `data` = Country, `"Country saved successfully"`.
Errors: 400 `"Name is empty"`, `"Country already exists: <name>"`; 403 `"You are not admin to do this action"`.

### POST /countries/update
Auth: ADMIN. Body: `{ "id", "name" }`. Response: `data` = Country, `"Country updated successfully"`.
Errors: 400 `"Name is empty"`; 403; 404 `"No country with id: <id>"`.

### DELETE /countries/delete/{id}
Auth: ADMIN. Response: `data` = `true`, `"Country deleted successfully"`. Errors: 403; 404 (`data=false`); 409 if provinces reference it.

### GET /countries/show-all
Public. Response: `data` = `Country[]`, `"All countries"`.

---

## ProvinceController (/provinces)
`GET` routes are public. Writes are now ADMIN only (previously any logged-in user).

### GET /provinces/show/{id}
Response: `data` = Province, `"Done"`. Error: 404 `"No province with id: <id>"`.

### POST /provinces/insert
Auth: ADMIN. Body: `{ "name": string, "countryId": number }`, both required.
```json
{ "name": "Cairo", "countryId": 1 }
```
Response: `data` = Province, `"Province saved successfully"`.
Errors: 400 `"Name or countryId is empty"`, `"Province already exists: <name>"`; 403 `"You are not admin to do this action"`; 404 `"No country with id: <id>"`.

### POST /provinces/update
Auth: ADMIN. Body: `{ "id", "name", "countryId" }`, all required. Response: `data` = Province, `"Province updated successfully"`.
Errors: 400 `"Name or country Id is empty"`; 403; 404 `"No province with id: <id>"` or `"No country with id: <id>"`.

### DELETE /provinces/delete/{id}
Auth: ADMIN. Response: `data` = `true`, `"Province deleted successfully"`. Errors: 403; 404 (`data=false`); 409 if cities reference it.

### GET /provinces/show-all
Public. Response: `data` = `Province[]`, `"All provinces"`.

### GET /provinces/by-country/{countryId}
Public. Response: `data` = `Province[]` (empty if none), `"Provinces by country"`.

---

## CommonSparePartsController (/common-spare-parts)
All routes need a token. Reads: any logged-in user. Writes: ADMIN only.

### GET /common-spare-parts/show/{id}
Response: `data` = CommonSpareParts, `"Done"`. Error: 404 `"there is no spare part with this id : <id>"`.

### POST /common-spare-parts/insert
Auth: ADMIN. Body: `{ "name": string }` (required, unique). Response: `data` = `{ "id", "name" }`, `"Spare part saved successfully"`.
Errors: 400 `"name is empty"`, `"there is already a spare part with this name : <name>"`; 403 `"You are not admin to do this action"`.

### POST /common-spare-parts/update
Auth: ADMIN. Body: `{ "id", "name" }`. Response: `data` = CommonSpareParts, `"Spare part updated successfully"`.
Errors: 400 `"name is empty"`; 403; 404 `"there is no spare part with this id : <id>"`.

### DELETE /common-spare-parts/delete/{id}
Auth: ADMIN. Response: `data` = `true`, `"Spare part deleted successfully"`. Errors: 403; 404 (`data=false`).

### GET /common-spare-parts/show-all
Response: `data` = `CommonSpareParts[]`, `"All spare parts"`.

---

## LinksController (/links)
All routes need a token. Reads: any logged-in user. Writes: PROVIDER only, bound to the caller's own provider profile.

### GET /links/show/{id}
Response: `data` = Links `{ "id", "link" }`, `"Done"`. Error: 404 `"there is no link with this id : <id>"`.

### POST /links/insert
Auth: PROVIDER. Body: `{ "link": string }` (required, non-blank, globally unique). `providerId` in the body is ignored.
```json
{ "link": "https://facebook.com/my-garage" }
```
Response: `data` = Links, `"Link saved successfully"`.
Errors: 400 `"link is required"`; 403 not a provider; 409 `"this link is already exist : <link>"`.

### POST /links/update
Auth: PROVIDER (owner). Body: `{ "id": number, "link": string }`, both required. Response: `data` = Links, `"Link updated successfully"`.
Errors: 400 `"link id is required"` / `"link is required"`; 403 not a provider or `"You can only manage your own links"`; 404 `"there is no link with this id : <id>"`; 409 duplicate link used by another row.

### DELETE /links/delete/{id}
Auth: PROVIDER (owner). Response: `data` = `true`, `"link deleted successfully"`. Errors: 403; 404 (`data=false`).

### GET /links/show-all
Response: `data` = `Links[]` (all providers' links, without owner info), `"All Links"`.

---

## ServiceController (/services)
All routes need a token. Reads: any logged-in user. Writes: ADMIN only.

### GET /services/show/{id}
Response: `data` = Service `{ "id", "name", "providers": [...] }`, `"Done"`. Error: 404 `"there is no service with this id : <id>"`.

### POST /services/insert
Auth: ADMIN. Body: `{ "name": string }` (required, unique). Response: `data` = Service, `"Service saved successfully"`.
Errors: 400 `"name is empty"`; 403 `"You are not admin to do this action"`; 404 `"there is a service with this name : <name>"` (duplicate is reported as 404).

### POST /services/update
Auth: ADMIN. Body: `{ "id", "name" }`. Response: `data` = Service, `"Service updated successfully"`.
Errors: 400 `"name is empty"`; 403; 404 (message says `"there is a service with this name : <name>"` although the id was not found).

### DELETE /services/delete/{id}
Auth: ADMIN. Response: `data` = `true`, `"service deleted successfully"`. Errors: 403; 404 (`data=false`); 409 if referenced.

### GET /services/show-all
Response: `data` = `Service[]`, `"All services"`.

---

## SparePartsController (/spare-parts)
All routes need a token. Reads: any logged-in user. Writes are now ADMIN only. (Customer-facing spare parts are created through orders, not here.)

### GET /spare-parts/show/{id}
Response: `data` = SpareParts, `"Done"`. Error: 404 `"there is no spare part with this id : <id>"`.

### POST /spare-parts/insert
Auth: ADMIN. Body: `{ "sparePartName": string }`. Response: `data` = SpareParts, `"Saved successfully"`.
Errors: 403 `"You are not admin to do this action"`. Quirk: a blank name returns HTTP 200 with `data: null` and `message: "name is empty"`. The table requires an order, so inserting a bare spare part usually fails with 409/500 from the database; do not use this from the app.

### POST /spare-parts/update
Auth: ADMIN. Body: `{ "id", "sparePartName" }`. Response: `data` = SpareParts, `"updated successfully"`.
Errors: 403; 404 `"there is no spare part with this id : <id>"`. Quirk: blank name returns HTTP 200, `data: null`, `"name is empty"`.

### DELETE /spare-parts/delete/{id}
Auth: ADMIN. Response: `data` = `true`, message `"car deleted successfully"` (sic). Errors: 403; 404 (`data=false`).

### GET /spare-parts/show-all
Response: `data` = `SpareParts[]`, `"All spare parts"`.

---

## WorkTimeController (/work-time)
All routes need a token. Reads: any logged-in user. Writes: PROVIDER only, and only on branches the caller owns.

### GET /work-time/show/{id}
Response: `data` = WorkTime, `"Done"`. Error: 404 `"there is no time with this id : <id>"`.

### POST /work-time/insert
Auth: PROVIDER. Body (`WorkTimeDto`): `branchId` (number), `startTime`, `closeTime`, `day` (non-blank strings; the format is not validated, e.g. `"09:00"`, `"17:00"`, `"Monday"`); all four required.
```json
{ "branchId": 12, "day": "Monday", "startTime": "09:00", "closeTime": "17:00" }
```
Response: `data` = WorkTime, `"Work time saved successfully"`.
Errors: 400 `"branchId, startTime, closeTime and day are required"`; 403 not a provider, or `"You can only manage your own branches"`; 404 `"there is no branch with this id : <id>"`.

### POST /work-time/update
Auth: PROVIDER (owner). Body: `id` plus all of `branchId`, `startTime`, `closeTime`, `day` (full replace; `branchId` may move the entry to another of the caller's own branches). Response: `data` = WorkTime, `"Work time updated successfully"`.
Errors: 400 `"work time id is required"` / required-fields message; 403 `"You can only manage your own work times"` or `"You can only manage your own branches"`; 404 work time or branch not found.

### DELETE /work-time/delete/{id}
Auth: PROVIDER (owner). Response: `data` = `true`, `"work time deleted successfully"`. Errors: 403; 404 (`data=false`).

### GET /work-time/show-all
Response: `data` = `WorkTime[]` (all providers), `"All times"`.

### POST /work-time/get-all-work-time/{branchId}
Read-only despite being POST; no body. Response: `data` = `WorkTime[]` for that branch (empty if none), `"All times"`.

---

## ReasonController (/reasons)
All routes need a token. Reads: any logged-in user. Writes are now ADMIN only.

### GET /reasons/show/{id}
Response: `data` = Reason `{ "id", "reason" }`, `"Done"`. Error: 404 `"there is no reason with this id : <id>"`.

### POST /reasons/insert
Auth: ADMIN. Body: `{ "reason": string }` (required, non-blank, trimmed). Response: `data` = Reason, `"Reason saved successfully"`.
```json
{ "reason": "Price too high" }
```
Errors: 400 `"reason is required"`; 403 `"You are not admin to do this action"`.

### POST /reasons/update
Auth: ADMIN. Body: `{ "id": number, "reason"?: string }` (blank/null reason leaves it unchanged). Response: `data` = Reason, `"Reason updated successfully"`.
Errors: 400 `"reason id is required"`; 403; 404 `"there is no reason with this id : <id>"`.

### DELETE /reasons/delete/{id}
Auth: ADMIN. Response: `data` = `true`, `"reason deleted successfully"`. Errors: 403; 404 (`data=false`); 409 if referenced.

### GET /reasons/show-all
Response: `data` = `Reason[]`, `"All reasons"`.

---

## SearchController (/search)

### GET /search?q={text}
Auth: any logged-in user. Query param `q` (required, non-blank; trimmed). Case-insensitive contains-match over offers, services, providers (by name) and branches (name or address). For offers, `saved` is set per the caller's saved offers.
Response: `data` = `{ "offers": Offer[], "services": Service[], "providers": Provider[], "branches": Branch[] }` (each possibly empty), message `"Search results"`.
```json
{ "data": { "offers": [], "services": [ { "id": 2, "name": "Oil change", "providers": [] } ], "providers": [], "branches": [] }, "message": "Search results", "statusCode": 200, "timestamp": "2026-10-21T10:15:30.123" }
```
Errors: 400 `"Search query is required"` (blank `q`) or `"Invalid request: ..."` when `q` is missing entirely; 401 without a token.


---

## Shared notes: providers, offers, rates

All endpoints below require `Authorization: Bearer <jwt>` (anything not listed as public is authenticated). Every result, success or failure, uses the envelope `{data, message, statusCode, timestamp}` with the real HTTP status (400/401/403/404/409/422/500). On any non-2xx result `data` is `null` (including for Boolean endpoints). Role/ownership failures are 403, not 200.

Common error statuses used here: 401 (no/invalid user), 403 (wrong role or not the owner), 404 (id not found), 400 (validation, bad enum/JSON/date, missing required param), 409 (duplicate / state conflict, also DB integrity violations: `The request conflicts with existing data`), 413 (upload too large), 500 `Internal server error`.

Base-controller routes (`/providers`, `/offers`, `/saved-offers`, `/rate`) are `GET /show/{id}`, `POST /insert`, `POST /update`, `DELETE /delete/{id}`, `GET /show-all`.

**User** (nested in Provider/Rate/SavedOffer/ProviderRequest): `{ id, firstName, lastName, email, image, backgroundImage, phone, role, city{id,name,province{...}} }`. `password`, `token`, `fcmToken` are never serialized. Depending on parent, `provider`, `orders`, `following`, `rates` are omitted. Inside Rate the user also omits `email` and `phone`.

**Provider**
```json
{
  "id": 3, "rate": 4.5, "followersCount": 12, "name": "Fast Motors",
  "active": true, "acceptingOrders": true,
  "branches": [ { "id": 7, "name": "Main", "address": "12 Nile St",
      "workTimes": [ { "id": 21, "startTime": "09:00", "closeTime": "17:00", "day": "Monday" } ] } ],
  "services": [ { "id": 2, "name": "Oil change" } ],
  "rates": [ { "id": 5, "comment": "Great", "rate": 5.0 } ],
  "links": [ { "id": 4, "link": "https://facebook.com/fastmotors" } ],
  "offers": [ { "id": 9, "title": "10% off", "description": "All week", "endDate": "2026-12-31",
      "services": [ { "id": 2, "name": "Oil change" } ], "saved": false } ],
  "user": { "id": 15, "firstName": "Ali", "lastName": "Hassan", "email": "ali@x.com",
            "image": "download.jpeg", "backgroundImage": "download.jpeg", "phone": "0100000000", "role": "PROVIDER",
            "city": { "id": 1, "name": "Cairo", "province": { "id": 1, "name": "Cairo Gov." } } }
}
```
`active` (admin suspension) and `acceptingOrders` (provider's own switch) are both default `true`; a provider receives new orders only if both are not false. Omitted: `followers`, `responses`; inside `branches`/`links`/`offers` the `provider`; inside `rates` the `provider` and `user`; inside `services` the `providers`/`orders`. Lists may be `[]`.

**Offer**: `{ "id": 9, "title", "description", "endDate": "yyyy-MM-dd", "provider": <Provider without offers>, "services": [{id,name}], "saved": boolean }`. `saved` is meaningful only on `/offers/show-all` and the two filter endpoints (true if the caller saved it); elsewhere `false`.

**Rate**: `{ "id": 5, "comment": "Great", "rate": 4.5, "provider": <Provider without rates,responses,followers,offers,links,services>, "user": <User without rates,orders,following,provider,email,phone> }`. The order link is not serialized.

**SavedOffer**: `{ "id": 1, "user": <User>, "offer": <Offer> }`.

**Follower**: `{ "id": {"userId": 15, "providerId": 3}, "user": <User>, "provider": <Provider without followers,workTimes,services,offers,rates,links> }`.

**ProviderRequest**: `{ "id": 1, "name", "frontIdImage": "uuid.jpg"|null, "backIdImage", "taxCardFront", "taxCardBack", "address", "phoneNumber", "services": [{id,name}], "links": ["https://..."], "user": <User>, "requestedAt": "2026-10-21T12:00:00" }`. The image fields are generated private filenames (`<uuid>.<ext>`); they are not URLs. Fetch the image through `GET /provider-requests/{id}/document/{type}`.

---

## ProviderController (/providers)

### GET /providers/show/{id}
- Auth: any valid token. Path `id` (Long).
- Response: Provider, message `Done`.
- Errors: 404 `there is no provider with this id : 3`.

### GET /providers/show-all
- Auth: any valid token. Response: `Provider[]`, message `All providers`.

### GET /providers/me
- Auth: any valid token. Provider profile of the caller.
- Response: Provider, message `Done`.
- Errors: 401 (caller not found); 404 `No provider found for this user`.

### GET /providers/filter-by-service/{serviceId}
- Auth: any valid token. Response: `Provider[]` (empty if none), message `Providers filtered by service`.

### GET /providers/filter-by-services
- Auth: any valid token. Query `serviceIds` (required, repeated or comma-separated), e.g. `?serviceIds=1,2,3`.
- Response: `Provider[]`, message `Providers filtered by services`.
- Errors: 400 if `serviceIds` is missing or not numeric.

### GET /providers/{id}/followers
- Auth: the provider's own user, or ADMIN. Path `id` = provider id.
- Response: `Follower[]`, message `Followers list`.
- Errors: 404 `No provider found with id: X`; 403 `You can only manage your own provider profile`.

### POST /providers/insert
- Auth: ADMIN only.
- Body (ProviderDto): `{ "userId": 15 }` (required; other fields ignored). Creates a Provider for an existing user (name = user's first+last name, rate 0, followersCount 0) and sets the user's role to PROVIDER. Prefer `/providers/create-by-admin`.
- Response: Provider, message `provider saved successfully`.
- Errors: 403 not admin; 400 `userId is required`; 404 `there is no user with this id : X`; 409 `This user is already a provider`.

### DELETE /providers/delete/{id}
- Auth: ADMIN only. Response: `data: true`, message `provider deleted successfully`.
- Errors: 403 not admin; 404 `there is no provider with this id : X` (`data` null).

### POST /providers/set-active/{id}/{active}
- Auth: ADMIN only. Path `id` = provider id, `active` = `true` or `false`. No body. `false` suspends the provider (no new orders); `true` reactivates.
- Example: `POST /providers/set-active/3/false`
- Response: Provider, message `Provider suspended` or `Provider activated`.
- Errors: 403 not admin; 404 `No provider found with id: X`; 400 if `active` is not a boolean.

### POST /providers/availability
- Auth: PROVIDER (the caller's own profile; no id in the request).
- Body: `{ "acceptingOrders": true }` (Boolean, required). `false` pauses new orders without suspension.
- Response: Provider, message `Availability updated`.
- Errors: 403 `You are not a provider to do this action` (role not PROVIDER) or `No provider profile found for this account`; 400 `acceptingOrders is required`.

### POST /providers/update
- Auth: the provider's owner or ADMIN.
- Body (ProviderDto): `id` (Long, required). Everything else optional, applied only when non-null:
  - ADMIN only (silently ignored for a provider caller): `userId` (reassign owner), `rate` (Double), `followersCount` (Long).
  - `branches`: `[{ "id"?: Long, "name": string (required when new), "address"?: string, "workTimes"?: [{ "id"?: Long, "day": string, "startTime": string, "closeTime": string }] }]`. Upsert. A workTime without id is matched by branch + day.
  - `services`: `[{ "id": Long }]` replaces the provider's service set.
  - `links`: `[{ "id"?: Long, "link": string }]` upsert.
  - `offers`: `[{ "id"?: Long, "title", "description", "endDate": "yyyy-MM-dd" }]` upsert.
  - Nested ids that do not exist create a new row; nested ids that exist must belong to this provider (else 403).
  - `rates`, `followers`, `responses` are ignored.
- Example:
```json
{ "id": 3,
  "branches": [ { "id": 7, "name": "Main", "address": "12 Nile St", "workTimes": [ { "day": "Monday", "startTime": "09:00", "closeTime": "17:00" } ] } ],
  "services": [ { "id": 2 }, { "id": 5 } ],
  "links": [ { "link": "https://fb.com/fm" } ],
  "offers": [ { "title": "10% off", "description": "All week", "endDate": "2026-12-31" } ] }
```
- Response: Provider, message `provider updated successfully`.
- Errors: 400 `provider id is required` | `branch name is required` | `One or more service ids do not exist` | bad date/JSON; 404 `there is no provider with this id : X` | `there is no user with this id : X`; 403 `You can only manage your own provider profile` | `Branch|Work time|Link|Offer N does not belong to this provider`.

### POST /providers/update-services
- Auth: the provider's owner or ADMIN.
- Body (ServicesProviderDto): `{ "providerId": 3, "serviceIds": [2, 5] }`. Replaces the provider's services (null/missing `serviceIds` clears them).
- Response: Provider, message `Services updated successfully`.
- Errors: 400 `providerId is required` | `One or more service ids do not exist`; 404 `No provider found with id: X`; 403 not owner/admin.

### POST /providers/update-all/{id}
- Auth: the provider's owner or ADMIN. Path `id` = provider id.
- Body (UpdateProviderDto, all optional). Nested `id` fields are plain ints: `0`/omitted = create new, `>0` = update that row (must belong to this provider, else 403; a non-existent id creates a new row).
```json
{ "firstName": "Ali", "lastName": "Hassan", "phone": "0100000000", "email": "ali@x.com",
  "serviceIds": [2, 5],
  "branches": [ { "id": 0, "name": "Second", "address": "5 Mall St",
                  "workTimes": [ { "id": 0, "day": "Sunday", "startTime": "10:00", "closeTime": "16:00" } ] } ],
  "links": [ { "id": 0, "link": "https://instagram.com/fm" } ],
  "offers": [ { "id": 0, "title": "Winter", "description": "Deal", "endDate": "2026-12-31" } ] }
```
  User fields (`firstName`, `lastName`, `phone`, `email`) update the provider's linked user. `serviceIds` replaces services. `endDate` is a string `yyyy-MM-dd`.
- Response: Provider, message `Provider updated successfully`.
- Errors: 404 `No provider found with id: X`; 403 not owner/admin or nested id not owned; 400 `email must not be blank` | `One or more service ids do not exist`; 409 `User already exists with this email = X`; 500 on an unparseable `endDate`.

### POST /providers/follow-provider/{id}
- Auth: any valid token; follower = caller. Path `id` = provider id. No body.
- Response: Follower, message `Done`. Increments `followersCount`.
- Errors: 404 `provider id not exist`; 409 `you are already following this provider`.

### DELETE /providers/unfollow-provider/{id}
- Auth: any valid token. Path `id` = provider id.
- Response: `data: true`, message `unfollowed successfully`; decrements `followersCount` (min 0).
- Errors: 404 `provider id not exist` | `you are not following this provider`.

### POST /providers/create-by-admin
- Auth: ADMIN only.
- Body (CreateProviderDto):
  - Required: `email` (string), `password` (string, min 6 chars), `providerName` (string).
  - Optional: `firstName`, `lastName`, `phone`, `cityId` (Long, must exist), `rate` (Double, default 0), `serviceIds` (Long[]), `branches`: `[{ "name": string (required), "address", "workTimes": [{ "day", "startTime", "closeTime" }] }]`, `links`: `[{ "link" }]`, `offers`: `[{ "title", "description", "endDate": "yyyy-MM-dd" }]`.
- Example:
```json
{ "firstName": "Ali", "lastName": "Hassan", "email": "fast@motors.com", "password": "Secret123", "phone": "0100000000", "cityId": 1,
  "providerName": "Fast Motors", "rate": 0, "serviceIds": [2],
  "branches": [ { "name": "Main", "address": "12 Nile St", "workTimes": [ { "day": "Monday", "startTime": "09:00", "closeTime": "17:00" } ] } ],
  "links": [ { "link": "https://fb.com/fm" } ],
  "offers": [ { "title": "10% off", "description": "All week", "endDate": "2026-12-31" } ] }
```
- Creates a user with role PROVIDER (password BCrypt-hashed, default images `download.jpeg`, no token issued; the provider logs in via `/auth/login`) and the Provider.
- Response: Provider, message `provider created successfully`.
- Errors: 403 not admin; 400 `email is required` | `Password must be at least 6 characters` | `providerName is required` | `this city id not exist with id = X` | `branch name is required` | `One or more service ids do not exist`; 409 `User already exists with this email = X`.

---

## ProviderRequestController (/provider-requests)
Does not extend the base controller; only the routes below exist.

### POST /provider-requests/request
- Auth: any valid token except ADMIN. The applicant is always the caller; a `userId` form field is ignored.
- Content-Type: `multipart/form-data`:
  - `name` (string, required)
  - `address`, `phoneNumber` (string, optional)
  - `serviceIds` (Long, repeat per id: `serviceIds=1&serviceIds=2`)
  - `links` (string, repeat per link)
  - Files (optional): `frontIdImage`, `backIdImage`, `taxCardFront`, `taxCardBack`. Only jpg/jpeg/png/gif/webp images (extension, `image/*` content type and file header are checked). Stored privately as `<uuid>.<ext>`; never publicly served.
- Example: `curl -H "Authorization: Bearer $T" -F name="Fast Motors" -F address="12 Nile St" -F phoneNumber=0100000000 -F serviceIds=2 -F serviceIds=5 -F links=https://fb.com/fm -F frontIdImage=@front.jpg -F backIdImage=@back.jpg http://host:8080/provider-requests/request`
- Response: ProviderRequest, message `Request submitted successfully`.
- Errors: 403 `Admins cannot apply to become providers`; 409 `This user is already a provider` | `A request already exists for this user`; 400 `name is required` | `Only jpg, jpeg, png, gif and webp images are allowed` | `The uploaded file must be an image` | `The uploaded file is not a valid image`; 404 `One or more service IDs are invalid`; 413 `File is too large`.

### GET /provider-requests/show-all
- Auth: ADMIN only. Response: `ProviderRequest[]`, message `All provider requests`.
- Errors: 403.

### GET /provider-requests/{id}/document/{type}
- Auth: ADMIN or the applicant of that request. Path `id` = request id; `type` = `front-id` | `back-id` | `tax-front` | `tax-back`.
- Response: NOT the JSON envelope on success: the raw image bytes with the image `Content-Type`, `Content-Disposition: inline`, `Cache-Control: private, no-store`. Because it needs the Bearer header, fetch it with `fetch`/axios as a blob and use `URL.createObjectURL`; a plain `<img src>` will get 401.
- Errors (envelope JSON): 404 `No request found with id: X` | `This document was not uploaded` | `File not found`; 403 `You are not allowed to access this document`; 400 `type must be front-id, back-id, tax-front or tax-back`.

### POST /provider-requests/accept/{id}
- Auth: ADMIN only. Path `id` = request id. No body.
- Effect: creates the Provider (name from the request, rate 0, followersCount 0, links and services copied), sets the user's role to PROVIDER, deletes the request, and notifies the user (inbox entry + push if they have an FCM token): type `PROVIDER_ACCEPTED`, title `Provider Request Accepted`.
- Response: Provider, message `Request accepted, provider created successfully`.
- Errors: 403 not admin; 404 `No request found with id: X`; 409 `This user is already a provider`.

### POST /provider-requests/reject/{id}
- Auth: ADMIN only. Path `id`; query `reason` (string, optional, appended to the notification text).
- Effect: deletes the request and notifies the user (inbox + push): type `PROVIDER_REJECTED`, title `Provider Request Rejected`.
- Response: the deleted ProviderRequest, message `Request rejected successfully`.
- Errors: 403 not admin; 404 `No request found with id: X`.

### POST /provider-requests/put-it-user/{userId}
- Auth: ADMIN only. Path `userId`. Sets the user's role to USER (the provider row is kept).
- Response: User, message `Provider reverted to user successfully`.
- Errors: 403; 404 `No user found with id: X`; 400 `User is not a provider`.

### POST /provider-requests/put-it-provider/{userId}
- Auth: ADMIN only. Path `userId`. If the user (role USER) already has a provider row, sets role PROVIDER.
- Response: User, message `User promoted to provider successfully`.
- If no provider row exists: HTTP 200 with `data` = User (role unchanged) and message `you can't put it provider because there is no id of provider binding with it`. Compare `data.role` or the message to detect this.
- Errors: 403; 404 `No user found with id: X`; 400 `User is not a User`.

---

## OffersController (/offers)

### GET /offers/show/{id}
- Auth: any valid token. Response: Offer (`saved` false), message `Done`.
- Errors: 404 `there is no offer with this id : X`.

### POST /offers/insert
- Auth: PROVIDER. The offer always belongs to the caller's provider; `providerId` in the body is ignored.
- Body (OfferDto): `title` (string, required non-blank), `description` (string, required non-blank), `endDate` (`"yyyy-MM-dd"`, required), `serviceIds` (Long[], optional).
- Example: `{ "title": "10% off", "description": "All week", "endDate": "2026-12-31", "serviceIds": [2, 5] }`
- Response: Offer, message `Offer saved successfully`.
- Errors: 403 `You are not a provider to do this action`; 400 `title, description and endDate are required` | `One or more service ids do not exist` | bad date format.

### POST /offers/update
- Auth: PROVIDER who owns the offer.
- Body (OfferDto): `id` (required); `title`, `description`, `endDate` optional and applied only when non-null; `serviceIds` applied only when non-empty (replaces the set). `providerId` is ignored (offers cannot be moved).
- Response: Offer, message `Offer updated successfully`.
- Errors: 403 not a provider | `You can only manage your own offers`; 400 `offer id is required` | `One or more service ids do not exist`; 404 `there is no offer with this id : X`.

### DELETE /offers/delete/{id}
- Auth: PROVIDER who owns the offer. Response: `true`, message `offer deleted successfully`.
- Errors: 403 not a provider | `You can only manage your own offers`; 404 `there is no offer with this id : X` (`data` null).

### GET /offers/show-all
- Auth: any valid token. Response: `Offer[]` with `saved` set for the caller, message `All offers`.

### GET /offers/filter-by-service/{serviceId}
- Auth: any valid token. Response: `Offer[]` (with `saved`), message `Offers filtered by service`.

### GET /offers/filter-by-services
- Auth: any valid token. Query `serviceIds` (required list, e.g. `?serviceIds=1,2`).
- Response: `Offer[]` (with `saved`), message `Offers filtered by services`.
- Errors: 400 if missing/not numeric.

---

## SavedOfferController (/saved-offers)

### GET /saved-offers/show/{id}
- Auth: the saved offer's owner or ADMIN. `id` = SavedOffer id (not offer id).
- Response: SavedOffer, message `Done`.
- Errors: 404 `No saved offer found with id: X`; 403 `You can only access your own account`.

### POST /saved-offers/insert
- Auth: any valid token; user = caller.
- Body (SavedOfferDto): `{ "offerId": 9 }` (required).
- Response: SavedOffer, message `Offer saved successfully`.
- Errors: 404 `User not found` | `No offer found with id: X`; 400 `Offer already saved`; 500 if `offerId` is missing.

### POST /saved-offers/update
- Always 400 `Not supported`.

### DELETE /saved-offers/delete/{id}
- Auth: the saved offer's owner or ADMIN. `id` = SavedOffer id. Response `true`, message `Saved offer removed successfully`.
- Errors: 404 `No saved offer found with id: X` (`data` null); 403 `You can only access your own account`.

### GET /saved-offers/show-all
- Auth: ADMIN only. Returns all users' saved offers: `SavedOffer[]`, message `All saved offers`.
- Errors: 403 `You are not admin to do this action`.

### GET /saved-offers/my-saved-offers
- Auth: any valid token. Response: `SavedOffer[]` for the caller, message `Saved offers`.
- Errors: 404 `User not found`.

### DELETE /saved-offers/remove/{offerId}
- Auth: any valid token. Path `offerId` = Offer id; removes the caller's save. Response `true`, message `Saved offer removed successfully`.
- Errors: 404 `User not found` | `Offer is not saved` (`data` null).

---

## RateController (/rate)

### GET /rate/show/{id}
- Auth: any valid token. Response: Rate, message `Done`.
- Errors: 404 `there is no rate with this id : X`.

### POST /rate/insert
- Auth: any valid token (must be the customer of the order). The rating user comes from the JWT (`userId` ignored) and the provider from the order.
- Body (RateDto): `orderId` (Long, required; must be the caller's order with status COMPLETED), `rate` (number, required, 1 to 5 inclusive), `comment` (string, optional, max 2000 chars), `providerId` (Long, optional; if sent must equal the order's provider).
- One rating per order. After saving, the provider's `rate` is recalculated as the average of all their rates.
- Example: `{ "orderId": 42, "rate": 4.5, "comment": "Great service" }`
- Response: Rate, message `Rate saved successfully`.
- Errors: 400 `rate must be between 1 and 5` (also when `rate` is omitted) | `comment is too long` | `orderId is required` | `providerId does not match the provider of this order`; 404 `there is no order with this id : X`; 403 `You can only rate providers of your own orders`; 409 `You can rate a provider only after the order is completed` | `You already rated this order`.

### POST /rate/update
- Auth: the rate's author.
- Body (RateDto): `id` (required), `rate` (1 to 5, required), `comment` (optional, max 2000). Other fields ignored. Provider average is recalculated.
- Response: Rate, message `Rate saved successfully`.
- Errors: 400 `rate id is required` | `rate must be between 1 and 5` | `comment is too long`; 404 `Rate with this id = X not exist`; 403 `You can only update rates you created`.

### DELETE /rate/delete/{id}
- Auth: the rate's author or ADMIN. Provider average is recalculated.
- Response: `true`, message `rate deleted successfully`.
- Errors: 404 `there is no rate with this id : X` (`data` null); 403 `You can only delete rates you created`.

### GET /rate/show-all
- Auth: any valid token. Response: `Rate[]`, message `All rates`.


---

## Shared notes: orders, responses, messaging

Every call below returns the envelope `{data, message, statusCode, timestamp}`. Failures use the real HTTP status, with `data: null` and the same `statusCode` in the body:

| HTTP | When |
|---|---|
| 400 | missing or invalid field, malformed JSON, bad param type |
| 401 | no, expired or invalid JWT (the security filter itself answers with `{"error": "..."}`, not the envelope), or the token's user no longer exists |
| 403 | wrong role, not the owner/participant, not invited, no accepted order between two users |
| 404 | entity not found |
| 409 | state conflict (order not open, already responded, already accepted, wrong status for the action) |
| 422 | semantically invalid (date in the past, direct provider cannot take the order) |
| 500 | unexpected error, message `"Internal server error"` |

Error example: `HTTP 409  {"data":null,"message":"You already responded to this order","statusCode":409,"timestamp":"2026-10-21T10:15:30.123"}`

Roles: `USER` (customer), `PROVIDER`, `ADMIN`. "Customer" below means any authenticated user acting as the order owner. Provider-only endpoints need role PROVIDER and an existing provider profile (otherwise 403).

### Enums
- `OrderStatus`: `PENDING` (created, no response yet), `RESPONSES_AVAILABLE` (at least one provider responded), `ACCEPTED` (customer picked a response), `IN_PROGRESS` (provider started), `COMPLETED`, `CANCELED`. An order is "open" (providers may respond, customer may accept or decline) only in `PENDING` or `RESPONSES_AVAILABLE`.
- `ResponsesState`: `PENDING`, `ACCEPTED`, `DECLINED` (customer chose another one or declined it), `CANCELED` (provider withdrew, or the order was canceled). Responses are never deleted by these flows.
- Order-to-provider invitation (`order_provider_targets`, internal, not returned in JSON): `TARGETED`, `VIEWED`, `RESPONDED`, `DECLINED`, `EXPIRED`.

### Order JSON
```json
{
  "id": 12, "carModelYear": "Toyota Camry 2018", "comment": "Brakes and oil", "date": "2026-11-02",
  "latitude": 24.7136, "longitude": 46.6753,
  "status": "RESPONSES_AVAILABLE",
  "providerId": null,
  "acceptedProviderId": null,
  "acceptedResponseId": null,
  "user": { "id": 7, "firstName": "...", "...": "..." },
  "services": [ { "id": 2, "name": "Brakes" } ],
  "spareParts": [ { "id": 31, "sparePartName": "Brake pad" } ],
  "responses": [ { "...Response JSON..." } ]
}
```
- `providerId`: the provider the customer picked directly, `null` when the order was matched by services.
- `acceptedProviderId` / `acceptedResponseId`: set once a response is accepted, else `null`.
- `spareParts[].id` is what a provider references when pricing.
- `responses`: all responses for the owner/admin; only the caller's own response (0 or 1 item) for a provider.

### Response JSON
```json
{
  "id": 40, "comment": "I can do it tomorrow", "latitude": 24.7, "longitude": 46.6,
  "provider": { "id": 3, "...": "provider fields" },
  "sparePartsPrices": [ { "id": 90, "price": 120.50, "sparePart": { "id": 31, "sparePartName": "Brake pad" } } ],
  "responsesState": "PENDING",
  "totalPrice": 120.50,
  "createdAt": "2026-10-21T10:15:30.123", "updatedAt": "2026-10-21T10:15:30.123"
}
```
`totalPrice` (BigDecimal, 2 decimals) is always computed by the server as the sum of `sparePartsPrices[].price` (0 when no prices); a client-sent total is not accepted. Inside an Order the response has no `order` field.

### Message JSON
`{ "id": 5, "text": "hello", "type": "text", "createdAt": "...", "isRead": false, "fromUser": {User}, "toUser": {User} }` (User without password/token/fcmToken). Allowed `type` values: `text`, `image`, `audio`.

### UserNotification JSON
`{ "id": 1, "title": "New order", "body": "A new order #12 matches your services.", "type": "NEW_ORDER", "isRead": false, "orderId": 12, "createdAt": "...", "user": {User} }`

Notification types written by the order flow (always stored in the inbox first, then sent as an FCM push when the user has an FCM token; a push failure never fails the request): `NEW_ORDER` (to invited providers), `NEW_RESPONSE` (to the customer), `RESPONSE_ACCEPTED`, `RESPONSE_DECLINED` (other pending responses when one is accepted), `RESPONSE_REJECTED` (customer declined that one response), `ORDER_CANCELED` (to providers with a pending/accepted response), `ORDER_STARTED` (to the customer), `ORDER_COMPLETED` (to the accepted provider). `PROVIDER_ACCEPTED` and `PROVIDER_REJECTED` come from the provider-request approval flow. Notifications never contain customer contact details, only the order reference (`orderId`).

### Flow summary
1. Customer `POST /orders/insert` (status `PENDING`). Backend invites providers and sends `NEW_ORDER`.
2. Invited providers `POST /responses/insert` (once each). First response moves the order to `RESPONSES_AVAILABLE`; customer gets `NEW_RESPONSE`.
3. Customer `POST /responses/accept-response`. One response `ACCEPTED`, the others `DECLINED`, order `ACCEPTED`. Chat, calls and presence now work between customer and that provider.
4. Provider `POST /orders/start/{id}` (`IN_PROGRESS`), customer `POST /orders/complete/{id}` (`COMPLETED`, enables rating).
5. At any point before `IN_PROGRESS` the customer may `POST /orders/cancel`.

---

## OrderController (/orders)

### GET /orders/show/{id}
- Auth: any authenticated user, but only these may see the order: the owner, an admin, a provider invited to it (targeted or direct), or the accepted provider. Everyone else gets 403.
- Path: `id` (Long).
- Response `data`: Order. For a provider, `responses` contains only that provider's own response. The first time an invited provider opens it, their invitation moves `TARGETED` to `VIEWED` (internal).
- Example: `GET /orders/show/12` returns `{"data":{Order},"message":"Done","statusCode":200,"timestamp":"..."}`
- Errors: 404 `"there is no order with this id : 12"`; 403 `"You are not allowed to access this order"`; 400 non-numeric id.

### GET /orders/show-all
- Auth: ADMIN only.
- Response `data`: `Order[]`, message `"All orders"`.
- Errors: 403 `"You are not admin to do this action"`.

### GET /orders/show-all-order-of-user
- Auth: any authenticated user. Returns the orders the caller created (all responses visible).
- Response `data`: `Order[]` (empty array when none; message `"There is no orders there"`, otherwise `"All orders"`).

### GET /orders/show-all-order-of-provider
- Auth: PROVIDER. Returns every order the provider was invited to (selected directly or matched by services), each with only the provider's own response.
- Response `data`: `Order[]`, message `"All orders for provider"`.
- Errors: 403 not a provider.

### POST /orders/insert
- Auth: any authenticated user. The owner is always the JWT user; `userId` in the body is ignored.
- Body `OrderDto` (JSON):

| field | type | notes |
|---|---|---|
| carModelYear | string | required, not blank |
| date | string `yyyy-MM-dd` | required; may not be earlier than yesterday (1 day tolerance) |
| servicesIds | long[] | required, non-empty; duplicates ignored; every id must exist |
| comment | string | optional |
| latitude | number | optional, -90..90 |
| longitude | number | optional, -180..180 |
| spareParts | `[{"sparePartName": string}]` | optional; each name must be non-blank |
| providerId | long | optional: send the order only to this provider |
| userId | long | ignored |

- Provider targeting:
  - With `providerId`: the provider must exist (404), their account must have role PROVIDER, not be the caller, be active and accepting orders, and offer ALL requested services (all failures 422). Only that provider is invited and notified.
  - Without `providerId`: every active, order-accepting provider (other than the caller) that offers ALL requested services is invited (rows in `order_provider_targets`) and gets a `NEW_ORDER` inbox notification + push. Providers who offer only some of the services are not invited. It is valid for nobody to match; the order stays `PENDING`.
- Example request:
```json
{
  "carModelYear": "Toyota Camry 2018", "comment": "Need brake pads and oil change",
  "date": "2026-11-02", "latitude": 24.7136, "longitude": 46.6753,
  "servicesIds": [2, 5],
  "spareParts": [{"sparePartName": "Brake pad"}, {"sparePartName": "Oil filter"}],
  "providerId": 3
}
```
- Response `data`: the saved Order with `status: "PENDING"`, `providerId` (3 or null), `spareParts` with their new ids, message `"Order saved successfully"`. Note `responses` is empty/null on this response.
- Errors: 400 `"carModelYear is required"`, `"date is required"`, `"servicesIds must not be empty"`, `"One or more service ids do not exist"`, `"latitude must be between -90 and 90"`, `"sparePartName must not be blank"`; 422 `"date cannot be in the past"`, `"The selected account is not a provider"`, `"The selected provider is not accepting orders"`, `"The selected provider does not offer the service: Brakes"`, `"You cannot send an order to your own provider profile"`; 404 unknown `providerId`.

### POST /orders/update
- Auth: the order owner only. Allowed only while the order is `PENDING` (no response yet).
- Body `OrderDto`: `id` required; every other field is optional and applied only if non-null (`carModelYear`, `comment`, `date`, `latitude`, `longitude`, same validation as insert). `servicesIds` (non-empty) replaces the services; `spareParts` (non-empty) replaces all spare parts (new spare part ids are created, so re-read them before pricing). `providerId` and `userId` are ignored.
- If `servicesIds` changes, the old invitations are removed and the matching is run again, so newly matching providers get `NEW_ORDER`. For a direct order the chosen provider must still offer all new services (422).
- Example: `{"id": 12, "comment": "Updated", "servicesIds": [2], "spareParts": [{"sparePartName": "Air filter"}]}`
- Response `data`: Order, message `"Order updated successfully"`.
- Errors: 400 `"order id is required"` + the insert validations; 404 order; 403 `"You can only manage orders you created"`; 409 `"Only orders without responses can be edited; cancel the order and create a new one"` (status is not `PENDING`); 422 as in insert.

### DELETE /orders/delete/{id}
- Auth: the owner or an ADMIN. Allowed only when the status is `PENDING` or `CANCELED`.
- Effect: removes the order with its invitations, cancel record, spare parts and any stored responses.
- Response `data`: `true`, message `"order deleted successfully"`.
- Errors: 404 order; 403 `"You can only manage orders you created"`; 409 `"Only pending or canceled orders can be deleted"` (status `RESPONSES_AVAILABLE`, `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`: cancel first where allowed); 409 `"An order with an accepted response cannot be deleted"`.

### POST /orders/cancel
- Auth: the order owner only.
- Body (`OrderCancelRequest`): `{"orderId": 12, "reasonId": 1}` (both required; `reasonId` must exist in `/reasons`).
- Allowed from `PENDING`, `RESPONSES_AVAILABLE`, `ACCEPTED`. Sets the order to `CANCELED` and stores the reason. Every response of the order that is `PENDING` or `ACCEPTED` becomes `CANCELED` (kept as history) and that provider gets an `ORDER_CANCELED` notification. `DECLINED`/`CANCELED` responses are untouched.
- Response `data`: Order (status `CANCELED`), message `"Order canceled successfully"`.
- Errors: 400 `"order id is required"`, `"reason id is required"`; 404 order or `"there is no reason with this id : 9"`; 403 not the owner; 409 `"The order is already canceled"`, 409 `"An order that is IN_PROGRESS cannot be canceled"` (also `COMPLETED`).

### POST /orders/start/{id}
- Auth: PROVIDER who is the order's accepted provider. No body.
- Transition `ACCEPTED` to `IN_PROGRESS`; the customer gets `ORDER_STARTED`.
- Response `data`: Order, message `"Order started"`.
- Errors: 403 not a provider, or `"Only the accepted provider can start this order"`; 404 order; 409 `"Only an ACCEPTED order can be started"`.

### POST /orders/complete/{id}
- Auth: the order owner (customer). No body.
- Transition `ACCEPTED` or `IN_PROGRESS` to `COMPLETED` (the customer may complete without the provider having started); the accepted provider gets `ORDER_COMPLETED`. Completing is what enables `POST /rate/insert` for the order.
- Response `data`: Order, message `"Order completed"`.
- Errors: 404 order; 403 `"You can only manage orders you created"`; 409 `"Only an ACCEPTED or IN_PROGRESS order can be completed"`.

---

## ResponseController (/responses)

A response is a provider's offer on an order. One provider can respond to an order at most once (database-unique, 409 on a second try, even after cancel/decline).

### GET /responses/show/{id}
- Auth: the order owner, the provider who wrote it, or an ADMIN.
- Response `data`: Response, message `"Done"`.
- Errors: 404 `"there is no response with this id : 40"`; 403 `"You are not allowed to access this response"`.

### GET /responses/show-all
- Auth: ADMIN only. Response `data`: `Response[]`. Errors: 403.

### GET /responses/get-all-response-of-user
- Auth: any authenticated user. Returns the responses (every state, including `DECLINED`/`CANCELED`) to the orders the caller created.
- Response `data`: `Response[]`, message `"All responses"`.

### POST /responses/insert
- Auth: PROVIDER. The provider is always the JWT user; `providerId` in the body is ignored.
- Preconditions: the provider is active and accepting orders (403), was invited to this order (403 `"You were not invited to respond to this order"`), the order is open, i.e. `PENDING` or `RESPONSES_AVAILABLE` (409), and has not responded before (409).
- Body `ResponseDto`:

| field | type | notes |
|---|---|---|
| orderId | long | required |
| comment | string | required, not blank (trimmed) |
| latitude, longitude | number | optional |
| sparePartsPrice | `[{"id": long, "price": number}]` | optional; `id` is a spare part id of THAT order (`order.spareParts[].id`), each at most once, `price` >= 0 |

- Example:
```json
{ "orderId": 12, "comment": "I can do it tomorrow", "latitude": 24.7, "longitude": 46.6,
  "sparePartsPrice": [ {"id": 31, "price": 120.50}, {"id": 32, "price": 35} ] }
```
- Effect: response created as `PENDING` with `totalPrice` = 155.50 (server computed); order `PENDING` becomes `RESPONSES_AVAILABLE`; customer gets `NEW_RESPONSE`.
- Response `data`: Response, message `"Response saved successfully"`.
- Errors: 400 `"orderId is required"`, `"comment is required"`, `"Spare part 99 does not belong to this order"`, `"Spare part 31 is priced more than once"`, `"price must be zero or greater"`; 404 order; 403 not a provider / not eligible / not invited; 409 `"This order is no longer open for responses"`, `"You already responded to this order"`.

### POST /responses/update
- Auth: PROVIDER who wrote the response. Allowed only while the response is `PENDING` and the order is open.
- Body `ResponseDto`: `id` required; `comment`, `latitude`, `longitude` optional (applied if non-null; blank comment is 400); `sparePartsPrice` replaces all prices when non-empty (same validation) and `totalPrice` is recomputed.
- Response `data`: Response, message `"Response updated successfully"`.
- Errors: 400 `"response id is required"`, price/comment validation; 404; 403 `"You can only update responses you created"`; 409 `"Only a pending response on an open order can be updated"`.

### DELETE /responses/delete/{id}
- Auth: PROVIDER who wrote it. Withdraws a `PENDING` response: it is not deleted, its state becomes `CANCELED`. The provider cannot respond to the same order again. The order status is not changed.
- Response `data`: `true`, message `"response canceled successfully"`.
- Errors: 404; 403 `"You can only cancel responses you created"`; 409 `"Only a pending response can be canceled"`.

### POST /responses/accept-response
- Auth: the owner of the response's order. Runs in one transaction with a row lock on the order, so concurrent accepts produce exactly one winner (the others get 409).
- Body: `{"responseId": 40}` (`id` is accepted as a fallback key).
- Effect: chosen response `ACCEPTED` (provider gets `RESPONSE_ACCEPTED`); every other `PENDING` response `DECLINED` (each provider gets `RESPONSE_DECLINED`); order `ACCEPTED` with `acceptedProviderId` and `acceptedResponseId` set. From now on customer and provider can chat and call.
- Response `data`: the accepted Response, message `"Response accepted"`.
- Errors: 400 `"responseId is required"`; 404 response; 403 `"You can only manage responses to your own orders"`; 409 `"This order is not open: it is ACCEPTED"`, `"A response was already accepted for this order"`, `"Only a pending response can be accepted"`.

### POST /responses/cancel-response
- Auth: the owner of the response's order. Declines one response without choosing another; the order stays open.
- Body: `{"responseId": 40}` (`id` as fallback).
- Effect: response `DECLINED` (kept as history); the provider gets `RESPONSE_REJECTED`. The provider cannot respond again to that order.
- Response `data`: Response (state `DECLINED`), message `"Response rejected"`.
- Errors: 400 `"responseId is required"`; 404; 403 not the order owner; 409 `"Only a pending response on an open order can be declined"`.

---

## MessageController (/messages)

Messages can only be sent between a customer and the provider of an accepted order (order status `ACCEPTED`, `IN_PROGRESS` or `COMPLETED`, either direction). Otherwise 403 `"You can only communicate with a user connected to you through an accepted order"`. Sending to yourself is also 403.

### GET /messages/show/{id}
- Auth: the sender, the receiver, or an ADMIN.
- Response `data`: Message. Errors: 404 `"there is no message with this id : 5"`; 403 `"You are not allowed to read this message"`.

### GET /messages/show-all
- Auth: ADMIN only. Response `data`: `Message[]`. Errors: 403.

### POST /messages/insert
- Auth: any authenticated user (sender = JWT user).
- Body `MessageDto`: `{"toUserId": 9, "text": "hello", "type": "text"}`; all three required and non-blank; `type` must be `text`, `image` or `audio` (case-insensitive check; stored as sent). For image/audio, `text` carries the file URL/reference.
- Effect: saves the message, updates both users' chat-list entries, pushes the Message to both users on STOMP `/user/queue/messages` (not to raw `/web-socket` clients), and sends an FCM push to the recipient if they have a token.
- Response `data`: Message, message `"Message sent successfully"`.
- Errors: 400 `"text, type, or toUserId is empty"`, `"type must be text, image or audio"`; 404 `"there is no user with this id : 9"`; 403 no accepted order between the two users.

### POST /messages/update
- Auth: the sender only. Body `{"id": 5, "text": "edited", "type": "text"}` (`id` required; `text`/`type` applied if non-blank; no re-validation of `type`).
- Response `data`: Message, message `"Message updated successfully"`.
- Errors: 400 `"message id is required"`; 404; 403 `"You can only update messages you created"`.

### DELETE /messages/delete/{id}
- Auth: the sender only. Hard delete. Response `data`: `true`, message `"message deleted successfully"`.
- Errors: 404 (`data: null`); 403 `"You can only delete messages you created"`.

### GET /messages/sent and GET /messages/received
- Auth: any authenticated user. `Message[]` newest first (messages the caller sent / received). Messages: `"Sent messages"` / `"Received messages"`.

### GET /messages/conversation/{userId}
- Auth: any authenticated user. Query: `page` (default 0), `size` (default 20).
- Response `data`: Spring `Page<Message>` (`content`, `totalElements`, `totalPages`, `number`, `size`, `last`, ...), only messages exchanged between the caller and `userId`. This read endpoint itself does not check the accepted-order relation (old history is still readable, an unrelated user simply returns an empty page).
- Errors: 404 `"there is no user with this id : 9"`.

### GET /messages/chat-list
- Auth: any authenticated user. Response `data`: `ChatContact[]` newest first: `{ "id", "user", "contact": {User}, "lastMessage", "messageType", "lastMessageAt" }`. Message `"Chat list"`.

---

## UserNotificationController (/notifications)

### GET /notifications/show/{id}
- Auth: the notification's owner or an ADMIN. Response `data`: UserNotification.
- Errors: 404 `"there is no notification with this id : 1"`; 403.

### POST /notifications/insert
- Auth: ADMIN only. Body `{"userId": 7, "title": "...", "body": "...", "type": "INFO"}` (`userId`, `title`, `body` required; `type` free text). Creates an inbox row only (no push).
- Response `data`: UserNotification, message `"Notification sent successfully"`.
- Errors: 403; 400 `"title, body, or userId is empty"`; 404 unknown user.

### POST /notifications/update
- Auth: ADMIN only. Body `{"id": 1, "title"?, "body"?, "type"?}`; non-blank fields are applied.
- Errors: 403; 400 `"notification id is required"`; 404.

### DELETE /notifications/delete/{id}
- Auth: the owner or an ADMIN. Response `data`: `true`, message `"notification deleted successfully"`. Errors: 404 (`data: null`); 403.

### GET /notifications/show-all
- Auth: ADMIN only. Response `data`: `UserNotification[]`.

### GET /notifications/my
- Auth: any authenticated user. The caller's inbox, newest first. Response `data`: `UserNotification[]`. Order notifications carry `orderId`.

### GET /notifications/my/unread
- Auth: any authenticated user. Only unread (`isRead: false`) items, newest first.

### GET /notifications/my/unread-count
- Auth: any authenticated user. Response `data`: number, e.g. `3`.

### POST /notifications/mark-read/{id}
- Auth: the notification's owner (admins cannot mark others' notifications). No body.
- Response `data`: UserNotification with `isRead: true`, message `"Notification marked as read"`.
- Errors: 404; 403 `"You can only mark your own notifications as read"`.

### POST /notifications/mark-all-read
- Auth: any authenticated user. No body. Response `data`: the `UserNotification[]` that were just marked read.

### POST /notifications/test-send/{userId}
- Auth: ADMIN only. Sends a dummy FCM push to the user.
- Response `data`: string (FCM result), message `"Test notification sent"`.
- Errors: 403; 404 unknown user; 400 `"user has no fcm token registered"`; 500 `"Failed to send test notification..."` on FCM failure.

---

## AgoraController (/agora)

Call endpoints require an accepted-order relation between the two users (same rule as chat). The channel name is built by the client from the two user ids, sorted ascending and joined with `_` (e.g. users 3 and 7 give `"3_7"`); the caller must be one of the two ids and the two users must be connected, otherwise 403.

### POST /agora/token
- Auth: any authenticated user.
- Body: `{"channelName": "3_7", "uid": 3}`; `uid` must equal the caller's user id (int).
- Response `data`: `{"token": "<rtc token>", "appId": "<agora app id>"}`, message `"Agora token generated"`.
- Errors: 400 `"channelName is required"`; 403 `"uid does not match the authenticated user"`, `"you are not a member of this channel"` (bad format, not your id, or no accepted order); 404 user.

### POST /agora/reject
- Auth: any authenticated user. Rejects an incoming call by relaying `CALL_REJECT` to the caller (raw socket, or FCM push when the caller has no open socket).
- Body (`CallSignal`): `{"channelName": "3_7", "toUserId": 3, "callType": "AUDIO", "fromUserName": "...", "fromUserImage": "...", "provider": "AGORA"}`. `fromUserId` is overwritten with the caller's id. `channelName` and `toUserId` are required.
- Response `data`: `null`, message `"Call rejected"`.
- Errors: 400 `"channelName is required"` / `"toUserId is required"`; 403 `"you are not a member of this channel"`; 404 user.

---

## LiveKitController (/livekit)

Same room rule as Agora: `roomName` = the two user ids sorted and joined with `_`; caller must be a member and connected through an accepted order.

### POST /livekit/token
- Auth: any authenticated user.
- Body: `{"roomName": "3_7", "identity": "3", "participantName": "Ahmed"}`; `identity` must equal the caller's user id as a string; `participantName` is the display name.
- Response `data`: `{"url": "wss://<livekit host>", "token": "<jwt>"}`, message `"LiveKit token generated"`.
- Errors: 400 `"roomName is required"`; 403 `"identity does not match the authenticated user"`, `"you are not a member of this room"`; 404 user.

---

## PresenceController (/presence)

Presence is only visible between a customer and the provider of an accepted order. Any other user always shows as offline, and you never receive presence events for unrelated users.

### GET /presence?userIds=1,2,3
- Auth: any authenticated user. Query: `userIds` comma-separated longs (required).
- Response `data`: map `userId` to boolean (JSON keys are strings): `{"1": true, "2": false}`. A user is `true` only if online and (a counterpart of the caller through an accepted order, or the caller). Message `"Presence status"`.
- Errors: 400 missing/invalid `userIds`.

### GET /presence/online
- Auth: any authenticated user. Response `data`: array of user ids that are currently online among the caller's counterparts, e.g. `[9, 14]`. Message `"Online users"`. Use it for the initial snapshot; sockets send no snapshot on connect.

---

## WebSocket / Real-time

Two socket flavors share one presence registry and one `userId` identity. Both require the JWT in the query string at handshake: `?token=<JWT>`. A missing or invalid token (or, for the raw socket, a `{userId}` in the path that differs from the token's user id) refuses the handshake (no upgrade). The server identifies the user from the token only.

| Client | URL | Protocol |
|---|---|---|
| Web (browser) | `ws(s)://host/ws?token=JWT` (SockJS) | STOMP |
| Mobile / plain | `ws(s)://host/web-socket/{userId}?token=JWT` | raw JSON text frames |

There is no `/ws-native` endpoint registered (only `/ws` for STOMP). The two flavors do not see each other's chat traffic, so use one consistently per client: `POST /messages/insert` delivers to STOMP queues only; a message sent on the raw socket is delivered only to raw sockets.

All chat, typing, read-receipt, call and presence traffic is limited to a customer and the provider of an accepted order (status `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`).

### STOMP (`/ws`)
- Broker prefixes: `/queue`, `/topic`; application prefix `/app`; user prefix `/user`.
- Send (STOMP SEND):

| Destination | Payload | Behavior |
|---|---|---|
| `/app/chat.send` | `{"toUserId": 9, "text": "hello", "type": "text"}` (`type` defaults to `text`) | validates, saves, delivers the saved Message to both users on `/user/queue/messages` |
| `/app/chat.typing` | `{"toUserId": 9, "typing": true}` | relays to the other user; silently dropped if not connected through an accepted order |
| `/app/chat.read` | `{"messageId": 5}` | marks a message addressed to you as read and notifies the sender; ignored if the message is not yours or already read |

- Subscribe:

| Destination | Payload |
|---|---|
| `/user/queue/messages` | Message JSON (also fed by `POST /messages/insert`) |
| `/user/queue/typing` | `{"fromUserId": 9, "typing": true}` |
| `/user/queue/read-receipts` | `{"messageId": 5, "readBy": 9}` |
| `/user/queue/presence` | `{"userId": 9, "online": true}` |
| `/user/queue/errors` | `{"code": 403, "message": "You can only chat with a user connected to you through an accepted order"}` |

- Presence moved: it used to be broadcast on `/topic/presence` to everyone. It is now delivered only on `/user/queue/presence`, and only for counterparts. Do not subscribe to `/topic/presence` any more. Events fire when a user's first session connects (`online: true`) and when their last session closes (`online: false`); use `GET /presence/online` for the initial state.
- Errors on `/app/chat.send` arrive on `/user/queue/errors` as `{code, message}`: `400` (blank text or type not `text|image|audio`) and `403` (no accepted order). The frame is not saved or delivered.

### Raw socket (`/web-socket/{userId}`)
Frames are JSON text. Client to server:
- Chat message (no `event` key): `{"toUserId": 9, "text": "hello", "type": "text"}`.
- Call signal: `{"event": "CALL_INVITE" | "CALL_ACCEPT" | "CALL_REJECT" | "CALL_END", "data": CallSignal}` with `CallSignal = {"channelName": "3_7", "callType": "VIDEO"|"AUDIO", "toUserId": 9, "fromUserName": "...", "fromUserImage": "...", "provider": "AGORA"|"LIVEKIT"}`. `fromUserId` is overwritten by the server with the authenticated user. The channel name is not checked here, only that the two users are connected through an accepted order. If a `CALL_INVITE` cannot be delivered because the callee has no open socket, the server falls back to an FCM data push so the call still rings.
- There is no typing or read-receipt event on the raw socket.

Server to client, always `{"event": "...", "data": ...}`:

| event | data |
|---|---|
| `MESSAGE` | Message JSON (sent to the recipient and echoed to the sender) |
| `PRESENCE` | `{"userId": 9, "online": true}`, only for counterparts, on first connect and last disconnect |
| `CALL_INVITE`, `CALL_ACCEPT`, `CALL_REJECT`, `CALL_END` | the `CallSignal` (also produced by `POST /agora/reject` as `CALL_REJECT`) |
| `ERROR` | `{"code": 400 \| 403, "message": "..."}` for a rejected chat frame (400: blank text or bad type; 403: no accepted order) or call signal (403) |

A user may hold several sockets at once (e.g. chat plus call); they are all kept and all receive each event. Presence is online while at least one STOMP or raw session is open.

### Typical call setup
1. Both users are connected through an accepted order.
2. Caller: `POST /agora/token` (or `/livekit/token`) for room `"{minId}_{maxId}"`, then send `CALL_INVITE` over the raw socket.
3. Callee receives `CALL_INVITE` (socket or push), fetches its own token for the same room, joins, and sends `CALL_ACCEPT`; or calls `POST /agora/reject` / sends `CALL_REJECT`.
4. Either side sends `CALL_END` when finished.


---

