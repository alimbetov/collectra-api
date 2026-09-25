import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { IntegrationJourneyPage } from './IntegrationJourneyPage';

describe('IntegrationJourneyPage', () => {
  it('explains the business journey and exposes available next actions', () => {
    render(
      <MemoryRouter>
        <I18nProvider requestedLocale="ru" requestedTimeZone="UTC">
          <IntegrationJourneyPage />
        </I18nProvider>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Подключите данные и доведите их до отправки' })).toBeInTheDocument();
    expect(screen.getByText('Источник данных')).toBeInTheDocument();
    expect(screen.getByText('Сопоставление полей')).toBeInTheDocument();
    expect(screen.getByText('Тестовый приём')).toBeInTheDocument();
    expect(screen.getByText('Контроль доставки')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Загрузить материалы' })).toHaveAttribute('href', '/templates/assets');
    expect(screen.getByRole('link', { name: 'Открыть шаблоны' })).toHaveAttribute('href', '/templates');
    expect(screen.getByRole('link', { name: 'Открыть кампании' })).toHaveAttribute('href', '/campaigns');
  });

  it('does not present planned backend gaps as clickable functionality', () => {
    render(
      <MemoryRouter>
        <I18nProvider requestedLocale="ru" requestedTimeZone="UTC">
          <IntegrationJourneyPage />
        </I18nProvider>
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: 'Настроить источник' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Настроить mapping' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Проверить импорт' })).toBeDisabled();
  });
});
