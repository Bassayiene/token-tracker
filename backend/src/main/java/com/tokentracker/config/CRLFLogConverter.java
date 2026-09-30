package com.tokentracker.config;

import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiElement;
import org.springframework.boot.ansi.AnsiOutput;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

/**
 * Prevents log forging: CR, LF and TAB characters coming from external input
 * (request parameters, API payloads) are replaced by an underscore.
 *
 * @see <a href="https://owasp.org/www-community/attacks/Log_Injection">OWASP Log Injection</a>
 */
public class CRLFLogConverter extends CompositeConverter<ILoggingEvent> {

    @Override
    protected String transform(ILoggingEvent event, String in) {
        if (event.getLoggerName().startsWith("org.hibernate")) {
            return in;
        }
        AnsiElement element = "red".equals(getFirstOption()) ? AnsiColor.RED : null;
        String replacement = element == null ? "_" : AnsiOutput.toString(element, "_");
        return in.replaceAll("[\n\r\t]", replacement);
    }
}
