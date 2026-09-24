import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import {
  archiveTemplate,
  createBuilderVersion,
  createTemplateVersion,
  renameTemplate,
} from '../../entities/template/api/template.api';
import { templateKeys, templateQueries } from '../../entities/template/api/template.queries';
import type {
  BuilderDocumentDto,
  TemplateChannel,
  TemplateVersionListItemDto,
} from '../../entities/template/model/template.types';
import { useAuth } from '../../features/auth/model/auth-context';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { formatInstant } from '../../shared/i18n/formatters';
import { useI18n } from '../../shared/i18n/i18n-context';
import {
  Alert,
  Button,
  ConfirmDialog,
  DataTable,
  FormField,
  Pagination,
  Spinner,
  StatusBadge,
} from '../../shared/ui';

const PAGE_SIZE = 50;
const CHANNELS: TemplateChannel[] = ['EMAIL', 'PDF', 'SMS', 'WHATSAPP', 'TELEGRAM'];

const emptyBuilderDocument = (): BuilderDocumentDto => ({
  version: '1.0',
  blocks: [
    {
      type: 'richText',
      props: { content: [{ type: 'text', value: '' }] },
    },
  ],
});

export function TemplateDetailPage() {
  const { templateId = '' } = useParams();
  const { t, locale: uiLocale, timeZone } = useI18n();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('TEMPLATE_MANAGE');
  const canPublish = hasPermission('TEMPLATE_PUBLISH');
  const navigate = useNavigate();
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const [renameOpen, setRenameOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const [channel, setChannel] = useState<TemplateChannel>('EMAIL');
  const [versionLocale, setVersionLocale] = useState('ru');
  const [subject, setSubject] = useState('');
  const [operationError, setOperationError] = useState<string | null>(null);

  const detail = useQuery(templateQueries.detail(templateId));
  const versions = useQuery(
    templateQueries.versionList(templateId, { page, size: PAGE_SIZE }),
  );

  const renameMutation = useMutation({
    mutationFn: () => {
      if (!detail.data) throw new Error('Template not loaded');
      return renameTemplate(templateId, {
        name: name.trim(),
        revision: detail.data.revision,
      });
    },
    onSuccess: async (updated) => {
      client.setQueryData(templateKeys.detail(templateId), updated);
      await client.invalidateQueries({ queryKey: templateKeys.lists() });
      setRenameOpen(false);
      setOperationError(null);
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.problem?.code === 'VERSION_CONFLICT') {
        setOperationError(t('templates.versionConflict'));
        await detail.refetch();
      }
    },
  });

  const archiveMutation = useMutation({
    mutationFn: () => {
      if (!detail.data) throw new Error('Template not loaded');
      return archiveTemplate(templateId, detail.data.revision);
    },
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: templateKeys.lists() });
      navigate('/templates', { replace: true });
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.problem?.code === 'VERSION_CONFLICT') {
        setOperationError(t('templates.versionConflict'));
        await detail.refetch();
      }
    },
  });

  const createVersionMutation = useMutation({
    mutationFn: async () => {
      if (channel === 'EMAIL' || channel === 'PDF') {
        return createBuilderVersion(templateId, {
          channel,
          locale: versionLocale.trim(),
          subject: channel === 'EMAIL' ? subject.trim() : null,
          builderJson: emptyBuilderDocument(),
          stylesheet: null,
        });
      }
      return createTemplateVersion(templateId, {
        channel,
        locale: versionLocale.trim(),
        subject: null,
        contentHtml: ' ',
        stylesheet: null,
      });
    },
    onSuccess: async (created) => {
      await client.invalidateQueries({ queryKey: templateKeys.versionLists(templateId) });
      setCreateOpen(false);
      navigate(`/templates/${templateId}/versions/${created.id}/builder`);
    },
  });

  if (detail.error instanceof ApiError && detail.error.status === 403) {
    return <Navigate to="/forbidden" replace />;
  }
  if (detail.isLoading) return <Spinner label={t('templates.loadingDetail')} />;
  if (detail.error || !detail.data) {
    return <ProblemDetailPanel error={detail.error} onRetry={() => void detail.refetch()} />;
  }

  const template = detail.data;

  const columns = [
    {
      key: 'version',
      header: t('templates.templateVersion'),
      render: (row: TemplateVersionListItemDto) => `v${row.templateVersion}`,
    },
    {
      key: 'channel',
      header: t('templates.channel'),
      render: (row: TemplateVersionListItemDto) => row.channel,
    },
    {
      key: 'locale',
      header: t('templates.locale'),
      render: (row: TemplateVersionListItemDto) => row.locale,
    },
    {
      key: 'subject',
      header: t('templates.subject'),
      render: (row: TemplateVersionListItemDto) => row.subject || '—',
    },
    {
      key: 'status',
      header: t('templates.status'),
      render: (row: TemplateVersionListItemDto) => (
        <StatusBadge tone={tone(row.status)}>{row.status}</StatusBadge>
      ),
    },
    {
      key: 'updated',
      header: t('templates.updatedAt'),
      render: (row: TemplateVersionListItemDto) =>
        formatInstant(row.updatedAt, uiLocale, timeZone),
    },
    {
      key: 'open',
      header: '',
      align: 'end' as const,
      render: (row: TemplateVersionListItemDto) => (
        <Link
          className="ui-button ui-button--secondary"
          to={`versions/${row.id}/builder`}
        >
          {t('templates.open')}
        </Link>
      ),
    },
  ];

  const submitVersion = (event: FormEvent) => {
    event.preventDefault();
    if (!versionLocale.trim()) return;
    if (channel === 'EMAIL' && !subject.trim()) return;
    createVersionMutation.mutate();
  };

  return (
    <div className="customer-detail template-detail">
      <header className="customer-detail__header">
        <div>
          <Link className="customer-detail__back" to="/templates">
            ← {t('templates.back')}
          </Link>
          <p className="eyebrow">{t('templates.detail')}</p>
          <h1>{template.name}</h1>
          <p>{template.code} · {template.documentType}</p>
        </div>
        <div className="campaign-detail__actions">
          <StatusBadge tone={template.status === 'ACTIVE' ? 'success' : 'neutral'}>
            {template.status}
          </StatusBadge>
          {canManage && template.status === 'ACTIVE' ? (
            <>
              <Button
                variant="secondary"
                onClick={() => {
                  setName(template.name);
                  setRenameOpen(true);
                  setOperationError(null);
                }}
              >
                {t('templates.rename')}
              </Button>
              <Button onClick={() => setCreateOpen(true)}>
                {t('templates.createVersion')}
              </Button>
              <Button variant="danger" onClick={() => setArchiveOpen(true)}>
                {t('templates.archive')}
              </Button>
            </>
          ) : null}
        </div>
      </header>

      {operationError ? <Alert variant="danger">{operationError}</Alert> : null}

      <section className="customer-detail-card">
        <dl className="customer-detail-fields">
          <div><dt>{t('templates.code')}</dt><dd>{template.code}</dd></div>
          <div><dt>{t('templates.documentType')}</dt><dd>{template.documentType}</dd></div>
          <div><dt>{t('templates.status')}</dt><dd>{template.status}</dd></div>
          <div><dt>{t('templates.revision')}</dt><dd>{template.revision}</dd></div>
          <div><dt>{t('templates.createdAt')}</dt><dd>{formatInstant(template.createdAt, uiLocale, timeZone)}</dd></div>
          <div><dt>{t('templates.updatedAt')}</dt><dd>{formatInstant(template.updatedAt, uiLocale, timeZone)}</dd></div>
        </dl>
      </section>

      <section className="customer-detail-card">
        <div className="dashboard-page__header">
          <div>
            <h2>{t('templates.versions')}</h2>
            <p>{t('templates.versionsDescription')}</p>
          </div>
          {!canPublish ? <span className="muted-text">{t('templates.noPublishPermission')}</span> : null}
        </div>

        {versions.isLoading ? <Spinner label={t('templates.loadingVersions')} /> : null}
        {versions.error ? <ProblemDetailPanel error={versions.error} onRetry={() => void versions.refetch()} /> : null}
        {versions.data ? (
          <>
            <DataTable
              columns={columns}
              rows={versions.data.items}
              rowKey={(row) => row.id}
              emptyTitle={t('templates.noVersions')}
            />
            <Pagination
              page={versions.data.page}
              totalPages={versions.data.totalPages}
              onPageChange={setPage}
            />
          </>
        ) : null}
      </section>

      <ConfirmDialog
        open={archiveOpen}
        title={t('templates.archiveTitle')}
        confirmLabel={t('templates.archive')}
        cancelLabel={t('templates.cancel')}
        closeLabel={t('templates.close')}
        destructive
        pending={archiveMutation.isPending}
        onCancel={() => setArchiveOpen(false)}
        onConfirm={() => archiveMutation.mutate()}
      >
        <p>{t('templates.archiveDescription')}</p>
      </ConfirmDialog>

      {renameOpen ? (
        <div className="customer-detail-card">
          <h2>{t('templates.rename')}</h2>
          <FormField label={t('templates.name')}>
            <input value={name} maxLength={200} onChange={(event) => setName(event.target.value)} />
          </FormField>
          <div className="campaign-form__actions">
            <Button
              loading={renameMutation.isPending}
              disabled={!name.trim()}
              onClick={() => renameMutation.mutate()}
            >
              {t('templates.save')}
            </Button>
            <Button variant="secondary" onClick={() => setRenameOpen(false)}>
              {t('templates.cancel')}
            </Button>
          </div>
        </div>
      ) : null}

      {createOpen ? (
        <form className="customer-detail-card campaign-form" onSubmit={submitVersion}>
          <h2>{t('templates.createVersion')}</h2>
          <FormField label={t('templates.channel')}>
            <select
              value={channel}
              onChange={(event) => setChannel(event.target.value as TemplateChannel)}
            >
              {CHANNELS.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </FormField>
          <FormField label={t('templates.locale')}>
            <input value={versionLocale} maxLength={10} onChange={(event) => setVersionLocale(event.target.value)} />
          </FormField>
          {channel === 'EMAIL' ? (
            <FormField label={t('templates.subject')}>
              <input value={subject} maxLength={300} onChange={(event) => setSubject(event.target.value)} />
            </FormField>
          ) : null}
          {createVersionMutation.error ? <ProblemDetailPanel error={createVersionMutation.error} /> : null}
          <div className="campaign-form__actions">
            <Button type="submit" loading={createVersionMutation.isPending}>
              {t('templates.createVersion')}
            </Button>
            <Button type="button" variant="secondary" onClick={() => setCreateOpen(false)}>
              {t('templates.cancel')}
            </Button>
          </div>
        </form>
      ) : null}
    </div>
  );
}

function tone(status: string): 'success' | 'warning' | 'neutral' {
  if (status === 'PUBLISHED') return 'success';
  if (status === 'DRAFT' || status === 'VALIDATED') return 'warning';
  return 'neutral';
}
