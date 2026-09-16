import type { Key, ReactNode } from 'react';
import { EmptyState } from './EmptyState';

export interface DataTableColumn<T> {
  key: string;
  header: ReactNode;
  render: (row: T) => ReactNode;
  align?: 'start' | 'center' | 'end';
}

interface DataTableProps<T> {
  columns: readonly DataTableColumn<T>[];
  rows: readonly T[];
  rowKey: (row: T) => Key;
  caption?: string;
  emptyTitle: ReactNode;
  emptyDescription?: ReactNode;
}

export function DataTable<T>({ columns, rows, rowKey, caption, emptyTitle, emptyDescription }: DataTableProps<T>) {
  if (rows.length === 0) return <EmptyState title={emptyTitle} description={emptyDescription} />;

  return (
    <div className="ui-data-table-scroll">
      <table className="ui-data-table">
        {caption ? <caption>{caption}</caption> : null}
        <thead>
          <tr>{columns.map((column) => <th key={column.key} scope="col" data-align={column.align ?? 'start'}>{column.header}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((column) => <td key={column.key} data-align={column.align ?? 'start'}>{column.render(row)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
