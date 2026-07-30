export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export interface ApiErrorEnvelope {
  error: {
    code: string;
    message: string;
    field_errors?: FieldError[];
  };
  correlation_id?: string;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly fieldErrors: FieldError[] = [],
    public readonly correlationId?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function parseApiError(status: number, value: unknown): ApiError {
  const candidate = value as Partial<ApiErrorEnvelope> | null;
  const error = candidate?.error;
  return new ApiError(
    status,
    typeof error?.code === "string" ? error.code : "REQUEST_FAILED",
    typeof error?.message === "string"
      ? error.message
      : "The request could not be completed.",
    Array.isArray(error?.field_errors) ? error.field_errors : [],
    typeof candidate?.correlation_id === "string"
      ? candidate.correlation_id
      : undefined,
  );
}
