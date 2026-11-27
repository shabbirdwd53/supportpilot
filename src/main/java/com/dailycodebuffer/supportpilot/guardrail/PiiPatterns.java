package com.dailycodebuffer.supportpilot.guardrail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PII detection shared by the input redactor and the output leak check.
 * Regex + a Luhn check is what most teams start with. Swap in Microsoft Presidio or a cloud
 * DLP API later without touching the guardrails that use this class.
 */
public final class PiiPatterns {

    /** 13 to 19 digits, optionally split by spaces or dashes. Confirmed with Luhn to avoid flagging order numbers. */
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w-])(?:\\+\\d{1,3}[ .-]?)?\\(?\\d{3}\\)?[ .-]?\\d{3}[ .-]?\\d{4}(?!\\d)");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");

    private static final Map<String, Pattern> TYPES = new LinkedHashMap<>();

    static {
        // Order matters: cards before phones, so a card number is never half-masked as a phone
        TYPES.put("CARD", CARD);
        TYPES.put("SSN", SSN);
        TYPES.put("EMAIL", EMAIL);
        TYPES.put("PHONE", PHONE);
    }

    private PiiPatterns() {
    }

    public record Redaction(String text, List<String> found) {
    }

    /** Replaces every PII match with a typed placeholder such as [CARD]. */
    public static Redaction redact(String text) {
        List<String> found = new ArrayList<>();
        String result = text;
        for (var entry : TYPES.entrySet()) {
            Matcher m = entry.getValue().matcher(result);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                if (entry.getKey().equals("CARD") && !luhn(m.group())) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                    continue;
                }
                found.add(entry.getKey());
                m.appendReplacement(sb, "[" + entry.getKey() + "]");
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return new Redaction(result, found);
    }

    public static List<String> detect(String text) {
        return redact(text).found();
    }

    static boolean luhn(String candidate) {
        String digits = candidate.replaceAll("[^0-9]", "");
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
