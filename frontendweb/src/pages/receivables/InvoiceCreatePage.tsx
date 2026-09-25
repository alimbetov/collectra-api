import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createInvoice, getInvoiceByExternalId } from '../../entities/receivable/api/receivable.api';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { Button, FormField } from '../../shared/ui';

export function InvoiceCreatePage() {
  const nav = useNavigate(); const [busy,setBusy]=useState(false); const [error,setError]=useState<unknown>(null);
  const [customerId,setCustomerId]=useState(''); const [contractId,setContractId]=useState('');
  const [externalId,setExternalId]=useState(''); const [invoiceNumber,setInvoiceNumber]=useState('');
  const [invoiceDate,setInvoiceDate]=useState(''); const [dueDate,setDueDate]=useState('');
  const [amount,setAmount]=useState(''); const [currency,setCurrency]=useState('KZT');

  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError(null);
    const command={customerId,contractId:contractId||null,externalId:externalId.trim(),invoiceNumber:invoiceNumber.trim(),invoiceDate:invoiceDate||null,dueDate,originalAmount:amount,currency:currency.toUpperCase(),documentFileId:null,customFields:null};
    try {
      const created=await createInvoice(command); nav(`/receivables/invoices/${created.id}`,{replace:true});
    } catch (x) {
      // POST may have committed while the response was lost. Reconcile by the tenant-scoped unique externalId.
      try {
        const existing=await getInvoiceByExternalId(command.externalId);
        nav(`/receivables/invoices/${existing.id}`,{replace:true});
      } catch { setError(x); }
    } finally { setBusy(false); }
  }
  return <div className="customers-page"><header><p className="eyebrow">Receivables</p><h1>Новый счёт</h1><p>При неопределённом результате POST клиент сверяет результат по external ID перед повторным созданием.</p></header>{error ? <ProblemDetailPanel error={error}/> : null}<form className="integration-form" onSubmit={submit}>
    <FormField label="Customer ID"><input required value={customerId} onChange={e=>setCustomerId(e.target.value)}/></FormField>
    <FormField label="Contract ID"><input value={contractId} onChange={e=>setContractId(e.target.value)}/></FormField>
    <FormField label="External ID"><input required value={externalId} onChange={e=>setExternalId(e.target.value)}/></FormField>
    <FormField label="Номер счёта"><input required value={invoiceNumber} onChange={e=>setInvoiceNumber(e.target.value)}/></FormField>
    <FormField label="Дата счёта"><input type="date" value={invoiceDate} onChange={e=>setInvoiceDate(e.target.value)}/></FormField>
    <FormField label="Срок оплаты"><input required type="date" value={dueDate} onChange={e=>setDueDate(e.target.value)}/></FormField>
    <FormField label="Сумма"><input required inputMode="decimal" pattern="[0-9]+(\\.[0-9]{1,4})?" value={amount} onChange={e=>setAmount(e.target.value)}/></FormField>
    <FormField label="Валюта"><input required minLength={3} maxLength={3} value={currency} onChange={e=>setCurrency(e.target.value.toUpperCase())}/></FormField>
    <Button type="submit" disabled={busy}>{busy?'Создание…':'Создать счёт'}</Button>
  </form></div>;
}
