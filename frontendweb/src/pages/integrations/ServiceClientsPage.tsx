import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { integrationQueries } from '../../entities/integration/api/integration.queries';
import { useAuth } from '../../features/auth/model/auth-context';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { EmptyState, Spinner, StatusBadge } from '../../shared/ui';

export function ServiceClientsPage() {
  const { hasPermission } = useAuth();
  const query = useQuery(integrationQueries.serviceClients());
  return <div className="integration-page">
    <header className="integration-page__header"><div><p className="eyebrow">Интеграции</p><h1>Service Clients</h1><p>Машинные учетные записи внешних систем.</p></div>
      {hasPermission('SERVICE_CLIENT_CREATE') ? <Link className="ui-button ui-button--primary" to="/integrations/service-clients/new">Создать</Link> : null}
    </header>
    {query.isLoading ? <Spinner label="Загружаем Service Clients…" /> : null}
    {query.error ? <ProblemDetailPanel error={query.error} onRetry={() => void query.refetch()} /> : null}
    {query.data && query.data.length === 0 ? <EmptyState title="Service Clients не созданы" description="Создайте машинную учетную запись для внешней системы." /> : null}
    {query.data?.length ? <div className="integration-list">{query.data.map(c => <Link key={c.id} className="integration-list__row" to={c.id}><div><strong>{c.name}</strong><span>{c.clientId}</span></div><StatusBadge tone={c.status === 'ACTIVE' ? 'success' : 'warning'}>{c.status}</StatusBadge></Link>)}</div> : null}
  </div>;
}
