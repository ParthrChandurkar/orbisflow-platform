import { apiRequest } from "./browser-client";
import type { AccessLink } from "@/lib/contracts/documents";

export const getDocumentAccessLink = (id: string) =>
  apiRequest<AccessLink>(`/api/v1/documents/${id}/access-link`);
