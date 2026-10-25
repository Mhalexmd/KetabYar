package com.example.library.api;

import com.example.library.AppInfo;
import com.example.library.model.Book;
import com.example.library.model.Loan;
import com.example.library.model.Member;
import com.example.library.service.LibraryService;
import com.example.library.service.LoanFilter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** REST API for the existing LibraryService. Binds to localhost by default. */
public final class LibraryApiServer implements AutoCloseable {
    private final LibraryService service;
    private final HttpServer server;
    private final ExecutorService executor;

    public LibraryApiServer(LibraryService service, int port) throws IOException {
        this.service = service;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        // Database currently owns one JDBC Connection, so serialize HTTP requests from this server.
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "library-api-worker");
            thread.setDaemon(false);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod().toUpperCase();
            String path = normalizePath(exchange.getRequestURI().getPath());

            if ("GET".equals(method) && "/".equals(path)) {
                sendJson(exchange, 200, mapOf("service", AppInfo.NAME, "owner", AppInfo.OWNER,
                        "status", "running", "version", AppInfo.VERSION));
                return;
            }
            if ("GET".equals(method) && "/api/health".equals(path)) {
                sendJson(exchange, 200, mapOf("status", "ok"));
                return;
            }

            String[] segments = path.split("/");
            if (segments.length >= 3 && "api".equals(segments[1])) {
                String resource = segments[2];
                switch (resource) {
                    case "books" -> handleBooks(exchange, method, segments);
                    case "members" -> handleMembers(exchange, method, segments);
                    case "loans" -> handleLoans(exchange, method, segments);
                    case "stats" -> {
                        if ("GET".equals(method) && segments.length == 3) sendJson(exchange, 200, statistics());
                        else methodNotAllowed(exchange);
                    }
                    default -> sendError(exchange, 404, "مسیر وب‌سرویس پیدا نشد.");
                }
                return;
            }
            sendError(exchange, 404, "مسیر وب‌سرویس پیدا نشد.");
        } catch (IllegalArgumentException ex) {
            // Validation errors and malformed request bodies are client errors.
            // Route handlers translate expected validation and business-rule errors; this is a safety net.
            try {
                if (exchange.getResponseCode() == -1) sendError(exchange, 400, safeMessage(ex));
            } catch (IOException ignored) {
                // The connection may already be closed.
            }
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            try {
                if (exchange.getResponseCode() == -1) sendError(exchange, 500, "خطای داخلی سرور رخ داد.");
            } catch (IOException ignored) {
                // The connection may already be closed.
            }
        } finally {
            exchange.close();
        }
    }

    private void handleBooks(HttpExchange exchange, String method, String[] segments) throws IOException {
        try {
            if (segments.length == 3 && "GET".equals(method)) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Book book : service.searchBooks(queryParameter(exchange, "q", ""))) result.add(bookJson(book));
                sendJson(exchange, 200, result);
                return;
            }
            if (segments.length == 3 && "POST".equals(method)) {
                Map<String, Object> body = readObject(exchange);
                Book book = service.addBook(requiredString(body, "title"), requiredString(body, "author"),
                        requiredString(body, "isbn"), requiredInt(body, "totalCopies"));
                sendJson(exchange, 201, bookJson(book));
                return;
            }
            if (segments.length == 4) {
                int id = parseId(segments[3]);
                if ("GET".equals(method)) {
                    Book book = service.findBookById(id);
                    if (book == null) { sendError(exchange, 404, "کتاب موردنظر پیدا نشد."); return; }
                    sendJson(exchange, 200, bookJson(book));
                    return;
                }
                if ("PUT".equals(method)) {
                    Map<String, Object> body = readObject(exchange);
                    service.updateBook(id, requiredString(body, "title"), requiredString(body, "author"),
                            requiredString(body, "isbn"), requiredInt(body, "totalCopies"));
                    sendJson(exchange, 200, bookJson(service.findBookById(id)));
                    return;
                }
                if ("DELETE".equals(method)) {
                    service.deleteBook(id);
                    sendNoContent(exchange);
                    return;
                }
            }
            methodNotAllowed(exchange);
        } catch (RuntimeException ex) {
            handleRouteError(exchange, ex);
        }
    }

    private void handleMembers(HttpExchange exchange, String method, String[] segments) throws IOException {
        try {
            if (segments.length == 3 && "GET".equals(method)) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Member member : service.searchMembers(queryParameter(exchange, "q", ""))) result.add(memberJson(member));
                sendJson(exchange, 200, result);
                return;
            }
            if (segments.length == 3 && "POST".equals(method)) {
                Map<String, Object> body = readObject(exchange);
                Member member = service.addMember(requiredString(body, "fullName"),
                        requiredString(body, "studentNumber"), optionalString(body, "phone", ""));
                sendJson(exchange, 201, memberJson(member));
                return;
            }
            if (segments.length == 4) {
                int id = parseId(segments[3]);
                if ("GET".equals(method)) {
                    Member member = service.findMemberById(id);
                    if (member == null) { sendError(exchange, 404, "عضو موردنظر پیدا نشد."); return; }
                    sendJson(exchange, 200, memberJson(member));
                    return;
                }
                if ("PUT".equals(method)) {
                    Map<String, Object> body = readObject(exchange);
                    service.updateMember(id, requiredString(body, "fullName"),
                            requiredString(body, "studentNumber"), optionalString(body, "phone", ""));
                    sendJson(exchange, 200, memberJson(service.findMemberById(id)));
                    return;
                }
                if ("DELETE".equals(method)) {
                    service.deleteMember(id);
                    sendNoContent(exchange);
                    return;
                }
            }
            methodNotAllowed(exchange);
        } catch (RuntimeException ex) {
            handleRouteError(exchange, ex);
        }
    }

    private void handleLoans(HttpExchange exchange, String method, String[] segments) throws IOException {
        try {
            if (segments.length == 3 && "GET".equals(method)) {
                List<Map<String, Object>> result = new ArrayList<>();
                LoanFilter filter;
                try {
                    filter = LoanFilter.valueOf(queryParameter(exchange, "status", "all").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("وضعیت باید یکی از all، active، overdue یا returned باشد.");
                }
                for (Loan loan : service.getLoans(filter)) result.add(loanJson(loan));
                sendJson(exchange, 200, result);
                return;
            }
            if (segments.length == 3 && "POST".equals(method)) {
                Map<String, Object> body = readObject(exchange);
                LocalDate dueDate;
                try {
                    dueDate = LocalDate.parse(requiredString(body, "dueDate"));
                } catch (DateTimeParseException ex) {
                    sendError(exchange, 400, "dueDate باید با قالب yyyy-MM-dd ارسال شود.");
                    return;
                }
                Loan loan = service.borrowBook(requiredInt(body, "bookId"), requiredInt(body, "memberId"), dueDate);
                sendJson(exchange, 201, loanJson(loan));
                return;
            }
            if (segments.length == 4) {
                int id = parseId(segments[3]);
                if ("GET".equals(method)) {
                    Loan loan = service.findLoanById(id);
                    if (loan == null) { sendError(exchange, 404, "رکورد امانت پیدا نشد."); return; }
                    sendJson(exchange, 200, loanJson(loan));
                    return;
                }
            }
            if (segments.length == 5 && "return".equals(segments[4]) && "POST".equals(method)) {
                int id = parseId(segments[3]);
                service.returnBook(id);
                sendJson(exchange, 200, loanJson(service.findLoanById(id)));
                return;
            }
            methodNotAllowed(exchange);
        } catch (RuntimeException ex) {
            handleRouteError(exchange, ex);
        }
    }

    private Map<String, Object> statistics() {
        return mapOf(
                "bookTitles", service.getBooks().size(),
                "totalCopies", service.getTotalCopiesCount(),
                "availableCopies", service.getAvailableCopiesCount(),
                "members", service.getMembers().size(),
                "activeLoans", service.getActiveLoanCount(),
                "overdueLoans", service.getOverdueLoanCount()
        );
    }

    private static Map<String, Object> bookJson(Book book) {
        return mapOf("id", book.getId(), "title", book.getTitle(), "author", book.getAuthor(),
                "isbn", book.getIsbn(), "totalCopies", book.getTotalCopies(),
                "availableCopies", book.getAvailableCopies());
    }

    private static Map<String, Object> memberJson(Member member) {
        return mapOf("id", member.getId(), "fullName", member.getFullName(),
                "studentNumber", member.getStudentNumber(), "phone", member.getPhone());
    }

    private static Map<String, Object> loanJson(Loan loan) {
        return mapOf("id", loan.getId(), "bookId", loan.getBookId(), "memberId", loan.getMemberId(),
                "loanDate", loan.getLoanDate().toString(), "dueDate", loan.getDueDate().toString(),
                "returnDate", loan.getReturnDate() == null ? null : loan.getReturnDate().toString(),
                "returned", loan.isReturned(), "overdueDays", loan.getOverdueDays(),
                "fineToman", loan.getFineToman());
    }

    private static Map<String, Object> readObject(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        if (bytes.length == 0) throw new IllegalArgumentException("بدنه درخواست نباید خالی باشد.");
        String json = new String(bytes, StandardCharsets.UTF_8);
        Object parsed = MiniJson.parse(json);
        if (!(parsed instanceof Map<?, ?> rawMap)) throw new IllegalArgumentException("بدنه درخواست باید یک شیء JSON باشد.");
        Map<String, Object> body = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) body.put(key, entry.getValue());
        }
        return body;
    }

    private static String queryParameter(HttpExchange exchange, String name, String fallback) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return fallback;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals(name)) {
                return parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            }
        }
        return fallback;
    }

    private static String requiredString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof String string) || string.trim().isEmpty()) {
            throw new IllegalArgumentException("فیلد «" + key + "» باید یک رشته غیرخالی باشد.");
        }
        return string.trim();
    }

    private static String optionalString(Map<String, Object> body, String key, String defaultValue) {
        Object value = body.get(key);
        if (value == null) return defaultValue;
        if (!(value instanceof String string)) throw new IllegalArgumentException("فیلد «" + key + "» باید رشته باشد.");
        return string.trim();
    }

    private static int requiredInt(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("فیلد «" + key + "» باید عدد صحیح باشد.");
        long longValue;
        try { longValue = new java.math.BigDecimal(number.toString()).longValueExact(); }
        catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("فیلد «" + key + "» باید عدد صحیح معتبر باشد.");
        }
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("مقدار فیلد «" + key + "» خارج از محدوده است.");
        }
        return (int) longValue;
    }

    private static int parseId(String text) {
        try {
            int id = Integer.parseInt(text);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("شناسه باید یک عدد صحیح مثبت باشد.");
        }
    }

    private void handleRouteError(HttpExchange exchange, RuntimeException ex) throws IOException {
        String message = safeMessage(ex);
        String lower = message.toLowerCase();
        if (message.contains("پیدا نشد")) {
            sendError(exchange, 404, message);
        } else if (message.contains("قبلاً") || message.contains("سابقه امانت دارد") || lower.contains("unique constraint")) {
            sendError(exchange, 409, message);
        } else if (ex instanceof IllegalArgumentException || ex instanceof DateTimeParseException) {
            sendError(exchange, 400, message);
        } else {
            ex.printStackTrace(System.err);
            sendError(exchange, 500, "خطای داخلی سرور رخ داد.");
        }
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "درخواست معتبر نیست." : message;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/";
        if (path.length() > 1 && path.endsWith("/")) return path.substring(0, path.length() - 1);
        return path;
    }

    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return map;
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = MiniJson.stringify(body).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, mapOf("error", message, "status", status));
    }

    private static void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(204, -1);
    }

    private static void methodNotAllowed(HttpExchange exchange) throws IOException {
        sendError(exchange, 405, "متد HTTP برای این مسیر پشتیبانی نمی‌شود.");
    }

    @Override
    public void close() {
        server.stop(1);
        executor.shutdown();
    }
}
