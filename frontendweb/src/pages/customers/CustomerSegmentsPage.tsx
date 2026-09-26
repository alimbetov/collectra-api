import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useBlocker, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { createSegment, updateSegment } from '../../entities/customer/api/customer.api';
import { customerQueries } from '../../entities/customer/api/customer.queries';
import type { SegmentCreateCommand, SegmentUpdateCommand } from '../../entities/customer/model/customer.types';
import { isCustomerId } from '../../features/customer-detail/model/customer-detail';
import {
  defaultSegmentListState, parseSegmentListState, serializeSegmentListState,
  toSegmentListQuery, updateSegmentListState, type SegmentListState,
} from '../../features/customer-segments/model/segment-list-filters';
import { applySegmentCreate, applySegmentUpdate } from '../../features/customer-segments/model/segment-mutations';
import { SegmentFormDialog } from '../../features/customer-segments/ui/SegmentFormDialog';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { formatInstant } from '../../shared/i18n/formatters';
import { useI18n } from '../../shared/i18n/i18n-context';
import { Button, ConfirmDialog, DataTable, EmptyState, FormField, Pagination, Spinner, StatusBadge, useToast } from '../../shared/ui';
import { useAuth } from '../../features/auth/model/auth-context';

export function CustomerSegmentsPage() {
  const { segmentId } = useParams();
  const validSegmentId = !segmentId || isCustomerId(segmentId);
  const { t, locale, timeZone } = useI18n();
  const { showToast } = useToast();
  const { hasPermission } = useAuth();
  const canManageCustomer = hasPermission('CUSTOMER_MANAGE');
  const queryClient = useQueryClient();
  const navigateRoute = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(() => parseSegmentListState(searchParams), [searchParams]);
  const [searchDraft, setSearchDraft] = useState(filters.search);
  const [createOpen, setCreateOpen] = useState(false);
  const [dirty, setDirty] = useState(false);
  const blocker = useBlocker(dirty);
  const list = useQuery({
    ...customerQueries.segmentList(toSegmentListQuery(filters)),
    placeholderData: keepPreviousData,
  });
  const detail = useQuery({
    ...customerQueries.segmentDetail(segmentId ?? ''),
    enabled: Boolean(segmentId && validSegmentId),
  });
  const navigate = useCallback((next: SegmentListState, replace = false) => {
    setSearchParams(serializeSegmentListState(next), { replace });
  }, [setSearchParams]);
  const closeEditor = () => navigateRoute({ pathname: '/customers/segments', search: searchParams.toString() ? `?${searchParams}` : '' });

  useEffect(() => setSearchDraft(filters.search), [filters.search]);
  useEffect(() => {
    if (searchDraft.trim() === filters.search) return;
    const timer = window.setTimeout(() => navigate(updateSegmentListState(filters, { search: searchDraft.trim() }), true), 300);
    return () => window.clearTimeout(timer);
  }, [filters, navigate, searchDraft]);
  useEffect(() => {
    const totalPages = list.data?.totalPages;
    if (totalPages !== undefined && filters.page > 0 && filters.page >= totalPages) {
      navigate(updateSegmentListState(filters, { page: Math.max(0, totalPages - 1) }), true);
    }
  }, [filters, list.data?.totalPages, navigate]);

  const createMutation = useMutation({
    mutationFn: (command: SegmentCreateCommand) => createSegment(command),
    onSuccess: async () => {
      await applySegmentCreate(queryClient);
      setDirty(false); setCreateOpen(false);
      showToast({ title: t('segments.created'), tone: 'success' });
    },
  });
  const updateMutation = useMutation({
    mutationFn: (command: SegmentUpdateCommand) => updateSegment(segmentId ?? '', command),
    onSuccess: async (segment) => {
      await applySegmentUpdate(queryClient, segment);
      setDirty(false); closeEditor();
      showToast({ title: t('segments.updated'), tone: 'success' });
    },
  });

  if (list.error instanceof ApiError && list.error.status === 403) return <Navigate to="/forbidden" replace />;
  const missingDetail = !validSegmentId || (detail.error instanceof ApiError && detail.error.status === 404);

  return <div className="customers-page segments-page">
    <header className="customers-page__header">
      <div><p className="eyebrow">{t('customers.eyebrow')}</p><h1>{t('segments.title')}</h1>
        <p>{list.data ? t('segments.total').replace('{count}', String(list.data.totalElements)) : t('customers.totalPending')}</p></div>
      <div className="segments-page__actions"><Link to="/customers">{t('customerDetail.back')}</Link>{canManageCustomer ? <Button onClick={() => { createMutation.reset(); setCreateOpen(true); }}>{t('segments.create')}</Button> : null}</div>
    </header>
    <div className="customer-filters__quick">
      <FormField label={t('segments.search')}><input type="search" value={searchDraft} onChange={(event) => setSearchDraft(event.target.value)} /></FormField>
      <FormField label={t('segments.status')}><select value={filters.active} onChange={(event) => navigate(updateSegmentListState(filters, { active: event.target.value as SegmentListState['active'] }))}>
        <option value="">{t('customers.filters.any')}</option><option value="true">{t('segments.active')}</option><option value="false">{t('segments.inactive')}</option>
      </select></FormField>
      <FormField label={t('customers.filters.size')}><select value={filters.size} onChange={(event) => navigate(updateSegmentListState(filters, { size: Number(event.target.value) as SegmentListState['size'] }))}>
        {[20, 50, 100].map((size) => <option key={size} value={size}>{size}</option>)}
      </select></FormField>
      <FormField label={t('customers.filters.sort')}><select value={filters.sort} onChange={(event) => navigate(updateSegmentListState(filters, { sort: event.target.value as SegmentListState['sort'] }))}>
        <option value="name,asc">{t('segments.sortName')}</option><option value="name,desc">{t('segments.sortNameDesc')}</option>
        <option value="code,asc">{t('segments.sortCode')}</option><option value="code,desc">{t('segments.sortCodeDesc')}</option>
        <option value="createdAt,desc">{t('segments.sortCreatedDesc')}</option><option value="createdAt,asc">{t('segments.sortCreatedAsc')}</option>
        <option value="updatedAt,desc">{t('segments.sortUpdated')}</option><option value="updatedAt,asc">{t('segments.sortUpdatedAsc')}</option>
      </select></FormField>
      <Button variant="secondary" onClick={() => { setSearchDraft(''); navigate(defaultSegmentListState); }}>{t('customers.filters.clear')}</Button>
    </div>
    {list.isLoading ? <Spinner label={t('segments.loading')} /> : null}
    {list.error ? <ProblemDetailPanel error={list.error} onRetry={() => void list.refetch()} /> : null}
    {list.data && !list.data.items.length ? <EmptyState title={t('segments.empty')} description={t('segments.emptyDescription')} /> : null}
    {list.data?.items.length ? <>
      <DataTable
        caption={t('segments.tableCaption')}
        rows={list.data.items}
        rowKey={(segment) => segment.id}
        emptyTitle={t('segments.empty')}
        columns={[
          { key: 'code', header: t('segments.code'), render: (segment) => segment.code },
          { key: 'name', header: t('segments.name'), render: (segment) => segment.name },
          { key: 'status', header: t('segments.status'), render: (segment) => <StatusBadge tone={segment.active ? 'success' : 'neutral'}>{t(segment.active ? 'segments.active' : 'segments.inactive')}</StatusBadge> },
          { key: 'updated', header: t('customers.columns.updated'), render: (segment) => formatInstant(segment.updatedAt, locale, timeZone) },
          { key: 'actions', header: t('contactEdit.actions'), render: (segment) => canManageCustomer ? <Button variant="secondary" onClick={() => navigateRoute({ pathname: `/customers/segments/${segment.id}`, search: searchParams.toString() ? `?${searchParams}` : '' })} >{t('contactEdit.edit')}</Button> : null },
        ]}
      />
      <Pagination page={list.data.page} totalPages={list.data.totalPages} onPageChange={(page) => navigate(updateSegmentListState(filters, { page }))} label={t('segments.pagination')} previousLabel={t('customers.pagination.previous')} nextLabel={t('customers.pagination.next')} />
    </> : null}
    {missingDetail ? <div className="segments-page__not-found"><EmptyState title={t('segments.notFound')} /><Button variant="secondary" onClick={closeEditor}>{t('segments.back')}</Button></div> : null}
    {segmentId && validSegmentId && detail.isLoading ? <Spinner label={t('segments.loadingDetail')} /> : null}
    {segmentId && detail.error && !missingDetail && !(detail.error instanceof ApiError && detail.error.status === 403)
      ? <ProblemDetailPanel error={detail.error} onRetry={() => void detail.refetch()} /> : null}
    {canManageCustomer ? <SegmentFormDialog
      open={createOpen}
      pending={createMutation.isPending}
      error={createMutation.error}
      onCreate={(command) => createMutation.mutate(command)} onUpdate={() => undefined}
      onReload={async () => { createMutation.reset(); return undefined; }}
      onClose={() => { createMutation.reset(); setCreateOpen(false); }} onDirtyChange={setDirty}
    /> : null}
    {canManageCustomer && detail.data ? <SegmentFormDialog
      open segment={detail.data} pending={updateMutation.isPending} error={updateMutation.error}
      onCreate={() => undefined} onUpdate={(command) => updateMutation.mutate(command)}
      onReload={async () => { updateMutation.reset(); return detail.refetch(); }}
      onClose={() => { updateMutation.reset(); closeEditor(); }} onDirtyChange={setDirty}
    /> : null}
    <ConfirmDialog open={blocker.state === 'blocked'} title={t('customerEdit.unsavedTitle')} confirmLabel={t('customerEdit.discard')} cancelLabel={t('customerEdit.continue')} closeLabel={t('customerEdit.close')} destructive onConfirm={() => { setDirty(false); blocker.proceed?.(); }} onCancel={() => blocker.reset?.()}>{t('customerEdit.unsavedDescription')}</ConfirmDialog>
  </div>;
}
