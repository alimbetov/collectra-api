import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, Navigate, useBlocker, useParams } from 'react-router-dom';
import { customerQueries } from '../../entities/customer/api/customer.queries';
import {
  addCustomerEmail, addCustomerPhone, addCustomerSegment, changeCustomerStatus, removeCustomerSegment, updateCustomer,
  updateCustomerEmail, updateCustomerPhone,
} from '../../entities/customer/api/customer.api';
import type {
  ContactPatchCommand, CustomerEmailDto, CustomerPhoneDto, EmailCreateCommand, PhoneCreateCommand,
} from '../../entities/customer/model/customer.types';
import { useAuth } from '../../features/auth/model/auth-context';
import { isCustomerId } from '../../features/customer-detail/model/customer-detail';
import { CustomerEmails, CustomerPhones } from '../../features/customer-detail/ui/CustomerContacts';
import { CustomerDetailHeader } from '../../features/customer-detail/ui/CustomerDetailHeader';
import { CustomerDetailTabs } from '../../features/customer-detail/ui/CustomerDetailTabs';
import { CustomerOverview } from '../../features/customer-detail/ui/CustomerOverview';
import { applyCustomerMutation } from '../../features/customer-edit/model/customer-mutations';
import { CustomerEditDialog } from '../../features/customer-edit/ui/CustomerEditDialog';
import { CustomerStatusDialog } from '../../features/customer-edit/ui/CustomerStatusDialog';
import { refreshContactMutation } from '../../features/customer-contact-edit/model/contact-mutations';
import type { ContactKind, CustomerContact } from '../../features/customer-contact-edit/model/customer-contact-edit';
import { CustomerContactDialog } from '../../features/customer-contact-edit/ui/CustomerContactDialog';
import { applyMembershipMutation } from '../../features/customer-segments/model/segment-mutations';
import type { MembershipDelta } from '../../features/customer-segments/model/segment-membership';
import { CustomerSegmentsDialog } from '../../features/customer-segments/ui/CustomerSegmentsDialog';
import { CustomerContractsPanel } from '../../features/contracts/ui/CustomerContractsPanel';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { useI18n } from '../../shared/i18n/i18n-context';
import { ConfirmDialog, EmptyState, Spinner, useToast } from '../../shared/ui';

export type CustomerDetailTab = 'overview' | 'contacts' | 'contracts';

type ContactIntent =
  | { kind: 'email'; mode: 'create'; command: EmailCreateCommand }
  | { kind: 'phone'; mode: 'create'; command: PhoneCreateCommand }
  | { kind: ContactKind; mode: 'patch'; contactId: string; command: ContactPatchCommand };

function status(error: unknown): number | null {
  return error instanceof ApiError ? error.status : null;
}

export function CustomerDetailPage({ tab }: { tab: CustomerDetailTab }) {
  const { customerId } = useParams();
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const [editOpen, setEditOpen] = useState(false);
  const [statusOpen, setStatusOpen] = useState(false);
  const [segmentsOpen, setSegmentsOpen] = useState(false);
  const [editDirty, setEditDirty] = useState(false);
  const [contactEditor, setContactEditor] = useState<{ kind: ContactKind; contact?: CustomerContact } | null>(null);
  const blocker = useBlocker(editDirty);
  const validId = isCustomerId(customerId);
  const id = validId ? customerId : '';
  const detail = useQuery({ ...customerQueries.detail(id), enabled: validId });
  const emails = useQuery({ ...customerQueries.emails(id), enabled: validId && tab === 'contacts' });
  const phones = useQuery({ ...customerQueries.phones(id), enabled: validId && tab === 'contacts' });
  const profileMutation = useMutation({
    mutationFn: (command: Parameters<typeof updateCustomer>[1]) => updateCustomer(id, command),
    onSuccess: async (customer) => {
      await applyCustomerMutation(queryClient, customer);
      setEditDirty(false);
      setEditOpen(false);
      showToast({ title: t('customerEdit.saved'), tone: 'success' });
    },
  });
  const statusMutation = useMutation({
    mutationFn: ({ status, version }: Parameters<typeof changeCustomerStatus>[1]) =>
      changeCustomerStatus(id, { status, version }),
    onSuccess: async (customer) => {
      await applyCustomerMutation(queryClient, customer);
      setStatusOpen(false);
      showToast({ title: t('customerStatus.saved'), tone: 'success' });
    },
  });
  const contactMutation = useMutation<CustomerContact, unknown, ContactIntent>({
    mutationFn: async (intent) => {
      if (intent.mode === 'create' && intent.kind === 'email') return await addCustomerEmail(id, intent.command);
      if (intent.mode === 'create' && intent.kind === 'phone') return await addCustomerPhone(id, intent.command);
      if (intent.mode === 'patch' && intent.kind === 'email') return await updateCustomerEmail(id, intent.contactId, intent.command);
      if (intent.mode === 'patch') return await updateCustomerPhone(id, intent.contactId, intent.command);
      throw new Error('Unsupported contact intent');
    },
    onSuccess: async (_contact, intent) => {
      await refreshContactMutation(queryClient, id, intent.kind);
      setEditDirty(false);
      setContactEditor(null);
      showToast({ title: t('contactEdit.saved'), tone: 'success' });
    },
  });
  const membershipMutation = useMutation<void, unknown, MembershipDelta>({
    mutationFn: async ({ additions, removals }) => {
      for (const segmentId of additions) await addCustomerSegment(id, segmentId);
      for (const segmentId of removals) await removeCustomerSegment(id, segmentId);
    },
    onSuccess: async () => {
      await applyMembershipMutation(queryClient, id);
      setEditDirty(false);
      setSegmentsOpen(false);
      showToast({ title: t('segments.membershipSaved'), tone: 'success' });
    },
    onError: async () => {
      // A multi-request delta can fail after an earlier idempotent operation succeeded.
      // PostgreSQL remains authoritative, so reconcile instead of rolling back the UI locally.
      await applyMembershipMutation(queryClient, id);
    },
  });
  const openContact = (kind: ContactKind, contact?: CustomerContact) => {
    contactMutation.reset();
    setContactEditor({ kind, contact });
  };
  const errors = [detail.error, emails.error, phones.error];

  if (errors.some((error) => status(error) === 403)) return <Navigate to="/forbidden" replace />;
  if (!validId || errors.some((error) => status(error) === 404)) {
    return (
      <div className="customer-detail customer-detail__not-found">
        <EmptyState title={t('customerDetail.notFound')} description={t('customerDetail.notFoundDescription')} />
        <Link to="/customers">{t('customerDetail.back')}</Link>
      </div>
    );
  }
  if (detail.isLoading) {
    return <div className="customer-detail__loading"><Spinner label={t('customerDetail.loading')} /></div>;
  }
  if (detail.error) {
    return <ProblemDetailPanel error={detail.error} onRetry={() => void detail.refetch()} />;
  }
  if (!detail.data) return null;

  return (
    <div className="customer-detail">
      <CustomerDetailHeader
        customer={detail.data}
        onEdit={() => { statusMutation.reset(); profileMutation.reset(); setEditOpen(true); }}
        onChangeStatus={() => { profileMutation.reset(); statusMutation.reset(); setStatusOpen(true); }}
      />
      <CustomerDetailTabs customerId={id} />
      {tab === 'overview' ? <CustomerOverview customer={detail.data} onManageSegments={() => { membershipMutation.reset(); setSegmentsOpen(true); }} /> : tab === 'contacts' ? (
        <div className="customer-detail-grid">
          {emails.isLoading ? <div className="customer-detail-card"><Spinner label={t('customerDetail.loadingEmails')} /></div> : null}
          {emails.error ? <ProblemDetailPanel error={emails.error} onRetry={() => void emails.refetch()} /> : null}
          {emails.data ? <CustomerEmails values={emails.data} onAdd={() => openContact('email')} onEdit={(contact: CustomerEmailDto) => openContact('email', contact)} /> : null}
          {phones.isLoading ? <div className="customer-detail-card"><Spinner label={t('customerDetail.loadingPhones')} /></div> : null}
          {phones.error ? <ProblemDetailPanel error={phones.error} onRetry={() => void phones.refetch()} /> : null}
          {phones.data ? <CustomerPhones values={phones.data} onAdd={() => openContact('phone')} onEdit={(contact: CustomerPhoneDto) => openContact('phone', contact)} /> : null}
        </div>
      ) : <CustomerContractsPanel customer={detail.data} onDirtyChange={setEditDirty} />}
      <CustomerEditDialog
        open={editOpen}
        customer={detail.data}
        canReadUsers={hasPermission('USER_READ')}
        pending={profileMutation.isPending}
        error={profileMutation.error}
        onSave={(command) => profileMutation.mutate(command)}
        onReload={async () => { profileMutation.reset(); return detail.refetch(); }}
        onClose={() => { profileMutation.reset(); setEditOpen(false); }}
        onDirtyChange={setEditDirty}
      />
      <CustomerStatusDialog
        open={statusOpen}
        customer={detail.data}
        pending={statusMutation.isPending}
        error={statusMutation.error}
        onConfirm={(status, version) => statusMutation.mutate({ status, version })}
        onReload={async () => { statusMutation.reset(); return detail.refetch(); }}
        onClose={() => { statusMutation.reset(); setStatusOpen(false); }}
      />
      {contactEditor ? <CustomerContactDialog
        open
        kind={contactEditor.kind}
        contact={contactEditor.contact}
        pending={contactMutation.isPending}
        error={contactMutation.error}
        onCreateEmail={(command) => contactMutation.mutate({ kind: 'email', mode: 'create', command })}
        onCreatePhone={(command) => contactMutation.mutate({ kind: 'phone', mode: 'create', command })}
        onPatch={(command) => contactMutation.mutate({
          kind: contactEditor.kind,
          mode: 'patch',
          contactId: contactEditor.contact?.id ?? '',
          command,
        })}
        onReload={async () => {
          contactMutation.reset();
          return contactEditor.kind === 'email' ? emails.refetch() : phones.refetch();
        }}
        onClose={() => { contactMutation.reset(); setContactEditor(null); }}
        onDirtyChange={setEditDirty}
      /> : null}
      {segmentsOpen ? <CustomerSegmentsDialog
        open
        customer={detail.data}
        pending={membershipMutation.isPending}
        error={membershipMutation.error}
        onSave={(delta) => membershipMutation.mutate(delta)}
        onReload={async () => {
          membershipMutation.reset();
          await applyMembershipMutation(queryClient, id);
          return detail.refetch();
        }}
        onClose={() => { membershipMutation.reset(); setSegmentsOpen(false); }}
        onDirtyChange={setEditDirty}
      /> : null}
      <ConfirmDialog
        open={blocker.state === 'blocked'}
        title={t('customerEdit.unsavedTitle')}
        confirmLabel={t('customerEdit.discard')}
        cancelLabel={t('customerEdit.continue')}
        closeLabel={t('customerEdit.close')}
        destructive
        onConfirm={() => { setEditDirty(false); blocker.proceed?.(); }}
        onCancel={() => blocker.reset?.()}
      >{t('customerEdit.unsavedDescription')}</ConfirmDialog>
    </div>
  );
}
