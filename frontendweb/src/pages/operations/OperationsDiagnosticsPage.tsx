import { FormEvent, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getCommunicationOperations, getGenerationJob, getIngestionBatches } from '../../entities/operations/api/operations.api';
import { PermissionGuard } from '../../features/auth/ui/PermissionGuard';

export function OperationsDiagnosticsPage() {
  const [status, setStatus] = useState('');
  const [jobInput, setJobInput] = useState('');
  const [jobId, setJobId] = useState('');
  const ingestion = useQuery({ queryKey: ['operations', 'ingestion', status], queryFn: () => getIngestionBatches(status || undefined) });
  const delivery = useQuery({ queryKey: ['operations', 'delivery'], queryFn: getCommunicationOperations });
  const job = useQuery({ queryKey: ['operations', 'generation-job', jobId], queryFn: () => getGenerationJob(jobId), enabled: Boolean(jobId) });

  function lookup(event: FormEvent) {
    event.preventDefault();
    setJobId(jobInput.trim());
  }

  return (
    <main>
      <h1>Operations & diagnostics</h1>
      <p>Safe tenant-scoped operational state. Raw payloads, credentials and message bodies are not exposed.</p>

      <PermissionGuard permission="INTEGRATION_SOURCE_READ">
        <section>
          <h2>Ingestion</h2>
          <label>Status <select value={status} onChange={(e) => setStatus(e.target.value)}><option value="">All</option><option>RECEIVED</option><option>PROCESSING</option><option>COMPLETED</option><option>FAILED</option></select></label>
          {ingestion.isError && <p role="alert">Failed to load ingestion diagnostics.</p>}
          <table><thead><tr><th>Received</th><th>Source</th><th>Status</th><th>Records</th><th>Created</th><th>Conflicts</th><th>Failed</th><th>Batch</th></tr></thead>
            <tbody>{(ingestion.data?.content ?? []).map((batch) => <tr key={batch.id}><td>{batch.receivedAt}</td><td>{batch.sourceCode}</td><td>{batch.status}</td><td>{batch.recordCount}</td><td>{batch.createdCount}</td><td>{batch.conflictCount}</td><td>{batch.failedCount}</td><td><Link to={`/imports/${batch.id}`}>{batch.id}</Link></td></tr>)}</tbody>
          </table>
        </section>
      </PermissionGuard>

      <PermissionGuard permission="CAMPAIGN_READ">
        <section>
          <h2>Delivery recovery health</h2>
          {delivery.isError && <p role="alert">Failed to load delivery health.</p>}
          <dl>
            <dt>Queued</dt><dd>{delivery.data?.queued ?? 0}</dd><dt>Processing</dt><dd>{delivery.data?.processing ?? 0}</dd>
            <dt>Retry wait</dt><dd>{delivery.data?.retryWait ?? 0}</dd><dt>Unknown</dt><dd>{delivery.data?.unknown ?? 0}</dd>
            <dt>Failed</dt><dd>{delivery.data?.failed ?? 0}</dd><dt>Stuck processing</dt><dd>{delivery.data?.stuckProcessing ?? 0}</dd>
            <dt>Due retry</dt><dd>{delivery.data?.dueRetry ?? 0}</dd><dt>Oldest stuck (s)</dt><dd>{delivery.data?.oldestStuckAgeSeconds ?? '—'}</dd>
          </dl>
          <Link to="/campaigns">Campaigns</Link>
        </section>
      </PermissionGuard>

      <PermissionGuard permission="DOCUMENT_READ">
        <section>
          <h2>Generation job lookup</h2>
          <p>The current backend exposes generation diagnostics by known job ID; it does not expose a tenant-wide job registry.</p>
          <form onSubmit={lookup}><label>Job ID<input value={jobInput} onChange={(e) => setJobInput(e.target.value)} required /></label><button type="submit">Lookup</button></form>
          {job.isError && <p role="alert">Generation job not found or unavailable.</p>}
          {job.data && <dl><dt>Status</dt><dd>{job.data.status}</dd><dt>Step</dt><dd>{job.data.currentStep ?? '—'}</dd><dt>Attempts</dt><dd>{job.data.attemptCount}</dd><dt>Error</dt><dd>{job.data.errorCode ?? '—'} {job.data.errorMessage ?? ''}</dd></dl>}
        </section>
      </PermissionGuard>
    </main>
  );
}
