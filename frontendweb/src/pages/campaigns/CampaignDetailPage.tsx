import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import {
  activateCampaign,
  prepareCampaignRun,
  previewCampaign,
  updateCampaign,
  validateCampaign,
} from '../../entities/campaign/api/campaign.api';
import { campaignKeys, campaignQueries } from '../../entities/campaign/api/campaign.queries';
import type { CampaignSaveCommand } from '../../entities/campaign/model/campaign.types';
import { useAuth } from '../../features/auth/model/auth-context';
import { CampaignForm } from '../../features/campaigns/ui/CampaignForm';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { formatInstant } from '../../shared/i18n/formatters';
import { useI18n } from '../../shared/i18n/i18n-context';
import {
  Alert,
  Button,
  DataTable,
  EmptyState,
  Spinner,
  StatusBadge,
  useToast,
} from '../../shared/ui';

export function CampaignDetailPage() {
  const { campaignId = '' } = useParams();
  const { t, locale, timeZone } = useI18n();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('CAMPAIGN_MANAGE');
  const client = useQueryClient();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [validation, setValidation] = useState<Awaited<ReturnType<typeof validateCampaign>> | null>(null);
  const [preview, setPreview] = useState<Awaited<ReturnType<typeof previewCampaign>> | null>(null);

  const detail = useQuery(campaignQueries.detail(campaignId));
  const runs = useQuery(campaignQueries.runs(campaignId, 0));

  const updateMutation = useMutation({
    mutationFn: (command: CampaignSaveCommand) => {
      if (!detail.data) throw new Error('Campaign is not loaded');
      return updateCampaign(campaignId, { ...command, revision: detail.data.revision });
    },
    onSuccess: async (campaign) => {
      client.setQueryData(campaignKeys.detail(campaign.id), campaign);
      await client.invalidateQueries({ queryKey: campaignKeys.lists() });
      showToast({ title: t('campaigns.saved'), tone: 'success' });
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.problem?.code === 'VERSION_CONFLICT') {
        await detail.refetch();
      }
    },
  });

  const validateMutation = useMutation({
    mutationFn: () => validateCampaign(campaignId),
    onSuccess: setValidation,
  });
  const previewMutation = useMutation({
    mutationFn: () => previewCampaign(campaignId),
    onSuccess: setPreview,
  });
  const activateMutation = useMutation({
    mutationFn: () => activateCampaign(campaignId, detail.data?.revision ?? -1),
    onSuccess: async (campaign) => {
      client.setQueryData(campaignKeys.detail(campaign.id), campaign);
      await Promise.all([
        client.invalidateQueries({ queryKey: campaignKeys.lists() }),
        client.invalidateQueries({ queryKey: campaignKeys.runs(campaign.id, 0) }),
      ]);
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.problem?.code === 'VERSION_CONFLICT') {
        await detail.refetch();
      }
    },
  });
  const runMutation = useMutation({
    mutationFn: (commandId: string) => prepareCampaignRun(campaignId, commandId),
    onSuccess: async (result) => {
      await client.invalidateQueries({ queryKey: campaignKeys.runs(campaignId, 0) });
      showToast({ title: t('campaigns.commandCreated'), tone: 'success' });
      navigate(`/campaigns/${campaignId}/runs/${result.runId}`);
    },
  });

  if (detail.error instanceof ApiError && detail.error.status === 403) {
    return <Navigate to="/forbidden" replace />;
  }
  if (detail.error instanceof ApiError && detail.error.status === 404) {
    return (
      <div className="customer-detail">
        <EmptyState title={t('campaigns.empty')} />
        <Link to="/campaigns">{t('campaigns.back')}</Link>
      </div>
    );
  }
  if (detail.isLoading) return <Spinner label={t('campaigns.loading')} />;
  if (detail.error) return <ProblemDetailPanel error={detail.error} onRetry={() => void detail.refetch()} />;
  if (!detail.data) return null;

  const campaign = detail.data;
  const actionError =
    updateMutation.error ??
    validateMutation.error ??
    previewMutation.error ??
    activateMutation.error ??
    runMutation.error;

  const runColumns = [
    {
      key: 'status',
      header: t('campaigns.runStatus'),
      render: (row: NonNullable<typeof runs.data>['items'][number]) => (
        <Link to={`runs/${row.id}`} className="table-link">
          <StatusBadge tone={row.status === 'COMPLETED' ? 'success' : 'info'}>{row.status}</StatusBadge>
        </Link>
      ),
    },
    {
      key: 'recipients',
      header: t('campaigns.recipientCount'),
      render: (row: NonNullable<typeof runs.data>['items'][number]) => row.recipientCount,
    },
    {
      key: 'sent',
      header: t('campaigns.sent'),
      render: (row: NonNullable<typeof runs.data>['items'][number]) => row.sentCount,
    },
    {
      key: 'failed',
      header: t('campaigns.failed'),
      render: (row: NonNullable<typeof runs.data>['items'][number]) => row.failedCount,
    },
    {
      key: 'created',
      header: t('campaigns.updated'),
      render: (row: NonNullable<typeof runs.data>['items'][number]) =>
        formatInstant(row.createdAt, locale, timeZone),
    },
  ];

  return (
    <div className="customer-detail campaign-detail">
      <header className="customer-detail__header">
        <div>
          <Link className="customer-detail__back" to="/campaigns">
            ← {t('campaigns.back')}
          </Link>
          <p className="eyebrow">{t('campaigns.detail')}</p>
          <h1>{campaign.name}</h1>
          <div className="campaign-detail__badges">
            <StatusBadge tone={campaign.status === 'ACTIVE' ? 'success' : 'warning'}>
              {campaign.status}
            </StatusBadge>
            <StatusBadge tone="info">{campaign.channel}</StatusBadge>
          </div>
        </div>
        {canManage ? (
          <div className="campaign-detail__actions">
            <Button
              variant="secondary"
              loading={validateMutation.isPending}
              onClick={() => validateMutation.mutate()}
            >
              {t('campaigns.validate')}
            </Button>
            <Button
              variant="secondary"
              loading={previewMutation.isPending}
              onClick={() => previewMutation.mutate()}
            >
              {t('campaigns.preview')}
            </Button>
            {campaign.status === 'DRAFT' ? (
              <Button loading={activateMutation.isPending} onClick={() => activateMutation.mutate()}>
                {t('campaigns.activate')}
              </Button>
            ) : null}
            {campaign.status === 'ACTIVE' ? (
              <Button
                loading={runMutation.isPending}
                onClick={() => runMutation.mutate(crypto.randomUUID())}
              >
                {t('campaigns.run')}
              </Button>
            ) : null}
          </div>
        ) : null}
      </header>

      {!canManage ? <Alert variant="info">{t('campaigns.noManagePermission')}</Alert> : null}
      {actionError ? <ProblemDetailPanel error={actionError} /> : null}

      {validation ? (
        <Alert variant={validation.valid ? 'success' : 'danger'}>
          <strong>
            {validation.valid
              ? t('campaigns.validationValid')
              : t('campaigns.validationInvalid')}
          </strong>
          {validation.errors.length ? (
            <ul>
              {validation.errors.map((issue) => (
                <li key={`${issue.code}:${issue.message}`}>
                  {issue.code}: {issue.message}
                </li>
              ))}
            </ul>
          ) : null}
          {validation.warnings.length ? (
            <>
              <strong>{t('campaigns.validationWarnings')}</strong>
              <ul>
                {validation.warnings.map((issue) => (
                  <li key={`${issue.code}:${issue.message}`}>
                    {issue.code}: {issue.message}
                  </li>
                ))}
              </ul>
            </>
          ) : null}
        </Alert>
      ) : null}

      {preview ? (
        <section className="customer-detail-card campaign-preview">
          <h2>{t('campaigns.previewTitle')}</h2>
          <dl className="customer-detail-fields">
            <div>
              <dt>{t('campaigns.destination')}</dt>
              <dd>{preview.destination}</dd>
            </div>
            <div>
              <dt>{t('campaigns.locale')}</dt>
              <dd>{preview.requestedLocale} → {preview.resolvedLocale}</dd>
            </div>
            <div>
              <dt>{t('campaigns.subject')}</dt>
              <dd>{preview.subject ?? '—'}</dd>
            </div>
          </dl>
          <div className="campaign-preview__body">{preview.body}</div>
          {preview.documentUrlPreview ? <code>{preview.documentUrlPreview}</code> : null}
        </section>
      ) : null}

      {campaign.status === 'DRAFT' && canManage ? (
        <section className="customer-detail-card">
          <h2>{t('campaigns.save')}</h2>
          <CampaignForm
            initial={campaign}
            pending={updateMutation.isPending}
            submitLabel={t('campaigns.save')}
            onSubmit={(command) => updateMutation.mutate(command)}
          />
        </section>
      ) : (
        <section className="customer-detail-card">
          <dl className="customer-detail-fields">
            <div><dt>{t('campaigns.templateVersion')}</dt><dd>{campaign.messageTemplateVersionId}</dd></div>
            <div><dt>{t('campaigns.audienceType')}</dt><dd>{campaign.audienceSelectionType}</dd></div>
            <div><dt>{t('campaigns.scheduledAt')}</dt><dd>{campaign.scheduledAt ? formatInstant(campaign.scheduledAt, locale, timeZone) : t('campaigns.unscheduled')}</dd></div>
            <div><dt>{t('campaigns.revision')}</dt><dd>{campaign.revision}</dd></div>
          </dl>
        </section>
      )}

      <section className="customer-detail-card">
        <h2>{t('campaigns.runs')}</h2>
        {runs.error ? <ProblemDetailPanel error={runs.error} onRetry={() => void runs.refetch()} /> : null}
        {runs.data ? (
          <DataTable
            columns={runColumns}
            rows={runs.data.items}
            rowKey={(row) => row.id}
            emptyTitle={t('campaigns.noRuns')}
          />
        ) : null}
      </section>
    </div>
  );
}
