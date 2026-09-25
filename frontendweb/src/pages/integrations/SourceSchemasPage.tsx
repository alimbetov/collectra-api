import {FormEvent,useState} from 'react'; import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {createSourceSchema} from '../../entities/integration/api/integration.api'; import {integrationKeys,integrationQueries} from '../../entities/integration/api/integration.queries'; import {ProblemDetailPanel} from '../../shared/errors/ProblemDetailPanel';
export function SourceSchemasPage(){const qc=useQueryClient(),q=useQuery(integrationQueries.sourceSchemas()); const [code,setCode]=useState(''),[name,setName]=useState('');
 const m=useMutation({mutationFn:createSourceSchema,onSuccess:async()=>{setCode('');setName('');await qc.invalidateQueries({queryKey:integrationKeys.sourceSchemas()})}});
 const submit=(e:FormEvent)=>{e.preventDefault();if(!m.isPending)m.mutate({code,name})};
 return <div className="integration-page"><header><p className="eyebrow">Integration Setup Center</p><h1>Source Schema Studio</h1><p>Версионируемые входные схемы. Публикация версии является readiness prerequisite.</p></header>
 <form className="integration-form" onSubmit={submit}><label>Code<input required value={code} onChange={e=>setCode(e.target.value)}/></label><label>Название<input required value={name} onChange={e=>setName(e.target.value)}/></label><button disabled={m.isPending}>Создать schema</button></form>
 {m.error?<ProblemDetailPanel error={m.error}/>:null}{q.error?<ProblemDetailPanel error={q.error} onRetry={()=>void q.refetch()}/>:null}
 <div className="integration-list">{q.data?.map(x=><div className="integration-list__row" key={x.id}><div><strong>{x.name}</strong><span>{x.code}</span></div><code>{x.id}</code></div>)}</div></div>}
