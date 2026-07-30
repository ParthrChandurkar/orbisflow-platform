import type { LineItem } from "@/lib/contracts/requests";
import { formatAmount } from "@/components/dashboards/request-summary-table";

export function LineItemsTable({ items }: { items: LineItem[] }) {
  if (items.length === 0) {
    return <p className="muted">No line items were extracted.</p>;
  }
  const sum = items.reduce((total, item) => total + Number(item.amount), 0);
  return (
    <div className="table-wrap compact-table">
      <table>
        <thead>
          <tr>
            <th>#</th>
            <th>Description</th>
            <th>Amount</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.line_number}>
              <td>{item.line_number}</td>
              <td>{item.description}</td>
              <td>{formatAmount(item.amount)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={2}>Line-item total</td>
            <td>{formatAmount(String(sum))}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  );
}
