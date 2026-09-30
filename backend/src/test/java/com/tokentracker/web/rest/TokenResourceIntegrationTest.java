package com.tokentracker.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.tokentracker.client.WavesDataApiClient;
import com.tokentracker.client.WavesNodeClient;
import com.tokentracker.client.dto.WavesAsset;
import com.tokentracker.domain.TokenHolderStats;
import com.tokentracker.domain.TokenPrice;
import com.tokentracker.domain.TokenQuantity;
import com.tokentracker.domain.TokenTopHolder;
import com.tokentracker.repository.TokenHolderStatsRepository;
import com.tokentracker.repository.TokenPriceRepository;
import com.tokentracker.repository.TokenQuantityRepository;
import com.tokentracker.repository.TokenRepository;
import com.tokentracker.repository.TokenTopHolderRepository;

import jakarta.persistence.EntityManager;

/**
 * Full application context on H2 with the real Liquibase changelogs (schema validated against the entities).
 * Waves clients are mocked; each test runs in a rolled-back transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TokenResourceIntegrationTest {

    private static final String TN = "bPWkA3MNyEr1TuDchWgdpqJZhGhfPXj7dJdr3qiW2kD";
    private static final String NEW_ASSET = "34N9YcEETLWn93qYQ64EsP1x89tSruJU44RrEMSXXEPJ";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenRepository tokenRepository;
    @Autowired private TokenPriceRepository priceRepository;
    @Autowired private TokenQuantityRepository quantityRepository;
    @Autowired private TokenTopHolderRepository topHolderRepository;
    @Autowired private TokenHolderStatsRepository holderStatsRepository;
    @Autowired private EntityManager entityManager;

    @MockitoBean private WavesDataApiClient dataApiClient;
    @MockitoBean private WavesNodeClient nodeClient;

    // ─── Read ────────────────────────────────────────────────────────────

    @Test
    void turtleNetworkIsSeededByLiquibase() throws Exception {
        mockMvc.perform(get("/api/tokens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].assetId", hasItem(TN)))
                .andExpect(jsonPath("$[0].ticker").value("TN"))
                .andExpect(jsonPath("$[0].decimals").value(8))
                .andExpect(jsonPath("$[0].createdAt").value("2019-03-13T18:36:10.377Z"))
                .andExpect(jsonPath("$[0].reissuable").value(false))
                .andExpect(jsonPath("$[0].priceAsset").value("WAVES"));
    }

    @Test
    void unknownTokenIs404() throws Exception {
        mockMvc.perform(get("/api/tokens/" + NEW_ASSET))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value("notfound"));
    }

    @Test
    void summaryComputesLastPriceAndVariations() throws Exception {
        Instant last = Instant.parse("2026-09-27T07:00:00Z");
        savePrice(last.minusSeconds(7 * 24 * 3600), "0.005", true);
        savePrice(last.minusSeconds(24 * 3600), "0.008", true);
        savePrice(last.minusSeconds(3600), "0.01", true);
        savePrice(last, "0.01", false);
        saveQuantity(LocalDate.parse("2026-09-27"), 9519332174608945L);

        mockMvc.perform(get("/api/tokens/" + TN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastPrice").value(0.01))
                .andExpect(jsonPath("$.lastPriceTime").value("2026-09-27T07:00:00Z"))
                .andExpect(jsonPath("$.lastTradeTime").value("2026-09-27T06:00:00Z"))
                .andExpect(jsonPath("$.change24h").value(25.0))
                .andExpect(jsonPath("$.change7d").value(100.0))
                .andExpect(jsonPath("$.quantity").value(95193321.74608945));
    }

    @Test
    void pricesAreFilteredByRange() throws Exception {
        savePrice(Instant.parse("2026-09-27T05:00:00Z"), "0.0062", false);
        savePrice(Instant.parse("2026-09-27T06:00:00Z"), "0.00640102", true);
        savePrice(Instant.parse("2026-09-27T07:00:00Z"), "0.00640102", false);

        mockMvc.perform(get("/api/tokens/" + TN + "/prices")
                        .param("from", "2026-09-27T06:00:00Z")
                        .param("to", "2026-09-27T07:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].time").value("2026-09-27T06:00:00Z"))
                .andExpect(jsonPath("$[0].hasTrades").value(true))
                .andExpect(jsonPath("$[1].hasTrades").value(false));
    }

    @Test
    void invalidRangesAreRejected() throws Exception {
        mockMvc.perform(get("/api/tokens/" + TN + "/prices")
                        .param("from", "2026-09-27T07:00:00Z")
                        .param("to", "2026-09-27T06:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("invalidrange"));
        mockMvc.perform(get("/api/tokens/" + TN + "/prices")
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("rangetoolarge"));
        mockMvc.perform(get("/api/tokens/" + TN + "/quantities").with(user()).param("from", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void topHoldersExposeSharesAndChangesSinceThePreviousSnapshot() throws Exception {
        LocalDate yesterday = LocalDate.parse("2026-09-26");
        LocalDate today = LocalDate.parse("2026-09-27");
        saveQuantity(today, 1_000_000_000L); // 10 TN
        saveHolder(yesterday, 1, "3PAddressA", 400_000_000L);
        saveHolder(yesterday, 2, "3PAddressB", 300_000_000L);
        saveHolder(today, 1, "3PAddressB", 500_000_000L);
        saveHolder(today, 2, "3PAddressC", 200_000_000L);

        mockMvc.perform(get("/api/tokens/" + TN + "/holders").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-09-27"))
                .andExpect(jsonPath("$.previousDate").value("2026-09-26"))
                .andExpect(jsonPath("$.availableDates", hasSize(2)))
                .andExpect(jsonPath("$.holders[0].address").value("3PAddressB"))
                .andExpect(jsonPath("$.holders[0].balance").value(5.0))
                .andExpect(jsonPath("$.holders[0].share").value(50.0))
                .andExpect(jsonPath("$.holders[0].balanceChange").value(2.0))
                .andExpect(jsonPath("$.holders[0].rankChange").value(1))
                .andExpect(jsonPath("$.holders[0].isNew").value(false))
                .andExpect(jsonPath("$.holders[1].isNew").value(true));

        mockMvc.perform(get("/api/tokens/" + TN + "/holders").with(user()).param("date", "2026-09-26"))
                .andExpect(jsonPath("$.previousDate").doesNotExist())
                .andExpect(jsonPath("$.holders[0].address").value("3PAddressA"))
                .andExpect(jsonPath("$.holders[0].isNew").value(false));
    }

    // ─── Write ───────────────────────────────────────────────────────────

    @Test
    void addTokenReadsItsInvariantDataFromWaves() throws Exception {
        when(dataApiClient.getAsset(NEW_ASSET)).thenReturn(Optional.of(new WavesAsset(NEW_ASSET, "Tether USD",
                "USDT", 6, "Tether on Waves", Instant.parse("2020-05-01T10:00:00Z"), 123L, true, false)));

        mockMvc.perform(post("/api/tokens").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"" + NEW_ASSET + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/tokens/" + NEW_ASSET))
                .andExpect(jsonPath("$.name").value("Tether USD"))
                .andExpect(jsonPath("$.decimals").value(6));

        assertThat(tokenRepository.findByAssetId(NEW_ASSET)).hasValueSatisfying(t -> {
            assertThat(t.getCreatedAt()).isEqualTo(Instant.parse("2020-05-01T10:00:00Z"));
            assertThat(t.isReissuable()).isTrue();
        });
    }

    @Test
    void addTokenRejectsInvalidDuplicateAndUnknownAssets() throws Exception {
        mockMvc.perform(post("/api/tokens").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"assetId\":\"../x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("validation"));

        mockMvc.perform(post("/api/tokens").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"" + TN + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("tokenexists"));

        when(dataApiClient.getAsset(NEW_ASSET)).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/tokens").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"" + NEW_ASSET + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("assetnotfound"));
    }

    @Test
    void deleteTokenCascadesToItsData() throws Exception {
        savePrice(Instant.parse("2026-09-27T06:00:00Z"), "0.0064", true);
        saveQuantity(LocalDate.parse("2026-09-27"), 1L);
        saveHolder(LocalDate.parse("2026-09-27"), 1, "3PAddressA", 1L);

        mockMvc.perform(delete("/api/tokens/" + TN).with(admin())).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        assertThat(tokenRepository.existsByAssetId(TN)).isFalse();
        assertThat(priceRepository.existsByAssetId(TN)).isFalse();
        assertThat(quantityRepository.findFirstByAssetIdOrderByDateDesc(TN)).isEmpty();
        assertThat(topHolderRepository.findDates(TN)).isEmpty();
    }

    @Test
    void corsAllowsTheConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/tokens")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    // ─── Access rules ────────────────────────────────────────────────────

    @Test
    void visitorsReadSummariesWithoutHolderFiguresAndPrices() throws Exception {
        saveQuantity(LocalDate.parse("2026-09-27"), 9519332174608945L);
        TokenHolderStats stats = new TokenHolderStats();
        stats.setAssetId(TN);
        stats.setDate(LocalDate.parse("2026-09-27"));
        stats.setHoldersCount(14522);
        stats.setTop10Share(new BigDecimal("84.367"));
        stats.setTop100Share(new BigDecimal("98.4691"));
        stats.setFetchedAt(Instant.now());
        holderStatsRepository.save(stats);

        mockMvc.perform(get("/api/tokens/" + TN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(95193321.74608945))
                .andExpect(jsonPath("$.holdersCount").doesNotExist())
                .andExpect(jsonPath("$.holdersDate").doesNotExist());
        mockMvc.perform(get("/api/tokens/" + TN).with(user()))
                .andExpect(jsonPath("$.holdersCount").value(14522))
                .andExpect(jsonPath("$.holdersDate").value("2026-09-27"));
        mockMvc.perform(get("/api/tokens/" + TN + "/prices")).andExpect(status().isOk());
    }

    @Test
    void visitorsCannotReadDailyData() throws Exception {
        for (String path : new String[] { "quantities", "holders", "holder-stats" }) {
            mockMvc.perform(get("/api/tokens/" + TN + "/" + path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorKey").value("unauthorized"));
            mockMvc.perform(get("/api/tokens/" + TN + "/" + path).with(user())).andExpect(status().isOk());
        }
    }

    @Test
    void onlyAdminsManageTokensAndSync() throws Exception {
        String body = "{\"assetId\":\"" + NEW_ASSET + "\"}";
        mockMvc.perform(post("/api/tokens").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/tokens").with(user()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorKey").value("forbidden"));
        mockMvc.perform(delete("/api/tokens/" + TN).with(user())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tokens/sync").with(user())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tokens/" + TN + "/sync").with(user())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tokens/" + TN + "/sync")).andExpect(status().isUnauthorized());
        assertThat(tokenRepository.existsByAssetId(TN)).isTrue();
    }

    @Test
    void invalidTokensAreRejectedEvenOnPublicEndpoints() throws Exception {
        mockMvc.perform(get("/api/tokens").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private static RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("alice").claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin").claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private void savePrice(Instant time, String price, boolean hasTrades) {
        TokenPrice row = new TokenPrice();
        row.setAssetId(TN);
        row.setPriceTime(time);
        row.setPrice(new BigDecimal(price));
        row.setVolume(hasTrades ? BigDecimal.ONE : BigDecimal.ZERO);
        row.setHasTrades(hasTrades);
        row.setFetchedAt(Instant.now());
        priceRepository.save(row);
    }

    private void saveQuantity(LocalDate date, long quantity) {
        TokenQuantity row = new TokenQuantity();
        row.setAssetId(TN);
        row.setDate(date);
        row.setQuantity(quantity);
        row.setFetchedAt(Instant.now());
        quantityRepository.save(row);
    }

    private void saveHolder(LocalDate date, int rank, String address, long balance) {
        TokenTopHolder row = new TokenTopHolder();
        row.setAssetId(TN);
        row.setDate(date);
        row.setRank(rank);
        row.setAddress(address);
        row.setBalance(balance);
        row.setHeight(5424233);
        row.setFetchedAt(Instant.now());
        topHolderRepository.save(row);
    }
}
