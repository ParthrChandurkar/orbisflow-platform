"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { FileUploadDropzone } from "@/components/documents/file-upload-dropzone";
import { createRequest } from "@/lib/api/requests";
import { ApiError } from "@/lib/contracts/api-error";

export default function NewRequestPage() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<ApiError | null>(null);

  async function upload(file: File) {
    setBusy(true);
    setProgress(0);
    setError(null);
    try {
      const request = await createRequest(file, setProgress);
      router.push(`/employee/requests/${request.id}`);
    } catch (caught) {
      setError(caught as ApiError);
      throw caught;
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <Link className="back-link" href="/employee/requests">
        ← Back to requests
      </Link>
      <div className="page-heading">
        <div>
          <span className="eyebrow">New request</span>
          <h1>Submit an invoice</h1>
          <p>We’ll extract the invoice details and route it automatically.</p>
        </div>
      </div>
      <section className="card upload-card">
        <div>
          <h2>Invoice document</h2>
          <p className="muted">
            Upload one clear document. The file remains private and is available
            only to people authorized for this request.
          </p>
        </div>
        <FileUploadDropzone
          busy={busy}
          onUpload={upload}
          progress={progress}
        />
        {error && (
          <div className="alert error">
            <strong>{uploadErrorTitle(error)}</strong>
            <span>{error.message}</span>
            {error.correlationId && <small>Reference: {error.correlationId}</small>}
          </div>
        )}
      </section>
    </>
  );
}

function uploadErrorTitle(error: ApiError): string {
  if (error.code === "MANAGER_NOT_ASSIGNED") return "No manager is assigned";
  if (error.status === 413) return "File is too large";
  if (error.status === 415) return "Unsupported file type";
  return "Upload failed";
}
