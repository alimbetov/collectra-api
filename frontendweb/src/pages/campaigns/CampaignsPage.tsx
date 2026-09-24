import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { createCampaign } from '../../entities/campaign/api/campaign.api';
import { campaignKeys, campaignQueries } from '../../entities/campaign/api/campaign.queries';
import type { CampaignChannel, CampaignSaveCommand } from '../../entities/campaign/model/campaign.types';
import { useAuth } from '../../features/auth/model/auth-context';
import { CampaignForm } from '../../features/campaigns/ui/CampaignForm';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { useI18n } from '../../shared/i18n/i18n-context';
import { formatInstant } from '../../shared/i18n/formatters';
import {
  Alert,
  Button,
  DataTable,
  Dialog,
  FormField,
  Pagination,
  Spinner,
  StatusBadge,
  useToast,
} from '../../shared/ui';

const statuses = ['DRAFT', 'ACTIVE', 'ARCHIVED'];
const channels: CampaignChannel[] = ['EMAIL', 'SMS', 'TELEGRAM', 'WHATSAPP'];

export function CampaignsPage() {
  const { t, locale, timeZone } = useI18n();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('CAMPAIGN_MANAGE');
  const { showToast } = useToast();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [createOpen, setCreateOpen] = useState(false);

  const query = useMemo(
    () => ({
      search: params.get('search')?.trim() || undefined,
      status: params.get('status') || undefined,
      channel: params.get('channel') || undefined,
      page: Math.max(0, Number(params.get('page') ?? '0') || 0),
      size: 50,
      sort: 'createdAt,desc',
    }),
    [params],
  );

  const campaigns = useQuery({
    ...campaignQueries.list(query),
    placeholderData: keepPreviousData,
  });

  const createMutation = useMutation({
    mutationFn: (command: CampaignSaveCommand) => createCampaign(command),
    onSuccess: async (campaign) => {
      await client.invalidateQueries({ queryKey: campaignKeys.lists() });
      setCreateOpen(false);
      showToast({ title: t('campaigns.created'), tone: 'success' });
      navigate(`/campaigns/${campaign.id}`);
    },
  });

  const updateParam = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    next.delete('page');
    setParams(next);
  };

  if (campaigns.error instanceof ApiError && campaigns.error.status === 403) {
    return <Navigate to="/forbidden" replace />;
  }

  const columns = [
    {
      key: 'name',
      header: t('campaigns.name'),
      render: (row: NonNullable<typeof campaigns.data>['items'][number]) => (
        <Link className="table-link" to={row.id}>
          <strong>{row.name}</strong>
        </Link>
      ),
    },
    {
      key: 'status',
      header: t('campaigns.status'),
      render: (row: NonNullable<typeof campaigns.data>['items'][number]) => (
        <StatusBadge
          tone={row.status === 'ACTIVE' ? 'success' : row.status === 'DRAFT' ? 'warning' : 'neutral'}
        >
          {row.status}
        </StatusBadge>
      ),
    },
    {
      key: 'channel',
      header: t('campaigns.channel'),
      render: (row: NonNullable<typeof campaigns.data>['items'][number]) => row.channel,
    },
    {
      key: 'schedule',
      header: t('campaigns.schedule'),
      render: (row: NonNullable<typeof campaigns.data>['items'][number]) =>
        row.scheduledAt ? formatInstant(row.scheduledAt, locale, timeZone) : t('campaigns.unscheduled'),
    },
    {
      key: 'updated',
      header: t('campaigns.updated'),
      render: (row: NonNullable<typeof campaigns.data>['items'][number]) =>
        formatInstant(row.updatedAt, locale, timeZone),
    },
    {
      key: 'open',
      header: '',
      render: (row: NonNullable<typeof campaigns.data>['items'][number]) => (
        <Link className="ui-button ui-button--secondary" to={row.id}>
          {t('campaigns.open')}
        </Link>
      ),
    },
  ];

  return (
    <div className="customers-page campaigns-page">
      <header className="customers-page__header">
        <div>
          <p className="eyebrow">{t('campaigns.eyebrow')}</p>
          <h1>{t('campaigns.title')}</h1>
          <p>{t('campaigns.description')}</p>
        </div>
        {canManage ? (
          <Button
            onClick={() => {
              createMutation.reset();
              setCreateOpen(true);
            }}
          >
            {t('campaigns.create')}
          </Button>
        ) : null}
      </header>

      {!canManage ? <Alert variant="info">{t('campaigns.noManagePermission')}</Alert> : null}

      <section className="customer-filters">
        <div className="customer-filters__quick">
          <FormField label={t('campaigns.search')}>
            <input
              type="search"
              value={params.get('search') ?? ''}
              onChange={(event) => updateParam('search', event.target.value)}
            />
          </FormField>
          <FormField label={t('campaigns.status')}>
            <select
              value={params.get('status') ?? ''}
              onChange={(event) => updateParam('status', event.target.value)}
            >
              <option value="">{t('campaigns.all')}</option>
              {statuses.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label={t('campaigns.channel')}>
            <select
              value={params.get('channel') ?? ''}
              onChange={(event) => updateParam('channel', event.target.value)}
            >
              <option value="">{t('campaigns.all')}</option>
              {channels.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </FormField>
        </div>
      </section>

      {campaigns.isLoading ? <Spinner label={t('campaigns.loading')} /> : null}
      {campaigns.error ? (
        <ProblemDetailPanel error={campaigns.error} onRetry={() => void campaigns.refetch()} />
      ) : null}
      {campaigns.data ? (
        <>
          <DataTable
            columns={columns}
            rows={campaigns.data.items}
            rowKey={(row) => row.id}
            emptyTitle={t('campaigns.empty')}
          />
          <Pagination
            page={campaigns.data.page}
            totalPages={campaigns.data.totalPages}
            onPageChange={(page) => {
              const next = new URLSearchParams(params);
              if (page === 0) next.delete('page');
              else next.set('page', String(page));
              setParams(next);
            }}
          />
        </>
      ) : null}

      <Dialog
        open={createOpen}
        title={t('campaigns.create')}
        closeLabel={t('customerEdit.close')}
        onClose={() => setCreateOpen(false)}
      >
        {createMutation.error ? <ProblemDetailPanel error={createMutation.error} /> : null}
        <CampaignForm
          pending={createMutation.isPending}
          submitLabel={t('campaigns.create')}
          onSubmit={(command) => createMutation.mutate(command)}
        />
      </Dialog>
    </div>
  );
}
