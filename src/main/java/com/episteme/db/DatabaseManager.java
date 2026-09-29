package com.episteme.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite database connection pool and schema lifecycle.
 * <p>
 * This class uses HikariCP for connection pooling and configures SQLite in WAL (Write-Ahead Logging) mode
 * for optimal read/write concurrency during AST parsing.
 */
public class DatabaseManager {

    private HikariDataSource dataSource;

    /**
     * Initializes the database connection pool and applies the table schemas if they do not exist.
     * <p>
     * @implNote This method writes to the filesystem (creates/modifies the SQLite .db file)
     * and performs DDL operations.
     * 
     * @param dbPath The path to the SQLite database file.
     * @throws SQLException If initialization or schema creation fails.
     */
    public void initialize(String dbPath) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        // SQLite concurrent config
        config.setMaximumPoolSize(1);
        config.setConnectionTestQuery("SELECT 1");

        this.dataSource = new HikariDataSource(config);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA synchronous=NORMAL;");
            stmt.execute("PRAGMA busy_timeout=5000;");
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS packages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    parent_id INTEGER
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS files (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    package_id INTEGER,
                    path TEXT NOT NULL UNIQUE,
                    md5_hash TEXT NOT NULL,
                    FOREIGN KEY(package_id) REFERENCES packages(id)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS symbols (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_id INTEGER,
                    kind TEXT NOT NULL,
                    name TEXT NOT NULL,
                    fqn TEXT NOT NULL UNIQUE,
                    is_external BOOLEAN,
                    FOREIGN KEY(file_id) REFERENCES files(id)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS contracts (
                    symbol_id INTEGER PRIMARY KEY,
                    return_type TEXT,
                    parameters_blob BLOB,
                    annotations_blob BLOB,
                    execution_trace_blob BLOB,
                    javadoc TEXT,
                    FOREIGN KEY(symbol_id) REFERENCES symbols(id)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS edges (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    from_symbol INTEGER,
                    to_symbol INTEGER,
                    edge_type TEXT NOT NULL,
                    FOREIGN KEY(from_symbol) REFERENCES symbols(id),
                    FOREIGN KEY(to_symbol) REFERENCES symbols(id)
                )""");
        }
    }

    /**
     * Obtains a connection from the HikariCP pool.
     * <p>
     * @implNote This method READS from the connection pool. The caller MUST close the connection
     * to return it to the pool.
     * 
     * @return A valid {@link Connection} to the SQLite database.
     * @throws SQLException If a connection cannot be obtained.
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DatabaseManager is not initialized.");
        }
        return dataSource.getConnection();
    }

    /**
     * Closes the connection pool and releases all resources.
     * <p>
     * @implNote This method changes the state of the connection pool to closed.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
