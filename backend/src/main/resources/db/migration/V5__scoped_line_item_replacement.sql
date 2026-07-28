-- The application role retains no table-level DELETE privilege. This narrowly
-- scoped function permits replacement of the implementation-child line-item
-- collection without allowing deletion of product resources.
CREATE OR REPLACE FUNCTION clear_invoice_line_items(p_extracted_invoice_data_id uuid)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    DELETE FROM invoice_line_items
    WHERE extracted_invoice_data_id = p_extracted_invoice_data_id;
$$;

REVOKE ALL ON FUNCTION clear_invoice_line_items(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION clear_invoice_line_items(uuid) TO orbisflow_app;
