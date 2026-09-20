import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { customerQueries } from '../../entities/customer/api/customer.queries';
import { useAuth } from '../../features/auth/model/auth-context';
import {
  defaultCustomerListState,
  hasCustomerFilters,
  hasValidCreatedRange,
  parseCustomerListState,
  serializeCustomerListState,
  toCustomerListQuery,
  updateCustomerListState,
  type CustomerListState,
} from '../../features/customer-list/model/customer-list-filters';
import { CustomerFilters } from '../../features/customer-list/ui/CustomerFilters';
import { CustomerTable } from '../../features/customer-list/ui/CustomerTable';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { useI18n } from '../../shared/i18n/i18n-context';
import { Alert, Button, Pagination, Spinner } from '../../shared/ui';

export function CustomersPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(
    () => parseCustomerListState(searchParams),
    [searchParams],
  );
  const [searchDraft, setSearchDraft] = useState(filters.search);
  const validRange = hasValidCreatedRange(filters);
  const query = useMemo(() => toCustomerListQuery(filters), [filters]);
  const customers = useQuery({
    ...customerQueries.list(query),
    enabled: validRange,
    placeholderData: keepPreviousData,
  });

  const navigate = useCallback(
    (next: CustomerListState, replace = false) => {
      setSearchParams(serializeCustomerListState(next), { replace });
    },
    [setSearchParams],
  );

  const change = useCallback(
    (patch: Partial<CustomerListState>) => navigate(updateCustomerListState(filters, patch)),
    [filters, navigate],
  );

  useEffect(() => setSearchDraft(filters.search), [filters.search]);
  useEffect(() => {
    if (searchDraft.trim() === filters.search) return;
    const timer = window.setTimeout(
      () => navigate(updateCustomerListState(filters, { search: searchDraft.trim() }), true),
      300,
    );
    return () => window.clearTimeout(timer);
  }, [filters, navigate, searchDraft]);

  useEffect(() => {
    const totalPages = customers.data?.totalPages;
    if (totalPages !== undefined && filters.page > 0 && filters.page >= totalPages) {
      navigate(updateCustomerListState(filters, { page: Math.max(0, totalPages - 1) }, false), true);
    }
  }, [customers.data?.totalPages, filters, navigate]);

  if (customers.error instanceof ApiError && customers.error.status === 403) {
    return <Navigate to="/forbidden" replace />;
  }

  const filtered = hasCustomerFilters(filters);
  return (
    <div className="customers-page">
      <header className="customers-page__header">
        <div>
          <p className="eyebrow">{t('customers.eyebrow')}</p>
          <h1>{t('customers.title')}</h1>
          <p aria-live="polite">
            {customers.data
              ? t('customers.total').replace('{count}', String(customers.data.totalElements))
              : t('customers.totalPending')}
          </p>
        </div>
        <div className="customers-page__header-actions">
          {customers.isFetching && !customers.isLoading ? <Spinner size="small" label={t('customers.refreshing')} /> : null}
          <Link className="ui-button ui-button--secondary" to="/customers/segments">{t('segments.manage')}</Link>
        </div>
      </header>

      <CustomerFilters
        value={filters}
        searchDraft={searchDraft}
        canReadUsers={hasPermission('USER_READ')}
        onSearchDraft={setSearchDraft}
        onSearchCommit={() =>
          navigate(updateCustomerListState(filters, { search: searchDraft.trim() }))
        }
        onChange={change}
        onClear={() => {
          setSearchDraft('');
          navigate(defaultCustomerListState);
        }}
      />

      {!validRange ? (
        <Alert variant="danger" title={t('customers.invalidRange')}>
          {t('customers.invalidRangeDescription')}
        </Alert>
      ) : null}
      {validRange && customers.isLoading ? (
        <div className="customers-page__loading">
          <Spinner label={t('customers.loading')} />
        </div>
      ) : null}
      {validRange && customers.error ? (
        <div className="customers-page__error">
          <ProblemDetailPanel error={customers.error} onRetry={() => void customers.refetch()} />
          {customers.error instanceof ApiError && customers.error.status === 400 ? (
            <Button
              variant="secondary"
              type="button"
              onClick={() => {
                setSearchDraft('');
                navigate(defaultCustomerListState);
              }}
            >
              {t('customers.filters.clear')}
            </Button>
          ) : null}
        </div>
      ) : null}
      {validRange && customers.data ? (
        <>
          <CustomerTable rows={customers.data.items} filtered={filtered} />
          <Pagination
            page={customers.data.page}
            totalPages={customers.data.totalPages}
            onPageChange={(page) =>
              navigate(updateCustomerListState(filters, { page }, false))
            }
            label={t('customers.pagination.label')}
            previousLabel={t('customers.pagination.previous')}
            nextLabel={t('customers.pagination.next')}
          />
        </>
      ) : null}
    </div>
  );
}
