package com.tokentracker.client;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.tokentracker.config.TokenTrackerProperties;
import com.tokentracker.exception.TelegramException;

/**
 * Sends messages to one chat through the Telegram Bot API.
 * The bot token is part of the URL, so errors are rebuilt here and never expose the URL.
 */
@Component
public class TelegramClient {

    /** Telegram rejects longer messages. */
    public static final int MAX_MESSAGE_LENGTH = 4096;

    private final RestTemplate restTemplate;
    private final TokenTrackerProperties.Telegram telegram;

    public TelegramClient(RestTemplate restTemplate, TokenTrackerProperties properties) {
        this.restTemplate = restTemplate;
        this.telegram = properties.telegram();
    }

    public boolean isConfigured() {
        return telegram.isConfigured();
    }

    /**
     * @param html message using Telegram's HTML subset; dynamic text must be escaped with {@link #escape}
     * @throws TelegramException when Telegram is not configured, unreachable or rejects the message
     */
    public void sendMessage(String html) {
        if (!isConfigured()) {
            throw new TelegramException("Telegram is not configured (TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID)");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", telegram.chatId());
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);
        URI uri = URI.create(telegram.apiUrl() + "/bot" + telegram.botToken() + "/sendMessage");
        try {
            JsonNode response = restTemplate.postForObject(uri, body, JsonNode.class);
            if (response == null || !response.path("ok").asBoolean(false)) {
                throw new TelegramException("Telegram returned an unexpected response");
            }
        } catch (HttpStatusCodeException e) {
            // e.g. {"ok":false,"error_code":400,"description":"Bad Request: chat not found"}
            throw new TelegramException("Telegram rejected the message: " + e.getStatusCode().value() + " "
                    + describe(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            // The cause is dropped on purpose: its message contains the request URL, hence the bot token.
            throw new TelegramException("Unable to reach Telegram");
        }
    }

    /** Escapes the three characters that are significant in Telegram HTML. */
    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String describe(String body) {
        String text = body == null ? "" : body;
        int start = text.indexOf("\"description\":\"");
        if (start >= 0) {
            int from = start + 15;
            int end = text.indexOf('"', from);
            if (end > from) {
                text = text.substring(from, end);
            }
        }
        // Defensive: the token must never reach a log or an API response.
        return text.replace(telegram.botToken(), "***");
    }
}
