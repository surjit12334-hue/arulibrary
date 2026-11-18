package com.library.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Central configuration holder.
 *
 * Resolution order (highest priority first):
 *   1. environment variables DB_URL / DB_USER / DB_PASSWORD
 *   2. config.properties in the working directory
 *   3. sensible local-development defaults
 */
public final class AppConfig {

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

        this.dbUrl = pick(env("DB_URL"), props.getProperty("db.url"),
                "jdbc:postgresql://127.0.0.1:5432/library_db");
        this.dbUser = pick(env("DB_USER"), props.getProperty("db.user"), "postgres");
        this.dbPassword = pick(env("DB_PASSWORD"), props.getProperty("db.password"), "postgres");
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
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String pick(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
