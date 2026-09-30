import { FormEvent, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getCommunicationChannels, getCommunicationSummary, getFinancialSummary, getFinancialTimeSeries, type AnalyticsBucket } from '../../entities/analytics/api/tenant-analytics.api';
import { PermissionGuard } from '../../features/auth/ui/PermissionGuard';

export function TenantAnalyticsPage() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [bucket, setBucket] = useState<AnalyticsBucket>('DAY');
  const [filters, setFilters] = useState({ from: '', to: '', bucket: 'DAY' as AnalyticsBucket });

  const financial = useQuery({ queryKey: ['analytics', 'financial', filters], queryFn: () => getFinancialSummary(filters.from || undefined, filters.to || undefined) });
  const series = useQuery({ queryKey: ['analytics', 'financial-series', filters], queryFn: () => getFinancialTimeSeries(filters.from || undefined, filters.to || undefined, filters.bucket) });
  const communication = useQuery({ queryKey: ['analytics', 'communication', filters.from, filters.to], queryFn: () => getCommunicationSummary(filters.from ? `${filters.from}T00:00:00Z` : undefined, filters.to ? `${filters.to}T23:59:59Z` : undefined) });
  const channels = useQuery({ queryKey: ['analytics', 'channels', filters.from, filters.to], queryFn: () => getCommunicationChannels(filters.from ? `${filters.from}T00:00:00Z` : undefined, filters.to ? `${filters.to}T23:59:59Z` : undefined) });

  function apply(event: FormEvent) {
    event.preventDefault();
    setFilters({ from, to, bucket });
  }

  return (
    <main>
      <h1>Analytics</h1>
      <form onSubmit={apply}>
        <label>From<input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label>To<input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
        <label>Bucket<select value={bucket} onChange={(e) => setBucket(e.target.value as AnalyticsBucket)}><option>DAY</option><option>WEEK</option><option>MONTH</option></select></label>
        <button type="submit">Apply</button>
      </form>

      <PermissionGuard permission="RECEIVABLE_READ">
        <PermissionGuard permission="COLLECTION_READ">
          <section>
            <h2>Financial</h2>
            {financial.isError && <p role="alert">Failed to load financial analytics.</p>}
            <p>Generated: {financial.data?.generatedAt ?? '—'} · Range: {financial.data?.from ?? '—'} — {financial.data?.to ?? '—'}</p>
            <table><thead><tr><th>Currency</th><th>Invoiced</th><th>Payments</th><th>Allocated</th><th>Outstanding</th><th>Overdue</th></tr></thead>
              <tbody>{(financial.data?.currencies ?? []).map((m) => <tr key={m.currency}><td>{m.currency}</td><td>{m.invoiced}</td><td>{m.payments}</td><td>{m.allocated}</td><td>{m.currentSnapshot?.outstanding ?? '—'}</td><td>{m.currentSnapshot?.overdueOutstanding ?? '—'}</td></tr>)}</tbody>
            </table>
            <h3>{series.data?.bucket ?? filters.bucket} trend</h3>
            <table><thead><tr><th>Period</th><th>Currency</th><th>Invoiced</th><th>Payments</th><th>Collections opened/closed</th></tr></thead>
              <tbody>{(series.data?.items ?? []).flatMap((point) => point.currencies.map((m) => <tr key={`${point.from}-${m.currency}`}><td>{point.from} — {point.to}</td><td>{m.currency}</td><td>{m.invoiced}</td><td>{m.payments}</td><td>{m.collectionOpened}/{m.collectionClosed}</td></tr>))}</tbody>
            </table>
          </section>
        </PermissionGuard>
      </PermissionGuard>

      <PermissionGuard permission="CAMPAIGN_READ">
        <section>
          <h2>Communication</h2>
          {communication.isError && <p role="alert">Failed to load communication analytics.</p>}
          <p>Generated: {communication.data?.generatedAt ?? '—'}</p>
          <dl><dt>Recipients</dt><dd>{communication.data?.business.recipients ?? 0}</dd><dt>Sent</dt><dd>{communication.data?.business.sent ?? 0}</dd><dt>Failed</dt><dd>{communication.data?.business.failed ?? 0}</dd><dt>Success rate</dt><dd>{communication.data?.terminalSuccessRate ?? '—'}</dd></dl>
          <h3>Channels</h3>
          <table><thead><tr><th>Channel</th><th>Messages</th><th>Sent</th><th>Failed</th><th>Retries</th><th>Success rate</th></tr></thead>
            <tbody>{(channels.data?.items ?? []).map((item) => <tr key={item.channel}><td>{item.channel}</td><td>{item.messageCount}</td><td>{item.sent}</td><td>{item.failed}</td><td>{item.retries}</td><td>{item.terminalSuccessRate}</td></tr>)}</tbody>
          </table>
        </section>
      </PermissionGuard>
    </main>
  );
}
