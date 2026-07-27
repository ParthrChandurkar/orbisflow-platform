-- No product resource exposes a DELETE operation per rbac.md section 2 and
-- backend-api.md section 2.8; all product DELETE attempts return
-- 405 METHOD_NOT_ALLOWED. Defense in depth requires the database application
-- role to be no more permissive than the application contract. The
-- invoice_line_items ON DELETE CASCADE remains a technical parent-child rule
-- and does not require a direct application-level DELETE grant to fire.
GRANT SELECT, INSERT, UPDATE ON
    users, requests, documents, extracted_invoice_data,
    invoice_line_items, notifications
TO orbisflow_app;

REVOKE DELETE ON
    users, requests, documents, extracted_invoice_data,
    invoice_line_items, notifications
FROM orbisflow_app;
