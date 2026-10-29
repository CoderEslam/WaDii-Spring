# WaDii Domain Model (V1)

Source of truth for the re-architected backend. The schema lives in
`src/main/resources/db/migration` (Flyway); entities mirror it with `ddl-auto=validate`.

## Conventions

- Money: `NUMERIC(12,2)` / `BigDecimal`, currency `CHAR(3)` (default `EGP`). No floating point money.
- Time: every instant is `TIMESTAMPTZ` / `java.time.Instant`, stored in UTC.
  Calendar values (requested day, working hours) use `DATE` / `TIME` interpreted in
  the platform zone `wadii.timezone` (default `Africa/Cairo`).
- History is never deleted: responses, payments, proofs, refunds, policy versions,
  appointment changes, timeline, audit log.
- Optimistic `version` columns on aggregates that see concurrent writes
  (orders, responses, payments, appointments, disputes); pessimistic row locks on the
  order for every state transition that touches several rows.

## Identity

| Table | Notes |
|---|---|
| `users` | `role` USER / PROVIDER / ADMIN, `status` ACTIVE / SUSPENDED / BANNED, `token_version` (bumping it revokes all JWTs) |
| `user_admin_roles` | SUPER_ADMIN, SUPPORT_ADMIN, CONTENT_ADMIN, FINANCE_ADMIN. SUPER_ADMIN implies all. |

## Provider

| Table | Notes |
|---|---|
| `providers` | 1:1 with a PROVIDER user. `status` ACTIVE / SUSPENDED / BANNED, `verification_status` PENDING / VERIFIED / REJECTED / SUSPENDED, `accepting_orders`, `service_radius_km` |
| `provider_documents` | private verification files (ID, tax card, licence) |
| `provider_branches` | name, address, lat/lng, phone, active |
| `branch_working_hours` | one row per branch and ISO day of week |
| `provider_services` | which catalog services a provider offers |
| `provider_links` | social / website links |
| `provider_payment_accounts` | receiving info per method (Vodafone Cash number, InstaPay address) |
| `provider_policies` + `provider_policy_versions` | one policy per (provider, type); versions are append-only |
| `provider_stats` | reliability metrics, recomputed by a job |
| `provider_offers` (+ `provider_offer_services`) | promotional offers shown in the app |

A provider is **eligible** for new orders when user ACTIVE, provider ACTIVE,
verification VERIFIED and `accepting_orders = true`.

## Order

```
Order ──< OrderService >── Service
  ├──< OrderSparePart
  ├──< OrderAttachment            (private files)
  ├──< OrderTimelineEvent
  ├──< OrderProviderTarget        (who was invited, funnel status)
  ├──< OrderResponse ──< ResponsePartPrice
  ├──  OrderCancellation          (0..1)
  ├──< OrderPolicySnapshot        (one per policy type, immutable)
  ├──  Appointment ──< AppointmentChange
  ├──< Payment ──< PaymentProof
  │       └──< Refund
  ├──< AdditionalCharge
  ├──< Dispute ──< DisputeEvidence
  ├──  ServiceWarranty ──< WarrantyClaim
  ├──< ReturnRequest
  ├──  Invoice
  ├──  Rating
  └──  Conversation ──< Message, Call
```

### Order status

```
CREATED → MATCHING → QUOTING → RESPONSES_AVAILABLE → ACCEPTED → IN_PROGRESS → COMPLETED
             │          │              │                 │            │
             └──────────┴──────────────┴─→ EXPIRED       └────────────┴─→ CANCELED
                (response deadline passed, nothing accepted)   (customer / provider / admin / no-show)
```

- `MATCHING`: no eligible provider found yet; a job retries matching until the deadline.
- `QUOTING`: providers targeted, no live offer yet.
- `RESPONSES_AVAILABLE`: at least one offer exists.
- `ACCEPTED → IN_PROGRESS` requires the order to be fully paid (V1 rule: PAY_BEFORE_SERVICE).
- `IN_PROGRESS`: the provider may mark the service done (`provider_completed_at`); the
  customer confirms → `COMPLETED`, or the auto-complete job does it after
  `wadii.order.auto-complete-hours` when enabled.
- Payment status is never stored on the order.

### Response (offer) status

`PENDING → ACCEPTED | DECLINED | EXPIRED | CANCELED`.
`UNIQUE(order_id, provider_id)` and a partial unique index
`UNIQUE(order_id) WHERE status = 'ACCEPTED'` enforce one offer per provider and at most one accepted offer per order.

## Payments

- `Payment`: one payment request for an amount due on an order. An order may have several payments
  over time (method changed after a proof was submitted, an additional charge approved after payment),
  but **at most one active payment** (`PENDING`, `PROOF_SUBMITTED`, `DISPUTED`) — a partial unique index.
  This row is the "payment attempt" from the spec; a separate attempt table would duplicate it.
- Status: `PENDING → PROOF_SUBMITTED → PROVIDER_CONFIRMED | REJECTED`, `DISPUTED`, `EXPIRED`, `CANCELED`, `REFUNDED`.
- Methods: VODAFONE_CASH, INSTAPAY, CASH (CARD, WALLET, OTHER reserved).
- `amount_due(order) = accepted price + approved additional charges`; refunds are tracked separately;
  `outstanding = amount_due − confirmed payments`.
- `PaymentProof`: append-only, private files. A proof never confirms a payment.
- `Refund`: partial refunds allowed up to the confirmed amount minus earlier refunds.
- `Invoice`: issued when the order completes, computed only from the records above, including
  `platform_fee` (`wadii.commission.percent`) and `provider_net`.

## Policies

Precedence: **legal/platform rules → provider policy version → (reserved: product/service policy) → order snapshot**.
Provider versions that weaken a platform rule are rejected (`POLICY_VIOLATES_PLATFORM_RULES`).
When an offer is accepted, the current version of every policy type is copied into
`order_policy_snapshots` (platform default if the provider has none). Disputes, cancellations,
refunds and warranties read the snapshot, never the live policy.

## Other aggregates

- `Dispute`: types ORDER / PAYMENT / SERVICE / PRODUCT / REFUND / WARRANTY; statuses
  OPEN, UNDER_REVIEW, WAITING_CUSTOMER, WAITING_PROVIDER, RESOLVED, REJECTED, CANCELED.
- `Conversation`: one per order, created when an offer is accepted; only the customer, the accepted
  provider and support/super admins can read it.
- `Notification`: persisted inbox; FCM and WebSocket are delivery channels only.
- `AuditLog`: append-only; `FraudFlag`: rule-based signals for admin review.
- `Advertisement` + `advertisement_daily_stats`.
- `Rating` (`UNIQUE(order_id)`), `Follow` (`PK(user_id, provider_id)`), `SavedOffer` (`UNIQUE(user_id, offer_id)`).

## Deliberately not built in V1

- `ProviderSubscription`: no business rules defined yet.
- Product catalog / product-specific policies: precedence slot reserved, `ReturnRequest` covers spare parts.
- Automated payment gateways: `Refund.PROCESSING/FAILED` and methods CARD/WALLET are reserved for them.
