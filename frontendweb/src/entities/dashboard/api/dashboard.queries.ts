import { queryOptions } from '@tanstack/react-query';
import {
  getDashboardCollections,
  getDashboardDelivery,
  getDashboardReceivables,
  getDashboardSummary,
} from './dashboard.api';

export const dashboardKeys = {
  all: ['dashboard'] as const,
  summary: () => [...dashboardKeys.all, 'summary'] as const,
  receivables: () => [...dashboardKeys.all, 'receivables'] as const,
  delivery: () => [...dashboardKeys.all, 'delivery'] as const,
  collections: () => [...dashboardKeys.all, 'collections'] as const,
};

export const dashboardQueries = {
  summary: () => queryOptions({ queryKey: dashboardKeys.summary(), queryFn: getDashboardSummary }),
  receivables: () =>
    queryOptions({ queryKey: dashboardKeys.receivables(), queryFn: getDashboardReceivables }),
  delivery: () =>
    queryOptions({ queryKey: dashboardKeys.delivery(), queryFn: getDashboardDelivery }),
  collections: () =>
    queryOptions({ queryKey: dashboardKeys.collections(), queryFn: getDashboardCollections }),
};
