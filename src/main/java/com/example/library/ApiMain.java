package com.example.library;

import com.example.library.api.LibraryApiServer;
import com.example.library.db.Database;
import com.example.library.repository.SqliteBookRepository;
import com.example.library.repository.SqliteLoanRepository;
import com.example.library.repository.SqliteMemberRepository;
import com.example.library.service.LibraryService;

import java.io.IOException;
import java.nio.file.Path;

/** Starts only the REST API, leaving the existing Swing Main class unchanged. */
public final class ApiMain {
    private ApiMain() { }

    public static void main(String[] args) throws IOException {
        String databasePath = args.length > 0 ? args[0] : "library.db";
        int port = args.length > 1 ? parsePort(args[1]) : 8080;

        Database database = new Database(Path.of(databasePath));
        LibraryService service = new LibraryService(
                new SqliteBookRepository(database),
                new SqliteMemberRepository(database),
                new SqliteLoanRepository(database),
                database
        );

        LibraryApiServer apiServer;
        try {
            apiServer = new LibraryApiServer(service, port);
        } catch (IOException | RuntimeException ex) {
            database.close();
            throw ex;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            apiServer.close();
            database.close();
        }, "library-api-shutdown"));

        apiServer.start();
        System.out.println("Library REST API is running at http://127.0.0.1:" + apiServer.getPort());
        System.out.println("Health check: GET http://127.0.0.1:" + apiServer.getPort() + "/api/health");
        System.out.println("Using SQLite database: " + Path.of(databasePath).toAbsolutePath());
        System.out.println("Press Ctrl+C to stop the API.");
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("شماره پورت باید عددی بین 1 تا 65535 باشد.", ex);
        }
    }
}
