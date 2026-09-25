import { FormEvent, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { createServiceClient } from '../../entities/integration/api/integration.api';
import { integrationQueries } from '../../entities/integration/api/integration.queries';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { Alert, Button } from '../../shared/ui';

export function ServiceClientCreatePage() {
  const scopes = useQuery(integrationQueries.scopes());
  const [clientId,setClientId]=useState(''); const [name,setName]=useState(''); const [selected,setSelected]=useState<string[]>([]);
  const [secret,setSecret]=useState<string|null>(null); const [ack,setAck]=useState(false);
  const mutation=useMutation({mutationFn:createServiceClient,onSuccess:(r)=>{ setSecret(r.clientSecret); setAck(false); }});
  const submit=(e:FormEvent)=>{e.preventDefault(); if(!mutation.isPending) mutation.mutate({clientId,name,scopes:selected});};
  if(secret) return <div className="integration-page integration-secret"><p className="eyebrow">Service Client создан</p><h1>Сохраните секрет сейчас</h1>
    <Alert variant="warning">После закрытия этой страницы полный секрет больше не будет показан.</Alert>
    <code>{secret}</code>
    <label><input type="checkbox" checked={ack} onChange={e=>setAck(e.target.checked)}/> Я сохранил секрет в безопасном хранилище</label>
    <Link className={ack?'ui-button ui-button--primary':'ui-button ui-button--secondary'} aria-disabled={!ack} onClick={e=>{if(!ack)e.preventDefault();}} to="/integrations/service-clients">Завершить</Link>
  </div>;
  return <div className="integration-page"><Link to="/integrations/service-clients">← Service Clients</Link><h1>Новый Service Client</h1>
    <form className="integration-form" onSubmit={submit}>
      <label>Client ID<input required minLength={3} maxLength={100} pattern="[a-z0-9-]{3,100}" value={clientId} onChange={e=>setClientId(e.target.value)}/></label>
      <label>Название<input required value={name} onChange={e=>setName(e.target.value)}/></label>
      <fieldset><legend>Scopes</legend>{scopes.data?.map(s=><label key={s.code}><input type="checkbox" checked={selected.includes(s.code)} onChange={e=>setSelected(v=>e.target.checked?[...v,s.code]:v.filter(x=>x!==s.code))}/>{s.code}</label>)}</fieldset>
      {scopes.error?<ProblemDetailPanel error={scopes.error} onRetry={()=>void scopes.refetch()}/>:null}
      {mutation.error?<ProblemDetailPanel error={mutation.error}/>:null}
      <Button type="submit" loading={mutation.isPending} disabled={!clientId||!name||selected.length===0}>Создать и выпустить секрет</Button>
    </form>
  </div>;
}
