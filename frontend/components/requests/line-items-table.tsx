import type { LineItem } from "@/lib/contracts/requests";
import { formatAmount } from "@/components/dashboards/request-summary-table";
import { ListMinus } from "lucide-react";

export function LineItemsTable({ items }: { items: LineItem[] }) {
  if (items.length === 0) {
    return (
      <div className="compact-empty">
        <ListMinus aria-hidden="true" size={20} />
        <span>No line items were extracted from this invoice.</span>
      </div>
    );
  }
  const sum = items.reduce((total, item) => total + Number(item.amount), 0);
  return (
    <div className="table-wrap compact-table responsive-table">
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
              <td data-label="#">{item.line_number}</td>
              <td data-label="Description">{item.description}</td>
              <td className="numeric" data-label="Amount">
                {formatAmount(item.amount)}
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={2} data-label="Summary">Line-item total</td>
            <td className="numeric" data-label="Total">
              {formatAmount(String(sum))}
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  );
}
