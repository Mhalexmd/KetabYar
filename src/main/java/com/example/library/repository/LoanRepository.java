package com.example.library.repository;

import com.example.library.model.Loan;

import java.time.LocalDate;
import java.util.List;

/** Data-access contract for loans; implemented by the SQLite layer. */
public interface LoanRepository {
    List<Loan> findAll();

    Loan findById(int id);

    boolean existsByBookId(int bookId);

    boolean existsByMemberId(int memberId);

    /** Inserts the loan and returns the generated database id. */
    int insert(int bookId, int memberId, LocalDate loanDate, LocalDate dueDate);

    /** Marks the loan returned; returns false when it has already been returned. */
    boolean markReturned(int loanId, LocalDate returnDate);

    long countActive();

    long countOverdue(LocalDate today);
}
