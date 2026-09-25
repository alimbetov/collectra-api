import { Link } from 'react-router-dom';
import { useAuth } from '../../features/auth/model/auth-context';

export function IntegrationsPage() {
  const { hasPermission } = useAuth();
  return <div className="integration-page">
    <header><p className="eyebrow">Интеграции</p><h1>Integration Setup Center</h1><p>Настройка безопасных подключений внешних систем к Collectra.</p></header>
    <div className="integration-card-grid">
      {hasPermission('INTEGRATION_SOURCE_READ') ? <Link className="integration-card" to="/integrations/sources"><strong>Integration Sources</strong><span>Source, schema, mapping и readiness перед активацией.</span></Link> : null}
      {hasPermission('SERVICE_CLIENT_READ') ? <Link className="integration-card" to="/integrations/service-clients"><strong>Service Clients</strong><span>Учетные данные, scopes и ротация секретов.</span></Link> : null}
    </div>
        {hasPermission('SOURCE_SCHEMA_READ') ? <Link className="integration-card" to="/integrations/source-schemas"><strong>Source Schema Studio</strong><span>Входные схемы и версии.</span></Link> : null}
      {hasPermission('MAPPING_PROFILE_READ') ? <Link className="integration-card" to="/integrations/mapping-profiles"><strong>Mapping Studio</strong><span>Канонические mapping profiles.</span></Link> : null}
    </div>
  </div>;
}
