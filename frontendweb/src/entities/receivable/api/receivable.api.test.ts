import { beforeEach, describe, expect, it, vi } from 'vitest';
import { invoiceListPath, paymentListPath } from './receivable.api';

describe('receivable api', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('encodes invoice server filters without converting decimal strings', () => {
    const path=invoiceListPath({page:2,size:50,sort:'outstandingAmount,desc',currency:'KZT',outstandingMin:'900719925474099.9999',overdue:true});
    const url=new URL(path,'https://collectra.test');
    expect(url.pathname).toBe('/api/v1/invoices');
    expect(url.searchParams.get('outstandingMin')).toBe('900719925474099.9999');
    expect(url.searchParams.get('overdue')).toBe('true');
    expect(url.searchParams.get('page')).toBe('2');
  });

  it('encodes payment filters without converting decimal strings', () => {
    const path=paymentListPath({page:0,size:50,sort:'createdAt,desc',customerId:'11111111-1111-1111-1111-111111111111',amountMin:'0.10',amountMax:'999999999999999999.99',unallocatedOnly:true,search:'REF-42'});
    const url=new URL(path,'https://collectra.test');
    expect(url.pathname).toBe('/api/v1/payments');
    expect(url.searchParams.get('customerId')).toBe('11111111-1111-1111-1111-111111111111');
    expect(url.searchParams.get('amountMin')).toBe('0.10');
    expect(url.searchParams.get('amountMax')).toBe('999999999999999999.99');
    expect(url.searchParams.get('unallocatedOnly')).toBe('true');
    expect(url.searchParams.get('search')).toBe('REF-42');
  });
});
