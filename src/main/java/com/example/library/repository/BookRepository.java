package com.example.library.repository;

import com.example.library.model.Book;

import java.util.List;

/** Data-access contract for books; implemented by the SQLite layer. */
public interface BookRepository {
    List<Book> findAll();

    Book findById(int id);

    /** Checks whether the ISBN is already used by another book (excluding the given book id). */
    boolean existsByIsbn(String isbn, int excludeBookId);

    /** Inserts the book and returns the generated database id. */
    int insert(String title, String author, String isbn, int totalCopies, int availableCopies);

    boolean update(int id, String title, String author, String isbn, int totalCopies, int availableCopies);

    /** Decreases available copies only while a copy is actually available; returns the number of updated rows. */
    int decrementAvailableCopies(int id);

    /** Increases available copies capped at total copies; returns the number of updated rows. */
    int incrementAvailableCopies(int id);

    boolean deleteById(int id);

    int sumTotalCopies();

    int sumAvailableCopies();
}
