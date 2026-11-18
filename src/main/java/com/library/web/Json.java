package com.library.web;

import java.math.BigDecimal;
import java.util.Map;

/** Tiny JSON helpers — enough for the demo REST API, no third-party library needed. */
public final class Json {

    private Json() {
    }

    public static String esc(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String str(String value) {
        return value == null ? "null" : "\"" + esc(value) + "\"";
    }

    public static String num(int value) {
        return Integer.toString(value);
    }

    public static String dec(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    public static String bool(boolean value) {
        return value ? "true" : "false";
    }

    /** Builds {"k1":v1,"k2":v2,...} from alternating key / raw-JSON-value arguments. */
    public static String obj(Object... pairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(str(String.valueOf(pairs[i]))).append(':').append(pairs[i + 1]);
        }
        return sb.append('}').toString();
    }

    /** Wraps already-built JSON object strings in an array. */
    public static String arr(String... items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(items[i]);
        }
        return sb.append(']').toString();
    }

    public static String error(String message) {
        return obj("error", str(message));
    }

    public static String ok(String message) {
        return obj("ok", str(message));
    }

    /** Parses application/x-www-form-urlencoded bodies into a simple map. */
    public static Map<String, String> parseForm(String body) {
        Map<String, String> result = new java.util.HashMap<>();
        if (body == null || body.isEmpty()) {
            return result;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = decode(pair.substring(0, eq));
            String value = decode(pair.substring(eq + 1));
            result.put(key, value);
        }
        return result;
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value.replace("+", " "), java.nio.charset.StandardCharsets.UTF_8);
    }
}
