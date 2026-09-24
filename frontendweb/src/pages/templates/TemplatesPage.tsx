import { useQuery } from '@tanstack/react-query';
import { FormEvent, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { templateQueries } from '../../entities/template/api/template.queries';
import type {
  TemplateChannel,
  TemplateListItemDto,
  TemplateListParams,
} from '../../entities/template/model/template.types';
import { useAuth } from '../../features/auth/model/auth-context';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { formatInstant } from '../../shared/i18n/formatters';
import { useI18n } from '../../shared/i18n/i18n-context';
import { Button, DataTable, EmptyState, Pagination, Spinner, StatusBadge } from '../../shared/ui';

const PAGE_SIZE = 50;
const CHANNELS: TemplateChannel[] = ['EMAIL', 'PDF', 'SMS', 'WHATSAPP', 'TELEGRAM'];

export function TemplatesPage() {
  const { t, locale, timeZone } = useI18n();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('TEMPLATE_MANAGE');
  const [searchParams, setSearchParams] = useSearchParams();
  const [searchDraft, setSearchDraft] = useState(searchParams.get('search') ?? '');

  const page = Math.max(0, Number(searchParams.get('page') ?? '0') || 0);
  const channel = readChannel(searchParams.get('channel'));
  const sort = readSort(searchParams.get('sort'));

  const params = useMemo<TemplateListParams>(
    () => ({
      search: searchParams.get('search')?.trim() || undefined,
      channel,
      status: searchParams.get('status')?.trim() || undefined,
      locale: searchParams.get('locale')?.trim() || undefined,
      createdFrom: dateStart(searchParams.get('createdFrom')),
      createdTo: dateExclusiveEnd(searchParams.get('createdTo')),
      page,
      size: PAGE_SIZE,
      sort,
    }),
    [channel, page, searchParams, sort],
  );

  const query = useQuery(templateQueries.list(params));

  const update = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    setSearchParams(next);
  };

  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    update({ search: searchDraft.trim() || undefined, page: undefined });
  };

  const columns = [
    {
      key: 'name',
      header: t('templates.name'),
      render: (row: TemplateListItemDto) => (
        <div>
          <Link className="table-link" to={row.id}>
            <strong>{row.name}</strong>
          </Link>
          <div className="muted-text">{row.code}</div>
        </div>
      ),
    },
    {
      key: 'documentType',
      header: t('templates.documentType'),
      render: (row: TemplateListItemDto) => row.documentType,
    },
    {
      key: 'status',
      header: t('templates.status'),
      render: (row: TemplateListItemDto) => (
        <StatusBadge tone={row.status === 'ACTIVE' ? 'success' : 'neutral'}>
          {row.status}
        </StatusBadge>
      ),
    },
    {
      key: 'updatedAt',
      header: t('templates.updatedAt'),
      render: (row: TemplateListItemDto) => formatInstant(row.updatedAt, locale, timeZone),
    },
    {
      key: 'open',
      header: '',
      align: 'end' as const,
      render: (row: TemplateListItemDto) => (
        <Link className="ui-button ui-button--secondary" to={row.id}>
          {t('templates.open')}
        </Link>
      ),
    },
  ];

  return (
    <div className="templates-page">
      <header className="dashboard-page__header">
        <div>
          <p className="eyebrow">{t('templates.eyebrow')}</p>
          <h1>{t('templates.title')}</h1>
          <p>{t('templates.description')}</p>
        </div>
        <div className="campaign-detail__actions">
          <Link className="ui-button ui-button--secondary" to="/templates/assets">
            {t('templates.assets')}
          </Link>
          {canManage ? (
            <Link className="ui-button ui-button--primary" to="/templates/new">
              {t('templates.create')}
            </Link>
          ) : null}
        </div>
      </header>

      <form className="platform-tenant-filters" onSubmit={submitSearch}>
        <label>
          <span>{t('templates.search')}</span>
          <input
            value={searchDraft}
            placeholder={t('templates.searchPlaceholder')}
            onChange={(event) => setSearchDraft(event.target.value)}
          />
        </label>
        <label>
          <span>{t('templates.channel')}</span>
          <select
            value={channel ?? ''}
            onChange={(event) =>
              update({ channel: event.target.value || undefined, page: undefined })
            }
          >
            <option value="">{t('templates.all')}</option>
            {CHANNELS.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>{t('templates.status')}</span>
          <select
            value={searchParams.get('status') ?? ''}
            onChange={(event) =>
              update({ status: event.target.value || undefined, page: undefined })
            }
          >
            <option value="">{t('templates.all')}</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
        </label>
        <label>
          <span>{t('templates.locale')}</span>
          <input
            value={searchParams.get('locale') ?? ''}
            placeholder="ru"
            onChange={(event) =>
              update({ locale: event.target.value || undefined, page: undefined })
            }
          />
        </label>
        <label>
          <span>{t('templates.createdFrom')}</span>
          <input
            type="date"
            value={searchParams.get('createdFrom') ?? ''}
            onChange={(event) =>
              update({ createdFrom: event.target.value || undefined, page: undefined })
            }
          />
        </label>
        <label>
          <span>{t('templates.createdTo')}</span>
          <input
            type="date"
            value={searchParams.get('createdTo') ?? ''}
            onChange={(event) =>
              update({ createdTo: event.target.value || undefined, page: undefined })
            }
          />
        </label>
        <label>
          <span>{t('templates.sort')}</span>
          <select
            value={sort}
            onChange={(event) => update({ sort: event.target.value, page: undefined })}
          >
            <option value="createdAt,desc">{t('templates.sortCreatedDesc')}</option>
            <option value="updatedAt,desc">{t('templates.sortUpdatedDesc')}</option>
            <option value="name,asc">{t('templates.sortNameAsc')}</option>
            <option value="code,asc">{t('templates.sortCodeAsc')}</option>
          </select>
        </label>
        <div className="platform-tenant-filters__actions">
          <Button type="submit">{t('templates.apply')}</Button>
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              setSearchDraft('');
              setSearchParams(new URLSearchParams());
            }}
          >
            {t('templates.clear')}
          </Button>
        </div>
      </form>

      {query.isLoading ? <Spinner label={t('templates.loading')} /> : null}
      {query.error ? <ProblemDetailPanel error={query.error} onRetry={() => void query.refetch()} /> : null}
      {query.data ? (
        query.data.items.length ? (
          <>
            <DataTable
              columns={columns}
              rows={query.data.items}
              rowKey={(row) => row.id}
              emptyTitle={t('templates.empty')}
            />
            <Pagination
              page={query.data.page}
              totalPages={query.data.totalPages}
              onPageChange={(next) =>
                update({ page: next === 0 ? undefined : String(next) })
              }
            />
          </>
        ) : (
          <EmptyState title={t('templates.empty')} description={t('templates.emptyDescription')} />
        )
      ) : null}
    </div>
  );
}

function readChannel(value: string | null): TemplateChannel | undefined {
  return value && ['EMAIL', 'PDF', 'SMS', 'WHATSAPP', 'TELEGRAM'].includes(value)
    ? (value as TemplateChannel)
    : undefined;
}

function readSort(value: string | null): string {
  return ['createdAt,desc', 'updatedAt,desc', 'name,asc', 'code,asc'].includes(value ?? '')
    ? value!
    : 'createdAt,desc';
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
