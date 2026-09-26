import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { deleteFile, getFileDownloadUrl } from '../../entities/file/api/file.api';
import { fileKeys, fileQueries } from '../../entities/file/api/file.queries';
import { useAuth } from '../../features/auth/model/auth-context';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { Button, Spinner, StatusBadge } from '../../shared/ui';

export function FileDetailPage() {
  const { fileId = '' } = useParams();
  const { hasPermission } = useAuth();
  const navigate = useNavigate();
  const client = useQueryClient();
  const query = useQuery(fileQueries.detail(fileId));
  const remove = useMutation({
    mutationFn: () => deleteFile(fileId),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: fileKeys.all });
      navigate('/files', { replace: true });
    },
  });

  if (query.error instanceof ApiError && query.error.status === 403) return <Navigate to="/forbidden" replace />;
  if (query.isLoading) return <Spinner label="Загрузка файла" />;
  if (query.error) return <ProblemDetailPanel error={query.error} onRetry={() => void query.refetch()} />;
  if (!query.data) return null;
  const file = query.data;

  const download = async () => {
    const result = await getFileDownloadUrl(fileId);
    window.location.assign(result.url);
  };
  const confirmDelete = () => {
    if (window.confirm(`Удалить файл «${file.originalFilename}»?`)) remove.mutate();
  };

  return <div className="file-detail-page">
    <header className="dashboard-page__header"><div><p className="eyebrow">File</p><h1>{file.originalFilename}</h1><StatusBadge tone={file.status === 'READY' ? 'success' : file.status === 'FAILED' ? 'danger' : 'warning'}>{file.status}</StatusBadge></div></header>
    <dl><dt>Категория</dt><dd>{file.category}</dd><dt>Content-Type</dt><dd>{file.contentType ?? '—'}</dd><dt>Размер</dt><dd>{file.sizeBytes ?? '—'}</dd><dt>Создан</dt><dd>{new Date(file.createdAt).toLocaleString()}</dd><dt>Истекает</dt><dd>{file.expiresAt ? new Date(file.expiresAt).toLocaleString() : '—'}</dd></dl>
    <div className="form-actions">
      <Button type="button" onClick={() => void download()} disabled={file.status !== 'READY'}>Скачать</Button>
      {hasPermission('FILE_DELETE') ? <Button type="button" variant="secondary" onClick={confirmDelete} disabled={remove.isPending || file.status === 'DELETED'}>Удалить</Button> : null}
    </div>
    {remove.error ? <ProblemDetailPanel error={remove.error} /> : null}
  </div>;
}
