package com.tokentracker.client;

import static com.tokentracker.TestProperties.DATA_API_URL;
import static com.tokentracker.TestProperties.fixture;
import static com.tokentracker.TestProperties.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.tokentracker.client.dto.WavesAsset;
import com.tokentracker.client.dto.WavesCandle;
import com.tokentracker.exception.WavesApiException;

class WavesDataApiClientTest {

    private static final String TN = "bPWkA3MNyEr1TuDchWgdpqJZhGhfPXj7dJdr3qiW2kD";

    private MockRestServiceServer server;
    private WavesDataApiClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new WavesDataApiClient(restTemplate, properties(100, 24, 30));
    }

    @Test
    void getAssetParsesTheRealTurtleNetworkResponse() {
        server.expect(requestTo(DATA_API_URL + "/assets/" + TN))
                .andRespond(withSuccess(fixture("asset-tn.json"), MediaType.APPLICATION_JSON));

        Optional<WavesAsset> asset = client.getAsset(TN);

        assertThat(asset).hasValueSatisfying(a -> {
            assertThat(a.assetId()).isEqualTo(TN);
            assertThat(a.name()).isEqualTo("TurtleNetwork");
            assertThat(a.ticker()).isEqualTo("TN");
            assertThat(a.decimals()).isEqualTo(8);
            assertThat(a.description()).isEqualTo("Official token for TurtleNetwork\n1:1 backed by TurtleNetwork");
            assertThat(a.issuedAt()).isEqualTo(Instant.parse("2019-03-13T18:36:10.377Z"));
            assertThat(a.quantity()).isEqualTo(9519332174608945L);
            assertThat(a.reissuable()).isFalse();
            assertThat(a.hasScript()).isFalse();
        });
        server.verify();
    }

    @Test
    void getAssetReturnsEmptyWhenTheAssetIsUnknown() {
        server.expect(requestTo(DATA_API_URL + "/assets/" + TN)).andRespond(withResourceNotFound());

        assertThat(client.getAsset(TN)).isEmpty();
    }

    @Test
    void getAssetWrapsServerErrors() {
        server.expect(requestTo(DATA_API_URL + "/assets/" + TN)).andRespond(withServerError());

        assertThatThrownBy(() -> client.getAsset(TN)).isInstanceOf(WavesApiException.class);
    }

    @Test
    void getCandlesParsesHoursWithAndWithoutTrades() {
        Instant from = Instant.parse("2026-09-27T05:00:00Z");
        Instant to = Instant.parse("2026-09-27T07:00:00Z");
        server.expect(requestTo(DATA_API_URL + "/candles/" + TN + "/WAVES?timeStart=" + from.toEpochMilli()
                        + "&timeEnd=" + to.toEpochMilli() + "&interval=1h"))
                .andRespond(withSuccess(fixture("candles-1h-tn.json"), MediaType.APPLICATION_JSON));

        List<WavesCandle> candles = client.getCandles(TN, "WAVES", from, to, WavesDataApiClient.INTERVAL_HOUR);

        assertThat(candles).hasSize(3);
        assertThat(candles.get(0).hasTrades()).isFalse();
        assertThat(candles.get(0).close()).isNull();
        assertThat(candles.get(0).volume()).isNull();

        WavesCandle traded = candles.get(1);
        assertThat(traded.time()).isEqualTo(Instant.parse("2026-09-27T06:00:00Z"));
        assertThat(traded.hasTrades()).isTrue();
        assertThat(traded.txsCount()).isEqualTo(5);
        assertThat(traded.close()).isEqualByComparingTo(new BigDecimal("0.00640102"));
        assertThat(traded.volume()).isEqualByComparingTo(new BigDecimal("534.69341725"));
        server.verify();
    }
}
