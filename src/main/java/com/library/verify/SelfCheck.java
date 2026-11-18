package com.library.verify;

import com.library.db.DatabaseManager;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.service.LibraryException;
import com.library.service.LibraryService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Non-interactive end-to-end verification. Run with:  java ... com.library.Main --self-check
 * It resets the target database, exercises every feature and reports PASS/FAIL.
 */
public class SelfCheck {

    private final DatabaseManager db;
    private final LibraryService service;
    private int passed;
    private int failed;

    public SelfCheck(DatabaseManager db) {
        this.db = db;
        this.service = new LibraryService(db);
    }

    public int run() throws Exception {
        System.out.println("=========================================================");
        System.out.println("  LIBRARY MANAGEMENT SYSTEM - AUTOMATED SELF CHECK");
        System.out.println("=========================================================");

        resetDatabase();
        seedTestData();

        // --- data entry and search -----------------------------------
        check("books were inserted", service.countBooks() == 3);
        check("members were inserted", service.countMembers() == 2);
        check("total available copies = 4", service.countAvailableCopies() == 4);

        List<Book> found = service.searchBooks("effective");
        check("case-insensitive search finds 'Effective Java'", found.size() == 1
                && found.get(0).getTitle().equals("Effective Java"));

        check("duplicate ISBN is rejected", expectFailure(() -> {
            Book dup = new Book("978-0134685991", "Duplicate", "Someone");
            dup.setTotalCopies(1);
            service.addBook(dup);
        }));

        check("duplicate membership number is rejected", expectFailure(() ->
                service.addMember(new Member("M-9001", "Duplicate Person"))));

        // --- issuing --------------------------------------------------
        Loan loan = service.issueBook(bookId("978-0134685991"), memberId("M-9001"));
        check("issue creates an active loan", loan.isActive() && service.countActiveLoans() == 1);
        check("issue decrements available copies", service.countAvailableCopies() == 3);

        check("same member cannot borrow the same book twice", expectFailure(() ->
                service.issueBook(bookId("978-0134685991"), memberId("M-9001"))));

        // Exhaust the single copy of book C, then prove nobody else can borrow it.
        Loan singleCopyLoan = service.issueBook(bookId("978-1111111111"), memberId("M-9002"));
        check("book with no copies left cannot be borrowed", expectFailure(() ->
                service.issueBook(bookId("978-1111111111"), memberId("M-9001"))));
        service.returnBook(singleCopyLoan.getId());

        service.setMemberActive(memberId("M-9002"), false);
        check("inactive member cannot borrow", expectFailure(() ->
                service.issueBook(bookId("978-0596007126"), memberId("M-9002"))));
        service.setMemberActive(memberId("M-9002"), true);

        check("member with active loans cannot be deactivated", expectFailure(() ->
                service.setMemberActive(memberId("M-9001"), false)));

        // --- returning ------------------------------------------------
        BigDecimal fine = service.returnBook(loan.getId());
        check("on-time return has no fine", fine.compareTo(BigDecimal.ZERO) == 0);
        check("return restores available copies", service.countAvailableCopies() == 4);
        check("return clears the active loan", service.countActiveLoans() == 0);
        check("double return is rejected", expectFailure(() -> service.returnBook(loan.getId())));

        // --- overdue fine and history ---------------------------------
        Loan overdue = service.issueBook(bookId("978-0596007126"), memberId("M-9001"));
        makeOverdue(overdue.getId());
        BigDecimal overdueFine = service.returnBook(overdue.getId());
        check("3-day overdue loan produces \u20B93.00 fine",
                overdueFine.compareTo(new BigDecimal("3.00")) == 0);
        check("loan history is recorded", service.countTotalLoans() == 3);
        check("fines are stored", service.totalFines().compareTo(new BigDecimal("3.00")) == 0);

        // --- protective rules ----------------------------------------
        check("book with loan history cannot be deleted", expectFailure(() ->
                service.deleteBook(bookId("978-0596007126"))));
        check("member with loan history cannot be deleted", expectFailure(() ->
                service.deleteMember(memberId("M-9001"))));
        check("copies cannot drop below copies on loan", expectFailure(() ->
                service.updateBookCopies(bookId("978-0134685991"), 0)));

        // --- reports --------------------------------------------------
        check("overdue report is queryable", service.getOverdueLoans().isEmpty());
        check("summary counts are consistent", service.countAvailableCopies() == 4);

        System.out.println("-".repeat(57));
        System.out.printf("RESULT: %d passed, %d failed%n", passed, failed);
        System.out.println("=========================================================");
        return failed == 0 ? 0 : 1;
    }

    // ------------------------------------------------------------------
    private void resetDatabase() throws SQLException, java.io.IOException {
        db.executeScript(java.nio.file.Path.of("sql/schema.sql"));
    }

    private void seedTestData() throws SQLException {
        Book a = new Book("978-0134685991", "Effective Java", "Joshua Bloch");
        a.setTotalCopies(1);
        service.addBook(a);

        Book b = new Book("978-0596007126", "Design Patterns", "Erich Gamma");
        b.setTotalCopies(2);
        service.addBook(b);

        Book c = new Book("978-1111111111", "Single Copy Book", "Test Author");
        c.setTotalCopies(1);
        service.addBook(c);

        service.addMember(new Member("M-9001", "Test Reader One"));
        service.addMember(new Member("M-9002", "Test Reader Two"));
    }

    private int bookId(String isbn) throws SQLException {
        return service.getAllBooks().stream()
                .filter(b -> b.getIsbn().equals(isbn))
                .findFirst()
                .orElseThrow().getId();
    }

    private int memberId(String no) throws SQLException {
        return service.getAllMembers().stream()
                .filter(m -> m.getMembershipNo().equals(no))
                .findFirst()
                .orElseThrow().getId();
    }

    /** Moves a loan three days past its due date (keeping issued_at < due_at) for fine testing. */
    private void makeOverdue(int loanId) throws SQLException {
        String sql = "UPDATE loans SET issued_at = CURRENT_TIMESTAMP - INTERVAL '5 days', "
                + "due_at = CURRENT_TIMESTAMP - INTERVAL '3 days' WHERE id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, loanId);
            ps.executeUpdate();
        }
    }

    private void check(String label, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + label);
        } else {
            failed++;
            System.out.println("[FAIL] " + label);
        }
    }

    private boolean expectFailure(ThrowingRunnable action) {
        try {
            action.run();
            return false;
        } catch (LibraryException e) {
            return true;
        } catch (Exception e) {
            failed++;
            System.out.println("[FAIL] unexpected exception: " + e);
            return false;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
