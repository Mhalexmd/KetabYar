package com.example.library.service;

import com.example.library.db.Database;
import com.example.library.model.Book;
import com.example.library.model.Loan;
import com.example.library.model.Member;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.MemberRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Holds the library's business rules. All reads and writes go to SQLite through the repository
 * layer; borrow and return run inside a database transaction so the loan record and the book's
 * copy counts can never become inconsistent.
 */
public class LibraryService {
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final Database database;

    public LibraryService(BookRepository bookRepository, MemberRepository memberRepository,
                          LoanRepository loanRepository, Database database) {
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.database = database;
    }

    public List<Book> getBooks() { return bookRepository.findAll(); }
    public List<Member> getMembers() { return memberRepository.findAll(); }
    public List<Loan> getLoans() { return loanRepository.findAll(); }

    public Book findBookById(int id) { return bookRepository.findById(id); }
    public Member findMemberById(int id) { return memberRepository.findById(id); }
    public Loan findLoanById(int id) { return loanRepository.findById(id); }

    public Book addBook(String title, String author, String isbn, int totalCopies) {
        title = required(title, "عنوان کتاب");
        author = required(author, "نام نویسنده");
        isbn = required(isbn, "شابک");
        if (totalCopies < 1) throw new IllegalArgumentException("تعداد نسخه باید حداقل ۱ باشد.");
        ensureUniqueIsbn(isbn, -1);

        int id = bookRepository.insert(title, author, isbn, totalCopies, totalCopies);
        return new Book(id, title, author, isbn, totalCopies);
    }

    public void updateBook(int id, String title, String author, String isbn, int totalCopies) {
        Book book = requireBook(id);
        title = required(title, "عنوان کتاب");
        author = required(author, "نام نویسنده");
        isbn = required(isbn, "شابک");
        if (totalCopies < 1) throw new IllegalArgumentException("تعداد نسخه باید حداقل ۱ باشد.");
        ensureUniqueIsbn(isbn, id);

        int borrowedCopies = book.getTotalCopies() - book.getAvailableCopies();
        if (totalCopies < borrowedCopies) {
            throw new IllegalArgumentException("تعداد نسخه نمی‌تواند از تعداد نسخه‌های امانت‌داده‌شده کمتر باشد: " + borrowedCopies);
        }

        if (!bookRepository.update(id, title, author, isbn, totalCopies, totalCopies - borrowedCopies)) {
            throw new IllegalArgumentException("کتاب موردنظر پیدا نشد.");
        }
    }

    public void deleteBook(int id) {
        requireBook(id);
        if (loanRepository.existsByBookId(id)) {
            throw new IllegalArgumentException("این کتاب سابقه امانت دارد و برای حفظ سوابق قابل حذف نیست.");
        }
        bookRepository.deleteById(id);
    }

    public Member addMember(String fullName, String studentNumber, String phone) {
        fullName = required(fullName, "نام و نام خانوادگی");
        studentNumber = required(studentNumber, "شماره دانشجویی");
        phone = phone == null ? "" : phone.trim();
        ensureUniqueStudentNumber(studentNumber, -1);

        int id = memberRepository.insert(fullName, studentNumber, phone);
        return new Member(id, fullName, studentNumber, phone);
    }

    public void updateMember(int id, String fullName, String studentNumber, String phone) {
        requireMember(id);
        fullName = required(fullName, "نام و نام خانوادگی");
        studentNumber = required(studentNumber, "شماره دانشجویی");
        phone = phone == null ? "" : phone.trim();
        ensureUniqueStudentNumber(studentNumber, id);

        if (!memberRepository.update(id, fullName, studentNumber, phone)) {
            throw new IllegalArgumentException("عضو موردنظر پیدا نشد.");
        }
    }

    public void deleteMember(int id) {
        requireMember(id);
        if (loanRepository.existsByMemberId(id)) {
            throw new IllegalArgumentException("این عضو سابقه امانت دارد و برای حفظ سوابق قابل حذف نیست.");
        }
        memberRepository.deleteById(id);
    }

    public Loan borrowBook(int bookId, int memberId, LocalDate dueDate) {
        Book book = requireBook(bookId);
        requireMember(memberId);
        if (book.getAvailableCopies() < 1) {
            throw new IllegalArgumentException("نسخه‌ای از این کتاب برای امانت موجود نیست.");
        }
        if (dueDate == null || dueDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("تاریخ سررسید نمی‌تواند قبل از امروز باشد.");
        }

        LocalDate today = LocalDate.now();
        return database.inTransaction(() -> {
            // The guarded update keeps the copy count safe; zero rows means no copy was available.
            int updatedRows = bookRepository.decrementAvailableCopies(bookId);
            if (updatedRows == 0) {
                throw new IllegalArgumentException("نسخه‌ای از این کتاب برای امانت موجود نیست.");
            }
            int loanId = loanRepository.insert(bookId, memberId, today, dueDate);
            return new Loan(loanId, bookId, memberId, today, dueDate);
        });
    }

    public void returnBook(int loanId) {
        Loan loan = findLoanById(loanId);
        if (loan == null) throw new IllegalArgumentException("رکورد امانت پیدا نشد.");
        if (loan.isReturned()) throw new IllegalArgumentException("این کتاب قبلاً بازگردانده شده است.");
        if (findBookById(loan.getBookId()) == null) {
            throw new IllegalStateException("کتاب مرتبط با این امانت پیدا نشد.");
        }

        LocalDate today = LocalDate.now();
        database.inTransaction(() -> {
            boolean marked = loanRepository.markReturned(loanId, today);
            if (!marked) {
                throw new IllegalArgumentException("این کتاب قبلاً بازگردانده شده است.");
            }
            bookRepository.incrementAvailableCopies(loan.getBookId());
        });
    }

    public int getTotalCopiesCount() { return bookRepository.sumTotalCopies(); }
    public int getAvailableCopiesCount() { return bookRepository.sumAvailableCopies(); }
    public long getActiveLoanCount() { return loanRepository.countActive(); }
    public long getOverdueLoanCount() { return loanRepository.countOverdue(LocalDate.now()); }

    private Book requireBook(int id) {
        Book book = bookRepository.findById(id);
        if (book == null) throw new IllegalArgumentException("کتاب موردنظر پیدا نشد.");
        return book;
    }

    private Member requireMember(int id) {
        Member member = memberRepository.findById(id);
        if (member == null) throw new IllegalArgumentException("عضو موردنظر پیدا نشد.");
        return member;
    }

    private String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("فیلد «" + fieldName + "» نباید خالی باشد.");
        }
        return value.trim();
    }

    private void ensureUniqueIsbn(String isbn, int currentId) {
        if (bookRepository.existsByIsbn(isbn, currentId)) {
            throw new IllegalArgumentException("این شابک قبلاً برای کتاب دیگری ثبت شده است.");
        }
    }

    private void ensureUniqueStudentNumber(String studentNumber, int currentId) {
        if (memberRepository.existsByStudentNumber(studentNumber, currentId)) {
            throw new IllegalArgumentException("این شماره دانشجویی قبلاً ثبت شده است.");
        }
    }
}
