-- WaDii V1 schema (cash-only marketplace). See docs/BUSINESS_RULES_V1.md.
-- Money: NUMERIC(12,2) EGP. Instants: DATETIME(6) (UTC). Local dates/times are Africa/Cairo.
-- MariaDB copy of db/migration/postgresql (BIGSERIAL -> AUTO_INCREMENT, TIMESTAMPTZ -> DATETIME(6) holding UTC).

-- ============================================================ reference data
CREATE TABLE areas (
    id      BIGINT PRIMARY KEY AUTO_INCREMENT,
    name_en VARCHAR(80) NOT NULL UNIQUE,
    name_ar VARCHAR(80) NOT NULL
);

CREATE TABLE service_categories (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    code             VARCHAR(30)  NOT NULL UNIQUE,
    name_en          VARCHAR(80)  NOT NULL,
    name_ar          VARCHAR(80)  NOT NULL,
    vehicle_required BOOLEAN      NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL
);

-- Versioned admin configuration. The row in force is the newest one with effective_from <= now.
CREATE TABLE config_versions (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key     VARCHAR(30)  NOT NULL,
    payload        TEXT         NOT NULL,
    effective_from DATETIME(6)  NOT NULL,
    created_by     BIGINT,
    reason         VARCHAR(500) NOT NULL,
    created_at     DATETIME(6)  NOT NULL
);
CREATE INDEX ix_config_versions_key ON config_versions (config_key, effective_from);

-- ============================================================ identity
CREATE TABLE users (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name         VARCHAR(120) NOT NULL,
    email             VARCHAR(255) NOT NULL UNIQUE,
    phone             VARCHAR(20)  NOT NULL UNIQUE,
    password_hash     VARCHAR(100) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    is_admin          BOOLEAN      NOT NULL DEFAULT FALSE,
    admin_roles       VARCHAR(200),
    token_version     INT          NOT NULL DEFAULT 0,
    fcm_token         VARCHAR(512),
    status_reason     VARCHAR(500),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE stored_files (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_key     VARCHAR(64)  NOT NULL UNIQUE,
    owner_id     BIGINT       NOT NULL,
    purpose      VARCHAR(30)  NOT NULL,
    content_type VARCHAR(60)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    public_file  BOOLEAN      NOT NULL,
    ref_type     VARCHAR(30),
    ref_id       BIGINT,
    created_at   DATETIME(6)  NOT NULL,
    CONSTRAINT fk_files_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE vehicles (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    make         VARCHAR(60)  NOT NULL,
    model        VARCHAR(60)  NOT NULL,
    vehicle_year INT          NOT NULL,
    archived     BOOLEAN      NOT NULL DEFAULT FALSE,
    last_used_at DATETIME(6),
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT fk_vehicles_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX ix_vehicles_user ON vehicles (user_id);

-- ============================================================ providers
CREATE TABLE providers (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL UNIQUE,
    business_name      VARCHAR(120) NOT NULL,
    description        TEXT,
    phone              VARCHAR(20)  NOT NULL,
    area_id            BIGINT       NOT NULL,
    address            VARCHAR(300) NOT NULL,
    application_status VARCHAR(20)  NOT NULL,
    provider_status    VARCHAR(20)  NOT NULL,
    accepting_orders   BOOLEAN      NOT NULL DEFAULT TRUE,
    rejection_reason   VARCHAR(500),
    status_reason      VARCHAR(500),
    submitted_at       DATETIME(6),
    reviewed_at        DATETIME(6),
    reviewed_by        BIGINT,
    verified_at        DATETIME(6),
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_providers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_providers_area FOREIGN KEY (area_id) REFERENCES areas (id)
);

CREATE TABLE provider_categories (
    provider_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (provider_id, category_id),
    CONSTRAINT fk_pc_provider FOREIGN KEY (provider_id) REFERENCES providers (id),
    CONSTRAINT fk_pc_category FOREIGN KEY (category_id) REFERENCES service_categories (id)
);

CREATE TABLE provider_documents (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id  BIGINT      NOT NULL,
    doc_type     VARCHAR(30) NOT NULL,
    file_key     VARCHAR(64) NOT NULL,
    content_type VARCHAR(60) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    CONSTRAINT fk_pd_provider FOREIGN KEY (provider_id) REFERENCES providers (id)
);

CREATE TABLE provider_branches (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id           BIGINT       NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    area_id               BIGINT       NOT NULL,
    address               VARCHAR(300) NOT NULL,
    latitude              NUMERIC(9, 6),
    longitude             NUMERIC(9, 6),
    phone                 VARCHAR(20)  NOT NULL,
    service_radius_km     INT,
    slot_minutes          INT          NOT NULL,
    max_bookings_per_slot INT          NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    CONSTRAINT fk_pb_provider FOREIGN KEY (provider_id) REFERENCES providers (id),
    CONSTRAINT fk_pb_area FOREIGN KEY (area_id) REFERENCES areas (id)
);
CREATE INDEX ix_branches_provider ON provider_branches (provider_id);

CREATE TABLE branch_hours (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    branch_id   BIGINT NOT NULL,
    day_of_week INT    NOT NULL,
    opens_at    TIME   NOT NULL,
    closes_at   TIME   NOT NULL,
    CONSTRAINT uq_branch_hours UNIQUE (branch_id, day_of_week),
    CONSTRAINT fk_bh_branch FOREIGN KEY (branch_id) REFERENCES provider_branches (id)
);

CREATE TABLE provider_vehicle_support (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id BIGINT      NOT NULL,
    make        VARCHAR(60) NOT NULL,
    model       VARCHAR(60),
    year_from   INT,
    year_to     INT,
    CONSTRAINT fk_pvs_provider FOREIGN KEY (provider_id) REFERENCES providers (id)
);
CREATE INDEX ix_pvs_provider ON provider_vehicle_support (provider_id);

CREATE TABLE provider_policies (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id        BIGINT      NOT NULL,
    policy_version     INT         NOT NULL,
    cancellation_terms TEXT        NOT NULL,
    no_show_terms      TEXT        NOT NULL,
    service_conditions TEXT        NOT NULL,
    return_terms       TEXT        NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    CONSTRAINT uq_provider_policy_version UNIQUE (provider_id, policy_version),
    CONSTRAINT fk_pp_provider FOREIGN KEY (provider_id) REFERENCES providers (id)
);

CREATE TABLE provider_subscriptions (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id          BIGINT      NOT NULL UNIQUE,
    current_period_start DATE,
    next_due_date        DATE,
    exempt_until         DATE,
    last_paid_at         DATETIME(6),
    last_reminder_due    DATE,
    last_reminder_days   INT,
    overdue_notified_due DATE,
    restricted_notified_due DATE,
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_ps_provider FOREIGN KEY (provider_id) REFERENCES providers (id)
);

CREATE TABLE subscription_exemptions (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id BIGINT       NOT NULL,
    until_date  DATE         NOT NULL,
    reason      VARCHAR(500) NOT NULL,
    created_by  BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_se_provider FOREIGN KEY (provider_id) REFERENCES providers (id)
);

-- ============================================================ requests and offers
CREATE TABLE service_requests (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id          BIGINT       NOT NULL,
    vehicle_id           BIGINT,
    vehicle_make         VARCHAR(60),
    vehicle_model        VARCHAR(60),
    vehicle_year         INT,
    area_id              BIGINT       NOT NULL,
    address              VARCHAR(300) NOT NULL,
    latitude             NUMERIC(9, 6),
    longitude            NUMERIC(9, 6),
    preferred_date       DATE,
    preferred_time_from  TIME,
    preferred_time_to    TIME,
    notes                TEXT,
    photo_keys           TEXT,
    explicit_provider_id BIGINT,
    status               VARCHAR(30)  NOT NULL,
    response_deadline    DATETIME(6)  NOT NULL,
    cancel_reason        VARCHAR(500),
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_sr_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT fk_sr_area FOREIGN KEY (area_id) REFERENCES areas (id)
);
CREATE INDEX ix_requests_customer ON service_requests (customer_id, created_at);
CREATE INDEX ix_requests_status ON service_requests (status, response_deadline);

CREATE TABLE service_items (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id  BIGINT      NOT NULL,
    category_id BIGINT      NOT NULL,
    description TEXT,
    status      VARCHAR(30) NOT NULL,
    order_id    BIGINT,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_item_category UNIQUE (request_id, category_id),
    CONSTRAINT fk_si_request FOREIGN KEY (request_id) REFERENCES service_requests (id),
    CONSTRAINT fk_si_category FOREIGN KEY (category_id) REFERENCES service_categories (id)
);

CREATE TABLE request_targets (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id   BIGINT        NOT NULL,
    provider_id  BIGINT        NOT NULL,
    branch_id    BIGINT,
    distance_km  NUMERIC(8, 2),
    rank_score   NUMERIC(8, 4) NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    viewed_at    DATETIME(6),
    responded_at DATETIME(6),
    created_at   DATETIME(6)   NOT NULL,
    CONSTRAINT uq_request_target UNIQUE (request_id, provider_id),
    CONSTRAINT fk_rt_request FOREIGN KEY (request_id) REFERENCES service_requests (id),
    CONSTRAINT fk_rt_provider FOREIGN KEY (provider_id) REFERENCES providers (id)
);
CREATE INDEX ix_targets_provider ON request_targets (provider_id, status);

CREATE TABLE offers (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id  BIGINT      NOT NULL,
    provider_id BIGINT      NOT NULL,
    branch_id   BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    notes       TEXT,
    expires_at  DATETIME(6) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_offer_provider UNIQUE (request_id, provider_id),
    CONSTRAINT fk_offers_request FOREIGN KEY (request_id) REFERENCES service_requests (id),
    CONSTRAINT fk_offers_provider FOREIGN KEY (provider_id) REFERENCES providers (id),
    CONSTRAINT fk_offers_branch FOREIGN KEY (branch_id) REFERENCES provider_branches (id)
);
CREATE INDEX ix_offers_status ON offers (status, expires_at);

CREATE TABLE offer_lines (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    offer_id             BIGINT         NOT NULL,
    service_item_id      BIGINT         NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    available            BOOLEAN        NOT NULL,
    service_price        NUMERIC(12, 2) NOT NULL,
    labor_cost           NUMERIC(12, 2) NOT NULL,
    parts_total          NUMERIC(12, 2) NOT NULL,
    discount             NUMERIC(12, 2) NOT NULL,
    provider_total       NUMERIC(12, 2) NOT NULL,
    commission_mode      VARCHAR(10)    NOT NULL,
    commission_value     NUMERIC(12, 2) NOT NULL,
    commission_amount    NUMERIC(12, 2) NOT NULL,
    commission_payer     VARCHAR(10)    NOT NULL,
    commission_config_id BIGINT,
    customer_total       NUMERIC(12, 2) NOT NULL,
    estimated_minutes    INT,
    proposed_start       DATETIME(6),
    notes                TEXT,
    warranty_provided    BOOLEAN        NOT NULL,
    warranty_days        INT,
    warranty_coverage    VARCHAR(20),
    warranty_exclusions  TEXT,
    warranty_conditions  TEXT,
    order_id             BIGINT,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    CONSTRAINT fk_ol_offer FOREIGN KEY (offer_id) REFERENCES offers (id),
    CONSTRAINT fk_ol_item FOREIGN KEY (service_item_id) REFERENCES service_items (id)
);
CREATE INDEX ix_lines_item ON offer_lines (service_item_id, status);
CREATE INDEX ix_lines_offer ON offer_lines (offer_id);

CREATE TABLE offer_line_parts (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    line_id    BIGINT         NOT NULL,
    name       VARCHAR(120)   NOT NULL,
    quantity   INT            NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    total      NUMERIC(12, 2) NOT NULL,
    CONSTRAINT fk_olp_line FOREIGN KEY (line_id) REFERENCES offer_lines (id)
);

-- ============================================================ service orders
CREATE TABLE service_orders (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id                 BIGINT         NOT NULL,
    service_item_id            BIGINT         NOT NULL UNIQUE,
    line_id                    BIGINT         NOT NULL UNIQUE,
    offer_id                   BIGINT         NOT NULL,
    customer_id                BIGINT         NOT NULL,
    provider_id                BIGINT         NOT NULL,
    provider_user_id           BIGINT         NOT NULL,
    branch_id                  BIGINT         NOT NULL,
    category_id                BIGINT         NOT NULL,
    status                     VARCHAR(40)    NOT NULL,
    cash_status                VARCHAR(30)    NOT NULL,
    service_price              NUMERIC(12, 2) NOT NULL,
    labor_cost                 NUMERIC(12, 2) NOT NULL,
    parts_total                NUMERIC(12, 2) NOT NULL,
    discount                   NUMERIC(12, 2) NOT NULL,
    provider_total             NUMERIC(12, 2) NOT NULL,
    commission_mode            VARCHAR(10)    NOT NULL,
    commission_value           NUMERIC(12, 2) NOT NULL,
    commission_base            NUMERIC(12, 2) NOT NULL,
    commission_amount          NUMERIC(12, 2) NOT NULL,
    commission_payer           VARCHAR(10)    NOT NULL,
    commission_config_id       BIGINT,
    customer_total             NUMERIC(12, 2) NOT NULL,
    approved_charges_total     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    final_amount               NUMERIC(12, 2),
    cash_reported_amount       NUMERIC(12, 2),
    cash_reported_at           DATETIME(6),
    cash_confirmed_at          DATETIME(6),
    warranty_provided          BOOLEAN        NOT NULL,
    warranty_days              INT,
    warranty_coverage          VARCHAR(20),
    warranty_exclusions        TEXT,
    warranty_conditions        TEXT,
    policy_snapshot            TEXT           NOT NULL,
    appointment_start          DATETIME(6),
    appointment_end            DATETIME(6),
    started_at                 DATETIME(6),
    provider_completed_at      DATETIME(6),
    completed_at               DATETIME(6),
    canceled_at                DATETIME(6),
    cancellation_kind          VARCHAR(30),
    cancellation_reason        VARCHAR(500),
    canceled_by                BIGINT,
    invoice_number             VARCHAR(30) UNIQUE,
    interruption_requested_at  DATETIME(6),
    interruption_reason        VARCHAR(1000),
    interruption_work_done     TEXT,
    interruption_labor         NUMERIC(12, 2),
    interruption_parts         NUMERIC(12, 2),
    interruption_proposed      NUMERIC(12, 2),
    interruption_evidence      TEXT,
    interruption_statement_at  DATETIME(6),
    interruption_outcome       VARCHAR(20),
    created_at                 DATETIME(6)    NOT NULL,
    updated_at                 DATETIME(6)    NOT NULL,
    version                    BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_so_request FOREIGN KEY (request_id) REFERENCES service_requests (id),
    CONSTRAINT fk_so_item FOREIGN KEY (service_item_id) REFERENCES service_items (id),
    CONSTRAINT fk_so_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT fk_so_provider FOREIGN KEY (provider_id) REFERENCES providers (id),
    CONSTRAINT fk_so_branch FOREIGN KEY (branch_id) REFERENCES provider_branches (id)
);
CREATE INDEX ix_orders_customer ON service_orders (customer_id, created_at);
CREATE INDEX ix_orders_provider ON service_orders (provider_id, created_at);
CREATE INDEX ix_orders_branch_slot ON service_orders (branch_id, appointment_start);

CREATE TABLE order_events (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id      BIGINT      NOT NULL,
    event_type    VARCHAR(40) NOT NULL,
    actor_user_id BIGINT,
    actor_role    VARCHAR(20) NOT NULL,
    note          TEXT,
    created_at    DATETIME(6) NOT NULL,
    CONSTRAINT fk_oe_order FOREIGN KEY (order_id) REFERENCES service_orders (id)
);
CREATE INDEX ix_order_events_order ON order_events (order_id);

CREATE TABLE order_charges (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id    BIGINT         NOT NULL,
    description VARCHAR(500)   NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    decided_at  DATETIME(6),
    created_at  DATETIME(6)    NOT NULL,
    CONSTRAINT fk_oc_order FOREIGN KEY (order_id) REFERENCES service_orders (id)
);

CREATE TABLE ratings (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id    BIGINT      NOT NULL UNIQUE,
    customer_id BIGINT      NOT NULL,
    provider_id BIGINT      NOT NULL,
    stars       INT         NOT NULL,
    comment     TEXT,
    hidden      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_ratings_order FOREIGN KEY (order_id) REFERENCES service_orders (id)
);
CREATE INDEX ix_ratings_provider ON ratings (provider_id, created_at);

-- ============================================================ warranty and disputes
CREATE TABLE warranties (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id      BIGINT      NOT NULL UNIQUE,
    customer_id   BIGINT      NOT NULL,
    provider_id   BIGINT      NOT NULL,
    coverage      VARCHAR(20) NOT NULL,
    duration_days INT         NOT NULL,
    exclusions    TEXT,
    conditions    TEXT,
    starts_at     DATETIME(6) NOT NULL,
    ends_at       DATETIME(6) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    CONSTRAINT fk_w_order FOREIGN KEY (order_id) REFERENCES service_orders (id)
);

CREATE TABLE warranty_claims (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    warranty_id   BIGINT      NOT NULL,
    order_id      BIGINT      NOT NULL,
    customer_id   BIGINT      NOT NULL,
    provider_id   BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    description   TEXT        NOT NULL,
    evidence_keys TEXT,
    provider_note TEXT,
    responded_at  DATETIME(6),
    dispute_id    BIGINT,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_wc_warranty FOREIGN KEY (warranty_id) REFERENCES warranties (id)
);

CREATE TABLE disputes (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    dispute_type       VARCHAR(30)    NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    order_id           BIGINT,
    request_id         BIGINT,
    listing_id         BIGINT,
    warranty_claim_id  BIGINT,
    opened_by          BIGINT         NOT NULL,
    opened_by_role     VARCHAR(20)    NOT NULL,
    respondent_user_id BIGINT,
    description        TEXT           NOT NULL,
    assigned_admin_id  BIGINT,
    outcome            VARCHAR(30),
    resolution_note    TEXT,
    final_amount       NUMERIC(12, 2),
    resolved_by        BIGINT,
    resolved_at        DATETIME(6),
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_disputes_status ON disputes (status, created_at);
CREATE INDEX ix_disputes_order ON disputes (order_id);

CREATE TABLE dispute_evidence (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    dispute_id BIGINT      NOT NULL,
    by_user_id BIGINT      NOT NULL,
    note       TEXT,
    file_key   VARCHAR(64),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_de_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id)
);

-- ============================================================ finance
CREATE TABLE ledger_entries (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    entry_type        VARCHAR(30)    NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    amount            NUMERIC(12, 2) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    payer_user_id     BIGINT         NOT NULL,
    provider_id       BIGINT,
    order_id          BIGINT,
    request_id        BIGINT,
    listing_id        BIGINT,
    rule_description  VARCHAR(500)   NOT NULL,
    config_version_id BIGINT,
    due_date          DATE,
    reported_at       DATETIME(6),
    settled_at        DATETIME(6),
    method            VARCHAR(20),
    reference         VARCHAR(120),
    note              TEXT,
    created_by        BIGINT,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_ledger_payer ON ledger_entries (payer_user_id, status);
CREATE INDEX ix_ledger_provider ON ledger_entries (provider_id, entry_type);
CREATE INDEX ix_ledger_type ON ledger_entries (entry_type, status);

-- ============================================================ used-item marketplace
CREATE TABLE listings (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    seller_id        BIGINT         NOT NULL,
    title            VARCHAR(120)   NOT NULL,
    category         VARCHAR(20)    NOT NULL,
    item_condition   VARCHAR(20)    NOT NULL,
    description      TEXT           NOT NULL,
    price            NUMERIC(12, 2) NOT NULL,
    area_id          BIGINT         NOT NULL,
    location_text    VARCHAR(200),
    contact_method   VARCHAR(10)    NOT NULL,
    photo_keys       TEXT,
    status           VARCHAR(20)    NOT NULL,
    rejection_reason VARCHAR(500),
    published_at     DATETIME(6),
    expires_at       DATETIME(6),
    sold_at          DATETIME(6),
    closed_at        DATETIME(6),
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_listings_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT fk_listings_area FOREIGN KEY (area_id) REFERENCES areas (id)
);
CREATE INDEX ix_listings_status ON listings (status, published_at);
CREATE INDEX ix_listings_seller ON listings (seller_id, status);

-- ============================================================ communication
CREATE TABLE conversations (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    kind            VARCHAR(10) NOT NULL,
    ref_id          BIGINT      NOT NULL,
    user_a          BIGINT      NOT NULL,
    user_b          BIGINT      NOT NULL,
    a_last_read_id  BIGINT      NOT NULL DEFAULT 0,
    b_last_read_id  BIGINT      NOT NULL DEFAULT 0,
    last_message_at DATETIME(6),
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT uq_conversation UNIQUE (kind, ref_id, user_a, user_b)
);
CREATE INDEX ix_conversations_a ON conversations (user_a);
CREATE INDEX ix_conversations_b ON conversations (user_b);

CREATE TABLE messages (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    sender_id       BIGINT      NOT NULL,
    body            TEXT,
    file_key        VARCHAR(64),
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
);
CREATE INDEX ix_messages_conversation ON messages (conversation_id, id);

CREATE TABLE notifications (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    ref_type   VARCHAR(30),
    ref_id     BIGINT,
    read_at    DATETIME(6),
    created_at DATETIME(6)  NOT NULL
);
CREATE INDEX ix_notifications_user ON notifications (user_id, read_at);

-- ============================================================ trust
CREATE TABLE fraud_flags (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    flag_type       VARCHAR(30)  NOT NULL,
    subject_user_id BIGINT       NOT NULL,
    details         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    reviewed_by     BIGINT,
    review_note     VARCHAR(1000),
    reviewed_at     DATETIME(6),
    created_at      DATETIME(6)  NOT NULL
);
CREATE INDEX ix_fraud_status ON fraud_flags (status, created_at);
CREATE INDEX ix_fraud_subject ON fraud_flags (subject_user_id, flag_type);

CREATE TABLE audit_logs (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id    BIGINT,
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   BIGINT,
    details     TEXT,
    ip          VARCHAR(64),
    created_at  DATETIME(6)  NOT NULL
);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
