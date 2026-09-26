import { useQuery } from '@tanstack/react-query';
import { Link, Navigate, useParams } from 'react-router-dom';
import { messageQueries } from '../../entities/message/api/message.queries';
import { messageChannelLabel, messageStatusLabel, messageStatusTone } from '../../features/messages/model/message-display';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { DataTable, Spinner, StatusBadge } from '../../shared/ui';

export function MessageDetailPage(){
 const {campaignId='',runId='',messageId=''}=useParams(); const q=useQuery(messageQueries.detail(campaignId,runId,messageId));
 if(q.error instanceof ApiError&&q.error.status===403)return <Navigate to="/forbidden" replace/>;
 if(q.isLoading)return <Spinner label="Загрузка сообщения"/>; if(q.error)return <ProblemDetailPanel error={q.error} onRetry={()=>void q.refetch()}/>; if(!q.data)return null;
 const m=q.data; const cols=[{key:'name',header:'Файл',render:(a:any)=>a.filename},{key:'type',header:'Content-Type',render:(a:any)=>a.contentType??'—'},{key:'required',header:'Обязательное',render:(a:any)=>a.required?'Да':'Нет'},{key:'status',header:'Готовность',render:(a:any)=><StatusBadge tone={a.status==='READY'?'success':a.status==='FAILED'?'danger':'warning'}>{a.status}</StatusBadge>}];
 return <div className="message-detail-page"><header className="dashboard-page__header"><div><Link to={`/campaigns/${campaignId}/runs/${runId}/messages`}>← Сообщения</Link><p className="eyebrow">Message delivery</p><h1>{m.maskedDestination}</h1></div><StatusBadge tone={messageStatusTone(m.status)}>{messageStatusLabel(m.status)}</StatusBadge></header>
 <section className="customer-detail-card"><dl className="customer-detail-fields"><div><dt>Канал</dt><dd>{messageChannelLabel(m.channel)}</dd></div><div><dt>Попыток</dt><dd>{m.attemptCount}</dd></div><div><dt>Locale</dt><dd>{m.resolvedLocale??'—'}</dd></div><div><dt>Provider message ID</dt><dd>{m.providerMessageId??'—'}</dd></div><div><dt>Processing</dt><dd>{m.processingStartedAt??'—'}</dd></div><div><dt>Next retry</dt><dd>{m.nextRetryAt??'—'}</dd></div><div><dt>Sent</dt><dd>{m.sentAt??'—'}</dd></div></dl></section>
 {m.lastErrorCode||m.lastErrorSummary?<section className="customer-detail-card"><h2>Ошибка доставки</h2><p><strong>{m.lastErrorCode??'DELIVERY_ERROR'}</strong></p><p>{m.lastErrorSummary??'—'}</p></section>:null}
 <section className="customer-detail-card"><h2>Вложения</h2><DataTable columns={cols} rows={m.attachments} rowKey={a=>a.id} emptyTitle="Вложений нет"/></section></div>;
}
