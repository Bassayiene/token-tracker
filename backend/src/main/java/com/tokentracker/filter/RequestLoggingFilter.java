package com.tokentracker.filter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Logs start/end of every request with its duration, and tags all log lines of the
 * request with a correlation id (taken from {@code X-Correlation-Id} or generated).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    // Hit constantly by healthchecks and Prometheus scraping: DEBUG instead of INFO.
    private static final Set<String> LOW_PRIORITY_PATHS = Set.of("/management/health", "/management/prometheus");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String method = request.getMethod();
        String uri = request.getRequestURI();
        Level level = LOW_PRIORITY_PATHS.contains(uri) ? Level.DEBUG : Level.INFO;
        long startTime = System.currentTimeMillis();

        try {
            MDC.put("correlationId", correlationId);
            MDC.put("method", method);
            MDC.put("uri", uri);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            LOG.atLevel(level).log("REQUEST_START: method={}, uri={}", method, uri);
            filterChain.doFilter(request, response);
            LOG.atLevel(level).log("REQUEST_END: status={}, duration={}ms",
                    response.getStatus(), System.currentTimeMillis() - startTime);
        } catch (Exception ex) {
            LOG.error("REQUEST_ERROR: error={}, duration={}ms",
                    ex.getMessage(), System.currentTimeMillis() - startTime, ex);
            throw ex;
        } finally {
            MDC.remove("correlationId");
            MDC.remove("method");
            MDC.remove("uri");
        }
    }
}
