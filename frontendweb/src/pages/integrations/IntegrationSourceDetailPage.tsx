import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { activateIntegrationSource, archiveIntegrationSource, suspendIntegrationSource } from '../../entities/integration/api/integration.api';
import { integrationKeys, integrationQueries } from '../../entities/integration/api/integration.queries';
import { useAuth } from '../../features/auth/model/auth-context';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { Spinner, StatusBadge } from '../../shared/ui';

export function IntegrationSourceDetailPage() {
  const id=useParams().sourceId??'', qc=useQueryClient(), {hasPermission}=useAuth();
  const source=useQuery(integrationQueries.source(id)), ready=useQuery(integrationQueries.readiness(id));
  const refresh=async()=>{await qc.invalidateQueries({queryKey:integrationKeys.source(id)});await qc.invalidateQueries({queryKey:integrationKeys.readiness(id)});await qc.invalidateQueries({queryKey:integrationKeys.sources()})};
  const action=useMutation({mutationFn:(kind:'activate'|'suspend'|'archive')=>{const v=source.data!.version; return kind==='activate'?activateIntegrationSource(id,v):kind==='suspend'?suspendIntegrationSource(id,v):archiveIntegrationSource(id,v)},onSuccess:refresh});
  if(source.isLoading)return <Spinner label="Загружаем Integration Source…" />;
  if(source.error)return <ProblemDetailPanel error={source.error} onRetry={()=>void source.refetch()} />;
  if(!source.data)return null; const s=source.data;
  return <div className="integration-page"><header className="integration-page__header"><div><p className="eyebrow">Integration Source</p><h1>{s.name}</h1><p>{s.code}</p></div><StatusBadge tone={s.status==='ACTIVE'?'success':'warning'}>{s.status}</StatusBadge></header>
    <section className="integration-panel"><h2>Readiness</h2>{ready.isLoading?<Spinner label="Проверяем готовность…" />:null}{ready.error?<ProblemDetailPanel error={ready.error} onRetry={()=>void ready.refetch()}/>:null}
      {ready.data?<><p><strong>{ready.data.ready?'READY':'BLOCKED'}</strong></p><div className="integration-list">{ready.data.checks.map(c=><div className="integration-list__row" key={c.code}><div><strong>{c.code}</strong><span>{c.resource}</span></div><StatusBadge tone={c.state==='READY'?'success':'warning'}>{c.state}</StatusBadge></div>)}</div></>:null}
    </section>
    <section className="integration-panel"><h2>Связи</h2><dl><dt>Service Client</dt><dd>{s.serviceClientId}</dd><dt>Source Schema</dt><dd>{s.sourceSchemaDefinitionId}</dd><dt>Mapping Profile</dt><dd>{s.mappingProfileDefinitionId}</dd><dt>Version</dt><dd>{s.version}</dd></dl></section>
    {hasPermission('INTEGRATION_SOURCE_MANAGE')?<div className="integration-actions">{s.status!=='ACTIVE'?<button disabled={action.isPending||!ready.data?.ready} onClick={()=>action.mutate('activate')}>Активировать</button>:<button disabled={action.isPending} onClick={()=>action.mutate('suspend')}>Приостановить</button>}<button disabled={action.isPending||s.status==='ARCHIVED'} onClick={()=>action.mutate('archive')}>Архивировать</button></div>:null}
    {action.error?<ProblemDetailPanel error={action.error}/>:null}
  </div>;
}
