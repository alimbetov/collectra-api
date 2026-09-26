import { useQuery } from '@tanstack/react-query';
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom';
import { messageQueries } from '../../entities/message/api/message.queries';
import { messageChannelLabel, messageStatusLabel, messageStatusTone } from '../../features/messages/model/message-display';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { DataTable, Button, Spinner, StatusBadge } from '../../shared/ui';

const statuses=['PENDING','PROCESSING','RETRY_WAIT','SENT','FAILED','SKIPPED'];
const channels=['EMAIL','SMS','TELEGRAM','WHATSAPP'];

export function MessagesPage(){
 const {campaignId='',runId=''}=useParams(); const [sp,setSp]=useSearchParams();
 const page=Math.max(0,Number(sp.get('page')??0)||0); const status=sp.get('status')||undefined; const channel=sp.get('channel')||undefined; const customerId=sp.get('customerId')||undefined;
 const q=useQuery(messageQueries.list(campaignId,runId,{status,channel,customerId,page,size:50}));
 const update=(k:string,v?:string)=>{const n=new URLSearchParams(sp);v?n.set(k,v):n.delete(k);if(k!=='page')n.delete('page');setSp(n)};
 if(q.error instanceof ApiError&&q.error.status===403)return <Navigate to="/forbidden" replace/>;
 const columns=[
  {key:'status',header:'Статус',render:(r:any)=><StatusBadge tone={messageStatusTone(r.status)}>{messageStatusLabel(r.status)}</StatusBadge>},
  {key:'destination',header:'Получатель',render:(r:any)=><Link className="table-link" to={r.id}>{r.maskedDestination}</Link>},
  {key:'channel',header:'Канал',render:(r:any)=>messageChannelLabel(r.channel)},
  {key:'customer',header:'Customer ID',render:(r:any)=><Link to={`/customers/${r.customerId}`}>{r.customerId}</Link>},
  {key:'attempts',header:'Попыток',render:(r:any)=>r.attemptCount},
 ];
 return <div className="messages-page"><header className="dashboard-page__header"><div><Link to={`/campaigns/${campaignId}/runs/${runId}`}>← Campaign run</Link><p className="eyebrow">Message monitoring</p><h1>Сообщения</h1></div></header>
 <div className="platform-tenant-filters"><label><span>Статус</span><select value={status??''} onChange={e=>update('status',e.target.value||undefined)}><option value="">Все</option>{statuses.map(v=><option key={v}>{v}</option>)}</select></label><label><span>Канал</span><select value={channel??''} onChange={e=>update('channel',e.target.value||undefined)}><option value="">Все</option>{channels.map(v=><option key={v}>{v}</option>)}</select></label><label><span>Customer ID</span><input value={customerId??''} onChange={e=>update('customerId',e.target.value||undefined)}/></label></div>
 {q.isLoading?<Spinner label="Загрузка сообщений"/>:null}{q.error?<ProblemDetailPanel error={q.error} onRetry={()=>void q.refetch()}/>:null}
 {q.data?<><DataTable columns={columns} rows={q.data.content} rowKey={r=>r.id} emptyTitle="Сообщений нет"/><div className="form-actions"><Button type="button" variant="secondary" disabled={page===0} onClick={()=>update('page',page>1?String(page-1):undefined)}>Назад</Button><span>Страница {page+1}</span><Button type="button" variant="secondary" disabled={!q.data.hasNext} onClick={()=>update('page',String(page+1))}>Вперёд</Button></div></>:null}</div>;
}
