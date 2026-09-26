import { describe, expect, it } from 'vitest';
import { defaultInvoiceListState, parseInvoiceListState, serializeInvoiceListState, updateInvoiceListState } from './invoice-list-filters';

describe('invoice list URL state', () => {
  it('normalizes unsafe URL values', () => {
    const state=parseInvoiceListState(new URLSearchParams('page=-2&size=999&sort=hack&paymentStatus=NOPE&currency=kzt'));
    expect(state).toEqual({...defaultInvoiceListState,currency:'KZT'});
  });
  it('round trips supported filters and resets page on filter changes', () => {
    const state={...defaultInvoiceListState,customerId:'11111111-1111-4111-8111-111111111111',page:3,size:50 as const,overdue:'true' as const,dueFrom:'2026-01-01',outstandingMin:'10.2500'};
    expect(parseInvoiceListState(serializeInvoiceListState(state))).toEqual(state);
    expect(updateInvoiceListState(state,{overdue:'false'}).page).toBe(0);
  });
});
