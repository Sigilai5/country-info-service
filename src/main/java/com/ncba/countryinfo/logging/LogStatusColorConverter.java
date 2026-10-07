package com.ncba.countryinfo.logging;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiElement;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiStyle;

/**
 * Logback converter {@code %statusColor(...)} that colors console output by outcome:
 * <ul>
 *   <li>{@code logStatus=SUCCESS} - green</li>
 *   <li>{@code logStatus=FAILED} or level ERROR - red (bold for ERROR)</li>
 *   <li>level WARN - yellow</li>
 *   <li>{@code logStatus=IN_PROGRESS} - cyan</li>
 *   <li>anything else - default terminal color</li>
 * </ul>
 * Colors are only emitted when Spring's ANSI support is active ({@code spring.output.ansi.enabled},
 * auto-detected: on in IntelliJ and real terminals, off in Kubernetes pods and log files).
 */
public class LogStatusColorConverter extends CompositeConverter<ILoggingEvent> {

    @Override
    protected String transform(ILoggingEvent event, String in) {
        AnsiElement[] color = colorFor(event);
        if (color.length == 0) {
            return in;
        }
        // Keep a trailing line break outside the color, so the reset code never leaks onto the next line
        String text = in;
        String lineBreak = "";
        if (text.endsWith("\r\n")) {
            lineBreak = "\r\n";
        } else if (text.endsWith("\n")) {
            lineBreak = "\n";
        }
        text = text.substring(0, text.length() - lineBreak.length());

        Object[] elements = new Object[color.length + 1];
        System.arraycopy(color, 0, elements, 0, color.length);
        elements[color.length] = text;
        return AnsiOutput.toString(elements) + lineBreak;
    }

    private static AnsiElement[] colorFor(ILoggingEvent event) {
        Level level = event.getLevel();
        String status = logStatus(event.getKeyValuePairs());
        if (level.isGreaterOrEqual(Level.ERROR)) {
            return new AnsiElement[] {AnsiStyle.BOLD, AnsiColor.RED};
        }
        if (LogConstants.FAILED_STATUS.equals(status)) {
            return new AnsiElement[] {AnsiColor.RED};
        }
        if (level.isGreaterOrEqual(Level.WARN)) {
            return new AnsiElement[] {AnsiColor.YELLOW};
        }
        if (LogConstants.SUCCESS_STATUS.equals(status)) {
            return new AnsiElement[] {AnsiColor.GREEN};
        }
        if (LogConstants.IN_PROGRESS_STATUS.equals(status)) {
            return new AnsiElement[] {AnsiColor.CYAN};
        }
        return new AnsiElement[0];
    }

    private static String logStatus(List<KeyValuePair> pairs) {
        if (pairs == null) {
            return null;
        }
        for (KeyValuePair pair : pairs) {
            if ("logStatus".equals(pair.key)) {
                return String.valueOf(pair.value);
            }
        }
        return null;
    }
}
