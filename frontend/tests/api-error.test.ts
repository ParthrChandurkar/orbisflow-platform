import { describe, expect, it } from "vitest";
import { parseApiError } from "../lib/contracts/api-error";

describe("parseApiError", () => {
  it("preserves the backend error envelope and correlation id", () => {
    const error = parseApiError(422, {
      error: {
        code: "VALIDATION_FAILED",
        message: "Invoice details remain incomplete.",
        field_errors: [
          {
            field: "vendor",
            code: "MISSING_VALUE",
            message: "Vendor is required.",
          },
        ],
      },
      correlation_id: "request-123",
    });

    expect(error.status).toBe(422);
    expect(error.code).toBe("VALIDATION_FAILED");
    expect(error.message).toBe("Invoice details remain incomplete.");
    expect(error.fieldErrors).toHaveLength(1);
    expect(error.correlationId).toBe("request-123");
  });

  it("returns a safe fallback when the response is not an envelope", () => {
    const error = parseApiError(500, "<html>failure</html>");

    expect(error.code).toBe("REQUEST_FAILED");
    expect(error.message).toBe("The request could not be completed.");
    expect(error.fieldErrors).toEqual([]);
  });
});
