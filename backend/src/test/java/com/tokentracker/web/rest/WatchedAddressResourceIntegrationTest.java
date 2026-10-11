package com.tokentracker.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokentracker.client.TelegramClient;
import com.tokentracker.client.WavesDataApiClient;
import com.tokentracker.client.WavesNodeClient;
import com.tokentracker.client.dto.WavesAssetBalance;
import com.tokentracker.exception.TelegramException;
import com.tokentracker.exception.WavesApiException;

/**
 * Watched addresses end to end on H2: the Waves node and Telegram are mocked, everything else is real.
 * The test configuration allows two watched addresses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WatchedAddressResourceIntegrationTest {

    private static final String ADDRESS = "3PRCoguiVX33Fj6z46qgxheib6K1267W7Zr";
    private static final String OTHER_ADDRESS = "3P5YD9kDiNMLAYnXdM49dPkfm6eqZHcoEXH";
    private static final String THIRD_ADDRESS = "3PKPd4HDTM9qFmDYQ4Fn7vrjnPR63aFWdej";
    private static final String TN = "bPWkA3MNyEr1TuDchWgdpqJZhGhfPXj7dJdr3qiW2kD";
    private static final String DAPP_ASSET = "DG2xFkPdDwKUoBkzGAhQtLpSGzfXLiCYPEzeKH2Ad24p";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private WavesDataApiClient dataApiClient;
    @MockitoBean private WavesNodeClient nodeClient;
    @MockitoBean private TelegramClient telegramClient;

    @BeforeEach
    void setUp() {
        when(telegramClient.isConfigured()).thenReturn(true);
        when(nodeClient.isValidAddress(anyString())).thenReturn(true);
        // 1 WAVES and 2500 TN
        holds(ADDRESS, 100_000_000L, new WavesAssetBalance(TN, "TurtleNetwork", 8, 250_000_000_000L));
    }

    // ─── Baseline ────────────────────────────────────────────────────────

    @Test
    void addingAnAddressStoresItsBalancesAsTheBaselineWithoutAnyVariation() throws Exception {
        long id = watch(ADDRESS, "Trésorerie");

        mockMvc.perform(get("/api/watched-addresses").with(admin()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label").value("Trésorerie"))
                .andExpect(jsonPath("$[0].assetsCount").value(2))
                .andExpect(jsonPath("$[0].lastCheckedAt").exists())
                .andExpect(jsonPath("$[0].lastError").doesNotExist());
        mockMvc.perform(get("/api/watched-addresses/" + id + "/balances").with(admin()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].assetId").value("WAVES"))
                .andExpect(jsonPath("$[0].balance").value(1.0))
                .andExpect(jsonPath("$[1].assetName").value("TurtleNetwork"))
                .andExpect(jsonPath("$[1].balance").value(2500.0));
        mockMvc.perform(get("/api/watched-addresses/" + id + "/changes").with(admin()))
                .andExpect(jsonPath("$", hasSize(0)));
        assertThat(sentMessage()).contains("Surveillance activée", "Trésorerie", "2 actif(s)");
    }

    @Test
    void aCheckWithoutAnyVariationSendsNothing() throws Exception {
        long id = watch(ADDRESS, null);
        clearInvocations(telegramClient);

        mockMvc.perform(post("/api/watched-addresses/" + id + "/check").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes").value(0))
                .andExpect(jsonPath("$.notified").value(true));

        verify(telegramClient, never()).sendMessage(anyString());
    }

    // ─── Variations ──────────────────────────────────────────────────────

    @Test
    void variationsAreRecordedAndSentInOneMessage() throws Exception {
        long id = watch(ADDRESS, "Trésorerie");
        clearInvocations(telegramClient);
        // WAVES 1 -> 0.5, TN 2500 -> 3000, and a new asset issued by a dApp (described by /assets/details)
        holds(ADDRESS, 50_000_000L,
                new WavesAssetBalance(TN, "TurtleNetwork", 8, 300_000_000_000L),
                new WavesAssetBalance(DAPP_ASSET, null, null, 1050L));
        when(nodeClient.getAssetDetails(DAPP_ASSET))
                .thenReturn(new WavesAssetBalance(DAPP_ASSET, "<b>Free</b> t.me/scam", 2, 0L));

        mockMvc.perform(post("/api/watched-addresses/" + id + "/check").with(admin()))
                .andExpect(jsonPath("$.changes").value(3))
                .andExpect(jsonPath("$.notified").value(true))
                .andExpect(jsonPath("$.error").doesNotExist());

        assertThat(sentMessage())
                .startsWith("🔔 <b>Trésorerie</b>\n<a href=\"https://wscan.io/" + ADDRESS + "\">" + ADDRESS + "</a>\n")
                .contains("🔴 <code>WAVES</code> -0,5 → 0,5")
                .contains("🟢 <code>TurtleNetwork</code> +500 → 3 000")
                // The name chosen by the issuer is escaped and kept inside <code>, so it cannot inject markup or links
                .contains("🆕 <code>&lt;b&gt;Free&lt;/b&gt; t.me/scam</code> +10,5 → 10,5 (nouveau)");
        mockMvc.perform(get("/api/watched-addresses/" + id + "/changes").with(admin()))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.assetId == 'WAVES')].delta").value(-0.5))
                .andExpect(jsonPath("$[?(@.assetId == '" + TN + "')].newBalance").value(3000.0))
                .andExpect(jsonPath("$[?(@.assetId == '" + DAPP_ASSET + "')].newAsset").value(true))
                .andExpect(jsonPath("$[*].notified").value(everyItem(is(true))));
    }

    @Test
    void anAssetThatIsNoLongerHeldIsReportedAndForgotten() throws Exception {
        long id = watch(ADDRESS, null);
        clearInvocations(telegramClient);
        holds(ADDRESS, 100_000_000L);

        mockMvc.perform(post("/api/watched-addresses/" + id + "/check").with(admin()))
                .andExpect(jsonPath("$.changes").value(1));

        assertThat(sentMessage()).contains("🔴 <code>TurtleNetwork</code> -2 500 → 0 (plus détenu)");
        mockMvc.perform(get("/api/watched-addresses/" + id + "/balances").with(admin()))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void longListsOfVariationsAreSummarised() throws Exception {
        long id = watch(ADDRESS, null);
        clearInvocations(telegramClient);
        WavesAssetBalance[] airdrops = new WavesAssetBalance[40];
        for (int i = 0; i < airdrops.length; i++) {
            airdrops[i] = new WavesAssetBalance("asset" + i, "Airdrop " + i, 0, 1L);
        }
        holds(ADDRESS, 100_000_000L, airdrops);

        mockMvc.perform(post("/api/watched-addresses/" + id + "/check").with(admin()))
                .andExpect(jsonPath("$.changes").value(41)); // 40 new assets, TN no longer held

        // max-changes-per-message is 30 in the test configuration
        assertThat(sentMessage()).endsWith("… et 11 autre(s) variation(s)").hasSizeLessThan(4096);
    }

    // ─── Failures ────────────────────────────────────────────────────────

    @Test
    void aNodeFailureChangesNothingAndIsNeverReadAsEmptiedBalances() throws Exception {
        long id = watch(ADDRESS, null);
        clearInvocations(telegramClient);
        when(nodeClient.getAssetBalances(ADDRESS)).thenThrow(new WavesApiException("Unable to reach the Waves node"));

        mockMvc.perform(post("/api/watched-addresses/" + id + "/check").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes").value(0))
                .andExpect(jsonPath("$.error").value("Unable to reach the Waves node"));

        verify(telegramClient, never()).sendMessage(anyString());
        mockMvc.perform(get("/api/watched-addresses/" + id + "/balances").with(admin()))
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/watched-addresses").with(admin()))
                .andExpect(jsonPath("$[0].lastError").value("Unable to reach the Waves node"));
    }

    @Test
    void aFailedNotificationIsSentAgainAtTheNextCheck() throws Exception {
        long id = watch(ADDRESS, null);
        holds(ADDRESS, 50_000_000L, new WavesAssetBalance(TN, "TurtleNetwork", 8, 250_000_000_000L));
        doThrow(new TelegramException("Unable to reach Telegram")).when(telegramClient).sendMessage(anyString());

        mockMvc.perform(post("/api/watched-addresses/" + id + "/check").with(admin()))
                .andExpect(jsonPath("$.changes").value(1))
                .andExpect(jsonPath("$.notified").value(false))
                .andExpect(jsonPath("$.error").value("Unable to reach Telegram"));
        mockMvc.perform(get("/api/watched-addresses/" + id + "/changes").with(admin()))
                .andExpect(jsonPath("$[0].notified").value(false));
        mockMvc.perform(get("/api/watched-addresses").with(admin()))
                .andExpect(jsonPath("$[0].lastError").value("Telegram: Unable to reach Telegram"));

        // Telegram is back; the balances did not move again
        doNothing().when(telegramClient).sendMessage(anyString());
        clearInvocations(telegramClient);
        mockMvc.perform(post("/api/watched-addresses/" + id + "/check").with(admin()))
                .andExpect(jsonPath("$.changes").value(0))
                .andExpect(jsonPath("$.notified").value(true));

        assertThat(sentMessage()).contains("🔴 <code>WAVES</code> -0,5 → 0,5");
        mockMvc.perform(get("/api/watched-addresses/" + id + "/changes").with(admin()))
                .andExpect(jsonPath("$[0].notified").value(true));
    }

    // ─── Management ──────────────────────────────────────────────────────

    @Test
    void invalidDuplicateAndExcessAddressesAreRejected() throws Exception {
        mockMvc.perform(add("not-an-address", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("validation"));

        when(nodeClient.isValidAddress(THIRD_ADDRESS)).thenReturn(false);
        mockMvc.perform(add(THIRD_ADDRESS, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("invalidaddress"));

        watch(ADDRESS, null);
        mockMvc.perform(add(ADDRESS, "again"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("addressexists"));

        holds(OTHER_ADDRESS, 0L);
        watch(OTHER_ADDRESS, null);
        when(nodeClient.isValidAddress(THIRD_ADDRESS)).thenReturn(true);
        mockMvc.perform(add(THIRD_ADDRESS, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("toomanyaddresses"));
    }

    @Test
    void anAddressIsNotWatchedWhenItsBalancesCannotBeRead() throws Exception {
        when(nodeClient.getWavesBalance(ADDRESS)).thenThrow(new WavesApiException("Unable to reach the Waves node"));

        mockMvc.perform(add(ADDRESS, null)).andExpect(status().isBadGateway());

        mockMvc.perform(get("/api/watched-addresses").with(admin())).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deletingAnAddressDeletesItsBalancesAndHistory() throws Exception {
        long id = watch(ADDRESS, null);
        holds(ADDRESS, 50_000_000L);
        mockMvc.perform(post("/api/watched-addresses/" + id + "/check").with(admin()))
                .andExpect(jsonPath("$.changes").value(2));

        mockMvc.perform(delete("/api/watched-addresses/" + id).with(admin())).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/watched-addresses").with(admin())).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/watched-addresses/" + id + "/changes").with(admin()))
                .andExpect(status().isNotFound());
        // The same address can be watched again from a fresh baseline
        watch(ADDRESS, null);
    }

    // ─── Telegram set-up and access rules ────────────────────────────────

    @Test
    void theTelegramTestReportsWhyAMessageCouldNotBeDelivered() throws Exception {
        mockMvc.perform(get("/api/watched-addresses/telegram").with(admin()))
                .andExpect(jsonPath("$.configured").value(true));
        mockMvc.perform(post("/api/watched-addresses/telegram/test").with(admin())).andExpect(status().isNoContent());

        doThrow(new TelegramException("Telegram rejected the message: 400 Bad Request: chat not found"))
                .when(telegramClient).sendMessage(anyString());
        mockMvc.perform(post("/api/watched-addresses/telegram/test").with(admin()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorKey").value("telegramerror"))
                .andExpect(jsonPath("$.message").value("Telegram rejected the message: 400 Bad Request: chat not found"));
    }

    @Test
    void onlyAdminsReachTheWatchedAddresses() throws Exception {
        RequestPostProcessor user = jwt().jwt(j -> j.subject("alice").claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));

        mockMvc.perform(get("/api/watched-addresses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/watched-addresses").with(user)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/watched-addresses/1/balances").with(user)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/watched-addresses/telegram/test").with(user)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/watched-addresses").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"" + ADDRESS + "\"}"))
                .andExpect(status().isForbidden());
        verify(nodeClient, never()).getWavesBalance(anyString());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private void holds(String address, long waves, WavesAssetBalance... assets) {
        when(nodeClient.getWavesBalance(address)).thenReturn(waves);
        when(nodeClient.getAssetBalances(address)).thenReturn(List.of(assets));
    }

    private MockHttpServletRequestBuilder add(String address, String label)
            throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("address", address);
        body.put("label", label);
        return post("/api/watched-addresses").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private long watch(String address, String label) throws Exception {
        String json = mockMvc.perform(add(address, label))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    /** The single message sent to Telegram since the last {@code clearInvocations}. */
    private String sentMessage() {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(message.capture());
        return message.getValue();
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin").claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
