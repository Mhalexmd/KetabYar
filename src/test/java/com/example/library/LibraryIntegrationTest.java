package com.example.library;

import com.example.library.api.LibraryApiServer;
import com.example.library.api.MiniJson;
import com.example.library.db.Database;
import com.example.library.export.CsvExporter;
import com.example.library.model.Book;
import com.example.library.model.Loan;
import com.example.library.model.Member;
import com.example.library.repository.*;
import com.example.library.service.*;
import javax.swing.table.DefaultTableModel;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Dependency-free integration checks against a fresh, isolated SQLite database. */
public final class LibraryIntegrationTest {
    private static int checks;
    private static HttpClient client;

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("ketabyar-test-");
        Path file = folder.resolve("test.db");
        try {
            check(PersianSearch.matches("كيوان ۱۲۳", "کیوان", "123"), "Arabic spelling and Persian digits");
            check(PersianSearch.matches("مدیریت", "مُديريت"), "Diacritics");
            check(PersianSearch.matches("کتابیار", "کتاب‌یار"), "Half-space");
            check(!PersianSearch.matches("مفقود", "کتاب"), "No false match");
            try (Database database = new Database(file)) {
                LibraryService service = service(database);
                Book book = service.addBook("مدیریت کتابخانه", "کیوان", "ISBN-123", 2);
                Member member = service.addMember("علی کریمی", "123", "09120000000");
                check(service.searchBooks("كيوان").size() == 1, "Persian catalog search");
                check(service.searchMembers("۱۲۳").size() == 1, "Persian member search");
                rejects(() -> service.addBook("تکراری", "نویسنده", "isbn-123", 1), "Duplicate ISBN");
                rejects(() -> service.addMember("تکراری", "123", ""), "Duplicate student number");
                Member codeMember = service.addMember("عضو آزمایشی", "AB-1", "");
                rejects(() -> service.addMember("تکراری", "ab-1", ""), "Student number case collision");
                service.deleteMember(codeMember.getId());
                rejects(() -> service.addBook(" ", "نویسنده", "empty", 1), "Blank title");
                rejects(() -> service.borrowBook(book.getId(), member.getId(), LocalDate.now().minusDays(1)), "Past due date");
                check(service.getAvailableCopiesCount() == 2, "Rejected loan preserves inventory");
                Loan loan = service.borrowBook(book.getId(), member.getId(), LocalDate.now().plusDays(14));
                check(service.getAvailableCopiesCount() == 1, "Borrow reduces inventory");
                check(service.getLoans(LoanFilter.ACTIVE).size() == 1, "Active filter");
                rejects(() -> service.deleteBook(book.getId()), "History protects book");
                service.returnBook(loan.getId());
                check(service.getAvailableCopiesCount() == 2, "Return restores inventory");
                rejects(() -> service.returnBook(loan.getId()), "Second return rejected");
                check(service.getAvailableCopiesCount() == 2, "Second return does not inflate inventory");
                check(service.getLoans(LoanFilter.RETURNED).size() == 1, "Returned filter");

                SqliteBookRepository books = new SqliteBookRepository(database);
                rejects(() -> database.inTransaction(() -> {
                    books.decrementAvailableCopies(book.getId());
                    throw new IllegalArgumentException("force rollback");
                }), "Transaction failure");
                check(service.getAvailableCopiesCount() == 2, "Transaction rollback restores inventory");
                database.inTransaction(() -> {
                    books.decrementAvailableCopies(book.getId());
                    new SqliteLoanRepository(database).insert(book.getId(), member.getId(),
                            LocalDate.now().minusDays(5), LocalDate.now().minusDays(2));
                });
                check(service.getLoans(LoanFilter.OVERDUE).size() == 1, "Overdue filter");
                check(service.getLoans(LoanFilter.OVERDUE).get(0).getFineToman() == 20_000, "Two-day fine");

                if (!Arrays.asList(args).contains("--core-only")) {
                try (LibraryApiServer api = new LibraryApiServer(service, 0)) {
                    api.start();
                    String base = "http://127.0.0.1:" + api.getPort();
                    check(request(base, "GET", "/api/health", null, 200).contains("ok"), "Health response");
                    check(request(base, "GET", "/", null, 200).contains(AppInfo.NAME), "Personal API identity");
                    String query = URLEncoder.encode("كيوان", StandardCharsets.UTF_8);
                    check(request(base, "GET", "/api/books?q=" + query, null, 200).contains("ISBN-123"), "API normalized search");
                    check(request(base, "GET", "/api/books?q=missing", null, 200).equals("[]"), "API empty result");
                    request(base, "GET", "/api/loans?status=overdue", null, 200);
                    request(base, "GET", "/api/loans?status=invalid", null, 400);
                    request(base, "GET", "/api/books/99999", null, 404);
                    request(base, "POST", "/api/books", "{}", 400);
                    request(base, "POST", "/api/books", "{\"title\":\"Duplicate\",\"author\":\"A\",\"isbn\":\"ISBN-123\",\"totalCopies\":1}", 409);
                    int newBook = id(request(base, "POST", "/api/books", "{\"title\":\"API Book\",\"author\":\"A\",\"isbn\":\"API-1\",\"totalCopies\":1}", 201));
                    int newMember = id(request(base, "POST", "/api/members", "{\"fullName\":\"API Member\",\"studentNumber\":\"456\"}", 201));
                    String body = "{\"bookId\":" + newBook + ",\"memberId\":" + newMember + ",\"dueDate\":\"" + LocalDate.now().plusDays(14) + "\"}";
                    int newLoan = id(request(base, "POST", "/api/loans", body, 201));
                    request(base, "POST", "/api/loans", body, 400);
                    request(base, "POST", "/api/loans/" + newLoan + "/return", "{}", 200);
                    request(base, "POST", "/api/loans/" + newLoan + "/return", "{}", 409);
                    request(base, "GET", "/api/stats", null, 200);
                }
                } else {
                    // Supply the same data used by the HTTP test so persistence assertions stay useful.
                    service.addBook("API Book", "A", "API-1", 1);
                    System.out.println("Core-only mode: HTTP checks skipped.");
                }
            }
            try (Database reopened = new Database(file)) {
                check(service(reopened).getBooks().size() == 2, "Books persist across reopen");
                check(service(reopened).getActiveLoanCount() == 1, "Loans persist across reopen");
            }
            Path csv = folder.resolve("report.csv");
            DefaultTableModel table = new DefaultTableModel(new Object[]{"عنوان", "تعداد"}, 0);
            table.addRow(new Object[]{"کتاب, \"اول\"\nبعدی", 2});
            table.addRow(new Object[]{"=1+1", 1});
            table.addRow(new Object[]{"  @SUM(1)", 0});
            CsvExporter.write(table, csv);
            String output = Files.readString(csv);
            check(output.startsWith("\uFEFF"), "Excel UTF-8 marker");
            check(output.contains("\"کتاب, \"\"اول\"\"\nبعدی\""), "CSV escaping");
            check(output.contains("\"'=1+1\""), "Formula escaped");
            check(output.contains("\"'  @SUM(1)\""), "Whitespace-prefixed formula escaped");
            check(output.contains("\"2\"\r\n"), "Numeric CSV values");
            System.out.println("PASS: " + checks + " integration checks.");
        } finally {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static LibraryService service(Database database) {
        return new LibraryService(new SqliteBookRepository(database), new SqliteMemberRepository(database),
                new SqliteLoanRepository(database), database);
    }

    private static String request(String base, String method, String path, String body, int expected) throws Exception {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == expected, method + " " + path + " expected " + expected + " got " + response.statusCode());
        return response.body();
    }

    private static int id(String json) { return ((Number) ((Map<?, ?>) MiniJson.parse(json)).get("id")).intValue(); }

    private static void rejects(Runnable action, String label) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(label + " was not rejected");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
