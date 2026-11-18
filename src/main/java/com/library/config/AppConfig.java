package com.library.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central configuration holder.
 *
 * Resolution order (highest priority first):
 *   1. environment variables DB_URL / DB_USER / DB_PASSWORD
 *   2. credentials embedded in DB_URL (Render connection strings)
 *   3. config.properties in the working directory
 *   4. sensible local-development defaults
 */
public final class AppConfig {

    private static final Pattern URL_CREDENTIALS =
            Pattern.compile("^(jdbc:postgresql://)([^@/]+)@(.+)$");

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public AppConfig() {
        Properties props = new Properties();
        Path configFile = Path.of("config.properties");
        if (Files.exists(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Warning: could not read config.properties: " + e.getMessage());
            }
        }

        String configuredUrl = pick(env("DB_URL"), props.getProperty("db.url"),
                "jdbc:postgresql://127.0.0.1:5432/library_db");
        String normalizedUrl = normalizeJdbcUrl(configuredUrl);

        String[] parts = splitCredentials(normalizedUrl);
        this.dbUrl = parts[0];
        this.dbUser = pick(env("DB_USER"), props.getProperty("db.user"), parts[1], "postgres");
        this.dbPassword = pick(env("DB_PASSWORD"), props.getProperty("db.password"), parts[2], "postgres");
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public String describeConnection() {
        return dbUrl + " (user: " + dbUser + ")";
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String pick(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /** Accepts Render-style postgres:// URLs and adds SSL for remote hosts. */
    private static String normalizeJdbcUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String url = raw.trim();
        if (url.startsWith("postgres://")) {
            url = "jdbc:postgresql://" + url.substring("postgres://".length());
        } else if (url.startsWith("postgresql://")) {
            url = "jdbc:postgresql://" + url.substring("postgresql://".length());
        } else if (!url.startsWith("jdbc:")) {
            url = "jdbc:postgresql://" + url;
        }

        boolean local = url.contains("127.0.0.1") || url.contains("localhost") || url.contains("[::1]");
        if (!local && url.startsWith("jdbc:postgresql://") && !url.contains("sslmode=")) {
            url += (url.contains("?") ? "&" : "?") + "sslmode=require";
        }
        return url;
    }

    /** Returns {cleanUrl, user, password} and removes credentials from the URL. */
    private static String[] splitCredentials(String url) {
        Matcher matcher = URL_CREDENTIALS.matcher(url);
        if (!matcher.matches()) {
            return new String[]{url, null, null};
        }
        String credentials = decode(matcher.group(2));
        int colon = credentials.indexOf(':');
        String user = colon < 0 ? credentials : credentials.substring(0, colon);
        String password = colon < 0 ? "" : credentials.substring(colon + 1);
        return new String[]{matcher.group(1) + matcher.group(3), user, password};
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
