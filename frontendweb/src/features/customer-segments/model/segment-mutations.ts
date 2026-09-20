import type { QueryClient } from '@tanstack/react-query';
import { customerKeys, segmentKeys } from '../../../entities/customer/api/customer.queries';
import type { SegmentOptionDto } from '../../../entities/customer/model/customer.types';

export async function applySegmentCreate(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: segmentKeys.lists(), refetchType: 'active' }),
    queryClient.invalidateQueries({ queryKey: segmentKeys.optionsRoot(), refetchType: 'active' }),
  ]);
}

export async function applySegmentUpdate(queryClient: QueryClient, segment: SegmentOptionDto): Promise<void> {
  queryClient.setQueryData(segmentKeys.detail(segment.id), segment);
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: segmentKeys.lists(), refetchType: 'active' }),
    queryClient.invalidateQueries({ queryKey: segmentKeys.optionsRoot(), refetchType: 'active' }),
    queryClient.invalidateQueries({ queryKey: customerKeys.lists(), refetchType: 'active' }),
    queryClient.invalidateQueries({
      predicate: (query) => query.queryKey[0] === 'customers'
        && query.queryKey[1] === 'detail'
        && query.queryKey.length === 3,
      refetchType: 'active',
    }),
  ]);
}

export async function applyMembershipMutation(queryClient: QueryClient, customerId: string): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: customerKeys.detail(customerId), exact: true, refetchType: 'active' }),
    queryClient.invalidateQueries({ queryKey: customerKeys.lists(), refetchType: 'active' }),
    queryClient.invalidateQueries({ queryKey: segmentKeys.optionsRoot(), refetchType: 'active' }),
  ]);
}
