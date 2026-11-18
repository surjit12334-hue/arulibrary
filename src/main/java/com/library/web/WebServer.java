package com.library.web;

import com.library.db.DatabaseManager;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.service.LibraryException;
import com.library.service.LibraryService;
import com.library.util.Money;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Small HTTP server for the browser front end. It exposes the existing
 * LibraryService as a JSON API and serves the static pages from web/.
 *
 * A single-threaded executor is intentional: DatabaseManager owns one shared
 * JDBC connection, so requests are serialised.
 */
public final class WebServer {

    private final LibraryService service;
    private final DatabaseManager db;
    private final Path webRoot;
    private final int port;
    private final String host;
    private HttpServer server;

    public WebServer(LibraryService service, DatabaseManager db, int port, String host) {
        this.service = service;
        this.db = db;
        this.port = port;
        this.host = host;
        this.webRoot = Path.of("web").toAbsolutePath().normalize();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        // Specific context first; the root context handles static files.
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "library-web");
            t.setDaemon(true);
            return t;
        }));
        server.start();

        System.out.println("=========================================================");
        System.out.println("   LIBRARY MANAGEMENT SYSTEM - WEB INTERFACE");
        System.out.println("=========================================================");
        System.out.println("Open in a browser:  http://localhost:" + port + "/");
        System.out.println("API base:           http://localhost:" + port + "/api/");
        System.out.println("Static files:       " + webRoot);
        System.out.println("Press Ctrl+C to stop the server.");
        System.out.println();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // Static files
    // ------------------------------------------------------------------

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        Path relative = Path.of(path.substring(1)).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            sendBytes(ex, 403, "text/plain; charset=utf-8", "Forbidden".getBytes(StandardCharsets.UTF_8));
            return;
        }
        Path file = webRoot.resolve(relative).normalize();
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            byte[] body = ("<h1>404 - Not found</h1><p>No file at " + path
                    + "</p><p><a href='/'>Back to dashboard</a></p>")
                    .getBytes(StandardCharsets.UTF_8);
            sendBytes(ex, 404, "text/html; charset=utf-8", body);
            return;
        }
        sendBytes(ex, 200, contentType(file), Files.readAllBytes(file));
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------
    // JSON API
    // ------------------------------------------------------------------

    private void handleApi(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        Map<String, String> query = Json.parseForm(ex.getRequestURI().getRawQuery());
        Map<String, String> form = Map.of();
        if ("POST".equals(method) || "PUT".equals(method)) {
            form = Json.parseForm(readBody(ex));
        }
        System.out.println("[web] " + method + " " + ex.getRequestURI());

        int status = 200;
        String response;
        try {
            response = route(method, path, query, form);
            if (response == null) {
                status = 404;
                response = Json.error("Unknown API endpoint: " + path);
            }
        } catch (LibraryException e) {
            status = 400;
            response = Json.error(e.getMessage());
        } catch (SQLException e) {
            status = 500;
            response = Json.error("Database error: " + e.getMessage());
        } catch (NumberFormatException e) {
            status = 400;
            response = Json.error("A number was expected but text was supplied");
        } catch (Exception e) {
            status = 500;
            response = Json.error(e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }
        sendBytes(ex, status, "application/json; charset=utf-8",
                response.getBytes(StandardCharsets.UTF_8));
    }

    private String route(String method, String path, Map<String, String> q, Map<String, String> f)
            throws SQLException, IOException {
        // ---- dashboard / health ----
        if (path.equals("/api/health")) {
            return Json.obj("status", Json.str("ok"), "time", Json.str(Instant.now().toString()));
        }
        if (path.equals("/api/dashboard")) {
            return Json.obj(
                    "books", Json.num(service.countBooks()),
                    "availableCopies", Json.num(service.countAvailableCopies()),
                    "members", Json.num(service.countMembers()),
                    "activeLoans", Json.num(service.countActiveLoans()),
                    "totalLoans", Json.num(service.countTotalLoans()),
                    "fines", Json.dec(service.totalFines()),
                    "overdue", Json.num(service.getOverdueLoans().size()),
                    "loanPeriodDays", Json.num(LibraryService.LOAN_PERIOD_DAYS),
                    "maxLoansPerMember", Json.num(LibraryService.MAX_LOANS_PER_MEMBER),
                    "finePerDay", Json.dec(LibraryService.FINE_PER_DAY),
                    "currency", Json.str("INR"),
                    "currencySymbol", Json.str(Money.SYMBOL),
                    "today", Json.str(LocalDate.now().toString()));
        }
        if (path.equals("/api/setup") && method.equals("POST")) {
            db.initializeDatabase(Path.of("sql"));
            return Json.ok("Sample data reloaded");
        }

        // ---- books ----
        if (path.equals("/api/books")) {
            if (method.equals("GET")) {
                String term = q.get("q");
                List<Book> books = (term == null || term.isBlank())
                        ? service.getAllBooks() : service.searchBooks(term);
                return Json.arr(books.stream().map(WebServer::bookJson).toArray(String[]::new));
            }
            if (method.equals("POST")) {
                Book b = new Book(f.get("isbn"), f.get("title"), f.get("author"));
                b.setPublisher(blankToNull(f.get("publisher")));
                b.setPublicationYear(blankToNullInt(f.get("publicationYear")));
                b.setGenre(blankToNull(f.get("genre")));
                b.setTotalCopies(parseInt(f.get("totalCopies"), "Number of copies", 1));
                int id = service.addBook(b);
                return Json.obj("ok", Json.str("Book added"), "id", Json.num(id));
            }
        }
        if (path.equals("/api/books/update") && method.equals("POST")) {
            int id = parseInt(f.get("id"), "Book id", 1);
            int copies = parseInt(f.get("totalCopies"), "Total copies", 1);
            service.updateBookCopies(id, copies);
            return Json.ok("Copies updated");
        }
        if (path.equals("/api/books/delete") && method.equals("POST")) {
            service.deleteBook(parseInt(f.get("id"), "Book id", 1));
            return Json.ok("Book deleted");
        }

        // ---- members ----
        if (path.equals("/api/members")) {
            if (method.equals("GET")) {
                String term = q.get("q");
                List<Member> members = (term == null || term.isBlank())
                        ? service.getAllMembers() : service.searchMembers(term);
                return Json.arr(members.stream().map(WebServer::memberJson).toArray(String[]::new));
            }
            if (method.equals("POST")) {
                Member m = new Member(f.get("membershipNo"), f.get("fullName"));
                m.setEmail(blankToNull(f.get("email")));
                m.setPhone(blankToNull(f.get("phone")));
                m.setAddress(blankToNull(f.get("address")));
                int id = service.addMember(m);
                return Json.obj("ok", Json.str("Member added"), "id", Json.num(id));
            }
        }
        if (path.equals("/api/members/status") && method.equals("POST")) {
            int id = parseInt(f.get("id"), "Member id", 1);
            boolean active = "true".equalsIgnoreCase(f.getOrDefault("active", ""));
            service.setMemberActive(id, active);
            return Json.ok(active ? "Member activated" : "Member deactivated");
        }
        if (path.equals("/api/members/delete") && method.equals("POST")) {
            service.deleteMember(parseInt(f.get("id"), "Member id", 1));
            return Json.ok("Member deleted");
        }

        // ---- loans ----
        if (path.equals("/api/loans")) {
            if (method.equals("GET")) {
                String view = q.getOrDefault("view", "active");
                List<Loan> loans;
                if ("overdue".equals(view)) {
                    loans = service.getOverdueLoans();
                } else if ("history".equals(view)) {
                    int limit = parseInt(q.getOrDefault("limit", "50"), "Limit", 1);
                    loans = service.getLoanHistory(limit);
                } else {
                    loans = service.getActiveLoans();
                }
                return Json.arr(loans.stream().map(WebServer::loanJson).toArray(String[]::new));
            }
            if (method.equals("POST")) {
                Loan loan = service.issueBook(
                        parseInt(f.get("bookId"), "Book id", 1),
                        parseInt(f.get("memberId"), "Member id", 1));
                return Json.obj("ok", Json.str("Book issued"), "id", Json.num(loan.getId()),
                        "dueAt", Json.str(ts(loan.getDueAt())),
                        "loan", loanJson(loan));
            }
        }
        if (path.equals("/api/loans/return") && method.equals("POST")) {
            BigDecimal fine = service.returnBook(parseInt(f.get("loanId"), "Loan id", 1));
            return Json.obj("ok", Json.str(fine.signum() == 0
                            ? "Returned on time - no fine"
                            : "Returned with a fine of " + Money.format(fine)),
                    "fine", Json.dec(fine),
                    "fineFormatted", Json.str(Money.format(fine)));
        }
        if (path.equals("/api/loans/member") && method.equals("GET")) {
            int id = parseInt(q.get("memberId"), "Member id", 1);
            List<Loan> loans = service.getLoansForMember(id);
            return Json.arr(loans.stream().map(WebServer::loanJson).toArray(String[]::new));
        }

        return null;
    }

    // ------------------------------------------------------------------
    // Serialization and parsing helpers
    // ------------------------------------------------------------------

    private static String bookJson(Book b) {
        return Json.obj(
                "id", Json.num(b.getId()),
                "isbn", Json.str(b.getIsbn()),
                "title", Json.str(b.getTitle()),
                "author", Json.str(b.getAuthor()),
                "publisher", Json.str(b.getPublisher()),
                "year", b.getPublicationYear() == null ? "null" : Json.num(b.getPublicationYear()),
                "genre", Json.str(b.getGenre()),
                "totalCopies", Json.num(b.getTotalCopies()),
                "availableCopies", Json.num(b.getAvailableCopies()),
                "createdAt", Json.str(ts(b.getCreatedAt())));
    }

    private static String memberJson(Member m) {
        return Json.obj(
                "id", Json.num(m.getId()),
                "membershipNo", Json.str(m.getMembershipNo()),
                "fullName", Json.str(m.getFullName()),
                "email", Json.str(m.getEmail()),
                "phone", Json.str(m.getPhone()),
                "address", Json.str(m.getAddress()),
                "joinedAt", Json.str(m.getJoinedAt() == null ? "" : m.getJoinedAt().toString()),
                "active", Json.bool(m.isActive()));
    }

    private static String loanJson(Loan l) {
        boolean overdue = l.isActive() && l.getDueAt() != null
                && l.getDueAt().toInstant().isBefore(Instant.now());
        return Json.obj(
                "id", Json.num(l.getId()),
                "bookId", Json.num(l.getBookId()),
                "memberId", Json.num(l.getMemberId()),
                "bookTitle", Json.str(l.getBookTitle()),
                "memberName", Json.str(l.getMemberName()),
                "memberNo", Json.str(l.getMemberNo()),
                "issuedAt", Json.str(ts(l.getIssuedAt())),
                "dueAt", Json.str(ts(l.getDueAt())),
                "returnedAt", Json.str(ts(l.getReturnedAt())),
                "fine", Json.dec(l.getFineAmount()),
                "active", Json.bool(l.isActive()),
                "overdue", Json.bool(overdue));
    }

    private static String ts(Timestamp value) {
        return value == null ? "" : value.toLocalDateTime().toString();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static Integer blankToNullInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.valueOf(value.trim());
    }

    private static int parseInt(String value, String label, int min) {
        if (value == null || value.isBlank()) {
            throw new LibraryException(label + " is required");
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new LibraryException(label + " must be a whole number");
        }
        if (parsed < min) {
            throw new LibraryException(label + " must be at least " + min);
        }
        return parsed;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        if (bytes.length > 64 * 1024) {
            throw new LibraryException("Request body is too large");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void sendBytes(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
