package com.library.db;

import com.library.config.AppConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the single JDBC connection used by this single-threaded console app and
 * offers small helpers for running SQL scripts and managing transactions.
 */
public final class DatabaseManager implements AutoCloseable {

    private final String url;
    private final String user;
    private final String password;
    private Connection connection;

    public DatabaseManager(AppConfig config) {
        this(config.getDbUrl(), config.getDbUser(), config.getDbPassword());
    }

    public DatabaseManager(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Returns the shared connection, opening it on first use. */
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                throw new SQLException("PostgreSQL JDBC driver not found on the classpath", e);
            }
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    public void testConnection() throws SQLException {
        try (Statement st = getConnection().createStatement()) {
            st.execute("SELECT 1");
        }
    }

    /** Executes every statement in a SQL file. The file may contain -- comments. */
    public void executeScript(Path sqlFile) throws IOException, SQLException {
        String script = Files.readString(sqlFile, StandardCharsets.UTF_8);
        executeSql(script);
    }

    /** Executes a set of semicolon-separated SQL statements in one transaction. */
    public void executeSql(String script) throws SQLException {
        Connection c = getConnection();
        boolean previousAutoCommit = c.getAutoCommit();
        try {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                for (String sql : splitStatements(script)) {
                    st.execute(sql);
                }
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(previousAutoCommit);
        }
    }

    /** Convenience: applies schema.sql followed by seed.sql from a base directory. */
    public void initializeDatabase(Path sqlDirectory) throws IOException, SQLException {
        executeScript(sqlDirectory.resolve("schema.sql"));
        executeScript(sqlDirectory.resolve("seed.sql"));
    }

    public void beginTransaction() throws SQLException {
        getConnection().setAutoCommit(false);
    }

    public void commit() throws SQLException {
        getConnection().commit();
        getConnection().setAutoCommit(true);
    }

    public void rollbackQuietly() {
        try {
            Connection c = getConnection();
            c.rollback();
            c.setAutoCommit(true);
        } catch (SQLException ignored) {
            // best effort
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /** Strips line comments and splits on semicolons (our SQL has no ';' inside literals). */
    private static String[] splitStatements(String script) {
        StringBuilder clean = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            clean.append(line).append('\n');
        }
        return clean.toString().split(";");
    }
}
