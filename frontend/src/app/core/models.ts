/**
 * Shapes returned by the backend `/api/tokens` endpoints.
 * Decimal amounts arrive as JSON numbers, instants as ISO-8601 strings and days as `yyyy-MM-dd`.
 */

export interface TokenSummary {
  assetId: string;
  name: string;
  ticker: string | null;
  decimals: number;
  description: string | null;
  createdAt: string | null;
  reissuable: boolean;
  hasScript: boolean;
  priceAsset: string;
  lastPrice: number | null;
  lastPriceTime: string | null;
  lastTradeTime: string | null;
  change24h: number | null;
  change7d: number | null;
  quantity: number | null;
  quantityDate: string | null;
  holdersCount: number | null;
  top10Share: number | null;
  top100Share: number | null;
  holdersDate: string | null;
}

export interface TokenPrice {
  time: string;
  /** Null until the token has traded at least once against the price asset. */
  price: number | null;
  volume: number;
  hasTrades: boolean;
}

export interface TokenQuantity {
  date: string;
  rawQuantity: number;
  quantity: number;
}

export interface HolderStats {
  date: string;
  holdersCount: number;
  top10Share: number;
  top100Share: number;
}

export interface TopHolder {
  rank: number;
  address: string;
  balance: number;
  share: number;
  balanceChange: number | null;
  rankChange: number | null;
  isNew: boolean;
}

export interface TopHolders {
  date: string | null;
  previousDate: string | null;
  height: number | null;
  totalQuantity: number | null;
  holders: TopHolder[];
  availableDates: string[];
}

export interface SyncResult {
  assetId: string;
  priceHours: number;
  dailyDone: boolean;
  error: string | null;
}
