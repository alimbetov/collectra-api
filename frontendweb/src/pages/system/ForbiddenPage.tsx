import { Link } from 'react-router-dom';
import { useI18n } from '../../shared/i18n/i18n-context';

export function ForbiddenPage() {
  const { t } = useI18n();
  return (
    <section className="system-page" aria-labelledby="forbidden-title">
      <p className="eyebrow">403</p>
      <h1 id="forbidden-title">{t('system.forbidden')}</h1>
      <p>{t('system.forbiddenDescription')}</p>
      <Link to="/">{t('system.returnHome')}</Link>
    </section>
  );
}
