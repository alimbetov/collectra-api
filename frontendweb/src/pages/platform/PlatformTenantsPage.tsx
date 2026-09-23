import { useQuery } from '@tanstack/react-query';
import { FormEvent, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { platformQueries } from '../../entities/platform/api/platform.queries';
import type {
  PlatformTenantListItemDto,
  PlatformTenantListParams,
  PlatformTenantStatus,
} from '../../entities/platform/model/platform.types';
import { useI18n } from '../../shared/i18n/i18n-context';
import { formatInstant, formatNumber } from '../../shared/i18n/formatters';
import { Alert, Button, DataTable, Pagination, StatusBadge } from '../../shared/ui';

const PAGE_SIZE = 50;
const ALLOWED_SORTS = new Set([
  'createdAt,desc',
  'createdAt,asc',
  'updatedAt,desc',
  'name,asc',
  'name,desc',
  'slug,asc',
  'status,asc',
]);

function readStatus(value: string | null): PlatformTenantStatus | undefined {
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

export function PlatformTenantsPage() {
  const { t, locale, timeZone } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();
  const [searchDraft, setSearchDraft] = useState(searchParams.get('search') ?? '');

  const page = Math.max(0, Number(searchParams.get('page') ?? '0') || 0);
  const sortCandidate = searchParams.get('sort') ?? 'createdAt,desc';
  const sort = ALLOWED_SORTS.has(sortCandidate) ? sortCandidate : 'createdAt,desc';
  const status = readStatus(searchParams.get('status'));
  const createdFromDate = searchParams.get('createdFrom') ?? '';
  const createdToDate = searchParams.get('createdTo') ?? '';

  const params = useMemo<PlatformTenantListParams>(
    () => ({
      search: searchParams.get('search')?.trim() || undefined,
      status,
      createdFrom: dateStart(createdFromDate),
      createdTo: dateExclusiveEnd(createdToDate),
      page,
      size: PAGE_SIZE,
      sort,
    }),
    [createdFromDate, createdToDate, page, searchParams, sort, status],
  );

  const tenants = useQuery(platformQueries.tenants(params));

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
      key: 'tenant',
      header: t('platform.tenants.name'),
      render: (row: PlatformTenantListItemDto) => (
        <div>
          <Link to={row.id} className="table-link">
            <strong>{row.name}</strong>
          </Link>
          <div className="muted-text">{row.slug}</div>
        </div>
      ),
    },
    {
      key: 'status',
      header: t('platform.tenants.status'),
      render: (row: PlatformTenantListItemDto) => (
        <StatusBadge tone={row.status === 'ACTIVE' ? 'success' : 'danger'}>
          {row.status === 'ACTIVE'
            ? t('platform.tenants.statusActive')
            : t('platform.tenants.statusBlocked')}
        </StatusBadge>
      ),
    },
    {
      key: 'users',
      header: t('platform.tenants.activeUsers'),
      align: 'end' as const,
      render: (row: PlatformTenantListItemDto) => formatNumber(row.activeUsers, locale),
    },
    {
      key: 'campaigns',
      header: t('platform.tenants.campaigns30d'),
      align: 'end' as const,
      render: (row: PlatformTenantListItemDto) => formatNumber(row.campaignsLast30d, locale),
    },
    {
      key: 'messages',
      header: t('platform.tenants.messages30d'),
      align: 'end' as const,
      render: (row: PlatformTenantListItemDto) => formatNumber(row.messagesLast30d, locale),
    },
    {
      key: 'created',
      header: t('platform.tenants.createdAt'),
      render: (row: PlatformTenantListItemDto) => formatInstant(row.createdAt, locale, timeZone),
    },
    {
      key: 'open',
      header: '',
      align: 'end' as const,
      render: (row: PlatformTenantListItemDto) => (
        <Link to={row.id} className="ui-button ui-button--secondary">
          {t('platform.tenants.open')}
        </Link>
      ),
    },
  ];

  return (
    <div className="platform-tenants-page">
      <header className="dashboard-page__header">
        <div>
          <p className="eyebrow">{t('platform.eyebrow')}</p>
          <h1>{t('platform.tenants.title')}</h1>
          <p>{t('platform.tenants.description')}</p>
        </div>
        {tenants.data ? (
          <div className="platform-tenants-page__total">
            <span>{t('platform.tenants.total')}</span>
            <strong>{formatNumber(tenants.data.totalElements, locale)}</strong>
          </div>
        ) : null}
      </header>

      <form className="platform-tenant-filters" onSubmit={applySearch}>
        <label>
          <span>{t('platform.tenants.search')}</span>
          <input
            value={searchDraft}
            placeholder={t('platform.tenants.searchPlaceholder')}
            onChange={(event) => setSearchDraft(event.target.value)}
          />
        </label>
        <label>
          <span>{t('platform.tenants.status')}</span>
          <select
            value={status ?? ''}
            onChange={(event) =>
              updateParams({ status: event.target.value || undefined, page: undefined })
            }
          >
            <option value="">{t('platform.tenants.statusAll')}</option>
            <option value="ACTIVE">{t('platform.tenants.statusActive')}</option>
            <option value="BLOCKED">{t('platform.tenants.statusBlocked')}</option>
          </select>
        </label>
        <label>
          <span>Created from</span>
          <input
            type="date"
            value={createdFromDate}
            onChange={(event) =>
              updateParams({ createdFrom: event.target.value || undefined, page: undefined })
            }
          />
        </label>
        <label>
          <span>Created to</span>
          <input
            type="date"
            value={createdToDate}
            onChange={(event) =>
              updateParams({ createdTo: event.target.value || undefined, page: undefined })
            }
          />
        </label>
        <label>
          <span>Sort</span>
          <select
            value={sort}
            onChange={(event) => updateParams({ sort: event.target.value, page: undefined })}
          >
            <option value="createdAt,desc">Created ↓</option>
            <option value="createdAt,asc">Created ↑</option>
            <option value="updatedAt,desc">Updated ↓</option>
            <option value="name,asc">Name A–Z</option>
            <option value="name,desc">Name Z–A</option>
            <option value="slug,asc">Slug A–Z</option>
            <option value="status,asc">Status</option>
          </select>
        </label>
        <div className="platform-tenant-filters__actions">
          <Button type="submit">{t('platform.tenants.apply')}</Button>
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              setSearchDraft('');
              setSearchParams(new URLSearchParams());
            }}
          >
            {t('platform.tenants.clear')}
          </Button>
        </div>
      </form>

      {tenants.isLoading ? (
        <div className="platform-overview__state">{t('platform.tenants.loading')}</div>
      ) : tenants.error ? (
        <Alert variant="danger" title={t('error.title')}>
          {t('error.server')}
        </Alert>
      ) : tenants.data ? (
        <>
          <DataTable
            columns={columns}
            rows={tenants.data.items}
            rowKey={(row) => row.id}
            emptyTitle={t('platform.tenants.empty')}
            emptyDescription={t('platform.tenants.emptyDescription')}
          />
          <Pagination
            page={tenants.data.page}
            totalPages={tenants.data.totalPages}
            onPageChange={(nextPage) =>
              updateParams({ page: nextPage === 0 ? undefined : String(nextPage) })
            }
          />
        </>
      ) : null}
    </div>
  );
}
