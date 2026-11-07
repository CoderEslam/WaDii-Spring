# WaDii API v1 — contract

Source of truth for the backend (`WaDii-Spring`) and the web client (`WaDii-KMP`).
Business rules: `docs/BUSINESS_RULES_V1.md` (derived from *WaDii — Business Logic & Product Rules, consolidated*).

## 0. Conventions

| Topic | Rule |
|---|---|
| Base path | `/api/v1` (all paths below are relative to it) |
| Auth | `Authorization: Bearer <jwt>` on every endpoint except those marked **public** |
| Envelope | Every JSON response: `{"success":bool, "code":string?, "message":string, "data":T?, "statusCode":int, "details":{field:msg}?, "timestamp":ISO}` |
| Errors | `success=false`, `code` = stable error code (see §15). Clients branch on `code`, never on `message` |
| Pages | `data` = `{"items":[T], "page":0, "size":20, "totalItems":N, "totalPages":N}`; query `?page=0&size=20` (size ≤ 100) |
| Money | JSON number, 2 decimals, currency EGP. Server computes every total; clients never send totals |
| Instants | ISO-8601 UTC string `2026-11-07T10:15:30Z` |
| Dates / times | `LocalDate` `"2026-11-07"`, `LocalTime` `"09:00"`, interpreted in `Africa/Cairo` |
| Nulls | Null fields are omitted from responses |
| Ids | `long` numbers. The caller is always taken from the JWT; ids in bodies never identify the caller |
| Created | Creates return HTTP 201 |

Modes: one account has capabilities (`customer` always, `provider` once VERIFIED, `seller` always). The client
switches mode locally; the server checks capabilities on each endpoint (provider endpoints live under `/provider/**`).

---

## 1. Auth & account

### POST /auth/register — public
Body `{ "fullName": "Mona Adel", "email": "mona@x.com", "phone": "01001234567", "password": "min 8 chars" }`
→ 201 `AuthResponse`

### POST /auth/login — public
Body `{ "email": "...", "password": "..." }` → 200 `AuthResponse`. Wrong email or password → `INVALID_CREDENTIALS`.
Suspended/banned → `ACCOUNT_SUSPENDED`. Rate-limited (`RATE_LIMITED`).

`AuthResponse = { "token": "jwt", "expiresInSeconds": 86400, "user": Me }`

### GET /me → `Me`
```
Me = {
  "id": 1, "fullName": "...", "email": "...", "phone": "...", "status": "ACTIVE|SUSPENDED|BANNED",
  "admin": false, "adminRoles": ["SUPER_ADMIN","SUPPORT_ADMIN","FINANCE_ADMIN","CONTENT_ADMIN"],
  "capabilities": {
    "customer": true,
    "seller": true,
    "provider": {            // omitted when the user never started an application
      "providerId": 7, "applicationStatus": "DRAFT|SUBMITTED|UNDER_REVIEW|VERIFIED|REJECTED",
      "providerStatus": "ACTIVE|SUSPENDED", "canOperate": true   // VERIFIED && ACTIVE && account ACTIVE
    }
  },
  "createdAt": "..."
}
```
### PATCH /me — body `{ "fullName"?, "phone"? }` → `Me`
### POST /me/password — body `{ "currentPassword", "newPassword" }` → `AuthResponse` (all old tokens revoked)
### POST /auth/logout-all → `null` (revokes every token)
### PUT /me/push-token — body `{ "token": "fcm token" }` → `null`

---

## 2. Catalog & public config — public

### GET /catalog/categories → `[Category]`
`Category = { "id":1, "code":"SPARE_PARTS|BATTERIES|TIRES|OIL_CHANGE|CAR_WASH|ACCESSORIES|REPAIRS", "nameEn":"Batteries", "nameAr":"بطاريات", "vehicleRequired":true, "active":true, "sortOrder":1 }`
Only active categories are returned here (admin sees all, §13).

### GET /catalog/areas → `[Area]`
`Area = { "id":1, "nameEn":"Cairo", "nameAr":"القاهرة" }` (Egyptian governorates)

### GET /config/public
```
{ "responseDeadlineHours":48, "offerValidityDefaultHours":24, "offerValidityMinHours":1, "offerValidityMaxHours":48,
  "noShowGraceMinutes":30,
  "commission": { "mode":"NONE|FIXED|PERCENT", "value":0, "payer":"CUSTOMER|PROVIDER" },
  "seller": { "maxActiveListings":3, "feeMode":"NONE|FIXED|PERCENT", "feeValue":0, "feeTiming":"ON_PUBLISH|ON_SOLD", "listingDurationDays":30 },
  "listingCategories": ["SPARE_PARTS","BATTERIES","TIRES","ACCESSORIES","TOOLS","OTHER"],
  "listingConditions": ["NEW","LIKE_NEW","GOOD","FAIR","FOR_PARTS"] }
```

---

## 3. Vehicles (customer)

`Vehicle = { "id":3, "make":"Chevrolet", "model":"Lanos", "year":2016, "label":"Chevrolet Lanos 2016", "lastUsedAt":"..."?, "createdAt":"..." }`

| Method | Path | Body | Result |
|---|---|---|---|
| GET | /vehicles | — | `[Vehicle]` sorted last-used first (first one = default preselection) |
| POST | /vehicles | `{make, model, year}` | 201 `Vehicle` |
| PUT | /vehicles/{id} | `{make, model, year}` | `Vehicle` |
| DELETE | /vehicles/{id} | — | `null` (archived; past requests keep their snapshot) |

`year` 1950..current+1. make/model 1..60 chars.

---

## 4. Files

### POST /files — multipart `file`, form field `purpose` = `PROVIDER_DOCUMENT|EVIDENCE|LISTING_PHOTO|CHAT|REQUEST_PHOTO`
→ 201 `FileRef = { "key":"uuid.jpg", "contentType":"image/jpeg", "size":12345, "public":false }`
Allowed: jpg/png/webp (+pdf for documents/evidence), max 10 MB. `LISTING_PHOTO` is public; the rest private.

### GET /files/public/{key} — public, streams public files
### GET /files/{key} — streams a private file. Allowed: uploader, admins, and parties of the dispute / order /
conversation / provider application the file is attached to. Otherwise `ACCESS_DENIED`.

---

## 5. Provider application & profile

### GET /provider/application → `ProviderApplication` (404 `PROVIDER_NOT_FOUND` if none)
### PUT /provider/application — create or update while `DRAFT` or `REJECTED`
Body `{ "businessName", "description"?, "phone", "areaId", "address", "categoryIds":[1,2] }` → `ProviderApplication`
### POST /provider/application/documents — body `{ "type":"COMMERCIAL_REGISTER|TAX_CARD|NATIONAL_ID|SHOP_PHOTO|OTHER", "fileKey":"..." }` → 201 `ProviderDocument`
### DELETE /provider/application/documents/{id} → `null` (only while DRAFT/REJECTED)
### POST /provider/application/submit → `ProviderApplication` (status SUBMITTED; needs ≥1 category and ≥1 document)
```
ProviderApplication = { "providerId":7, "businessName", "description", "phone", "areaId", "address",
  "categories":[Category], "documents":[ProviderDocument], "applicationStatus", "providerStatus",
  "rejectionReason"?, "submittedAt"?, "reviewedAt"?, "acceptingOrders":true }
ProviderDocument = { "id", "type", "fileKey", "contentType", "uploadedAt" }
```

The following require `canOperate` (VERIFIED + ACTIVE) unless noted; otherwise `PROVIDER_NOT_ACTIVE`.

| Method | Path | Body | Result |
|---|---|---|---|
| GET | /provider/profile | — | `ProviderApplication` (works for any status) |
| PATCH | /provider/profile | `{description?, phone?, acceptingOrders?}` | `ProviderApplication` |
| PUT | /provider/services | `{categoryIds:[..]}` (≥1) | `ProviderApplication` |
| GET | /provider/branches | — | `[Branch]` |
| POST | /provider/branches | `BranchInput` | 201 `Branch` |
| PUT | /provider/branches/{id} | `BranchInput` | `Branch` |
| PUT | /provider/branches/{id}/hours | `{hours:[{dayOfWeek:1..7, opensAt:"09:00", closesAt:"18:00"}]}` | `Branch` |
| GET | /provider/vehicle-support | — | `[VehicleSupport]` |
| PUT | /provider/vehicle-support | `{items:[{make, model?, yearFrom?, yearTo?}]}` | `[VehicleSupport]` (empty list = supports all vehicles) |
| GET | /provider/policy | — | `ProviderPolicy` |
| PUT | /provider/policy | `{cancellationTerms, noShowTerms, serviceConditions, returnTerms}` | `ProviderPolicy` (new version) |

```
BranchInput = { "name", "areaId", "address", "latitude"?, "longitude"?, "phone", "serviceRadiusKm"?(1..200),
                "slotMinutes": 60 (15..480), "maxBookingsPerSlot": 2 (1..50), "active": true }
Branch = BranchInput + { "id", "hours":[{dayOfWeek, opensAt, closesAt}] }
VehicleSupport = { "id", "make", "model"?, "yearFrom"?, "yearTo"? }
ProviderPolicy = { "version":3, "cancellationTerms", "noShowTerms", "serviceConditions", "returnTerms", "createdAt" }
```
Latitude and longitude must be sent together. dayOfWeek 1 = Monday … 7 = Sunday. `closesAt` > `opensAt`.

### GET /providers/{id} — public provider card
`PublicProvider = { "id", "businessName", "description", "areaId", "categories":[Category], "ratingAverage":4.6, "ratingCount":12, "completedOrders":40, "branches":[{id,name,areaId,address,latitude?,longitude?,phone}], "memberSince" }`
### GET /providers/{id}/ratings?page= → Page<`Rating`> (hidden ratings excluded)

---

## 6. Service requests (customer mode)

### POST /requests → 201 `RequestDetail`
```
{
  "vehicle": { "vehicleId": 3 } | { "make":"Kia", "model":"Cerato", "year":2019, "save": true } | null,
  "items": [ { "categoryId": 2, "description": "Battery dead, 70Ah" }, { "categoryId": 4, "description": "" } ],  // 1..7, distinct categories
  "areaId": 1, "address": "Nasr City, Abbas El Akkad", "latitude"?: 30.05, "longitude"?: 31.34,
  "preferredDate"?: "2026-11-09", "preferredTimeFrom"?: "10:00", "preferredTimeTo"?: "14:00",
  "notes"?: "...", "photoKeys"?: ["uuid.jpg"],
  "providerId"?: 7          // explicit provider; validated with the same eligibility rules, then only that provider is invited
}
```
Vehicle is required when any item's category has `vehicleRequired=true` (`VEHICLE_REQUIRED`). Matching runs immediately.

### GET /requests?status=&page= → Page<`RequestSummary`> (mine, newest first)
`RequestSummary = { "id", "status", "vehicleLabel"?, "areaId", "itemCount", "categories":["BATTERIES"], "offerCount", "acceptedCount", "responseDeadline", "createdAt" }`

### GET /requests/{id} → `RequestDetail` (owner, or admin)
```
RequestDetail = {
  "id", "status": "MATCHING|OFFERS_AVAILABLE|PARTIALLY_MATCHED|CLOSED|CANCELED|EXPIRED",
  "vehicle": { "make","model","year","label" }?, "areaId", "address", "latitude"?, "longitude"?,
  "preferredDate"?, "preferredTimeFrom"?, "preferredTimeTo"?, "notes"?, "photoKeys":[...],
  "responseDeadline", "editable": true, "invitedProviders": 5, "createdAt",
  "items": [ {
     "id", "category": Category, "description", "status": "MATCHING|OFFERS_AVAILABLE|ACCEPTED|CANCELED|EXPIRED",
     "orderId"?: 12,
     "offers": [ OfferLineForCustomer ]     // PENDING lines first, then the rest; UNAVAILABLE lines are omitted
  } ]
}
OfferLineForCustomer = {
  "lineId", "offerId", "status": "PENDING|ACCEPTED|DECLINED|WITHDRAWN|EXPIRED",
  "provider": { "id", "businessName", "ratingAverage", "ratingCount", "completedOrders" },
  "branch": { "id", "name", "address", "areaId", "latitude"?, "longitude"?, "distanceKm"? },
  "pricing": Pricing, "parts":[{ "name", "quantity", "unitPrice", "total" }],
  "estimatedMinutes"?, "proposedStart"?, "notes"?, "warranty": Warranty, "expiresAt", "submittedAt"
}
Pricing = { "servicePrice", "laborCost", "partsTotal", "discount", "providerTotal",
            "commissionAmount", "commissionPayer": "CUSTOMER|PROVIDER", "customerTotal" }
   // providerTotal = servicePrice + laborCost + partsTotal − discount
   // customerTotal = providerTotal + (payer==CUSTOMER ? commissionAmount : 0)
Warranty = { "provided": true, "durationDays"?: 180, "coverage"?: "PARTS|LABOR|PARTS_AND_LABOR", "exclusions"?, "conditions"? }
```

### PUT /requests/{id} — edit; only while no offer line exists (`REQUEST_NOT_EDITABLE`). Same body as create minus `providerId`.
### POST /requests/{id}/cancel — body `{ "reason"? }` → `RequestDetail`. Cancels every item that is not ACCEPTED.
### POST /requests/{id}/items/{itemId}/cancel → `RequestDetail` (item not ACCEPTED)
### POST /requests/{id}/items/{itemId}/accept — body `{ "lineId": 55, "appointmentStart"?: ISO }` → 201 `OrderDetail`
Atomically: line → ACCEPTED, competing PENDING lines for the same item → DECLINED, item → ACCEPTED, creates a
Service Order with pricing, commission and policy snapshots. `appointmentStart` defaults to the line's
`proposedStart`; it must fall in the branch working hours and capacity (`SLOT_FULL`, `OUTSIDE_WORKING_HOURS`).
Errors: `OFFER_NOT_PENDING`, `OFFER_EXPIRED`, `ITEM_NOT_OPEN`, `SELF_DEALING_FORBIDDEN`.
### POST /requests/{id}/lines/{lineId}/decline → `RequestDetail`

---

## 7. Provider inbox & offers (provider mode)

### GET /provider/requests?status=INVITED|VIEWED|OFFERED|DECLINED&page= → Page<`ProviderRequestSummary`>
`ProviderRequestSummary = { "requestId", "targetStatus", "requestStatus", "areaId", "vehicleLabel"?, "matchingCategories":["BATTERIES"], "responseDeadline", "distanceKm"?, "invitedAt", "myOfferId"? }`

### GET /provider/requests/{id} → `ProviderRequestView` (marks target VIEWED)
Only the service items this provider offers are included. Customer contact data is not exposed before acceptance.
```
ProviderRequestView = { "requestId", "requestStatus", "targetStatus", "vehicle"?, "areaId", "address",
  "latitude"?, "longitude"?, "preferredDate"?, "preferredTimeFrom"?, "preferredTimeTo"?, "notes"?, "photoKeys":[],
  "responseDeadline", "items":[{ "id", "category", "description", "status" }], "myOffer": ProviderOffer? }
```
### POST /provider/requests/{id}/decline → `null` (target DECLINED)
### POST /provider/requests/{id}/offer → 201 `ProviderOffer` — one active offer per provider per request
```
{ "branchId": 4, "validHours"?: 24, "notes"?: "...",
  "lines": [ {
      "serviceItemId": 10, "available": true,                 // false = "not currently serviceable" (no prices needed)
      "servicePrice": 300, "laborCost": 100, "discount": 0,
      "parts": [ { "name":"Battery 70Ah", "quantity":1, "unitPrice": 2500 } ],
      "estimatedMinutes"?: 45, "proposedStart"?: ISO, "notes"?: "...",
      "warranty": { "provided": true, "durationDays": 365, "coverage":"PARTS", "exclusions"?: "...", "conditions"?: "..." }
  } ] }
```
Rules: every line's item must belong to the request, be open, and be in a category the provider offers
(`CATEGORY_NOT_OFFERED`); ≥1 available line; money ≥ 0; discount ≤ gross; expiry = now + validHours within
[min,max] and never after the request deadline (`INVALID_EXPIRY`). A warranty must state `provided`; when true,
`durationDays` (1..3650) and `coverage` are required.

### PUT /provider/offers/{id} — same body; allowed while no line is ACCEPTED (`OFFER_LOCKED`). Replaces PENDING lines.
### POST /provider/offers/{id}/withdraw → `ProviderOffer` (PENDING lines → WITHDRAWN)
### GET /provider/offers?status=ACTIVE|WITHDRAWN|EXPIRED|CLOSED&page= → Page<`ProviderOffer`>
```
ProviderOffer = { "id", "requestId", "status":"ACTIVE|WITHDRAWN|EXPIRED|CLOSED", "branchId", "notes"?, "expiresAt", "createdAt",
  "lines":[{ "lineId", "serviceItemId", "categoryCode", "status":"PENDING|UNAVAILABLE|ACCEPTED|DECLINED|WITHDRAWN|EXPIRED",
             "available", "pricing": Pricing, "parts":[...], "estimatedMinutes"?, "proposedStart"?, "notes"?, "warranty": Warranty, "orderId"? }] }
```

---

## 8. Service orders (both parties)

### GET /orders?as=customer|provider&status=&page= → Page<`OrderSummary`>
`OrderSummary = { "id", "requestId", "category": Category, "status", "cashStatus", "counterpartName", "customerTotal", "appointmentStart"?, "createdAt" }`

### GET /orders/{id} → `OrderDetail` (customer, provider, or admin)
```
OrderDetail = {
  "id", "requestId", "serviceItemId", "category", "itemDescription",
  "status": "ACCEPTED|IN_PROGRESS|PROVIDER_MARKED_COMPLETED|COMPLETED|INTERRUPTION_REQUESTED|INTERRUPTION_STATEMENT_SUBMITTED|INTERRUPTED|DISPUTED|CANCELED",
  "cashStatus": "NOT_DUE|DUE|REPORTED_COLLECTED|CONFIRMED|PARTIAL|DISPUTED",
  "myRole": "CUSTOMER|PROVIDER|ADMIN",
  "customer": { "id", "fullName", "phone" },               // phone visible to the provider after acceptance
  "provider": { "id", "businessName", "phone" },
  "branch": { "id", "name", "address", "phone", "latitude"?, "longitude"? },
  "vehicle"?: { "make","model","year","label" },
  "pricing": Pricing, "parts":[...],
  "approvedChargesTotal": 0, "amountDue": 0,               // amountDue = customerTotal + approved charges (or interruption final amount)
  "cashReportedAmount"?, "cashReportedAt"?, "cashConfirmedAt"?,
  "warranty": Warranty, "warrantyId"?, "warrantyEndsAt"?,
  "appointmentStart"?, "appointmentEnd"?,
  "startedAt"?, "providerCompletedAt"?, "completedAt"?, "canceledAt"?,
  "cancellation"?: { "kind":"CUSTOMER_CANCELED|PROVIDER_CANCELED|CUSTOMER_NO_SHOW|PROVIDER_NO_SHOW|ADMIN_CANCELED", "reason"?, "byUserId" },
  "interruption"?: Interruption, "charges":[Charge], "policySnapshot": PolicySnapshot,
  "invoiceNumber"?, "rating"?: Rating, "canRate": false, "openDisputeId"?,
  "actions": ["START","MARK_COMPLETED","CONFIRM_COMPLETION","COMPLAIN","CANCEL","RESCHEDULE","REPORT_NO_SHOW",
              "REQUEST_INTERRUPTION","SUBMIT_INTERRUPTION_STATEMENT","ACCEPT_INTERRUPTION","DISPUTE_INTERRUPTION",
              "REPORT_CASH","CONFIRM_CASH","DISPUTE_CASH","PROPOSE_CHARGE","RATE"],   // what the caller may do now
  "createdAt"
}
Interruption = { "requestedAt", "reason", "workDone"?, "laborCost"?, "partsCost"?, "proposedAmount"?, "evidenceKeys":[], "statementAt"?, "outcome"?: "ACCEPTED|DISPUTED|RESOLVED", "finalAmount"? }
Charge = { "id", "description", "amount", "status":"PENDING|APPROVED|REJECTED", "createdAt", "decidedAt"? }
PolicySnapshot = { "providerPolicyVersion"?, "cancellationTerms", "noShowTerms", "serviceConditions", "returnTerms",
                   "platformRules": ["..."], "warranty": Warranty, "commission": { "mode","value","payer","base","amount" } }
```

Order actions (all `POST /orders/{id}/<action>`, return `OrderDetail` unless noted):

| Action | Who | Allowed when | Body | Effect |
|---|---|---|---|---|
| start | provider | ACCEPTED | — | IN_PROGRESS |
| mark-completed | provider | IN_PROGRESS | `{note?}` | PROVIDER_MARKED_COMPLETED, cash NOT_DUE→DUE, customer notified to confirm |
| confirm-completion | customer | PROVIDER_MARKED_COMPLETED | — | COMPLETED, invoice number, warranty activated, commission ledger entry, rating allowed |
| complain | customer | PROVIDER_MARKED_COMPLETED | `{description, evidenceKeys?}` | DISPUTED + dispute SERVICE_QUALITY → 201 `Dispute` |
| cancel | customer or provider | ACCEPTED | `{reason}` | CANCELED (kind by party). Free in V1 |
| reschedule | customer or provider | ACCEPTED | `{appointmentStart, reason?}` | appointment moved (working hours + capacity checked), change logged |
| report-no-show | customer or provider | ACCEPTED and now ≥ appointmentStart + grace | — | CANCELED with kind *_NO_SHOW of the **other** party (`NO_SHOW_TOO_EARLY`) |
| request-interruption | customer | IN_PROGRESS | `{reason}` | INTERRUPTION_REQUESTED |
| interruption-statement | provider | INTERRUPTION_REQUESTED | `{workDone, laborCost, partsCost, proposedAmount, evidenceKeys?}` | INTERRUPTION_STATEMENT_SUBMITTED |
| accept-interruption | customer | INTERRUPTION_STATEMENT_SUBMITTED | — | INTERRUPTED, amountDue = proposedAmount, cash DUE |
| dispute-interruption | customer | INTERRUPTION_STATEMENT_SUBMITTED | `{description, evidenceKeys?}` | DISPUTED + dispute INTERRUPTION → 201 `Dispute` |
| cash/report | provider | status ∈ IN_PROGRESS…, cash ∈ NOT_DUE/DUE/PARTIAL | `{amount}` | REPORTED_COLLECTED (amount ≥ amountDue) or PARTIAL |
| cash/confirm | customer | cash ∈ REPORTED_COLLECTED/PARTIAL | — | CONFIRMED (or stays PARTIAL if partial) |
| cash/dispute | customer | cash ∈ REPORTED_COLLECTED/PARTIAL | `{description}` | cash DISPUTED + dispute CASH_COLLECTION + fraud flag → 201 `Dispute` |
| charges | provider | IN_PROGRESS | `{description, amount}` | 201 `Charge` PENDING |
| charges/{cid}/approve, /reject | customer | charge PENDING | — | charge decided |
| rating | customer | COMPLETED, once | `{stars:1..5, comment?}` | 201 `Rating` |

Wrong state → `ORDER_INVALID_STATE`; wrong party → `ACCESS_DENIED`.

### GET /orders/{id}/events → `[OrderEvent]` `{ "id", "type", "actorRole":"CUSTOMER|PROVIDER|ADMIN|SYSTEM", "note"?, "createdAt" }`

`Rating = { "id", "orderId", "stars", "comment"?, "customerName", "createdAt", "hidden"? }`

---

## 9. Warranty

| Method | Path | Who | Body | Result |
|---|---|---|---|---|
| GET | /warranties?as=customer\|provider | party | — | `[WarrantyView]` |
| POST | /warranties/{id}/claims | customer, warranty active | `{description, evidenceKeys?}` | 201 `WarrantyClaim` |
| GET | /warranty-claims?as=customer\|provider&page= | party | — | Page<`WarrantyClaim`> |
| POST | /warranty-claims/{id}/respond | provider, OPEN | `{accept:bool, note}` | `WarrantyClaim` ACCEPTED/REJECTED |
| POST | /warranty-claims/{id}/resolve | customer, ACCEPTED | — | RESOLVED |
| POST | /warranty-claims/{id}/escalate | customer, REJECTED or ACCEPTED | `{description}` | ESCALATED + dispute WARRANTY → 201 `Dispute` |

```
WarrantyView = { "id", "orderId", "category", "providerName", "coverage", "durationDays", "exclusions"?, "conditions"?, "startsAt", "endsAt", "active": true }
WarrantyClaim = { "id", "warrantyId", "orderId", "status":"OPEN|ACCEPTED|REJECTED|ESCALATED|RESOLVED", "description", "evidenceKeys":[], "providerNote"?, "createdAt", "respondedAt"? }
```

---

## 10. Disputes

### GET /disputes?page= → Page<`Dispute`> (disputes I opened or am a party to)
### GET /disputes/{id} → `Dispute` (parties + admins)
### POST /disputes — body `{ "type":"CANCELLATION|LISTING|OTHER", "orderId"?, "listingId"?, "description", "evidenceKeys"? }` → 201
(other types are opened by the order/warranty actions above)
### POST /disputes/{id}/evidence — body `{ "note"?, "fileKey"? }` → `Dispute`
```
Dispute = { "id", "type":"SERVICE_QUALITY|INTERRUPTION|CASH_COLLECTION|WARRANTY|CANCELLATION|LISTING|OTHER",
  "status":"OPEN|UNDER_REVIEW|RESOLVED|REJECTED", "orderId"?, "requestId"?, "listingId"?, "warrantyClaimId"?,
  "openedByUserId", "openedByRole":"CUSTOMER|PROVIDER|SELLER|BUYER", "description",
  "evidence":[{ "id", "byUserId", "note"?, "fileKey"?, "createdAt" }],
  "assignedAdminId"?, "outcome"?, "resolutionNote"?, "finalAmount"?, "createdAt", "resolvedAt"? }
```

---

## 11. Individual used-item marketplace (seller)

```
Listing = { "id", "sellerId", "sellerName", "title", "category", "condition", "description", "price",
  "areaId", "locationText"?, "contactMethod":"CHAT|PHONE", "phone"? (only when contactMethod=PHONE and ACTIVE),
  "photoKeys":[], "status":"DRAFT|PENDING_REVIEW|ACTIVE|SOLD|CLOSED|REJECTED|EXPIRED",
  "rejectionReason"?, "publishedAt"?, "expiresAt"?, "createdAt", "mine": bool }
ListingInput = { "title"(3..120), "category", "condition", "description"(≤4000), "price"(>0), "areaId", "locationText"?, "contactMethod", "photoKeys"(≤8) }
```
| Method | Path | Notes |
|---|---|---|
| GET | /listings?category=&areaId=&q=&minPrice=&maxPrice=&page= | **public**, ACTIVE only |
| GET | /listings/{id} | **public** for ACTIVE/SOLD; owner/admin for others |
| GET | /me/listings?status=&page= | mine |
| GET | /me/seller | `{ "activeListings":1, "maxActiveListings":3, "feesDue": 0, "fees":[LedgerEntry] }` |
| POST | /listings | `ListingInput` → 201 DRAFT |
| PUT | /listings/{id} | DRAFT/REJECTED only |
| POST | /listings/{id}/submit | DRAFT/REJECTED → PENDING_REVIEW (or ACTIVE if moderation off). Quota: ACTIVE+PENDING_REVIEW < max (`LISTING_LIMIT_REACHED`) |
| POST | /listings/{id}/mark-sold | ACTIVE → SOLD |
| POST | /listings/{id}/close | DRAFT/PENDING_REVIEW/ACTIVE → CLOSED |

Seller fee: when the configured timing happens (publish or sold) and fee > 0, a `SELLER_FEE` ledger entry (DUE) is created.

---

## 12. Communication

### Conversations
`Conversation = { "id", "kind":"ORDER|REQUEST|LISTING", "refId", "title", "counterpartName", "lastMessage"?, "lastMessageAt"?, "unread": 0 }`
`Message = { "id", "senderId", "mine": bool, "body"?, "fileKey"?, "createdAt" }`

| Method | Path | Result |
|---|---|---|
| GET | /conversations?page= | Page<Conversation> |
| POST | /conversations | body `{kind, refId}` → get-or-create. ORDER: the order's parties. REQUEST: caller is the customer (needs `providerId` in body) or a targeted provider. LISTING: a buyer with the seller |
| GET | /conversations/{id}/messages?afterId=&limit=50 | `[Message]` ascending; marks read |
| POST | /conversations/{id}/messages | `{body?, fileKey?}` → 201 Message |

Admins (SUPPORT) can read any conversation.

### Notifications
`Notification = { "id", "type", "title", "body", "refType"?: "REQUEST|OFFER|ORDER|DISPUTE|WARRANTY_CLAIM|LISTING|SUBSCRIPTION|APPLICATION", "refId"?, "read": bool, "createdAt" }`

| Method | Path | Result |
|---|---|---|
| GET | /notifications?unreadOnly=&page= | Page<Notification> |
| GET | /notifications/unread-count | `{ "count": 3 }` |
| POST | /notifications/{id}/read | `null` |
| POST | /notifications/read-all | `null` |

---

## 13. Provider dashboard & finance (provider mode)

### GET /provider/dashboard?days=30|90 →
```
{ "windowDays":30,
  "operations": { "invited", "viewed", "offersSubmitted", "responseRate", "avgResponseMinutes", "offersAccepted",
                  "completedOrders", "providerCancellations", "customerCancellations", "noShows", "activeOrders" },
  "quality": { "ratingAverage", "ratingCount", "recentRatingAverage", "disputes", "disputeRate", "warrantyClaims", "completionRate" },
  "finance": { "grossAccepted", "grossCompleted", "cashReportedCollected", "cashConfirmed", "cashDisputed",
               "commissionAccrued", "commissionSettled", "outstandingBalance" },
  "subscription": ProviderSubscription }
```
Rates are 0..1. "cashReportedCollected" is self-reported, not verified by WaDii.

### GET /provider/subscription → `ProviderSubscription`
`ProviderSubscription = { "status":"FREE_OR_DISABLED|ACTIVE|REMINDER_DUE|PAYMENT_OVERDUE|RESTRICTED|EXEMPT", "monthlyPrice", "currentPeriodStart"?, "nextDueDate"?, "graceEndsOn"?, "exemptUntil"?, "lastPaidAt"? }`
### GET /provider/ledger?type=&status=&page= → Page<`LedgerEntry`>
### POST /provider/ledger/{id}/report-paid — body `{ "method":"CASH|BANK_TRANSFER|WALLET", "reference"? }` → `LedgerEntry` REPORTED_PAID
```
LedgerEntry = { "id", "type":"SUBSCRIPTION|COMMISSION|SELLER_FEE|CANCELLATION_FEE|ADJUSTMENT", "status":"DUE|REPORTED_PAID|SETTLED|WAIVED|CANCELED",
  "amount", "currency":"EGP", "payerUserId", "providerId"?, "orderId"?, "requestId"?, "listingId"?,
  "ruleDescription", "configVersionId"?, "dueDate"?, "reportedAt"?, "settledAt"?, "method"?, "reference"?, "note"?, "createdAt" }
```
The same report-paid endpoint exists for sellers at `POST /me/ledger/{id}/report-paid`.

---

## 14. Admin (`/admin/**`, admin accounts only; sub-role in brackets)

| Area | Endpoints |
|---|---|
| Users [SUPPORT] | `GET /admin/users?q=&status=&page=` → Page<AdminUser>; `POST /admin/users/{id}/suspend|ban|reactivate` `{reason}` |
| Admin accounts [SUPER] | `POST /admin/admins` `{fullName,email,phone,password,roles:[..]}`; `PUT /admin/admins/{id}/roles` `{roles}` |
| Applications [SUPPORT] | `GET /admin/provider-applications?status=&page=`; `GET /admin/provider-applications/{providerId}`; `POST …/{providerId}/start-review`; `POST …/approve`; `POST …/reject` `{reason}` |
| Providers [SUPPORT] | `GET /admin/providers?q=&status=&page=`; `POST /admin/providers/{id}/suspend|reactivate` `{reason}` |
| Categories [SUPER] | `GET /admin/categories`; `PUT /admin/categories/{id}` `{nameEn,nameAr,vehicleRequired,active,sortOrder}` |
| Config [SUPER, FINANCE for financial keys] | `GET /admin/config/{key}` → `{current: ConfigVersion, history:[ConfigVersion]}`; `POST /admin/config/{key}` `{payload:{..}, effectiveFrom?, reason}` → ConfigVersion. key ∈ `MARKETPLACE|COMMISSION|SUBSCRIPTION|SELLER` |
| Requests/Orders [SUPPORT] | `GET /admin/requests?status=&page=`; `GET /admin/orders?status=&cashStatus=&providerId=&categoryId=&page=`; `POST /admin/orders/{id}/cancel` `{reason}` |
| Disputes [SUPPORT; FINANCE when finalAmount or cash outcome] | `GET /admin/disputes?status=&type=&page=`; `POST /admin/disputes/{id}/assign` (to me); `POST /admin/disputes/{id}/resolve` `{outcome, note, finalAmount?}`; `POST /admin/disputes/{id}/reject` `{note}` |
| Warranty claims [SUPPORT] | `GET /admin/warranty-claims?status=&page=` |
| Subscriptions [FINANCE] | `GET /admin/subscriptions?status=&page=` → Page<{providerId,businessName,…ProviderSubscription}>; `POST /admin/subscriptions/{providerId}/payments` `{amount, paidOn, method, reference?}`; `POST /admin/subscriptions/{providerId}/exemptions` `{months? , until?, reason}` |
| Ledger [FINANCE] | `GET /admin/ledger?type=&status=&providerId=&page=`; `POST /admin/ledger/{id}/settle` `{method, reference?, note?}`; `POST /admin/ledger/{id}/waive` `{note}`; `POST /admin/ledger/adjustments` `{payerUserId, providerId?, amount, note}` |
| Listings [CONTENT] | `GET /admin/listings?status=&page=`; `POST /admin/listings/{id}/approve`; `POST /admin/listings/{id}/reject` `{reason}` |
| Ratings [CONTENT] | `GET /admin/ratings?page=`; `POST /admin/ratings/{id}/hide|unhide` |
| Fraud [SUPPORT] | `GET /admin/fraud-flags?status=&page=`; `POST /admin/fraud-flags/{id}/dismiss|action` `{note}`; `POST /admin/fraud-flags` `{subjectUserId, note}` (manual) |
| Reports [any admin] | `GET /admin/reports/summary?from=&to=` → see below |
| Audit [SUPER] | `GET /admin/audit?entityType=&entityId=&page=` |

```
AdminUser = { "id","fullName","email","phone","status","admin","adminRoles":[],"providerId"?,"applicationStatus"?,"createdAt" }
ConfigVersion = { "id","key","payload":{..},"effectiveFrom","createdByUserId","reason","createdAt" }
Summary = { "from","to",
  "requests": { "total", "byStatus":{..} }, "orders": { "total", "byStatus":{..}, "byCategory":{"BATTERIES":3} },
  "users": { "total", "newInRange", "providersVerified", "pendingApplications" },
  "finance": { "commissionAccrued","commissionSettled","subscriptionDue","subscriptionSettled","sellerFeesDue","sellerFeesSettled","overdueProviders" },
  "trust": { "openDisputes","openFraudFlags","openWarrantyClaims","cancellations","noShows" } }
```

---

## 15. Error codes

`VALIDATION_FAILED`(400) `MALFORMED_REQUEST`(400) `UNAUTHENTICATED`(401) `INVALID_CREDENTIALS`(401) `TOKEN_EXPIRED`(401)
`TOKEN_INVALID`(401) `ACCESS_DENIED`(403) `ACCOUNT_SUSPENDED`(403) `NOT_FOUND`(404) `CONFLICT`(409)
`CONCURRENT_MODIFICATION`(409) `RATE_LIMITED`(429) `PAYLOAD_TOO_LARGE`(413) `INTERNAL_ERROR`(500)
`EMAIL_ALREADY_USED` `PHONE_ALREADY_USED` `FILE_REQUIRED` `FILE_TYPE_NOT_ALLOWED` `FILE_CONTENT_MISMATCH`
`PROVIDER_NOT_FOUND` `PROVIDER_NOT_ACTIVE` `PROVIDER_NOT_ELIGIBLE` `APPLICATION_NOT_EDITABLE` `APPLICATION_INCOMPLETE`
`APPLICATION_INVALID_STATE` `CATEGORY_NOT_FOUND` `VEHICLE_REQUIRED` `REQUEST_NOT_FOUND` `REQUEST_NOT_EDITABLE`
`REQUEST_DEADLINE_PASSED` `ITEM_NOT_OPEN` `NOT_TARGETED` `CATEGORY_NOT_OFFERED` `OFFER_ALREADY_EXISTS` `OFFER_NOT_FOUND`
`OFFER_LOCKED` `OFFER_NOT_PENDING` `OFFER_EXPIRED` `INVALID_EXPIRY` `SELF_DEALING_FORBIDDEN` `OUTSIDE_WORKING_HOURS`
`SLOT_FULL` `APPOINTMENT_IN_PAST` `ORDER_NOT_FOUND` `ORDER_INVALID_STATE` `NO_SHOW_TOO_EARLY` `CASH_INVALID_STATE`
`RATING_NOT_ALLOWED` `RATING_ALREADY_EXISTS` `WARRANTY_NOT_FOUND` `WARRANTY_NOT_ACTIVE` `CLAIM_INVALID_STATE`
`DISPUTE_NOT_FOUND` `DISPUTE_INVALID_STATE` `DISPUTE_ALREADY_OPEN` `LISTING_NOT_FOUND` `LISTING_INVALID_STATE`
`LISTING_LIMIT_REACHED` `LEDGER_INVALID_STATE` `CONFIG_INVALID` `CONVERSATION_NOT_FOUND` `SUBSCRIPTION_RESTRICTED`
