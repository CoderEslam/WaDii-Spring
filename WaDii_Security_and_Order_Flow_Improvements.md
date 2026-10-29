# WaDii Backend — Full Re-Architecture, Business Rules, Database & API Implementation

You are working on the backend of **WaDii**, a car-services marketplace.

The application is **NOT in production yet**.

This is extremely important:

> You are explicitly allowed to redesign the database and backend architecture from scratch where necessary.
>
> Do NOT preserve legacy database structures, entities, endpoints, or business logic merely for backward compatibility if they conflict with the new requirements.
>
> Preserve the business requirements, not the old implementation mistakes.

Your job is to fully review the existing backend and then implement the final business architecture described below.

---

# 1. FIRST: REVIEW THE EXISTING PROJECT

Before changing code, inspect the entire backend project.

You must understand:

- existing entities
- repositories
- services
- controllers
- DTOs
- security configuration
- JWT implementation
- database configuration
- relationships
- WebSocket implementation
- notifications
- file upload system
- provider system
- order system
- response/offer system
- advertisements
- authentication
- authorization
- scheduled jobs
- existing tests
- existing API behavior

Also inspect:

```text
API_REFERENCE.md
APP_GUIDE_BY_ROLE.md
```

These documents describe the current application and must be treated as important input for understanding the existing system.

However:

> Existing documentation is NOT necessarily correct.

The final implemented backend and the new business rules in this prompt are the source of truth.

---

# 2. DO NOT PATCH THE OLD ARCHITECTURE BLINDLY

Do not simply add random fields to existing entities.

First determine whether the existing model is still appropriate.

If an entity is badly designed, replace it.

If a relationship is incorrect, redesign it.

If an endpoint violates security rules, redesign its implementation.

If the current database schema is no longer appropriate, recreate it.

---

# 3. DATABASE CAN BE RECREATED FROM SCRATCH

Because WaDii has not reached production:

You are allowed to:

- drop existing development tables
- recreate the database schema
- remove obsolete tables
- rename entities/tables
- redesign relationships
- add proper foreign keys
- add unique constraints
- add indexes
- add audit tables
- add new business entities

Do NOT waste time maintaining backward compatibility with an obsolete schema.

The new database must represent the final business model cleanly.

---

# 4. DATABASE PRINCIPLES

Use proper relational design.

Important principles:

- Foreign keys where appropriate
- Unique constraints
- Indexes for frequently queried fields
- Proper nullable/non-nullable definitions
- Transaction-safe operations
- Optimistic/pessimistic locking where necessary
- Decimal types for money
- UTC timestamps internally
- No floating-point money
- No duplicated ownership information unless justified
- Soft deletion only where business history requires it

---

# 5. ROLES

Current roles:

```text
USER = 0
PROVIDER = 1
ADMIN = 2
```

Preserve compatibility with the existing role system if necessary, but internally design authorization cleanly.

Future administrative permissions should support:

```text
SUPER_ADMIN
SUPPORT_ADMIN
CONTENT_ADMIN
FINANCE_ADMIN
```

Do not rely only on:

```text
role == ADMIN
```

for sensitive operations.

---

# 6. AUTHENTICATION SECURITY

JWT is the source of authenticated identity.

Never trust these values from the client for authorization:

```text
userId
providerId
role
ownerId
```

The backend must derive identity from JWT.

Example:

```text
JWT
 ↓
Authenticated User
 ↓
Provider Profile
 ↓
Authorized Resource
```

A request containing another user's ID must never allow access to that user's resources.

---

# 7. ADMIN REGISTRATION

Public users must NOT be able to create ADMIN accounts.

Remove/disable any public flow equivalent to:

```text
register(userType = ADMIN)
```

Administrative accounts must be created through a controlled administrative process.

---

# 8. PASSWORD SECURITY

Passwords must:

- be hashed using BCrypt or Argon2
- never be stored as plaintext
- never be returned in API responses
- never be included in DTOs

Audit existing endpoints that create users.

If any endpoint currently stores a raw password, fix it.

---

# 9. HTTP STATUS CODES

Use correct HTTP semantics.

```text
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
422 Unprocessable Entity
500 Internal Server Error
```

Do not return HTTP 200 for authorization failures.

---

# 10. CORE DOMAIN MODEL

The target architecture should be conceptually close to:

```text
User

Provider
 ├── ProviderBranch
 ├── ProviderService
 ├── ProviderPolicy
 ├── ProviderPolicyVersion
 ├── ProviderPaymentInformation
 └── ProviderSubscription

Order
 ├── OrderProviderTarget
 ├── OrderAttachment
 ├── OrderTimeline
 ├── Appointment
 ├── Response / Offer
 ├── Payment
 │    ├── PaymentAttempt
 │    ├── PaymentProof
 │    └── Refund
 ├── AdditionalCharge
 ├── Dispute
 │    └── DisputeEvidence
 └── Invoice

Conversation
 └── Message

Notification

Rating
SavedOffer
Follow

Advertisement
AdvertisementAnalytics

AuditLog
```

You may improve this model if your analysis identifies a better relational design.

---

# 11. PROVIDER

A Provider is a user account plus a business profile.

Provider should support:

- profile
- branches
- services
- work times
- links
- offers
- ratings
- followers
- verification
- reliability
- payment receiving information
- policies
- analytics

Provider verification states:

```text
PENDING
VERIFIED
REJECTED
SUSPENDED
```

Only eligible providers should receive/respond to new orders.

---

# 12. PROVIDER BRANCHES

A provider may have multiple branches.

Each branch may contain:

```text
name
address
latitude
longitude
phone
working hours
active status
```

The system should be able to determine the nearest eligible branch.

---

# 13. PROVIDER SERVICES

Providers define which services they offer.

An order can request multiple services.

IMPORTANT:

If an order requests:

```text
Service 1
Service 2
```

the automatic matching rule is:

```text
Provider must offer Service 1 AND Service 2
```

Not:

```text
Service 1 OR Service 2
```

Example:

```text
Provider A -> 1 + 2 -> eligible
Provider B -> 1     -> not eligible
Provider C -> 2     -> not eligible
```

---

# 14. ORDER CREATION

Customer creates an order.

The backend must derive:

```text
customerId
```

from JWT.

Never trust:

```json
{
  "userId": "..."
}
```

for ownership.

Order should support:

- car information
- requested services
- requested date
- location
- latitude
- longitude
- customer comment
- attachments
- optional selected provider

---

# 15. ORDER STATUS

Use a formal state machine.

Recommended:

```text
CREATED
MATCHING
QUOTING
RESPONSES_AVAILABLE
ACCEPTED
IN_PROGRESS
COMPLETED
CANCELED
EXPIRED
```

Do NOT use:

```text
PAID
```

as an Order status.

Payment is a separate domain.

---

# 16. PROVIDER SELECTION

There are two modes.

## Explicit Provider

If customer explicitly selects a provider:

```text
Order -> selected provider only
```

Do not notify unrelated providers.

The backend must validate that provider is:

- active
- verified
- eligible
- capable of requested services

## Automatic Matching

If customer does not select a provider:

Find eligible providers according to:

- all requested services
- active status
- verification
- branch/service area
- distance
- availability

---

# 17. ORDER PROVIDER TARGET

Create a dedicated concept such as:

```text
OrderProviderTarget
```

Suggested fields:

```text
id
orderId
providerId
status
notifiedAt
viewedAt
respondedAt
createdAt
updatedAt
```

Statuses:

```text
TARGETED
VIEWED
RESPONDED
DECLINED
EXPIRED
```

This is important for analytics and provider matching.

---

# 18. PROVIDER RANKING

Support ranking providers using:

- service compatibility
- distance
- rating
- number of ratings
- completed orders
- response time
- cancellation rate
- verified status
- reliability score
- nearest branch
- availability

Customer sorting:

```text
RECOMMENDED
LOWEST_PRICE
HIGHEST_RATED
NEAREST
FASTEST_RESPONSE
```

Do not expose sensitive internal ranking calculations unnecessarily.

---

# 19. ORDER RESPONSE WINDOW

An Order should have an overall response deadline.

Recommended initial configuration:

```text
48 hours
```

After the order response deadline:

- no new offers
- pending offers expire
- order may become EXPIRED if no offer was accepted

This is separate from individual offer expiration.

---

# 20. RESPONSE / OFFER

A provider response represents an offer.

Recommended fields:

```text
id
orderId
providerId
price
currency
description
estimatedDuration
expiresAt
status
createdAt
updatedAt
```

Potential future fields:

```text
discount
partsCost
laborCost
tax
notes
```

---

# 21. ONE RESPONSE PER PROVIDER PER ORDER

Enforce:

```text
UNIQUE(order_id, provider_id)
```

A provider cannot submit multiple responses for the same order unless a future negotiation/versioning system is explicitly introduced.

---

# 22. OFFER EXPIRATION

Every offer MUST have:

```text
expiresAt
```

Recommended defaults:

```text
Default: 48 hours
Minimum: 1 hour
Maximum: 7 days
```

These should be configurable.

Creation validation:

```text
expiresAt > now
```

Acceptance validation:

```text
now < expiresAt
```

---

# 23. RESPONSE STATUSES

Use:

```text
PENDING
ACCEPTED
DECLINED
EXPIRED
CANCELED
```

Never delete expired/declined responses.

They are part of business history.

---

# 24. OFFER EXPIRATION JOB

Implement scheduled expiration.

Conceptually:

```sql
UPDATE responses
SET status = 'EXPIRED'
WHERE status = 'PENDING'
AND expires_at <= NOW();
```

Notify customers when offers expire when appropriate.

---

# 25. ACCEPT OFFER

Acceptance must be atomic.

Flow:

```text
Customer JWT
 ↓
Verify order ownership
 ↓
Lock order
 ↓
Verify response belongs to order
 ↓
Verify response is PENDING
 ↓
Verify response has not expired
 ↓
Accept selected response
 ↓
Decline all other pending responses
 ↓
Set acceptedProviderId
 ↓
Set acceptedResponseId
 ↓
Order = ACCEPTED
 ↓
Create notifications
 ↓
Commit transaction
```

---

# 26. CONCURRENCY

Prevent two responses from being accepted simultaneously.

Use appropriate:

- database locking
- optimistic locking/versioning
- transaction boundaries
- unique constraints

Invariant:

```text
ONE ORDER = MAXIMUM ONE ACCEPTED RESPONSE
```

---

# 27. DECLINED RESPONSES

When customer accepts Provider A:

```text
Provider A -> ACCEPTED

Provider B -> DECLINED
Provider C -> DECLINED
```

Do NOT delete Provider B/C responses.

---

# 28. ACCEPTED PROVIDER

Order should store:

```text
acceptedProviderId
acceptedResponseId
```

These must be server-controlled.

---

# 29. PROVIDER POLICIES

Every provider should be able to define business policies.

At minimum:

```text
Return Policy
Refund Policy
Cancellation Policy
Warranty Policy
Payment Policy
Service Policy
No-Show Policy
```

Provider policies should be editable by the provider.

However, they must never override platform/legal restrictions.

---

# 30. POLICY VERSIONING

Do NOT simply overwrite provider policies.

Create versioned policies.

Conceptually:

```text
ProviderPolicy
ProviderPolicyVersion
```

Example:

```text
Provider:
Return Policy Version 1
    -> 7 days

Provider:
Return Policy Version 2
    -> 3 days
```

An old Order must continue using the policy applicable when the transaction occurred.

---

# 31. ORDER POLICY SNAPSHOT

When a provider is selected/offer accepted, create an immutable snapshot of relevant policies.

Conceptually:

```text
OrderPolicySnapshot
```

Include:

```text
returnPolicy
refundPolicy
cancellationPolicy
warrantyPolicy
paymentPolicy
servicePolicy
noShowPolicy
providerPolicyVersion
createdAt
```

This is critical for disputes.

---

# 32. PLATFORM POLICY

Platform-level rules override provider-specific rules where required.

Conceptually:

```text
Legal Requirements
       ↓
Platform Policy
       ↓
Provider Policy
       ↓
Product/Service Policy
       ↓
Order Snapshot
```

Provider policies cannot weaken mandatory platform/legal rules.

---

# 33. PRODUCT-SPECIFIC POLICIES

If/when WaDii supports spare parts/products, allow product-specific rules.

Examples:

```text
returnPeriod
warrantyPeriod
returnCondition
restockingFee
refundEligibility
```

Policy precedence should be clearly defined.

---

# 34. RETURN VS REFUND

Keep these concepts separate.

Return:

```text
Customer returns product
```

Refund:

```text
Money is returned
```

Possible scenarios:

```text
Return + Refund
Return without Refund
Refund without Return
```

depending on policy and dispute resolution.

---

# 35. CANCELLATION POLICY

Support:

```text
Customer cancellation
Provider cancellation
Admin cancellation
```

Track:

```text
cancelledBy
reason
timestamp
```

Cancellation rules may depend on order stage.

---

# 36. NO-SHOW POLICY

Support:

```text
CUSTOMER_NO_SHOW
PROVIDER_NO_SHOW
```

Possible consequences:

- cancellation
- refund
- penalty
- reliability score impact

Do not silently delete the appointment/order.

---

# 37. WARRANTY

Services and products may have warranties.

Conceptually:

```text
ServiceWarranty
```

Possible fields:

```text
id
orderId
providerId
startDate
endDate
terms
status
```

A customer may create a warranty claim during the valid period.

---

# 38. DISPUTE SYSTEM

Implement a general dispute model.

Types:

```text
ORDER_DISPUTE
PAYMENT_DISPUTE
SERVICE_DISPUTE
PRODUCT_DISPUTE
REFUND_DISPUTE
WARRANTY_DISPUTE
```

Statuses:

```text
OPEN
UNDER_REVIEW
WAITING_CUSTOMER
WAITING_PROVIDER
RESOLVED
REJECTED
CANCELED
```

Suggested entity:

```text
Dispute
```

Fields:

```text
id
orderId
customerId
providerId
type
status
reason
description
resolution
resolvedBy
createdAt
resolvedAt
```

---

# 39. DISPUTE EVIDENCE

Create:

```text
DisputeEvidence
```

Support:

- images
- videos
- documents
- payment proof
- relevant messages
- invoices
- service evidence

Evidence must be access-controlled.

---

# 40. REFUNDS

Create a separate:

```text
Refund
```

entity.

Suggested:

```text
id
paymentId
orderId
amount
reason
status
requestedBy
approvedBy
createdAt
processedAt
```

Statuses:

```text
REQUESTED
APPROVED
REJECTED
PROCESSING
COMPLETED
FAILED
```

Support partial refunds.

---

# 41. ADDITIONAL CHARGES

A provider must NOT silently change the accepted offer price.

If additional work is discovered:

```text
Provider requests additional charge
        ↓
Customer reviews
        ↓
Customer accepts/rejects
        ↓
If accepted:
    amountDue increases
```

Create an explicit:

```text
AdditionalCharge
```

entity or equivalent.

Track:

```text
reason
amount
requestedBy
approvedBy
status
createdAt
```

---

# 42. PAYMENT

Payment must be a separate domain from Order.

Recommended:

```text
Payment
PaymentAttempt
PaymentProof
Refund
```

Payment status:

```text
PENDING
PROOF_SUBMITTED
PROVIDER_CONFIRMED
REJECTED
DISPUTED
EXPIRED
CANCELED
REFUNDED
```

---

# 43. PAYMENT METHODS

Initial methods:

```text
VODAFONE_CASH
INSTAPAY
CASH
```

Design for future:

```text
CARD
WALLET
OTHER
```

---

# 44. PAYMENT ENTITY

Suggested fields:

```text
id
orderId
customerId
providerId
method
status
amount
currency
amountPaid
remainingAmount
createdAt
submittedAt
confirmedAt
confirmedBy
rejectedAt
rejectionReason
disputedAt
metadata
updatedAt
```

Use:

```text
DECIMAL
```

for money.

Never use floating point.

---

# 45. PAYMENT AMOUNT

The backend determines:

```text
amountDue
```

based on:

```text
accepted offer
+
approved additional charges
-
approved discounts/refunds
```

The client cannot arbitrarily choose the payable amount.

---

# 46. VODAFONE CASH

Flow:

```text
Customer selects Vodafone Cash
        ↓
Backend displays provider receiving information
        ↓
Customer transfers money
        ↓
Customer uploads screenshot
        ↓
Payment = PROOF_SUBMITTED
        ↓
Provider receives notification
        ↓
Provider checks actual Vodafone Cash account
        ↓
Provider confirms only if money actually arrived
        ↓
Payment = PROVIDER_CONFIRMED
```

CRITICAL:

```text
Screenshot != confirmation
```

---

# 47. INSTAPAY

Flow:

```text
Customer selects InstaPay
        ↓
Backend displays receiving information
        ↓
Customer transfers money
        ↓
Customer uploads proof
        ↓
Payment = PROOF_SUBMITTED
        ↓
Provider verifies actual account
        ↓
Provider confirms
        ↓
Payment = PROVIDER_CONFIRMED
```

Again:

```text
Screenshot != confirmation
```

---

# 48. CASH

Flow:

```text
Customer selects CASH
        ↓
Customer pays provider
        ↓
Provider confirms cash received
        ↓
Payment = PROVIDER_CONFIRMED
```

Only the accepted provider may confirm.

---

# 49. PAYMENT PROOF

Create:

```text
PaymentProof
```

Fields:

```text
id
paymentId
fileUrl
uploadedBy
uploadedAt
reviewedBy
reviewedAt
status
rejectionReason
```

Proof must be private.

---

# 50. MULTIPLE PAYMENT PROOFS

Support multiple proofs.

Never overwrite old evidence.

Possible reasons:

- corrected screenshot
- multiple transfers
- provider requests new proof
- split payment

---

# 51. PAYMENT AUTHORIZATION

Only the provider assigned to the accepted order can confirm/reject payment.

Authorization:

```text
JWT
 ↓
Provider
 ↓
Accepted Order
 ↓
Payment
```

Never trust a provider ID from request body.

---

# 52. PAYMENT DISPUTE

Example:

```text
Customer:
"I transferred the money."

Provider:
"I did not receive it."
```

Payment can become:

```text
DISPUTED
```

Admin/Finance investigates using:

- payment proof
- transaction information
- audit history
- relevant chat/evidence

---

# 53. PAYMENT AUDIT

Audit:

```text
PAYMENT_CREATED
PAYMENT_METHOD_SELECTED
PAYMENT_PROOF_UPLOADED
PAYMENT_PROOF_REVIEWED
PAYMENT_CONFIRMED
PAYMENT_REJECTED
PAYMENT_DISPUTED
PAYMENT_REFUNDED
```

---

# 54. PAYMENT NOTIFICATIONS

Customer:

```text
PAYMENT_PROOF_SUBMITTED
PAYMENT_CONFIRMED
PAYMENT_REJECTED
PAYMENT_DISPUTED
```

Provider:

```text
PAYMENT_PROOF_SUBMITTED
PAYMENT_DISPUTED
```

Admin:

```text
PAYMENT_DISPUTED
```

---

# 55. PAYMENT TIMING

The architecture must support:

```text
PAY_BEFORE_SERVICE
PAY_AT_SERVICE
PAY_AFTER_SERVICE
DEPOSIT_THEN_REMAINDER
```

For V1, implement the selected business rule cleanly but do not couple Payment status to Order status.

---

# 56. PAYMENT METHOD CHANGE

Recommended:

While payment is:

```text
PENDING
```

customer can change method.

After proof submission:

```text
PROOF_SUBMITTED
```

do not silently mutate the old payment.

Either:

- cancel old payment attempt and create new one
- or create a new payment attempt

Preserve history.

---

# 57. INVOICING

Create:

```text
Invoice
```

Conceptually:

```text
id
orderId
providerId
customerId
subtotal
discount
additionalCharges
refunds
total
paid
remaining
currency
issuedAt
```

The invoice must be consistent with the actual financial records.

---

# 58. PLATFORM COMMISSION

Design the financial model to support future commission.

Potential values:

```text
grossAmount
discount
additionalCharges
refundAmount
platformFee
providerNetAmount
```

Do not hard-code the assumption that:

```text
provider receives 100% of order price
```

---

# 59. APPOINTMENTS

Orders may contain appointments.

Appointments should consider:

- provider working hours
- branch
- availability
- requested date
- service duration

Do not blindly accept impossible appointment times.

---

# 60. APPOINTMENT RESCHEDULING

Do not simply overwrite the appointment date.

Preserve history.

Track:

```text
oldDate
newDate
changedBy
reason
createdAt
```

---

# 61. NO-SHOW

Appointment no-show must be represented explicitly.

Possible outcomes:

```text
CUSTOMER_NO_SHOW
PROVIDER_NO_SHOW
RESCHEDULED
CANCELED
```

Consequences should be configurable.

---

# 62. ORDER TIMELINE

Create:

```text
OrderTimeline
```

Example:

```text
Order created
Providers notified
Provider submitted offer
Customer accepted offer
Payment proof uploaded
Payment confirmed
Service started
Service completed
```

Every important order transition should produce timeline history.

---

# 63. ORDER COMPLETION

Recommended flow:

```text
Provider marks service completed
        ↓
Customer confirms
        ↓
Order = COMPLETED
```

A configurable timeout may automatically complete the order if customer does not respond.

Do not finalize this behavior without making it configurable.

---

# 64. RATINGS

Customer can rate provider after a qualifying completed order.

Prevent arbitrary ratings.

Recommended:

```text
UNIQUE(order_id, customer_id)
```

or equivalent business constraint.

---

# 65. PROVIDER RELIABILITY

Track:

```text
response rate
response time
acceptance rate
cancellation rate
completion rate
no-show rate
dispute rate
average rating
```

Use these metrics to calculate provider reliability/ranking.

---

# 66. CUSTOMER RELIABILITY

Collect data for:

- cancellation
- no-show
- dispute behavior
- completed orders

Do not use this to unfairly punish customers in V1.

Keep it primarily analytical until business rules are finalized.

---

# 67. PROVIDER SUSPENSION

Provider status should support:

```text
ACTIVE
SUSPENDED
BANNED
```

Suspended providers should not receive new orders/offers.

Existing orders require explicit business handling.

---

# 68. FRAUD / ABUSE

Implement basic detection/auditing for:

- repeated payment proof submissions
- duplicate transaction information
- suspicious refunds
- excessive disputes
- fake payment confirmation
- excessive provider cancellations
- abnormal account behavior
- repeated authorization failures

Do not over-engineer ML fraud detection in V1.

Build the data foundation first.

---

# 69. CHAT

Conversation should be associated with the Order.

Prefer:

```text
Conversation
 ├── orderId
 ├── customerId
 └── providerId
```

rather than one permanent customer-provider conversation.

This keeps conversations isolated per transaction.

---

# 70. CHAT AUTHORIZATION

Only:

```text
customer
accepted provider
authorized admin/support
```

should access an order conversation.

A user must not access another order's chat by changing an order ID.

---

# 71. CALL SIGNALING

Secure:

- JWT
- participant IDs
- call room/channel IDs
- Agora/LiveKit tokens

Never allow arbitrary users to generate tokens for another user.

---

# 72. FILE SECURITY

For all uploads:

```text
profile image
background image
provider document
order attachment
payment proof
dispute evidence
```

validate:

- MIME type
- extension
- file signature when possible
- size
- generated filename

Do not trust original filenames.

Private files must not be publicly accessible.

---

# 73. NOTIFICATIONS

Persist important notifications.

Examples:

```text
ORDER_CREATED
ORDER_TARGETED
NEW_RESPONSE
RESPONSE_ACCEPTED
RESPONSE_DECLINED
RESPONSE_EXPIRED
ORDER_CANCELED
PAYMENT_PROOF_SUBMITTED
PAYMENT_CONFIRMED
PAYMENT_REJECTED
PAYMENT_DISPUTED
ORDER_COMPLETED
```

FCM is delivery mechanism, not the only notification storage.

---

# 74. ADVERTISEMENTS

Support:

```text
ACTIVE
INACTIVE
EXPIRED
```

Automatic expiration job.

Manual INACTIVE should remain inactive.

Track:

```text
impressions
clicks
```

---

# 75. SAVED OFFERS

User ownership must come from JWT.

Prevent duplicate saved offers.

Recommended:

```text
UNIQUE(user_id, offer_id)
```

---

# 76. FOLLOW PROVIDERS

Prevent duplicate follows:

```text
UNIQUE(user_id, provider_id)
```

---

# 77. AUDIT LOG

Create centralized audit logging.

Important events:

```text
LOGIN
REGISTER
ROLE_CHANGE
PROVIDER_APPROVED
PROVIDER_REJECTED
PROVIDER_SUSPENDED

ORDER_CREATED
ORDER_CANCELED
ORDER_COMPLETED

RESPONSE_CREATED
RESPONSE_ACCEPTED
RESPONSE_DECLINED
RESPONSE_EXPIRED

PAYMENT_CREATED
PAYMENT_PROOF_UPLOADED
PAYMENT_CONFIRMED
PAYMENT_REJECTED
PAYMENT_DISPUTED

REFUND_CREATED
ADMIN_ACTION
```

---

# 78. ERROR MODEL

Use consistent error responses.

Example:

```json
{
  "success": false,
  "code": "OFFER_EXPIRED",
  "message": "This offer has expired.",
  "timestamp": "2026-10-29T10:00:00Z"
}
```

Implement meaningful business error codes.

---

# 79. PAGINATION

Paginate:

- orders
- responses
- notifications
- providers
- offers
- payments
- disputes
- audit logs
- advertisements

Never return unlimited lists.

---

# 80. TRANSACTION SAFETY

Use transactions for:

- offer acceptance
- order cancellation
- payment confirmation
- payment rejection
- refund approval
- additional charge approval
- provider assignment

Critical state transitions must be atomic.

---

# 81. TESTING

After implementation, create/execute tests for:

## Authentication

- registration
- login
- invalid credentials
- unauthorized access
- admin registration blocked

## Authorization

- user accessing another user's order
- provider accessing another provider's order
- provider modifying another provider
- unauthorized payment confirmation

## Orders

- create order
- selected provider
- automatic matching
- ALL-services matching
- order expiration
- cancellation

## Offers

- valid offer
- duplicate offer
- expired offer
- accepting expired offer
- accepting already accepted offer
- concurrent acceptance

## Payments

- Vodafone Cash
- InstaPay
- Cash
- proof upload
- provider confirmation
- wrong provider confirmation
- rejection
- dispute
- refund

## Policies

- provider policy
- policy versioning
- order policy snapshot
- cancellation
- return
- refund
- warranty
- no-show

## Security

Test every endpoint for IDOR/ownership vulnerabilities.

---

# 82. DATABASE RESET / SEEDING

Because this is not production:

You may recreate the database.

After schema creation:

Create development seed data where useful:

- users
- providers
- branches
- services
- reference data
- sample orders
- sample offers
- sample payments
- sample disputes

Do NOT create fake production credentials that could accidentally be deployed.

---

# 83. API DESIGN

Existing endpoints may be retained temporarily for compatibility, for example:

```text
POST /orders/insert
GET /responses/get-all-response-of-user
POST /responses/accept-response
POST /responses/cancel-response
POST /orders/cancel
POST /responses/insert
POST /responses/update
```

But their implementation must follow the new rules.

You may redesign endpoint naming where beneficial.

Do not preserve bad endpoint semantics merely because they already exist.

---

# 84. API DOCUMENTATION

After coding is COMPLETE:

Update:

```text
API_REFERENCE.md
```

The documentation must describe the FINAL implemented backend.

It must include:

- authentication
- all endpoints
- request DTOs
- response DTOs
- HTTP status codes
- error codes
- authorization requirements
- role requirements
- ownership rules
- pagination
- upload requirements
- WebSocket
- notifications
- payment flow
- offer expiration
- refund
- disputes
- policies

Do not update the document based on assumptions.

Generate it from the actual implementation.

---

# 85. ROLE-BASED APPLICATION GUIDE

After coding is COMPLETE:

Update:

```text
APP_GUIDE_BY_ROLE.md
```

It must describe the actual application behavior for:

```text
USER
PROVIDER
ADMIN
```

And where relevant:

```text
SUPER_ADMIN
SUPPORT_ADMIN
CONTENT_ADMIN
FINANCE_ADMIN
```

For each role document:

- login
- registration
- profile
- permissions
- order flow
- offers
- payment
- disputes
- refunds
- notifications
- chat
- calls
- ratings
- provider management
- policies
- analytics
- restrictions

Again:

> The guide must describe what the final backend actually does.

---

# 86. DOCUMENTATION VALIDATION

Before declaring the task complete:

Compare:

```text
Actual Controllers
Actual DTOs
Actual Security
Actual Services
Actual Entities
Actual WebSocket endpoints
Actual scheduled jobs
```

against:

```text
API_REFERENCE.md
APP_GUIDE_BY_ROLE.md
```

Fix any mismatch.

There must be no documented endpoint that does not exist.

There must be no important implemented endpoint missing from the documentation.

---

# 87. LEGACY CODE CLEANUP

After migration:

Remove or refactor:

- obsolete entities
- obsolete repositories
- obsolete DTOs
- duplicate business logic
- insecure endpoints
- unused fields
- unused services
- dead code

Do not leave two conflicting implementations of the same business rule.

---

# 88. FINAL BUSINESS FLOW

The final flow should conceptually be:

```text
Customer
   |
   v
Create Order
   |
   v
MATCHING
   |
   +---- Explicit Provider
   |          |
   |          v
   |     Target Provider
   |
   +---- Automatic Matching
              |
              v
       Match ALL Services
              |
              v
       Notify Providers
              |
              v
            QUOTING
              |
       +------+------+------+
       |      |      |
    Offer A Offer B Offer C
       |      |      |
    expires expires expires
       +------+------+------+
              |
              v
       Customer compares
              |
              v
       Accept one offer
              |
              v
       Atomic transaction
              |
       +------+------+
       |             |
   ACCEPTED       DECLINED
   Provider       Others
       |
       v
  Payment Created
       |
       +------------------------+
       |            |           |
 Vodafone Cash   InstaPay     Cash
       |            |           |
 Proof upload   Proof upload  Provider
       |            |          confirms
       +------------+-----------+
                    |
                    v
          Provider verification
                    |
                    v
           PAYMENT_CONFIRMED
                    |
                    v
              IN_PROGRESS
                    |
                    v
              SERVICE DONE
                    |
                    v
              COMPLETED
                    |
                    v
             Rating / Warranty
```

---

# 89. NON-NEGOTIABLE BUSINESS PRINCIPLES

The implementation MUST follow these principles:

1. Never trust client identity.
2. Never trust client authorization.
3. Never trust client payment amount.
4. Never treat payment screenshot as payment confirmation.
5. Never accept expired offers.
6. Never delete historical offers/responses.
7. Never allow two accepted responses for one order.
8. Never mix payment status with order status.
9. Never expose private payment/provider documents publicly.
10. Never allow a provider to confirm another provider's payment.
11. Never silently modify accepted offer prices.
12. Never silently overwrite policy history.
13. Never allow provider policies to violate platform/legal restrictions.
14. Never rely only on frontend validation.
15. Audit important business and financial actions.
16. Preserve transaction history.
17. Backend/database transactions are the source of truth.

---

# 90. IMPLEMENTATION ORDER

Do the work in this order:

### Phase 1

Analyze current backend.

### Phase 2

Finalize domain model.

### Phase 3

Recreate development database.

### Phase 4

Implement entities/repositories.

### Phase 5

Implement authentication/authorization.

### Phase 6

Implement provider/business management.

### Phase 7

Implement order and provider matching.

### Phase 8

Implement offers and expiration.

### Phase 9

Implement acceptance and concurrency protection.

### Phase 10

Implement policies and snapshots.

### Phase 11

Implement appointments.

### Phase 12

Implement payments.

### Phase 13

Implement proofs/refunds/disputes.

### Phase 14

Implement notifications.

### Phase 15

Implement audit/history.

### Phase 16

Implement tests.

### Phase 17

Remove legacy/dead code.

### Phase 18

Update:

```text
API_REFERENCE.md
APP_GUIDE_BY_ROLE.md
```

### Phase 19

Perform final consistency/security review.

---

# 91. FINAL COMPLETION REQUIREMENT

Do NOT consider the task complete merely because the application compiles.

Before finishing, verify:

```text
[ ] Backend compiles
[ ] Database schema works
[ ] Database relationships are correct
[ ] Security works
[ ] Ownership checks work
[ ] Provider matching works
[ ] ALL-services matching works
[ ] Offer expiration works
[ ] Offer acceptance is atomic
[ ] Payment lifecycle works
[ ] Vodafone Cash works
[ ] InstaPay works
[ ] Cash works
[ ] Payment proof is private
[ ] Provider payment verification is secured
[ ] Refunds work
[ ] Disputes work
[ ] Policies work
[ ] Policy snapshots work
[ ] Cancellation works
[ ] Warranty works
[ ] No-show rules work
[ ] Notifications work
[ ] Audit logging works
[ ] Tests pass
[ ] Legacy insecure code removed
[ ] API_REFERENCE.md updated
[ ] APP_GUIDE_BY_ROLE.md updated
```

---

# 92. FINAL OUTPUT REQUIRED FROM YOU

When implementation is complete, provide a concise implementation report containing:

### Database

- entities created
- entities removed
- major relationships
- important indexes/constraints

### Security

- authentication changes
- authorization changes
- ownership protections

### Orders

- lifecycle
- matching
- offer expiration
- acceptance

### Payments

- methods
- proof
- confirmation
- disputes
- refunds

### Policies

- provider policies
- versioning
- order snapshots
- cancellation
- return
- warranty
- no-show

### APIs

- added endpoints
- modified endpoints
- removed/deprecated endpoints

### Tests

- tests added
- critical scenarios verified

### Documentation

Confirm that:

```text
API_REFERENCE.md
APP_GUIDE_BY_ROLE.md
```

were updated to match the actual final implementation.

---

# FINAL INSTRUCTION

Do not rush into coding.

First understand the current project.

Then redesign the domain and database.

Then implement the backend systematically.

The objective is not to preserve the current backend.

The objective is to create a **secure, transactional, scalable, auditable WaDii marketplace backend** that can support:

```text
Customers
Providers
Multiple branches
Multiple services
Orders
Provider matching
Offers
Offer expiration
Appointments
Payments
Vodafone Cash
InstaPay
Cash
Payment proofs
Refunds
Disputes
Returns
Warranty
Provider policies
Policy versioning
Additional charges
Invoices
Ratings
Chat
Calls
Notifications
Analytics
Provider reliability
Platform commission
Future automated payments
```

Do not declare completion until the implementation and the two documentation files accurately represent the same final system.