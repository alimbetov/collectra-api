import { Button } from './Button';

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  label?: string;
  previousLabel?: string;
  nextLabel?: string;
}

export function Pagination({ page, totalPages, onPageChange, label = 'Пагинация', previousLabel = 'Назад', nextLabel = 'Далее' }: PaginationProps) {
  const safeTotal = Math.max(0, totalPages);
  const current = safeTotal === 0 ? 0 : Math.min(Math.max(0, page), safeTotal - 1);
  return (
    <nav className="ui-pagination" aria-label={label}>
      <Button variant="secondary" disabled={current === 0} onClick={() => onPageChange(current - 1)}>{previousLabel}</Button>
      <span aria-live="polite">{safeTotal === 0 ? '0 / 0' : `${current + 1} / ${safeTotal}`}</span>
      <Button variant="secondary" disabled={safeTotal === 0 || current >= safeTotal - 1} onClick={() => onPageChange(current + 1)}>{nextLabel}</Button>
    </nav>
  );
}
