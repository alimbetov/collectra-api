import { FormEvent, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { createPayment } from '../../entities/receivable/api/receivable.api';
import { receivableKeys } from '../../entities/receivable/api/receivable.queries';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { Button } from '../../shared/ui';

export function PaymentCreatePage(){
 const nav=useNavigate(),qc=useQueryClient();
 const [form,setForm]=useState({customerId:'',externalId:'',paymentDate:'',amount:'',currency:'KZT',paymentReference:'',source:''});
 const m=useMutation({mutationFn:()=>createPayment({...form,paymentReference:form.paymentReference||null,source:form.source||null}),onSuccess:async p=>{await qc.invalidateQueries({queryKey:receivableKeys.paymentLists()});nav(`/receivables/payments/${p.id}`,{replace:true})}});
 const submit=(e:FormEvent)=>{e.preventDefault();m.mutate()};
 return <div className="customer-detail"><header><Link to="/receivables/payments">← Платежи</Link><h1>Новый платёж</h1></header><form className="integration-form" onSubmit={submit}>
  <label>Customer ID<input required value={form.customerId} onChange={e=>setForm({...form,customerId:e.target.value})}/></label>
  <label>External ID<input required value={form.externalId} onChange={e=>setForm({...form,externalId:e.target.value})}/></label>
  <label>Дата<input required type="date" value={form.paymentDate} onChange={e=>setForm({...form,paymentDate:e.target.value})}/></label>
  <label>Сумма<input required inputMode="decimal" value={form.amount} onChange={e=>setForm({...form,amount:e.target.value})}/></label>
  <label>Валюта<input required maxLength={3} value={form.currency} onChange={e=>setForm({...form,currency:e.target.value.toUpperCase()})}/></label>
  <label>Reference<input value={form.paymentReference} onChange={e=>setForm({...form,paymentReference:e.target.value})}/></label>
  <label>Source<input value={form.source} onChange={e=>setForm({...form,source:e.target.value})}/></label>
  <Button type="submit" loading={m.isPending}>Создать</Button>
 </form>{m.error?<ProblemDetailPanel error={m.error}/>:null}</div>;
}
