-- WaDii V1 schema, MySQL / MariaDB flavour of db/migration/postgresql. See docs/DOMAIN_MODEL.md.
-- Money: NUMERIC(12,2). Instants: DATETIME(6) holding UTC (hibernate.jdbc.time_zone=UTC). History tables are append-only.
-- Partial indexes do not exist here: partial unique indexes become a unique index on a generated column that is
-- NULL outside the guarded state (NULLs never collide), the plain partial indexes become ordinary indexes.

-- ============================================================ catalog
CREATE TABLE countries (
    id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE provinces (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    country_id BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    CONSTRAINT uq_provinces_country_name UNIQUE (country_id, name),
    FOREIGN KEY (country_id) REFERENCES countries (id)
);

CREATE TABLE cities (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    province_id BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    CONSTRAINT uq_cities_province_name UNIQUE (province_id, name),
    FOREIGN KEY (province_id) REFERENCES provinces (id)
);

CREATE TABLE services (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE car_types (
    id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE common_spare_parts (
    id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL UNIQUE
);

CREATE TABLE cancellation_reasons (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    text       VARCHAR(255) NOT NULL,
    applies_to VARCHAR(20)  NOT NULL DEFAULT 'ANY', -- CUSTOMER / PROVIDER / ANY
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ============================================================ identity
CREATE TABLE users (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    first_name       VARCHAR(100),
    last_name        VARCHAR(100),
    phone            VARCHAR(30),
    image            VARCHAR(255),
    background_image VARCHAR(255),
    city_id          BIGINT,
    role             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    fcm_token        VARCHAR(512),
    token_version    INTEGER      NOT NULL DEFAULT 0,
    last_login_at    DATETIME(6),
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'PROVIDER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED')),
    FOREIGN KEY (city_id) REFERENCES cities (id)
);
-- The *_ci collation makes this unique index case-insensitive, like LOWER(email) on PostgreSQL.
CREATE UNIQUE INDEX uq_users_email_lower ON users (email);

CREATE TABLE user_admin_roles (
    user_id    BIGINT      NOT NULL,
    admin_role VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, admin_role),
    CONSTRAINT ck_admin_role CHECK (admin_role IN ('SUPER_ADMIN', 'SUPPORT_ADMIN', 'CONTENT_ADMIN', 'FINANCE_ADMIN')),
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- ============================================================ provider
CREATE TABLE providers (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL UNIQUE,
    name                VARCHAR(150) NOT NULL,
    description         TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    accepting_orders    BOOLEAN      NOT NULL DEFAULT TRUE,
    service_radius_km   NUMERIC(7, 2),
    rating_avg          NUMERIC(3, 2) NOT NULL DEFAULT 0,
    rating_count        INTEGER      NOT NULL DEFAULT 0,
    followers_count     INTEGER      NOT NULL DEFAULT 0,
    verified_at         DATETIME(6),
    verified_by         BIGINT,
    status_reason       VARCHAR(500),
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_providers_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED')),
    CONSTRAINT ck_providers_verification CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED')),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (verified_by) REFERENCES users (id)
);
CREATE INDEX ix_providers_eligibility ON providers (status, verification_status, accepting_orders);

CREATE TABLE provider_documents (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id  BIGINT       NOT NULL,
    type         VARCHAR(30)  NOT NULL,
    file_key     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    uploaded_at  DATETIME(6) NOT NULL,
    reviewed_by  BIGINT,
    reviewed_at  DATETIME(6),
    FOREIGN KEY (provider_id) REFERENCES providers (id),
    FOREIGN KEY (reviewed_by) REFERENCES users (id)
);
CREATE INDEX ix_provider_documents_provider ON provider_documents (provider_id);

CREATE TABLE provider_branches (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id BIGINT       NOT NULL,
    name        VARCHAR(150) NOT NULL,
    address     VARCHAR(500),
    latitude    NUMERIC(9, 6),
    longitude   NUMERIC(9, 6),
    phone       VARCHAR(30),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT ck_branch_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_branch_lng CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    FOREIGN KEY (provider_id) REFERENCES providers (id)
);
CREATE INDEX ix_provider_branches_provider ON provider_branches (provider_id);

CREATE TABLE branch_working_hours (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    branch_id   BIGINT   NOT NULL,
    day_of_week INTEGER NOT NULL,
    opens_at    TIME     NOT NULL,
    closes_at   TIME     NOT NULL,
    CONSTRAINT uq_working_hours_branch_day UNIQUE (branch_id, day_of_week),
    CONSTRAINT ck_working_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_working_hours_range CHECK (closes_at > opens_at),
    FOREIGN KEY (branch_id) REFERENCES provider_branches (id) ON DELETE CASCADE
);

CREATE TABLE provider_services (
    provider_id BIGINT NOT NULL,
    service_id  BIGINT NOT NULL,
    PRIMARY KEY (provider_id, service_id),
    FOREIGN KEY (provider_id) REFERENCES providers (id) ON DELETE CASCADE,
    FOREIGN KEY (service_id) REFERENCES services (id)
);
CREATE INDEX ix_provider_services_service ON provider_services (service_id);

CREATE TABLE provider_links (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id BIGINT       NOT NULL,
    url         VARCHAR(500) NOT NULL,
    FOREIGN KEY (provider_id) REFERENCES providers (id) ON DELETE CASCADE
);

CREATE TABLE provider_payment_accounts (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id        BIGINT       NOT NULL,
    method             VARCHAR(20)  NOT NULL,
    account_name       VARCHAR(150) NOT NULL,
    account_identifier VARCHAR(150) NOT NULL,
    instructions       VARCHAR(500),
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    CONSTRAINT uq_payment_account_provider_method UNIQUE (provider_id, method),
    FOREIGN KEY (provider_id) REFERENCES providers (id)
);

CREATE TABLE provider_policies (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id        BIGINT      NOT NULL,
    policy_type        VARCHAR(30) NOT NULL,
    current_version_id BIGINT,
    created_at         DATETIME(6)NOT NULL,
    CONSTRAINT uq_provider_policy_type UNIQUE (provider_id, policy_type),
    FOREIGN KEY (provider_id) REFERENCES providers (id)
);

CREATE TABLE provider_policy_versions (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    policy_id               BIGINT      NOT NULL,
    version_no              INTEGER     NOT NULL,
    terms                   TEXT        NOT NULL,
    period_days             INTEGER,
    fee_percent             NUMERIC(5, 2),
    free_cancellation_hours INTEGER,
    created_by              BIGINT      NOT NULL,
    created_at              DATETIME(6)NOT NULL,
    CONSTRAINT uq_policy_version UNIQUE (policy_id, version_no),
    CONSTRAINT ck_policy_period CHECK (period_days IS NULL OR period_days >= 0),
    CONSTRAINT ck_policy_fee CHECK (fee_percent IS NULL OR fee_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_policy_free_hours CHECK (free_cancellation_hours IS NULL OR free_cancellation_hours >= 0),
    FOREIGN KEY (policy_id) REFERENCES provider_policies (id),
    FOREIGN KEY (created_by) REFERENCES users (id)
);
ALTER TABLE provider_policies
    ADD CONSTRAINT fk_provider_policy_current_version FOREIGN KEY (current_version_id) REFERENCES provider_policy_versions (id);

CREATE TABLE provider_stats (
    provider_id          BIGINT PRIMARY KEY,
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
    updated_at           DATETIME(6)  NOT NULL,
    FOREIGN KEY (provider_id) REFERENCES providers (id)
);

CREATE TABLE customer_stats (
    user_id         BIGINT PRIMARY KEY,
    orders_count    INTEGER     NOT NULL DEFAULT 0,
    completed_count INTEGER     NOT NULL DEFAULT 0,
    canceled_count  INTEGER     NOT NULL DEFAULT 0,
    no_show_count   INTEGER     NOT NULL DEFAULT 0,
    dispute_count   INTEGER     NOT NULL DEFAULT 0,
    updated_at      DATETIME(6)NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE provider_offers (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id BIGINT       NOT NULL,
    title       VARCHAR(200) NOT NULL,
    description TEXT         NOT NULL,
    end_date    DATE         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    FOREIGN KEY (provider_id) REFERENCES providers (id)
);
CREATE INDEX ix_provider_offers_provider ON provider_offers (provider_id);

CREATE TABLE provider_offer_services (
    offer_id   BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    PRIMARY KEY (offer_id, service_id),
    FOREIGN KEY (offer_id) REFERENCES provider_offers (id) ON DELETE CASCADE,
    FOREIGN KEY (service_id) REFERENCES services (id)
);

CREATE TABLE saved_offers (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    offer_id   BIGINT      NOT NULL,
    created_at DATETIME(6)NOT NULL,
    CONSTRAINT uq_saved_offer UNIQUE (user_id, offer_id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (offer_id) REFERENCES provider_offers (id) ON DELETE CASCADE
);

CREATE TABLE follows (
    user_id     BIGINT      NOT NULL,
    provider_id BIGINT      NOT NULL,
    created_at  DATETIME(6)NOT NULL,
    PRIMARY KEY (user_id, provider_id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (provider_id) REFERENCES providers (id)
);
CREATE INDEX ix_follows_provider ON follows (provider_id);

-- ============================================================ orders
CREATE TABLE orders (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id           BIGINT       NOT NULL,
    status                VARCHAR(30)  NOT NULL,
    car_type_id           BIGINT,
    car_description       VARCHAR(200) NOT NULL,
    requested_date        DATE         NOT NULL,
    address               VARCHAR(500),
    latitude              NUMERIC(9, 6),
    longitude             NUMERIC(9, 6),
    comment               TEXT,
    selected_provider_id  BIGINT,
    accepted_provider_id  BIGINT,
    accepted_response_id  BIGINT,
    currency              VARCHAR(3)      NOT NULL,
    response_deadline     DATETIME(6) NOT NULL,
    accepted_at           DATETIME(6),
    started_at            DATETIME(6),
    provider_completed_at DATETIME(6),
    completed_at          DATETIME(6),
    canceled_at           DATETIME(6),
    expired_at            DATETIME(6),
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_orders_status CHECK (status IN ('CREATED', 'MATCHING', 'QUOTING', 'RESPONSES_AVAILABLE', 'ACCEPTED',
                                                  'IN_PROGRESS', 'COMPLETED', 'CANCELED', 'EXPIRED')),
    CONSTRAINT ck_orders_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_orders_lng CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_orders_accepted_pair CHECK ((accepted_provider_id IS NULL) = (accepted_response_id IS NULL)),
    FOREIGN KEY (customer_id) REFERENCES users (id),
    FOREIGN KEY (car_type_id) REFERENCES car_types (id),
    FOREIGN KEY (selected_provider_id) REFERENCES providers (id),
    FOREIGN KEY (accepted_provider_id) REFERENCES providers (id)
);
CREATE INDEX ix_orders_customer ON orders (customer_id, created_at DESC);
CREATE INDEX ix_orders_status_deadline ON orders (status, response_deadline);
CREATE INDEX ix_orders_accepted_provider ON orders (accepted_provider_id);

CREATE TABLE order_services (
    order_id   BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    PRIMARY KEY (order_id, service_id),
    FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    FOREIGN KEY (service_id) REFERENCES services (id)
);

CREATE TABLE order_spare_parts (
    id       BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT       NOT NULL,
    name     VARCHAR(150) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);
CREATE INDEX ix_order_spare_parts_order ON order_spare_parts (order_id);

CREATE TABLE order_attachments (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    uploaded_by  BIGINT       NOT NULL,
    file_key     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (uploaded_by) REFERENCES users (id)
);
CREATE INDEX ix_order_attachments_order ON order_attachments (order_id);

CREATE TABLE order_timeline (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id      BIGINT      NOT NULL,
    event         VARCHAR(50) NOT NULL,
    actor_user_id BIGINT,
    actor_role    VARCHAR(20) NOT NULL,
    details       VARCHAR(1000),
    created_at    DATETIME(6)NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (actor_user_id) REFERENCES users (id)
);
CREATE INDEX ix_order_timeline_order ON order_timeline (order_id, created_at);

CREATE TABLE order_provider_targets (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id     BIGINT      NOT NULL,
    provider_id  BIGINT      NOT NULL,
    branch_id    BIGINT,
    distance_km  NUMERIC(9, 2),
    rank_score   NUMERIC(9, 4),
    status       VARCHAR(20) NOT NULL,
    notified_at  DATETIME(6),
    viewed_at    DATETIME(6),
    responded_at DATETIME(6),
    created_at   DATETIME(6)NOT NULL,
    updated_at   DATETIME(6)NOT NULL,
    CONSTRAINT uq_order_target UNIQUE (order_id, provider_id),
    CONSTRAINT ck_target_status CHECK (status IN ('TARGETED', 'VIEWED', 'RESPONDED', 'DECLINED', 'EXPIRED')),
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (provider_id) REFERENCES providers (id),
    FOREIGN KEY (branch_id) REFERENCES provider_branches (id)
);
CREATE INDEX ix_targets_provider_status ON order_provider_targets (provider_id, status);

CREATE TABLE order_cancellations (
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id               BIGINT      NOT NULL UNIQUE,
    canceled_by            BIGINT,
    actor_role             VARCHAR(20) NOT NULL,
    reason_id              BIGINT,
    reason_text            VARCHAR(1000),
    order_status_at_cancel VARCHAR(30) NOT NULL,
    created_at             DATETIME(6)NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (canceled_by) REFERENCES users (id),
    FOREIGN KEY (reason_id) REFERENCES cancellation_reasons (id)
);

CREATE TABLE order_policy_snapshots (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id                   BIGINT      NOT NULL,
    policy_type                VARCHAR(30) NOT NULL,
    source                     VARCHAR(20) NOT NULL, -- PLATFORM / PROVIDER
    provider_policy_version_id BIGINT,
    version_no                 INTEGER,
    terms                      TEXT        NOT NULL,
    period_days                INTEGER,
    fee_percent                NUMERIC(5, 2),
    free_cancellation_hours    INTEGER,
    created_at                 DATETIME(6)NOT NULL,
    CONSTRAINT uq_order_policy_snapshot UNIQUE (order_id, policy_type),
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (provider_policy_version_id) REFERENCES provider_policy_versions (id)
);

-- ============================================================ responses (offers)
CREATE TABLE order_responses (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id                   BIGINT         NOT NULL,
    provider_id                BIGINT         NOT NULL,
    branch_id                  BIGINT,
    labor_cost                 NUMERIC(12, 2) NOT NULL DEFAULT 0,
    parts_cost                 NUMERIC(12, 2) NOT NULL DEFAULT 0,
    discount                   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    price                      NUMERIC(12, 2) NOT NULL,
    currency                   VARCHAR(3)        NOT NULL,
    description                TEXT           NOT NULL,
    estimated_duration_minutes INTEGER,
    proposed_appointment_at    DATETIME(6),
    expires_at                 DATETIME(6)   NOT NULL,
    status                     VARCHAR(20)    NOT NULL,
    status_changed_at          DATETIME(6),
    created_at                 DATETIME(6)   NOT NULL,
    updated_at                 DATETIME(6)   NOT NULL,
    version                    BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_response_order_provider UNIQUE (order_id, provider_id),
    CONSTRAINT ck_response_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CANCELED')),
    CONSTRAINT ck_response_amounts CHECK (labor_cost >= 0 AND parts_cost >= 0 AND discount >= 0 AND price >= 0),
    CONSTRAINT ck_response_duration CHECK (estimated_duration_minutes IS NULL OR estimated_duration_minutes > 0),
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (provider_id) REFERENCES providers (id),
    FOREIGN KEY (branch_id) REFERENCES provider_branches (id)
);
-- ONE ORDER = MAXIMUM ONE ACCEPTED RESPONSE
ALTER TABLE order_responses
    ADD COLUMN accepted_order_id BIGINT AS (CASE WHEN status = 'ACCEPTED' THEN order_id END) PERSISTENT;
CREATE UNIQUE INDEX uq_response_one_accepted_per_order ON order_responses (accepted_order_id);
CREATE INDEX ix_responses_pending_expiry ON order_responses (status, expires_at);
CREATE INDEX ix_responses_provider ON order_responses (provider_id, created_at DESC);

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_accepted_response FOREIGN KEY (accepted_response_id) REFERENCES order_responses (id);

CREATE TABLE response_part_prices (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    response_id         BIGINT         NOT NULL,
    order_spare_part_id BIGINT         NOT NULL,
    price               NUMERIC(12, 2) NOT NULL,
    CONSTRAINT uq_response_part UNIQUE (response_id, order_spare_part_id),
    CONSTRAINT ck_response_part_price CHECK (price >= 0),
    FOREIGN KEY (response_id) REFERENCES order_responses (id) ON DELETE CASCADE,
    FOREIGN KEY (order_spare_part_id) REFERENCES order_spare_parts (id)
);

-- ============================================================ appointments
CREATE TABLE appointments (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id            BIGINT      NOT NULL UNIQUE,
    branch_id           BIGINT,
    scheduled_start     DATETIME(6)NOT NULL,
    scheduled_end       DATETIME(6)NOT NULL,
    status              VARCHAR(30) NOT NULL,
    no_show_reported_by BIGINT,
    no_show_reported_at DATETIME(6),
    created_at          DATETIME(6)NOT NULL,
    updated_at          DATETIME(6)NOT NULL,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_appointment_status CHECK (status IN ('SCHEDULED', 'RESCHEDULED', 'COMPLETED', 'CANCELED',
                                                       'CUSTOMER_NO_SHOW', 'PROVIDER_NO_SHOW')),
    CONSTRAINT ck_appointment_range CHECK (scheduled_end > scheduled_start),
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (branch_id) REFERENCES provider_branches (id),
    FOREIGN KEY (no_show_reported_by) REFERENCES users (id)
);
CREATE INDEX ix_appointments_branch_time ON appointments (branch_id, scheduled_start);

CREATE TABLE appointment_changes (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    appointment_id BIGINT      NOT NULL,
    old_start      DATETIME(6),
    new_start      DATETIME(6),
    old_status     VARCHAR(30),
    new_status     VARCHAR(30) NOT NULL,
    changed_by     BIGINT,
    reason         VARCHAR(1000),
    created_at     DATETIME(6)NOT NULL,
    FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    FOREIGN KEY (changed_by) REFERENCES users (id)
);
CREATE INDEX ix_appointment_changes_appointment ON appointment_changes (appointment_id, created_at);

-- ============================================================ payments
CREATE TABLE payments (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id           BIGINT         NOT NULL,
    customer_id        BIGINT         NOT NULL,
    provider_id        BIGINT         NOT NULL,
    method             VARCHAR(20),
    status             VARCHAR(20)    NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL,
    currency           VARCHAR(3)        NOT NULL,
    amount_paid        NUMERIC(12, 2) NOT NULL DEFAULT 0,
    amount_refunded    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    payee_account_name VARCHAR(150),
    payee_account_identifier VARCHAR(150),
    method_selected_at DATETIME(6),
    submitted_at       DATETIME(6),
    confirmed_at       DATETIME(6),
    confirmed_by       BIGINT,
    rejected_at        DATETIME(6),
    rejection_reason   VARCHAR(1000),
    disputed_at        DATETIME(6),
    closed_at          DATETIME(6),
    metadata           TEXT,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'PROOF_SUBMITTED', 'PROVIDER_CONFIRMED', 'REJECTED',
                                                   'DISPUTED', 'EXPIRED', 'CANCELED', 'REFUNDED')),
    CONSTRAINT ck_payment_method CHECK (method IS NULL OR method IN ('VODAFONE_CASH', 'INSTAPAY', 'CASH', 'CARD', 'WALLET', 'OTHER')),
    CONSTRAINT ck_payment_amounts CHECK (amount > 0 AND amount_paid >= 0 AND amount_refunded >= 0 AND amount_refunded <= amount_paid),
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (customer_id) REFERENCES users (id),
    FOREIGN KEY (provider_id) REFERENCES providers (id),
    FOREIGN KEY (confirmed_by) REFERENCES users (id)
);
CREATE INDEX ix_payments_order ON payments (order_id);
CREATE INDEX ix_payments_provider_status ON payments (provider_id, status);
CREATE INDEX ix_payments_customer ON payments (customer_id, created_at DESC);
-- At most one active payment per order.
ALTER TABLE payments
    ADD COLUMN active_order_id BIGINT AS (CASE WHEN status IN ('PENDING', 'PROOF_SUBMITTED', 'DISPUTED') THEN order_id END) PERSISTENT;
CREATE UNIQUE INDEX uq_payment_one_active_per_order ON payments (active_order_id);

CREATE TABLE payment_proofs (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_id            BIGINT       NOT NULL,
    file_key              VARCHAR(255) NOT NULL,
    content_type          VARCHAR(100) NOT NULL,
    transaction_reference VARCHAR(100),
    sender_identifier     VARCHAR(150),
    note                  VARCHAR(1000),
    uploaded_by           BIGINT       NOT NULL,
    uploaded_at           DATETIME(6) NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    reviewed_by           BIGINT,
    reviewed_at           DATETIME(6),
    rejection_reason      VARCHAR(1000),
    CONSTRAINT ck_proof_status CHECK (status IN ('PENDING_REVIEW', 'ACCEPTED', 'REJECTED', 'SUPERSEDED')),
    FOREIGN KEY (payment_id) REFERENCES payments (id),
    FOREIGN KEY (uploaded_by) REFERENCES users (id),
    FOREIGN KEY (reviewed_by) REFERENCES users (id)
);
CREATE INDEX ix_payment_proofs_payment ON payment_proofs (payment_id, uploaded_at);
CREATE INDEX ix_payment_proofs_txref ON payment_proofs (transaction_reference);

CREATE TABLE additional_charges (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id     BIGINT         NOT NULL,
    amount       NUMERIC(12, 2) NOT NULL,
    currency     VARCHAR(3)        NOT NULL,
    reason       VARCHAR(1000)  NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    requested_by BIGINT         NOT NULL,
    decided_by   BIGINT,
    decided_at   DATETIME(6),
    created_at   DATETIME(6)   NOT NULL,
    CONSTRAINT ck_charge_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'CANCELED')),
    CONSTRAINT ck_charge_amount CHECK (amount > 0),
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (requested_by) REFERENCES users (id),
    FOREIGN KEY (decided_by) REFERENCES users (id)
);
CREATE INDEX ix_additional_charges_order ON additional_charges (order_id);

-- ============================================================ disputes
CREATE TABLE disputes (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id           BIGINT        NOT NULL,
    payment_id         BIGINT,
    customer_id        BIGINT        NOT NULL,
    provider_id        BIGINT        NOT NULL,
    opened_by          BIGINT        NOT NULL,
    type               VARCHAR(30)   NOT NULL,
    status             VARCHAR(30)   NOT NULL,
    reason             VARCHAR(255)  NOT NULL,
    description        TEXT,
    resolution         TEXT,
    resolution_outcome VARCHAR(30),
    resolved_by        BIGINT,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    resolved_at        DATETIME(6),
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_dispute_type CHECK (type IN ('ORDER_DISPUTE', 'PAYMENT_DISPUTE', 'SERVICE_DISPUTE', 'PRODUCT_DISPUTE',
                                               'REFUND_DISPUTE', 'WARRANTY_DISPUTE')),
    CONSTRAINT ck_dispute_status CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'WAITING_CUSTOMER', 'WAITING_PROVIDER',
                                                   'RESOLVED', 'REJECTED', 'CANCELED')),
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (payment_id) REFERENCES payments (id),
    FOREIGN KEY (customer_id) REFERENCES users (id),
    FOREIGN KEY (provider_id) REFERENCES providers (id),
    FOREIGN KEY (opened_by) REFERENCES users (id),
    FOREIGN KEY (resolved_by) REFERENCES users (id)
);
CREATE INDEX ix_disputes_order ON disputes (order_id);
CREATE INDEX ix_disputes_status ON disputes (status, created_at);

CREATE TABLE dispute_evidence (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    dispute_id       BIGINT      NOT NULL,
    submitted_by     BIGINT      NOT NULL,
    kind             VARCHAR(30) NOT NULL,
    file_key         VARCHAR(255),
    content_type     VARCHAR(100),
    message_id       BIGINT,
    payment_proof_id BIGINT,
    note             TEXT,
    created_at       DATETIME(6)NOT NULL,
    FOREIGN KEY (dispute_id) REFERENCES disputes (id),
    FOREIGN KEY (submitted_by) REFERENCES users (id),
    FOREIGN KEY (payment_proof_id) REFERENCES payment_proofs (id)
);
CREATE INDEX ix_dispute_evidence_dispute ON dispute_evidence (dispute_id, created_at);

CREATE TABLE refunds (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_id      BIGINT         NOT NULL,
    order_id        BIGINT         NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(3)        NOT NULL,
    reason          VARCHAR(1000)  NOT NULL,
    source          VARCHAR(30)    NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    dispute_id      BIGINT,
    requested_by    BIGINT,
    approved_by     BIGINT,
    decision_note   VARCHAR(1000),
    created_at      DATETIME(6)   NOT NULL,
    approved_at     DATETIME(6),
    processed_at    DATETIME(6),
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT ck_refund_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_refund_amount CHECK (amount > 0),
    FOREIGN KEY (payment_id) REFERENCES payments (id),
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (dispute_id) REFERENCES disputes (id),
    FOREIGN KEY (requested_by) REFERENCES users (id),
    FOREIGN KEY (approved_by) REFERENCES users (id)
);
CREATE INDEX ix_refunds_payment ON refunds (payment_id);
CREATE INDEX ix_refunds_order ON refunds (order_id);
CREATE INDEX ix_refunds_status ON refunds (status, created_at);

CREATE TABLE invoices (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id           BIGINT         NOT NULL UNIQUE,
    invoice_number     VARCHAR(40)    NOT NULL UNIQUE,
    provider_id        BIGINT         NOT NULL,
    customer_id        BIGINT         NOT NULL,
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
    issued_at          DATETIME(6)   NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (provider_id) REFERENCES providers (id),
    FOREIGN KEY (customer_id) REFERENCES users (id)
);

-- ============================================================ warranty / returns
CREATE TABLE service_warranties (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id    BIGINT      NOT NULL UNIQUE,
    provider_id BIGINT      NOT NULL,
    customer_id BIGINT      NOT NULL,
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    terms       TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  DATETIME(6)NOT NULL,
    CONSTRAINT ck_warranty_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'VOID')),
    CONSTRAINT ck_warranty_range CHECK (end_date >= start_date),
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (provider_id) REFERENCES providers (id),
    FOREIGN KEY (customer_id) REFERENCES users (id)
);

CREATE TABLE warranty_claims (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    warranty_id       BIGINT      NOT NULL,
    description       TEXT        NOT NULL,
    status            VARCHAR(20) NOT NULL,
    provider_response TEXT,
    dispute_id        BIGINT,
    created_by        BIGINT      NOT NULL,
    created_at        DATETIME(6)NOT NULL,
    decided_at        DATETIME(6),
    CONSTRAINT ck_warranty_claim_status CHECK (status IN ('SUBMITTED', 'ACCEPTED', 'REJECTED', 'RESOLVED', 'ESCALATED')),
    FOREIGN KEY (warranty_id) REFERENCES service_warranties (id),
    FOREIGN KEY (dispute_id) REFERENCES disputes (id),
    FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_warranty_claims_warranty ON warranty_claims (warranty_id);

CREATE TABLE return_requests (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id      BIGINT      NOT NULL,
    requested_by  BIGINT      NOT NULL,
    description   TEXT        NOT NULL,
    status        VARCHAR(20) NOT NULL,
    refund_id     BIGINT,
    decided_by    BIGINT,
    decision_note VARCHAR(1000),
    decided_at    DATETIME(6),
    created_at    DATETIME(6)NOT NULL,
    CONSTRAINT ck_return_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'RECEIVED', 'CLOSED')),
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (requested_by) REFERENCES users (id),
    FOREIGN KEY (refund_id) REFERENCES refunds (id),
    FOREIGN KEY (decided_by) REFERENCES users (id)
);
CREATE INDEX ix_return_requests_order ON return_requests (order_id);

-- ============================================================ ratings
CREATE TABLE ratings (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id    BIGINT      NOT NULL,
    customer_id BIGINT      NOT NULL,
    provider_id BIGINT      NOT NULL,
    score       INTEGER    NOT NULL,
    comment     VARCHAR(2000),
    created_at  DATETIME(6)NOT NULL,
    updated_at  DATETIME(6)NOT NULL,
    CONSTRAINT uq_rating_order_customer UNIQUE (order_id, customer_id),
    CONSTRAINT ck_rating_score CHECK (score BETWEEN 1 AND 5),
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (customer_id) REFERENCES users (id),
    FOREIGN KEY (provider_id) REFERENCES providers (id)
);
CREATE INDEX ix_ratings_provider ON ratings (provider_id, created_at DESC);

-- ============================================================ chat / calls
CREATE TABLE conversations (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id             BIGINT      NOT NULL UNIQUE,
    customer_id          BIGINT      NOT NULL,
    provider_id          BIGINT      NOT NULL,
    provider_user_id     BIGINT      NOT NULL,
    last_message_at      DATETIME(6),
    last_message_preview VARCHAR(200),
    created_at           DATETIME(6)NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders (id),
    FOREIGN KEY (customer_id) REFERENCES users (id),
    FOREIGN KEY (provider_id) REFERENCES providers (id),
    FOREIGN KEY (provider_user_id) REFERENCES users (id)
);
CREATE INDEX ix_conversations_customer ON conversations (customer_id, last_message_at DESC);
CREATE INDEX ix_conversations_provider_user ON conversations (provider_user_id, last_message_at DESC);

CREATE TABLE messages (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    sender_id       BIGINT,
    type            VARCHAR(20) NOT NULL,
    text            TEXT,
    file_key        VARCHAR(255),
    content_type    VARCHAR(100),
    created_at      DATETIME(6)NOT NULL,
    read_at         DATETIME(6),
    CONSTRAINT ck_message_type CHECK (type IN ('TEXT', 'IMAGE', 'AUDIO', 'SYSTEM')),
    FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    FOREIGN KEY (sender_id) REFERENCES users (id)
);
CREATE INDEX ix_messages_conversation ON messages (conversation_id, created_at DESC);
ALTER TABLE dispute_evidence
    ADD CONSTRAINT fk_dispute_evidence_message FOREIGN KEY (message_id) REFERENCES messages (id);

CREATE TABLE calls (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id  BIGINT       NOT NULL,
    caller_id        BIGINT       NOT NULL,
    callee_id        BIGINT       NOT NULL,
    call_type        VARCHAR(10)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    channel_name     VARCHAR(100) NOT NULL,
    started_at       DATETIME(6) NOT NULL,
    answered_at      DATETIME(6),
    ended_at         DATETIME(6),
    duration_seconds BIGINT,
    CONSTRAINT ck_call_type CHECK (call_type IN ('AUDIO', 'VIDEO')),
    CONSTRAINT ck_call_status CHECK (status IN ('RINGING', 'ONGOING', 'ENDED', 'REJECTED', 'MISSED')),
    FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    FOREIGN KEY (caller_id) REFERENCES users (id),
    FOREIGN KEY (callee_id) REFERENCES users (id)
);
CREATE INDEX ix_calls_conversation ON calls (conversation_id, started_at DESC);

-- ============================================================ notifications
CREATE TABLE notifications (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    order_id   BIGINT,
    data       TEXT,
    read_at    DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (order_id) REFERENCES orders (id)
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id, read_at);

-- ============================================================ advertisements
CREATE TABLE advertisements (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    image_url       VARCHAR(500),
    target_url      VARCHAR(500),
    advertiser_name VARCHAR(150),
    status          VARCHAR(20)  NOT NULL,
    priority        INTEGER      NOT NULL DEFAULT 0,
    impressions     BIGINT       NOT NULL DEFAULT 0,
    clicks          BIGINT       NOT NULL DEFAULT 0,
    start_at        DATETIME(6),
    end_at          DATETIME(6),
    created_by      BIGINT,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    CONSTRAINT ck_ad_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED')),
    CONSTRAINT ck_ad_range CHECK (end_at IS NULL OR start_at IS NULL OR end_at > start_at),
    FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_advertisements_status ON advertisements (status, priority DESC);

CREATE TABLE advertisement_daily_stats (
    advertisement_id BIGINT NOT NULL,
    day              DATE   NOT NULL,
    impressions      BIGINT NOT NULL DEFAULT 0,
    clicks           BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (advertisement_id, day),
    FOREIGN KEY (advertisement_id) REFERENCES advertisements (id) ON DELETE CASCADE
);

-- ============================================================ audit / fraud
CREATE TABLE audit_logs (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT,
    actor_role    VARCHAR(20)  NOT NULL,
    action        VARCHAR(50)  NOT NULL,
    entity_type   VARCHAR(50),
    entity_id     BIGINT,
    details       TEXT,
    ip_address    VARCHAR(64),
    created_at    DATETIME(6) NOT NULL
);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_actor ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX ix_audit_action ON audit_logs (action, created_at DESC);

CREATE TABLE fraud_flags (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    subject_user_id BIGINT,
    type            VARCHAR(50)   NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       BIGINT,
    details         VARCHAR(2000),
    status          VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    reviewed_by     BIGINT,
    reviewed_at     DATETIME(6),
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT ck_fraud_status CHECK (status IN ('OPEN', 'REVIEWED', 'DISMISSED')),
    FOREIGN KEY (subject_user_id) REFERENCES users (id),
    FOREIGN KEY (reviewed_by) REFERENCES users (id)
);
CREATE INDEX ix_fraud_flags_status ON fraud_flags (status, created_at DESC);
CREATE INDEX ix_fraud_flags_subject ON fraud_flags (subject_user_id);
