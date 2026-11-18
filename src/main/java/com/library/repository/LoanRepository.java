package com.library.repository;

import com.library.db.DatabaseManager;
import com.library.model.Loan;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LoanRepository {

    private static final String SELECT =
            "SELECT l.id, l.book_id, l.member_id, l.issued_at, l.due_at, l.returned_at, "
            + "l.fine_amount, l.note, b.title AS book_title, m.full_name AS member_name, "
            + "m.membership_no AS member_no "
            + "FROM loans l JOIN books b ON b.id = l.book_id JOIN members m ON m.id = l.member_id";

    private final DatabaseManager db;

    public LoanRepository(DatabaseManager db) {
        this.db = db;
    }

    public int insert(int bookId, int memberId, Timestamp dueAt) throws SQLException {
        String sql = "INSERT INTO loans (book_id, member_id, issued_at, due_at) VALUES (?, ?, CURRENT_TIMESTAMP, ?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, bookId);
            ps.setInt(2, memberId);
            ps.setTimestamp(3, dueAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Inserting loan did not return a generated id");
    }

    public boolean markReturned(int loanId, Timestamp returnedAt, BigDecimal fine) throws SQLException {
        String sql = "UPDATE loans SET returned_at=?, fine_amount=? WHERE id=? AND returned_at IS NULL";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setTimestamp(1, returnedAt);
            ps.setBigDecimal(2, fine);
            ps.setInt(3, loanId);
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<Loan> findById(int id) throws SQLException {
        return queryOne(SELECT + " WHERE l.id=?", ps -> ps.setInt(1, id));
    }

    public Optional<Loan> findActiveByBookAndMember(int bookId, int memberId) throws SQLException {
        return queryOne(SELECT + " WHERE l.book_id=? AND l.member_id=? AND l.returned_at IS NULL",
                ps -> {
                    ps.setInt(1, bookId);
                    ps.setInt(2, memberId);
                });
    }

    public List<Loan> findActive() throws SQLException {
        return query(SELECT + " WHERE l.returned_at IS NULL ORDER BY l.due_at, l.id");
    }

    public List<Loan> findOverdue() throws SQLException {
        return query(SELECT + " WHERE l.returned_at IS NULL AND l.due_at < CURRENT_TIMESTAMP ORDER BY l.due_at");
    }

    public List<Loan> findByMember(int memberId) throws SQLException {
        return query(SELECT + " WHERE l.member_id=? ORDER BY l.issued_at DESC", ps -> ps.setInt(1, memberId));
    }

    public List<Loan> findByBook(int bookId) throws SQLException {
        return query(SELECT + " WHERE l.book_id=? ORDER BY l.issued_at DESC", ps -> ps.setInt(1, bookId));
    }

    public List<Loan> findHistory(int limit) throws SQLException {
        return query(SELECT + " ORDER BY l.issued_at DESC LIMIT " + Math.max(1, limit));
    }

    public int countActiveByMember(int memberId) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE member_id=? AND returned_at IS NULL")) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int countActive() throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE returned_at IS NULL");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public int countAll() throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT COUNT(*) FROM loans");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public BigDecimal totalFines() throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT COALESCE(SUM(fine_amount), 0) FROM loans");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    @FunctionalInterface
    private interface ParamSetter {
        void set(PreparedStatement ps) throws SQLException;
    }

    private Optional<Loan> queryOne(String sql, ParamSetter setter) throws SQLException {
        List<Loan> result = query(sql, setter);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    private List<Loan> query(String sql) throws SQLException {
        return query(sql, ps -> { });
    }

    private List<Loan> query(String sql, ParamSetter setter) throws SQLException {
        List<Loan> result = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            setter.set(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    private Loan map(ResultSet rs) throws SQLException {
        Loan loan = new Loan();
        loan.setId(rs.getInt("id"));
        loan.setBookId(rs.getInt("book_id"));
        loan.setMemberId(rs.getInt("member_id"));
        loan.setIssuedAt(rs.getTimestamp("issued_at"));
        loan.setDueAt(rs.getTimestamp("due_at"));
        loan.setReturnedAt(rs.getTimestamp("returned_at"));
        loan.setFineAmount(rs.getBigDecimal("fine_amount"));
        loan.setNote(rs.getString("note"));
        loan.setBookTitle(rs.getString("book_title"));
        loan.setMemberName(rs.getString("member_name"));
        loan.setMemberNo(rs.getString("member_no"));
        return loan;
    }
}
