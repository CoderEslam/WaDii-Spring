# WaDii API Reference (v1)

Every endpoint of the WaDii backend with its access rule, parameters, request body and response, for frontend
developers (web and mobile). Generated from the controllers and DTO records in `WaDii-Spring` (branch
`feature/v1-consolidated`). Business rules behind the endpoints: `docs/BUSINESS_RULES_V1.md`.

**Contents**
1. [Conventions](#1-conventions)
2. [Shared objects (schemas)](#2-shared-objects-schemas)
3. [Auth & account](#3-auth--account)
4. [Catalog & public config](#4-catalog--public-config)
5. [Files](#5-files)
6. [Vehicles](#6-vehicles)
7. [Provider application & workshop](#7-provider-application--workshop)
8. [Public provider pages](#8-public-provider-pages)
9. [Service requests (customer)](#9-service-requests-customer)
10. [Provider inbox & offers](#10-provider-inbox--offers)
11. [Service orders](#11-service-orders)
12. [Warranty](#12-warranty)
13. [Disputes](#13-disputes)
14. [Used-item listings (seller)](#14-used-item-listings-seller)
15. [Chat](#15-chat)
16. [Notifications](#16-notifications)
17. [Provider dashboard & finance](#17-provider-dashboard--finance)
18. [Admin](#18-admin)
19. [Enums](#19-enums)
20. [Error codes](#20-error-codes)
21. [Typical flows](#21-typical-flows)

---

## 1. Conventions

| Topic | Rule |
|---|---|
| Base URL | `http://<host>:8080/api/v1` — every path below is relative to it. Android emulator: `http://10.0.2.2:8080/api/v1` |
| Format | JSON, `Content-Type: application/json` (uploads: `multipart/form-data`) |
| Auth | `Authorization: Bearer <token>` on every endpoint not marked **Public**. Token from `/auth/login` or `/auth/register`, valid 24 h |
| Identity | The caller always comes from the token. Never send `userId`/`providerId` to identify yourself |
| Money | JSON number with 2 decimals, currency EGP (e.g. `1250.00`). Totals are computed by the server — never send them |
| Instants | ISO-8601 UTC string: `"2026-11-08T09:30:00Z"` |
| Dates / times | `"2026-11-09"` and `"14:30"`, interpreted in `Africa/Cairo` |
| Nulls | Fields with `null` values are **omitted** from responses. Treat a missing field as null |
| Unknown fields | Request bodies ignore unknown fields |
| Created | Create endpoints answer HTTP **201** |
| CORS | Allowed browser origins are configured on the server (`wadii.cors.allowed-origins`) |

### 1.1 Response envelope
Every JSON response — success and error — has the same envelope.

Success:
```json
{
  "success": true,
  "message": "OK",
  "data": { "...": "endpoint result" },
  "statusCode": 200,
  "timestamp": "2026-11-08T09:30:00.123Z"
}
```

Error:
```json
{
  "success": false,
  "code": "VALIDATION_FAILED",
  "message": "The request is invalid.",
  "statusCode": 400,
  "details": { "items[0].categoryId": "must not be null", "address": "must not be blank" },
  "timestamp": "2026-11-08T09:30:00.123Z"
}
```
- Branch on `code` (stable), never on `message` (human text, may change).
- `details` is present only for field validation errors; keys are field paths (`lines[0].servicePrice`).
- Endpoints documented as returning `null` answer `{"success":true,"message":"OK","statusCode":200,...}` with no `data`.

### 1.2 Pagination
List endpoints that accept `page` / `size` return a **Page**:
```json
{ "items": [ ], "page": 0, "size": 20, "totalItems": 57, "totalPages": 3 }
```
Query: `?page=0&size=20` — `page` starts at 0, `size` 1..100 (default 20). Values out of range are clamped.

### 1.3 HTTP status usage
| Status | Meaning |
|---|---|
| 200 / 201 | Success / created |
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST` (bad JSON, wrong enum value, bad type), file errors |
| 401 | Missing, invalid, expired or revoked token (`UNAUTHENTICATED`, `TOKEN_INVALID`, `TOKEN_EXPIRED`, `INVALID_CREDENTIALS`) — sign the user out |
| 403 | Not allowed (`ACCESS_DENIED`, `ACCOUNT_SUSPENDED`, `PROVIDER_NOT_ACTIVE`, `SUBSCRIPTION_RESTRICTED`, `NOT_TARGETED`) |
| 404 | Not found **or not yours** (other users' resources are reported as not found) |
| 409 | State conflict (`ORDER_INVALID_STATE`, `OFFER_NOT_PENDING`, ...) — refresh and show the message |
| 413 | File too large |
| 422 | Business rule violated (`VEHICLE_REQUIRED`, `CATEGORY_NOT_OFFERED`, ...) |
| 429 | `RATE_LIMITED` (login/register) |
| 500 | `INTERNAL_ERROR` |

### 1.4 Roles and modes
One account can act in several modes; the client switches mode locally, the server checks each call.

| Mode | Who | Endpoints |
|---|---|---|
| Customer | every non-staff account | `/vehicles`, `/requests`, `/orders?as=customer`, `/warranties`, ... |
| Seller | every non-staff account | `/listings`, `/me/listings`, `/me/seller` |
| Provider | `me.capabilities.provider.canOperate == true` | `/provider/**`, `/orders?as=provider` |
| Admin | `me.admin == true`; sub-roles in `me.adminRoles` | `/admin/**` |

Admin sub-roles: `SUPER_ADMIN` (has every role), `SUPPORT_ADMIN`, `FINANCE_ADMIN`, `CONTENT_ADMIN`. Staff accounts cannot
use customer/provider/seller endpoints.

---

## 2. Shared objects (schemas)

Objects returned by several endpoints. Endpoint sections refer to them by name. A value of `"?"` in an example
means the field is optional: it is absent when it has no value (it is never sent as `null`).

### Me
```json
{
  "id": 12,
  "fullName": "Mona Adel",
  "email": "mona@example.com",
  "phone": "01001234567",
  "status": "ACTIVE",
  "admin": false,
  "adminRoles": [],
  "capabilities": {
    "customer": true,
    "seller": true,
    "provider": {
      "providerId": 7,
      "applicationStatus": "VERIFIED",
      "providerStatus": "ACTIVE",
      "canOperate": true
    }
  },
  "createdAt": "2026-11-01T10:00:00Z"
}
```
- `capabilities.provider` is absent until the user saves a provider application.
- Staff accounts: `admin: true`, `adminRoles: ["SUPER_ADMIN", ...]`, `customer: false`, `seller: false`, no `provider`.
- For a `SUPER_ADMIN`, `adminRoles` lists all four roles.

### AuthResponse
```json
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "expiresInSeconds": 86400, "user": { "...": "Me" } }
```

### Category
```json
{ "id": 2, "code": "BATTERIES", "nameEn": "Batteries", "nameAr": "بطاريات", "vehicleRequired": true, "active": true, "sortOrder": 2 }
```

### Area
```json
{ "id": 1, "nameEn": "Cairo", "nameAr": "القاهرة" }
```

### Vehicle
```json
{ "id": 3, "make": "Chevrolet", "model": "Lanos", "year": 2016, "label": "Chevrolet Lanos 2016", "lastUsedAt": "2026-11-05T08:00:00Z", "createdAt": "2026-10-20T08:00:00Z" }
```

### VehicleSnapshot (vehicle copied into a request/order)
```json
{ "make": "Hyundai", "model": "Elantra", "year": 2019, "label": "Hyundai Elantra 2019" }
```

### FileRef
```json
{ "key": "1b4e28ba-2fa1-11d2-883f-0016d3cca427.jpg", "contentType": "image/jpeg", "size": 183402, "public": false }
```

### ProviderApplication
Used by the provider's own application/profile and by admin provider lists.
```json
{
  "providerId": 7,
  "businessName": "Fathy Auto Electric",
  "description": "Batteries, diagnostics and electrical repairs.",
  "phone": "0224000000",
  "areaId": 1,
  "address": "Nasr City, Makram Ebeid St.",
  "categories": [ { "...": "Category" } ],
  "documents": [ { "...": "ProviderDocument" } ],
  "applicationStatus": "VERIFIED",
  "providerStatus": "ACTIVE",
  "rejectionReason": "Blurry ID (only when REJECTED)",
  "statusReason": "Last suspend/reactivate reason ?",
  "submittedAt": "2026-11-02T10:00:00Z",
  "reviewedAt": "2026-11-03T10:00:00Z",
  "acceptingOrders": true
}
```

### ProviderDocument
```json
{ "id": 4, "type": "COMMERCIAL_REGISTER", "fileKey": "....pdf", "contentType": "application/pdf", "uploadedAt": "2026-11-02T09:00:00Z" }
```

### Branch
```json
{
  "id": 1, "name": "Main branch", "areaId": 1, "address": "Nasr City", "latitude": 30.0561, "longitude": 31.3301,
  "phone": "0224000000", "serviceRadiusKm": 25, "slotMinutes": 60, "maxBookingsPerSlot": 3, "active": true,
  "hours": [ { "dayOfWeek": 1, "opensAt": "09:00", "closesAt": "21:00" } ]
}
```
`dayOfWeek`: 1 = Monday … 7 = Sunday. `latitude`/`longitude`/`serviceRadiusKm` are optional.

### VehicleSupport
```json
{ "id": 9, "make": "Toyota", "model": "Corolla", "yearFrom": 2010, "yearTo": 2022 }
```
`model`, `yearFrom`, `yearTo` optional. An empty list means the provider supports every vehicle.

### ProviderPolicy
```json
{ "version": 3, "cancellationTerms": "...", "noShowTerms": "...", "serviceConditions": "...", "returnTerms": "...", "createdAt": "2026-11-03T10:00:00Z" }
```
`version: 0` (and no `createdAt`) = the provider has not saved terms yet; the platform defaults are shown.

### Pricing
```json
{
  "servicePrice": 1900.00, "laborCost": 100.00, "partsTotal": 50.00, "discount": 0.00,
  "providerTotal": 2050.00, "commissionAmount": 0.00, "commissionPayer": "PROVIDER", "customerTotal": 2050.00
}
```
- `providerTotal = servicePrice + laborCost + partsTotal − discount`
- `customerTotal = providerTotal + commissionAmount` when `commissionPayer = CUSTOMER`, otherwise `= providerTotal`.

### Part
```json
{ "name": "Battery 70Ah", "quantity": 1, "unitPrice": 2500.00, "total": 2500.00 }
```

### Warranty
```json
{ "provided": true, "durationDays": 365, "coverage": "PARTS", "exclusions": "Physical damage", "conditions": "Keep the invoice" }
```
No warranty: `{ "provided": false }` (other fields absent; `conditions` may still be present).

### OfferLineForCustomer (inside RequestDetail)
```json
{
  "lineId": 1, "offerId": 1, "status": "PENDING",
  "provider": { "id": 1, "businessName": "Fathy Auto Electric", "ratingAverage": 4.6, "ratingCount": 12, "completedOrders": 40 },
  "branch": { "id": 1, "name": "Main branch", "address": "Nasr City", "areaId": 1, "latitude": 30.05, "longitude": 31.33, "distanceKm": 3.4 },
  "pricing": { "...": "Pricing" },
  "parts": [ { "...": "Part" } ],
  "estimatedMinutes": 60,
  "proposedStart": "2026-11-09T12:00:00Z",
  "notes": "Can start today.",
  "warranty": { "...": "Warranty" },
  "expiresAt": "2026-11-09T19:18:50Z",
  "submittedAt": "2026-11-08T19:18:50Z"
}
```

### RequestSummary
```json
{
  "id": 1, "status": "OFFERS_AVAILABLE", "vehicleLabel": "Hyundai Elantra 2019", "areaId": 1, "itemCount": 2,
  "categories": ["BATTERIES", "OIL_CHANGE"], "offerCount": 2, "acceptedCount": 0,
  "responseDeadline": "2026-11-10T19:18:50Z", "createdAt": "2026-11-08T19:18:50Z"
}
```

### RequestDetail
```json
{
  "id": 1,
  "status": "OFFERS_AVAILABLE",
  "vehicle": { "...": "VehicleSnapshot ?" },
  "areaId": 1, "address": "Nasr City, Abbas El Akkad", "latitude": 30.05, "longitude": 31.34,
  "preferredDate": "2026-11-09", "preferredTimeFrom": "10:00", "preferredTimeTo": "14:00",
  "notes": "Available after 4 pm",
  "photoKeys": ["....jpg"],
  "responseDeadline": "2026-11-10T19:18:50Z",
  "editable": false,
  "invitedProviders": 2,
  "createdAt": "2026-11-08T19:18:50Z",
  "items": [
    {
      "id": 1,
      "category": { "...": "Category" },
      "description": "Car does not start in the morning",
      "status": "OFFERS_AVAILABLE",
      "orderId": 12,
      "offers": [ { "...": "OfferLineForCustomer" } ]
    }
  ]
}
```
- `items[].offers`: pending lines first, then the rest; cheapest first within a group. Lines a provider marked unavailable are not shown.
- `items[].orderId` is set once the item is accepted.
- `editable`: the request can still be edited (no offer exists yet).

### ProviderOffer (provider side)
```json
{
  "id": 1, "requestId": 1, "status": "ACTIVE", "branchId": 1, "notes": "Can start today.",
  "expiresAt": "2026-11-09T19:18:50Z", "createdAt": "2026-11-08T19:18:50Z",
  "lines": [
    {
      "lineId": 1, "serviceItemId": 1, "categoryCode": "BATTERIES", "status": "PENDING", "available": true,
      "pricing": { "...": "Pricing" }, "parts": [ ], "estimatedMinutes": 60, "proposedStart": "2026-11-09T12:00:00Z",
      "notes": "...", "warranty": { "...": "Warranty" }, "orderId": 12
    }
  ]
}
```
`orderId` is present on a line once the customer accepted it.

### OrderSummary
```json
{
  "id": 12, "requestId": 1, "category": { "...": "Category" }, "status": "ACCEPTED", "cashStatus": "NOT_DUE",
  "counterpartName": "Fathy Auto Electric", "customerTotal": 2050.00,
  "appointmentStart": "2026-11-09T12:00:00Z", "createdAt": "2026-11-08T20:00:00Z"
}
```
`counterpartName` is the provider's business name for customers and the customer's name for providers/admins.

### OrderDetail
```json
{
  "id": 12, "requestId": 1, "serviceItemId": 1,
  "category": { "...": "Category" },
  "itemDescription": "Car does not start in the morning",
  "status": "IN_PROGRESS",
  "cashStatus": "PARTIAL",
  "myRole": "CUSTOMER",
  "customer": { "id": 3, "fullName": "Mona Adel", "phone": "01011111111" },
  "provider": { "id": 1, "businessName": "Fathy Auto Electric", "phone": "0224000000" },
  "branch": { "id": 1, "name": "Main branch", "address": "Nasr City", "phone": "0224000000", "latitude": 30.05, "longitude": 31.33 },
  "vehicle": { "...": "VehicleSnapshot ?" },
  "pricing": { "...": "Pricing" },
  "parts": [ { "...": "Part" } ],
  "approvedChargesTotal": 150.00,
  "amountDue": 2200.00,
  "cashReportedAmount": 1000.00, "cashReportedAt": "2026-11-09T13:00:00Z", "cashConfirmedAt": "2026-11-09T13:05:00Z",
  "warranty": { "...": "Warranty" }, "warrantyId": 5, "warrantyEndsAt": "2027-11-09T14:00:00Z",
  "appointmentStart": "2026-11-09T12:00:00Z", "appointmentEnd": "2026-11-09T13:00:00Z",
  "startedAt": "2026-11-09T12:05:00Z", "providerCompletedAt": "?", "completedAt": "?", "canceledAt": "?",
  "cancellation": { "kind": "CUSTOMER_CANCELED", "reason": "Changed plans", "byUserId": 3 },
  "interruption": {
    "requestedAt": "...", "reason": "Need the car now", "workDone": "Diagnosis", "laborCost": 100.00, "partsCost": 50.00,
    "proposedAmount": 150.00, "evidenceKeys": ["....jpg"], "statementAt": "...", "outcome": "ACCEPTED", "finalAmount": 150.00
  },
  "charges": [ { "id": 3, "description": "Extra filter", "amount": 150.00, "status": "APPROVED", "createdAt": "...", "decidedAt": "..." } ],
  "policySnapshot": {
    "providerPolicyVersion": 3,
    "cancellationTerms": "...", "noShowTerms": "...", "serviceConditions": "...", "returnTerms": "...",
    "platformRules": ["Payment is made in cash directly to the provider. WaDii does not verify cash payments.", "..."],
    "warranty": { "...": "Warranty" },
    "commission": { "mode": "PERCENT", "value": 10.00, "payer": "CUSTOMER", "base": 2050.00, "amount": 205.00 }
  },
  "invoiceNumber": "WD-2026-000012",
  "rating": { "...": "Rating ?" },
  "canRate": false,
  "openDisputeId": 4,
  "actions": ["REPORT_CASH", "MARK_COMPLETED", "PROPOSE_CHARGE"],
  "createdAt": "2026-11-08T20:00:00Z"
}
```
- **`actions` is the only source of truth for which buttons to show.** Values: `START`, `MARK_COMPLETED`,
  `CONFIRM_COMPLETION`, `COMPLAIN`, `CANCEL`, `RESCHEDULE`, `REPORT_NO_SHOW`, `REQUEST_INTERRUPTION`,
  `SUBMIT_INTERRUPTION_STATEMENT`, `ACCEPT_INTERRUPTION`, `DISPUTE_INTERRUPTION`, `REPORT_CASH`, `CONFIRM_CASH`,
  `DISPUTE_CASH`, `PROPOSE_CHARGE`, `RATE`. Pending charges are approved/rejected by the customer from `charges[]`
  (status `PENDING`).
- `status` (execution) and `cashStatus` (cash collection) are independent; show them as two tracks.
  Cash figures are **reported by the provider and confirmed by the customer**; WaDii does not verify them.
- `amountDue` = `customerTotal + approvedChargesTotal`, or the final amount decided by an interruption/dispute.
- `myRole`: `CUSTOMER`, `PROVIDER` or `ADMIN`. The customer's phone is visible to the provider.
- `cancellation`, `interruption`, `rating`, `openDisputeId`, `warrantyId` appear only when relevant.

### OrderEvent
```json
{ "id": 40, "type": "STARTED", "actorRole": "PROVIDER", "note": "optional text", "createdAt": "2026-11-09T12:05:00Z" }
```
Types include `ACCEPTED`, `STARTED`, `PROVIDER_MARKED_COMPLETED`, `COMPLETED`, `RESCHEDULED`, `CUSTOMER_CANCELED`,
`PROVIDER_CANCELED`, `CUSTOMER_NO_SHOW`, `PROVIDER_NO_SHOW`, `ADMIN_CANCELED`, `INTERRUPTION_REQUESTED`,
`INTERRUPTION_STATEMENT`, `INTERRUPTED`, `INTERRUPTION_DISPUTED`, `COMPLAINT`, `CASH_REPORTED`, `CASH_CONFIRMED`,
`CASH_DISPUTED`, `CHARGE_PROPOSED`, `CHARGE_APPROVED`, `CHARGE_REJECTED`, `REOPENED`, `CASH_CONFIRMED_BY_SUPPORT`,
`CASH_REPORT_REJECTED_BY_SUPPORT`, `DISPUTE_CLOSED`. `actorRole`: `CUSTOMER`, `PROVIDER`, `ADMIN`, `SYSTEM`.

### Charge
```json
{ "id": 3, "description": "Extra filter", "amount": 150.00, "status": "PENDING", "createdAt": "2026-11-09T12:30:00Z", "decidedAt": "?" }
```

### Rating
```json
{ "id": 8, "orderId": 12, "stars": 5, "comment": "Great", "customerName": "Mona", "createdAt": "2026-11-10T10:00:00Z", "hidden": false }
```
Public provider ratings show only the reviewer's first name.

### WarrantyView
```json
{
  "id": 5, "orderId": 12, "category": "BATTERIES", "providerName": "Fathy Auto Electric", "coverage": "PARTS",
  "durationDays": 365, "exclusions": "?", "conditions": "?", "startsAt": "2026-11-09T14:00:00Z", "endsAt": "2027-11-09T14:00:00Z", "active": true
}
```
`category` is the category **code** string.

### WarrantyClaim
```json
{
  "id": 2, "warrantyId": 5, "orderId": 12, "status": "OPEN", "description": "Battery died again",
  "evidenceKeys": ["....jpg"], "providerNote": "?", "disputeId": 9, "createdAt": "2026-12-01T10:00:00Z", "respondedAt": "?"
}
```
`disputeId` is set once the claim was escalated (open it with `GET /disputes/{disputeId}`).

### Dispute
```json
{
  "id": 4, "type": "SERVICE_QUALITY", "status": "OPEN",
  "orderId": 12, "requestId": 1, "listingId": "?", "warrantyClaimId": "?",
  "openedByUserId": 3, "openedByRole": "CUSTOMER",
  "description": "Still not working",
  "evidence": [ { "id": 1, "byUserId": 3, "note": "?", "fileKey": "?....jpg", "createdAt": "2026-11-10T10:00:00Z" } ],
  "assignedAdminId": 1, "outcome": "COMPLETE_ORDER", "resolutionNote": "?", "finalAmount": 120.00,
  "createdAt": "2026-11-10T10:00:00Z", "resolvedAt": "?"
}
```

### Listing
```json
{
  "id": 1, "sellerId": 3, "sellerName": "Karim Saleh", "title": "Original Toyota alloy wheels (set of 4)",
  "category": "ACCESSORIES", "condition": "GOOD", "description": "15 inch, minor scratches.", "price": 6500.00,
  "areaId": 1, "locationText": "Maadi", "contactMethod": "PHONE", "phone": "01022222222",
  "photoKeys": ["....jpg"], "status": "ACTIVE", "rejectionReason": "?",
  "publishedAt": "2026-11-08T10:00:00Z", "expiresAt": "2026-12-08T10:00:00Z", "createdAt": "2026-11-08T09:00:00Z",
  "mine": false
}
```
`phone` appears only when `contactMethod = PHONE` and the listing is `ACTIVE`. Photos: `GET /files/public/{key}`.

### LedgerEntry
```json
{
  "id": 6, "type": "COMMISSION", "status": "DUE", "amount": 205.00, "currency": "EGP",
  "payerUserId": 5, "providerId": 1, "orderId": 12, "requestId": 1, "listingId": "?",
  "ruleDescription": "10.00% of EGP 2050.00 (customer-paid surcharge collected in cash by the provider)",
  "configVersionId": 7, "dueDate": "2026-12-09", "reportedAt": "?", "settledAt": "?",
  "method": "CASH", "reference": "?", "note": "?", "createdAt": "2026-11-09T14:00:00Z"
}
```

### ProviderSubscription
```json
{
  "status": "REMINDER_DUE", "monthlyPrice": 300.00, "currentPeriodStart": "2026-10-15", "nextDueDate": "2026-11-15",
  "graceEndsOn": "2026-11-22", "exemptUntil": "?", "lastPaidAt": "2026-10-15T09:00:00Z"
}
```

### Notification
```json
{ "id": 30, "type": "NEW_OFFER", "title": "New offer", "body": "Fathy Auto Electric sent you an offer.", "refType": "REQUEST", "refId": 1, "read": false, "createdAt": "2026-11-08T19:18:50Z" }
```

### Conversation
```json
{ "id": 2, "kind": "ORDER", "refId": 12, "title": "Order #12", "counterpartName": "Fathy Auto Electric", "lastMessage": "On my way", "lastMessageAt": "2026-11-09T11:50:00Z", "unread": 1 }
```

### Message
```json
{ "id": 15, "senderId": 5, "mine": false, "body": "On my way", "fileKey": "?", "createdAt": "2026-11-09T11:50:00Z" }
```

---

## 3. Auth & account

### POST /auth/register
**Public.** Creates a normal account (customer + seller). Admins can never be created here.

Request:
```json
{ "fullName": "Mona Adel", "email": "mona@example.com", "phone": "01001234567", "password": "Password123" }
```
| Field | Rules |
|---|---|
| fullName | required, ≤ 120 |
| email | required, valid email, ≤ 255, unique (case-insensitive) |
| phone | required, Egyptian mobile `^01[0125][0-9]{8}$`, unique |
| password | required, 8–72 characters |

Response `201`: **AuthResponse**.
Errors: `VALIDATION_FAILED`, `EMAIL_ALREADY_USED` (409), `PHONE_ALREADY_USED` (409), `RATE_LIMITED` (429).

### POST /auth/login
**Public.**
```json
{ "email": "mona@example.com", "password": "Password123" }
```
Response `200`: **AuthResponse**.
Errors: `INVALID_CREDENTIALS` (401, same for unknown email and wrong password), `ACCOUNT_SUSPENDED` (403), `RATE_LIMITED` (429).

### POST /auth/logout-all
Revokes every token of the caller (all devices). No body. Response: `null`. The current token stops working too.

### GET /me
Response: **Me**. Call it after login/app start to know the modes the user can use.

### PATCH /me
Update own profile. Send only the fields to change.
```json
{ "fullName": "Mona A.", "phone": "01009999999" }
```
`fullName` 1–120; `phone` Egyptian mobile, unique. Response: **Me**. Errors: `VALIDATION_FAILED`, `PHONE_ALREADY_USED`.

### POST /me/password
```json
{ "currentPassword": "Password123", "newPassword": "NewPassword456" }
```
`newPassword` 8–72. Response: **AuthResponse** — **replace the stored token with the new one**; all older tokens are revoked.
Errors: `INVALID_CREDENTIALS` (wrong current password), `VALIDATION_FAILED`.

### PUT /me/push-token
Registers the device's FCM token for push notifications (send `null`/empty to clear).
```json
{ "token": "fcm-registration-token" }
```
`token` ≤ 512. Response: `null`.

---

## 4. Catalog & public config

### GET /catalog/categories
**Public.** Active service categories, sorted. Response: `[Category]`.

| code | nameEn | vehicleRequired (default) |
|---|---|---|
| SPARE_PARTS | Spare parts | true |
| BATTERIES | Batteries | true |
| TIRES | Tires | true |
| OIL_CHANGE | Oil change | true |
| CAR_WASH | Car wash | false |
| ACCESSORIES | Accessories | false |
| REPAIRS | Repairs and maintenance | true |

`vehicleRequired` is admin-configurable — always read it from the API.

### GET /catalog/areas
**Public.** The 27 Egyptian governorates. Response: `[Area]` (id 1 = Cairo, 2 = Giza, 3 = Alexandria, ...).

### GET /config/public
**Public.** Rules the UI needs to explain things to users.
```json
{
  "responseDeadlineHours": 48,
  "offerValidityDefaultHours": 24,
  "offerValidityMinHours": 1,
  "offerValidityMaxHours": 48,
  "noShowGraceMinutes": 30,
  "commission": { "mode": "NONE", "value": 0, "payer": "PROVIDER" },
  "seller": { "maxActiveListings": 3, "feeMode": "NONE", "feeValue": 0, "feeTiming": "ON_PUBLISH", "listingDurationDays": 30 },
  "listingCategories": ["SPARE_PARTS", "BATTERIES", "TIRES", "ACCESSORIES", "TOOLS", "OTHER"],
  "listingConditions": ["NEW", "LIKE_NEW", "GOOD", "FAIR", "FOR_PARTS"]
}
```

---

## 5. Files

### POST /files
Upload a file, then use the returned `key` in other requests (documents, photos, evidence, chat).

`multipart/form-data`:
| Part | Value |
|---|---|
| `file` | the file |
| `purpose` | `PROVIDER_DOCUMENT` \| `EVIDENCE` \| `REQUEST_PHOTO` \| `CHAT` \| `LISTING_PHOTO` |

| Purpose | Allowed types | Visibility |
|---|---|---|
| PROVIDER_DOCUMENT | jpg, png, webp, pdf | private |
| EVIDENCE | jpg, png, webp, pdf | private |
| REQUEST_PHOTO | jpg, png, webp | private |
| CHAT | jpg, png, webp, pdf | private |
| LISTING_PHOTO | jpg, png, webp | **public** |

Max 10 MB. The extension, declared content type and file signature must agree.

Response `201`: **FileRef**.
Errors: `FILE_REQUIRED`, `FILE_TYPE_NOT_ALLOWED`, `FILE_CONTENT_MISMATCH` (400), `PAYLOAD_TOO_LARGE` (413).

Rules: a key can be used only by the user who uploaded it, with the matching purpose, and attached to one record.
Using someone else's key or a key already attached elsewhere fails with `VALIDATION_FAILED`.

### GET /files/public/{key}
**Public.** Streams a public file (listing photos). Use directly as an image URL.

### GET /files/{key}
Streams a private file. Allowed for the uploader, staff, and the parties of the record the file is attached to
(dispute, order, provider application, conversation, warranty claim, request). Otherwise `403 ACCESS_DENIED`.
**Fetch it with the `Authorization` header** (e.g. download to a blob / byte array) — a plain `<img src>` cannot send the token.

---

## 6. Vehicles

### GET /vehicles
Saved vehicles, **last used first** (preselect the first one in the request form). Response: `[Vehicle]`.

### POST /vehicles
```json
{ "make": "Chevrolet", "model": "Lanos", "year": 2016 }
```
`make`, `model` required ≤ 60; `year` 1950 … current year + 1. Response `201`: **Vehicle**.

### PUT /vehicles/{id}
Same body as create. Response: **Vehicle**. Past requests keep their own copy of the vehicle.

### DELETE /vehicles/{id}
Archives the vehicle (hidden from the list). Response: `null`. Errors: `NOT_FOUND`.

---

## 7. Provider application & workshop

Provider endpoints use the caller's own provider profile. A provider is **operating** when the application is
`VERIFIED` and the provider is `ACTIVE` (`me.capabilities.provider.canOperate`). Endpoints marked *operating*
answer `403 PROVIDER_NOT_ACTIVE` otherwise. Staff accounts always get `PROVIDER_NOT_ACTIVE`/`ACCESS_DENIED`.

### GET /provider/application
Response: **ProviderApplication**. `404 PROVIDER_NOT_FOUND` = the user never applied (show "Offer your services").

### PUT /provider/application
Create or update the application while it is `DRAFT` or `REJECTED`.
```json
{
  "businessName": "Fathy Auto Electric",
  "description": "Batteries and electrical repairs",
  "phone": "0224000000",
  "areaId": 1,
  "address": "Nasr City, Makram Ebeid St.",
  "categoryIds": [2, 4, 7]
}
```
| Field | Rules |
|---|---|
| businessName | required ≤ 120 |
| description | optional ≤ 2000 |
| phone | required, Egyptian phone `^0[0-9]{9,10}$` |
| areaId | required, existing area |
| address | required ≤ 300 |
| categoryIds | required (may be empty while drafting), ≤ 7 active category ids |

Response: **ProviderApplication**. Errors: `APPLICATION_NOT_EDITABLE` (409), `CATEGORY_NOT_FOUND`, `VALIDATION_FAILED`.

### POST /provider/application/documents
Attach an uploaded file (`purpose=PROVIDER_DOCUMENT`).
```json
{ "type": "COMMERCIAL_REGISTER", "fileKey": "....pdf" }
```
`type`: `COMMERCIAL_REGISTER` | `TAX_CARD` | `NATIONAL_ID` | `SHOP_PHOTO` | `OTHER`. Response `201`: **ProviderDocument**.
Errors: `APPLICATION_NOT_EDITABLE`, `VALIDATION_FAILED` (bad key).

### DELETE /provider/application/documents/{id}
Only while `DRAFT`/`REJECTED`. Response: `null`.

### POST /provider/application/submit
No body. Moves the application to `SUBMITTED`. Needs ≥ 1 category and ≥ 1 document.
Response: **ProviderApplication**. Errors: `APPLICATION_INCOMPLETE` (422), `APPLICATION_INVALID_STATE` (409).

Status flow: `DRAFT → SUBMITTED → UNDER_REVIEW → VERIFIED | REJECTED` (a rejected application can be edited and submitted again).

### GET /provider/profile
Works in any state. Response: **ProviderApplication**.

### PATCH /provider/profile  *(operating)*
```json
{ "description": "New text", "phone": "0224000001", "acceptingOrders": false }
```
All optional. `acceptingOrders: false` stops new requests from reaching the provider. Response: **ProviderApplication**.

### PUT /provider/services  *(operating)*
```json
{ "categoryIds": [2, 4] }
```
1–7 active category ids. Response: **ProviderApplication**.

### GET /provider/branches  *(operating)*
Response: `[Branch]`.

### POST /provider/branches  *(operating)*
```json
{
  "name": "Main branch", "areaId": 1, "address": "Nasr City", "latitude": 30.0561, "longitude": 31.3301,
  "phone": "0224000000", "serviceRadiusKm": 25, "slotMinutes": 60, "maxBookingsPerSlot": 3, "active": true
}
```
| Field | Rules |
|---|---|
| name | required ≤ 120 |
| areaId | required |
| address | required ≤ 300 |
| latitude / longitude | optional, **both or neither**, -90..90 / -180..180 |
| phone | required, Egyptian phone |
| serviceRadiusKm | optional 1–200 (default from config when matching) |
| slotMinutes | 15–480 — length of one appointment slot |
| maxBookingsPerSlot | 1–50 — how many orders can overlap one slot (capacity) |
| active | boolean |

Response `201`: **Branch**.

### PUT /provider/branches/{id}  *(operating)*
Same body. Response: **Branch**.

### PUT /provider/branches/{id}/hours  *(operating)*
Replaces the weekly hours.
```json
{ "hours": [ { "dayOfWeek": 1, "opensAt": "09:00", "closesAt": "21:00" }, { "dayOfWeek": 6, "opensAt": "10:00", "closesAt": "16:00" } ] }
```
`dayOfWeek` 1 (Mon) … 7 (Sun), each at most once; `closesAt` > `opensAt`. Days not listed are closed.
A branch with **no hours at all** accepts any appointment time. Response: **Branch**.

### GET /provider/vehicle-support  *(operating)*
Response: `[VehicleSupport]`.

### PUT /provider/vehicle-support  *(operating)*
Replaces the list. Empty list = all vehicles.
```json
{ "items": [ { "make": "Toyota", "model": "Corolla", "yearFrom": 2010, "yearTo": 2022 }, { "make": "Kia" } ] }
```
`make` required ≤ 60; `model` optional; years 1950–2100, `yearTo ≥ yearFrom`. ≤ 200 items. Response: `[VehicleSupport]`.

### GET /provider/policy  *(operating)*
Response: **ProviderPolicy** (`version: 0` with platform default texts if none saved yet).

### PUT /provider/policy  *(operating)*
Saves a **new version** (orders already accepted keep the version they agreed to).
```json
{ "cancellationTerms": "...", "noShowTerms": "...", "serviceConditions": "...", "returnTerms": "..." }
```
Each required, ≤ 4000. Response: **ProviderPolicy**.

---

## 8. Public provider pages

### GET /providers/{id}
**Public.** Only operating providers (otherwise `404 PROVIDER_NOT_FOUND`).
```json
{
  "id": 1, "businessName": "Fathy Auto Electric", "description": "...", "areaId": 1,
  "categories": [ { "...": "Category" } ],
  "ratingAverage": 4.6, "ratingCount": 12, "completedOrders": 40,
  "branches": [ { "id": 1, "name": "Main branch", "areaId": 1, "address": "Nasr City", "latitude": 30.05, "longitude": 31.33, "phone": "0224000000" } ],
  "memberSince": "2026-11-03T10:00:00Z"
}
```

### GET /providers/{id}/ratings?page=&size=
**Public.** Visible ratings, newest first. Response: Page<**Rating**> (`customerName` = first name only).

---

## 9. Service requests (customer)

A request has 1–7 **service items** (one per category). Matching providers are invited immediately. Each item is
accepted separately — different items can go to different providers — and each accepted item becomes its own
**service order**.

### POST /requests
```json
{
  "vehicle": { "vehicleId": 3 },
  "items": [
    { "categoryId": 2, "description": "Battery dead, 70Ah" },
    { "categoryId": 4, "description": "Synthetic 5W-30" }
  ],
  "areaId": 1,
  "address": "Nasr City, Abbas El Akkad",
  "latitude": 30.05,
  "longitude": 31.34,
  "preferredDate": "2026-11-09",
  "preferredTimeFrom": "10:00",
  "preferredTimeTo": "14:00",
  "notes": "Available after 4 pm",
  "photoKeys": ["....jpg"],
  "providerId": 7
}
```
| Field | Rules |
|---|---|
| vehicle | one of: `{ "vehicleId": 3 }` (saved vehicle) · `{ "make": "Kia", "model": "Cerato", "year": 2019, "save": true }` (other car; `save` adds it to the garage) · omitted/`null` (no vehicle). **Required** when any chosen category has `vehicleRequired = true` |
| items | 1–7, distinct `categoryId`s (active categories); `description` ≤ 2000 optional |
| areaId | required |
| address | required ≤ 300 |
| latitude / longitude | optional, both or neither. With coordinates, providers are matched by distance; without, by governorate |
| preferredDate / preferredTimeFrom / preferredTimeTo | optional; `To` must be after `From` |
| notes | optional ≤ 2000 |
| photoKeys | optional ≤ 8 keys uploaded with `purpose=REQUEST_PHOTO` |
| providerId | optional: send the request **only** to this provider (must be eligible) |

Response `201`: **RequestDetail**.
Errors: `VEHICLE_REQUIRED` (422), `CATEGORY_NOT_FOUND` (404), `PROVIDER_NOT_FOUND`, `PROVIDER_NOT_ELIGIBLE` (422, explicit
provider cannot serve it), `SELF_DEALING_FORBIDDEN` (422, your own provider account), `VALIDATION_FAILED`.

### GET /requests?status=&page=&size=
My requests, newest first. `status` optional: `MATCHING` | `OFFERS_AVAILABLE` | `PARTIALLY_MATCHED` | `CLOSED` | `CANCELED` | `EXPIRED`.
Response: Page<**RequestSummary**>.

### GET /requests/{id}
Response: **RequestDetail**. Other users' requests → `404 REQUEST_NOT_FOUND`.

### PUT /requests/{id}
Edit — only while `editable` is true (open and no offer received yet). Same body as create **without `providerId`**
(an explicit provider stays). Items are replaced and providers re-matched.
Response: **RequestDetail**. Errors: `REQUEST_NOT_EDITABLE` (409) + create errors.

### POST /requests/{id}/cancel
Cancels every item that is not accepted. Accepted items/orders are not affected.
```json
{ "reason": "Found another solution" }
```
Body optional; `reason` ≤ 500. Response: **RequestDetail**. Errors: `ITEM_NOT_OPEN` (nothing left to cancel).

### POST /requests/{id}/items/{itemId}/cancel
No body. Cancels one open item. Response: **RequestDetail**. Errors: `ITEM_NOT_OPEN` (409), `NOT_FOUND`.

### POST /requests/{id}/items/{itemId}/accept
Accept one offer line for one item. Creates the service order.
```json
{ "lineId": 55, "appointmentStart": "2026-11-09T12:00:00Z" }
```
- `appointmentStart` optional; defaults to the line's `proposedStart`. Without both, the order has no appointment
  yet (set it later with reschedule).
- Competing **pending** lines for the **same item** are declined automatically; other items are untouched.

Response `201`: **OrderDetail**.
Errors: `OFFER_NOT_PENDING` (409), `OFFER_EXPIRED` (409), `ITEM_NOT_OPEN` (409), `REQUEST_DEADLINE_PASSED` (409),
`OFFER_NOT_FOUND`, `PROVIDER_NOT_ELIGIBLE` (provider suspended meanwhile), `SELF_DEALING_FORBIDDEN`,
`APPOINTMENT_IN_PAST` (422), `OUTSIDE_WORKING_HOURS` (422), `SLOT_FULL` (409, branch capacity reached at that time).

### POST /requests/{id}/lines/{lineId}/decline
No body. Declines one pending offer line. Response: **RequestDetail**. Errors: `OFFER_NOT_PENDING`, `OFFER_NOT_FOUND`.

**Request status** (summary of its items):

| Status | Meaning |
|---|---|
| MATCHING | no accepted item, no offers yet |
| OFFERS_AVAILABLE | no accepted item, at least one pending offer |
| PARTIALLY_MATCHED | some items accepted, others still open |
| CLOSED | every item accepted, canceled or expired, at least one accepted |
| CANCELED | everything canceled, nothing accepted |
| EXPIRED | the response deadline passed with nothing accepted |

Item status: `MATCHING`, `OFFERS_AVAILABLE`, `ACCEPTED`, `CANCELED`, `EXPIRED`. Deadline = creation + 48 h (config).

---

## 10. Provider inbox & offers

All *operating*. Sending or changing offers also needs a subscription that is not `RESTRICTED`
(`403 SUBSCRIPTION_RESTRICTED`).

### GET /provider/requests?status=&page=&size=
Requests sent to me. `status` optional: `INVITED` | `VIEWED` | `OFFERED` | `DECLINED`.
```json
{
  "items": [
    {
      "requestId": 1, "targetStatus": "INVITED", "requestStatus": "MATCHING", "areaId": 1,
      "vehicleLabel": "Hyundai Elantra 2019", "matchingCategories": ["BATTERIES", "OIL_CHANGE"],
      "responseDeadline": "2026-11-10T19:18:50Z", "distanceKm": 3.40, "invitedAt": "2026-11-08T19:18:50Z", "myOfferId": 1
    }
  ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1
}
```
`matchingCategories`: only the categories I offer. `myOfferId` present when I already sent an offer.

### GET /provider/requests/{id}
Opening it marks the invitation `VIEWED`. Shows **only the items in my categories**; no customer contact data before acceptance.
```json
{
  "requestId": 1, "requestStatus": "MATCHING", "targetStatus": "VIEWED",
  "vehicle": { "...": "VehicleSnapshot ?" },
  "areaId": 1, "address": "Nasr City", "latitude": 30.05, "longitude": 31.34,
  "preferredDate": "2026-11-09", "preferredTimeFrom": "10:00", "preferredTimeTo": "14:00",
  "notes": "Available after 4 pm", "photoKeys": ["....jpg"], "responseDeadline": "2026-11-10T19:18:50Z",
  "items": [ { "id": 1, "category": { "...": "Category" }, "description": "Car does not start", "status": "MATCHING" } ],
  "myOffer": { "...": "ProviderOffer ?" }
}
```
Errors: `NOT_TARGETED` (403).

### POST /provider/requests/{id}/decline
No body. Not interested. Response: `null`. Errors: `NOT_TARGETED`, `OFFER_ALREADY_EXISTS` (withdraw the offer instead).

### POST /provider/requests/{id}/offer
One offer per provider per request, with one line per item.
```json
{
  "branchId": 1,
  "validHours": 24,
  "notes": "Can start today.",
  "lines": [
    {
      "serviceItemId": 1,
      "available": true,
      "servicePrice": 300,
      "laborCost": 100,
      "discount": 0,
      "parts": [ { "name": "Battery 70Ah", "quantity": 1, "unitPrice": 2500 } ],
      "estimatedMinutes": 45,
      "proposedStart": "2026-11-09T12:00:00Z",
      "notes": "Original battery",
      "warranty": { "provided": true, "durationDays": 365, "coverage": "PARTS", "exclusions": "Physical damage", "conditions": "Keep the invoice" }
    },
    { "serviceItemId": 2, "available": false }
  ]
}
```
| Field | Rules |
|---|---|
| branchId | required, one of my **active** branches |
| validHours | optional (default from config), within `offerValidityMinHours..MaxHours`; expiry is capped at the request deadline |
| notes | optional ≤ 2000 |
| lines | 1–7, each `serviceItemId` once, each item open and in a category I offer; at least one `available: true` |
| available=false | "not currently serviceable" — no prices or warranty needed |
| servicePrice / laborCost / discount | ≥ 0, 2 decimals; discount ≤ price; total must be > 0 |
| parts | ≤ 50; `name` ≤ 120, `quantity` 1–1000, `unitPrice` ≥ 0 |
| estimatedMinutes | optional 5–10080 |
| proposedStart | optional, in the future |
| warranty | **required for available lines**: `{ "provided": false }` or `provided: true` with `durationDays` 1–3650 and `coverage` `PARTS`\|`LABOR`\|`PARTS_AND_LABOR` |

Never send totals or commission — the server computes them.

Response `201`: **ProviderOffer**.
Errors: `NOT_TARGETED` (403), `CATEGORY_NOT_OFFERED` (422), `OFFER_ALREADY_EXISTS` (409), `OFFER_LOCKED` (409),
`ITEM_NOT_OPEN`, `REQUEST_DEADLINE_PASSED`, `INVALID_EXPIRY` (422), `APPOINTMENT_IN_PAST`, `SUBSCRIPTION_RESTRICTED`, `VALIDATION_FAILED`.
A withdrawn or expired offer can be sent again with this endpoint.

### PUT /provider/offers/{id}
Replace an `ACTIVE` offer's lines while **none is accepted**. Same body as above; expiry is recomputed from `validHours`.
Response: **ProviderOffer**. Errors: `OFFER_LOCKED` (409), `OFFER_NOT_PENDING`, `OFFER_NOT_FOUND`, + the submit errors.

### POST /provider/offers/{id}/withdraw
No body. Pending lines → `WITHDRAWN`. Response: **ProviderOffer**. Errors: `OFFER_NOT_PENDING`.

### GET /provider/offers?status=&page=&size=
`status` optional: `ACTIVE` | `WITHDRAWN` | `EXPIRED` | `CLOSED`. Response: Page<**ProviderOffer**>.

Offer line status: `PENDING`, `UNAVAILABLE`, `ACCEPTED`, `DECLINED`, `WITHDRAWN`, `EXPIRED`.

---

## 11. Service orders

Customer and provider of the order (and staff) can read it; strangers get `404 ORDER_NOT_FOUND`. Calling an action
that belongs to the other party → `403 ACCESS_DENIED`; wrong state → `409 ORDER_INVALID_STATE`.
**Every action returns the refreshed `OrderDetail`** unless stated otherwise — re-render from it.

### GET /orders?as=customer|provider&status=&page=&size=
`as` default `customer`. `status` optional (see enums). Response: Page<**OrderSummary**>.

### GET /orders/{id}
Response: **OrderDetail**.

### GET /orders/{id}/events
Timeline, oldest first. Response: `[OrderEvent]`.

### Execution
| Endpoint | Who | When | Body | Result |
|---|---|---|---|---|
| `POST /orders/{id}/start` | provider | `ACCEPTED` | — | `IN_PROGRESS` |
| `POST /orders/{id}/mark-completed` | provider | `IN_PROGRESS` | `{ "note": "optional ≤ 1000" }` (body optional) | `PROVIDER_MARKED_COMPLETED`; cash becomes `DUE`; customer asked to confirm |
| `POST /orders/{id}/confirm-completion` | customer | `PROVIDER_MARKED_COMPLETED` | — | `COMPLETED`: invoice number, warranty starts, rating allowed |
| `POST /orders/{id}/complain` | customer | `PROVIDER_MARKED_COMPLETED` | `{ "description": "required ≤ 4000", "evidenceKeys": ["EVIDENCE keys", "≤ 10"] }` | order `DISPUTED`; returns `201` **Dispute** (type `SERVICE_QUALITY`) |

There is **no automatic completion** — only the customer (or staff deciding a dispute) completes an order.

### Before work starts
| Endpoint | Who | When | Body | Result |
|---|---|---|---|---|
| `POST /orders/{id}/cancel` | either party | `ACCEPTED` | `{ "reason": "required ≤ 500" }` | `CANCELED` (`CUSTOMER_CANCELED`/`PROVIDER_CANCELED`). Free in V1 |
| `POST /orders/{id}/reschedule` | either party | `ACCEPTED` | `{ "appointmentStart": "2026-11-10T12:00:00Z", "reason": "optional ≤ 500" }` | new appointment |
| `POST /orders/{id}/report-no-show` | either party | `ACCEPTED`, now ≥ appointment + 30 min (config) | — | `CANCELED`; customer reporting → `PROVIDER_NO_SHOW`, provider reporting → `CUSTOMER_NO_SHOW` |

Reschedule errors: `APPOINTMENT_IN_PAST`, `OUTSIDE_WORKING_HOURS`, `SLOT_FULL`. No-show errors: `NO_SHOW_TOO_EARLY` (409).

### Interruption (stopping work that has started)
| Endpoint | Who | When | Body | Result |
|---|---|---|---|---|
| `POST /orders/{id}/request-interruption` | customer | `IN_PROGRESS` | `{ "reason": "required ≤ 500" }` | `INTERRUPTION_REQUESTED` |
| `POST /orders/{id}/interruption-statement` | provider | `INTERRUPTION_REQUESTED` | see below | `INTERRUPTION_STATEMENT_SUBMITTED` |
| `POST /orders/{id}/accept-interruption` | customer | `INTERRUPTION_STATEMENT_SUBMITTED` | — | `INTERRUPTED`; `amountDue` = proposed amount |
| `POST /orders/{id}/dispute-interruption` | customer | `INTERRUPTION_STATEMENT_SUBMITTED` | `{ "description": "...", "evidenceKeys": [] }` | `DISPUTED`; returns `201` **Dispute** (type `INTERRUPTION`) |

Statement body:
```json
{ "workDone": "Diagnosis and parts removal", "laborCost": 100, "partsCost": 50, "proposedAmount": 150, "evidenceKeys": ["....jpg"] }
```
`workDone` required ≤ 4000; amounts ≥ 0; `proposedAmount` ≤ the current `amountDue`; evidence keys uploaded with `purpose=EVIDENCE`, ≤ 10.

### Cash collection (independent from execution)
| Endpoint | Who | When | Body | Result |
|---|---|---|---|---|
| `POST /orders/{id}/cash/report` | provider | order `IN_PROGRESS`, `PROVIDER_MARKED_COMPLETED`, `COMPLETED` or `INTERRUPTED`; cash `NOT_DUE`/`DUE`/`PARTIAL` | `{ "amount": 2050.00 }` (> 0, **total received so far**) | `REPORTED_COLLECTED` if amount ≥ `amountDue`, else `PARTIAL` |
| `POST /orders/{id}/cash/confirm` | customer | cash `REPORTED_COLLECTED`, or `PARTIAL` not yet confirmed | — | `CONFIRMED` (a partial report stays `PARTIAL`, marked confirmed) |
| `POST /orders/{id}/cash/dispute` | customer | same as confirm | `{ "description": "required ≤ 4000" }` | cash `DISPUTED`; returns `201` **Dispute** (type `CASH_COLLECTION`); a review flag is raised |

Errors: `CASH_INVALID_STATE` (409), `ORDER_INVALID_STATE`.
UI wording: "reported as collected" — never imply WaDii verified the cash.

### Additional work (charges)
| Endpoint | Who | When | Body | Response |
|---|---|---|---|---|
| `POST /orders/{id}/charges` | provider | `IN_PROGRESS` | `{ "description": "Extra filter (≤ 500)", "amount": 150.00 }` | `201` **Charge** (`PENDING`) |
| `POST /orders/{id}/charges/{chargeId}/approve` | customer | charge `PENDING`, order not finished | — | **Charge** (`APPROVED`); added to `amountDue` |
| `POST /orders/{id}/charges/{chargeId}/reject` | customer | same | — | **Charge** (`REJECTED`) |

### Rating
`POST /orders/{id}/rating` — customer, order `COMPLETED`, once.
```json
{ "stars": 5, "comment": "Great service" }
```
`stars` 1–5, `comment` optional ≤ 2000. Response `201`: **Rating**. Errors: `RATING_NOT_ALLOWED`, `RATING_ALREADY_EXISTS` (409).

**Order status:** `ACCEPTED`, `IN_PROGRESS`, `PROVIDER_MARKED_COMPLETED`, `COMPLETED`, `INTERRUPTION_REQUESTED`,
`INTERRUPTION_STATEMENT_SUBMITTED`, `INTERRUPTED`, `DISPUTED`, `CANCELED`.
**Cash status:** `NOT_DUE`, `DUE`, `REPORTED_COLLECTED`, `CONFIRMED`, `PARTIAL`, `DISPUTED`.

---

## 12. Warranty

A warranty starts when the customer confirms completion of an order whose accepted line had `warranty.provided = true`.

### GET /warranties?as=customer|provider
`as` default `customer`. Response: `[WarrantyView]` (not paged).

### POST /warranties/{id}/claims
Customer; warranty must be `active`; one claim in progress (`OPEN`/`ACCEPTED`) per warranty.
```json
{ "description": "The battery died again", "evidenceKeys": ["....jpg"] }
```
`description` required ≤ 4000; `evidenceKeys` ≤ 10 (`purpose=EVIDENCE`). Response `201`: **WarrantyClaim**.
Errors: `WARRANTY_NOT_FOUND`, `WARRANTY_NOT_ACTIVE` (409), `CLAIM_INVALID_STATE` (409).

### GET /warranty-claims?as=customer|provider&page=&size=
Response: Page<**WarrantyClaim**>. `as=provider` without a provider profile → `404 PROVIDER_NOT_FOUND`.

### POST /warranty-claims/{id}/respond
Provider of the warranty; claim `OPEN`.
```json
{ "accept": true, "note": "Bring the car tomorrow, we will replace it." }
```
`note` required ≤ 2000. Response: **WarrantyClaim** (`ACCEPTED` or `REJECTED`). Errors: `CLAIM_INVALID_STATE`.

### POST /warranty-claims/{id}/resolve
Customer confirms an `ACCEPTED` claim was handled. No body. Response: **WarrantyClaim** (`RESOLVED`).

### POST /warranty-claims/{id}/escalate
Customer, claim `REJECTED` or `ACCEPTED` (not honoured). Opens a `WARRANTY` dispute.
```json
{ "description": "The provider refused although it is covered." }
```
Response `201`: **Dispute**. The claim becomes `ESCALATED` with `disputeId` set.

Claim status: `OPEN`, `ACCEPTED`, `REJECTED`, `ESCALATED`, `RESOLVED`.

---

## 13. Disputes

Disputes about orders are opened by order/warranty actions (`complain`, `dispute-interruption`, `cash/dispute`,
`escalate`). This section covers reading them and the remaining types.

### GET /disputes?page=&size=
Disputes I opened or that were opened against me. Response: Page<**Dispute**>.

### GET /disputes/{id}
Parties and staff. Response: **Dispute**. Others → `404 DISPUTE_NOT_FOUND`.

### POST /disputes
For cancellation/no-show disagreements, listing complaints and other issues.
```json
{ "type": "CANCELLATION", "orderId": 12, "description": "The provider never came but reported me as no-show.", "evidenceKeys": [] }
```
```json
{ "type": "LISTING", "listingId": 4, "description": "The item was not as described." }
```
| Field | Rules |
|---|---|
| type | `CANCELLATION` \| `LISTING` \| `OTHER` (other types come from the order/warranty actions) |
| orderId / listingId | one is required; you must be a party of the order / not the seller of the listing |
| description | required ≤ 4000 |
| evidenceKeys | optional ≤ 10 |

Response `201`: **Dispute**. Errors: `DISPUTE_ALREADY_OPEN` (409), `ORDER_NOT_FOUND`, `LISTING_NOT_FOUND`, `VALIDATION_FAILED`.

### POST /disputes/{id}/evidence
Party or staff, while the dispute is `OPEN`/`UNDER_REVIEW`. Send a note, a file, or both.
```json
{ "note": "Photo of the receipt", "fileKey": "....jpg" }
```
`note` ≤ 4000; `fileKey` uploaded with `purpose=EVIDENCE`. Response: **Dispute**. Errors: `DISPUTE_INVALID_STATE`.

Dispute status: `OPEN`, `UNDER_REVIEW`, `RESOLVED`, `REJECTED`. Staff decide (section 18).

---

## 14. Used-item listings (seller)

Any non-staff account can sell. Selling never makes the account a provider.

### GET /listings?category=&areaId=&q=&minPrice=&maxPrice=&page=&size=
**Public.** `ACTIVE` listings, newest first. All filters optional: `category` (listing category enum), `areaId`,
`q` (title/description text), `minPrice`, `maxPrice`. Response: Page<**Listing**>.

### GET /listings/{id}
**Public** for `ACTIVE`/`SOLD`; owner and staff see every status (send the token). Response: **Listing**.

### POST /listings
Creates a `DRAFT`.
```json
{
  "title": "Original Toyota alloy wheels (set of 4)",
  "category": "ACCESSORIES",
  "condition": "GOOD",
  "description": "15 inch, minor scratches, no bends.",
  "price": 6500,
  "areaId": 1,
  "locationText": "Maadi",
  "contactMethod": "CHAT",
  "photoKeys": ["....jpg"]
}
```
| Field | Rules |
|---|---|
| title | 3–120 |
| category | `SPARE_PARTS` \| `BATTERIES` \| `TIRES` \| `ACCESSORIES` \| `TOOLS` \| `OTHER` |
| condition | `NEW` \| `LIKE_NEW` \| `GOOD` \| `FAIR` \| `FOR_PARTS` |
| description | required ≤ 4000 |
| price | ≥ 1, 2 decimals |
| areaId | required |
| locationText | optional ≤ 200 |
| contactMethod | `CHAT` \| `PHONE` (PHONE shows the seller's phone while active) |
| photoKeys | ≤ 8 keys uploaded with `purpose=LISTING_PHOTO` |

Response `201`: **Listing**.

### PUT /listings/{id}
Owner, while `DRAFT` or `REJECTED`. Same body. Response: **Listing**. Errors: `LISTING_INVALID_STATE` (409).

### POST /listings/{id}/submit
`DRAFT`/`REJECTED` → `PENDING_REVIEW` (or straight to `ACTIVE` when moderation is off). No body.
Quota: listings in `PENDING_REVIEW` + `ACTIVE` must stay under `maxActiveListings`.
Response: **Listing**. Errors: `LISTING_LIMIT_REACHED` (409), `LISTING_INVALID_STATE`.

### POST /listings/{id}/mark-sold
`ACTIVE` → `SOLD`. Response: **Listing**.

### POST /listings/{id}/close
`DRAFT`/`PENDING_REVIEW`/`ACTIVE` → `CLOSED`. Response: **Listing**.

### GET /me/listings?status=&page=&size=
My listings, any status. Response: Page<**Listing**>.

### GET /me/seller
```json
{ "activeListings": 1, "maxActiveListings": 3, "feesDue": 25.00, "fees": [ { "...": "LedgerEntry (type SELLER_FEE)" } ] }
```

### POST /me/ledger/{id}/report-paid
Tell WaDii you paid an amount you owe (seller fees). Same body/response as the provider version (section 17).

Listing status: `DRAFT`, `PENDING_REVIEW`, `ACTIVE`, `SOLD`, `CLOSED`, `REJECTED`, `EXPIRED` (after `listingDurationDays`).

---

## 15. Chat

Conversations belong to an order, a request (customer ↔ invited provider) or a listing (buyer ↔ seller).
There is no websocket — poll `messages?afterId=` every few seconds while a thread is open.

### GET /conversations?page=&size=
Mine, most recent first. Response: Page<**Conversation**>.

### GET /conversations/unread-count
```json
{ "count": 2 }
```
Unread messages across all my conversations (for the badge).

### POST /conversations
Get or create (answers **200**, not 201).
```json
{ "kind": "ORDER", "refId": 12 }
```
```json
{ "kind": "REQUEST", "refId": 1, "providerId": 7 }
```
```json
{ "kind": "LISTING", "refId": 4 }
```
| kind | Who can open | Other party |
|---|---|---|
| ORDER | customer or provider of the order | the other one |
| REQUEST | the customer (needs `providerId` of an invited provider) or an invited provider | customer ↔ provider |
| LISTING | anyone except the seller (listing `ACTIVE`/`SOLD`) | the seller |

Response: **Conversation**. Errors: `ORDER_NOT_FOUND`, `REQUEST_NOT_FOUND`, `LISTING_NOT_FOUND`, `VALIDATION_FAILED`.

### GET /conversations/{id}/messages?afterId=&limit=
Ascending by id. `afterId` (default 0) = only newer messages; `limit` 1–100 (default 50). Reading marks them read.
Response: `[Message]`. Strangers → `404 CONVERSATION_NOT_FOUND` (support staff can read any thread).

### POST /conversations/{id}/messages
```json
{ "body": "On my way", "fileKey": "....jpg" }
```
Text (≤ 4000) or a file (`purpose=CHAT`) or both. Response `201`: **Message**.

---

## 16. Notifications

The inbox is the source of truth; push (FCM) only delivers a copy. Push data contains `type`, and when relevant `refType` and `refId`.

### GET /notifications?unreadOnly=false&page=&size=
Newest first. Response: Page<**Notification**>.

### GET /notifications/unread-count
```json
{ "count": 3 }
```

### POST /notifications/{id}/read
Response: `null`.

### POST /notifications/read-all
Response: `null`.

Routing a notification (by `refType` + `refId`):

| refType | Open |
|---|---|
| REQUEST | type `NEW_MATCHING_REQUEST` → provider request view `/provider/requests/{refId}`; otherwise customer `/requests/{refId}` |
| OFFER | provider: my offers |
| ORDER | `/orders/{refId}` |
| DISPUTE | `/disputes/{refId}` |
| WARRANTY_CLAIM | warranty claims |
| LISTING | `/listings/{refId}` |
| SUBSCRIPTION / LEDGER | provider subscription & ledger (or seller fees) |
| APPLICATION | provider application |
| CONVERSATION | `/conversations/{refId}` thread |
| USER | account screen |

---

## 17. Provider dashboard & finance

### GET /provider/dashboard?days=30|90  *(operating)*
```json
{
  "windowDays": 30,
  "operations": {
    "invited": 40, "viewed": 35, "offersSubmitted": 20, "responseRate": 0.5, "avgResponseMinutes": 42,
    "offersAccepted": 9, "completedOrders": 7, "providerCancellations": 1, "customerCancellations": 2, "noShows": 0, "activeOrders": 3
  },
  "quality": {
    "ratingAverage": 4.60, "ratingCount": 12, "recentRatingAverage": 4.80, "disputes": 1, "disputeRate": 0.1,
    "warrantyClaims": 1, "completionRate": 0.875
  },
  "finance": {
    "grossAccepted": 25000.00, "grossCompleted": 18000.00, "cashReportedCollected": 17000.00, "cashConfirmed": 15000.00,
    "cashDisputed": 0.00, "commissionAccrued": 0.00, "commissionSettled": 0.00, "outstandingBalance": 0.00
  },
  "subscription": { "...": "ProviderSubscription" }
}
```
Rates are 0..1. `avgResponseMinutes` / `recentRatingAverage` absent when there is no data. Cash values are
**self-reported** — label them that way. `days` other than 90 means 30.

### GET /provider/subscription
Works even when restricted (so the provider can see what to pay). Response: **ProviderSubscription**.

Status: `FREE_OR_DISABLED` (price 0), `ACTIVE`, `REMINDER_DUE` (due within the reminder window),
`PAYMENT_OVERDUE` (past due, still inside the grace period — full access), `RESTRICTED` (after grace — no new
requests/offers; customer mode and existing orders continue), `EXEMPT`.

### GET /provider/ledger?type=&status=&page=&size=
Amounts I owe WaDii. Filters: `type` (`SUBSCRIPTION` \| `COMMISSION` \| `SELLER_FEE` \| `CANCELLATION_FEE` \| `ADJUSTMENT`),
`status` (`DUE` \| `REPORTED_PAID` \| `SETTLED` \| `WAIVED` \| `CANCELED`). Response: Page<**LedgerEntry**>.

### POST /provider/ledger/{id}/report-paid
Payer only, entry `DUE`. WaDii finance then confirms (`SETTLED`).
```json
{ "method": "BANK_TRANSFER", "reference": "TRX-88123" }
```
`method`: `CASH` | `BANK_TRANSFER` | `WALLET`; `reference` optional ≤ 120. Response: **LedgerEntry** (`REPORTED_PAID`).
Errors: `LEDGER_INVALID_STATE` (409), `ACCESS_DENIED`.

---

## 18. Admin

All under `/admin/**`, staff accounts only. The required sub-role is shown per endpoint (`SUPER_ADMIN` holds all roles).
Missing role → `403 ACCESS_DENIED`. List filters are optional query parameters.

### 18.1 Users & staff

#### GET /admin/users?q=&status=&admin=&page=&size=  [SUPPORT]
`q` searches name/email/phone; `status` `ACTIVE`\|`SUSPENDED`\|`BANNED`; `admin=true` staff only, `admin=false` regular users only.
```json
{
  "items": [
    {
      "id": 3, "fullName": "Mona Adel", "email": "mona@example.com", "phone": "01011111111", "status": "ACTIVE",
      "admin": false, "adminRoles": [], "providerId": 7, "applicationStatus": "VERIFIED",
      "statusReason": "?", "createdAt": "2026-11-01T10:00:00Z"
    }
  ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1
}
```
(**AdminUser** = one item.)

#### POST /admin/users/{id}/suspend · POST /admin/users/{id}/ban · POST /admin/users/{id}/reactivate  [SUPPORT]
```json
{ "reason": "Repeated no-shows (≤ 500, required)" }
```
Response: **AdminUser**. Suspend/ban revokes all the user's tokens. Only a super admin can change another staff
account; nobody can change their own status (`VALIDATION_FAILED`).

#### POST /admin/admins  [SUPER]
```json
{ "fullName": "Support Sam", "email": "sam@wadii.app", "phone": "01122223333", "password": "StrongPass123!", "roles": ["SUPPORT_ADMIN"] }
```
`password` 12–72; `roles` non-empty set of `SUPER_ADMIN`\|`SUPPORT_ADMIN`\|`FINANCE_ADMIN`\|`CONTENT_ADMIN`.
Response `201`: **AdminUser**. Errors: `EMAIL_ALREADY_USED`, `PHONE_ALREADY_USED`.

#### PUT /admin/admins/{id}/roles  [SUPER]
```json
{ "roles": ["SUPPORT_ADMIN", "FINANCE_ADMIN"] }
```
Response: **AdminUser** (the staff member's tokens are revoked). You cannot remove your own `SUPER_ADMIN`.

### 18.2 Provider applications & providers  [SUPPORT]

| Endpoint | Body | Response |
|---|---|---|
| `GET /admin/provider-applications?status=&page=&size=` (default: `SUBMITTED` + `UNDER_REVIEW`, oldest first) | — | Page<**ProviderApplication**> |
| `GET /admin/provider-applications/{providerId}` | — | **ProviderApplication** (open documents with `GET /files/{fileKey}` + token) |
| `POST /admin/provider-applications/{providerId}/start-review` (`SUBMITTED` → `UNDER_REVIEW`) | — | **ProviderApplication** |
| `POST /admin/provider-applications/{providerId}/approve` (→ `VERIFIED`) | — | **ProviderApplication** |
| `POST /admin/provider-applications/{providerId}/reject` (→ `REJECTED`) | `{ "reason": "Blurry ID" }` | **ProviderApplication** |
| `GET /admin/providers?q=&status=ACTIVE\|SUSPENDED&page=&size=` (verified providers) | — | Page<**ProviderApplication**> |
| `POST /admin/providers/{id}/suspend` | `{ "reason": "..." }` | **ProviderApplication** |
| `POST /admin/providers/{id}/reactivate` | `{ "reason": "..." }` | **ProviderApplication** |

Wrong state → `409 APPLICATION_INVALID_STATE`. Suspending withdraws the provider's pending offers and open
invitations; existing orders continue (support handles them individually).

### 18.3 Categories & configuration

#### GET /admin/categories  [any admin]
All categories including inactive. Response: `[Category]`.

#### PUT /admin/categories/{id}  [SUPER]
```json
{ "nameEn": "Batteries", "nameAr": "بطاريات", "vehicleRequired": true, "active": true, "sortOrder": 2 }
```
Names required ≤ 80; `sortOrder` 0–1000. Response: **Category**.

#### GET /admin/config/{key}  [any admin]
`key`: `MARKETPLACE` | `COMMISSION` | `SUBSCRIPTION` | `SELLER`.
```json
{
  "current": { "id": 7, "key": "COMMISSION", "payload": { "mode": "NONE", "value": 0, "payer": "PROVIDER" }, "effectiveFrom": "2026-11-07T19:18:00Z", "createdByUserId": 1, "reason": "Initial V1 configuration", "createdAt": "2026-11-07T19:18:00Z" },
  "history": [ { "...": "ConfigVersion, newest first" } ]
}
```

#### POST /admin/config/{key}  [SUPER for MARKETPLACE; FINANCE for COMMISSION, SUBSCRIPTION, SELLER]
Publishes a new version (versions are never edited).
```json
{ "payload": { "mode": "PERCENT", "value": 10, "payer": "CUSTOMER" }, "effectiveFrom": "2026-12-01T00:00:00Z", "reason": "Start commission" }
```
`effectiveFrom` optional (default/past = now); `reason` required ≤ 500. The payload must contain **every** field of the key:

| key | payload fields |
|---|---|
| MARKETPLACE | `responseDeadlineHours` 1–336, `offerValidityDefaultHours`, `offerValidityMinHours` ≥ 1, `offerValidityMaxHours` (min ≤ default ≤ max), `maxProviderTargets` 1–200, `defaultMatchRadiusKm` 1–500, `noShowGraceMinutes` 0–240, `rankingWindowDays` 7–365, `providerCancellationFlagThreshold`, `customerCancellationFlagThreshold`, `disputeFlagThreshold` (≥ 1) |
| COMMISSION | `mode` `NONE`\|`FIXED`\|`PERCENT`, `value` ≥ 0 (≤ 100 for PERCENT), `payer` `CUSTOMER`\|`PROVIDER` |
| SUBSCRIPTION | `monthlyPrice` ≥ 0 (0 = free), `graceDays` 0–60, `reminderDaysBefore` list of 1–30 (e.g. `[7, 3]`) |
| SELLER | `maxActiveListings` 1–100, `feeMode` `NONE`\|`FIXED`\|`PERCENT`, `feeValue` ≥ 0, `feeTiming` `ON_PUBLISH`\|`ON_SOLD`, `listingDurationDays` 1–365, `moderationRequired` boolean |

Response `201`: **ConfigVersion**. Errors: `CONFIG_INVALID` (422, message says which rule), `ACCESS_DENIED`.

### 18.4 Requests, orders, disputes, warranty

| Endpoint | Role | Response |
|---|---|---|
| `GET /admin/requests?status=&page=&size=` | SUPPORT | Page<**RequestSummary**> (open details with `GET /requests/{id}`) |
| `GET /admin/orders?status=&cashStatus=&providerId=&categoryId=&page=&size=` | SUPPORT or FINANCE | Page<**OrderSummary**> (details with `GET /orders/{id}`, `myRole = ADMIN`) |
| `POST /admin/orders/{id}/cancel` body `{ "reason": "..." }` | SUPPORT | **OrderDetail** (`ADMIN_CANCELED`) |
| `GET /admin/disputes?status=&type=&page=&size=` | SUPPORT or FINANCE | Page<**Dispute**> |
| `GET /admin/disputes/{id}` | SUPPORT or FINANCE | **Dispute** |
| `POST /admin/disputes/{id}/assign` (to me, → `UNDER_REVIEW`) | SUPPORT or FINANCE | **Dispute** |
| `POST /admin/disputes/{id}/resolve` | SUPPORT or FINANCE (see below) | **Dispute** |
| `POST /admin/disputes/{id}/reject` body `{ "note": "..." }` | SUPPORT | **Dispute** |
| `GET /admin/warranty-claims?status=&page=&size=` | SUPPORT | Page<**WarrantyClaim**> |

Resolve body:
```json
{ "outcome": "SET_FINAL_AMOUNT", "note": "Only diagnosis was done", "finalAmount": 120.00 }
```
| Dispute type | Allowed outcomes |
|---|---|
| SERVICE_QUALITY | `COMPLETE_ORDER`, `REOPEN_ORDER` (back to `IN_PROGRESS`), `CANCEL_ORDER`, `SET_FINAL_AMOUNT` (completes with that amount) |
| INTERRUPTION | `SET_FINAL_AMOUNT` (order `INTERRUPTED` with that amount), `CANCEL_ORDER` |
| CASH_COLLECTION | `CASH_CONFIRMED`, `CASH_NOT_COLLECTED` (cash back to `DUE`) |
| WARRANTY | `WARRANTY_HONORED` (claim back to `ACCEPTED`), `NO_ACTION` |
| CANCELLATION, LISTING, OTHER | `NO_ACTION` |

Roles: `SET_FINAL_AMOUNT`, `CASH_CONFIRMED`, `CASH_NOT_COLLECTED` need **FINANCE**; all other outcomes need **SUPPORT**.
`finalAmount` (≥ 0) is required for `SET_FINAL_AMOUNT`. `note` required ≤ 4000.
**Reject** = the complaint does not stand: the order goes back to its normal course (a quality complaint completes
the order, a cash report stands, an interruption proposal applies).
Errors: `DISPUTE_INVALID_STATE` (409, already decided), `VALIDATION_FAILED` (outcome not allowed), `ORDER_INVALID_STATE`.

### 18.5 Finance  [FINANCE]

#### GET /admin/subscriptions?status=&page=&size=
```json
{
  "items": [ { "providerId": 7, "businessName": "Fathy Auto Electric", "status": "PAYMENT_OVERDUE", "monthlyPrice": 300.00,
               "currentPeriodStart": "2026-10-15", "nextDueDate": "2026-11-15", "graceEndsOn": "2026-11-22", "exemptUntil": "?", "lastPaidAt": "?" } ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1
}
```

#### POST /admin/subscriptions/{providerId}/payments
Record a payment received outside the app; settles the open subscription entry and starts the next cycle from `paidOn`.
```json
{ "amount": 300.00, "paidOn": "2026-11-08", "method": "CASH", "reference": "Receipt 1182" }
```
`paidOn` not in the future. Response `201`: **ProviderSubscription**.

#### POST /admin/subscriptions/{providerId}/exemptions
```json
{ "months": 3, "reason": "Launch partner" }
```
or `{ "until": "2027-02-01", "reason": "..." }`. `months` 1–36; `reason` required ≤ 500. Response `201`: **ProviderSubscription** (`EXEMPT`).

#### GET /admin/ledger?type=&status=&providerId=&page=&size=
Response: Page<**LedgerEntry**>.

#### POST /admin/ledger/{id}/settle
```json
{ "method": "BANK_TRANSFER", "reference": "TRX-88123", "note": "Checked on statement" }
```
Entry `DUE` or `REPORTED_PAID` → `SETTLED`. Response: **LedgerEntry**. Errors: `LEDGER_INVALID_STATE`.

#### POST /admin/ledger/{id}/waive
```json
{ "note": "Goodwill (required ≤ 1000)" }
```
Response: **LedgerEntry** (`WAIVED`).

#### POST /admin/ledger/adjustments
```json
{ "payerUserId": 5, "providerId": 1, "amount": 50.00, "note": "Late remittance fee" }
```
`amount` > 0 (an amount the user owes); `providerId` optional. Response `201`: **LedgerEntry** (type `ADJUSTMENT`, `DUE`).

### 18.6 Content & trust

| Endpoint | Role | Body | Response |
|---|---|---|---|
| `GET /admin/listings?status=&page=&size=` | CONTENT | — | Page<**Listing**> |
| `POST /admin/listings/{id}/approve` (`PENDING_REVIEW` → `ACTIVE`) | CONTENT | — | **Listing** |
| `POST /admin/listings/{id}/reject` | CONTENT | `{ "reason": "Prohibited item" }` | **Listing** (`REJECTED`) |
| `GET /admin/ratings?page=&size=` | CONTENT | — | Page<**Rating**> (full names, hidden included) |
| `POST /admin/ratings/{id}/hide` · `POST /admin/ratings/{id}/unhide` | CONTENT | — | **Rating** |
| `GET /admin/fraud-flags?status=OPEN\|DISMISSED\|ACTIONED&page=&size=` | SUPPORT | — | Page<**FraudFlag**> |
| `POST /admin/fraud-flags` (manual flag) | SUPPORT | `{ "subjectUserId": 5, "note": "..." }` | `201` **FraudFlag** |
| `POST /admin/fraud-flags/{id}/dismiss` · `POST /admin/fraud-flags/{id}/action` | SUPPORT | `{ "note": "..." }` | **FraudFlag** |

**FraudFlag**:
```json
{ "id": 3, "type": "CASH_CONFLICT", "subjectUserId": 5, "details": "Customer disputed the cash reported...", "status": "OPEN",
  "reviewedBy": "?", "reviewNote": "?", "reviewedAt": "?", "createdAt": "2026-11-09T14:00:00Z" }
```
Flags are for review only; nothing is blocked automatically. A flag can be reviewed once (`409 CONFLICT` afterwards).

### 18.7 Reports & audit

#### GET /admin/reports/summary?from=2026-10-09&to=2026-11-08  [any admin]
Dates `yyyy-MM-dd` (Cairo, inclusive). Default: last 30 days.
```json
{
  "from": "2026-10-09", "to": "2026-11-08",
  "requests": { "total": 120, "byStatus": { "CLOSED": 80, "EXPIRED": 10, "OFFERS_AVAILABLE": 30 } },
  "orders": { "total": 95, "byStatus": { "COMPLETED": 70, "CANCELED": 5 }, "byCategory": { "BATTERIES": 30, "TIRES": 20 } },
  "users": { "total": 900, "newInRange": 150, "providersVerified": 40, "pendingApplications": 6 },
  "finance": { "commissionAccrued": 0.00, "commissionSettled": 0.00, "subscriptionDue": 600.00, "subscriptionSettled": 3000.00,
               "sellerFeesDue": 0.00, "sellerFeesSettled": 0.00, "overdueProviders": 2 },
  "trust": { "openDisputes": 3, "openFraudFlags": 1, "openWarrantyClaims": 2, "cancellations": 5, "noShows": 1 }
}
```

#### GET /admin/audit?entityType=&entityId=&page=&size=  [SUPER]
Newest first.
```json
{ "items": [ { "id": 501, "actorId": 1, "action": "PROVIDER_VERIFIED", "entityType": "PROVIDER", "entityId": 7, "details": "?", "ip": "127.0.0.1", "createdAt": "2026-11-03T10:00:00Z" } ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1 }
```
`entityType` examples: `USER`, `PROVIDER`, `REQUEST`, `OFFER`, `ORDER`, `DISPUTE`, `LEDGER`, `LISTING`, `CATEGORY`, `CONFIG_COMMISSION`, `ENDPOINT`.

---

## 19. Enums

| Enum | Values |
|---|---|
| AccountStatus | `ACTIVE`, `SUSPENDED`, `BANNED` |
| AdminRole | `SUPER_ADMIN`, `SUPPORT_ADMIN`, `FINANCE_ADMIN`, `CONTENT_ADMIN` |
| ApplicationStatus | `DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `VERIFIED`, `REJECTED` |
| ProviderStatus | `ACTIVE`, `SUSPENDED` |
| DocumentType | `COMMERCIAL_REGISTER`, `TAX_CARD`, `NATIONAL_ID`, `SHOP_PHOTO`, `OTHER` |
| FilePurpose | `PROVIDER_DOCUMENT`, `EVIDENCE`, `REQUEST_PHOTO`, `CHAT`, `LISTING_PHOTO` |
| CategoryCode | `SPARE_PARTS`, `BATTERIES`, `TIRES`, `OIL_CHANGE`, `CAR_WASH`, `ACCESSORIES`, `REPAIRS` |
| RequestStatus | `MATCHING`, `OFFERS_AVAILABLE`, `PARTIALLY_MATCHED`, `CLOSED`, `CANCELED`, `EXPIRED` |
| ItemStatus | `MATCHING`, `OFFERS_AVAILABLE`, `ACCEPTED`, `CANCELED`, `EXPIRED` |
| TargetStatus | `INVITED`, `VIEWED`, `OFFERED`, `DECLINED` |
| OfferStatus | `ACTIVE`, `WITHDRAWN`, `EXPIRED`, `CLOSED` |
| OfferLineStatus | `PENDING`, `UNAVAILABLE`, `ACCEPTED`, `DECLINED`, `WITHDRAWN`, `EXPIRED` |
| WarrantyCoverage | `PARTS`, `LABOR`, `PARTS_AND_LABOR` |
| CommissionPayer | `CUSTOMER`, `PROVIDER` |
| FeeMode | `NONE`, `FIXED`, `PERCENT` |
| FeeTiming | `ON_PUBLISH`, `ON_SOLD` |
| OrderStatus | `ACCEPTED`, `IN_PROGRESS`, `PROVIDER_MARKED_COMPLETED`, `COMPLETED`, `INTERRUPTION_REQUESTED`, `INTERRUPTION_STATEMENT_SUBMITTED`, `INTERRUPTED`, `DISPUTED`, `CANCELED` |
| CashStatus | `NOT_DUE`, `DUE`, `REPORTED_COLLECTED`, `CONFIRMED`, `PARTIAL`, `DISPUTED` |
| CancellationKind | `CUSTOMER_CANCELED`, `PROVIDER_CANCELED`, `CUSTOMER_NO_SHOW`, `PROVIDER_NO_SHOW`, `ADMIN_CANCELED` |
| InterruptionOutcome | `ACCEPTED`, `DISPUTED`, `RESOLVED` |
| OrderAction | `START`, `MARK_COMPLETED`, `CONFIRM_COMPLETION`, `COMPLAIN`, `CANCEL`, `RESCHEDULE`, `REPORT_NO_SHOW`, `REQUEST_INTERRUPTION`, `SUBMIT_INTERRUPTION_STATEMENT`, `ACCEPT_INTERRUPTION`, `DISPUTE_INTERRUPTION`, `REPORT_CASH`, `CONFIRM_CASH`, `DISPUTE_CASH`, `PROPOSE_CHARGE`, `RATE` |
| MyRole | `CUSTOMER`, `PROVIDER`, `ADMIN` |
| ChargeStatus | `PENDING`, `APPROVED`, `REJECTED` |
| WarrantyClaimStatus | `OPEN`, `ACCEPTED`, `REJECTED`, `ESCALATED`, `RESOLVED` |
| DisputeType | `SERVICE_QUALITY`, `INTERRUPTION`, `CASH_COLLECTION`, `WARRANTY`, `CANCELLATION`, `LISTING`, `OTHER` |
| DisputeStatus | `OPEN`, `UNDER_REVIEW`, `RESOLVED`, `REJECTED` |
| DisputePartyRole | `CUSTOMER`, `PROVIDER`, `SELLER`, `BUYER` |
| DisputeOutcome | `COMPLETE_ORDER`, `REOPEN_ORDER`, `CANCEL_ORDER`, `SET_FINAL_AMOUNT`, `CASH_CONFIRMED`, `CASH_NOT_COLLECTED`, `WARRANTY_HONORED`, `NO_ACTION` |
| ListingCategory | `SPARE_PARTS`, `BATTERIES`, `TIRES`, `ACCESSORIES`, `TOOLS`, `OTHER` |
| ListingCondition | `NEW`, `LIKE_NEW`, `GOOD`, `FAIR`, `FOR_PARTS` |
| ListingStatus | `DRAFT`, `PENDING_REVIEW`, `ACTIVE`, `SOLD`, `CLOSED`, `REJECTED`, `EXPIRED` |
| ContactMethod | `CHAT`, `PHONE` |
| ConversationKind | `ORDER`, `REQUEST`, `LISTING` |
| LedgerType | `SUBSCRIPTION`, `COMMISSION`, `SELLER_FEE`, `CANCELLATION_FEE`, `ADJUSTMENT` |
| LedgerStatus | `DUE`, `REPORTED_PAID`, `SETTLED`, `WAIVED`, `CANCELED` |
| PaymentMethod | `CASH`, `BANK_TRANSFER`, `WALLET` |
| SubscriptionStatus | `FREE_OR_DISABLED`, `ACTIVE`, `REMINDER_DUE`, `PAYMENT_OVERDUE`, `RESTRICTED`, `EXEMPT` |
| ConfigKey | `MARKETPLACE`, `COMMISSION`, `SUBSCRIPTION`, `SELLER` |
| FraudFlagType | `PROVIDER_CANCELLATIONS`, `CUSTOMER_CANCELLATIONS`, `EXCESSIVE_DISPUTES`, `CASH_CONFLICT`, `DUPLICATE_LISTING`, `MANUAL` |
| FraudFlagStatus | `OPEN`, `DISMISSED`, `ACTIONED` |
| NotificationType | `NEW_MATCHING_REQUEST`, `NEW_OFFER`, `OFFER_ACCEPTED`, `OFFER_DECLINED`, `OFFER_EXPIRED`, `REQUEST_EXPIRED`, `APPOINTMENT_RESCHEDULED`, `SERVICE_STARTED`, `CONFIRMATION_REQUIRED`, `ORDER_COMPLETED`, `ORDER_CANCELED`, `NO_SHOW_REPORTED`, `INTERRUPTION_REQUESTED`, `INTERRUPTION_STATEMENT`, `INTERRUPTION_ACCEPTED`, `CASH_REPORTED`, `CASH_CONFIRMED`, `CASH_DISPUTED`, `CHARGE_PROPOSED`, `CHARGE_DECIDED`, `RATING_RECEIVED`, `DISPUTE_OPENED`, `DISPUTE_UPDATED`, `DISPUTE_RESOLVED`, `WARRANTY_CLAIM`, `WARRANTY_CLAIM_UPDATE`, `SUBSCRIPTION_REMINDER`, `SUBSCRIPTION_OVERDUE`, `SUBSCRIPTION_RESTRICTED`, `SUBSCRIPTION_PAID`, `APPLICATION_UPDATE`, `PROVIDER_STATUS`, `LISTING_PUBLISHED`, `LISTING_REJECTED`, `LISTING_EXPIRED`, `LISTING_SOLD`, `LEDGER_UPDATE`, `NEW_MESSAGE`, `ACCOUNT_STATUS` |
| NotificationRefType | `REQUEST`, `OFFER`, `ORDER`, `DISPUTE`, `WARRANTY_CLAIM`, `LISTING`, `SUBSCRIPTION`, `LEDGER`, `APPLICATION`, `CONVERSATION`, `USER` |

Clients should map unknown values to a safe "unknown" state rather than crash, so new values can be added later.

---

## 20. Error codes

| Code | HTTP | When |
|---|---|---|
| VALIDATION_FAILED | 400 | Invalid field (see `details`) or a business validation message |
| MALFORMED_REQUEST | 400 | Unreadable JSON, wrong type, unknown enum value, missing parameter |
| UNAUTHENTICATED | 401 | No token on a protected endpoint |
| INVALID_CREDENTIALS | 401 | Wrong email/password, or wrong current password |
| TOKEN_EXPIRED | 401 | Token expired — log in again |
| TOKEN_INVALID | 401 | Bad or revoked token (logout-all, password change, suspension, role change) |
| ACCESS_DENIED | 403 | Not allowed for this user/role |
| ACCOUNT_SUSPENDED | 403 | Account suspended or banned |
| NOT_FOUND | 404 | Generic not found |
| METHOD_NOT_ALLOWED | 405 | Wrong HTTP method |
| CONFLICT | 409 | Generic conflict (unique constraint, already reviewed) |
| CONCURRENT_MODIFICATION | 409 | Someone changed the record at the same time — reload and retry |
| PAYLOAD_TOO_LARGE | 413 | File over 10 MB |
| RATE_LIMITED | 429 | Too many login/registration attempts |
| INTERNAL_ERROR | 500 | Unexpected server error |
| EMAIL_ALREADY_USED | 409 | |
| PHONE_ALREADY_USED | 409 | |
| FILE_REQUIRED | 400 | Empty upload |
| FILE_TYPE_NOT_ALLOWED | 400 | Extension/content type not allowed for the purpose |
| FILE_CONTENT_MISMATCH | 400 | File bytes do not match the declared type |
| PROVIDER_NOT_FOUND | 404 | No provider profile / unknown provider |
| PROVIDER_NOT_ACTIVE | 403 | Provider mode not open (not verified, suspended, or staff) |
| PROVIDER_NOT_ELIGIBLE | 422 | Provider cannot receive / serve this request |
| APPLICATION_NOT_EDITABLE | 409 | Application not in DRAFT/REJECTED |
| APPLICATION_INCOMPLETE | 422 | Submit needs ≥ 1 service and ≥ 1 document |
| APPLICATION_INVALID_STATE | 409 | Wrong application/provider state for this action |
| SUBSCRIPTION_RESTRICTED | 403 | Unpaid subscription after grace — new provider activity blocked |
| CATEGORY_NOT_FOUND | 404 | Unknown or inactive category |
| CONFIG_INVALID | 422 | Configuration payload breaks a rule |
| VEHICLE_REQUIRED | 422 | A chosen service needs vehicle details |
| REQUEST_NOT_FOUND | 404 | |
| REQUEST_NOT_EDITABLE | 409 | Request already has offers or is closed |
| REQUEST_DEADLINE_PASSED | 409 | Response deadline passed |
| ITEM_NOT_OPEN | 409 | Service item already accepted/canceled/expired |
| NOT_TARGETED | 403 | Request was not sent to this provider |
| CATEGORY_NOT_OFFERED | 422 | Provider does not offer this service |
| OFFER_ALREADY_EXISTS | 409 | Provider already has an active offer on the request |
| OFFER_NOT_FOUND | 404 | |
| OFFER_LOCKED | 409 | Part of the offer was accepted — cannot change it |
| OFFER_NOT_PENDING | 409 | Line/offer no longer pending |
| OFFER_EXPIRED | 409 | Offer expired |
| INVALID_EXPIRY | 422 | `validHours` outside the allowed range |
| SELF_DEALING_FORBIDDEN | 422 | Customer and provider are the same account |
| OUTSIDE_WORKING_HOURS | 422 | Appointment outside the branch hours |
| SLOT_FULL | 409 | Branch has no capacity left at that time |
| APPOINTMENT_IN_PAST | 422 | Appointment/proposed start is not in the future |
| ORDER_NOT_FOUND | 404 | |
| ORDER_INVALID_STATE | 409 | Action not allowed in the order's current status |
| NO_SHOW_TOO_EARLY | 409 | Grace period after the appointment not over yet |
| CASH_INVALID_STATE | 409 | Cash action not allowed in the current cash status |
| RATING_NOT_ALLOWED | 409 | Order not completed |
| RATING_ALREADY_EXISTS | 409 | |
| WARRANTY_NOT_FOUND | 404 | |
| WARRANTY_NOT_ACTIVE | 409 | Warranty ended |
| CLAIM_INVALID_STATE | 409 | Claim not in a state allowing the action |
| DISPUTE_NOT_FOUND | 404 | |
| DISPUTE_INVALID_STATE | 409 | Dispute already decided |
| DISPUTE_ALREADY_OPEN | 409 | Same dispute already open |
| LISTING_NOT_FOUND | 404 | |
| LISTING_INVALID_STATE | 409 | |
| LISTING_LIMIT_REACHED | 409 | Active-listing quota reached |
| LEDGER_INVALID_STATE | 409 | Entry already reported/settled/waived |
| CONVERSATION_NOT_FOUND | 404 | |

---

## 21. Typical flows

**Customer gets a service**
1. `POST /auth/register` → store `token` → `GET /me`.
2. `GET /catalog/categories`, `GET /catalog/areas`, `GET /vehicles` (preselect first).
3. Optional photos: `POST /files` (`REQUEST_PHOTO`) → keys.
4. `POST /requests` → `RequestDetail`.
5. Poll/refresh `GET /requests/{id}` (or react to `NEW_OFFER` notifications) and compare `items[].offers`.
6. `POST /requests/{id}/items/{itemId}/accept` for each item → `OrderDetail`.
7. Follow the order with `GET /orders/{id}`; show buttons from `actions`.
8. When the provider marks it done: `confirm-completion` (or `complain`), then `cash/confirm`, then `rating`.

**Provider answers a request**
1. `GET /me` → `capabilities.provider.canOperate == true`.
2. `GET /provider/requests?status=INVITED` → `GET /provider/requests/{id}`.
3. `GET /provider/branches` → `POST /provider/requests/{id}/offer`.
4. On `OFFER_ACCEPTED`: `GET /orders/{orderId}` → `start` → (`charges`) → `mark-completed` → `cash/report`.

**Becoming a provider**
`PUT /provider/application` → `POST /files` (`PROVIDER_DOCUMENT`) → `POST /provider/application/documents` →
`POST /provider/application/submit` → wait for `APPLICATION_UPDATE` → `GET /me` → add a branch
(`POST /provider/branches`, `PUT .../hours`) → switch to provider mode.

**Selling a used item**
`POST /files` (`LISTING_PHOTO`) → `POST /listings` → `POST /listings/{id}/submit` → after approval it is public →
`POST /conversations` (`LISTING`) from buyers → `POST /listings/{id}/mark-sold`.
