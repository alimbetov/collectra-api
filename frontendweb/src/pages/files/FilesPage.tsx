import { useQuery } from '@tanstack/react-query';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { fileQueries } from '../../entities/file/api/file.queries';
import type { FileCategory, FileStatus } from '../../entities/file/model/file.types';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { DataTable, Pagination, Spinner, StatusBadge } from '../../shared/ui';

const categories: FileCategory[] = ['IMPORT_SOURCE', 'GENERATED_DOCUMENT', 'TEMPLATE_ASSET', 'ATTACHMENT', 'TEMP'];
const statuses: FileStatus[] = ['UPLOADING', 'READY', 'FAILED', 'DELETE_PENDING', 'DELETED'];

export function FilesPage() {
  const [sp, setSp] = useSearchParams();
  const page = Math.max(0, Number(sp.get('page') ?? 0) || 0);
  const category = categories.includes(sp.get('category') as FileCategory) ? sp.get('category') as FileCategory : undefined;
  const status = statuses.includes(sp.get('status') as FileStatus) ? sp.get('status') as FileStatus : undefined;
  const filename = sp.get('filename')?.trim() || undefined;
  const query = useQuery(fileQueries.list({ category, status, filename, page, size: 50, sort: 'createdAt,desc' }));

  const update = (key: string, value?: string) => {
    const next = new URLSearchParams(sp);
    value ? next.set(key, value) : next.delete(key);
    if (key !== 'page') next.delete('page');
    setSp(next);
  };

  if (query.error instanceof ApiError && query.error.status === 403) return <Navigate to="/forbidden" replace />;

  const columns = [
    { key: 'name', header: 'Файл', render: (row: any) => <Link className="table-link" to={row.fileId}>{row.originalFilename}</Link> },
    { key: 'category', header: 'Категория', render: (row: any) => row.category },
    { key: 'status', header: 'Статус', render: (row: any) => <StatusBadge tone={row.status === 'READY' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'}>{row.status}</StatusBadge> },
    { key: 'size', header: 'Размер', render: (row: any) => row.sizeBytes == null ? '—' : `${Math.ceil(row.sizeBytes / 1024)} KB` },
    { key: 'created', header: 'Создан', render: (row: any) => new Date(row.createdAt).toLocaleString() },
  ];

  return <div className="files-page">
    <header className="dashboard-page__header"><div><p className="eyebrow">Files</p><h1>Файлы</h1><p>Tenant-scoped реестр загруженных и созданных файлов.</p></div><Link className="ui-button ui-button--primary" to="/files/new">Загрузить файл</Link></header>
    <div className="platform-tenant-filters">
      <label><span>Имя</span><input value={filename ?? ''} onChange={e => update('filename', e.target.value || undefined)} /></label>
      <label><span>Категория</span><select value={category ?? ''} onChange={e => update('category', e.target.value || undefined)}><option value="">Все</option>{categories.map(v => <option key={v}>{v}</option>)}</select></label>
      <label><span>Статус</span><select value={status ?? ''} onChange={e => update('status', e.target.value || undefined)}><option value="">Все</option>{statuses.map(v => <option key={v}>{v}</option>)}</select></label>
    </div>
    {query.isLoading ? <Spinner label="Загрузка файлов" /> : null}
    {query.error ? <ProblemDetailPanel error={query.error} onRetry={() => void query.refetch()} /> : null}
    {query.data ? <><DataTable columns={columns} rows={query.data.items} rowKey={r => r.fileId} emptyTitle="Файлов нет" /><Pagination page={query.data.page} totalPages={query.data.totalPages} onPageChange={p => update('page', p ? String(p) : undefined)} /></> : null}
  </div>;
}
