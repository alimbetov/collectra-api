import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { createIntegrationSource } from '../../entities/integration/api/integration.api';
import { integrationKeys, integrationQueries } from '../../entities/integration/api/integration.queries';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';

export function IntegrationSourceCreatePage() {
  const nav=useNavigate(), qc=useQueryClient();
  const clients=useQuery(integrationQueries.serviceClients()), schemas=useQuery(integrationQueries.sourceSchemas()), mappings=useQuery(integrationQueries.mappingProfiles());
  const [code,setCode]=useState(''); const [name,setName]=useState(''); const [client,setClient]=useState(''); const [schema,setSchema]=useState(''); const [mapping,setMapping]=useState('');
  const mutation=useMutation({mutationFn:createIntegrationSource,onSuccess:async s=>{await qc.invalidateQueries({queryKey:integrationKeys.sources()});nav('/integrations/sources/'+s.id)}});
  const submit=(e:FormEvent)=>{e.preventDefault(); if(mutation.isPending)return; mutation.mutate({code,name,serviceClientId:client,sourceSchemaDefinitionId:schema,mappingProfileDefinitionId:mapping,processingMode:'STANDARD',headerMapping:{},resourcePolicy:{},routingConfig:{}})};
  return <div className="integration-page"><header><p className="eyebrow">Integration Sources</p><h1>Новый источник</h1></header>
    <form className="integration-form" onSubmit={submit}>
      <label>Code<input required pattern="[a-z0-9-]{3,100}" value={code} onChange={e=>setCode(e.target.value)} /></label>
      <label>Название<input required value={name} onChange={e=>setName(e.target.value)} /></label>
      <label>Service Client<select required value={client} onChange={e=>setClient(e.target.value)}><option value="">Выберите</option>{clients.data?.map(x=><option key={x.id} value={x.id}>{x.name} ({x.clientId})</option>)}</select></label>
      <label>Source Schema<select required value={schema} onChange={e=>setSchema(e.target.value)}><option value="">Выберите</option>{schemas.data?.map(x=><option key={x.id} value={x.id}>{x.name} ({x.code})</option>)}</select></label>
      <label>Mapping Profile<select required value={mapping} onChange={e=>setMapping(e.target.value)}><option value="">Выберите</option>{mappings.data?.map(x=><option key={x.id} value={x.id}>{x.name} · {x.documentType}</option>)}</select></label>
      {mutation.error?<ProblemDetailPanel error={mutation.error}/>:null}
      <button className="ui-button ui-button--primary" disabled={mutation.isPending || clients.isLoading || schemas.isLoading || mappings.isLoading}>{mutation.isPending?'Создание…':'Создать'}</button>
    </form>
  </div>;
}
