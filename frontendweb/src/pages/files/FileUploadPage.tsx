import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { uploadFile } from '../../entities/file/api/file.api';
import { fileKeys } from '../../entities/file/api/file.queries';
import type { FileCategory } from '../../entities/file/model/file.types';
import { useAuth } from '../../features/auth/model/auth-context';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { Button } from '../../shared/ui';

const categories: FileCategory[] = ['IMPORT_SOURCE', 'TEMPLATE_ASSET', 'ATTACHMENT', 'TEMP'];

export function FileUploadPage() {
  const { hasPermission } = useAuth();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [category, setCategory] = useState<FileCategory>('IMPORT_SOURCE');
  const upload = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('Выберите файл');
      return uploadFile(file, category);
    },
    onSuccess: async result => {
      await client.invalidateQueries({ queryKey: fileKeys.all });
      navigate(`/files/${result.fileId}`, { replace: true });
    },
  });

  if (!hasPermission('FILE_UPLOAD')) return <Navigate to="/forbidden" replace />;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (file) upload.mutate();
  };

  return <div className="file-upload-page"><header className="dashboard-page__header"><div><p className="eyebrow">Files</p><h1>Загрузка файла</h1></div></header>
    <form onSubmit={submit}><label><span>Категория</span><select value={category} onChange={e => setCategory(e.target.value as FileCategory)}>{categories.map(v => <option key={v}>{v}</option>)}</select></label><label><span>Файл</span><input type="file" required onChange={e => setFile(e.target.files?.[0] ?? null)} /></label><div className="form-actions"><Button type="submit" disabled={!file || upload.isPending}>{upload.isPending ? 'Загрузка…' : 'Загрузить'}</Button></div></form>
    {upload.error ? <ProblemDetailPanel error={upload.error} /> : null}
  </div>;
}
