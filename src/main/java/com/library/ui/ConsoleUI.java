package com.library.ui;

import com.library.config.AppConfig;
import com.library.db.DatabaseManager;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.service.LibraryException;
import com.library.service.LibraryService;
import com.library.util.Money;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Menu-driven command line interface. */
public class ConsoleUI {

    private final LibraryService service;
    private final DatabaseManager db;
    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

    public ConsoleUI(LibraryService service, DatabaseManager db) {
        this.service = service;
        this.db = db;
    }

    public void run() throws IOException, SQLException {
        printBanner();
        while (true) {
            System.out.println();
            System.out.println("========== MAIN MENU ==========");
            System.out.println(" 1) Books");
            System.out.println(" 2) Members");
            System.out.println(" 3) Issue a book");
            System.out.println(" 4) Return a book");
            System.out.println(" 5) Reports");
            System.out.println(" 6) Database setup (load schema + sample data)");
            System.out.println(" 0) Exit");
            String choice = prompt("Choose an option: ");
            try {
                switch (choice) {
                    case "1" -> booksMenu();
                    case "2" -> membersMenu();
                    case "3" -> issueBook();
                    case "4" -> returnBook();
                    case "5" -> reportsMenu();
                    case "6" -> setupDatabase();
                    case "0" -> {
                        System.out.println("Goodbye!");
                        return;
                    }
                    default -> System.out.println("Unknown option: " + choice);
                }
            } catch (LibraryException e) {
                System.out.println("! " + e.getMessage());
            } catch (SQLException e) {
                System.out.println("! Database error: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Books
    // ------------------------------------------------------------------

    private void booksMenu() throws IOException, SQLException {
        while (true) {
            System.out.println();
            System.out.println("---- BOOKS ----");
            System.out.println(" 1) List all books");
            System.out.println(" 2) Search books");
            System.out.println(" 3) Add a book");
            System.out.println(" 4) Change number of copies");
            System.out.println(" 5) Delete a book");
            System.out.println(" 0) Back");
            String choice = prompt("Choose an option: ");
            switch (choice) {
                case "1" -> printBooks(service.getAllBooks());
                case "2" -> printBooks(service.searchBooks(prompt("Search ISBN / title / author: ")));
                case "3" -> addBook();
                case "4" -> changeCopies();
                case "5" -> {
                    printBooks(service.getAllBooks());
                    service.deleteBook(readInt("Book id to delete: "));
                    System.out.println("Book deleted.");
                }
                case "0" -> {
                    return;
                }
                default -> System.out.println("Unknown option: " + choice);
            }
        }
    }

    private void addBook() throws IOException, SQLException {
        System.out.println("(press Enter to leave optional fields blank)");
        Book book = new Book();
        book.setIsbn(prompt("ISBN: "));
        book.setTitle(prompt("Title: "));
        book.setAuthor(prompt("Author: "));
        book.setPublisher(blankToNull(prompt("Publisher: ")));
        String year = prompt("Publication year: ");
        if (!year.isBlank()) {
            book.setPublicationYear(parseYear(year));
        }
        book.setGenre(blankToNull(prompt("Genre: ")));
        book.setTotalCopies(readInt("Number of copies: "));
        int id = service.addBook(book);
        System.out.println("Book added with id " + id + ".");
    }

    private void changeCopies() throws IOException, SQLException {
        printBooks(service.getAllBooks());
        int id = readInt("Book id: ");
        int copies = readInt("New total number of copies: ");
        service.updateBookCopies(id, copies);
        System.out.println("Copies updated.");
    }

    // ------------------------------------------------------------------
    // Members
    // ------------------------------------------------------------------

    private void membersMenu() throws IOException, SQLException {
        while (true) {
            System.out.println();
            System.out.println("---- MEMBERS ----");
            System.out.println(" 1) List all members");
            System.out.println(" 2) Search members");
            System.out.println(" 3) Add a member");
            System.out.println(" 4) Activate / deactivate a member");
            System.out.println(" 5) Delete a member");
            System.out.println(" 6) Loan history for a member");
            System.out.println(" 0) Back");
            String choice = prompt("Choose an option: ");
            switch (choice) {
                case "1" -> printMembers(service.getAllMembers());
                case "2" -> printMembers(service.searchMembers(prompt("Search number / name / email: ")));
                case "3" -> addMember();
                case "4" -> toggleMember();
                case "5" -> {
                    printMembers(service.getAllMembers());
                    service.deleteMember(readInt("Member id to delete: "));
                    System.out.println("Member deleted.");
                }
                case "6" -> {
                    printMembers(service.getAllMembers());
                    printLoans(service.getLoansForMember(readInt("Member id: ")));
                }
                case "0" -> {
                    return;
                }
                default -> System.out.println("Unknown option: " + choice);
            }
        }
    }

    private void addMember() throws IOException, SQLException {
        Member member = new Member();
        member.setMembershipNo(prompt("Membership number (e.g. M-1005): "));
        member.setFullName(prompt("Full name: "));
        member.setEmail(blankToNull(prompt("Email: ")));
        member.setPhone(blankToNull(prompt("Phone: ")));
        member.setAddress(blankToNull(prompt("Address: ")));
        int id = service.addMember(member);
        System.out.println("Member added with id " + id + ".");
    }

    private void toggleMember() throws IOException, SQLException {
        printMembers(service.getAllMembers());
        int id = readInt("Member id: ");
        Member member = service.findMember(id)
                .orElseThrow(() -> new LibraryException("Member #" + id + " not found"));
        service.setMemberActive(id, !member.isActive());
        System.out.println("Member is now " + (member.isActive() ? "INACTIVE" : "ACTIVE") + ".");
    }

    // ------------------------------------------------------------------
    // Issue / return
    // ------------------------------------------------------------------

    private void issueBook() throws IOException, SQLException {
        System.out.println("---- ISSUE A BOOK ----");
        printBooks(service.getAllBooks());
        int bookId = readInt("Book id: ");
        System.out.println("---- ACTIVE MEMBERS ----");
        printMembers(service.getAllMembers().stream().filter(Member::isActive).toList());
        int memberId = readInt("Member id: ");
        Loan loan = service.issueBook(bookId, memberId);
        System.out.printf("Issued! Loan #%d, due %s.%n", loan.getId(), loan.getDueAt().toLocalDateTime());
    }

    private void returnBook() throws IOException, SQLException {
        System.out.println("---- ACTIVE LOANS ----");
        printLoans(service.getActiveLoans());
        int loanId = readInt("Loan id to return: ");
        BigDecimal fine = service.returnBook(loanId);
        if (fine.compareTo(BigDecimal.ZERO) > 0) {
            System.out.printf("Returned. Overdue fine: %s%n", Money.format(fine));
        } else {
            System.out.println("Returned on time - no fine.");
        }
    }

    // ------------------------------------------------------------------
    // Reports
    // ------------------------------------------------------------------

    private void reportsMenu() throws IOException, SQLException {
        while (true) {
            System.out.println();
            System.out.println("---- REPORTS ----");
            System.out.println(" 1) Collection summary");
            System.out.println(" 2) Currently borrowed books");
            System.out.println(" 3) Overdue books");
            System.out.println(" 4) Recent loan history");
            System.out.println(" 0) Back");
            String choice = prompt("Choose an option: ");
            switch (choice) {
                case "1" -> printSummary();
                case "2" -> printLoans(service.getActiveLoans());
                case "3" -> printLoans(service.getOverdueLoans());
                case "4" -> printLoans(service.getLoanHistory(20));
                case "0" -> {
                    return;
                }
                default -> System.out.println("Unknown option: " + choice);
            }
        }
    }

    private void printSummary() throws SQLException {
        System.out.println();
        System.out.println("---- COLLECTION SUMMARY ----");
        System.out.printf("  Books (titles)      : %d%n", service.countBooks());
        System.out.printf("  Copies available    : %d%n", service.countAvailableCopies());
        System.out.printf("  Members             : %d%n", service.countMembers());
        System.out.printf("  Active loans        : %d%n", service.countActiveLoans());
        System.out.printf("  Total loans recorded: %d%n", service.countTotalLoans());
        System.out.printf("  Fines collected     : %s%n", Money.format(service.totalFines()));
    }

    // ------------------------------------------------------------------
    // Setup + printing helpers
    // ------------------------------------------------------------------

    private void setupDatabase() throws IOException, SQLException {
        System.out.print("This will ERASE existing data and reload schema.sql + seed.sql. Continue? [y/N] ");
        String answer = in.readLine();
        if (answer != null && answer.equalsIgnoreCase("y")) {
            db.initializeDatabase(Path.of("sql"));
            System.out.println("Database reinitialized with sample data.");
        } else {
            System.out.println("Cancelled.");
        }
    }

    private void printBooks(List<Book> books) {
        if (books.isEmpty()) {
            System.out.println("(no books found)");
            return;
        }
        System.out.printf("%-5s %-38s %-24s %-18s %-8s %-6s%n", "ID", "TITLE", "AUTHOR", "ISBN", "AVAIL", "TOTAL");
        System.out.println("-".repeat(105));
        for (Book b : books) {
            System.out.printf("%-5d %-38.38s %-24.24s %-18.18s %-8d %-6d%n",
                    b.getId(), b.getTitle(), b.getAuthor(), b.getIsbn(),
                    b.getAvailableCopies(), b.getTotalCopies());
        }
        System.out.println("(" + books.size() + " book(s))");
    }

    private void printMembers(List<Member> members) {
        if (members.isEmpty()) {
            System.out.println("(no members found)");
            return;
        }
        System.out.printf("%-5s %-12s %-24s %-30s %-8s%n", "ID", "MEMBER NO", "NAME", "EMAIL", "STATUS");
        System.out.println("-".repeat(85));
        for (Member m : members) {
            System.out.printf("%-5d %-12.12s %-24.24s %-30.30s %-8s%n",
                    m.getId(), m.getMembershipNo(), m.getFullName(),
                    m.getEmail() == null ? "-" : m.getEmail(), m.isActive() ? "ACTIVE" : "INACTIVE");
        }
        System.out.println("(" + members.size() + " member(s))");
    }

    private void printLoans(List<Loan> loans) {
        if (loans.isEmpty()) {
            System.out.println("(no loans found)");
            return;
        }
        System.out.printf("%-5s %-30s %-22s %-19s %-19s %-10s %-8s%n",
                "ID", "BOOK", "MEMBER", "ISSUED", "DUE", "STATUS", "FINE");
        System.out.println("-".repeat(120));
        for (Loan l : loans) {
            System.out.printf("%-5d %-30.30s %-22.22s %-19.19s %-19.19s %-10s %-8s%n",
                    l.getId(), l.getBookTitle(), l.getMemberName(),
                    l.getIssuedAt().toLocalDateTime().toString(),
                    l.getDueAt().toLocalDateTime().toString(),
                    l.isActive() ? "ACTIVE" : "RETURNED",
                    Money.format(l.getFineAmount()));
        }
        System.out.println("(" + loans.size() + " loan(s))");
    }

    private void printBanner() throws SQLException {
        System.out.println("=====================================================");
        System.out.println("      ARULIBRARY  (Java + PostgreSQL)");
        System.out.println("=====================================================");
        System.out.println("Connected to " + new AppConfig().describeConnection());
        System.out.println("Today: " + java.time.LocalDate.now());
    }

    // ------------------------------------------------------------------
    // Input helpers
    // ------------------------------------------------------------------

    private String prompt(String message) throws IOException {
        System.out.print(message);
        String line = in.readLine();
        return line == null ? "" : line.trim();
    }

    private int readInt(String message) throws IOException {
        while (true) {
            String value = prompt(message);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number.");
            }
        }
    }

    private Integer parseYear(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new LibraryException("Invalid publication year: " + value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
