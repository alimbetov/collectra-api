import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import {
  changePlatformMembershipStatus,
  revokePlatformMembershipSessions,
} from '../../entities/platform/api/platform.api';
import { platformKeys, platformQueries } from '../../entities/platform/api/platform.queries';
import type { PlatformSessionDto } from '../../entities/platform/model/platform.types';
import { ApiError } from '../../shared/api/http-client';
import { useI18n } from '../../shared/i18n/i18n-context';
import { formatInstant, formatNumber } from '../../shared/i18n/formatters';
import { Alert, Button, ConfirmDialog, DataTable, Pagination, StatusBadge } from '../../shared/ui';

type UserTab = 'profile' | 'roles' | 'sessions' | 'communication' | 'audit';

const tabs: Array<{
  value: UserTab;
  key:
    | 'platform.user.profile'
    | 'platform.user.roles'
    | 'platform.user.sessions'
    | 'platform.user.communication'
    | 'platform.user.audit';
}> = [
  { value: 'profile', key: 'platform.user.profile' },
  { value: 'roles', key: 'platform.user.roles' },
  { value: 'sessions', key: 'platform.user.sessions' },
  { value: 'communication', key: 'platform.user.communication' },
  { value: 'audit', key: 'platform.user.audit' },
];

function readTab(value: string | null): UserTab {
  return tabs.some((tab) => tab.value === value) ? (value as UserTab) : 'profile';
}

export function PlatformUserDetailPage() {
  const { userId = '' } = useParams();
  const { t, locale, timeZone } = useI18n();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const user = useQuery(platformQueries.user(userId));
  const [sessionPage, setSessionPage] = useState(0);
  const tab = readTab(searchParams.get('tab'));
  const sessions = useQuery(
    platformQueries.membershipSessions(user.data?.membershipId ?? '', sessionPage, 50),
  );

  const [dialog, setDialog] = useState<'status' | 'sessions' | null>(null);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState(false);
  const [operationError, setOperationError] = useState<string | null>(null);

  const statusMutation = useMutation({
    mutationFn: async () => {
      if (!user.data) throw new Error('User is not loaded');
      return changePlatformMembershipStatus(user.data.membershipId, {
        active: user.data.membershipStatus === 'BLOCKED',
        revision: user.data.revision,
        reason: reason.trim(),
      });
    },
    onSuccess: async (updated) => {
      setDialog(null);
      setReason('');
      setOperationError(null);
      queryClient.setQueryData(platformKeys.userDetail(updated.userId), updated);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: platformKeys.users() }),
        queryClient.invalidateQueries({ queryKey: platformKeys.overview() }),
        queryClient.invalidateQueries({
          queryKey: platformKeys.membershipSessions(updated.membershipId, sessionPage, 50),
        }),
      ]);
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.problem?.code === 'VERSION_CONFLICT') {
        setOperationError(t('platform.user.versionConflict'));
        await user.refetch();
        return;
      }
      if (error instanceof ApiError && error.problem?.code === 'LAST_ACTIVE_TENANT_ADMIN') {
        setOperationError(t('platform.user.lastAdmin'));
        return;
      }
      setOperationError(t('platform.user.lifecycleError'));
    },
  });

  const revokeMutation = useMutation({
    mutationFn: async () => {
      if (!user.data) throw new Error('User is not loaded');
      return revokePlatformMembershipSessions(user.data.membershipId, reason.trim());
    },
    onSuccess: async (updated) => {
      setDialog(null);
      setReason('');
      setOperationError(null);
      queryClient.setQueryData(platformKeys.userDetail(updated.userId), updated);
      await queryClient.invalidateQueries({
        queryKey: platformKeys.users(),
      });
      await queryClient.invalidateQueries({
        queryKey: platformKeys.membershipSessions(updated.membershipId, sessionPage, 50),
      });
    },
    onError: () => setOperationError(t('platform.user.sessionsError')),
  });

  if (user.isLoading) {
    return <div className="platform-overview__state">{t('platform.user.loading')}</div>;
  }

  if (user.error || !user.data) {
    return (
      <Alert variant="danger" title={t('error.title')}>
        {t('error.server')}
      </Alert>
    );
  }

  const data = user.data;
  const activating = data.membershipStatus === 'BLOCKED';
  const pending = statusMutation.isPending || revokeMutation.isPending;

  const submitDialog = () => {
    if (!reason.trim()) {
      setReasonError(true);
      return;
    }
    setReasonError(false);
    if (dialog === 'status') statusMutation.mutate();
    if (dialog === 'sessions') revokeMutation.mutate();
  };

  const sessionColumns = [
    {
      key: 'created',
      header: t('platform.user.sessionCreated'),
      render: (row: PlatformSessionDto) => formatInstant(row.createdAt, locale, timeZone),
    },
    {
      key: 'expires',
      header: t('platform.user.sessionExpires'),
      render: (row: PlatformSessionDto) => formatInstant(row.expiresAt, locale, timeZone),
    },
    {
      key: 'status',
      header: t('platform.user.sessionStatus'),
      render: (row: PlatformSessionDto) => (
        <StatusBadge tone={row.revokedAt ? 'danger' : 'success'}>
          {row.revokedAt ? t('platform.user.sessionRevoked') : t('platform.user.sessionActive')}
        </StatusBadge>
      ),
    },
    {
      key: 'used',
      header: t('platform.user.sessionLastUsed'),
      render: (row: PlatformSessionDto) =>
        row.lastUsedAt ? formatInstant(row.lastUsedAt, locale, timeZone) : '—',
    },
    {
      key: 'agent',
      header: t('platform.user.sessionAgent'),
      render: (row: PlatformSessionDto) => row.userAgent || '—',
    },
    {
      key: 'ip',
      header: t('platform.user.sessionIp'),
      render: (row: PlatformSessionDto) => row.sourceIp || '—',
    },
  ];

  return (
    <div className="platform-user-detail">
      <Link to="/platform/users" className="table-link">
        ← {t('platform.user.back')}
      </Link>

      <header className="dashboard-page__header platform-tenant-detail__header">
        <div>
          <p className="eyebrow">{t('platform.user.title')}</p>
          <h1>{data.displayName || data.email}</h1>
          <p>
            {data.email} · {data.tenantName} ({data.tenantSlug})
          </p>
        </div>
        <div className="platform-tenant-detail__actions">
          <StatusBadge tone={data.effectiveAccessStatus === 'ACTIVE' ? 'success' : 'danger'}>
            {data.effectiveAccessStatus === 'ACTIVE'
              ? t('platform.users.statusActive')
              : t('platform.users.statusBlocked')}
          </StatusBadge>
          <Button
            variant={activating ? 'primary' : 'danger'}
            onClick={() => {
              setDialog('status');
              setReason('');
              setReasonError(false);
              setOperationError(null);
            }}
          >
            {activating ? t('platform.user.activate') : t('platform.user.block')}
          </Button>
          <Button
            variant="secondary"
            onClick={() => {
              setDialog('sessions');
              setReason('');
              setReasonError(false);
              setOperationError(null);
            }}
          >
            {t('platform.user.revokeSessions')}
          </Button>
        </div>
      </header>

      {operationError ? (
        <Alert variant="danger" title={t('error.title')}>
          {operationError}
        </Alert>
      ) : null}

      <nav className="platform-tenant-tabs" aria-label={t('platform.user.title')}>
        {tabs.map((item) => (
          <button
            key={item.value}
            type="button"
            className={tab === item.value ? 'active' : ''}
            onClick={() => {
              const next = new URLSearchParams(searchParams);
              if (item.value === 'profile') next.delete('tab');
              else next.set('tab', item.value);
              setSearchParams(next);
            }}
          >
            {t(item.key)}
          </button>
        ))}
      </nav>

      {tab === 'profile' ? (
        <section className="platform-overview__section">
          <dl className="platform-tenant-metadata">
            <Meta label={t('platform.users.tenant')} value={data.tenantName} />
            <Meta label={t('platform.users.accountStatus')} value={data.accountStatus} />
            <Meta label={t('platform.users.membershipStatus')} value={data.membershipStatus} />
            <Meta label={t('platform.user.effectiveStatus')} value={data.effectiveAccessStatus} />
            <Meta label={t('platform.user.locale')} value={data.locale || '—'} />
            <Meta label={t('platform.user.timezone')} value={data.timezone || '—'} />
            <Meta
              label={t('platform.user.authorizationVersion')}
              value={String(data.authorizationVersion)}
            />
            <Meta label={t('platform.user.revision')} value={String(data.revision)} />
            <Meta
              label={t('platform.user.createdAt')}
              value={formatInstant(data.createdAt, locale, timeZone)}
            />
            <Meta
              label={t('platform.user.updatedAt')}
              value={formatInstant(data.updatedAt, locale, timeZone)}
            />
          </dl>
        </section>
      ) : null}

      {tab === 'roles' ? (
        <section className="platform-overview__section">
          <h2>{t('platform.user.roles')}</h2>
          <div className="platform-role-list">
            {data.roleCodes.length ? data.roleCodes.map((role) => <code key={role}>{role}</code>) : '—'}
          </div>
        </section>
      ) : null}

      {tab === 'sessions' ? (
        <section className="platform-overview__section">
          <div className="dashboard-page__header">
            <div>
              <h2>{t('platform.user.sessions')}</h2>
              <p>
                {t('platform.user.sessionSummary')
                  .replace('{active}', formatNumber(data.activeSessionCount, locale))
                  .replace('{total}', formatNumber(data.sessionCount, locale))}
              </p>
            </div>
          </div>
          {sessions.isLoading ? (
            <div className="platform-overview__state">{t('platform.user.sessionsLoading')}</div>
          ) : sessions.error ? (
            <Alert variant="danger" title={t('error.title')}>
              {t('error.server')}
            </Alert>
          ) : sessions.data ? (
            <>
              <DataTable
                columns={sessionColumns}
                rows={sessions.data.items}
                rowKey={(row) => row.id}
                emptyTitle={t('platform.user.sessionsEmpty')}
              />
              <Pagination
                page={sessions.data.page}
                totalPages={sessions.data.totalPages}
                onPageChange={setSessionPage}
              />
            </>
          ) : null}
        </section>
      ) : null}

      {tab === 'communication' || tab === 'audit' ? (
        <div className="ui-empty-state">{t('platform.user.sectionDeferred')}</div>
      ) : null}

      <ConfirmDialog
        open={dialog !== null}
        title={
          dialog === 'sessions'
            ? t('platform.user.revokeSessionsTitle')
            : activating
              ? t('platform.user.activateTitle')
              : t('platform.user.blockTitle')
        }
        confirmLabel={
          dialog === 'sessions'
            ? t('platform.user.revokeSessions')
            : activating
              ? t('platform.user.activate')
              : t('platform.user.block')
        }
        cancelLabel={t('platform.user.cancel')}
        closeLabel={t('platform.user.close')}
        pending={pending}
        destructive={dialog === 'sessions' || !activating}
        onCancel={() => setDialog(null)}
        onConfirm={submitDialog}
      >
        <p>
          {dialog === 'sessions'
            ? t('platform.user.revokeSessionsDescription')
            : activating
              ? t('platform.user.activateDescription')
              : t('platform.user.blockDescription')}
        </p>
        <label className="ui-form-field">
          <span className="ui-form-field__label">{t('platform.user.reason')}</span>
          <textarea
            value={reason}
            maxLength={255}
            aria-invalid={reasonError || undefined}
            onChange={(event) => {
              setReason(event.target.value);
              if (event.target.value.trim()) setReasonError(false);
            }}
          />
          {reasonError ? (
            <span className="ui-form-field__error">{t('platform.user.reasonRequired')}</span>
          ) : null}
        </label>
      </ConfirmDialog>
    </div>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
