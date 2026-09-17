import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import {
  Button,
  ConfirmDialog,
  DataTable,
  Dialog,
  FormField,
  Pagination,
  ToastProvider,
  useToast,
} from '.';

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

  it('traps focus inside a dialog and restores the trigger focus', async () => {
    const user = userEvent.setup();
    function Harness() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>Открыть</button>
          <Dialog open={open} title="Диалог" closeLabel="Закрыть" onClose={() => setOpen(false)} actions={<button type="button">Сохранить</button>}>
            Текст
          </Dialog>
        </>
      );
    }
    render(<Harness />);
    const trigger = screen.getByRole('button', { name: 'Открыть' });
    await user.click(trigger);
    const close = screen.getByRole('button', { name: 'Закрыть' });
    const save = screen.getByRole('button', { name: 'Сохранить' });
    expect(close).toHaveFocus();
    save.focus();
    await user.tab();
    expect(close).toHaveFocus();
    await user.keyboard('{Escape}');
    expect(trigger).toHaveFocus();
  });

  it('blocks confirmation controls while a command is pending', () => {
    render(
      <ConfirmDialog
        open
        pending
        title="Удалить запись"
        confirmLabel="Удалить"
        cancelLabel="Отмена"
        closeLabel="Закрыть"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      >
        Действие нельзя отменить.
      </ConfirmDialog>,
    );
    expect(screen.getByRole('button', { name: 'Удалить' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Отмена' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Закрыть' })).toBeDisabled();
  });

  it('announces and dismisses toast feedback', async () => {
    function Trigger() {
      const { showToast } = useToast();
      return <button type="button" onClick={() => showToast({ title: 'Сохранено', tone: 'success', durationMs: 0 })}>Показать</button>;
    }
    render(<ToastProvider closeLabel="Закрыть"><Trigger /></ToastProvider>);
    await userEvent.click(screen.getByRole('button', { name: 'Показать' }));
    expect(screen.getByRole('status')).toHaveTextContent('Сохранено');
    await userEvent.click(screen.getByRole('button', { name: 'Закрыть' }));
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
