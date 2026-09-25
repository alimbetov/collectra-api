import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { integrationQueries } from '../../entities/integration/api/integration.queries';
import { useAuth } from '../../features/auth/model/auth-context';
import { EmptyState, Spinner, StatusBadge } from '../../shared/ui';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';

export function IntegrationSourcesPage() {
  const { hasPermission } = useAuth();
  const query = useQuery(integrationQueries.sources());
  return <div className="integration-page">
    <header className="integration-page__header"><div><p className="eyebrow">Интеграции</p><h1>Integration Sources</h1><p>Связь Service Client, опубликованной схемы и mapping profile.</p></div>
      {hasPermission('INTEGRATION_SOURCE_MANAGE') ? <Link className="ui-button ui-button--primary" to="/integrations/sources/new">Создать source</Link> : null}
    </header>
    {query.isLoading ? <Spinner label="Загружаем Integration Sources…" /> : null}
    {query.error ? <ProblemDetailPanel error={query.error} onRetry={() => void query.refetch()} /> : null}
    {query.data?.length === 0 ? <EmptyState title="Integration Sources не созданы" description="Создайте источник и свяжите его с клиентом, схемой и mapping profile." /> : null}
    {query.data?.length ? <div className="integration-list">{query.data.map(s => <Link key={s.id} className="integration-list__row" to={s.id}><div><strong>{s.name}</strong><span>{s.code}</span></div><StatusBadge tone={s.status === 'ACTIVE' ? 'success' : s.status === 'DRAFT' ? 'warning' : 'neutral'}>{s.status}</StatusBadge></Link>)}</div> : null}
  </div>;
}
