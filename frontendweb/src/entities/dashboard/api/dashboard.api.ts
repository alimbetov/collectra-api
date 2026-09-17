import { apiRequest } from '../../../shared/api/http-client';
import type {
  DashboardCollectionsDto,
  DashboardDeliveryDto,
  DashboardReceivablesDto,
  DashboardSummaryDto,
} from '../model/dashboard.types';

export const getDashboardSummary = () =>
  apiRequest<DashboardSummaryDto>('/api/v1/dashboard/summary');

export const getDashboardReceivables = () =>
  apiRequest<DashboardReceivablesDto>('/api/v1/dashboard/receivables');

export const getDashboardDelivery = () =>
  apiRequest<DashboardDeliveryDto>('/api/v1/dashboard/delivery');

export const getDashboardCollections = () =>
  apiRequest<DashboardCollectionsDto>('/api/v1/dashboard/collections');
