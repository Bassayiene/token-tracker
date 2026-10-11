package com.tokentracker.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.tokentracker.client.dto.WavesAssetBalance;
import com.tokentracker.client.dto.WavesDistributionPage;
import com.tokentracker.config.TokenTrackerProperties;
import com.tokentracker.exception.WavesApiException;

/**
 * Client of a public Waves node REST API (https://nodes.wavesnodes.com).
 */
@Component
public class WavesNodeClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public WavesNodeClient(RestTemplate restTemplate, TokenTrackerProperties properties) {
        this.restTemplate = restTemplate;
        this.baseUrl = properties.waves().nodeUrl();
    }

    /** Current blockchain height. */
    public int getHeight() {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).pathSegment("blocks", "height").build().toUri();
        JsonNode root = get(uri, "blockchain height");
        if (root == null || !root.path("height").isNumber()) {
            throw new WavesApiException("Waves node returned an invalid height response");
        }
        return root.path("height").asInt();
    }

    /**
     * One page of the balance distribution of an asset at a given height.
     * The node only serves recent heights (roughly the last 2000 blocks).
     *
     * @param after cursor returned by the previous page ({@code lastItem}), null for the first page
     */
    public WavesDistributionPage getDistribution(String assetId, int height, int limit, String after) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("assets", assetId, "distribution", String.valueOf(height), "limit", String.valueOf(limit));
        if (after != null) {
            builder.queryParam("after", after);
        }
        JsonNode root = get(builder.build().toUri(), "distribution of " + assetId);
        if (root == null || !root.path("items").isObject()) {
            throw new WavesApiException("Waves node returned an invalid distribution response");
        }

        Map<String, Long> balances = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.path("items").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            balances.put(entry.getKey(), entry.getValue().asLong());
        }
        JsonNode lastItem = root.path("lastItem");
        return new WavesDistributionPage(
                balances,
                root.path("hasNext").asBoolean(false),
                lastItem.isNull() || lastItem.isMissingNode() ? null : lastItem.asText());
    }

    /** Whether the node recognises the address (format, checksum and network). */
    public boolean isValidAddress(String address) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).pathSegment("addresses", "validate", address).build().toUri();
        JsonNode root = get(uri, "validity of " + address);
        return root != null && root.path("valid").asBoolean(false);
    }

    /** Raw WAVES balance of an address. */
    public long getWavesBalance(String address) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).pathSegment("addresses", "balance", address).build().toUri();
        JsonNode root = get(uri, "WAVES balance of " + address);
        if (root == null || !root.path("balance").isNumber()) {
            throw new WavesApiException("Waves node returned an invalid balance response");
        }
        return root.path("balance").asLong();
    }

    /** Every asset held by an address (WAVES excluded), in one call. */
    public List<WavesAssetBalance> getAssetBalances(String address) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).pathSegment("assets", "balance", address).build().toUri();
        JsonNode root = get(uri, "asset balances of " + address);
        if (root == null || !root.path("balances").isArray()) {
            throw new WavesApiException("Waves node returned an invalid asset balances response");
        }
        List<WavesAssetBalance> balances = new ArrayList<>();
        for (JsonNode item : root.path("balances")) {
            if (!item.path("assetId").isTextual() || !item.path("balance").isNumber()) {
                throw new WavesApiException("Waves node returned an invalid asset balance entry");
            }
            JsonNode issue = item.path("issueTransaction");
            balances.add(new WavesAssetBalance(
                    item.path("assetId").asText(),
                    issue.path("name").isTextual() ? issue.path("name").asText() : null,
                    issue.path("decimals").isNumber() ? issue.path("decimals").asInt() : null,
                    item.path("balance").asLong()));
        }
        return balances;
    }

    /** Name and decimals of an asset, for those {@link #getAssetBalances} could not describe. */
    public WavesAssetBalance getAssetDetails(String assetId) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).pathSegment("assets", "details", assetId).build().toUri();
        JsonNode root = get(uri, "details of " + assetId);
        if (root == null || !root.path("name").isTextual() || !root.path("decimals").isNumber()) {
            throw new WavesApiException("Waves node returned invalid asset details");
        }
        return new WavesAssetBalance(assetId, root.path("name").asText(), root.path("decimals").asInt(), 0L);
    }

    private JsonNode get(URI uri, String what) {
        try {
            return restTemplate.getForObject(uri, JsonNode.class);
        } catch (HttpStatusCodeException e) {
            // The node explains errors in the body, e.g. {"error":199,"message":"Unable to get distribution past height ..."}
            throw new WavesApiException("Waves node error while fetching " + what + ": "
                    + e.getStatusCode().value() + " " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new WavesApiException("Unable to reach the Waves node while fetching " + what, e);
        }
    }
}
