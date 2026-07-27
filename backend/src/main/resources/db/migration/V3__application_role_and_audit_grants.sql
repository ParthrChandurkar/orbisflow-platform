DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'orbisflow_app') THEN
        CREATE ROLE orbisflow_app LOGIN;
    END IF;
END
$$;

ALTER ROLE orbisflow_app PASSWORD '${applicationDatabasePassword}';

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO orbisflow_app', current_database());
END
$$;

GRANT USAGE ON SCHEMA public TO orbisflow_app;
GRANT USAGE ON TYPE user_role, request_status, payment_status TO orbisflow_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    users, requests, documents, extracted_invoice_data,
    invoice_line_items, notifications
TO orbisflow_app;

REVOKE ALL ON audit_log FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM orbisflow_app;
GRANT SELECT, INSERT ON audit_log TO orbisflow_app;
