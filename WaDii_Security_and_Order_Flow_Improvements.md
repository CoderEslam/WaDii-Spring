# WaDii – Security & Order/Response System Improvements

## Purpose

This document defines the backend changes required to make WaDii safer and to implement the intended production business flow for:

- Authentication and authorization
- User/provider/admin permissions
- Ownership checks
- Secure order creation
- Provider targeting
- Service-based order distribution
- Provider responses/offers
- Accepting exactly one provider response
- Declining all other responses without deleting them
- Notifications
- Chat/call access after an accepted response
- Data privacy and API response security
- Concurrency and transactional consistency

The goal is to preserve the current WaDii business idea while making the backend secure, predictable, and production-ready.

---

# 1. Target Business Flow

## 1.1 Customer creates an order

A customer creates an order containing:

- Car type/model
- Car year
- Comment/description
- Requested date
- Location
- Requested services
- Optional spare parts
- Optional target provider

The backend MUST NOT trust the `userId` sent by the client.

The owner must always come from the authenticated JWT.

```text
JWT
 ↓
authenticatedUserId
 ↓
Order.userId = authenticatedUserId
```

Never:

```text
request.userId → Order.userId
```

---

# 2. Order Targeting Rules

An order can be targeted in two ways.

## 2.1 Customer selected a specific provider

If the customer explicitly selects a provider:

```text
Order
 ├── userId = current authenticated user
 ├── providerId = selected provider
 └── services = requested services
```

The backend must validate:

1. The provider exists.
2. The provider account is actually a `PROVIDER`.
3. The provider is active/approved.
4. The provider is allowed to receive new orders.
5. The provider offers the requested service(s), if this is a business requirement.

Then:

```text
Order
 ↓
Selected Provider
 ↓
ORDER_RECEIVED notification
```

The order must NOT be broadcast to unrelated providers.

---

## 2.2 Customer does NOT select a provider

If no provider is selected, the backend finds eligible providers based on the requested services.

Example:

```text
Requested services:
- Service ID 1
- Service ID 2
```

The system should target providers who offer the requested service set.

### Recommended rule

A provider must offer **all requested services**.

Example:

```text
Provider A → Service 1 + Service 2     ✅
Provider B → Service 1                 ❌
Provider C → Service 2                 ❌
Provider D → Service 1 + Service 2     ✅
```

So the order is sent to:

```text
Provider A
Provider D
```

This avoids sending an order to a provider who cannot perform the complete requested job.

### Important

The backend must perform this filtering.

The mobile/web application must NOT decide which providers are eligible.

---

# 3. Provider Notification

When an order is created:

## Direct provider order

```text
Customer
   ↓
Create Order
   ↓
Selected Provider
   ↓
Push Notification
   ↓
Provider sees Order
```

## Service-based order

```text
Customer
   ↓
Create Order
   ↓
Find eligible providers
   ↓
Provider 1 ─┐
Provider 2 ─┤
Provider 3 ─┤
             └── Push notifications
```

Notifications should contain enough information to identify the order but should not expose sensitive user information.

Recommended notification type:

```text
NEW_ORDER
```

---

# 4. Order Visibility

Providers must only see orders that they are eligible to respond to.

A provider must NOT be able to access arbitrary orders by changing an ID.

For example:

```http
GET /orders/{orderId}
```

must verify:

```text
currentUser is:
    order owner
OR
    targeted provider
OR
    authorized admin
```

A provider who was not targeted by an order must receive:

```http
403 Forbidden
```

or, if hiding resource existence is preferred:

```http
404 Not Found
```

---

# 5. Provider Response / Offer

Each provider can submit one response for an eligible order.

Example:

```text
Order #100
Requested:
- Service 1
- Service 2
```

Responses:

```text
Provider A → 4,000 EGP
Provider B → 4,100 EGP
Provider C → 4,500 EGP
```

Each response contains:

- Response ID
- Order ID
- Provider ID
- Comment
- Location
- Spare-part prices
- Total price
- Status
- Created date
- Updated date

---

# 6. Response Status

Responses should NOT be deleted when another response is accepted.

Recommended lifecycle:

```text
PENDING
   │
   ├── Customer accepts
   │       ↓
   │    ACCEPTED
   │
   └── Customer chooses another response
           ↓
        DECLINED
```

Possible statuses:

```text
PENDING
ACCEPTED
DECLINED
CANCELED
```

The exact final list can be simplified if `CANCELED` is not needed.

---

# 7. Accepting a Response

This is one of the most important operations in the system.

When the customer accepts response `R1`:

```text
Order #100

Response A → ACCEPTED
Response B → DECLINED
Response C → DECLINED
Response D → DECLINED
```

Only ONE response can ever be `ACCEPTED`.

The backend must verify:

1. The current user owns the order.
2. The response belongs to the order.
3. The response is currently `PENDING`.
4. The order is still open.
5. No other response has already been accepted.

---

# 8. Atomic Acceptance

Accepting a response must be handled as a database transaction.

Conceptually:

```text
BEGIN TRANSACTION

1. Lock the order
2. Verify order owner
3. Verify order is still open
4. Verify selected response belongs to order
5. Verify selected response is PENDING
6. Set selected response = ACCEPTED
7. Set all other PENDING responses = DECLINED
8. Set order = ACCEPTED
9. Store selected provider on the order
10. Create notification for provider

COMMIT
```

If anything fails:

```text
ROLLBACK
```

This prevents two requests from accepting two different responses at the same time.

---

# 9. Order Status

The current order status is too limited for the complete business flow.

Recommended lifecycle:

```text
PENDING
   ↓
RESPONSES_AVAILABLE
   ↓
ACCEPTED
   ↓
IN_PROGRESS
   ↓
COMPLETED
```

Alternative cancellation path:

```text
PENDING ─────→ CANCELED
RESPONSES_AVAILABLE ─→ CANCELED
ACCEPTED ────────────→ CANCELED
```

If the backend wants to keep the existing simple statuses, at minimum add:

```text
PENDING
ACCEPTED
CANCELED
COMPLETED
```

The important point is that the order must clearly know whether it already has a winning provider.

---

# 10. Store the Winning Provider on the Order

After acceptance, the order should store:

```text
acceptedProviderId
```

or an equivalent relation.

Example:

```text
Order
 ├── userId
 ├── acceptedProviderId
 ├── status = ACCEPTED
 └── acceptedResponseId
```

This makes later authorization much easier.

After acceptance:

```text
Customer ↔ Accepted Provider
```

becomes the official service relationship for that order.

---

# 11. Notification After Acceptance

When the customer accepts Provider A:

```text
Provider A
    ↓
RESPONSE_ACCEPTED
```

Notification example:

```text
"Your offer for order #100 was accepted."
```

Providers B/C/D receive:

```text
RESPONSE_DECLINED
```

or a more generic:

```text
ORDER_RESPONSE_NOT_SELECTED
```

The notification should not expose unnecessary customer information.

---

# 12. Chat Access After Acceptance

Before acceptance, the customer and provider may have limited communication according to the business rules.

Recommended secure rule:

```text
Customer
   ↕
Accepted Provider
```

After acceptance, allow:

- Chat
- Call
- Presence
- Shared order context

The backend should verify that the two users are actually connected through an accepted order.

Do NOT rely only on:

```text
userId + userId
```

sent by the client.

The server should verify the relationship.

---

# 13. Call Authorization

For Agora/LiveKit token generation:

The backend must verify that the caller is authorized to communicate with the target user.

Recommended:

```text
currentUser
    ↓
Is there an accepted order connecting both users?
    ↓
YES → issue call token
NO  → reject
```

Do not allow any authenticated user to generate a call token for arbitrary users unless open calling is explicitly part of the product requirements.

---

# 14. Order Cancellation

Only the order owner can cancel an order.

```text
JWT user
   ↓
order.userId == JWT user ID?
   ↓
YES → cancel
NO  → 403
```

Recommended rule:

An order cannot be canceled after completion.

For an accepted order, define whether cancellation is allowed and what happens to the accepted response.

---

# 15. Provider Response Authorization

Before creating a response:

```text
currentUser.role == PROVIDER
```

AND:

```text
currentProvider.id == response.providerId
```

The client must NOT be allowed to submit:

```json
{
  "providerId": 999
}
```

and act as another provider.

The server should derive the provider from the authenticated account.

Recommended:

```text
JWT
 ↓
User
 ↓
Provider profile
 ↓
response.providerId
```

not:

```text
request.providerId
```

---

# 16. Provider Eligibility

Before a provider responds to an order, verify:

1. Provider exists.
2. Provider is active.
3. Provider is approved.
4. Provider owns/offers the required services.
5. Provider was targeted by the order.
6. Order is still open.
7. Provider has not already submitted a response.
8. Provider is not blocked/suspended.

---

# 17. Prevent Duplicate Responses

A provider should normally have only one active response per order.

Recommended database constraint:

```text
UNIQUE(order_id, provider_id)
```

This is better than relying only on frontend checks.

---

# 18. Ownership Security

All resources must verify ownership on the server.

## User resources

A normal user can modify:

```text
own profile
own images
own orders
own responses/actions where applicable
own ratings
own saved offers
own notifications
```

They cannot modify another user's resources by changing an ID.

---

# 19. Provider Ownership

A provider can modify:

```text
own provider profile
own services
own branches
own work times
own links
own offers
own responses
```

A provider must not be able to update another provider by changing:

```text
providerId
```

in the request.

The backend must compare the resource owner with the authenticated provider.

---

# 20. Rating Security

When creating a rating:

Do NOT trust:

```json
{
  "userId": 123
}
```

Instead:

```text
JWT → currentUserId
```

The server should also verify that the user is allowed to rate the provider.

Recommended rule:

```text
User
 ↓
has completed/accepted service with provider?
 ↓
YES → can rate
NO  → reject
```

Also consider:

```text
UNIQUE(user_id, provider_id, order_id)
```

to prevent duplicate ratings.

---

# 21. Admin Registration Must Be Disabled

Current behavior allows:

```text
userType = 2
```

during public registration.

This must be removed.

Public registration should allow only:

```text
USER
PROVIDER
```

If direct provider registration is still desired, it can remain:

```text
userType = PROVIDER
```

but admin registration must be controlled.

Admins should be created through:

- Secure database migration
- Existing trusted admin
- Internal admin creation endpoint
- Deployment/bootstrap process

Never through public registration.

---

# 22. Admin Endpoint Authorization

Every admin-only endpoint must explicitly require:

```text
ROLE_ADMIN
```

Examples:

```text
/users/insert
/users/show-all
/users/update
/users/delete

/provider-requests/show-all
/provider-requests/accept
/provider-requests/reject

/services/insert
/services/update
/services/delete

/car-types/*
/common-spare-parts/*
/advertisements/*
```

Do not rely on frontend hiding buttons.

The backend must enforce it.

---

# 23. Provider Request Security

Provider requests must be protected.

A normal user can create:

```text
POST /provider-requests/request
```

but cannot:

```text
GET /provider-requests/show-all
POST /provider-requests/accept/*
POST /provider-requests/reject/*
```

unless they are an admin.

Also:

```text
One pending request per user
```

must be enforced by the database.

---

# 24. Role Change Security

Endpoints such as:

```text
put-it-user
put-it-provider
```

must be admin-only.

The affected user ID may come from the request, but the authorization must come from:

```text
JWT → ADMIN
```

A provider or normal user must never be able to change their own role through a public endpoint.

---

# 25. Sensitive User Data

Never return passwords from normal API responses.

Even hashed passwords should not be exposed.

User DTOs should contain only safe fields.

For example:

```text
UserResponse
 ├── id
 ├── name
 ├── email
 ├── phone
 ├── role
 ├── profileImage
 ├── backgroundImage
 └── ...
```

Do NOT return:

```text
password
```

Tokens should also only be returned when necessary, normally during authentication/refresh flows.

---

# 26. Password Handling

Passwords must always be:

```text
hashed
```

using a modern password hashing algorithm such as:

```text
BCrypt
```

or:

```text
Argon2
```

Never store plaintext passwords.

Admin-created users must also receive a valid hashed password.

The current behavior where `/users/insert` may store a raw password must be fixed.

---

# 27. JWT Security

JWT must be validated on every protected request.

Validate:

- Signature
- Expiration
- User existence
- Account status
- Role where required

Do not trust role values sent by the client.

Never accept:

```json
{
  "role": "ADMIN"
}
```

as authorization input.

Authorization must come from the authenticated server-side user.

---

# 28. HTTP Status Codes

Do not return HTTP 200 for authorization failures.

Recommended:

```text
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
422 Unprocessable Entity
500 Internal Server Error
```

Example:

```text
Provider tries to accept another user's response
→ 403 Forbidden
```

Example:

```text
Provider submits duplicate response
→ 409 Conflict
```

---

# 29. Validation

Every request must be validated on the backend.

Examples:

```text
serviceIds must not be empty
price >= 0
date must be valid
phone must be valid
email must be valid
required IDs must exist
```

Never trust frontend validation.

---

# 30. Price Security

Provider prices must be validated.

Recommended:

```text
price >= 0
```

Use decimal-safe database types for money.

Prefer:

```text
DECIMAL
```

instead of floating point.

Never use floating-point arithmetic for financial calculations.

---

# 31. Total Response Price

The backend should calculate the total response price where possible.

Do not blindly trust:

```text
clientTotalPrice
```

Instead:

```text
spare part prices
+
service price
+
other defined costs
=
server calculated total
```

If the business model does not have service-level pricing, clearly define how the total is calculated.

---

# 32. Notification Reliability

Notifications should be stored in the database before/around push delivery.

Recommended:

```text
Business event
   ↓
Create notification record
   ↓
Send FCM push
```

If FCM fails:

```text
Notification remains in inbox
```

so the user does not lose the event.

---

# 33. FCM Token Security

The FCM token belongs to the authenticated user.

The backend should update:

```text
currentUser.fcmToken
```

and never allow a user to modify another user's token.

If multiple devices are supported, consider:

```text
User
 └── DeviceTokens
      ├── device A
      ├── device B
      └── device C
```

instead of storing only one token.

---

# 34. Advertisement Security

Advertisement management must be admin-only.

Public users can:

```text
GET /advertisements/show-all
POST /track-impression
POST /track-click
```

but cannot create/update/delete advertisements.

Validate:

```text
startDate <= endDate
priority >= 0
status ∈ ACTIVE, INACTIVE, EXPIRED
```

---

# 35. File Upload Security

Profile images, provider documents, and other uploads must be protected.

Validate:

- File size
- MIME type
- File extension
- Filename
- Content type

Do not trust the filename supplied by the client.

Generate server-side filenames.

Example:

```text
UUID + safe extension
```

Provider ID/tax documents should NOT be publicly accessible.

---

# 36. Provider Application Documents

ID/tax-card images are sensitive.

They should be accessible only to:

```text
ADMIN
```

and the owning user where required.

Do NOT expose them through a public:

```text
GET /users/{filename}
```

style endpoint.

Public profile images can be public if required, but verification documents must be private.

---

# 37. Search Security

Search endpoints can remain public/authenticated according to product requirements.

However:

- Sanitize input
- Use parameterized queries
- Limit result size
- Add pagination
- Avoid exposing internal database fields

Never concatenate raw search input into SQL.

---

# 38. Pagination

Large endpoints should use pagination.

Especially:

```text
/providers/show-all
/orders/show-all
/responses/show-all
/messages/chat-list
/notifications/my
/advertisements/*
/users/show-all
```

Example:

```text
page=0
size=20
```

Also enforce a maximum:

```text
size <= 100
```

---

# 39. Database Constraints

Important business rules should be protected by database constraints, not only Java/Kotlin code.

Recommended constraints include:

```text
unique user.email
unique user.phone where appropriate
unique(order_id, provider_id)
one pending provider request per user
valid foreign keys
non-null required fields
```

---

# 40. Transactional Business Operations

Use database transactions for operations involving multiple records.

Especially:

### Accept response

```text
Order
+
Selected Response
+
Other Responses
+
Notification
```

### Provider approval

```text
Provider Request
+
User Role
+
Provider Profile
+
Notification
```

### Order cancellation

```text
Order
+
Related responses
+
Notifications
```

This prevents partially completed operations.

---

# 41. Concurrency Protection

The accept-response endpoint is especially sensitive.

Scenario:

```text
Customer taps Provider A
Customer taps Provider B
```

almost simultaneously.

The server must guarantee:

```text
Only A OR B
```

can become accepted.

Never allow:

```text
A = ACCEPTED
B = ACCEPTED
```

Use one or more of:

- Database row locking
- Optimistic locking
- Transaction
- Unique constraint
- Conditional update

Recommended implementation:

```text
@Transactional
+
lock order
+
verify status
+
accept one response
+
decline remaining responses
```

---

# 42. Response History Must Be Preserved

Do not delete responses after another response wins.

Example:

```text
Provider A → ACCEPTED
Provider B → DECLINED
Provider C → DECLINED
```

This gives the platform historical information.

It also helps with:

- Analytics
- Customer support
- Dispute handling
- Provider statistics
- Admin auditing

---

# 43. Audit Log

For important actions, introduce an audit log.

Examples:

```text
ORDER_CREATED
ORDER_CANCELED
RESPONSE_CREATED
RESPONSE_UPDATED
RESPONSE_ACCEPTED
RESPONSE_DECLINED
PROVIDER_APPROVED
PROVIDER_REJECTED
USER_ROLE_CHANGED
ADVERTISEMENT_CREATED
ADVERTISEMENT_STATUS_CHANGED
```

Audit record:

```text
actorUserId
action
entityType
entityId
timestamp
metadata
```

Do not store sensitive data unnecessarily.

---

# 44. Provider Order Matching

Recommended matching service:

```text
OrderMatchingService
```

Responsibilities:

1. Receive created order.
2. Determine whether provider was explicitly selected.
3. If yes → target selected provider.
4. If no → find providers offering all requested services.
5. Exclude inactive/suspended providers.
6. Create order-target records if needed.
7. Send notifications.

Conceptually:

```text
createOrder()
      ↓
OrderMatchingService
      ↓
 ┌───────────────┐
 │ providerId?   │
 └───────┬───────┘
         │
    ┌────┴────┐
   YES       NO
    │         │
    ▼         ▼
Target     Match services
one        against providers
provider       │
               ▼
         eligible providers
               │
               ▼
          notifications
```

---

# 45. Recommended Order Target Table

For better scalability, consider introducing:

```text
order_provider_targets
```

Example:

```text
id
order_id
provider_id
status
notified_at
viewed_at
responded_at
```

Status could be:

```text
TARGETED
VIEWED
RESPONDED
DECLINED
EXPIRED
```

This gives the system a clear relationship between an order and the providers who were invited.

It also makes it easier to answer:

- Which providers received the order?
- Which providers viewed it?
- Which providers responded?
- Which providers ignored it?

---

# 46. Response Expiration

Consider adding a response expiration time.

Example:

```text
Response created
     ↓
Valid for 24 hours
     ↓
Automatically EXPIRED
```

This prevents old offers from remaining active forever.

This should be decided as a business rule before implementation.

---

# 47. Order Expiration

Orders may also need an expiration rule.

For example:

```text
Requested service date passed
        ↓
Order EXPIRED
```

This prevents providers from responding to outdated orders.

Recommended future status:

```text
EXPIRED
```

---

# 48. Provider Availability

Consider adding:

```text
acceptingOrders
```

or:

```text
isAvailable
```

to the provider.

Then matching becomes:

```text
offers required services
AND
approved
AND
active
AND
accepting orders
```

This prevents notifications being sent to providers who are temporarily unavailable.

---

# 49. Notification Fan-out Protection

If the platform grows, sending thousands of FCM notifications inside the HTTP request can become slow.

Recommended future architecture:

```text
Create Order
   ↓
Save transaction
   ↓
Publish OrderCreated event
   ↓
Background worker
   ↓
Find eligible providers
   ↓
Send notifications
```

For the current scale, synchronous processing may be acceptable, but the design should allow asynchronous processing later.

---

# 50. API Authorization Summary

## USER

Allowed:

```text
Own profile
Own orders
Browse marketplace
Create order
Accept own order response
Cancel own order
Chat/call with authorized users
Rate eligible providers
Save offers
Follow providers
```

Not allowed:

```text
Admin operations
Other users' resources
Other providers' business data modifications
Other users' provider requests
```

---

# 51. PROVIDER

Everything USER can do, plus:

```text
Own provider profile
Own branches
Own work times
Own links
Own offers
View eligible orders
Create own responses
Update own responses
Cancel own responses
```

Not allowed:

```text
Other providers' resources
Admin operations
Arbitrary orders
Arbitrary response ownership
```

---

# 52. ADMIN

Allowed:

```text
User management
Provider management
Provider approval
Reference data
Advertisements
Platform monitoring
Notifications/testing
Audit information
```

Admin actions must still be explicitly authorized by backend security rules.

---

# 53. Recommended API Changes

## Create order

Current:

```http
POST /orders/insert
```

Keep the endpoint if desired, but change the backend behavior.

Do not trust:

```text
userId
```

from request body.

Optional:

```text
providerId
```

means direct provider targeting.

If `providerId == null`:

```text
match providers by requested services
```

---

## Create response

```http
POST /responses/insert
```

The server derives:

```text
providerId
```

from JWT.

The client should primarily send:

```text
orderId
comment
location
sparePartsPrice
```

The server determines the provider.

---

## Accept response

```http
POST /responses/accept-response
```

Request:

```json
{
  "responseId": 123
}
```

Server:

```text
authenticate user
↓
verify order ownership
↓
lock order
↓
verify response belongs to order
↓
verify order still open
↓
accept selected response
↓
decline all other responses
↓
set acceptedProviderId
↓
set order status
↓
create notifications
↓
commit
```

---

# 54. Recommended Response State Example

Before selection:

```text
Order #100

Provider A → PENDING → 4000
Provider B → PENDING → 4100
Provider C → PENDING → 4500
```

Customer chooses A:

```text
Order #100 → ACCEPTED

Provider A → ACCEPTED → 4000
Provider B → DECLINED → 4100
Provider C → DECLINED → 4500
```

Notifications:

```text
Provider A:
"Your offer was accepted."

Provider B:
"Your offer was not selected."

Provider C:
"Your offer was not selected."
```

---

# 55. Post-Acceptance Flow

After acceptance:

```text
Customer
    │
    │ accepted response
    ▼
Provider
    │
    ├── Chat
    ├── Call
    ├── Order details
    └── Service execution
```

After the service:

```text
Order
 ↓
COMPLETED
 ↓
Customer can rate provider
```

---

# 56. Recommended Final Business Lifecycle

```text
                    ┌────────────────────┐
                    │ Customer creates   │
                    │      ORDER         │
                    └─────────┬──────────┘
                              │
                     provider selected?
                         /          \
                       YES           NO
                       /              \
                      ▼                ▼
             Target provider     Match providers
                                 by ALL services
                      \              /
                       \            /
                        ▼          ▼
                    Notify eligible
                     providers
                          │
                          ▼
                    PROVIDER RESPONSE
                          │
                    ┌─────┴─────┐
                    │           │
                 PENDING      PENDING
                    │           │
                    └─────┬─────┘
                          │
                    Customer selects
                          │
                          ▼
                 ┌──────────────────┐
                 │ Accepted Response│
                 └────────┬─────────┘
                          │
             ┌────────────┴────────────┐
             ▼                         ▼
      Selected Provider         Other Responses
          ACCEPTED                  DECLINED
             │
             ▼
        Notification
             │
             ▼
        Chat / Call
             │
             ▼
        Service Started
             │
             ▼
        COMPLETED
             │
             ▼
           Rating
```

---

# 57. Priority Implementation Plan

## P0 – Critical Security

Implement first:

- Disable public admin registration.
- JWT-based identity everywhere.
- Remove trust in `userId` from request bodies.
- Remove trust in `providerId` for provider-owned operations.
- Add ownership checks.
- Add admin role checks.
- Protect provider requests.
- Protect role-change endpoints.
- Remove passwords from API responses.
- Fix plaintext password creation.
- Protect private provider documents.
- Return correct HTTP authorization errors.

---

## P1 – Core Order Business Logic

Implement next:

- Optional `providerId` on order.
- Direct provider targeting.
- Service-based provider matching.
- Require providers to offer all requested services.
- Provider eligibility validation.
- Provider response ownership.
- One response per provider per order.
- Response statuses.
- Order accepted state.
- `acceptedProviderId`.
- Atomic accept-response transaction.
- Decline other responses instead of deleting them.
- Notifications for accepted/declined responses.

---

## P2 – Communication

Then:

- Secure chat relationship.
- Secure call authorization.
- Accepted-order communication.
- Reliable notification storage.
- FCM token/device management.

---

## P3 – Production Hardening

Then:

- Pagination.
- Database constraints.
- Audit logs.
- Rate limiting.
- File upload hardening.
- Response/order expiration.
- Provider availability.
- Background notification processing.
- Monitoring and structured logs.

---

# 58. Non-Negotiable Security Rules

The following rules must always be enforced by the backend:

```text
1. Never trust userId from the client.
2. Never trust providerId from the client for ownership.
3. Never trust role from the client.
4. Never allow public admin registration.
5. Never expose passwords.
6. Never allow users to modify another user's resources.
7. Never allow providers to modify another provider's resources.
8. Never allow a provider to respond to an unauthorized order.
9. Never allow more than one accepted response per order.
10. Accepting a response must be transactional.
11. Do not delete declined responses.
12. Preserve response history.
13. Admin endpoints must be admin-only.
14. Provider documents must be private.
15. Call tokens must be authorized.
16. Important business rules must also be protected by database constraints.
17. The frontend is never the security boundary.
18. Every authorization decision must be made by the backend.
```

---

# 59. Final Target Architecture

The target system should behave like this:

```text
                    JWT Authentication
                           │
                           ▼
                  Current Authenticated User
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
             USER       PROVIDER       ADMIN
              │            │            │
              │            │            └── Platform Management
              │            │
              │            └── Provider Business
              │
              └── Customer Marketplace
                           │
                           ▼
                         ORDER
                           │
                  ┌────────┴────────┐
                  │                 │
          Direct Provider      Service Matching
                  │                 │
                  └────────┬────────┘
                           ▼
                   Eligible Providers
                           │
                           ▼
                      RESPONSES
                           │
                           ▼
                    Customer Selects
                           │
                           ▼
                 One ACCEPTED Response
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
          Others DECLINED          Winning Provider
                                        │
                              ┌─────────┴─────────┐
                              ▼                   ▼
                            CHAT                CALL
                              │                   │
                              └─────────┬─────────┘
                                        ▼
                                   SERVICE
                                        │
                                        ▼
                                    COMPLETED
                                        │
                                        ▼
                                      RATE
```

This should be treated as the target production behavior for the WaDii backend.
