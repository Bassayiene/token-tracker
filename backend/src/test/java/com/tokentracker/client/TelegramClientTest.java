package com.tokentracker.client;

import static com.tokentracker.TestProperties.TELEGRAM_URL;
import static com.tokentracker.TestProperties.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.tokentracker.exception.TelegramException;

class TelegramClientTest {

    private static final String SEND_URL = TELEGRAM_URL + "/bottest-bot-token/sendMessage";

    private MockRestServiceServer server;
    private TelegramClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new TelegramClient(restTemplate, properties(100, 24, 30));
    }

    @Test
    void sendsAnHtmlMessageToTheConfiguredChat() {
        server.expect(requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value("-1001"))
                .andExpect(jsonPath("$.text").value("<b>hello</b>"))
                .andExpect(jsonPath("$.parse_mode").value("HTML"))
                .andExpect(jsonPath("$.disable_web_page_preview").value(true))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{}}", MediaType.APPLICATION_JSON));

        client.sendMessage("<b>hello</b>");

        server.verify();
    }

    @Test
    void aRejectedMessageReportsTelegramsDescriptionWithoutTheToken() {
        server.expect(requestTo(SEND_URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}"));

        assertThatThrownBy(() -> client.sendMessage("hello"))
                .isInstanceOf(TelegramException.class)
                .hasMessage("Telegram rejected the message: 400 Bad Request: chat not found")
                .hasNoCause();
    }

    @Test
    void aNetworkFailureNeverExposesTheTokenCarriedByTheUrl() {
        server.expect(requestTo(SEND_URL)).andRespond(request -> {
            throw new IOException("I/O error on POST request for \"" + SEND_URL + "\"");
        });

        assertThatThrownBy(() -> client.sendMessage("hello"))
                .isInstanceOf(TelegramException.class)
                .hasNoCause()
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("test-bot-token"));
    }

    @Test
    void escapesTheCharactersSignificantInTelegramHtml() {
        assertThat(TelegramClient.escape("<a href=\"x\">A & B</a>")).isEqualTo("&lt;a href=\"x\"&gt;A &amp; B&lt;/a&gt;");
    }
}
