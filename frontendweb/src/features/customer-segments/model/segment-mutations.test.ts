import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { customerKeys, segmentKeys } from '../../../entities/customer/api/customer.queries';
import type { SegmentOptionDto } from '../../../entities/customer/model/customer.types';
import { applyMembershipMutation, applySegmentUpdate } from './segment-mutations';

describe('segment mutation cache contract', () => {
  it('keeps authoritative detail and targets dependent collections', async () => {
    const client = new QueryClient();
    const invalidate = vi.spyOn(client, 'invalidateQueries').mockResolvedValue();
    const segment = { id: 'segment', code: 'VIP', name: 'Priority', version: 2 } as SegmentOptionDto;

    await applySegmentUpdate(client, segment);

    expect(client.getQueryData(segmentKeys.detail('segment'))).toBe(segment);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: segmentKeys.lists(), refetchType: 'active' });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: segmentKeys.optionsRoot(), refetchType: 'active' });
    expect(invalidate).not.toHaveBeenCalledWith(expect.objectContaining({ queryKey: segmentKeys.details() }));
  });

  it('reconciles the affected customer projections and segment options after membership changes', async () => {
    const client = new QueryClient();
    const invalidate = vi.spyOn(client, 'invalidateQueries').mockResolvedValue();

    await applyMembershipMutation(client, 'customer');

    expect(invalidate).toHaveBeenCalledWith({ queryKey: customerKeys.detail('customer'), exact: true, refetchType: 'active' });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: customerKeys.lists(), refetchType: 'active' });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: segmentKeys.optionsRoot(), refetchType: 'active' });
    expect(invalidate).not.toHaveBeenCalledWith(expect.objectContaining({ queryKey: segmentKeys.lists() }));
  });
});
