export interface PlatformChannelCountDto {
  channel: string;
  messages: number;
}

export interface PlatformOverviewDto {
  generatedAt: string;
  periodFrom: string;
  tenantsTotal: number;
  tenantsActive: number;
  tenantsBlocked: number;
  usersTotal: number;
  usersActive: number;
  usersBlocked: number;
  campaignsLast30Days: number;
  messagesLast30Days: number;
  sentLast30Days: number;
  failedLast30Days: number;
  retryWaitCurrent: number;
  unknownCurrent: number;
  channels: PlatformChannelCountDto[];
}
