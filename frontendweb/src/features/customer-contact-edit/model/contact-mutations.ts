import type { QueryClient } from '@tanstack/react-query';
import { customerKeys } from '../../../entities/customer/api/customer.queries';
import type { ContactKind } from './customer-contact-edit';

export async function refreshContactMutation(queryClient: QueryClient, customerId: string, kind: ContactKind) {
  const contactKey = kind === 'email' ? customerKeys.emails(customerId) : customerKeys.phones(customerId);
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: contactKey, refetchType: 'active' }),
    queryClient.invalidateQueries({ queryKey: customerKeys.lists(), refetchType: 'active' }),
  ]);
}
