import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import {
  archiveTemplateAsset,
  registerTemplateAsset,
  uploadTemplateAssetFile,
} from '../../entities/template/api/template.api';
import { templateKeys, templateQueries } from '../../entities/template/api/template.queries';
import type { TemplateAssetDto } from '../../entities/template/model/template.types';
import { useAuth } from '../../features/auth/model/auth-context';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { useI18n } from '../../shared/i18n/i18n-context';
import { Button, ConfirmDialog, DataTable, FormField, Pagination, Spinner } from '../../shared/ui';

const PAGE_SIZE = 50;
const MAX_BYTES = 2 * 1024 * 1024;
const ALLOWED_TYPES = new Set(['image/png', 'image/jpeg', 'image/gif']);

export function TemplateAssetsPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('TEMPLATE_MANAGE');
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const [file, setFile] = useState<File | null>(null);
  const [key, setKey] = useState('');
  const [altText, setAltText] = useState('');
  const [clientError, setClientError] = useState<string | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<TemplateAssetDto | null>(null);

  const assets = useQuery(templateQueries.assets(page, PAGE_SIZE));

  const uploadMutation = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('Asset file is required');
      const uploaded = await uploadTemplateAssetFile(file);
      return registerTemplateAsset({
        key: key.trim(),
        fileId: uploaded.fileId,
        altText: altText.trim() || null,
      });
    },
    onSuccess: async () => {
      setFile(null);
      setKey('');
      setAltText('');
      setClientError(null);
      await client.invalidateQueries({ queryKey: templateKeys.assets() });
    },
  });

  const archiveMutation = useMutation({
    mutationFn: (assetId: string) => archiveTemplateAsset(assetId),
    onSuccess: async () => {
      setArchiveTarget(null);
      await client.invalidateQueries({ queryKey: templateKeys.assets() });
    },
  });

  if (!hasPermission('TEMPLATE_READ')) {
    return <Navigate to="/forbidden" replace />;
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!canManage) return;
    if (!file || !key.trim()) {
      setClientError(t('templates.assetRequired'));
      return;
    }
    if (!ALLOWED_TYPES.has(file.type)) {
      setClientError(t('templates.assetTypeError'));
      return;
    }
    if (file.size > MAX_BYTES) {
      setClientError(t('templates.assetSizeError'));
      return;
    }
    setClientError(null);
    uploadMutation.mutate();
  };

  const columns = [
    {
      key: 'key',
      header: t('templates.assetKey'),
      render: (row: TemplateAssetDto) => (
        <div>
          <strong>{row.key}</strong>
          <div><code>{row.placeholder}</code></div>
        </div>
      ),
    },
    {
      key: 'alt',
      header: t('templates.assetAlt'),
      render: (row: TemplateAssetDto) => row.altText || '—',
    },
    {
      key: 'file',
      header: t('templates.assetFile'),
      render: (row: TemplateAssetDto) => <code>{row.fileId}</code>,
    },
    {
      key: 'actions',
      header: '',
      align: 'end' as const,
      render: (row: TemplateAssetDto) =>
        canManage ? (
          <Button variant="danger" onClick={() => setArchiveTarget(row)}>
            {t('templates.assetArchive')}
          </Button>
        ) : null,
    },
  ];

  return (
    <div className="customer-detail">
      <header className="customer-detail__header">
        <div>
          <Link className="customer-detail__back" to="/templates">
            ← {t('templates.back')}
          </Link>
          <p className="eyebrow">{t('templates.eyebrow')}</p>
          <h1>{t('templates.assets')}</h1>
          <p>{t('templates.assetsDescription')}</p>
        </div>
      </header>

      {canManage ? (
        <form className="customer-detail-card campaign-form" onSubmit={submit}>
          <h2>{t('templates.assetUpload')}</h2>
          <FormField label={t('templates.assetFile')}>
            <input
              type="file"
              accept="image/png,image/jpeg,image/gif"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </FormField>
          <FormField label={t('templates.assetKey')}>
            <input value={key} maxLength={64} onChange={(event) => setKey(event.target.value)} />
          </FormField>
          <FormField label={t('templates.assetAlt')}>
            <input value={altText} maxLength={300} onChange={(event) => setAltText(event.target.value)} />
          </FormField>
          {clientError ? <p className="ui-form-field__error">{clientError}</p> : null}
          {uploadMutation.error ? <ProblemDetailPanel error={uploadMutation.error} /> : null}
          <Button type="submit" loading={uploadMutation.isPending}>
            {t('templates.assetUpload')}
          </Button>
        </form>
      ) : null}

      <section className="customer-detail-card">
        {assets.isLoading ? <Spinner label={t('templates.assetsLoading')} /> : null}
        {assets.error ? <ProblemDetailPanel error={assets.error} onRetry={() => void assets.refetch()} /> : null}
        {assets.data ? (
          <>
            <DataTable
              columns={columns}
              rows={assets.data.items}
              rowKey={(row) => row.id}
              emptyTitle={t('templates.assetsEmpty')}
            />
            <Pagination
              page={assets.data.page}
              totalPages={assets.data.totalPages}
              onPageChange={setPage}
            />
          </>
        ) : null}
      </section>

      <ConfirmDialog
        open={Boolean(archiveTarget)}
        title={t('templates.assetArchiveTitle')}
        confirmLabel={t('templates.assetArchive')}
        cancelLabel={t('templates.cancel')}
        closeLabel={t('templates.close')}
        destructive
        pending={archiveMutation.isPending}
        onCancel={() => setArchiveTarget(null)}
        onConfirm={() => {
          if (archiveTarget) archiveMutation.mutate(archiveTarget.id);
        }}
      >
        <p>{t('templates.assetArchiveDescription')}</p>
      </ConfirmDialog>
    </div>
  );
}
