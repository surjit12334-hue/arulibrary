package com.library;

import com.library.config.AppConfig;
import com.library.db.DatabaseManager;
import com.library.service.LibraryService;
import com.library.ui.ConsoleUI;
import com.library.verify.SelfCheck;
import com.library.web.WebServer;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class Main {

    public static void main(String[] args) {
        AppConfig config = new AppConfig();

        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            printHelp();
            return;
        }

        try (DatabaseManager db = new DatabaseManager(config)) {
            System.out.println("Connecting to " + config.describeConnection() + " ...");
            db.testConnection();
            System.out.println("Connection OK.");

            if (args.length > 0 && args[0].equals("--self-check")) {
                ensureSchema(db);
                int result = new SelfCheck(db).run();
                if (result != 0) {
                    System.exit(result);
                }
                return;
            }

            if (args.length > 0 && args[0].equals("--setup")) {
                db.initializeDatabase(Path.of("sql"));
                System.out.println("Schema and sample data loaded successfully.");
                return;
            }

            if (args.length > 0 && args[0].equals("--web")) {
                ensureSchema(db);
                int port = webPort(args);
                String host = System.getenv().getOrDefault("WEB_HOST", "0.0.0.0");
                new WebServer(new LibraryService(db), db, port, host).start();
                // Keep the try-with-resources connection open while the server runs.
                Thread.currentThread().join();
                return;
            }

            ensureSchema(db);
            new ConsoleUI(new LibraryService(db), db).run();
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.err.println();
            System.err.println("Check that PostgreSQL is running and that DB_URL / DB_USER / DB_PASSWORD");
            System.err.println("match your database. See README.md for setup instructions.");
            System.exit(1);
        }
    }

    /** On a brand-new database, create the tables and load sample data automatically. */
    private static void ensureSchema(DatabaseManager db) throws SQLException, java.io.IOException {
        try (ResultSet rs = db.getConnection().getMetaData()
                .getTables(null, "public", "books", new String[]{"TABLE"})) {
            if (rs.next()) {
                return;
            }
        }
        System.out.println("No schema found - creating tables and loading sample data...");
        db.initializeDatabase(Path.of("sql"));
        System.out.println("Database initialized.");
    }

    private static void printHelp() {
        System.out.println("AruLibrary - Java + PostgreSQL");
        System.out.println();
        System.out.println("Usage: java -cp <classpath> com.library.Main [option]");
        System.out.println();
        System.out.println("  (no option)   Start the interactive menu");
        System.out.println("  --setup       (Re)create tables and load sample data");
        System.out.println("  --self-check  Run automated end-to-end verification");
        System.out.println("  --web         Start the browser interface on port 8080");
        System.out.println("  --web 9000    Start the browser interface on a custom port");
        System.out.println("  --help        Show this message");
        System.out.println();
        System.out.println("Configuration: DB_URL, DB_USER, DB_PASSWORD, WEB_HOST env vars or config.properties");
    }

    /** Port for --web: second argument, then WEB_PORT / Render's PORT, then 8080. */
    private static int webPort(String[] args) {
        if (args.length > 1) {
            return Integer.parseInt(args[1]);
        }
        String fromEnv = System.getenv("WEB_PORT");
        if (fromEnv == null || fromEnv.isBlank()) {
            fromEnv = System.getenv("PORT");
        }
        return (fromEnv == null || fromEnv.isBlank()) ? 8080 : Integer.parseInt(fromEnv);
    }
}
