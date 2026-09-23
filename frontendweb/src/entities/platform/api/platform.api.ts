import { apiRequest } from '../../../shared/api/http-client';
import type { PlatformOverviewDto } from '../model/platform.types';

export const getPlatformOverview = () =>
  apiRequest<PlatformOverviewDto>('/api/v1/platform/overview');
