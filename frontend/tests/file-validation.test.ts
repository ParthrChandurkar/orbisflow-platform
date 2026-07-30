import { describe, expect, it } from "vitest";
import { validateInvoiceFile } from "../components/documents/file-upload-dropzone";

describe("validateInvoiceFile", () => {
  it.each(["application/pdf", "image/jpeg", "image/png"])(
    "accepts supported MIME type %s",
    (type) => {
      expect(validateInvoiceFile({ type, size: 1024 })).toBeNull();
    },
  );

  it("rejects unsupported, empty, and oversized files", () => {
    expect(validateInvoiceFile({ type: "text/plain", size: 10 })).toMatch(
      /PDF, JPG, or PNG/,
    );
    expect(
      validateInvoiceFile({ type: "application/pdf", size: 0 }),
    ).toMatch(/empty/);
    expect(
      validateInvoiceFile({
        type: "image/png",
        size: 10 * 1024 * 1024 + 1,
      }),
    ).toMatch(/10 MB/);
  });
});
