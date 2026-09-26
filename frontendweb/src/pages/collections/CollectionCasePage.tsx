import { useMutation,useQuery,useQueryClient } from '@tanstack/react-query';
import { Link,Navigate,useParams } from 'react-router-dom';
import { useState } from 'react';
import { collectionKeys,collectionQueries } from '../../entities/collection/api/collection.queries';
import { closeCollectionCase,createAction,createDispute,createPromise,transitionAction,transitionCollectionCase,transitionPromise,cancelDispute,resolveDispute } from '../../entities/collection/api/collection.api';
import type { CollectionCloseReason } from '../../entities/collection/model/collection.types';
import { useAuth } from '../../features/auth/model/auth-context';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { Button,Spinner,StatusBadge } from '../../shared/ui';

export function CollectionCasePage(){
 const {caseId=''}=useParams();const client=useQueryClient();const {hasPermission}=useAuth();const canManage=hasPermission('COLLECTION_MANAGE');
 const detail=useQuery({...collectionQueries.detail(caseId),enabled:Boolean(caseId)});
 const promises=useQuery({...collectionQueries.promises(caseId),enabled:Boolean(detail.data)});
 const disputes=useQuery({...collectionQueries.disputes(caseId),enabled:Boolean(detail.data)});
 const actions=useQuery({...collectionQueries.actions(caseId),enabled:Boolean(detail.data)});
 const timeline=useQuery({...collectionQueries.timeline(caseId),enabled:Boolean(detail.data)});
 const [promise,setPromise]=useState({amount:'',currency:'KZT',promisedDate:''});
 const [dispute,setDispute]=useState({reason:'',description:''});
 const [action,setAction]=useState({actionType:'CALL',description:'',dueAt:'',priority:'NORMAL'});
 const refresh=async()=>{await Promise.all([client.invalidateQueries({queryKey:collectionKeys.detail(caseId)}),client.invalidateQueries({queryKey:collectionKeys.lists()}),client.invalidateQueries({queryKey:collectionKeys.promises(caseId)}),client.invalidateQueries({queryKey:collectionKeys.disputes(caseId)}),client.invalidateQueries({queryKey:collectionKeys.actions(caseId)}),client.invalidateQueries({queryKey:collectionKeys.timeline(caseId)})])};
 const command=useMutation({mutationFn:async(v:{kind:'start'|'hold'|'close';reason?:CollectionCloseReason})=>v.kind==='close'?closeCollectionCase(caseId,detail.data?.version??-1,v.reason??'OTHER'):transitionCollectionCase(caseId,v.kind,detail.data?.version??-1),onSuccess:refresh});
 const promiseCreate=useMutation({mutationFn:()=>createPromise(caseId,promise),onSuccess:async()=>{setPromise({amount:'',currency:'KZT',promisedDate:''});await refresh()}});
 const promiseCommand=useMutation({mutationFn:(v:{id:string;cmd:'fulfill'|'break'|'cancel';version:number})=>transitionPromise(caseId,v.id,v.cmd,v.version),onSuccess:refresh});
 const disputeCreate=useMutation({mutationFn:()=>createDispute(caseId,dispute),onSuccess:async()=>{setDispute({reason:'',description:''});await refresh()}});
 const disputeCommand=useMutation({mutationFn:(v:{id:string;cmd:'resolve'|'cancel';version:number})=>v.cmd==='cancel'?cancelDispute(caseId,v.id,v.version):resolveDispute(caseId,v.id,{version:v.version,resolutionCode:'RESOLVED'}),onSuccess:refresh});
 const actionCreate=useMutation({mutationFn:()=>createAction(caseId,{...action,dueAt:new Date(action.dueAt).toISOString()}),onSuccess:async()=>{setAction({actionType:'CALL',description:'',dueAt:'',priority:'NORMAL'});await refresh()}});
 const actionCommand=useMutation({mutationFn:(v:{id:string;cmd:'complete'|'cancel';version:number})=>transitionAction(caseId,v.id,v.cmd,v.version),onSuccess:refresh});
 const allErrors=[detail.error,promises.error,disputes.error,actions.error,timeline.error,command.error,promiseCreate.error,disputeCreate.error,actionCreate.error];
 if(allErrors.some(e=>e instanceof ApiError&&e.status===403))return <Navigate to="/forbidden" replace/>;
 if(detail.error instanceof ApiError&&detail.error.status===404)return <div><h1>Collection case not found</h1><Link to="/collections">Назад</Link></div>;
 if(detail.isLoading)return <Spinner label="Загружаем collection case…"/>;
 if(detail.error)return <ProblemDetailPanel error={detail.error} onRetry={()=>void detail.refetch()}/>;
 const x=detail.data;if(!x)return null;
 return <div className="customer-detail"><header className="customer-detail__header"><div><Link to="/collections">← Collections</Link><h1>Collection case</h1><p>{x.id}</p></div><div><StatusBadge tone={x.status==='CLOSED'?'neutral':x.status==='ON_HOLD'?'warning':'success'}>{x.status}</StatusBadge>{canManage&&x.status==='OPEN'?<Button onClick={()=>command.mutate({kind:'start'})}>Start</Button>:null}{canManage&&x.status==='IN_PROGRESS'?<Button variant="secondary" onClick={()=>command.mutate({kind:'hold'})}>Hold</Button>:null}{canManage&&x.status!=='CLOSED'?<Button variant="danger" onClick={()=>command.mutate({kind:'close',reason:'OTHER'})}>Close</Button>:null}</div></header>
 {command.error?<ProblemDetailPanel error={command.error} onRetry={()=>void detail.refetch()}/>:null}
 <div className="customer-detail-grid"><section className="customer-detail-card"><h2>Связи</h2><p><Link to={`/customers/${x.customerId}`}>Customer</Link></p><p><Link to={`/receivables/invoices/${x.invoiceId}`}>Invoice</Link></p></section><section className="customer-detail-card"><h2>Case</h2><p>Priority: {x.priority}</p><p>Version: {x.version}</p><p>Assignee: {x.assignedTo??'—'}</p></section></div>
 <section className="customer-detail-card"><h2>Promises</h2>{canManage?<form onSubmit={e=>{e.preventDefault();promiseCreate.mutate()}}><input required placeholder="Amount" value={promise.amount} onChange={e=>setPromise({...promise,amount:e.target.value})}/><input required maxLength={3} value={promise.currency} onChange={e=>setPromise({...promise,currency:e.target.value.toUpperCase()})}/><input required type="date" value={promise.promisedDate} onChange={e=>setPromise({...promise,promisedDate:e.target.value})}/><Button type="submit">Add promise</Button></form>:null}{promises.data?.items.map(v=><p key={v.id}>{v.amount} {v.currency} · {v.promisedDate} · {v.status} {canManage&&v.status==='OPEN'?<><Button variant="secondary" onClick={()=>promiseCommand.mutate({id:v.id,cmd:'fulfill',version:v.version})}>Fulfill</Button><Button variant="secondary" onClick={()=>promiseCommand.mutate({id:v.id,cmd:'break',version:v.version})}>Break</Button><Button variant="secondary" onClick={()=>promiseCommand.mutate({id:v.id,cmd:'cancel',version:v.version})}>Cancel</Button></>:null}</p>)}</section>
 <section className="customer-detail-card"><h2>Disputes</h2>{canManage?<form onSubmit={e=>{e.preventDefault();disputeCreate.mutate()}}><input required placeholder="Reason" value={dispute.reason} onChange={e=>setDispute({...dispute,reason:e.target.value})}/><input placeholder="Description" value={dispute.description} onChange={e=>setDispute({...dispute,description:e.target.value})}/><Button type="submit">Open dispute</Button></form>:null}{disputes.data?.items.map(v=><p key={v.id}>{v.reason} · {v.status} {canManage&&v.status==='OPEN'?<><Button variant="secondary" onClick={()=>disputeCommand.mutate({id:v.id,cmd:'resolve',version:v.version})}>Resolve</Button><Button variant="secondary" onClick={()=>disputeCommand.mutate({id:v.id,cmd:'cancel',version:v.version})}>Cancel</Button></>:null}</p>)}</section>
 <section className="customer-detail-card"><h2>Actions</h2>{canManage?<form onSubmit={e=>{e.preventDefault();actionCreate.mutate()}}><input required value={action.actionType} onChange={e=>setAction({...action,actionType:e.target.value})}/><input value={action.description} onChange={e=>setAction({...action,description:e.target.value})}/><input required type="datetime-local" value={action.dueAt} onChange={e=>setAction({...action,dueAt:e.target.value})}/><Button type="submit">Add action</Button></form>:null}{actions.data?.items.map(v=><p key={v.id}>{v.actionType} · {v.status} · {v.dueAt} {canManage&&v.status==='OPEN'?<><Button variant="secondary" onClick={()=>actionCommand.mutate({id:v.id,cmd:'complete',version:v.version})}>Complete</Button><Button variant="secondary" onClick={()=>actionCommand.mutate({id:v.id,cmd:'cancel',version:v.version})}>Cancel</Button></>:null}</p>)}</section>
 <section className="customer-detail-card"><h2>Timeline</h2>{timeline.isLoading?<Spinner label="Загружаем timeline…"/>:null}{timeline.data?.items.map(v=><p key={v.eventId}>{v.eventAt} · {v.eventType} · {v.summary}</p>)}</section>
 </div>;
}
