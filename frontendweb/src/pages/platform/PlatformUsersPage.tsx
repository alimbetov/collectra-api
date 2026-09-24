import { useQuery } from '@tanstack/react-query';
import { FormEvent, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { platformQueries } from '../../entities/platform/api/platform.queries';
import type {
  PlatformAccountStatus,
  PlatformMembershipStatus,
  PlatformUserListItemDto,
  PlatformUserListParams,
} from '../../entities/platform/model/platform.types';
import { useI18n } from '../../shared/i18n/i18n-context';
import { formatInstant, formatNumber } from '../../shared/i18n/formatters';
import { Alert, Button, DataTable, Pagination, StatusBadge } from '../../shared/ui';

const PAGE_SIZE = 50;
const ALLOWED_SORTS = new Set([
  'createdAt,desc',
  'createdAt,asc',
  'updatedAt,desc',
  'email,asc',
  'email,desc',
  'displayName,asc',
  'tenantSlug,asc',
  'membershipStatus,asc',
  'accountStatus,asc',
]);

function readStatus(value: string | null): PlatformAccountStatus | undefined {
  return value === 'ACTIVE' || value === 'BLOCKED' ? value : undefined;
}

function dateStart(value: string | null): string | undefined {
  return value ? `${value}T00:00:00.000Z` : undefined;
}

function dateExclusiveEnd(value: string | null): string | undefined {
  if (!value) return undefined;
  const date = new Date(`${value}T00:00:00.000Z`);
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString();
}

export function PlatformUsersPage() {
  const { t, locale, timeZone } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();
  const [searchDraft, setSearchDraft] = useState(searchParams.get('search') ?? '');

  const page = Math.max(0, Number(searchParams.get('page') ?? '0') || 0);
  const sortCandidate = searchParams.get('sort') ?? 'createdAt,desc';
  const sort = ALLOWED_SORTS.has(sortCandidate) ? sortCandidate : 'createdAt,desc';
  const membershipStatus = readStatus(searchParams.get('membershipStatus')) as
    | PlatformMembershipStatus
    | undefined;
  const accountStatus = readStatus(searchParams.get('accountStatus'));
  const createdFromDate = searchParams.get('createdFrom') ?? '';
  const createdToDate = searchParams.get('createdTo') ?? '';

  const params = useMemo<PlatformUserListParams>(
    () => ({
      search: searchParams.get('search')?.trim() || undefined,
      tenantSlug: searchParams.get('tenantSlug')?.trim() || undefined,
      membershipStatus,
      accountStatus,
      roleCode: searchParams.get('roleCode')?.trim() || undefined,
      createdFrom: dateStart(createdFromDate),
      createdTo: dateExclusiveEnd(createdToDate),
      page,
      size: PAGE_SIZE,
      sort,
    }),
    [
      accountStatus,
      createdFromDate,
      createdToDate,
      membershipStatus,
      page,
      searchParams,
      sort,
    ],
  );

  const users = useQuery(platformQueries.users(params));

  const updateParams = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    setSearchParams(next);
  };

  const applySearch = (event: FormEvent) => {
    event.preventDefault();
    updateParams({ search: searchDraft.trim() || undefined, page: undefined });
  };

  const columns = [
    {
      key: 'user',
      header: t('platform.users.user'),
      render: (row: PlatformUserListItemDto) => (
        <div>
          <Link to={row.userId} className="table-link">
            <strong>{row.displayName || row.email}</strong>
          </Link>
          <div className="muted-text">{row.email}</div>
        </div>
      ),
    },
    {
      key: 'tenant',
      header: t('platform.users.tenant'),
      render: (row: PlatformUserListItemDto) => (
        <div>
          <strong>{row.tenantName}</strong>
          <div className="muted-text">{row.tenantSlug}</div>
        </div>
      ),
    },
    {
      key: 'membership',
      header: t('platform.users.membershipStatus'),
      render: (row: PlatformUserListItemDto) => (
        <StatusBadge tone={row.membershipStatus === 'ACTIVE' ? 'success' : 'danger'}>
          {row.membershipStatus === 'ACTIVE'
            ? t('platform.users.statusActive')
            : t('platform.users.statusBlocked')}
        </StatusBadge>
      ),
    },
    {
      key: 'account',
      header: t('platform.users.accountStatus'),
      render: (row: PlatformUserListItemDto) => (
        <StatusBadge tone={row.accountStatus === 'ACTIVE' ? 'success' : 'danger'}>
          {row.accountStatus === 'ACTIVE'
            ? t('platform.users.statusActive')
            : t('platform.users.statusBlocked')}
        </StatusBadge>
      ),
    },
    {
      key: 'roles',
      header: t('platform.users.roles'),
      render: (row: PlatformUserListItemDto) => row.roleCodes.join(', ') || '—',
    },
    {
      key: 'created',
      header: t('platform.users.createdAt'),
      render: (row: PlatformUserListItemDto) => formatInstant(row.createdAt, locale, timeZone),
    },
    {
      key: 'open',
      header: '',
      align: 'end' as const,
      render: (row: PlatformUserListItemDto) => (
        <Link to={row.userId} className="ui-button ui-button--secondary">
          {t('platform.users.open')}
        </Link>
      ),
    },
  ];

  return (
    <div className="platform-users-page">
      <header className="dashboard-page__header">
        <div>
          <p className="eyebrow">{t('platform.eyebrow')}</p>
          <h1>{t('platform.users.title')}</h1>
          <p>{t('platform.users.description')}</p>
        </div>
        {users.data ? (
          <div className="platform-tenants-page__total">
            <span>{t('platform.users.total')}</span>
            <strong>{formatNumber(users.data.totalElements, locale)}</strong>
          </div>
        ) : null}
      </header>

      <form className="platform-tenant-filters" onSubmit={applySearch}>
        <label>
          <span>{t('platform.users.search')}</span>
          <input
            value={searchDraft}
            placeholder={t('platform.users.searchPlaceholder')}
            onChange={(event) => setSearchDraft(event.target.value)}
          />
        </label>
        <label>
          <span>{t('platform.users.tenantSlug')}</span>
          <input
            value={searchParams.get('tenantSlug') ?? ''}
            onChange={(event) =>
              updateParams({ tenantSlug: event.target.value || undefined, page: undefined })
            }
          />
        </label>
        <label>
          <span>{t('platform.users.membershipStatus')}</span>
          <select
            value={membershipStatus ?? ''}
            onChange={(event) =>
              updateParams({
                membershipStatus: event.target.value || undefined,
                page: undefined,
              })
            }
          >
            <option value="">{t('platform.users.statusAll')}</option>
            <option value="ACTIVE">{t('platform.users.statusActive')}</option>
            <option value="BLOCKED">{t('platform.users.statusBlocked')}</option>
          </select>
        </label>
        <label>
          <span>{t('platform.users.accountStatus')}</span>
          <select
            value={accountStatus ?? ''}
            onChange={(event) =>
              updateParams({ accountStatus: event.target.value || undefined, page: undefined })
            }
          >
            <option value="">{t('platform.users.statusAll')}</option>
            <option value="ACTIVE">{t('platform.users.statusActive')}</option>
            <option value="BLOCKED">{t('platform.users.statusBlocked')}</option>
          </select>
        </label>
        <label>
          <span>{t('platform.users.role')}</span>
          <input
            value={searchParams.get('roleCode') ?? ''}
            placeholder="TENANT_ADMIN"
            onChange={(event) =>
              updateParams({ roleCode: event.target.value || undefined, page: undefined })
            }
          />
        </label>
        <label>
          <span>{t('platform.users.createdFrom')}</span>
          <input
            type="date"
            value={createdFromDate}
            onChange={(event) =>
              updateParams({ createdFrom: event.target.value || undefined, page: undefined })
            }
          />
        </label>
        <label>
          <span>{t('platform.users.createdTo')}</span>
          <input
            type="date"
            value={createdToDate}
            onChange={(event) =>
              updateParams({ createdTo: event.target.value || undefined, page: undefined })
            }
          />
        </label>
        <label>
          <span>{t('platform.users.sort')}</span>
          <select
            value={sort}
            onChange={(event) => updateParams({ sort: event.target.value, page: undefined })}
          >
            <option value="createdAt,desc">{t('platform.users.sortCreatedDesc')}</option>
            <option value="createdAt,asc">{t('platform.users.sortCreatedAsc')}</option>
            <option value="updatedAt,desc">{t('platform.users.sortUpdatedDesc')}</option>
            <option value="email,asc">{t('platform.users.sortEmailAsc')}</option>
            <option value="email,desc">{t('platform.users.sortEmailDesc')}</option>
            <option value="tenantSlug,asc">{t('platform.users.sortTenantAsc')}</option>
          </select>
        </label>
        <div className="platform-tenant-filters__actions">
          <Button type="submit">{t('platform.users.apply')}</Button>
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              setSearchDraft('');
              setSearchParams(new URLSearchParams());
            }}
          >
            {t('platform.users.clear')}
          </Button>
        </div>
      </form>

      {users.isLoading ? (
        <div className="platform-overview__state">{t('platform.users.loading')}</div>
      ) : users.error ? (
        <Alert variant="danger" title={t('error.title')}>
          {t('error.server')}
        </Alert>
      ) : users.data ? (
        <>
          <DataTable
            columns={columns}
            rows={users.data.items}
            rowKey={(row) => row.membershipId}
            emptyTitle={t('platform.users.empty')}
            emptyDescription={t('platform.users.emptyDescription')}
          />
          <Pagination
            page={users.data.page}
            totalPages={users.data.totalPages}
            onPageChange={(nextPage) =>
              updateParams({ page: nextPage === 0 ? undefined : String(nextPage) })
            }
          />
        </>
      ) : null}
    </div>
  );
}
