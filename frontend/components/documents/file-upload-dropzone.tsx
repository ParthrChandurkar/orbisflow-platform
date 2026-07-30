"use client";

import { ChangeEvent, DragEvent, useRef, useState } from "react";

const MAX_FILE_SIZE = 10 * 1024 * 1024;
const ACCEPTED_TYPES = new Set([
  "application/pdf",
  "image/jpeg",
  "image/png",
]);

export function validateInvoiceFile(file: Pick<File, "size" | "type">):
  | string
  | null {
  if (!ACCEPTED_TYPES.has(file.type)) {
    return "Choose a PDF, JPG, or PNG invoice.";
  }
  if (file.size === 0) return "The selected file is empty.";
  if (file.size > MAX_FILE_SIZE) return "The file must be 10 MB or smaller.";
  return null;
}

export function FileUploadDropzone({
  onUpload,
  busy = false,
  progress = 0,
  compact = false,
}: {
  onUpload: (file: File) => Promise<void>;
  busy?: boolean;
  progress?: number;
  compact?: boolean;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [dragging, setDragging] = useState(false);

  function choose(candidate?: File) {
    if (!candidate) return;
    const validation = validateInvoiceFile(candidate);
    setError(validation ?? "");
    setFile(validation ? null : candidate);
  }

  function changed(event: ChangeEvent<HTMLInputElement>) {
    choose(event.target.files?.[0]);
  }

  function dropped(event: DragEvent) {
    event.preventDefault();
    setDragging(false);
    choose(event.dataTransfer.files?.[0]);
  }

  async function upload() {
    if (!file) return;
    try {
      await onUpload(file);
    } catch {
      // Parent renders API errors while the selected file remains available.
    }
  }

  return (
    <div className={compact ? "upload-widget compact" : "upload-widget"}>
      <div
        className={dragging ? "dropzone dragging" : "dropzone"}
        onClick={() => !busy && input.current?.click()}
        onDragEnter={() => setDragging(true)}
        onDragLeave={() => setDragging(false)}
        onDragOver={(event) => event.preventDefault()}
        onDrop={dropped}
        onKeyDown={(event) => {
          if (!busy && (event.key === "Enter" || event.key === " ")) {
            input.current?.click();
          }
        }}
        role="button"
        tabIndex={0}
      >
        <input
          accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
          disabled={busy}
          hidden
          onChange={changed}
          ref={input}
          type="file"
        />
        <span className="upload-icon">↑</span>
        <strong>{file ? file.name : "Drop an invoice here"}</strong>
        <span>
          {file
            ? `${(file.size / 1024 / 1024).toFixed(2)} MB`
            : "or choose a PDF, JPG, or PNG · up to 10 MB"}
        </span>
      </div>
      {error && <div className="alert error">{error}</div>}
      {busy && (
        <div className="progress" aria-label={`Upload ${progress}% complete`}>
          <span style={{ width: `${progress}%` }} />
        </div>
      )}
      <button
        className="button primary"
        disabled={!file || busy}
        onClick={upload}
        type="button"
      >
        {busy ? `Uploading ${progress}%` : compact ? "Replace document" : "Submit invoice"}
      </button>
    </div>
  );
}
