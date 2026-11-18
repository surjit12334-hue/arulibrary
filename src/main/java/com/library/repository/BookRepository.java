package com.library.repository;

import com.library.db.DatabaseManager;
import com.library.model.Book;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BookRepository {

    private static final String SELECT = "SELECT id, isbn, title, author, publisher, publication_year, "
            + "genre, total_copies, available_copies, created_at FROM books";

    private final DatabaseManager db;

    public BookRepository(DatabaseManager db) {
        this.db = db;
    }

    public int insert(Book book) throws SQLException {
        String sql = "INSERT INTO books (isbn, title, author, publisher, publication_year, genre, "
                + "total_copies, available_copies) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, book.getIsbn());
            ps.setString(2, book.getTitle());
            ps.setString(3, book.getAuthor());
            setNullableString(ps, 4, book.getPublisher());
            if (book.getPublicationYear() == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, book.getPublicationYear());
            }
            setNullableString(ps, 6, book.getGenre());
            ps.setInt(7, book.getTotalCopies());
            ps.setInt(8, book.getTotalCopies());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Inserting book did not return a generated id");
    }

    public boolean update(Book book) throws SQLException {
        String sql = "UPDATE books SET isbn=?, title=?, author=?, publisher=?, publication_year=?, "
                + "genre=?, total_copies=?, available_copies=? WHERE id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, book.getIsbn());
            ps.setString(2, book.getTitle());
            ps.setString(3, book.getAuthor());
            setNullableString(ps, 4, book.getPublisher());
            if (book.getPublicationYear() == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, book.getPublicationYear());
            }
            setNullableString(ps, 6, book.getGenre());
            ps.setInt(7, book.getTotalCopies());
            ps.setInt(8, book.getAvailableCopies());
            ps.setInt(9, book.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement("DELETE FROM books WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<Book> findById(int id) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(SELECT + " WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Book> findByIsbn(String isbn) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(SELECT + " WHERE isbn=?")) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Book> findAll() throws SQLException {
        return query(SELECT + " ORDER BY id");
    }

    public List<Book> findAvailable() throws SQLException {
        return query(SELECT + " WHERE available_copies > 0 ORDER BY id");
    }

    /** Case-insensitive substring search across ISBN, title and author. */
    public List<Book> search(String term) throws SQLException {
        String like = "%" + term.toLowerCase() + "%";
        String sql = SELECT + " WHERE lower(isbn) LIKE ? OR lower(title) LIKE ? OR lower(author) LIKE ? ORDER BY id";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            return collect(ps);
        }
    }

    /** Called inside a transaction from LoanService. Positive delta = return, negative = issue. */
    public boolean adjustCopies(int bookId, int delta) throws SQLException {
        String sql = "UPDATE books SET available_copies = available_copies + ? "
                + "WHERE id = ? AND available_copies + ? BETWEEN 0 AND total_copies";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setInt(2, bookId);
            ps.setInt(3, delta);
            return ps.executeUpdate() > 0;
        }
    }

    public int countAll() throws SQLException {
        return count("SELECT COUNT(*) FROM books");
    }

    public int countAvailableTitles() throws SQLException {
        return count("SELECT COUNT(*) FROM books WHERE available_copies > 0");
    }

    public int totalCopies() throws SQLException {
        return count("SELECT COALESCE(SUM(total_copies), 0) FROM books");
    }

    public int availableCopies() throws SQLException {
        return count("SELECT COALESCE(SUM(available_copies), 0) FROM books");
    }

    private List<Book> query(String sql) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            return collect(ps);
        }
    }

    private List<Book> collect(PreparedStatement ps) throws SQLException {
        List<Book> result = new ArrayList<>();
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

    private Book map(ResultSet rs) throws SQLException {
        Book b = new Book();
        b.setId(rs.getInt("id"));
        b.setIsbn(rs.getString("isbn"));
        b.setTitle(rs.getString("title"));
        b.setAuthor(rs.getString("author"));
        b.setPublisher(rs.getString("publisher"));
        int year = rs.getInt("publication_year");
        b.setPublicationYear(rs.wasNull() ? null : year);
        b.setGenre(rs.getString("genre"));
        b.setTotalCopies(rs.getInt("total_copies"));
        b.setAvailableCopies(rs.getInt("available_copies"));
        b.setCreatedAt(rs.getTimestamp("created_at"));
        return b;
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
