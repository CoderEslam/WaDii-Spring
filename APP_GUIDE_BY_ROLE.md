# WaDii – Application Guide by Role

WaDii is a car-services marketplace. **Users** post a request for car work (an *order*: which services and spare parts they need, where, and when). **Providers** (workshops / service centres) answer with a priced *response*. The user accepts the best response and the two chat or call each other. **Admins** run the platform: reference data, provider approvals, advertisements and user management.

For exact request/response bodies see [`API_REFERENCE.md`](API_REFERENCE.md). This document explains **what each role can do and how the pieces fit together**.

---

## 1. Core concepts

| Concept | Meaning |
|---|---|
| **User** | An account (name, email, phone, city, profile and background image, FCM token for push). Has one role. |
| **Role** | `USER` (0), `PROVIDER` (1), `ADMIN` (2). Stored on the account and checked by the server. |
| **Provider** | The business profile attached to a `PROVIDER` account: name, rating, followers count, branches, services, links, offers. |
| **Branch / WorkTime** | A provider's physical location, with opening hours per day. |
| **Service** | A kind of work (e.g. "Oil change"). Providers choose which services they offer; orders request services. |
| **Spare part** | A part named on an order. Common spare parts are an admin-managed catalogue. |
| **Order** | A user's request: car model/year, comment, date, location, services and spare parts. Status `PENDING` or `CANCELED`. |
| **Response** | A provider's reply to an order: comment, location, and a price per requested spare part. State `PENDING`, `ACCEPT` or `CANCEL`. |
| **Offer** | A promotion a provider publishes (title, description, end date, services). Users can save offers. |
| **Rate** | A user's star rating and comment for a provider. |
| **Advertisement** | A banner ad managed by admins, with a date range and a status. |
| **Message / Chat** | One-to-one chat between any two users, over REST and WebSocket. |
| **Notification** | In-app inbox entries plus FCM push notifications. |
| **Call** | Audio/video calls between two users using Agora or LiveKit, signalled over WebSocket. |
| **Location data** | Country → Province → City hierarchy, used at registration. |

### Authentication in one minute
1. `POST /auth/register` or `POST /auth/login` returns the user with a JWT in `data.token`.
2. Send `Authorization: Bearer <token>` on every other request.
3. Public without a token: `/auth/**`, `GET /countries`, `/provinces`, `/cities`, and profile images (`GET /users/{filename}`).

Registration takes `userType`: `0` = User, `1` = Provider (also needs `providerName`, which creates the provider profile), `2` = Admin. **The server does not restrict who can register as an admin** (see §6).

---

## 2. Role: USER (customer)

A car owner looking for services.

### 2.1 Account and profile
- Register / log in; send an `fcmToken` so push notifications work (send `""` if there is none, not `null`).
- View own profile: `GET /users/me`.
- Update name, phone and FCM token: `POST /users/update`.
- Upload profile image and background image: `POST /users/upload-image`, `POST /users/upload-background-image` (multipart `file`, max 5 MB).
- See how many unread chat messages they have: `GET /users/me/unread-count`.
- Check whether another user is online: `GET /users/{id}/online`.

### 2.2 Browse the marketplace
- **Search** everything by text: `GET /search?q=` → offers, services, providers and branches that match.
- **Providers**: list (`GET /providers/show-all`), details, filter by one service or several services (`/providers/filter-by-service/{id}`, `/providers/filter-by-services?serviceIds=1,2`).
- **Branches and opening hours**: `GET /branches/by-provider/{providerId}`, `POST /work-time/get-all-work-time/{branchId}`.
- **Reference data**: services, car types, common spare parts, cancel reasons, countries / provinces / cities.
- **Offers**: all offers (each marked `saved` for the current user), filtered by service(s).
- **Advertisements**: `GET /advertisements/show-all` returns only ads that are currently valid (see §4.5). Report display and clicks with `POST /advertisements/track-impression/{id}` and `/track-click/{id}`.

### 2.3 Follow, rate and save
- **Follow / unfollow a provider**: `POST /providers/follow-provider/{id}`, `DELETE /providers/unfollow-provider/{id}`. The provider's follower count updates. List followers with `GET /providers/{id}/followers`.
- **Rate a provider** with a score and comment: `POST /rate/insert`; edit or delete only your own rating.
- **Save offers** to a personal list: `POST /saved-offers/insert`, list with `GET /saved-offers/my-saved-offers`, remove with `DELETE /saved-offers/remove/{offerId}`.

### 2.4 The main flow: request work and choose a provider
1. **Create an order** – `POST /orders/insert` with car model/year, comment, date, optional latitude/longitude, `servicesIds` and `spareParts` (names). Status starts as `PENDING`.
2. **Providers answer** with responses (see §3.3). The user reads them with `GET /responses/get-all-response-of-user`, each showing the provider, comment, location and the price of every spare part.
3. **Accept a response** – `POST /responses/accept-response` `{id}`:
   - the response becomes `ACCEPT`;
   - all other responses on that order are deleted;
   - the provider gets an in-app notification (`RESPONSE_ACCEPTED`) and a push.
4. **Or reject one** – `POST /responses/cancel-response` `{id}`: that response is deleted and the provider is notified (`RESPONSE_REJECTED`).
5. **Cancel the whole order** – `POST /orders/cancel` `{orderId, reasonId}` with a reason from `/reasons/show-all`. Only the owner can cancel; status becomes `CANCELED`.
6. **Review own orders** – `GET /orders/show-all-order-of-user`. A user may delete only their own orders.

### 2.5 Become a provider
A user can apply to be a provider: `POST /provider-requests/request` (multipart) with business name, address, phone, services, social links and optional ID / tax-card photos. One pending request per user. An admin approves or rejects it (see §4.3); the user receives a push either way and, once approved, their role becomes `PROVIDER`.

### 2.6 Chat, notifications, calls
- **Chat**: send and read messages (`/messages/...`), see the conversation list (`GET /messages/chat-list`), a conversation with one user (`GET /messages/conversation/{userId}`). Real-time delivery, typing indicators and read receipts use the WebSocket channel (§5).
- **Notifications**: `GET /notifications/my`, `/my/unread`, `/my/unread-count`, `POST /notifications/mark-read/{id}`, `/mark-all-read`.
- **Calls**: request a token with `POST /agora/token` or `POST /livekit/token`, then send the invite over the WebSocket (§5).

---

## 3. Role: PROVIDER (workshop / service centre)

A provider can do everything a user can do, plus manage the business and answer orders.

### 3.1 How an account becomes a provider
- **Register directly** with `userType = 1` and `providerName`; or
- **Apply** from a user account and be approved by an admin; or
- **Created by an admin** via `POST /providers/create-by-admin`.

### 3.2 Manage the business profile
| Task | Endpoint |
|---|---|
| View own provider profile | `GET /providers/me` |
| Edit personal info, services, branches (with opening hours), links and offers in one call | `POST /providers/update-all/{id}` |
| Replace the services offered | `POST /providers/update-services` |
| Add / edit / delete a branch | `POST /branches/insert`, `/update`, `DELETE /branches/delete/{id}` |
| Add / edit / delete opening hours of a branch | `/work-time/insert`, `/update`, `DELETE /work-time/delete/{id}` |
| Add / edit / delete social or web links | `/links/insert`, `/update`, `DELETE /links/delete/{id}` |
| Publish / edit / delete offers (promotions linked to services, with an end date) | `/offers/insert`, `/update`, `DELETE /offers/delete/{id}` |
| See followers | `GET /providers/{id}/followers` |

Ratings from users are shown on the provider profile; the provider cannot edit them.

### 3.3 Win business: answer orders
1. **See relevant orders**: `GET /orders/show-all-order-of-provider`.
2. **Send a response**: `POST /responses/insert` with `orderId`, `providerId`, a comment, optional location, and `sparePartsPrice: [{id: <spare part id>, price}]`. The state starts as `PENDING`. Only accounts with role `PROVIDER` may do this.
3. **Edit** your own response with `POST /responses/update`, or **delete** it.
4. **Learn the result** through notifications: `RESPONSE_ACCEPTED` (your offer won; other responses on that order disappear) or `RESPONSE_REJECTED`.
5. **Talk to the customer** by chat or call.

### 3.4 Everything a user can do
Providers keep the full user toolset: browsing, search, chat, calls, notifications, following other providers, saving offers, and profile / image upload.

---

## 4. Role: ADMIN (platform operator)

Admins maintain the data the app depends on and moderate the platform.

### 4.1 Reference data (admin-only writes)
| Data | Endpoints (insert / update / delete) |
|---|---|
| Services | `/services/insert`, `/update`, `DELETE /delete/{id}` |
| Car types | `/car-types/...` |
| Common spare parts | `/common-spare-parts/...` |
| Countries | `/countries/...` |
| Cities | `/cities/...` |

Provinces (`/provinces`), reasons (`/reasons`) and individual spare parts (`/spare-parts`) can currently be written by any logged-in user, not only admins. Reading countries / provinces / cities needs no login.

### 4.2 User management
- Create a user with any role: `POST /users/insert` (admin only; password is stored as given, see §6).
- List, view, update and delete users: `/users/show-all`, `/users/show/{id}`, `/users/update`, `DELETE /users/delete/{id}`.
- Create a provider with full profile in one call: `POST /providers/create-by-admin` (user fields, provider name, services, branches with work times, links, offers).

### 4.3 Provider applications
1. `GET /provider-requests/show-all` – list pending applications (with ID / tax-card image filenames).
2. `POST /provider-requests/accept/{id}` – creates the provider (name, services and links from the application), sets the user's role to `PROVIDER`, deletes the application and pushes `PROVIDER_ACCEPTED`.
3. `POST /provider-requests/reject/{id}?reason=...` – deletes the application and pushes `PROVIDER_REJECTED` with the reason.
4. Role switches without deleting data: `POST /provider-requests/put-it-user/{userId}` (provider → user) and `/put-it-provider/{userId}` (back to provider).

### 4.4 Advertisements
- Create / edit / delete: `/advertisements/insert`, `/update`, `DELETE /delete/{id}`. Fields: title (required), description, image URL, target URL, advertiser name, priority (higher shows first), start date, end date, status.
- **Change status manually**: `PUT /advertisements/status/{id}/{status}` where status is `ACTIVE`, `INACTIVE` or `EXPIRED`.
- Browse by status or advertiser, and see performance (`impressions`, `clicks`) on each ad: `/advertisements/by-status/{status}`, `/by-advertiser/{name}`.

### 4.5 Advertisement lifecycle (automatic)
- Apps see only ads that are `ACTIVE` **and** between start and end date.
- A scheduled job runs **every day at 00:01**: an ad whose date range includes today becomes `ACTIVE`; otherwise it becomes `EXPIRED`.
- Ads an admin set to `INACTIVE` are **never touched** by the job, so an admin can switch an ad off and it stays off. To bring it back, set it to `ACTIVE`; the next daily run will expire it if it is outside its dates.

### 4.6 Oversight
Admins can read all orders, responses, rates, messages and notifications through the `show-all` endpoints, and send test pushes (`POST /notifications/test-send/{userId}`).

---

## 5. Real-time features (all roles)

| Feature | How it works |
|---|---|
| **Chat** | Connect to the STOMP WebSocket `/ws` with the JWT. Send a message, receive messages on `/user/queue/messages`. REST endpoints under `/messages` give history. |
| **Typing & read receipts** | STOMP payloads `{toUserId, typing}` and `{messageId}`. |
| **Presence** | `GET /presence?userIds=1,2,3`, `GET /presence/online`, and live updates on `/topic/presence`. |
| **Calls** | Both sides get a token (`/agora/token` or `/livekit/token`). The channel name is the two user IDs sorted and joined with `_`. Signalling (`CALL_INVITE`, `CALL_ACCEPT`, `CALL_REJECT`, `CALL_END`) goes over the raw WebSocket `/web-socket/{userId}?token=JWT`. If the callee is offline they get a push. |
| **Push notifications (FCM)** | Sent for accepted/rejected responses, accepted/rejected provider applications and incoming calls, using the `fcmToken` saved on the account. |

---

## 6. Permission matrix

✔ = allowed and enforced by the server. ✘ = not available.

| Capability | USER | PROVIDER | ADMIN |
|---|:-:|:-:|:-:|
| Register / login, own profile, images | ✔ | ✔ | ✔ |
| Search, browse providers, offers, branches, ads | ✔ | ✔ | ✔ |
| Follow providers, rate, save offers | ✔ | ✔ | ✔ |
| Create / cancel own orders | ✔ | ✔ | ✔ |
| Accept / decline responses | own orders | own orders | own orders |
| Send responses to orders | ✘ | ✔ | ✘ |
| Manage branches, work times, links | ✘ | ✔ | ✘ |
| Manage offers | ✘ | ✔ | ✘ |
| Update provider profile (`update-all`, `update-services`) | ✘ | own profile | any provider |
| Apply to become a provider | ✔ | ✔ | ✔ |
| Approve / reject applications | ✘ | ✘ | ✔ |
| Create providers by admin / create users | ✘ | ✘ | ✔ |
| Manage services, car types, common spare parts, countries, cities | ✘ | ✘ | ✔ |
| Manage provinces, reasons, spare parts | ✘ | ✘ | ✔ |
| Manage advertisements, change ad status | ✘ | ✘ | ✔ |
| Update / delete a user | own account | own account | any user |
| Chat, calls, notifications, presence | ✔ | ✔ | ✔ |

### Security update
The gaps listed in earlier versions of this guide (admin self-registration, missing ownership checks, ids trusted from request bodies, HTTP 200 on denied actions, passwords/tokens in responses) were fixed in the security and order-flow rewrite. The matrix above therefore treats every ◐ entry as enforced:

- Only the order owner can accept or decline responses, cancel, complete or delete an order.
- Provider data (profile, services, branches, links, offers) can be changed only by that provider or an admin.
- Provider requests, role changes, user listing and reference-data writes are admin only.
- Chat, calls and presence need an accepted order between the two users.
- Ratings need a completed order. See `API_REFERENCE.md` for details.

## 7. Typical journeys

**Customer gets a repair quote**
1. Register (`userType 0`) → pick country/province/city from public endpoints.
2. Search or browse providers → create an order.
3. Wait for providers to respond → open responses → compare prices → accept one.
4. Chat or call the provider → after the job, rate the provider.

**Workshop joins and gets work**
1. Register as provider (`userType 1`) or apply and wait for admin approval.
2. Complete profile: services, branches + opening hours, links, offers.
3. Check `show-all-order-of-provider` → send priced responses.
4. Get a push when accepted → contact the customer.

**Admin launches a banner**
1. Create the ad with dates and priority (status `ACTIVE`).
2. The app shows it only while today is within its dates; the nightly job expires it afterwards.
3. Pause it any time with `PUT /advertisements/status/{id}/INACTIVE`.
4. Monitor impressions and clicks on the ad.
