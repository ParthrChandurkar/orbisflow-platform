import type { PageResponse, RequestSummary } from "@/lib/contracts/requests";
import { RequestSummaryTable } from "./request-summary-table";

export function PaginatedTable({
  data,
  requestBasePath,
  onPageChange,
}: {
  data: PageResponse<RequestSummary>;
  requestBasePath: string;
  onPageChange: (page: number) => void;
}) {
  return (
    <>
      <RequestSummaryTable
        items={data.items}
        requestBasePath={requestBasePath}
      />
      <div className="pagination">
        <span>
          Page {data.page + 1} of {Math.max(data.total_pages, 1)} ·{" "}
          {data.total_elements} requests
        </span>
        <div>
          <button
            className="button small"
            disabled={data.page === 0}
            onClick={() => onPageChange(data.page - 1)}
            type="button"
          >
            Previous
          </button>
          <button
            className="button small"
            disabled={data.page + 1 >= data.total_pages}
            onClick={() => onPageChange(data.page + 1)}
            type="button"
          >
            Next
          </button>
        </div>
      </div>
    </>
  );
}
