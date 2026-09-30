package com.tokentracker.client;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.tokentracker.client.dto.WavesAsset;
import com.tokentracker.client.dto.WavesCandle;
import com.tokentracker.config.TokenTrackerProperties;
import com.tokentracker.exception.WavesApiException;

/**
 * Client of the public Waves Data API (https://api.wavesplatform.com/v0).
 */
@Component
public class WavesDataApiClient {

    private static final Logger LOG = LoggerFactory.getLogger(WavesDataApiClient.class);

    public static final String INTERVAL_HOUR = "1h";
    public static final String INTERVAL_DAY = "1d";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public WavesDataApiClient(RestTemplate restTemplate, TokenTrackerProperties properties) {
        this.restTemplate = restTemplate;
        this.baseUrl = properties.waves().dataApiUrl();
    }

    /**
     * @return the asset, or empty when the Data API does not know it
     */
    public Optional<WavesAsset> getAsset(String assetId) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("assets", assetId)
                .build().toUri();
        JsonNode root;
        try {
            root = restTemplate.getForObject(uri, JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new WavesApiException("Unable to fetch asset " + assetId + " from the Waves Data API", e);
        }

        JsonNode data = root == null ? null : root.path("data");
        if (data == null || data.isMissingNode() || data.isNull()) {
            return Optional.empty();
        }
        return Optional.of(new WavesAsset(
                data.path("id").asText(),
                data.path("name").asText(),
                textOrNull(data.path("ticker")),
                data.path("precision").asInt(),
                textOrNull(data.path("description")),
                Instant.parse(data.path("timestamp").asText()),
                data.path("quantity").asLong(),
                data.path("reissuable").asBoolean(),
                data.path("hasScript").asBoolean()));
    }

    /**
     * Candles of the pair amountAsset/priceAsset whose start time is in [from, to] (both included).
     */
    public List<WavesCandle> getCandles(String amountAsset, String priceAsset, Instant from, Instant to,
            String interval) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("candles", amountAsset, priceAsset)
                .queryParam("timeStart", from.toEpochMilli())
                .queryParam("timeEnd", to.toEpochMilli())
                .queryParam("interval", interval)
                .build().toUri();
        LOG.debug("Fetching {} candles {}/{} from {} to {}", interval, amountAsset, priceAsset, from, to);

        JsonNode root;
        try {
            root = restTemplate.getForObject(uri, JsonNode.class);
        } catch (RestClientException e) {
            throw new WavesApiException("Unable to fetch candles of " + amountAsset + "/" + priceAsset, e);
        }
        if (root == null || !root.path("data").isArray()) {
            throw new WavesApiException("Waves Data API returned an invalid candles response");
        }

        List<WavesCandle> candles = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            JsonNode c = item.path("data");
            candles.add(new WavesCandle(
                    Instant.parse(c.path("time").asText()),
                    decimalOrNull(c.path("open")),
                    decimalOrNull(c.path("high")),
                    decimalOrNull(c.path("low")),
                    decimalOrNull(c.path("close")),
                    decimalOrNull(c.path("volume")),
                    c.path("txsCount").asInt(0)));
        }
        return candles;
    }

    private static String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        return node.isNumber() ? node.decimalValue() : null;
    }
}
