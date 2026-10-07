package com.ncba.countryinfo.util;

import java.util.Locale;

/**
 * Normalizes user-supplied country names before they are sent to the SOAP service.
 *
 * <p>The upstream CountryInfoService matches names case-sensitively ("kenya" fails, "Kenya"
 * works), and multi-word names only match when every word is capitalised ("United States",
 * not "United states"). So instead of strict sentence case we capitalise the first letter of
 * every word, including words joined by a hyphen ("guinea-bissau" -> "Guinea-Bissau").
 */
public final class CountryNameFormatter {

    private CountryNameFormatter() {
    }

    public static String toSentenceCase(String raw) {
        if (raw == null) {
            return null;
        }
        String collapsed = raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(collapsed.length());
        boolean capitalizeNext = true;
        for (char c : collapsed.toCharArray()) {
            out.append(capitalizeNext ? Character.toUpperCase(c) : c);
            capitalizeNext = c == ' ' || c == '-';
        }
        return out.toString();
    }
}
