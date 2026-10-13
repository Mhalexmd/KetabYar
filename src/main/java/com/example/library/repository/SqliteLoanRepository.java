package com.example.library.repository;

import com.example.library.db.Database;
import com.example.library.model.Loan;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of {@link LoanRepository}. Dates are stored as ISO-8601 text (yyyy-MM-dd),
 * so plain string comparison matches chronological order.
 */
public final class SqliteLoanRepository implements LoanRepository {
    private static final String SELECT_COLUMNS = "id, book_id, member_id, loan_date, due_date, return_date";

    private final Database database;

    public SqliteLoanRepository(Database database) {
        this.database = database;
    }

    @Override
    public List<Loan> findAll() {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM loans ORDER BY id";
        try (PreparedStatement statement = database.prepare(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Loan> loans = new ArrayList<>();
            while (resultSet.next()) {
                loans.add(mapRow(resultSet));
            }
            return loans;
        } catch (SQLException e) {
            throw new IllegalStateException("خواندن فهرست امانت‌ها از دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public Loan findById(int id) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM loans WHERE id = ?";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRow(resultSet) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("خواندن امانت از دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public boolean existsByBookId(int bookId) {
        return existsBy("book_id", bookId);
    }

    @Override
    public boolean existsByMemberId(int memberId) {
        return existsBy("member_id", memberId);
    }

    private boolean existsBy(String column, int value) {
        String sql = "SELECT COUNT(*) FROM loans WHERE " + column + " = ?";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setInt(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("بررسی سابقه‌ی امانت در دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public int insert(int bookId, int memberId, LocalDate loanDate, LocalDate dueDate) {
        String sql = "INSERT INTO loans (book_id, member_id, loan_date, due_date) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = database.prepareInsert(sql)) {
            statement.setInt(1, bookId);
            statement.setInt(2, memberId);
            statement.setString(3, loanDate.toString());
            statement.setString(4, dueDate.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new IllegalStateException("ذخیره‌ی امانت در دیتابیس ناموفق بود.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("ذخیره‌ی امانت در دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public boolean markReturned(int loanId, LocalDate returnDate) {
        // The "return_date IS NULL" guard rejects a second return of the same loan.
        String sql = "UPDATE loans SET return_date = ? WHERE id = ? AND return_date IS NULL";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setString(1, returnDate.toString());
            statement.setInt(2, loanId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("ثبت بازگشت امانت در دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public long countActive() {
        String sql = "SELECT COUNT(*) FROM loans WHERE return_date IS NULL";
        try (PreparedStatement statement = database.prepare(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("شمارش امانت‌های فعال ناموفق بود.", e);
        }
    }

    @Override
    public long countOverdue(LocalDate today) {
        String sql = "SELECT COUNT(*) FROM loans WHERE return_date IS NULL AND due_date < ?";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setString(1, today.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("شمارش امانت‌های دارای دیرکرد ناموفق بود.", e);
        }
    }

    private static Loan mapRow(ResultSet resultSet) throws SQLException {
        Loan loan = new Loan(resultSet.getInt("id"),
                resultSet.getInt("book_id"),
                resultSet.getInt("member_id"),
                LocalDate.parse(resultSet.getString("loan_date")),
                LocalDate.parse(resultSet.getString("due_date")));
        String returnDate = resultSet.getString("return_date");
        if (returnDate != null) {
            loan.markReturned(LocalDate.parse(returnDate));
        }
        return loan;
    }
}
