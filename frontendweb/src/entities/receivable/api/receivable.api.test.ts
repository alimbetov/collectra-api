import { beforeEach, describe, expect, it, vi } from 'vitest';
import { invoiceListPath } from './receivable.api';

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
});
