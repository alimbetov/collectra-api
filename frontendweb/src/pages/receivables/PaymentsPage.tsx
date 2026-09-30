import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { receivableQueries } from '../../entities/receivable/api/receivable.queries';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { Pagination, Spinner } from '../../shared/ui';

export function PaymentsPage() {
  const [sp,setSp]=useSearchParams();
  const page=Math.max(0,Number(sp.get('page')??0)||0);
  const search=sp.get('search')?.trim()||undefined;
  const query=useQuery({...receivableQueries.payments({page,size:50,sort:'createdAt,desc',search}),placeholderData:keepPreviousData});
  const update=(key:string,value?:string)=>{const n=new URLSearchParams(sp);value?n.set(key,value):n.delete(key);if(key!=='page')n.delete('page');setSp(n)};
  return <div className="customers-page receivables-page">
    <header className="customers-page__header"><div><p className="eyebrow">Payments</p><h1>Платежи</h1></div><Link className="ui-button" to="/receivables/payments/new">Новый платёж</Link></header>
    <section className="customer-filters"><input type="search" placeholder="Поиск" defaultValue={search??''} onChange={e=>update('search',e.target.value||undefined)} /></section>
    {query.isLoading?<Spinner label="Загружаем платежи…"/>:null}
    {query.error?<ProblemDetailPanel error={query.error} onRetry={()=>void query.refetch()}/>:null}
    {query.data?<><table><thead><tr><th>Дата</th><th>Клиент</th><th>External ID</th><th>Сумма</th><th>Reference</th></tr></thead><tbody>{query.data.items.map(p=><tr key={p.id}><td>{p.paymentDate}</td><td>{p.customerDisplayName}</td><td><Link to={`/receivables/payments/${p.id}`}>{p.externalId}</Link></td><td>{p.amount} {p.currency}</td><td>{p.paymentReference??'—'}</td></tr>)}</tbody></table><Pagination page={query.data.page} totalPages={query.data.totalPages} onPageChange={p=>update('page',p?String(p):undefined)} /></>:null}
  </div>;
}
