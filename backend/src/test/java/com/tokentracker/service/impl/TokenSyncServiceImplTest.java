package com.tokentracker.service.impl;

import static com.tokentracker.TestProperties.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.tokentracker.client.WavesDataApiClient;
import com.tokentracker.client.WavesNodeClient;
import com.tokentracker.client.dto.WavesAsset;
import com.tokentracker.client.dto.WavesCandle;
import com.tokentracker.client.dto.WavesDistributionPage;
import com.tokentracker.config.TokenTrackerProperties;
import com.tokentracker.domain.Token;
import com.tokentracker.domain.TokenHolderStats;
import com.tokentracker.domain.TokenPrice;
import com.tokentracker.domain.TokenQuantity;
import com.tokentracker.domain.TokenTopHolder;
import com.tokentracker.exception.WavesApiException;
import com.tokentracker.repository.TokenHolderStatsRepository;
import com.tokentracker.repository.TokenPriceRepository;
import com.tokentracker.repository.TokenQuantityRepository;
import com.tokentracker.repository.TokenRepository;
import com.tokentracker.repository.TokenTopHolderRepository;
import com.tokentracker.service.dto.SyncResultDTO;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenSyncServiceImplTest {

    private static final String TN = "bPWkA3MNyEr1TuDchWgdpqJZhGhfPXj7dJdr3qiW2kD";
    private static final String OTHER = "34N9YcEETLWn93qYQ64EsP1x89tSruJU44RrEMSXXEPJ";
    // 08:10 UTC -> the last closed hour is 07:00
    private static final Instant NOW = Instant.parse("2026-09-27T08:10:00Z");

    @Mock private TokenRepository tokenRepository;
    @Mock private TokenPriceRepository priceRepository;
    @Mock private TokenQuantityRepository quantityRepository;
    @Mock private TokenTopHolderRepository topHolderRepository;
    @Mock private TokenHolderStatsRepository holderStatsRepository;
    @Mock private WavesDataApiClient dataApiClient;
    @Mock private WavesNodeClient nodeClient;
    @Mock private TransactionTemplate transactionTemplate;

    private TokenSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = newService(properties(3, 3, 30));
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private TokenSyncServiceImpl newService(TokenTrackerProperties props) {
        return new TokenSyncServiceImpl(tokenRepository, priceRepository, quantityRepository, topHolderRepository,
                holderStatsRepository, dataApiClient, nodeClient, props, transactionTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ─── Hourly prices ───────────────────────────────────────────────────

    @Test
    void hoursWithoutTradesCarryTheLastKnownPrice() {
        TokenPrice latest = price("2026-09-27T04:00:00Z", "0.0062");
        when(priceRepository.findFirstByAssetIdOrderByPriceTimeDesc(TN)).thenReturn(Optional.of(latest));
        when(priceRepository.findFirstByAssetIdAndPriceTimeBeforeAndPriceIsNotNullOrderByPriceTimeDesc(
                TN, Instant.parse("2026-09-27T05:00:00Z"))).thenReturn(Optional.of(latest));
        when(dataApiClient.getCandles(eq(TN), eq("WAVES"), any(), any(), eq("1h"))).thenReturn(List.of(
                candle("2026-09-27T05:00:00Z", null, null, 0),
                candle("2026-09-27T06:00:00Z", "0.00640102", "534.69341725", 5),
                candle("2026-09-27T07:00:00Z", null, null, 0)));

        int written = service.syncPrices(TN);

        assertThat(written).isEqualTo(3);
        List<TokenPrice> rows = savedPrices();
        assertThat(rows).extracting(p -> p.getPriceTime().toString())
                .containsExactly("2026-09-27T05:00:00Z", "2026-09-27T06:00:00Z", "2026-09-27T07:00:00Z");
        assertThat(rows.get(0).getPrice()).isEqualByComparingTo("0.0062");
        assertThat(rows.get(0).isHasTrades()).isFalse();
        assertThat(rows.get(0).getVolume()).isEqualByComparingTo("0");
        assertThat(rows.get(1).getPrice()).isEqualByComparingTo("0.00640102");
        assertThat(rows.get(1).isHasTrades()).isTrue();
        assertThat(rows.get(1).getVolume()).isEqualByComparingTo("534.69341725");
        assertThat(rows.get(2).getPrice()).isEqualByComparingTo("0.00640102");
        assertThat(rows.get(2).isHasTrades()).isFalse();
    }

    @Test
    void aDowntimeLongerThanTheLookbackIsCaughtUp() {
        // Last stored hour is 10 hours before the last closed hour, lookback is only 3 hours.
        when(priceRepository.findFirstByAssetIdOrderByPriceTimeDesc(TN))
                .thenReturn(Optional.of(price("2026-09-26T21:00:00Z", "0.006")));
        when(priceRepository.findFirstByAssetIdAndPriceTimeBeforeAndPriceIsNotNullOrderByPriceTimeDesc(eq(TN), any()))
                .thenReturn(Optional.of(price("2026-09-26T21:00:00Z", "0.006")));
        when(dataApiClient.getCandles(eq(TN), eq("WAVES"), any(), any(), eq("1h"))).thenReturn(List.of());

        int written = service.syncPrices(TN);

        // 22:00 .. 07:00 included
        assertThat(written).isEqualTo(10);
        assertThat(savedPrices()).allSatisfy(p -> assertThat(p.getPrice()).isEqualByComparingTo("0.006"));
    }

    @Test
    void aTokenWithoutPricesIsBackfilledAndSeededFromTheLastTradedDay() {
        when(priceRepository.findFirstByAssetIdOrderByPriceTimeDesc(TN)).thenReturn(Optional.empty());
        when(priceRepository.findFirstByAssetIdAndPriceTimeBeforeAndPriceIsNotNullOrderByPriceTimeDesc(eq(TN), any()))
                .thenReturn(Optional.empty());
        when(dataApiClient.getCandles(eq(TN), eq("WAVES"), any(), any(), eq("1h"))).thenReturn(List.of());
        when(dataApiClient.getCandles(eq(TN), eq("WAVES"), any(), any(), eq("1d"))).thenReturn(List.of(
                candle("2026-08-01T00:00:00Z", "0.009", "10", 3),
                candle("2026-08-20T00:00:00Z", "0.0081", "12", 2),
                candle("2026-08-21T00:00:00Z", null, null, 0)));

        int written = service.syncPrices(TN);

        // 30 days of hours, ending with the last closed hour
        assertThat(written).isEqualTo(30 * 24);
        List<TokenPrice> rows = savedPrices();
        assertThat(rows.get(0).getPriceTime()).isEqualTo(Instant.parse("2026-08-28T08:00:00Z"));
        assertThat(rows.get(rows.size() - 1).getPriceTime()).isEqualTo(Instant.parse("2026-09-27T07:00:00Z"));
        assertThat(rows).allSatisfy(p -> {
            assertThat(p.getPrice()).isEqualByComparingTo("0.0081");
            assertThat(p.isHasTrades()).isFalse();
        });
    }

    @Test
    void aTokenThatNeverTradedGetsNullPrices() {
        when(priceRepository.findFirstByAssetIdOrderByPriceTimeDesc(TN)).thenReturn(Optional.empty());
        when(dataApiClient.getCandles(eq(TN), eq("WAVES"), any(), any(), any())).thenReturn(List.of());

        service.syncPrices(TN);

        assertThat(savedPrices()).allSatisfy(p -> assertThat(p.getPrice()).isNull());
    }

    // ─── Distribution ────────────────────────────────────────────────────

    @Test
    void scanKeepsOnlyTheLargestHoldersAndCountsPositiveBalances() {
        when(nodeClient.getDistribution(TN, 100, 1000, null)).thenReturn(page(true, "c1",
                "A", 50L, "B", 500L, "C", 5L, "D", 0L));
        when(nodeClient.getDistribution(TN, 100, 1000, "c1")).thenReturn(page(false, "c2",
                "E", 400L, "F", 60L));

        TokenSyncServiceImpl.DistributionScan scan = service.scanDistribution(TN, 100);

        assertThat(scan.holdersCount()).isEqualTo(5);
        assertThat(scan.top()).extracting(Map.Entry::getKey).containsExactly("B", "E", "F");
    }

    @Test
    void scanFailsWhenPaginationDoesNotAdvance() {
        when(nodeClient.getDistribution(eq(TN), eq(100), eq(1000), any())).thenReturn(page(true, "same", "A", 1L));
        when(nodeClient.getDistribution(TN, 100, 1000, null)).thenReturn(page(true, "same", "A", 1L));

        assertThatThrownBy(() -> service.scanDistribution(TN, 100)).isInstanceOf(WavesApiException.class);
    }

    @Test
    void dailySyncStoresQuantityTopHoldersAndStats() {
        when(dataApiClient.getAsset(TN)).thenReturn(Optional.of(new WavesAsset(TN, "TurtleNetwork", "TN", 8,
                null, Instant.parse("2019-03-13T18:36:10.377Z"), 1000L, false, false)));
        when(nodeClient.getHeight()).thenReturn(101);
        when(nodeClient.getDistribution(TN, 100, 1000, null)).thenReturn(page(false, null,
                "A", 500L, "B", 300L, "C", 100L, "D", 50L));
        when(quantityRepository.findByAssetIdAndDate(any(), any())).thenReturn(Optional.empty());
        when(holderStatsRepository.findByAssetIdAndDate(any(), any())).thenReturn(Optional.empty());

        service.syncDaily(token(TN));

        LocalDate today = LocalDate.parse("2026-09-27");
        ArgumentCaptor<TokenQuantity> quantity = ArgumentCaptor.forClass(TokenQuantity.class);
        verify(quantityRepository).save(quantity.capture());
        assertThat(quantity.getValue().getQuantity()).isEqualTo(1000L);
        assertThat(quantity.getValue().getDate()).isEqualTo(today);

        verify(topHolderRepository).deleteByAssetIdAndDate(TN, today);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TokenTopHolder>> holders = ArgumentCaptor.forClass(List.class);
        verify(topHolderRepository).saveAll(holders.capture());
        assertThat(holders.getValue()).extracting(TokenTopHolder::getRank, TokenTopHolder::getAddress,
                        TokenTopHolder::getBalance, TokenTopHolder::getHeight)
                .containsExactly(
                        tuple(1, "A", 500L, 100),
                        tuple(2, "B", 300L, 100),
                        tuple(3, "C", 100L, 100));

        ArgumentCaptor<TokenHolderStats> stats = ArgumentCaptor.forClass(TokenHolderStats.class);
        verify(holderStatsRepository).save(stats.capture());
        assertThat(stats.getValue().getHoldersCount()).isEqualTo(4);
        // top 3 (limit in this test) hold 900 of 1000; top 10 as well
        assertThat(stats.getValue().getTop10Share()).isEqualByComparingTo("90");
        assertThat(stats.getValue().getTop100Share()).isEqualByComparingTo("90");
    }

    @Test
    void dailySyncWritesNothingWhenTheNodeFails() {
        when(dataApiClient.getAsset(TN)).thenReturn(Optional.of(new WavesAsset(TN, "TurtleNetwork", "TN", 8,
                null, Instant.EPOCH, 1000L, false, false)));
        when(nodeClient.getHeight()).thenThrow(new WavesApiException("node down"));

        assertThatThrownBy(() -> service.syncDaily(token(TN))).isInstanceOf(WavesApiException.class);
        verify(quantityRepository, never()).save(any());
        verify(topHolderRepository, never()).saveAll(any());
    }

    // ─── Error isolation ─────────────────────────────────────────────────

    @Test
    void aFailingTokenDoesNotStopTheOthers() {
        when(tokenRepository.findAllByOrderByIdAsc()).thenReturn(List.of(token(TN), token(OTHER)));
        when(priceRepository.findFirstByAssetIdOrderByPriceTimeDesc(any())).thenReturn(Optional.empty());
        when(dataApiClient.getCandles(eq(TN), any(), any(), any(), any()))
                .thenThrow(new WavesApiException("Waves Data API down"));
        when(dataApiClient.getCandles(eq(OTHER), any(), any(), any(), any())).thenReturn(List.of());

        List<SyncResultDTO> results = service.syncAllPrices();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).error()).contains("Waves Data API down");
        assertThat(results.get(1).error()).isNull();
        assertThat(results.get(1).priceHours()).isEqualTo(30 * 24);
    }

    @Test
    void shareIsAPercentageWithFourDecimals() {
        assertThat(TokenSyncServiceImpl.share(1, 3)).isEqualByComparingTo("33.3333");
        assertThat(TokenSyncServiceImpl.share(5, 0)).isEqualByComparingTo("0");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<TokenPrice> savedPrices() {
        ArgumentCaptor<List<TokenPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(priceRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static TokenPrice price(String time, String value) {
        TokenPrice price = new TokenPrice();
        price.setAssetId(TN);
        price.setPriceTime(Instant.parse(time));
        price.setPrice(new BigDecimal(value));
        price.setVolume(BigDecimal.ZERO);
        return price;
    }

    private static WavesCandle candle(String time, String close, String volume, int txs) {
        BigDecimal c = close == null ? null : new BigDecimal(close);
        return new WavesCandle(Instant.parse(time), c, c, c, c, volume == null ? null : new BigDecimal(volume), txs);
    }

    private static WavesDistributionPage page(boolean hasNext, String lastItem, Object... addressBalancePairs) {
        Map<String, Long> balances = new LinkedHashMap<>();
        for (int i = 0; i < addressBalancePairs.length; i += 2) {
            balances.put((String) addressBalancePairs[i], (Long) addressBalancePairs[i + 1]);
        }
        return new WavesDistributionPage(balances, hasNext, lastItem);
    }

    private static Token token(String assetId) {
        Token token = new Token();
        token.setAssetId(assetId);
        token.setName(assetId);
        token.setDecimals(8);
        return token;
    }
}
