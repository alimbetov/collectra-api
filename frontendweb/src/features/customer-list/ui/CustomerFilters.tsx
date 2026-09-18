import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { customerQueries } from '../../../entities/customer/api/customer.queries';
import {
  customerStatuses,
  customerTypes,
  type CustomerSort,
} from '../../../entities/customer/model/customer.types';
import { ApiError } from '../../../shared/api/http-client';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { Button, FormField, Spinner } from '../../../shared/ui';
import {
  customerPageSizes,
  instantToLocalInput,
  localInputToInstant,
  type CustomerListState,
} from '../model/customer-list-filters';

interface CustomerFiltersProps {
  value: CustomerListState;
  searchDraft: string;
  canReadUsers: boolean;
  onSearchDraft: (value: string) => void;
  onSearchCommit: () => void;
  onChange: (patch: Partial<CustomerListState>) => void;
  onClear: () => void;
}

function useDebouncedValue(value: string, delay: number): string {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [delay, value]);
  return debounced;
}

export function CustomerFilters({
  value,
  searchDraft,
  canReadUsers,
  onSearchDraft,
  onSearchCommit,
  onChange,
  onClear,
}: CustomerFiltersProps) {
  const { t, timeZone } = useI18n();
  const [managerSearch, setManagerSearch] = useState('');
  const [segmentSearch, setSegmentSearch] = useState('');
  const debouncedManagerSearch = useDebouncedValue(managerSearch, 300);
  const debouncedSegmentSearch = useDebouncedValue(segmentSearch, 300);
  const managers = useQuery({
    ...customerQueries.managerOptions(debouncedManagerSearch),
    enabled: canReadUsers,
  });
  const segments = useQuery(customerQueries.segmentOptions(debouncedSegmentSearch));
  const managerForbidden = managers.error instanceof ApiError && managers.error.status === 403;

  return (
    <form
      className="customer-filters"
      onSubmit={(event) => {
        event.preventDefault();
        onSearchCommit();
      }}
    >
      <div className="customer-filters__quick">
        <FormField label={t('customers.filters.search')}>
          <input
            type="search"
            value={searchDraft}
            placeholder={t('customers.filters.searchPlaceholder')}
            onChange={(event) => onSearchDraft(event.target.value)}
          />
        </FormField>
        <Button type="submit" variant="secondary">
          {t('customers.filters.applySearch')}
        </Button>
        <Button type="button" variant="secondary" onClick={onClear}>
          {t('customers.filters.clear')}
        </Button>
      </div>

      <fieldset className="customer-filters__grid">
        <legend>{t('customers.filters.legend')}</legend>
        <FormField label={t('customers.filters.status')}>
          <select
            value={value.status}
            onChange={(event) =>
              onChange({ status: event.target.value as CustomerListState['status'] })
            }
          >
            <option value="">{t('customers.filters.any')}</option>
            {customerStatuses.map((status) => (
              <option key={status} value={status}>
                {t(`customers.status.${status}`)}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label={t('customers.filters.type')}>
          <select
            value={value.customerType}
            onChange={(event) =>
              onChange({ customerType: event.target.value as CustomerListState['customerType'] })
            }
          >
            <option value="">{t('customers.filters.any')}</option>
            {customerTypes.map((type) => (
              <option key={type} value={type}>
                {t(`customers.type.${type}`)}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label={t('customers.filters.externalId')}>
          <input
            value={value.externalId}
            onChange={(event) => onChange({ externalId: event.target.value })}
          />
        </FormField>
        <FormField label={t('customers.filters.email')}>
          <input
            type="email"
            value={value.email}
            onChange={(event) => onChange({ email: event.target.value })}
          />
        </FormField>
        <FormField label={t('customers.filters.phone')}>
          <input
            type="tel"
            value={value.phone}
            onChange={(event) => onChange({ phone: event.target.value })}
          />
        </FormField>
        <div className="customer-filter-option">
          <FormField label={t('customers.filters.managerSearch')}>
            <input
              value={managerSearch}
              disabled={!canReadUsers || managerForbidden}
              onChange={(event) => setManagerSearch(event.target.value)}
            />
          </FormField>
          <FormField label={t('customers.filters.manager')}>
            <select
              value={value.managerId}
              disabled={!canReadUsers || managerForbidden}
              onChange={(event) => onChange({ managerId: event.target.value })}
            >
              <option value="">{t('customers.filters.any')}</option>
              {value.managerId &&
              !managers.data?.items.some((item) => item.userId === value.managerId) ? (
                <option value={value.managerId}>{value.managerId}</option>
              ) : null}
              {managers.data?.items.map((manager) => (
                <option key={manager.userId} value={manager.userId}>
                  {manager.label}
                </option>
              ))}
            </select>
          </FormField>
          {managers.isFetching ? <Spinner size="small" label={t('customers.filters.loading')} /> : null}
          {!canReadUsers || managerForbidden ? (
            <small>{t('customers.filters.managerUnavailable')}</small>
          ) : null}
        </div>
        <div className="customer-filter-option">
          <FormField label={t('customers.filters.segmentSearch')}>
            <input
              value={segmentSearch}
              onChange={(event) => setSegmentSearch(event.target.value)}
            />
          </FormField>
          <FormField label={t('customers.filters.segment')}>
            <select
              value={value.segmentId}
              onChange={(event) => onChange({ segmentId: event.target.value })}
            >
              <option value="">{t('customers.filters.any')}</option>
              {value.segmentId &&
              !segments.data?.items.some((item) => item.id === value.segmentId) ? (
                <option value={value.segmentId}>{value.segmentId}</option>
              ) : null}
              {segments.data?.items.map((segment) => (
                <option key={segment.id} value={segment.id}>
                  {segment.name}
                </option>
              ))}
            </select>
          </FormField>
          {segments.isFetching ? <Spinner size="small" label={t('customers.filters.loading')} /> : null}
        </div>
        <FormField label={t('customers.filters.createdFrom')}>
          <input
            type="datetime-local"
            value={instantToLocalInput(value.createdFrom, timeZone)}
            onChange={(event) =>
              onChange({ createdFrom: localInputToInstant(event.target.value, timeZone) })
            }
          />
        </FormField>
        <FormField label={t('customers.filters.createdTo')}>
          <input
            type="datetime-local"
            value={instantToLocalInput(value.createdTo, timeZone)}
            onChange={(event) =>
              onChange({ createdTo: localInputToInstant(event.target.value, timeZone) })
            }
          />
        </FormField>
        <FormField label={t('customers.filters.size')}>
          <select
            value={value.size}
            onChange={(event) =>
              onChange({ size: Number(event.target.value) as CustomerListState['size'] })
            }
          >
            {customerPageSizes.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label={t('customers.filters.sort')}>
          <select
            value={value.sort}
            onChange={(event) => onChange({ sort: event.target.value as CustomerSort })}
          >
            <option value="createdAt,desc">{t('customers.sort.createdDesc')}</option>
            <option value="createdAt,asc">{t('customers.sort.createdAsc')}</option>
            <option value="updatedAt,desc">{t('customers.sort.updatedDesc')}</option>
            <option value="updatedAt,asc">{t('customers.sort.updatedAsc')}</option>
            <option value="displayName,asc">{t('customers.sort.nameAsc')}</option>
            <option value="displayName,desc">{t('customers.sort.nameDesc')}</option>
            <option value="externalId,asc">{t('customers.sort.externalAsc')}</option>
            <option value="externalId,desc">{t('customers.sort.externalDesc')}</option>
          </select>
        </FormField>
      </fieldset>
    </form>
  );
}
