package com.tokentracker.client;

import static com.tokentracker.TestProperties.NODE_URL;
import static com.tokentracker.TestProperties.fixture;
import static com.tokentracker.TestProperties.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.tokentracker.client.dto.WavesDistributionPage;
import com.tokentracker.exception.WavesApiException;

class WavesNodeClientTest {

    private static final String TN = "bPWkA3MNyEr1TuDchWgdpqJZhGhfPXj7dJdr3qiW2kD";

    private MockRestServiceServer server;
    private WavesNodeClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new WavesNodeClient(restTemplate, properties(100, 24, 30));
    }

    @Test
    void getHeightReadsTheHeightField() {
        server.expect(requestTo(NODE_URL + "/blocks/height"))
                .andRespond(withSuccess("{\"height\":5424234}", MediaType.APPLICATION_JSON));

        assertThat(client.getHeight()).isEqualTo(5424234);
    }

    @Test
    void getDistributionParsesAPageAndItsCursor() {
        server.expect(requestTo(NODE_URL + "/assets/" + TN + "/distribution/5424233/limit/1000"))
                .andRespond(withSuccess(fixture("distribution-page1.json"), MediaType.APPLICATION_JSON));

        WavesDistributionPage page = client.getDistribution(TN, 5424233, 1000, null);

        assertThat(page.hasNext()).isTrue();
        assertThat(page.lastItem()).isEqualTo("3P5YD9kDiNMLAYnXdM49dPkfm6eqZHcoEXH");
        assertThat(page.balances()).hasSize(4)
                .containsEntry("3P5YD9kDiNMLAYnXdM49dPkfm6eqZHcoEXH", 17624777745848L);
    }

    @Test
    void getDistributionPassesTheCursorOfThePreviousPage() {
        server.expect(requestTo(NODE_URL + "/assets/" + TN
                        + "/distribution/5424233/limit/1000?after=3P5YD9kDiNMLAYnXdM49dPkfm6eqZHcoEXH"))
                .andRespond(withSuccess(fixture("distribution-page2.json"), MediaType.APPLICATION_JSON));

        WavesDistributionPage page = client.getDistribution(TN, 5424233, 1000, "3P5YD9kDiNMLAYnXdM49dPkfm6eqZHcoEXH");

        assertThat(page.hasNext()).isFalse();
        server.verify();
    }

    @Test
    void getDistributionSurfacesTheNodeErrorMessage() {
        server.expect(requestTo(NODE_URL + "/assets/" + TN + "/distribution/100/limit/1000"))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":199,\"message\":\"Unable to get distribution past height 5422235\"}"));

        assertThatThrownBy(() -> client.getDistribution(TN, 100, 1000, null))
                .isInstanceOf(WavesApiException.class)
                .hasMessageContaining("Unable to get distribution past height");
    }
}
