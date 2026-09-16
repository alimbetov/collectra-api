import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button, DataTable, Dialog, FormField, Pagination } from '.';

describe('shared UI foundation', () => {
  it('makes a loading button inert and exposes busy state', () => {
    render(<Button loading>Сохранить</Button>);
    expect(screen.getByRole('button', { name: 'Сохранить' })).toBeDisabled();
    expect(screen.getByRole('button')).toHaveAttribute('aria-busy', 'true');
  });

  it('connects FormField labels and error descriptions', () => {
    render(<FormField label="Email" error="Обязательное поле"><input /></FormField>);
    const input = screen.getByLabelText('Email');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Обязательное поле');
  });

  it('renders a semantic table and a separate empty state', () => {
    const columns = [{ key: 'name', header: 'Имя', render: (row: { name: string }) => row.name }];
    const { rerender } = render(<DataTable columns={columns} rows={[{ name: 'A' }]} rowKey={(row) => row.name} emptyTitle="Пусто" />);
    expect(screen.getByRole('table')).toBeInTheDocument();
    rerender(<DataTable columns={columns} rows={[]} rowKey={(row) => row.name} emptyTitle="Пусто" />);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByText('Пусто')).toBeInTheDocument();
  });

  it('does not navigate beyond pagination bounds', async () => {
    const onPageChange = vi.fn();
    render(<Pagination page={0} totalPages={2} onPageChange={onPageChange} />);
    expect(screen.getByRole('button', { name: 'Назад' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Далее' }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it('closes a dialog on Escape', async () => {
    const onClose = vi.fn();
    render(<Dialog open title="Подтверждение" onClose={onClose}>Текст</Dialog>);
    expect(screen.getByRole('dialog')).toHaveAccessibleName('Подтверждение');
    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledOnce();
  });
});
