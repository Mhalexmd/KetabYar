package com.example.library.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class Loan {
    public static final long FINE_PER_DAY_TOMAN = 10_000L;

    private final int id;
    private final int bookId;
    private final int memberId;
    private final LocalDate loanDate;
    private final LocalDate dueDate;
    private LocalDate returnDate;

    public Loan(int id, int bookId, int memberId, LocalDate loanDate, LocalDate dueDate) {
        this.id = id;
        this.bookId = bookId;
        this.memberId = memberId;
        this.loanDate = loanDate;
        this.dueDate = dueDate;
    }

    public int getId() { return id; }
    public int getBookId() { return bookId; }
    public int getMemberId() { return memberId; }
    public LocalDate getLoanDate() { return loanDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public boolean isReturned() { return returnDate != null; }

    public void markReturned(LocalDate date) {
        if (returnDate != null) {
            throw new IllegalStateException("این امانت قبلاً بازگردانده شده است.");
        }
        this.returnDate = date;
    }

    public long getOverdueDays() {
        LocalDate effectiveDate = returnDate == null ? LocalDate.now() : returnDate;
        if (!effectiveDate.isAfter(dueDate)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(dueDate, effectiveDate);
    }

    public long getFineToman() {
        return getOverdueDays() * FINE_PER_DAY_TOMAN;
    }
}
