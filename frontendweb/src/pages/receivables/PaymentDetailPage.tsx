import { FormEvent, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { allocatePayment, reversePaymentAllocation } from '../../entities/receivable/api/receivable.api';
import { receivableKeys, receivableQueries } from '../../entities/receivable/api/receivable.queries';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { Button, Pagination, Spinner, StatusBadge } from '../../shared/ui';

export function PaymentDetailPage(){
 const {paymentId=''}=useParams(),qc=useQueryClient();const [page,setPage]=useState(0);const [invoiceId,setInvoiceId]=useState('');const [amount,setAmount]=useState('');const [reason,setReason]=useState('Customer request');const [intentId,setIntentId]=useState(()=>crypto.randomUUID());
 const detail=useQuery({...receivableQueries.payment(paymentId),enabled:Boolean(paymentId)});const allocations=useQuery({...receivableQueries.paymentAllocations(paymentId,page),enabled:Boolean(detail.data)});
 const refresh=async()=>{await Promise.all([qc.invalidateQueries({queryKey:receivableKeys.paymentDetail(paymentId)}),qc.invalidateQueries({queryKey:receivableKeys.paymentAllocations(paymentId,page)}),qc.invalidateQueries({queryKey:receivableKeys.invoiceLists()})])};
 const alloc=useMutation({mutationFn:()=>allocatePayment(paymentId,{commandId:intentId,invoiceId,amount}),onSuccess:async()=>{setInvoiceId('');setAmount('');setIntentId(crypto.randomUUID());await refresh()}});
 const reverse=useMutation({mutationFn:(x:{id:string;version:number})=>reversePaymentAllocation(paymentId,x.id,{version:x.version,reason}),onSuccess:refresh,onError:async e=>{if(e instanceof ApiError&&e.problem?.code==='VERSION_CONFLICT')await allocations.refetch()}});
 const submit=(e:FormEvent)=>{e.preventDefault();alloc.mutate()};
 if(detail.isLoading)return <Spinner label="Загружаем платёж…"/>;if(detail.error)return <ProblemDetailPanel error={detail.error} onRetry={()=>void detail.refetch()}/>;if(!detail.data)return null;const p=detail.data;
 return <div className="customer-detail"><header className="customer-detail__header"><div><Link to="/receivables/payments">← Платежи</Link><p className="eyebrow">{p.externalId}</p><h1>{p.amount} {p.currency}</h1></div></header>
  <section className="customer-detail-card"><p><Link to={`/customers/${p.customerId}`}>Клиент</Link></p><p>{p.paymentDate}</p><p>{p.paymentReference??'—'}</p></section>
  <section className="customer-detail-card"><h2>Распределить платёж</h2><form onSubmit={submit}><input required placeholder="Invoice ID" value={invoiceId} onChange={e=>setInvoiceId(e.target.value)}/><input required inputMode="decimal" placeholder="Amount" value={amount} onChange={e=>setAmount(e.target.value)}/><Button type="submit" loading={alloc.isPending}>Распределить</Button></form>{alloc.error?<ProblemDetailPanel error={alloc.error}/>:null}</section>
  <section className="customer-detail-card"><h2>Распределения</h2><label>Причина reversal<input value={reason} onChange={e=>setReason(e.target.value)}/></label>{allocations.isLoading?<Spinner label="Загружаем распределения…"/>:null}{allocations.error?<ProblemDetailPanel error={allocations.error} onRetry={()=>void allocations.refetch()}/>:null}{allocations.data?<><table><thead><tr><th>Invoice</th><th>Amount</th><th>Status</th><th></th></tr></thead><tbody>{allocations.data.items.map(a=><tr key={a.id}><td><Link to={`/receivables/invoices/${a.invoiceId}`}>{a.invoiceId}</Link></td><td>{a.amount} {p.currency}</td><td><StatusBadge tone={a.status==='ACTIVE'?'success':'neutral'}>{a.status}</StatusBadge></td><td>{a.status==='ACTIVE'?<Button variant="secondary" loading={reverse.isPending} onClick={()=>reverse.mutate({id:a.id,version:a.version})}>Reverse</Button>:null}</td></tr>)}</tbody></table><Pagination page={allocations.data.page} totalPages={allocations.data.totalPages} onPageChange={setPage}/></>:null}{reverse.error?<ProblemDetailPanel error={reverse.error}/>:null}</section>
 </div>;
}
