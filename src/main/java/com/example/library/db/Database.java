package com.example.library.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;

/**
 * Owns the single SQLite connection used by the whole application, creates the schema
 * automatically on first run and provides transaction boundaries for multi-step operations.
 */
public final class Database implements AutoCloseable {
    private final Connection connection;

    public Database(Path file) {
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + file);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
            createTables();
        } catch (SQLException e) {
            throw new IllegalStateException("اتصال به دیتابیس SQLite ناموفق بود: " + e.getMessage(), e);
        }
    }

    public PreparedStatement prepare(String sql) throws SQLException {
        return connection.prepareStatement(sql);
    }

    public PreparedStatement prepareInsert(String sql) throws SQLException {
        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    }

    /** Runs the given block inside a single transaction: either all statements are applied or none of them. */
    public void inTransaction(Runnable action) {
        inTransaction(() -> {
            action.run();
            return null;
        });
    }

    public <T> T inTransaction(Supplier<T> action) {
        try {
            connection.setAutoCommit(false);
            T result = action.get();
            connection.commit();
            return result;
        } catch (RuntimeException e) {
            rollback();
            throw e;
        } catch (SQLException e) {
            rollback();
            throw new IllegalStateException("انجام عملیات روی دیتابیس ناموفق بود.", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // The connection may be unusable; the original failure is the useful error.
            }
        }
    }

    private void rollback() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Nothing more can be done; the error that caused the rollback matters more.
        }
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS books ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "title TEXT NOT NULL, "
                            + "author TEXT NOT NULL, "
                            + "isbn TEXT NOT NULL, "
                            + "total_copies INTEGER NOT NULL CHECK (total_copies >= 1), "
                            + "available_copies INTEGER NOT NULL CHECK (available_copies >= 0 AND available_copies <= total_copies), "
                            + "UNIQUE (isbn COLLATE NOCASE))");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS members ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "full_name TEXT NOT NULL, "
                            + "student_number TEXT NOT NULL, "
                            + "phone TEXT NOT NULL DEFAULT '', "
                            + "UNIQUE (student_number COLLATE NOCASE))");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS loans ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "book_id INTEGER NOT NULL REFERENCES books(id), "
                            + "member_id INTEGER NOT NULL REFERENCES members(id), "
                            + "loan_date TEXT NOT NULL, "
                            + "due_date TEXT NOT NULL, "
                            + "return_date TEXT)");
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Closing is best-effort; the operating system reclaims the file anyway.
        }
    }
}
