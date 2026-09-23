import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { changePlatformTenantStatus } from '../../entities/platform/api/platform.api';
import { platformKeys, platformQueries } from '../../entities/platform/api/platform.queries';
import { ApiError } from '../../shared/api/http-client';
import { useI18n } from '../../shared/i18n/i18n-context';
import { formatInstant, formatNumber } from '../../shared/i18n/formatters';
import { Alert, Button, ConfirmDialog, StatusBadge } from '../../shared/ui';

type TenantTab = 'overview' | 'users' | 'communication' | 'usage' | 'audit';

const tabs: Array<{ value: TenantTab; key: 'platform.tenant.overview' | 'platform.tenant.usersTab' | 'platform.tenant.communicationTab' | 'platform.tenant.usageTab' | 'platform.tenant.auditTab' }> = [
  { value: 'overview', key: 'platform.tenant.overview' },
  { value: 'users', key: 'platform.tenant.usersTab' },
  { value: 'communication', key: 'platform.tenant.communicationTab' },
  { value: 'usage', key: 'platform.tenant.usageTab' },
  { value: 'audit', key: 'platform.tenant.auditTab' },
];

function readTab(value: string | null): TenantTab {
  return tabs.some((tab) => tab.value === value) ? (value as TenantTab) : 'overview';
}

export function PlatformTenantDetailPage() {
  const { tenantId = '' } = useParams();
  const { t, locale, timeZone } = useI18n();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const tenant = useQuery(platformQueries.tenant(tenantId));
  const [dialogOpen, setDialogOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState(false);
  const [operationError, setOperationError] = useState<string | null>(null);
  const tab = readTab(searchParams.get('tab'));

  const mutation = useMutation({
    mutationFn: async (active: boolean) => {
      const current = tenant.data;
      if (!current) throw new Error('Tenant is not loaded');
      return changePlatformTenantStatus(current.id, {
        active,
        revision: current.revision,
        reason: reason.trim(),
      });
    },
    onSuccess: async (updated) => {
      setDialogOpen(false);
      setReason('');
      setOperationError(null);
      queryClient.setQueryData(platformKeys.tenantDetail(updated.id), updated);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: platformKeys.tenants() }),
        queryClient.invalidateQueries({ queryKey: platformKeys.overview() }),
      ]);
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.problem?.code === 'VERSION_CONFLICT') {
        setOperationError(t('platform.tenant.versionConflict'));
        await tenant.refetch();
        return;
      }
      setOperationError(t('platform.tenant.lifecycleError'));
    },
  });

  if (tenant.isLoading) {
    return <div className="platform-overview__state">{t('platform.tenant.loading')}</div>;
  }

  if (tenant.error || !tenant.data) {
    return (
      <Alert variant="danger" title={t('error.title')}>
        {t('error.server')}
      </Alert>
    );
  }

  const data = tenant.data;
  const activating = data.status === 'BLOCKED';

  const submitLifecycle = () => {
    if (!reason.trim()) {
      setReasonError(true);
      return;
    }
    setReasonError(false);
    mutation.mutate(activating);
  };

  return (
    <div className="platform-tenant-detail">
      <Link to="/platform/tenants" className="table-link">
        ← {t('platform.tenant.back')}
      </Link>

      <header className="dashboard-page__header platform-tenant-detail__header">
        <div>
          <p className="eyebrow">{t('platform.tenant.title')}</p>
          <h1>{data.name}</h1>
          <p>{data.slug}</p>
        </div>
        <div className="platform-tenant-detail__actions">
          <StatusBadge tone={data.status === 'ACTIVE' ? 'success' : 'danger'}>
            {data.status === 'ACTIVE'
              ? t('platform.tenants.statusActive')
              : t('platform.tenants.statusBlocked')}
          </StatusBadge>
          <Button
            variant={data.status === 'ACTIVE' ? 'danger' : 'primary'}
            onClick={() => {
              setReason('');
              setReasonError(false);
              setOperationError(null);
              setDialogOpen(true);
            }}
          >
            {data.status === 'ACTIVE' ? t('platform.tenant.block') : t('platform.tenant.activate')}
          </Button>
        </div>
      </header>

      {operationError ? (
        <Alert variant="danger" title={t('error.title')}>
          {operationError}
        </Alert>
      ) : null}

      <nav className="platform-tenant-tabs" aria-label={t('platform.tenant.title')}>
        {tabs.map((item) => (
          <button
            key={item.value}
            type="button"
            className={tab === item.value ? 'active' : ''}
            onClick={() => {
              const next = new URLSearchParams(searchParams);
              if (item.value === 'overview') next.delete('tab');
              else next.set('tab', item.value);
              setSearchParams(next);
            }}
          >
            {t(item.key)}
          </button>
        ))}
      </nav>

      {tab === 'overview' ? (
        <>
          <section className="platform-kpi-grid">
            <Kpi label={t('platform.tenant.status')} value={data.status} />
            <Kpi label={t('platform.tenants.activeUsers')} value={formatNumber(data.activeUsers, locale)} />
            <Kpi label={t('platform.tenant.blockedUsers')} value={formatNumber(data.blockedUsers, locale)} />
            <Kpi label={t('platform.tenant.customers')} value={formatNumber(data.customers, locale)} />
            <Kpi label={t('platform.tenant.campaigns')} value={formatNumber(data.campaigns, locale)} />
            <Kpi label={t('platform.tenant.messages')} value={formatNumber(data.messages, locale)} />
            <Kpi label={t('platform.tenant.documents')} value={formatNumber(data.generatedDocuments, locale)} />
            <Kpi label={t('platform.tenant.files')} value={formatNumber(data.files, locale)} />
          </section>
          <section className="platform-overview__section">
            <dl className="platform-tenant-metadata">
              <div>
                <dt>{t('platform.tenant.revision')}</dt>
                <dd>{data.revision}</dd>
              </div>
              <div>
                <dt>{t('platform.tenants.createdAt')}</dt>
                <dd>{formatInstant(data.createdAt, locale, timeZone)}</dd>
              </div>
              <div>
                <dt>{t('platform.tenant.updatedAt')}</dt>
                <dd>{formatInstant(data.updatedAt, locale, timeZone)}</dd>
              </div>
            </dl>
          </section>
        </>
      ) : (
        <div className="ui-empty-state">{t('platform.tenant.sectionDeferred')}</div>
      )}

      <ConfirmDialog
        open={dialogOpen}
        title={activating ? t('platform.tenant.activateTitle') : t('platform.tenant.blockTitle')}
        confirmLabel={activating ? t('platform.tenant.activate') : t('platform.tenant.block')}
        cancelLabel={t('platform.tenant.cancel')}
        closeLabel={t('platform.tenant.close')}
        pending={mutation.isPending}
        destructive={!activating}
        onCancel={() => setDialogOpen(false)}
        onConfirm={submitLifecycle}
      >
        <p>
          {activating
            ? t('platform.tenant.activateDescription')
            : t('platform.tenant.blockDescription')}
        </p>
        <label className="ui-form-field">
          <span className="ui-form-field__label">{t('platform.tenant.reason')}</span>
          <textarea
            value={reason}
            maxLength={255}
            aria-invalid={reasonError || undefined}
            placeholder={t('platform.tenant.reasonPlaceholder')}
            onChange={(event) => {
              setReason(event.target.value);
              if (event.target.value.trim()) setReasonError(false);
            }}
          />
          {reasonError ? (
            <span className="ui-form-field__error">{t('platform.tenant.reasonRequired')}</span>
          ) : null}
        </label>
      </ConfirmDialog>
    </div>
  );
}

function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <article className="platform-kpi">
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}
