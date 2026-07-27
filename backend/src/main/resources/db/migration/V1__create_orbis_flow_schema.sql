CREATE TYPE user_role AS ENUM ('employee', 'manager', 'finance');
CREATE TYPE request_status AS ENUM (
    'uploaded_extracting', 'employee_review', 'manager_review',
    'rejected', 'finance_review', 'processed'
);
CREATE TYPE payment_status AS ENUM ('paid', 'scheduled');

CREATE TABLE users (
    id uuid PRIMARY KEY,
    login_identifier varchar(255) NOT NULL UNIQUE,
    password_hash text NOT NULL,
    role user_role NOT NULL,
    manager_id uuid NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_manager_not_self CHECK (manager_id IS NULL OR manager_id <> id),
    CONSTRAINT chk_users_manager_employee_only CHECK (role = 'employee' OR manager_id IS NULL)
);

CREATE TABLE requests (
    id uuid PRIMARY KEY,
    employee_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    manager_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status request_status NOT NULL DEFAULT 'uploaded_extracting',
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    manager_decision varchar(8) NULL CHECK (manager_decision IN ('approved', 'rejected')),
    manager_decided_by_user_id uuid NULL REFERENCES users(id) ON DELETE RESTRICT,
    manager_decided_at timestamptz NULL,
    rejection_reason text NULL,
    payment_status payment_status NULL,
    processed_by_user_id uuid NULL REFERENCES users(id) ON DELETE RESTRICT,
    processed_at timestamptz NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_requests_manager_decision_by_status CHECK (
        (status IN ('uploaded_extracting', 'employee_review', 'manager_review')
            AND manager_decision IS NULL
            AND manager_decided_by_user_id IS NULL
            AND manager_decided_at IS NULL
            AND rejection_reason IS NULL)
        OR
        (status = 'rejected'
            AND manager_decision = 'rejected'
            AND manager_decided_by_user_id IS NOT NULL
            AND manager_decided_at IS NOT NULL
            AND rejection_reason IS NOT NULL
            AND length(trim(rejection_reason)) > 0)
        OR
        (status IN ('finance_review', 'processed')
            AND manager_decision = 'approved'
            AND manager_decided_by_user_id IS NOT NULL
            AND manager_decided_at IS NOT NULL
            AND rejection_reason IS NULL)
    ),
    CONSTRAINT chk_requests_processing_by_status CHECK (
        (status = 'processed'
            AND payment_status IS NOT NULL
            AND processed_by_user_id IS NOT NULL
            AND processed_at IS NOT NULL)
        OR
        (status <> 'processed'
            AND payment_status IS NULL
            AND processed_by_user_id IS NULL
            AND processed_at IS NULL)
    )
);

CREATE TABLE documents (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES requests(id) ON DELETE RESTRICT,
    uploaded_by_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    s3_object_key text NOT NULL UNIQUE,
    original_filename text NOT NULL,
    mime_type varchar(32) NOT NULL CHECK (
        mime_type IN ('application/pdf', 'image/jpeg', 'image/png')
    ),
    file_size_bytes bigint NOT NULL CHECK (
        file_size_bytes > 0 AND file_size_bytes <= 10485760
    ),
    is_current boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE extracted_invoice_data (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL UNIQUE REFERENCES requests(id) ON DELETE RESTRICT,
    schema_version varchar(32) NOT NULL,
    extraction_status varchar(16) NOT NULL DEFAULT 'pending'
        CHECK (extraction_status IN ('pending', 'succeeded', 'failed')),
    vendor text NULL,
    total_amount numeric(19,4) NULL,
    invoice_date date NULL,
    validation_flags jsonb NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(validation_flags) = 'array'),
    failure_category varchar(64) NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_extraction_failure_category CHECK (
        (extraction_status = 'failed' AND failure_category IS NOT NULL)
        OR (extraction_status <> 'failed' AND failure_category IS NULL)
    )
);

CREATE TABLE invoice_line_items (
    id uuid PRIMARY KEY,
    extracted_invoice_data_id uuid NOT NULL
        REFERENCES extracted_invoice_data(id) ON DELETE CASCADE,
    line_number integer NOT NULL CHECK (line_number > 0),
    description text NOT NULL,
    amount numeric(19,4) NOT NULL,
    UNIQUE (extracted_invoice_data_id, line_number)
);

CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    request_id uuid NOT NULL REFERENCES requests(id) ON DELETE RESTRICT,
    type varchar(32) NOT NULL CHECK (type IN (
        'employee_correction', 'employee_rejection', 'manager_assignment',
        'finance_assignment', 'processed'
    )),
    read_at timestamptz NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES requests(id) ON DELETE RESTRICT,
    event_type varchar(32) NOT NULL CHECK (event_type IN (
        'upload', 'extraction', 'validation', 'field_correction',
        'routing', 'resubmission', 'approval', 'rejection', 'processing'
    )),
    actor_kind varchar(8) NOT NULL CHECK (actor_kind IN ('user', 'system')),
    actor_user_id uuid NULL REFERENCES users(id) ON DELETE RESTRICT,
    previous_status request_status NULL,
    resulting_status request_status NULL,
    context jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(context) = 'object'),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_actor CHECK (
        (actor_kind = 'user' AND actor_user_id IS NOT NULL)
        OR (actor_kind = 'system' AND actor_user_id IS NULL)
    )
);

CREATE INDEX idx_requests_employee_status_created
    ON requests (employee_id, status, created_at DESC, id DESC);
CREATE INDEX idx_requests_manager_status_updated
    ON requests (manager_id, status, updated_at DESC, id DESC);
CREATE INDEX idx_requests_finance_status_updated
    ON requests (status, updated_at DESC, id DESC)
    WHERE status IN ('finance_review', 'processed');
CREATE INDEX idx_users_manager
    ON users (manager_id, id) WHERE role = 'employee';
CREATE UNIQUE INDEX uq_documents_current_request
    ON documents (request_id) WHERE is_current;
CREATE INDEX idx_documents_request_created
    ON documents (request_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_user_read_created
    ON notifications (user_id, read_at, created_at DESC, id DESC);
CREATE INDEX idx_audit_request_created
    ON audit_log (request_id, created_at, id);
