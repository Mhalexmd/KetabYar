package com.example.library.repository;

import com.example.library.db.Database;
import com.example.library.model.Book;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQLite implementation of {@link BookRepository}; every query is parameterized. */
public final class SqliteBookRepository implements BookRepository {
    private static final String SELECT_COLUMNS = "id, title, author, isbn, total_copies, available_copies";

    private final Database database;

    public SqliteBookRepository(Database database) {
        this.database = database;
    }

    @Override
    public List<Book> findAll() {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM books ORDER BY id";
        try (PreparedStatement statement = database.prepare(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Book> books = new ArrayList<>();
            while (resultSet.next()) {
                books.add(mapRow(resultSet));
            }
            return books;
        } catch (SQLException e) {
            throw new IllegalStateException("خواندن فهرست کتاب‌ها از دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public Book findById(int id) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM books WHERE id = ?";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRow(resultSet) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("خواندن کتاب از دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public boolean existsByIsbn(String isbn, int excludeBookId) {
        String sql = "SELECT COUNT(*) FROM books WHERE isbn = ? AND id <> ?";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setString(1, isbn);
            statement.setInt(2, excludeBookId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("بررسی یکتایی شابک در دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public int insert(String title, String author, String isbn, int totalCopies, int availableCopies) {
        String sql = "INSERT INTO books (title, author, isbn, total_copies, available_copies) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = database.prepareInsert(sql)) {
            statement.setString(1, title);
            statement.setString(2, author);
            statement.setString(3, isbn);
            statement.setInt(4, totalCopies);
            statement.setInt(5, availableCopies);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new IllegalStateException("ذخیره‌ی کتاب در دیتابیس ناموفق بود.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("ذخیره‌ی کتاب در دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public boolean update(int id, String title, String author, String isbn, int totalCopies, int availableCopies) {
        String sql = "UPDATE books SET title = ?, author = ?, isbn = ?, total_copies = ?, available_copies = ? WHERE id = ?";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setString(1, title);
            statement.setString(2, author);
            statement.setString(3, isbn);
            statement.setInt(4, totalCopies);
            statement.setInt(5, availableCopies);
            statement.setInt(6, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("به‌روزرسانی کتاب در دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public int decrementAvailableCopies(int id) {
        // The guard in the WHERE clause keeps the count consistent even if two callers race.
        String sql = "UPDATE books SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setInt(1, id);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("کاهش نسخه‌های موجود کتاب در دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public int incrementAvailableCopies(int id) {
        // MIN() caps the count at total copies so a duplicate return cannot inflate availability.
        String sql = "UPDATE books SET available_copies = MIN(total_copies, available_copies + 1) WHERE id = ?";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setInt(1, id);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("افزایش نسخه‌های موجود کتاب در دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public boolean deleteById(int id) {
        String sql = "DELETE FROM books WHERE id = ?";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("حذف کتاب از دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public int sumTotalCopies() {
        return sumColumn("total_copies");
    }

    @Override
    public int sumAvailableCopies() {
        return sumColumn("available_copies");
    }

    private int sumColumn(String column) {
        String sql = "SELECT COALESCE(SUM(" + column + "), 0) FROM books";
        try (PreparedStatement statement = database.prepare(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("محاسبه‌ی آمار کتاب‌ها از دیتابیس ناموفق بود.", e);
        }
    }

    private static Book mapRow(ResultSet resultSet) throws SQLException {
        return new Book(resultSet.getInt("id"),
                resultSet.getString("title"),
                resultSet.getString("author"),
                resultSet.getString("isbn"),
                resultSet.getInt("total_copies"),
                resultSet.getInt("available_copies"));
    }
}
