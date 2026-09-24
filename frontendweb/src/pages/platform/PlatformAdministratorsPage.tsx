import { useQuery } from '@tanstack/react-query';
import { FormEvent, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { platformQueries } from '../../entities/platform/api/platform.queries';
import type {
  PlatformAccountStatus,
  PlatformAdministratorDto,
  PlatformAdministratorListParams,
} from '../../entities/platform/model/platform.types';
import { useI18n } from '../../shared/i18n/i18n-context';
import { formatInstant, formatNumber } from '../../shared/i18n/formatters';
import { Alert, Button, DataTable, Pagination, StatusBadge } from '../../shared/ui';

const PAGE_SIZE = 50;
const ALLOWED_SORTS = new Set([
  'email,asc',
  'email,desc',
  'createdAt,desc',
  'createdAt,asc',
  'updatedAt,desc',
  'status,asc',
]);

function readStatus(value: string | null): PlatformAccountStatus | undefined {
  return value === 'ACTIVE' || value === 'BLOCKED' ? value : undefined;
}

export function PlatformAdministratorsPage() {
  const { t, locale, timeZone } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();
  const [searchDraft, setSearchDraft] = useState(searchParams.get('search') ?? '');

  const page = Math.max(0, Number(searchParams.get('page') ?? '0') || 0);
  const status = readStatus(searchParams.get('status'));
  const sortCandidate = searchParams.get('sort') ?? 'email,asc';
  const sort = ALLOWED_SORTS.has(sortCandidate) ? sortCandidate : 'email,asc';

  const params = useMemo<PlatformAdministratorListParams>(
    () => ({
      search: searchParams.get('search')?.trim() || undefined,
      status,
      page,
      size: PAGE_SIZE,
      sort,
    }),
    [page, searchParams, sort, status],
  );

  const administrators = useQuery(platformQueries.administrators(params));

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
      key: 'email',
      header: t('platform.administrators.email'),
      render: (row: PlatformAdministratorDto) => <strong>{row.email}</strong>,
    },
    {
      key: 'status',
      header: t('platform.administrators.status'),
      render: (row: PlatformAdministratorDto) => (
        <StatusBadge tone={row.status === 'ACTIVE' ? 'success' : 'danger'}>
          {row.status === 'ACTIVE'
            ? t('platform.users.statusActive')
            : t('platform.users.statusBlocked')}
        </StatusBadge>
      ),
    },
    {
      key: 'authVersion',
      header: t('platform.administrators.authorizationVersion'),
      align: 'end' as const,
      render: (row: PlatformAdministratorDto) => formatNumber(row.authorizationVersion, locale),
    },
    {
      key: 'revision',
      header: t('platform.administrators.revision'),
      align: 'end' as const,
      render: (row: PlatformAdministratorDto) => formatNumber(row.revision, locale),
    },
    {
      key: 'created',
      header: t('platform.administrators.createdAt'),
      render: (row: PlatformAdministratorDto) => formatInstant(row.createdAt, locale, timeZone),
    },
    {
      key: 'updated',
      header: t('platform.administrators.updatedAt'),
      render: (row: PlatformAdministratorDto) => formatInstant(row.updatedAt, locale, timeZone),
    },
  ];

  return (
    <div className="platform-administrators-page">
      <header className="dashboard-page__header">
        <div>
          <p className="eyebrow">{t('platform.eyebrow')}</p>
          <h1>{t('platform.administrators.title')}</h1>
          <p>{t('platform.administrators.description')}</p>
        </div>
        {administrators.data ? (
          <div className="platform-tenants-page__total">
            <span>{t('platform.administrators.total')}</span>
            <strong>{formatNumber(administrators.data.totalElements, locale)}</strong>
          </div>
        ) : null}
      </header>

      <form className="platform-tenant-filters" onSubmit={applySearch}>
        <label>
          <span>{t('platform.administrators.search')}</span>
          <input
            value={searchDraft}
            placeholder={t('platform.administrators.searchPlaceholder')}
            onChange={(event) => setSearchDraft(event.target.value)}
          />
        </label>
        <label>
          <span>{t('platform.administrators.status')}</span>
          <select
            value={status ?? ''}
            onChange={(event) =>
              updateParams({ status: event.target.value || undefined, page: undefined })
            }
          >
            <option value="">{t('platform.users.statusAll')}</option>
            <option value="ACTIVE">{t('platform.users.statusActive')}</option>
            <option value="BLOCKED">{t('platform.users.statusBlocked')}</option>
          </select>
        </label>
        <label>
          <span>{t('platform.administrators.sort')}</span>
          <select
            value={sort}
            onChange={(event) => updateParams({ sort: event.target.value, page: undefined })}
          >
            <option value="email,asc">{t('platform.administrators.sortEmailAsc')}</option>
            <option value="email,desc">{t('platform.administrators.sortEmailDesc')}</option>
            <option value="createdAt,desc">{t('platform.administrators.sortCreatedDesc')}</option>
            <option value="createdAt,asc">{t('platform.administrators.sortCreatedAsc')}</option>
            <option value="updatedAt,desc">{t('platform.administrators.sortUpdatedDesc')}</option>
            <option value="status,asc">{t('platform.administrators.sortStatusAsc')}</option>
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

      {administrators.isLoading ? (
        <div className="platform-overview__state">{t('platform.administrators.loading')}</div>
      ) : administrators.error ? (
        <Alert variant="danger" title={t('error.title')}>
          {t('error.server')}
        </Alert>
      ) : administrators.data ? (
        <>
          <DataTable
            columns={columns}
            rows={administrators.data.items}
            rowKey={(row) => row.id}
            emptyTitle={t('platform.administrators.empty')}
            emptyDescription={t('platform.administrators.emptyDescription')}
          />
          <Pagination
            page={administrators.data.page}
            totalPages={administrators.data.totalPages}
            onPageChange={(nextPage) =>
              updateParams({ page: nextPage === 0 ? undefined : String(nextPage) })
            }
          />
        </>
      ) : null}
    </div>
  );
}
