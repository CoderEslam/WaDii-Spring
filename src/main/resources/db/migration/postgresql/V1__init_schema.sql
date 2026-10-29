-- WaDii V1 schema. See docs/DOMAIN_MODEL.md.
-- Money: NUMERIC(12,2). Instants: TIMESTAMPTZ (UTC). History tables are append-only.

-- ============================================================ catalog
CREATE TABLE countries (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE provinces (
    id         BIGSERIAL PRIMARY KEY,
    country_id BIGINT       NOT NULL REFERENCES countries (id),
    name       VARCHAR(100) NOT NULL,
    CONSTRAINT uq_provinces_country_name UNIQUE (country_id, name)
);

CREATE TABLE cities (
    id          BIGSERIAL PRIMARY KEY,
    province_id BIGINT       NOT NULL REFERENCES provinces (id),
    name        VARCHAR(100) NOT NULL,
    CONSTRAINT uq_cities_province_name UNIQUE (province_id, name)
);

CREATE TABLE services (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE car_types (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE common_spare_parts (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE
);

CREATE TABLE cancellation_reasons (
    id         BIGSERIAL PRIMARY KEY,
    text       VARCHAR(255) NOT NULL,
    applies_to VARCHAR(20)  NOT NULL DEFAULT 'ANY', -- CUSTOMER / PROVIDER / ANY
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ============================================================ identity
CREATE TABLE users (
    id               BIGSERIAL PRIMARY KEY,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    first_name       VARCHAR(100),
    last_name        VARCHAR(100),
    phone            VARCHAR(30),
    image            VARCHAR(255),
    background_image VARCHAR(255),
    city_id          BIGINT REFERENCES cities (id),
    role             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    fcm_token        VARCHAR(512),
    token_version    INTEGER      NOT NULL DEFAULT 0,
    last_login_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'PROVIDER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED'))
);
CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));

CREATE TABLE user_admin_roles (
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    admin_role VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, admin_role),
    CONSTRAINT ck_admin_role CHECK (admin_role IN ('SUPER_ADMIN', 'SUPPORT_ADMIN', 'CONTENT_ADMIN', 'FINANCE_ADMIN'))
);

-- ============================================================ provider
CREATE TABLE providers (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL UNIQUE REFERENCES users (id),
    name                VARCHAR(150) NOT NULL,
    description         TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    accepting_orders    BOOLEAN      NOT NULL DEFAULT TRUE,
    service_radius_km   NUMERIC(7, 2),
    rating_avg          NUMERIC(3, 2) NOT NULL DEFAULT 0,
    rating_count        INTEGER      NOT NULL DEFAULT 0,
    followers_count     INTEGER      NOT NULL DEFAULT 0,
    verified_at         TIMESTAMPTZ,
    verified_by         BIGINT REFERENCES users (id),
    status_reason       VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_providers_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED')),
    CONSTRAINT ck_providers_verification CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED'))
);
CREATE INDEX ix_providers_eligibility ON providers (status, verification_status, accepting_orders);

CREATE TABLE provider_documents (
    id           BIGSERIAL PRIMARY KEY,
    provider_id  BIGINT       NOT NULL REFERENCES providers (id),
    type         VARCHAR(30)  NOT NULL,
    file_key     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    uploaded_at  TIMESTAMPTZ  NOT NULL,
    reviewed_by  BIGINT REFERENCES users (id),
    reviewed_at  TIMESTAMPTZ
);
CREATE INDEX ix_provider_documents_provider ON provider_documents (provider_id);

CREATE TABLE provider_branches (
    id          BIGSERIAL PRIMARY KEY,
    provider_id BIGINT       NOT NULL REFERENCES providers (id),
    name        VARCHAR(150) NOT NULL,
    address     VARCHAR(500),
    latitude    NUMERIC(9, 6),
    longitude   NUMERIC(9, 6),
    phone       VARCHAR(30),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_branch_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_branch_lng CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);
CREATE INDEX ix_provider_branches_provider ON provider_branches (provider_id);

CREATE TABLE branch_working_hours (
    id          BIGSERIAL PRIMARY KEY,
    branch_id   BIGINT   NOT NULL REFERENCES provider_branches (id) ON DELETE CASCADE,
    day_of_week INTEGER NOT NULL,
    opens_at    TIME     NOT NULL,
    closes_at   TIME     NOT NULL,
    CONSTRAINT uq_working_hours_branch_day UNIQUE (branch_id, day_of_week),
    CONSTRAINT ck_working_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_working_hours_range CHECK (closes_at > opens_at)
);

CREATE TABLE provider_services (
    provider_id BIGINT NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    service_id  BIGINT NOT NULL REFERENCES services (id),
    PRIMARY KEY (provider_id, service_id)
);
CREATE INDEX ix_provider_services_service ON provider_services (service_id);

CREATE TABLE provider_links (
    id          BIGSERIAL PRIMARY KEY,
    provider_id BIGINT       NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    url         VARCHAR(500) NOT NULL
);

CREATE TABLE provider_payment_accounts (
    id                 BIGSERIAL PRIMARY KEY,
    provider_id        BIGINT       NOT NULL REFERENCES providers (id),
    method             VARCHAR(20)  NOT NULL,
    account_name       VARCHAR(150) NOT NULL,
    account_identifier VARCHAR(150) NOT NULL,
    instructions       VARCHAR(500),
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_payment_account_provider_method UNIQUE (provider_id, method)
);

CREATE TABLE provider_policies (
    id                 BIGSERIAL PRIMARY KEY,
    provider_id        BIGINT      NOT NULL REFERENCES providers (id),
    policy_type        VARCHAR(30) NOT NULL,
    current_version_id BIGINT,
    created_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_provider_policy_type UNIQUE (provider_id, policy_type)
);

CREATE TABLE provider_policy_versions (
    id                      BIGSERIAL PRIMARY KEY,
    policy_id               BIGINT      NOT NULL REFERENCES provider_policies (id),
    version_no              INTEGER     NOT NULL,
    terms                   TEXT        NOT NULL,
    period_days             INTEGER,
    fee_percent             NUMERIC(5, 2),
    free_cancellation_hours INTEGER,
    created_by              BIGINT      NOT NULL REFERENCES users (id),
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_policy_version UNIQUE (policy_id, version_no),
    CONSTRAINT ck_policy_period CHECK (period_days IS NULL OR period_days >= 0),
    CONSTRAINT ck_policy_fee CHECK (fee_percent IS NULL OR fee_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_policy_free_hours CHECK (free_cancellation_hours IS NULL OR free_cancellation_hours >= 0)
);
ALTER TABLE provider_policies
    ADD CONSTRAINT fk_provider_policy_current_version FOREIGN KEY (current_version_id) REFERENCES provider_policy_versions (id);

CREATE TABLE provider_stats (
    provider_id          BIGINT PRIMARY KEY REFERENCES providers (id),
    targeted_count       INTEGER       NOT NULL DEFAULT 0,
    responded_count      INTEGER       NOT NULL DEFAULT 0,
    accepted_count       INTEGER       NOT NULL DEFAULT 0,
    completed_count      INTEGER       NOT NULL DEFAULT 0,
    provider_cancel_count INTEGER      NOT NULL DEFAULT 0,
    no_show_count        INTEGER       NOT NULL DEFAULT 0,
    dispute_count        INTEGER       NOT NULL DEFAULT 0,
    avg_response_minutes NUMERIC(10, 2),
    response_rate        NUMERIC(5, 4) NOT NULL DEFAULT 0,
    acceptance_rate      NUMERIC(5, 4) NOT NULL DEFAULT 0,
    completion_rate      NUMERIC(5, 4) NOT NULL DEFAULT 0,
    cancellation_rate    NUMERIC(5, 4) NOT NULL DEFAULT 0,
    no_show_rate         NUMERIC(5, 4) NOT NULL DEFAULT 0,
    dispute_rate         NUMERIC(5, 4) NOT NULL DEFAULT 0,
    reliability_score    NUMERIC(5, 2) NOT NULL DEFAULT 50,
    updated_at           TIMESTAMPTZ   NOT NULL
);

CREATE TABLE customer_stats (
    user_id         BIGINT PRIMARY KEY REFERENCES users (id),
    orders_count    INTEGER     NOT NULL DEFAULT 0,
    completed_count INTEGER     NOT NULL DEFAULT 0,
    canceled_count  INTEGER     NOT NULL DEFAULT 0,
    no_show_count   INTEGER     NOT NULL DEFAULT 0,
    dispute_count   INTEGER     NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE provider_offers (
    id          BIGSERIAL PRIMARY KEY,
    provider_id BIGINT       NOT NULL REFERENCES providers (id),
    title       VARCHAR(200) NOT NULL,
    description TEXT         NOT NULL,
    end_date    DATE         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_provider_offers_provider ON provider_offers (provider_id);

CREATE TABLE provider_offer_services (
    offer_id   BIGINT NOT NULL REFERENCES provider_offers (id) ON DELETE CASCADE,
    service_id BIGINT NOT NULL REFERENCES services (id),
    PRIMARY KEY (offer_id, service_id)
);

CREATE TABLE saved_offers (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    offer_id   BIGINT      NOT NULL REFERENCES provider_offers (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_saved_offer UNIQUE (user_id, offer_id)
);

CREATE TABLE follows (
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    provider_id BIGINT      NOT NULL REFERENCES providers (id),
    created_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, provider_id)
);
CREATE INDEX ix_follows_provider ON follows (provider_id);

-- ============================================================ orders
CREATE TABLE orders (
    id                    BIGSERIAL PRIMARY KEY,
    customer_id           BIGINT       NOT NULL REFERENCES users (id),
    status                VARCHAR(30)  NOT NULL,
    car_type_id           BIGINT REFERENCES car_types (id),
    car_description       VARCHAR(200) NOT NULL,
    requested_date        DATE         NOT NULL,
    address               VARCHAR(500),
    latitude              NUMERIC(9, 6),
    longitude             NUMERIC(9, 6),
    comment               TEXT,
    selected_provider_id  BIGINT REFERENCES providers (id),
    accepted_provider_id  BIGINT REFERENCES providers (id),
    accepted_response_id  BIGINT,
    currency              VARCHAR(3)      NOT NULL,
    response_deadline     TIMESTAMPTZ  NOT NULL,
    accepted_at           TIMESTAMPTZ,
    started_at            TIMESTAMPTZ,
    provider_completed_at TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    canceled_at           TIMESTAMPTZ,
    expired_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_orders_status CHECK (status IN ('CREATED', 'MATCHING', 'QUOTING', 'RESPONSES_AVAILABLE', 'ACCEPTED',
                                                  'IN_PROGRESS', 'COMPLETED', 'CANCELED', 'EXPIRED')),
    CONSTRAINT ck_orders_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_orders_lng CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_orders_accepted_pair CHECK ((accepted_provider_id IS NULL) = (accepted_response_id IS NULL))
);
CREATE INDEX ix_orders_customer ON orders (customer_id, created_at DESC);
CREATE INDEX ix_orders_status_deadline ON orders (status, response_deadline);
CREATE INDEX ix_orders_accepted_provider ON orders (accepted_provider_id);

CREATE TABLE order_services (
    order_id   BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    service_id BIGINT NOT NULL REFERENCES services (id),
    PRIMARY KEY (order_id, service_id)
);

CREATE TABLE order_spare_parts (
    id       BIGSERIAL PRIMARY KEY,
    order_id BIGINT       NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    name     VARCHAR(150) NOT NULL
);
CREATE INDEX ix_order_spare_parts_order ON order_spare_parts (order_id);

CREATE TABLE order_attachments (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT       NOT NULL REFERENCES orders (id),
    uploaded_by  BIGINT       NOT NULL REFERENCES users (id),
    file_key     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_order_attachments_order ON order_attachments (order_id);

CREATE TABLE order_timeline (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT      NOT NULL REFERENCES orders (id),
    event         VARCHAR(50) NOT NULL,
    actor_user_id BIGINT REFERENCES users (id),
    actor_role    VARCHAR(20) NOT NULL,
    details       VARCHAR(1000),
    created_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_order_timeline_order ON order_timeline (order_id, created_at);

CREATE TABLE order_provider_targets (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT      NOT NULL REFERENCES orders (id),
    provider_id  BIGINT      NOT NULL REFERENCES providers (id),
    branch_id    BIGINT REFERENCES provider_branches (id),
    distance_km  NUMERIC(9, 2),
    rank_score   NUMERIC(9, 4),
    status       VARCHAR(20) NOT NULL,
    notified_at  TIMESTAMPTZ,
    viewed_at    TIMESTAMPTZ,
    responded_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_order_target UNIQUE (order_id, provider_id),
    CONSTRAINT ck_target_status CHECK (status IN ('TARGETED', 'VIEWED', 'RESPONDED', 'DECLINED', 'EXPIRED'))
);
CREATE INDEX ix_targets_provider_status ON order_provider_targets (provider_id, status);

CREATE TABLE order_cancellations (
    id                     BIGSERIAL PRIMARY KEY,
    order_id               BIGINT      NOT NULL UNIQUE REFERENCES orders (id),
    canceled_by            BIGINT REFERENCES users (id),
    actor_role             VARCHAR(20) NOT NULL,
    reason_id              BIGINT REFERENCES cancellation_reasons (id),
    reason_text            VARCHAR(1000),
    order_status_at_cancel VARCHAR(30) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL
);

CREATE TABLE order_policy_snapshots (
    id                         BIGSERIAL PRIMARY KEY,
    order_id                   BIGINT      NOT NULL REFERENCES orders (id),
    policy_type                VARCHAR(30) NOT NULL,
    source                     VARCHAR(20) NOT NULL, -- PLATFORM / PROVIDER
    provider_policy_version_id BIGINT REFERENCES provider_policy_versions (id),
    version_no                 INTEGER,
    terms                      TEXT        NOT NULL,
    period_days                INTEGER,
    fee_percent                NUMERIC(5, 2),
    free_cancellation_hours    INTEGER,
    created_at                 TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_order_policy_snapshot UNIQUE (order_id, policy_type)
);

-- ============================================================ responses (offers)
CREATE TABLE order_responses (
    id                         BIGSERIAL PRIMARY KEY,
    order_id                   BIGINT         NOT NULL REFERENCES orders (id),
    provider_id                BIGINT         NOT NULL REFERENCES providers (id),
    branch_id                  BIGINT REFERENCES provider_branches (id),
    labor_cost                 NUMERIC(12, 2) NOT NULL DEFAULT 0,
    parts_cost                 NUMERIC(12, 2) NOT NULL DEFAULT 0,
    discount                   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    price                      NUMERIC(12, 2) NOT NULL,
    currency                   VARCHAR(3)        NOT NULL,
    description                TEXT           NOT NULL,
    estimated_duration_minutes INTEGER,
    proposed_appointment_at    TIMESTAMPTZ,
    expires_at                 TIMESTAMPTZ    NOT NULL,
    status                     VARCHAR(20)    NOT NULL,
    status_changed_at          TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ    NOT NULL,
    updated_at                 TIMESTAMPTZ    NOT NULL,
    version                    BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_response_order_provider UNIQUE (order_id, provider_id),
    CONSTRAINT ck_response_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CANCELED')),
    CONSTRAINT ck_response_amounts CHECK (labor_cost >= 0 AND parts_cost >= 0 AND discount >= 0 AND price >= 0),
    CONSTRAINT ck_response_duration CHECK (estimated_duration_minutes IS NULL OR estimated_duration_minutes > 0)
);
-- ONE ORDER = MAXIMUM ONE ACCEPTED RESPONSE
CREATE UNIQUE INDEX uq_response_one_accepted_per_order ON order_responses (order_id) WHERE status = 'ACCEPTED';
CREATE INDEX ix_responses_pending_expiry ON order_responses (expires_at) WHERE status = 'PENDING';
CREATE INDEX ix_responses_provider ON order_responses (provider_id, created_at DESC);

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_accepted_response FOREIGN KEY (accepted_response_id) REFERENCES order_responses (id);

CREATE TABLE response_part_prices (
    id                  BIGSERIAL PRIMARY KEY,
    response_id         BIGINT         NOT NULL REFERENCES order_responses (id) ON DELETE CASCADE,
    order_spare_part_id BIGINT         NOT NULL REFERENCES order_spare_parts (id),
    price               NUMERIC(12, 2) NOT NULL,
    CONSTRAINT uq_response_part UNIQUE (response_id, order_spare_part_id),
    CONSTRAINT ck_response_part_price CHECK (price >= 0)
);

-- ============================================================ appointments
CREATE TABLE appointments (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT      NOT NULL UNIQUE REFERENCES orders (id),
    branch_id           BIGINT REFERENCES provider_branches (id),
    scheduled_start     TIMESTAMPTZ NOT NULL,
    scheduled_end       TIMESTAMPTZ NOT NULL,
    status              VARCHAR(30) NOT NULL,
    no_show_reported_by BIGINT REFERENCES users (id),
    no_show_reported_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_appointment_status CHECK (status IN ('SCHEDULED', 'RESCHEDULED', 'COMPLETED', 'CANCELED',
                                                       'CUSTOMER_NO_SHOW', 'PROVIDER_NO_SHOW')),
    CONSTRAINT ck_appointment_range CHECK (scheduled_end > scheduled_start)
);
CREATE INDEX ix_appointments_branch_time ON appointments (branch_id, scheduled_start);

CREATE TABLE appointment_changes (
    id             BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT      NOT NULL REFERENCES appointments (id),
    old_start      TIMESTAMPTZ,
    new_start      TIMESTAMPTZ,
    old_status     VARCHAR(30),
    new_status     VARCHAR(30) NOT NULL,
    changed_by     BIGINT REFERENCES users (id),
    reason         VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_appointment_changes_appointment ON appointment_changes (appointment_id, created_at);

-- ============================================================ payments
CREATE TABLE payments (
    id                 BIGSERIAL PRIMARY KEY,
    order_id           BIGINT         NOT NULL REFERENCES orders (id),
    customer_id        BIGINT         NOT NULL REFERENCES users (id),
    provider_id        BIGINT         NOT NULL REFERENCES providers (id),
    method             VARCHAR(20),
    status             VARCHAR(20)    NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL,
    currency           VARCHAR(3)        NOT NULL,
    amount_paid        NUMERIC(12, 2) NOT NULL DEFAULT 0,
    amount_refunded    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    payee_account_name VARCHAR(150),
    payee_account_identifier VARCHAR(150),
    method_selected_at TIMESTAMPTZ,
    submitted_at       TIMESTAMPTZ,
    confirmed_at       TIMESTAMPTZ,
    confirmed_by       BIGINT REFERENCES users (id),
    rejected_at        TIMESTAMPTZ,
    rejection_reason   VARCHAR(1000),
    disputed_at        TIMESTAMPTZ,
    closed_at          TIMESTAMPTZ,
    metadata           TEXT,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'PROOF_SUBMITTED', 'PROVIDER_CONFIRMED', 'REJECTED',
                                                   'DISPUTED', 'EXPIRED', 'CANCELED', 'REFUNDED')),
    CONSTRAINT ck_payment_method CHECK (method IS NULL OR method IN ('VODAFONE_CASH', 'INSTAPAY', 'CASH', 'CARD', 'WALLET', 'OTHER')),
    CONSTRAINT ck_payment_amounts CHECK (amount > 0 AND amount_paid >= 0 AND amount_refunded >= 0 AND amount_refunded <= amount_paid)
);
CREATE INDEX ix_payments_order ON payments (order_id);
CREATE INDEX ix_payments_provider_status ON payments (provider_id, status);
CREATE INDEX ix_payments_customer ON payments (customer_id, created_at DESC);
-- At most one active payment per order.
CREATE UNIQUE INDEX uq_payment_one_active_per_order ON payments (order_id)
    WHERE status IN ('PENDING', 'PROOF_SUBMITTED', 'DISPUTED');

CREATE TABLE payment_proofs (
    id                    BIGSERIAL PRIMARY KEY,
    payment_id            BIGINT       NOT NULL REFERENCES payments (id),
    file_key              VARCHAR(255) NOT NULL,
    content_type          VARCHAR(100) NOT NULL,
    transaction_reference VARCHAR(100),
    sender_identifier     VARCHAR(150),
    note                  VARCHAR(1000),
    uploaded_by           BIGINT       NOT NULL REFERENCES users (id),
    uploaded_at           TIMESTAMPTZ  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    reviewed_by           BIGINT REFERENCES users (id),
    reviewed_at           TIMESTAMPTZ,
    rejection_reason      VARCHAR(1000),
    CONSTRAINT ck_proof_status CHECK (status IN ('PENDING_REVIEW', 'ACCEPTED', 'REJECTED', 'SUPERSEDED'))
);
CREATE INDEX ix_payment_proofs_payment ON payment_proofs (payment_id, uploaded_at);
CREATE INDEX ix_payment_proofs_txref ON payment_proofs (transaction_reference) WHERE transaction_reference IS NOT NULL;

CREATE TABLE additional_charges (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT         NOT NULL REFERENCES orders (id),
    amount       NUMERIC(12, 2) NOT NULL,
    currency     VARCHAR(3)        NOT NULL,
    reason       VARCHAR(1000)  NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    requested_by BIGINT         NOT NULL REFERENCES users (id),
    decided_by   BIGINT REFERENCES users (id),
    decided_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_charge_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'CANCELED')),
    CONSTRAINT ck_charge_amount CHECK (amount > 0)
);
CREATE INDEX ix_additional_charges_order ON additional_charges (order_id);

-- ============================================================ disputes
CREATE TABLE disputes (
    id                 BIGSERIAL PRIMARY KEY,
    order_id           BIGINT        NOT NULL REFERENCES orders (id),
    payment_id         BIGINT REFERENCES payments (id),
    customer_id        BIGINT        NOT NULL REFERENCES users (id),
    provider_id        BIGINT        NOT NULL REFERENCES providers (id),
    opened_by          BIGINT        NOT NULL REFERENCES users (id),
    type               VARCHAR(30)   NOT NULL,
    status             VARCHAR(30)   NOT NULL,
    reason             VARCHAR(255)  NOT NULL,
    description        TEXT,
    resolution         TEXT,
    resolution_outcome VARCHAR(30),
    resolved_by        BIGINT REFERENCES users (id),
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    resolved_at        TIMESTAMPTZ,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_dispute_type CHECK (type IN ('ORDER_DISPUTE', 'PAYMENT_DISPUTE', 'SERVICE_DISPUTE', 'PRODUCT_DISPUTE',
                                               'REFUND_DISPUTE', 'WARRANTY_DISPUTE')),
    CONSTRAINT ck_dispute_status CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'WAITING_CUSTOMER', 'WAITING_PROVIDER',
                                                   'RESOLVED', 'REJECTED', 'CANCELED'))
);
CREATE INDEX ix_disputes_order ON disputes (order_id);
CREATE INDEX ix_disputes_status ON disputes (status, created_at);

CREATE TABLE dispute_evidence (
    id               BIGSERIAL PRIMARY KEY,
    dispute_id       BIGINT      NOT NULL REFERENCES disputes (id),
    submitted_by     BIGINT      NOT NULL REFERENCES users (id),
    kind             VARCHAR(30) NOT NULL,
    file_key         VARCHAR(255),
    content_type     VARCHAR(100),
    message_id       BIGINT,
    payment_proof_id BIGINT REFERENCES payment_proofs (id),
    note             TEXT,
    created_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_dispute_evidence_dispute ON dispute_evidence (dispute_id, created_at);

CREATE TABLE refunds (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      BIGINT         NOT NULL REFERENCES payments (id),
    order_id        BIGINT         NOT NULL REFERENCES orders (id),
    amount          NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(3)        NOT NULL,
    reason          VARCHAR(1000)  NOT NULL,
    source          VARCHAR(30)    NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    dispute_id      BIGINT REFERENCES disputes (id),
    requested_by    BIGINT REFERENCES users (id),
    approved_by     BIGINT REFERENCES users (id),
    decision_note   VARCHAR(1000),
    created_at      TIMESTAMPTZ    NOT NULL,
    approved_at     TIMESTAMPTZ,
    processed_at    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_refund_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_refund_amount CHECK (amount > 0)
);
CREATE INDEX ix_refunds_payment ON refunds (payment_id);
CREATE INDEX ix_refunds_order ON refunds (order_id);
CREATE INDEX ix_refunds_status ON refunds (status, created_at);

CREATE TABLE invoices (
    id                 BIGSERIAL PRIMARY KEY,
    order_id           BIGINT         NOT NULL UNIQUE REFERENCES orders (id),
    invoice_number     VARCHAR(40)    NOT NULL UNIQUE,
    provider_id        BIGINT         NOT NULL REFERENCES providers (id),
    customer_id        BIGINT         NOT NULL REFERENCES users (id),
    subtotal           NUMERIC(12, 2) NOT NULL,
    discount           NUMERIC(12, 2) NOT NULL,
    additional_charges NUMERIC(12, 2) NOT NULL,
    refunds            NUMERIC(12, 2) NOT NULL,
    total              NUMERIC(12, 2) NOT NULL,
    paid               NUMERIC(12, 2) NOT NULL,
    remaining          NUMERIC(12, 2) NOT NULL,
    platform_fee       NUMERIC(12, 2) NOT NULL,
    provider_net       NUMERIC(12, 2) NOT NULL,
    currency           VARCHAR(3)        NOT NULL,
    issued_at          TIMESTAMPTZ    NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL
);

-- ============================================================ warranty / returns
CREATE TABLE service_warranties (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT      NOT NULL UNIQUE REFERENCES orders (id),
    provider_id BIGINT      NOT NULL REFERENCES providers (id),
    customer_id BIGINT      NOT NULL REFERENCES users (id),
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    terms       TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_warranty_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'VOID')),
    CONSTRAINT ck_warranty_range CHECK (end_date >= start_date)
);

CREATE TABLE warranty_claims (
    id                BIGSERIAL PRIMARY KEY,
    warranty_id       BIGINT      NOT NULL REFERENCES service_warranties (id),
    description       TEXT        NOT NULL,
    status            VARCHAR(20) NOT NULL,
    provider_response TEXT,
    dispute_id        BIGINT REFERENCES disputes (id),
    created_by        BIGINT      NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ NOT NULL,
    decided_at        TIMESTAMPTZ,
    CONSTRAINT ck_warranty_claim_status CHECK (status IN ('SUBMITTED', 'ACCEPTED', 'REJECTED', 'RESOLVED', 'ESCALATED'))
);
CREATE INDEX ix_warranty_claims_warranty ON warranty_claims (warranty_id);

CREATE TABLE return_requests (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT      NOT NULL REFERENCES orders (id),
    requested_by  BIGINT      NOT NULL REFERENCES users (id),
    description   TEXT        NOT NULL,
    status        VARCHAR(20) NOT NULL,
    refund_id     BIGINT REFERENCES refunds (id),
    decided_by    BIGINT REFERENCES users (id),
    decision_note VARCHAR(1000),
    decided_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_return_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'RECEIVED', 'CLOSED'))
);
CREATE INDEX ix_return_requests_order ON return_requests (order_id);

-- ============================================================ ratings
CREATE TABLE ratings (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT      NOT NULL REFERENCES orders (id),
    customer_id BIGINT      NOT NULL REFERENCES users (id),
    provider_id BIGINT      NOT NULL REFERENCES providers (id),
    score       INTEGER    NOT NULL,
    comment     VARCHAR(2000),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_rating_order_customer UNIQUE (order_id, customer_id),
    CONSTRAINT ck_rating_score CHECK (score BETWEEN 1 AND 5)
);
CREATE INDEX ix_ratings_provider ON ratings (provider_id, created_at DESC);

-- ============================================================ chat / calls
CREATE TABLE conversations (
    id                   BIGSERIAL PRIMARY KEY,
    order_id             BIGINT      NOT NULL UNIQUE REFERENCES orders (id),
    customer_id          BIGINT      NOT NULL REFERENCES users (id),
    provider_id          BIGINT      NOT NULL REFERENCES providers (id),
    provider_user_id     BIGINT      NOT NULL REFERENCES users (id),
    last_message_at      TIMESTAMPTZ,
    last_message_preview VARCHAR(200),
    created_at           TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_conversations_customer ON conversations (customer_id, last_message_at DESC);
CREATE INDEX ix_conversations_provider_user ON conversations (provider_user_id, last_message_at DESC);

CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES conversations (id),
    sender_id       BIGINT REFERENCES users (id),
    type            VARCHAR(20) NOT NULL,
    text            TEXT,
    file_key        VARCHAR(255),
    content_type    VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL,
    read_at         TIMESTAMPTZ,
    CONSTRAINT ck_message_type CHECK (type IN ('TEXT', 'IMAGE', 'AUDIO', 'SYSTEM'))
);
CREATE INDEX ix_messages_conversation ON messages (conversation_id, created_at DESC);
ALTER TABLE dispute_evidence
    ADD CONSTRAINT fk_dispute_evidence_message FOREIGN KEY (message_id) REFERENCES messages (id);

CREATE TABLE calls (
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  BIGINT       NOT NULL REFERENCES conversations (id),
    caller_id        BIGINT       NOT NULL REFERENCES users (id),
    callee_id        BIGINT       NOT NULL REFERENCES users (id),
    call_type        VARCHAR(10)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    channel_name     VARCHAR(100) NOT NULL,
    started_at       TIMESTAMPTZ  NOT NULL,
    answered_at      TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,
    duration_seconds BIGINT,
    CONSTRAINT ck_call_type CHECK (call_type IN ('AUDIO', 'VIDEO')),
    CONSTRAINT ck_call_status CHECK (status IN ('RINGING', 'ONGOING', 'ENDED', 'REJECTED', 'MISSED'))
);
CREATE INDEX ix_calls_conversation ON calls (conversation_id, started_at DESC);

-- ============================================================ notifications
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    order_id   BIGINT REFERENCES orders (id),
    data       TEXT,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;

-- ============================================================ advertisements
CREATE TABLE advertisements (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    image_url       VARCHAR(500),
    target_url      VARCHAR(500),
    advertiser_name VARCHAR(150),
    status          VARCHAR(20)  NOT NULL,
    priority        INTEGER      NOT NULL DEFAULT 0,
    impressions     BIGINT       NOT NULL DEFAULT 0,
    clicks          BIGINT       NOT NULL DEFAULT 0,
    start_at        TIMESTAMPTZ,
    end_at          TIMESTAMPTZ,
    created_by      BIGINT REFERENCES users (id),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_ad_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED')),
    CONSTRAINT ck_ad_range CHECK (end_at IS NULL OR start_at IS NULL OR end_at > start_at)
);
CREATE INDEX ix_advertisements_status ON advertisements (status, priority DESC);

CREATE TABLE advertisement_daily_stats (
    advertisement_id BIGINT NOT NULL REFERENCES advertisements (id) ON DELETE CASCADE,
    day              DATE   NOT NULL,
    impressions      BIGINT NOT NULL DEFAULT 0,
    clicks           BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (advertisement_id, day)
);

-- ============================================================ audit / fraud
CREATE TABLE audit_logs (
    id            BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT,
    actor_role    VARCHAR(20)  NOT NULL,
    action        VARCHAR(50)  NOT NULL,
    entity_type   VARCHAR(50),
    entity_id     BIGINT,
    details       TEXT,
    ip_address    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_actor ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX ix_audit_action ON audit_logs (action, created_at DESC);

CREATE TABLE fraud_flags (
    id              BIGSERIAL PRIMARY KEY,
    subject_user_id BIGINT REFERENCES users (id),
    type            VARCHAR(50)   NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       BIGINT,
    details         VARCHAR(2000),
    status          VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    reviewed_by     BIGINT REFERENCES users (id),
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_fraud_status CHECK (status IN ('OPEN', 'REVIEWED', 'DISMISSED'))
);
CREATE INDEX ix_fraud_flags_status ON fraud_flags (status, created_at DESC);
CREATE INDEX ix_fraud_flags_subject ON fraud_flags (subject_user_id);
