# WaDii — Application Guide by Role

What each role can do in the implemented backend. Endpoint details: `API_REFERENCE.md` (section numbers in brackets).
Domain model: `docs/DOMAIN_MODEL.md`.

Global rules for every role:
- Identity always comes from the JWT. Ids of users/providers in request bodies never grant access.
- Failures use real HTTP codes and a business `code` (e.g. 403 `ACCESS_DENIED`, 409 `OFFER_EXPIRED`) [1.2, 1.3].
- Every important action is audited and written to the order timeline; history (offers, payments, proofs, refunds,
  policy versions, appointment changes) is never deleted.
- Lists are paginated (`page`, `size` <= 100) [1.4].

---

## 1. USER (customer, `userType` 0)

**Registration and login** [2]
- Registers with `userType: 0` (email unique, password >= 8, `cityId`). Gets a JWT immediately.
- Logs in with email + password (+ FCM token for push). Wrong email and wrong password give the same 401.
- Can change password or log out everywhere (all tokens revoked).

**Profile** [3]
- Reads and edits only their own profile (`/users/me`), uploads a public avatar / background image.
- Can apply to become a provider (`POST /providers/apply`) -> role PROVIDER, verification PENDING, must log in again [5.2].

**Browsing**
- Catalog (services, car types, cities) without login; verified providers with ratings, branches, policies [5.1];
  promotional offers, which they can save [20]; follows providers [5.1].

**Order flow** [6]
1. Creates an order: car, requested date, location, services (one or more), spare parts to price, optional comment,
   optional attachments (private photos/PDF), optionally a specific provider.
2. With a provider chosen: only that provider is invited (must be verified, active, and offer **all** services).
   Without: every eligible provider offering **all** services near the location is invited (top 20 by ranking).
   If nobody matches, the order waits in MATCHING and is retried until its 48 h deadline.
3. Receives offers (NEW_RESPONSE notification) and compares them sorted by RECOMMENDED, LOWEST_PRICE, HIGHEST_RATED,
   NEAREST or FASTEST_RESPONSE [7]. Each offer shows price breakdown, branch distance, expiry and provider rating.
4. Declines offers or accepts one. Acceptance is atomic: the chosen offer is ACCEPTED, all others DECLINED,
   policies are snapshotted, a chat and a payment request are created. Expired offers cannot be accepted.
5. Can edit the order only before any offer arrives; otherwise cancel and create a new one.
6. If no offer is accepted within 48 h, the order becomes EXPIRED.

**Appointment** [8]
- Schedules or reschedules (with a reason) inside the branch working hours; every change is kept.
- Reports a PROVIDER_NO_SHOW after the appointment time + 30 min -> order canceled, full refund approved.

**Payment (V1: pay before the service starts)** [9]
- Chooses Vodafone Cash, InstaPay or Cash on the payment request.
- Vodafone Cash / InstaPay: sees the provider's receiving account, transfers outside the app, uploads one or more
  screenshots (private). The payment shows PROOF_SUBMITTED until the **provider** confirms the money arrived.
  A screenshot alone never confirms anything.
- Cash: pays in person; the provider confirms.
- If rejected, pays again (new payment) or opens a payment dispute. Can switch method; after a proof was uploaded
  the old payment is canceled and a new one opened (history kept).
- Approves or rejects additional charges requested by the provider; approved charges must be paid before completion [10].

**Completion, rating, aftercare**
- When the provider marks the job done, confirms completion (or it auto-completes after 72 h if fully paid).
  An invoice is issued [12]; a warranty is issued if the provider's warranty policy grants one [15].
- Rates the provider once per completed order (1–5) [16].
- Files warranty claims while the warranty is valid; escalates a rejected claim to a dispute [15].
- Requests a return inside the return window (separate from refunds) [15].
- Requests partial or full refunds of confirmed payments inside the refund window; the provider (or finance)
  approves and sends the money back [11].

**Cancellation** [6.4]
- Any time before the service starts. Before acceptance: free. After acceptance: refund of what was paid, minus the
  cancellation fee from the snapshot if canceling inside the free-cancellation window before the appointment.
- After the service started: cannot cancel; opens a dispute instead.

**Disputes** [14]
- Opens ORDER, SERVICE, PRODUCT, REFUND, WARRANTY or PAYMENT disputes on their orders, adds private evidence
  (photos, videos, documents, chat messages, payment proofs, notes), follows status, cancels while OPEN.

**Notifications, chat, calls** [17, 18, 19]
- Persistent inbox + push + WebSocket. Chat and voice/video calls only with the accepted provider of each order,
  inside that order's conversation.

**Restrictions**
- Cannot see other customers' orders, offers they did not receive, other providers' data, or any private file
  that is not theirs. Cannot rate without a completed order. Customer statistics are collected for analytics only
  and never restrict a customer in V1.

---

## 2. PROVIDER (`userType` 1)

**Registration and verification** [2, 5]
- Registers with `userType: 1` and `providerName`, or applies from a customer account. Starts PENDING.
- Uploads verification documents (private: only the provider and support admins can open them).
- A SUPPORT admin verifies or rejects. Only VERIFIED + ACTIVE providers that are accepting orders receive orders.

**Business profile** [5.3]
- Name, description, service radius (km), services offered, links, availability switch (`acceptingOrders`).
- Branches with address, coordinates, phone, active flag and weekly working hours (used for matching distance,
  day availability and appointment validation). Branches are deactivated, never deleted.
- Receiving accounts for Vodafone Cash and InstaPay (copied onto each payment when the customer chooses the method).
- Own reliability statistics (response rate, completion, cancellations, no-shows, disputes, average response time).

**Policies** [13]
- Publishes versions of Return, Refund, Cancellation, Warranty, Payment, Service and No-Show policies.
  Every change is a new version; older versions stay. Versions weaker than platform rules are refused
  (e.g. cancellation fee above 50%).
- Orders keep the snapshot taken when the offer was accepted; later changes never affect them.

**Order flow** [6, 7]
1. Gets ORDER_TARGETED notifications (no customer identity until accepted) and lists invited orders by scope
   OPEN / ASSIGNED / CLOSED.
2. Sends one offer per order: labor, per-part prices, discount (price computed by the server), duration, branch,
   optional appointment proposal, expiry between 1 h and 7 days (default 48 h, capped at the order deadline).
3. Edits or withdraws a pending offer; declines invitations.
4. On acceptance (RESPONSE_ACCEPTED) the chat opens and the customer sees the payment request. Not selected ->
   RESPONSE_DECLINED; expired -> RESPONSE_EXPIRED.

**Payment** [9]
- Only the accepted provider of the order can confirm or reject its payments.
- Vodafone Cash / InstaPay: checks the real wallet/bank account and confirms only if the money arrived; otherwise
  rejects with a reason. Cash: confirms when received.
- Cannot start the service before the order is fully paid.
- Requests additional charges for extra work; never edits the accepted price [10].
- Approves/rejects refund requests and marks approved refunds as sent [11]. Provider/admin/no-show cancellations
  create refunds already approved that the provider must send.

**Service execution**
- Schedules/reschedules appointments, reports CUSTOMER_NO_SHOW (refund minus the no-show fee from the snapshot).
- Starts the job, marks it done; the customer confirms or it auto-completes.
- Handles warranty claims (accept / reject / resolve) and returns (approve, receive with optional refund, close).

**Cancellation** [6.4]
- May cancel an accepted order before starting, with a reason; the customer is refunded in full. Cancellations
  lower reliability; more than 5 in 30 days raise a fraud flag for admins.

**Disputes, chat, calls, notifications** [14, 17, 19]
- Opens disputes on orders they won, adds evidence; chats and calls with the customer of each accepted order.

**Promotions and audience** [20, 5.1]
- Publishes promotional offers linked to services; sees follower count and ratings.

**Restrictions**
- Cannot respond to orders they were not invited to, see other providers' offers, confirm another provider's payments,
  edit another provider's branches/documents, or access orders/chats of other providers.
- While SUSPENDED/BANNED: no new orders or offers; pending offers are withdrawn automatically; orders already
  accepted are handled by support.
- A provider account may also place orders as a customer, but never with its own provider profile.

---

## 3. ADMIN (`userType` 2) and admin permissions

Admins never come from public registration. The first SUPER_ADMIN comes from `ADMIN_EMAIL` / `ADMIN_PASSWORD`
at startup; other admins are created by a SUPER_ADMIN [22]. Admins cannot place orders. Every admin action is audited.

| Capability | SUPER | SUPPORT | CONTENT | FINANCE |
|---|:-:|:-:|:-:|:-:|
| Create admins, change admin roles | ✔ | | | |
| List users, suspend/ban/reactivate accounts (admins: SUPER only) | ✔ | ✔ | | |
| Verify/reject providers, suspend/ban/reactivate, read verification documents | ✔ | ✔ | | |
| Read any order, its timeline, attachments, conversation (read-only) | ✔ | ✔ | | |
| Cancel any non-terminal order (full refund) | ✔ | ✔ | | |
| Handle ORDER / SERVICE / PRODUCT / WARRANTY disputes | ✔ | ✔ | | |
| Handle PAYMENT / REFUND disputes, financial outcomes | ✔ | | | ✔ |
| List payments and refunds, approve/complete refunds, read payment proofs | ✔ | (proofs) | | ✔ |
| Catalog (services, car types, cities, reasons), advertisements, rating moderation | ✔ | (ratings) | ✔ | |
| Audit logs | ✔ | ✔ | | ✔ |
| Fraud flags (list, review, dismiss) | ✔ | ✔ | | ✔ |

### SUPER_ADMIN
Everything below, plus admin account management. Cannot remove their own SUPER_ADMIN role.

### SUPPORT_ADMIN
- **Provider management** [5.4]: review PENDING providers and documents, verify or reject (provider notified),
  suspend / ban / reactivate. On suspension, the response tells how many accepted orders need explicit handling.
- **Users** [22]: search, view, suspend/ban/reactivate non-admin accounts (tokens revoked immediately).
- **Orders**: view all orders, timelines, attachments and conversations; cancel orders with a reason (full refund).
- **Disputes** [14]: move non-financial disputes through UNDER_REVIEW / WAITING_CUSTOMER / WAITING_PROVIDER,
  resolve with an outcome (non-financial) or reject; read evidence.
- **Moderation**: delete ratings; review fraud flags.

### CONTENT_ADMIN
- **Catalog** [4]: countries, provinces, cities, services (activate/deactivate), car types, spare-part suggestions,
  cancellation reasons.
- **Advertisements** [21]: create, edit, activate/deactivate (INACTIVE stays inactive), delete, daily
  impression/click stats; ads past their end date expire automatically.
- **Ratings**: moderate (delete).

### FINANCE_ADMIN
- **Payments** [9]: list and filter all payments; read proofs.
- **Payment disputes** [14]: investigate with proofs, transaction references, audit history and chat evidence;
  resolve as PAYMENT_CONFIRMED (payment confirmed by finance) or PAYMENT_REJECTED, or REFUND_CUSTOMER with an amount.
- **Refunds** [11]: list, approve/reject, mark completed (overrides when the provider does not act).
- **Audit and fraud**: audit logs, fraud flags (duplicate transaction references, repeated proofs, suspicious refunds).
- Commission (`wadii.commission.percent`) shows in every invoice as `platformFee` / `providerNet` [12].

### Analytics available to admins
- Provider reliability metrics (`provider_stats`, recomputed hourly), customer behaviour (`customer_stats`,
  analytical only), order funnel per provider (`order_provider_targets`: targeted -> viewed -> responded),
  advertisement daily stats, audit log and fraud flags.

---

## 4. Calls and real-time (all roles)

- Calls only inside an order conversation, between its customer and accepted provider. The server creates the channel
  (`conv_<id>`) and issues Agora/LiveKit tokens only for the caller's own user id [17].
- Real-time events over STOMP (`/ws`, `/ws-native`): notifications, messages, typing, read receipts, call signaling;
  users can only subscribe to their own queues [18].

## 5. What changed for existing clients

- New API under `/api/v1`; `/auth/login` and `/auth/register` still work at the root.
- Only the legacy endpoints listed in `API_REFERENCE.md` section 23 remain (deprecated, same rules as the new API).
- Chat is per order (conversation id), not per user pair; the raw `/web-socket/{userId}` socket was removed.
- Payment is a separate flow after acceptance; the order has no PAID status.
