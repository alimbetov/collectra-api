import { Link } from 'react-router-dom';
import { useI18n } from '../../shared/i18n/i18n-context';

type JourneyStep = {
  number: number;
  title: string;
  description: string;
  status: 'available' | 'planned';
  href?: string;
  action: string;
};

const steps: JourneyStep[] = [
  { number: 1, title: 'Источник данных', description: 'Опишите внешнюю систему, из которой Collectra будет принимать данные. Источник объединит credentials, схему, mapping и правила обработки.', status: 'planned', action: 'Настроить источник' },
  { number: 2, title: 'Доступ API', description: 'Подключите сервисную учётную запись и scopes. Внешняя система получает стабильную точку входа, не зная внутренних UUID Collectra.', status: 'planned', action: 'Настроить доступ' },
  { number: 3, title: 'Формат входных данных', description: 'Определите поля и структуру JSON, XML, CSV или XLSX. Это контракт того, что Collectra должен уметь прочитать.', status: 'planned', action: 'Описать схему' },
  { number: 4, title: 'Сопоставление полей', description: 'Свяжите поля источника с каноническими полями Collectra и проверьте преобразования на тестовых данных.', status: 'planned', action: 'Настроить mapping' },
  { number: 5, title: 'Файлы и материалы', description: 'Загрузите логотипы и изображения для шаблонов. Внешние URL с режимом IMPORT будут безопасно переноситься во внутреннее хранилище.', status: 'available', href: '/templates/assets', action: 'Открыть assets' },
  { number: 6, title: 'Шаблоны', description: 'Подготовьте персонализированный EMAIL, SMS, WhatsApp или документ. Preview позволяет проверить результат до отправки.', status: 'available', href: '/templates', action: 'Открыть шаблоны' },
  { number: 7, title: 'Тестовый приём', description: 'Отправьте тестовый файл или payload и проверьте: parsing → mapping → нормализованные данные → диагностику строк.', status: 'planned', action: 'Проверить импорт' },
  { number: 8, title: 'Рассылка', description: 'Настройте аудиторию, канал, шаблон и вложения. Обязательные вложения блокируют отправку, пока документ не готов.', status: 'available', href: '/campaigns', action: 'Открыть кампании' },
  { number: 9, title: 'Контроль доставки', description: 'После запуска отслеживайте SENT, RETRY и FAILED, ошибки провайдера и готовность вложений.', status: 'available', href: '/campaigns', action: 'Смотреть запуски' },
];

export function IntegrationJourneyPage() {
  const { t } = useI18n();
  const available = steps.filter((step) => step.status === 'available').length;

  return (
    <div className="integration-journey">
      <header className="integration-journey__hero">
        <div>
          <p className="eyebrow">{t('integrationJourney.eyebrow')}</p>
          <h1>{t('integrationJourney.title')}</h1>
          <p>{t('integrationJourney.description')}</p>
        </div>
        <div className="integration-journey__progress" aria-label={t('integrationJourney.progress')}>
          <strong>{available}/{steps.length}</strong>
          <span>{t('integrationJourney.availableNow')}</span>
        </div>
      </header>

      <section className="integration-journey__next">
        <div>
          <span className="integration-journey__next-label">{t('integrationJourney.recommended')}</span>
          <h2>{t('integrationJourney.nextTitle')}</h2>
          <p>{t('integrationJourney.nextDescription')}</p>
        </div>
        <Link className="ui-button ui-button--primary" to="/templates/assets">
          {t('integrationJourney.nextAction')}
        </Link>
      </section>

      <div className="integration-journey__flow" aria-label={t('integrationJourney.steps')}>
        {steps.map((step, index) => (
          <article className="integration-step" key={step.number}>
            <div className="integration-step__rail" aria-hidden="true">
              <span className="integration-step__number">{step.number}</span>
              {index < steps.length - 1 ? <span className="integration-step__line" /> : null}
            </div>
            <div className="integration-step__card">
              <div className="integration-step__heading">
                <div>
                  <span className={`integration-step__status integration-step__status--${step.status}`}>
                    {step.status === 'available'
                      ? t('integrationJourney.available')
                      : t('integrationJourney.planned')}
                  </span>
                  <h2>{step.title}</h2>
                </div>
                {step.href ? (
                  <Link className="ui-button ui-button--secondary" to={step.href}>{step.action}</Link>
                ) : (
                  <button className="ui-button ui-button--secondary" type="button" disabled>{step.action}</button>
                )}
              </div>
              <p>{step.description}</p>
            </div>
          </article>
        ))}
      </div>

      <section className="integration-journey__result">
        <p className="eyebrow">{t('integrationJourney.resultEyebrow')}</p>
        <h2>{t('integrationJourney.resultTitle')}</h2>
        <p>{t('integrationJourney.resultDescription')}</p>
        <div className="integration-journey__pipeline" aria-label={t('integrationJourney.pipeline')}>
          <span>Приём</span><b>→</b><span>Parsing</span><b>→</b><span>Mapping</span><b>→</b>
          <span>Данные</span><b>→</b><span>Template</span><b>→</b><span>Файл</span><b>→</b>
          <span>Message</span><b>→</b><span>Mock / Provider</span><b>→</b><span>Статус</span>
        </div>
      </section>
    </div>
  );
}
