package com.library.service;

import com.library.db.DatabaseManager;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import com.library.repository.MemberRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * All business rules live here. Every multi-step operation (issue / return /
 * cascading checks) runs in a single database transaction.
 */
public class LibraryService {

    public static final int LOAN_PERIOD_DAYS = 14;
    public static final int MAX_LOANS_PER_MEMBER = 5;
    public static final BigDecimal FINE_PER_DAY = new BigDecimal("1.00");

    private final DatabaseManager db;
    private final BookRepository books;
    private final MemberRepository members;
    private final LoanRepository loans;

    public LibraryService(DatabaseManager db) {
        this.db = db;
        this.books = new BookRepository(db);
        this.members = new MemberRepository(db);
        this.loans = new LoanRepository(db);
    }

    // ------------------------------------------------------------------
    // Books
    // ------------------------------------------------------------------

    public List<Book> getAllBooks() throws SQLException {
        return books.findAll();
    }

    public List<Book> searchBooks(String term) throws SQLException {
        requireText(term, "Search term");
        return books.search(term.trim());
    }

    public Optional<Book> findBook(int id) throws SQLException {
        return books.findById(id);
    }

    public int addBook(Book book) throws SQLException {
        if (book.getIsbn() == null || book.getIsbn().isBlank()) {
            throw new LibraryException("ISBN is required");
        }
        if (book.getTitle() == null || book.getTitle().isBlank()) {
            throw new LibraryException("Title is required");
        }
        if (book.getAuthor() == null || book.getAuthor().isBlank()) {
            throw new LibraryException("Author is required");
        }
        if (book.getTotalCopies() < 1) {
            throw new LibraryException("Number of copies must be at least 1");
        }
        if (books.findByIsbn(book.getIsbn().trim()).isPresent()) {
            throw new LibraryException("A book with ISBN '" + book.getIsbn() + "' already exists");
        }
        book.setIsbn(book.getIsbn().trim());
        book.setTitle(book.getTitle().trim());
        book.setAuthor(book.getAuthor().trim());
        return books.insert(book);
    }

    public void updateBookCopies(int bookId, int newTotal) throws SQLException {
        if (newTotal < 1) {
            throw new LibraryException("Total copies must be at least 1");
        }
        Book book = books.findById(bookId).orElseThrow(() -> new LibraryException("Book #" + bookId + " not found"));
        int onLoan = book.getTotalCopies() - book.getAvailableCopies();
        if (newTotal < onLoan) {
            throw new LibraryException("Cannot reduce copies to " + newTotal
                    + " while " + onLoan + " copy/copies are on loan");
        }
        book.setTotalCopies(newTotal);
        book.setAvailableCopies(newTotal - onLoan);
        if (!books.update(book)) {
            throw new LibraryException("Could not update book #" + bookId);
        }
    }

    public void deleteBook(int bookId) throws SQLException {
        if (!books.findById(bookId).isPresent()) {
            throw new LibraryException("Book #" + bookId + " not found");
        }
        if (!loans.findByBook(bookId).isEmpty()) {
            throw new LibraryException("Cannot delete book #" + bookId
                    + " because it has loan history. Deactivate or rename it instead.");
        }
        books.delete(bookId);
    }

    // ------------------------------------------------------------------
    // Members
    // ------------------------------------------------------------------

    public List<Member> getAllMembers() throws SQLException {
        return members.findAll();
    }

    public List<Member> searchMembers(String term) throws SQLException {
        requireText(term, "Search term");
        return members.search(term.trim());
    }

    public Optional<Member> findMember(int id) throws SQLException {
        return members.findById(id);
    }

    public int addMember(Member member) throws SQLException {
        if (member.getMembershipNo() == null || member.getMembershipNo().isBlank()) {
            throw new LibraryException("Membership number is required");
        }
        if (member.getFullName() == null || member.getFullName().isBlank()) {
            throw new LibraryException("Full name is required");
        }
        if (members.findByMembershipNo(member.getMembershipNo().trim()).isPresent()) {
            throw new LibraryException("Membership number '" + member.getMembershipNo() + "' is already in use");
        }
        member.setMembershipNo(member.getMembershipNo().trim());
        member.setFullName(member.getFullName().trim());
        return members.insert(member);
    }

    public void setMemberActive(int memberId, boolean active) throws SQLException {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new LibraryException("Member #" + memberId + " not found"));
        if (!active && loans.countActiveByMember(memberId) > 0) {
            throw new LibraryException("Cannot deactivate member #" + memberId
                    + " while they still have books on loan");
        }
        member.setActive(active);
        members.update(member);
    }

    public void deleteMember(int memberId) throws SQLException {
        if (!members.findById(memberId).isPresent()) {
            throw new LibraryException("Member #" + memberId + " not found");
        }
        if (!loans.findByMember(memberId).isEmpty()) {
            throw new LibraryException("Cannot delete member #" + memberId + " because they have loan history");
        }
        members.delete(memberId);
    }

    // ------------------------------------------------------------------
    // Issuing and returning
    // ------------------------------------------------------------------

    /** Issues one copy of a book to a member. Transactional. */
    public Loan issueBook(int bookId, int memberId) throws SQLException {
        db.beginTransaction();
        try {
            Book book = books.findById(bookId)
                    .orElseThrow(() -> new LibraryException("Book #" + bookId + " not found"));
            Member member = members.findById(memberId)
                    .orElseThrow(() -> new LibraryException("Member #" + memberId + " not found"));

            if (!member.isActive()) {
                throw new LibraryException("Member '" + member.getFullName() + "' is inactive and cannot borrow");
            }
            if (book.getAvailableCopies() < 1) {
                throw new LibraryException("'" + book.getTitle() + "' has no available copies");
            }
            if (loans.countActiveByMember(memberId) >= MAX_LOANS_PER_MEMBER) {
                throw new LibraryException("Member '" + member.getFullName() + "' has reached the limit of "
                        + MAX_LOANS_PER_MEMBER + " concurrent loans");
            }
            if (loans.findActiveByBookAndMember(bookId, memberId).isPresent()) {
                throw new LibraryException("Member already has an active loan for '" + book.getTitle() + "'");
            }
            if (!books.adjustCopies(bookId, -1)) {
                throw new LibraryException("Copy count changed concurrently; please retry");
            }

            Timestamp due = Timestamp.from(Instant.now().plus(LOAN_PERIOD_DAYS, ChronoUnit.DAYS));
            int loanId = loans.insert(bookId, memberId, due);
            db.commit();
            return loans.findById(loanId)
                    .orElseThrow(() -> new LibraryException("Could not reload issued loan #" + loanId));
        } catch (SQLException | LibraryException e) {
            db.rollbackQuietly();
            throw e;
        }
    }

    /** Returns a loan and computes the overdue fine. Transactional. */
    public BigDecimal returnBook(int loanId) throws SQLException {
        db.beginTransaction();
        try {
            Loan loan = loans.findById(loanId)
                    .orElseThrow(() -> new LibraryException("Loan #" + loanId + " not found"));
            if (!loan.isActive()) {
                throw new LibraryException("Loan #" + loanId + " was already returned on " + loan.getReturnedAt());
            }

            Instant now = Instant.now();
            BigDecimal fine = calculateFine(loan.getDueAt().toInstant(), now);
            if (!loans.markReturned(loanId, Timestamp.from(now), fine)) {
                throw new LibraryException("Could not mark loan #" + loanId + " as returned");
            }
            if (!books.adjustCopies(loan.getBookId(), +1)) {
                throw new LibraryException("Could not restore the copy for book #" + loan.getBookId());
            }
            db.commit();
            return fine;
        } catch (SQLException | LibraryException e) {
            db.rollbackQuietly();
            throw e;
        }
    }

    /** Finds the active loan for a book+member pair (handy for the Return menu). */
    public Optional<Loan> findActiveLoan(int bookId, int memberId) throws SQLException {
        return loans.findActiveByBookAndMember(bookId, memberId);
    }

    public Optional<Loan> findLoan(int loanId) throws SQLException {
        return loans.findById(loanId);
    }

    // ------------------------------------------------------------------
    // Reports
    // ------------------------------------------------------------------

    public List<Loan> getActiveLoans() throws SQLException {
        return loans.findActive();
    }

    public List<Loan> getOverdueLoans() throws SQLException {
        return loans.findOverdue();
    }

    public List<Loan> getLoanHistory(int limit) throws SQLException {
        return loans.findHistory(limit);
    }

    public List<Loan> getLoansForMember(int memberId) throws SQLException {
        return loans.findByMember(memberId);
    }

    public int countBooks() throws SQLException {
        return books.countAll();
    }

    public int countAvailableCopies() throws SQLException {
        return books.availableCopies();
    }

    public int countMembers() throws SQLException {
        return members.countAll();
    }

    public int countActiveLoans() throws SQLException {
        return loans.countActive();
    }

    public int countTotalLoans() throws SQLException {
        return loans.countAll();
    }

    public BigDecimal totalFines() throws SQLException {
        return loans.totalFines();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Fine = FINE_PER_DAY per whole (or started) day after the due date.
     * Comparing calendar days keeps it deterministic regardless of the hour.
     */
    public static BigDecimal calculateFine(Instant dueAt, Instant now) {
        if (!now.isAfter(dueAt)) {
            return BigDecimal.ZERO;
        }
        long days = ChronoUnit.DAYS.between(
                dueAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                now.atZone(java.time.ZoneId.systemDefault()).toLocalDate());
        if (days < 1) {
            days = 1;
        }
        return FINE_PER_DAY.multiply(BigDecimal.valueOf(days));
    }

    public static LocalDate dueDateForNewLoan() {
        return LocalDate.now().plusDays(LOAN_PERIOD_DAYS);
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new LibraryException(label + " is required");
        }
    }
}
