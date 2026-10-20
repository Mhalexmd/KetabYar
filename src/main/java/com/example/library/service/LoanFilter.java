package com.example.library.service;

import com.example.library.model.Loan;

public enum LoanFilter {
    ALL("همهٔ امانت‌ها"), ACTIVE("امانت‌های فعال"), OVERDUE("دارای دیرکرد"), RETURNED("بازگردانده‌شده");

    private final String label;
    LoanFilter(String label) { this.label = label; }

    public boolean matches(Loan loan) {
        return switch (this) {
            case ALL -> true;
            case ACTIVE -> !loan.isReturned();
            case OVERDUE -> !loan.isReturned() && loan.getOverdueDays() > 0;
            case RETURNED -> loan.isReturned();
        };
    }

    @Override public String toString() { return label; }
}
