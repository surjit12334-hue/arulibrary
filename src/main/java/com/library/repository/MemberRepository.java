package com.library.repository;

import com.library.db.DatabaseManager;
import com.library.model.Member;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MemberRepository {

    private static final String SELECT =
            "SELECT id, membership_no, full_name, email, phone, address, joined_at, active FROM members";

    private final DatabaseManager db;

    public MemberRepository(DatabaseManager db) {
        this.db = db;
    }

    public int insert(Member member) throws SQLException {
        String sql = "INSERT INTO members (membership_no, full_name, email, phone, address) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, member.getMembershipNo());
            ps.setString(2, member.getFullName());
            setNullableString(ps, 3, member.getEmail());
            setNullableString(ps, 4, member.getPhone());
            setNullableString(ps, 5, member.getAddress());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Inserting member did not return a generated id");
    }

    public boolean update(Member member) throws SQLException {
        String sql = "UPDATE members SET membership_no=?, full_name=?, email=?, phone=?, address=?, active=? WHERE id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, member.getMembershipNo());
            ps.setString(2, member.getFullName());
            setNullableString(ps, 3, member.getEmail());
            setNullableString(ps, 4, member.getPhone());
            setNullableString(ps, 5, member.getAddress());
            ps.setBoolean(6, member.isActive());
            ps.setInt(7, member.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement("DELETE FROM members WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<Member> findById(int id) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(SELECT + " WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Member> findByMembershipNo(String no) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(SELECT + " WHERE membership_no=?")) {
            ps.setString(1, no);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Member> findAll() throws SQLException {
        return query(SELECT + " ORDER BY id");
    }

    public List<Member> search(String term) throws SQLException {
        String like = "%" + term.toLowerCase() + "%";
        String sql = SELECT + " WHERE lower(membership_no) LIKE ? OR lower(full_name) LIKE ? "
                + "OR lower(COALESCE(email,'')) LIKE ? ORDER BY id";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            return collect(ps);
        }
    }

    public int countAll() throws SQLException {
        return count("SELECT COUNT(*) FROM members");
    }

    public int countActive() throws SQLException {
        return count("SELECT COUNT(*) FROM members WHERE active");
    }

    private List<Member> query(String sql) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            return collect(ps);
        }
    }

    private List<Member> collect(PreparedStatement ps) throws SQLException {
        List<Member> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        }
        return result;
    }

    private int count(String sql) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private Member map(ResultSet rs) throws SQLException {
        Member m = new Member();
        m.setId(rs.getInt("id"));
        m.setMembershipNo(rs.getString("membership_no"));
        m.setFullName(rs.getString("full_name"));
        m.setEmail(rs.getString("email"));
        m.setPhone(rs.getString("phone"));
        m.setAddress(rs.getString("address"));
        Date joined = rs.getDate("joined_at");
        m.setJoinedAt(joined == null ? null : joined.toLocalDate());
        m.setActive(rs.getBoolean("active"));
        return m;
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
