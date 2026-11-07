# WaDii V1 — business rules as implemented

Source: *WaDii — Business Logic & Product Rules (consolidated)*. This file records how each rule is enforced in
this codebase and which default was chosen for every **OPEN DECISION**. API shapes: `docs/API_V1.md`.

## Decisions taken for the open items (all admin-configurable unless noted)

| # | Open decision | V1 implementation | Where to change it |
|---|---|---|---|
| 1 | Commission payer | Configurable `payer` = `CUSTOMER` (surcharge added to customer total) or `PROVIDER` (deducted conceptually from proceeds). Default `PROVIDER`, mode `NONE`, value 0. Never charged to both. | `POST /admin/config/COMMISSION` (finance) |
| 2 | Subscription overdue | Grace period, default 7 days. Within grace: `PAYMENT_OVERDUE` (full access). After: `RESTRICTED` = no new invitations or offers; customer mode and existing orders untouched. | `SUBSCRIPTION.graceDays` |
| 3 | Multi-service structure | One Service Order per accepted service item, linked to the parent request. | — (architecture) |
| 4 | Seller quota | Listings in `PENDING_REVIEW` + `ACTIVE`; sold/closed free the slot. Default 3. | `SELLER.maxActiveListings` |
| 5 | Cancellation fees | Free in V1 (disclosed in every policy snapshot). The ledger already has a `CANCELLATION_FEE` type for later. | code change when decided |
| 6 | Seller fee timing | Configurable `ON_PUBLISH` / `ON_SOLD`; default `ON_PUBLISH`, mode `NONE`. | `SELLER.feeTiming` |
| 7 | Fixed commission scope | Once per Service Order (per accepted item), capped at the provider total. | — |
| 8 | Billing month-end | Next due = payment date + 1 calendar month; a payment on the 31st falls due on the last day of a shorter month (Java `plusMonths`). | — |
| 9 | Cash settlement | Manual: amounts owed to WaDii are `DUE` ledger entries; the payer may "report paid"; only finance can mark `SETTLED` or `WAIVED`. | — |
| 10 | No-show values | Grace 30 min (configurable); a no-show cancels the order with no fee in V1. | `MARKETPLACE.noShowGraceMinutes` |

Further defaults: response deadline 48 h, offer validity default 24 h (min 1, max 48, never past the request
deadline), max 20 invited providers per request, default match radius 30 km, ranking window 90 days, fraud
thresholds 3 provider cancellations / 5 customer cancellations / 3 disputes per 30 days.

## Accounts and modes
- One account. `capabilities.customer` and `capabilities.seller` are always on for non-staff accounts;
  `capabilities.provider.canOperate` is true when the application is `VERIFIED`, the provider is `ACTIVE` and the
  account is `ACTIVE`. Staff accounts (`admin=true`) are created only by a super admin and cannot use the marketplace.
- Every JWT carries a token version; logout-everywhere, password change, suspension and role changes revoke all tokens.
- Provider application: `DRAFT → SUBMITTED → UNDER_REVIEW → VERIFIED | REJECTED` (rejected applications can be edited
  and resubmitted). Documents are private (applicant + staff).
- Provider suspension withdraws pending offers and declines open invitations; existing orders continue.

## Requests, matching and offers
- A request has 1–7 items, one per category. Vehicle details are required only if a chosen category has
  `vehicleRequired`. Requests store a vehicle snapshot; saved vehicles are ordered last-used first.
- Matching invites providers that are verified, active, accepting orders, not subscription-restricted, have an active
  branch covering the location (distance ≤ radius when both have coordinates, otherwise same governorate) and can
  serve at least one item (offer the category; if the category needs a vehicle, support it — an empty support list
  means all vehicles). Ranking = recent rating, response rate, completion rate, provider-fault cancellations and
  proximity over the ranking window. Ranking never sets prices.
- A provider sees only the items in categories it offers and can price only those (`CATEGORY_NOT_OFFERED`).
- A request can be edited only while no offer line exists.
- One offer per provider per request, one line per item. Each available line states a warranty (or explicit "no
  warranty"). The server computes parts total, provider total, commission and customer total, and stores the
  commission configuration version on the line.
- Accepting a line locks the item, declines competing pending lines for that item only, reserves the appointment
  (working hours + `maxBookingsPerSlot` per overlapping slot, branch row locked) and creates the Service Order with
  price, commission, warranty and policy snapshots.
- At the response deadline unaccepted open items expire with their pending lines; accepted items continue.

## Service Orders
- Execution: `ACCEPTED → IN_PROGRESS → PROVIDER_MARKED_COMPLETED → COMPLETED` (customer confirms; never automatic).
  A complaint instead of confirmation moves the order to `DISPUTED`.
- Before start: either party may cancel (free) or reschedule; after appointment + grace either party may report the
  other's no-show (order canceled with `*_NO_SHOW`).
- After start: no cancellation. Interruption: customer requests → provider submits a statement (work done, labor,
  parts, proposed amount ≤ amount due, evidence) → customer accepts (`INTERRUPTED`, amount due = proposal) or
  disputes (`DISPUTED`, staff set the final amount).
- Additional work: provider proposes a charge while in progress; it changes the amount due only when the customer approves.
- Cash (independent): provider reports the cash received (`REPORTED_COLLECTED`, or `PARTIAL` if below the amount due);
  the customer confirms (`CONFIRMED`) or disputes (`DISPUTED` + dispute + fraud flag). WaDii never claims to verify cash.
- Completion creates the invoice number, activates the warranty from the confirmation time, allows one rating and
  records the commission as a `DUE` ledger entry payable by the provider (who collects all cash).

## Disputes, warranty, fraud
- Dispute types: service quality, interruption, cash collection, warranty, cancellation, listing, other. Staff assign
  and resolve; outcomes that change money need the finance role, the rest the support role. Rejecting a dispute
  returns the order to its normal course.
- Warranty claims: customer files while active → provider accepts/rejects → customer resolves or escalates to a dispute.
- Fraud flags are for review only (one open flag per type and user); nothing is blocked automatically.

## Finance
- Ledger types are separate: `SUBSCRIPTION`, `COMMISSION`, `SELLER_FEE`, `CANCELLATION_FEE`, `ADJUSTMENT`.
  Statuses: `DUE → REPORTED_PAID → SETTLED`, or `WAIVED` / `CANCELED`. Zero amounts are never recorded.
- Subscription price 0 = `FREE_OR_DISABLED`. With a price: a `DUE` entry on the due date, reminders 7 and 3 days
  before (configurable), overdue then restricted after grace. Settling a subscription entry (or recording a payment)
  renews the cycle from the payment date. Exemptions (months or until a date) are audited with a reason.

## Configuration
- Groups `MARKETPLACE`, `COMMISSION`, `SUBSCRIPTION`, `SELLER` are versioned rows (`config_versions`) with an effective
  time and a mandatory reason; the newest version in force applies. Marketplace changes need a super admin, financial
  ones a finance admin. Category names, vehicle requirement and activation are edited per category.

## Not in V1 (by design)
Online payments, automated payouts/refunds, parts-fitment catalog, inventory, workshop resource scheduling, advanced
fraud detection, promotional plans, advertising. The data model keeps these additive.
