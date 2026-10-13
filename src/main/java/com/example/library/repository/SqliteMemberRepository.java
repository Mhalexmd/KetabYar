package com.example.library.repository;

import com.example.library.db.Database;
import com.example.library.model.Member;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQLite implementation of {@link MemberRepository}; every query is parameterized. */
public final class SqliteMemberRepository implements MemberRepository {
    private static final String SELECT_COLUMNS = "id, full_name, student_number, phone";

    private final Database database;

    public SqliteMemberRepository(Database database) {
        this.database = database;
    }

    @Override
    public List<Member> findAll() {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM members ORDER BY id";
        try (PreparedStatement statement = database.prepare(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Member> members = new ArrayList<>();
            while (resultSet.next()) {
                members.add(mapRow(resultSet));
            }
            return members;
        } catch (SQLException e) {
            throw new IllegalStateException("خواندن فهرست اعضا از دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public Member findById(int id) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM members WHERE id = ?";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRow(resultSet) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("خواندن عضو از دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public boolean existsByStudentNumber(String studentNumber, int excludeMemberId) {
        String sql = "SELECT COUNT(*) FROM members WHERE student_number = ? AND id <> ?";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setString(1, studentNumber);
            statement.setInt(2, excludeMemberId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("بررسی یکتایی شماره دانشجویی در دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public int insert(String fullName, String studentNumber, String phone) {
        String sql = "INSERT INTO members (full_name, student_number, phone) VALUES (?, ?, ?)";
        try (PreparedStatement statement = database.prepareInsert(sql)) {
            statement.setString(1, fullName);
            statement.setString(2, studentNumber);
            statement.setString(3, phone);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new IllegalStateException("ذخیره‌ی عضو در دیتابیس ناموفق بود.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("ذخیره‌ی عضو در دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public boolean update(int id, String fullName, String studentNumber, String phone) {
        String sql = "UPDATE members SET full_name = ?, student_number = ?, phone = ? WHERE id = ?";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setString(1, fullName);
            statement.setString(2, studentNumber);
            statement.setString(3, phone);
            statement.setInt(4, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("به‌روزرسانی عضو در دیتابیس ناموفق بود.", e);
        }
    }

    @Override
    public boolean deleteById(int id) {
        String sql = "DELETE FROM members WHERE id = ?";
        try (PreparedStatement statement = database.prepare(sql)) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("حذف عضو از دیتابیس ناموفق بود.", e);
        }
    }

    private static Member mapRow(ResultSet resultSet) throws SQLException {
        return new Member(resultSet.getInt("id"),
                resultSet.getString("full_name"),
                resultSet.getString("student_number"),
                resultSet.getString("phone"));
    }
}
