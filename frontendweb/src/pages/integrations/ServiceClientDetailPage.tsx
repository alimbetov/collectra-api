import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { activateServiceClientCredential, blockServiceClient, rotateServiceClientSecret, unblockServiceClient } from '../../entities/integration/api/integration.api';
import { integrationKeys, integrationQueries } from '../../entities/integration/api/integration.queries';
import { useAuth } from '../../features/auth/model/auth-context';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { Alert, Button, ConfirmDialog, Spinner, StatusBadge } from '../../shared/ui';

export function ServiceClientDetailPage() {
 const {clientId=''}=useParams(); const {hasPermission}=useAuth(); const qc=useQueryClient(); const q=useQuery(integrationQueries.serviceClient(clientId));
 const [confirm,setConfirm]=useState<'block'|'unblock'|null>(null); const [secret,setSecret]=useState<string|null>(null); const [issuedCredential,setIssuedCredential]=useState<string|null>(null); const [ack,setAck]=useState(false);
 const refresh=async()=>{await qc.invalidateQueries({queryKey:integrationKeys.serviceClients()}); await qc.invalidateQueries({queryKey:integrationKeys.serviceClient(clientId)});};
 const block=useMutation({mutationFn:()=>q.data?.status==='BLOCKED'?unblockServiceClient(clientId):blockServiceClient(clientId),onSuccess:async()=>{setConfirm(null);await refresh();}});
 const rotate=useMutation({mutationFn:()=>rotateServiceClientSecret(clientId,{}),onSuccess:r=>{setSecret(r.clientSecret);setIssuedCredential(r.client.credentialId);setAck(false);}});
 const activate=useMutation({mutationFn:()=>activateServiceClientCredential(clientId,issuedCredential!),onSuccess:async()=>{setSecret(null);setIssuedCredential(null);setAck(false);await refresh();}});
 if(q.isLoading)return <Spinner label="Загружаем Service Client…"/>; if(q.error)return <ProblemDetailPanel error={q.error} onRetry={()=>void q.refetch()}/>; if(!q.data)return null; const c=q.data;
 return <div className="integration-page"><Link to="/integrations/service-clients">← Service Clients</Link><header className="integration-page__header"><div><p className="eyebrow">{c.clientId}</p><h1>{c.name}</h1></div><StatusBadge tone={c.status==='ACTIVE'?'success':'warning'}>{c.status}</StatusBadge></header>
  <dl className="integration-facts"><div><dt>Scopes</dt><dd>{c.scopes.join(', ')}</dd></div><div><dt>Secret hint</dt><dd>{c.secretHint??'—'}</dd></div><div><dt>Credential</dt><dd>{c.credentialStatus??'—'}</dd></div><div><dt>Последнее использование</dt><dd>{c.lastUsedAt??'—'}</dd></div></dl>
  <div className="integration-actions">{hasPermission('SERVICE_CLIENT_ROTATE_SECRET')?<Button variant="secondary" loading={rotate.isPending} onClick={()=>rotate.mutate()}>Ротировать секрет</Button>:null}{hasPermission('SERVICE_CLIENT_BLOCK')?<Button variant={c.status==='BLOCKED'?'secondary':'danger'} onClick={()=>setConfirm(c.status==='BLOCKED'?'unblock':'block')}>{c.status==='BLOCKED'?'Разблокировать':'Заблокировать'}</Button>:null}</div>
  {secret?<div className="integration-secret"><Alert variant="warning">Новый секрет показывается только сейчас. Сохраните его до активации.</Alert><code>{secret}</code><label><input type="checkbox" checked={ack} onChange={e=>setAck(e.target.checked)}/> Секрет сохранён</label><Button disabled={!ack||!issuedCredential} loading={activate.isPending} onClick={()=>activate.mutate()}>Активировать credential</Button></div>:null}
  {(block.error||rotate.error||activate.error)?<ProblemDetailPanel error={block.error??rotate.error??activate.error}/>:null}
  <ConfirmDialog open={confirm!==null} title={confirm==='block'?'Заблокировать Service Client?':'Разблокировать Service Client?'} confirmLabel={confirm==='block'?'Заблокировать':'Разблокировать'} cancelLabel="Отмена" closeLabel="Закрыть" destructive={confirm==='block'} pending={block.isPending} onCancel={()=>setConfirm(null)} onConfirm={()=>block.mutate()}>Изменение применяется сервером и влияет на машинную аутентификацию.</ConfirmDialog>
 </div>;
}
