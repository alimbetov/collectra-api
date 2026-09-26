export function messageStatusLabel(value: string) {
  const labels: Record<string,string>={PENDING:'Ожидает',PROCESSING:'Обрабатывается',RETRY_WAIT:'Ожидает повтора',SENT:'Отправлено',FAILED:'Ошибка',SKIPPED:'Пропущено'};
  return labels[value] ?? value;
}
export function messageStatusTone(value:string):'success'|'danger'|'warning'|'info'|'neutral' {
  if(value==='SENT') return 'success'; if(value==='FAILED') return 'danger'; if(value==='PROCESSING') return 'info'; if(value==='RETRY_WAIT') return 'warning'; return 'neutral';
}
export function messageChannelLabel(value:string){const labels:Record<string,string>={EMAIL:'Email',SMS:'SMS',TELEGRAM:'Telegram',WHATSAPP:'WhatsApp'};return labels[value]??value;}
