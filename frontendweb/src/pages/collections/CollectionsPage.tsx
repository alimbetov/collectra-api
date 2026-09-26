import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { collectionQueries } from '../../entities/collection/api/collection.queries';
import type { CollectionCaseStatus, CollectionPriority } from '../../entities/collection/model/collection.types';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { Pagination, Spinner, StatusBadge } from '../../shared/ui';

export function CollectionsPage(){
 const [p,setP]=useSearchParams();
 const page=Math.max(0,Number(p.get('page')??0)||0),size=Number(p.get('size')??50)||50;
 const status=(p.get('status')??'') as ''|CollectionCaseStatus,priority=(p.get('priority')??'') as ''|CollectionPriority;
 const assignedTo=p.get('assignedTo')??'';const overdue=p.get('nextActionOverdue')??'';
 const q=useQuery({...collectionQueries.list({...(status?{status}:{}),...(priority?{priority}:{}),...(assignedTo?{assignedTo}:{}),...(overdue?{nextActionOverdue:overdue==='true'}:{}),page,size,sort:p.get('sort')??'createdAt,desc'}),placeholderData:keepPreviousData});
 const change=(k:string,v:string)=>{const n=new URLSearchParams(p);if(v)n.set(k,v);else n.delete(k);n.set('page','0');setP(n)};
 if(q.error instanceof ApiError&&q.error.status===403)return <Navigate to="/forbidden" replace/>;
 return <div className="customers-page"><header className="customers-page__header"><div><p className="eyebrow">Collections</p><h1>Работа с задолженностью</h1><p>{q.data?`${q.data.totalElements} кейсов`:'Загрузка очереди'}</p></div></header>
 <section className="customer-filters"><div className="customer-filters__quick"><label>Статус <select value={status} onChange={e=>change('status',e.target.value)}><option value="">Все</option>{['OPEN','IN_PROGRESS','ON_HOLD','CLOSED'].map(x=><option key={x}>{x}</option>)}</select></label><label>Приоритет <select value={priority} onChange={e=>change('priority',e.target.value)}><option value="">Все</option>{['LOW','NORMAL','HIGH','URGENT'].map(x=><option key={x}>{x}</option>)}</select></label><label>Assignee ID <input value={assignedTo} onChange={e=>change('assignedTo',e.target.value.trim())}/></label><label>Next action <select value={overdue} onChange={e=>change('nextActionOverdue',e.target.value)}><option value="">Все</option><option value="true">Просрочено</option><option value="false">Не просрочено</option></select></label></div></section>
 {q.isLoading?<Spinner label="Загружаем collection queue…"/>:null}{q.error?<ProblemDetailPanel error={q.error} onRetry={()=>void q.refetch()}/>:null}
 {q.data?<><div className="ui-data-table-scroll"><table className="ui-data-table"><thead><tr><th>Клиент</th><th>Счёт</th><th>Статус</th><th>Приоритет</th><th>Остаток</th><th>Следующее действие</th></tr></thead><tbody>{q.data.items.map(x=><tr key={x.id}><td><Link to={`/customers/${x.customerId}`}>{x.customerDisplayName}</Link></td><td><Link to={`/receivables/invoices/${x.invoiceId}`}>{x.invoiceNumber}</Link></td><td><Link to={`/collections/${x.id}`}><StatusBadge tone={x.status==='CLOSED'?'neutral':x.status==='ON_HOLD'?'warning':'success'}>{x.status}</StatusBadge></Link></td><td>{x.priority}</td><td>{x.outstandingAmount} {x.currency}</td><td>{x.nextActionType??'—'}</td></tr>)}</tbody></table></div><Pagination page={q.data.page} totalPages={q.data.totalPages} onPageChange={x=>{const n=new URLSearchParams(p);n.set('page',String(x));setP(n)}} label="Страницы collection queue" previousLabel="Назад" nextLabel="Далее"/></>:null}</div>;
}
