package com.example.library.ui;

import com.example.library.model.Book;
import com.example.library.model.Loan;
import com.example.library.model.Member;
import com.example.library.service.LibraryService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class MainFrame extends JFrame {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final LibraryService service;
    private final JLabel statisticsLabel = new JLabel();

    // Books tab
    private final DefaultTableModel bookModel = createTableModel("شناسه", "عنوان کتاب", "نویسنده", "شابک", "کل نسخه‌ها", "نسخه موجود");
    private final JTable bookTable = createTable(bookModel);
    private final JTextField bookTitleField = new JTextField(13);
    private final JTextField bookAuthorField = new JTextField(13);
    private final JTextField bookIsbnField = new JTextField(13);
    private final JTextField bookCopiesField = new JTextField("1", 5);
    private final JTextField bookSearchField = new JTextField(18);
    private int selectedBookId = -1;

    // Members tab
    private final DefaultTableModel memberModel = createTableModel("شناسه", "نام و نام خانوادگی", "شماره دانشجویی", "تلفن");
    private final JTable memberTable = createTable(memberModel);
    private final JTextField memberNameField = new JTextField(15);
    private final JTextField memberStudentNumberField = new JTextField(12);
    private final JTextField memberPhoneField = new JTextField(12);
    private final JTextField memberSearchField = new JTextField(18);
    private int selectedMemberId = -1;

    // Loans tab
    private final DefaultTableModel loanModel = createTableModel(
            "شناسه", "کتاب", "عضو", "تاریخ امانت", "سررسید", "تاریخ بازگشت", "وضعیت", "جریمه (تومان)");
    private final JTable loanTable = createTable(loanModel);
    private final JComboBox<Book> bookComboBox = new JComboBox<>();
    private final JComboBox<Member> memberComboBox = new JComboBox<>();
    private int selectedLoanId = -1;

    public MainFrame(LibraryService service) {
        super("سیستم مدیریت کتابخانه");
        this.service = service;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1080, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        add(buildHeader(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        tabs.addTab("کتاب‌ها", buildBooksPanel());
        tabs.addTab("اعضای کتابخانه", buildMembersPanel());
        tabs.addTab("امانت و بازگشت", buildLoansPanel());
        add(tabs, BorderLayout.CENTER);

        refreshAll();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 6, 16));
        header.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        JLabel title = new JLabel("سیستم مدیریت کتابخانه");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        statisticsLabel.setForeground(new Color(75, 85, 99));

        header.add(title, BorderLayout.NORTH);
        header.add(statisticsLabel, BorderLayout.SOUTH);
        return header;
    }

    private JPanel buildBooksPanel() {
        JPanel panel = basePanel();
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        JPanel form = new JPanel(new GridLayout(2, 6, 7, 7));
        form.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        form.add(new JLabel("عنوان کتاب:"));
        form.add(bookTitleField);
        form.add(new JLabel("نویسنده:"));
        form.add(bookAuthorField);
        form.add(new JLabel("شابک:"));
        form.add(bookIsbnField);
        form.add(new JLabel("تعداد نسخه:"));
        form.add(bookCopiesField);
        form.add(new JLabel(""));
        form.add(new JLabel(""));
        form.add(new JLabel(""));
        form.add(new JLabel(""));

        JButton saveButton = new JButton("ثبت / ذخیره تغییرات");
        JButton deleteButton = new JButton("حذف کتاب انتخاب‌شده");
        JButton clearButton = new JButton("پاک کردن فرم");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        actions.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        actions.add(saveButton);
        actions.add(deleteButton);
        actions.add(clearButton);

        JPanel search = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        search.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        search.add(new JLabel("جستجو در کتاب‌ها:"));
        search.add(bookSearchField);
        search.add(new JLabel("(عنوان، نویسنده یا شابک)"));

        top.add(form);
        top.add(actions);
        top.add(search);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(bookTable), BorderLayout.CENTER);

        saveButton.addActionListener(e -> saveBook());
        deleteButton.addActionListener(e -> deleteBook());
        clearButton.addActionListener(e -> clearBookForm());
        bookTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && bookTable.getSelectedRow() >= 0) {
                int row = bookTable.getSelectedRow();
                selectedBookId = (int) bookModel.getValueAt(row, 0);
                Book book = service.findBookById(selectedBookId);
                if (book != null) {
                    bookTitleField.setText(book.getTitle());
                    bookAuthorField.setText(book.getAuthor());
                    bookIsbnField.setText(book.getIsbn());
                    bookCopiesField.setText(String.valueOf(book.getTotalCopies()));
                }
            }
        });
        bookSearchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshBooksTable(); }
            public void removeUpdate(DocumentEvent e) { refreshBooksTable(); }
            public void changedUpdate(DocumentEvent e) { refreshBooksTable(); }
        });
        return panel;
    }

    private JPanel buildMembersPanel() {
        JPanel panel = basePanel();
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        JPanel form = new JPanel(new GridLayout(2, 4, 7, 7));
        form.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        form.add(new JLabel("نام و نام خانوادگی:"));
        form.add(memberNameField);
        form.add(new JLabel("شماره دانشجویی:"));
        form.add(memberStudentNumberField);
        form.add(new JLabel("شماره تلفن:"));
        form.add(memberPhoneField);
        form.add(new JLabel(""));
        form.add(new JLabel(""));

        JButton saveButton = new JButton("ثبت / ذخیره تغییرات");
        JButton deleteButton = new JButton("حذف عضو انتخاب‌شده");
        JButton clearButton = new JButton("پاک کردن فرم");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        actions.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        actions.add(saveButton);
        actions.add(deleteButton);
        actions.add(clearButton);

        JPanel search = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        search.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        search.add(new JLabel("جستجو در اعضا:"));
        search.add(memberSearchField);
        search.add(new JLabel("(نام یا شماره دانشجویی)"));

        top.add(form);
        top.add(actions);
        top.add(search);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(memberTable), BorderLayout.CENTER);

        saveButton.addActionListener(e -> saveMember());
        deleteButton.addActionListener(e -> deleteMember());
        clearButton.addActionListener(e -> clearMemberForm());
        memberTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && memberTable.getSelectedRow() >= 0) {
                int row = memberTable.getSelectedRow();
                selectedMemberId = (int) memberModel.getValueAt(row, 0);
                Member member = service.findMemberById(selectedMemberId);
                if (member != null) {
                    memberNameField.setText(member.getFullName());
                    memberStudentNumberField.setText(member.getStudentNumber());
                    memberPhoneField.setText(member.getPhone());
                }
            }
        });
        memberSearchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshMembersTable(); }
            public void removeUpdate(DocumentEvent e) { refreshMembersTable(); }
            public void changedUpdate(DocumentEvent e) { refreshMembersTable(); }
        });
        return panel;
    }

    private JPanel buildLoansPanel() {
        JPanel panel = basePanel();
        JPanel top = new JPanel(new GridLayout(2, 1, 5, 5));
        top.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        JPanel form = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 7));
        form.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        bookComboBox.setPreferredSize(new Dimension(300, 30));
        memberComboBox.setPreferredSize(new Dimension(270, 30));
        form.add(new JLabel("کتاب:"));
        form.add(bookComboBox);
        form.add(new JLabel("عضو:"));
        form.add(memberComboBox);
        JButton borrowButton = new JButton("ثبت امانت کتاب");
        form.add(borrowButton);

        JLabel note = new JLabel("مدت استاندارد امانت ۱۴ روز است؛ جریمه دیرکرد برای هر روز ۱۰٬۰۰۰ تومان محاسبه می‌شود.");
        note.setForeground(new Color(75, 85, 99));
        note.setBorder(BorderFactory.createEmptyBorder(0, 8, 5, 8));
        top.add(form);
        top.add(note);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(loanTable), BorderLayout.CENTER);

        JButton returnButton = new JButton("ثبت بازگشت امانت انتخاب‌شده");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        bottom.add(returnButton);
        panel.add(bottom, BorderLayout.SOUTH);

        borrowButton.addActionListener(e -> borrowBook());
        returnButton.addActionListener(e -> returnBook());
        loanTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && loanTable.getSelectedRow() >= 0) {
                selectedLoanId = (int) loanModel.getValueAt(loanTable.getSelectedRow(), 0);
            }
        });
        return panel;
    }

    private JPanel basePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        return panel;
    }

    private static DefaultTableModel createTableModel(String... headers) {
        return new DefaultTableModel(headers, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private static JTable createTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(27);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    private void saveBook() {
        try {
            String title = bookTitleField.getText();
            String author = bookAuthorField.getText();
            String isbn = bookIsbnField.getText();
            int copies = Integer.parseInt(bookCopiesField.getText().trim());
            if (selectedBookId == -1) {
                service.addBook(title, author, isbn, copies);
                showMessage("کتاب با موفقیت ثبت شد.");
            } else {
                service.updateBook(selectedBookId, title, author, isbn, copies);
                showMessage("اطلاعات کتاب به‌روزرسانی شد.");
            }
            clearBookForm();
            refreshAll();
        } catch (NumberFormatException ex) {
            showError("تعداد نسخه باید یک عدد صحیح باشد.");
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void deleteBook() {
        if (selectedBookId == -1) { showError("ابتدا یک کتاب را از جدول انتخاب کنید."); return; }
        if (confirm("از حذف این کتاب مطمئن هستید؟")) {
            try {
                service.deleteBook(selectedBookId);
                clearBookForm();
                refreshAll();
                showMessage("کتاب حذف شد.");
            } catch (RuntimeException ex) { showError(ex.getMessage()); }
        }
    }

    private void clearBookForm() {
        selectedBookId = -1;
        bookTable.clearSelection();
        bookTitleField.setText("");
        bookAuthorField.setText("");
        bookIsbnField.setText("");
        bookCopiesField.setText("1");
    }

    private void saveMember() {
        try {
            if (selectedMemberId == -1) {
                service.addMember(memberNameField.getText(), memberStudentNumberField.getText(), memberPhoneField.getText());
                showMessage("عضو با موفقیت ثبت شد.");
            } else {
                service.updateMember(selectedMemberId, memberNameField.getText(), memberStudentNumberField.getText(), memberPhoneField.getText());
                showMessage("اطلاعات عضو به‌روزرسانی شد.");
            }
            clearMemberForm();
            refreshAll();
        } catch (RuntimeException ex) { showError(ex.getMessage()); }
    }

    private void deleteMember() {
        if (selectedMemberId == -1) { showError("ابتدا یک عضو را از جدول انتخاب کنید."); return; }
        if (confirm("از حذف این عضو مطمئن هستید؟")) {
            try {
                service.deleteMember(selectedMemberId);
                clearMemberForm();
                refreshAll();
                showMessage("عضو حذف شد.");
            } catch (RuntimeException ex) { showError(ex.getMessage()); }
        }
    }

    private void clearMemberForm() {
        selectedMemberId = -1;
        memberTable.clearSelection();
        memberNameField.setText("");
        memberStudentNumberField.setText("");
        memberPhoneField.setText("");
    }

    private void borrowBook() {
        Book book = (Book) bookComboBox.getSelectedItem();
        Member member = (Member) memberComboBox.getSelectedItem();
        if (book == null) { showError("برای امانت، ابتدا یک کتاب موجود ثبت کنید."); return; }
        if (member == null) { showError("ابتدا یک عضو در کتابخانه ثبت کنید."); return; }
        try {
            service.borrowBook(book.getId(), member.getId(), LocalDate.now().plusDays(14));
            refreshAll();
            showMessage("امانت کتاب ثبت شد. موعد بازگشت: " + formatDate(LocalDate.now().plusDays(14)));
        } catch (RuntimeException ex) { showError(ex.getMessage()); }
    }

    private void returnBook() {
        if (selectedLoanId == -1) { showError("ابتدا یک رکورد امانت را از جدول انتخاب کنید."); return; }
        try {
            service.returnBook(selectedLoanId);
            selectedLoanId = -1;
            refreshAll();
            showMessage("بازگشت کتاب ثبت شد.");
        } catch (RuntimeException ex) { showError(ex.getMessage()); }
    }

    private void refreshAll() {
        refreshBooksTable();
        refreshMembersTable();
        refreshLoansTable();
        refreshCombos();
        statisticsLabel.setText("تعداد عناوین: " + service.getBooks().size()
                + "     |     تعداد کل نسخه‌ها: " + service.getTotalCopiesCount()
                + "     |     نسخه‌های موجود: " + service.getAvailableCopiesCount()
                + "     |     اعضا: " + service.getMembers().size()
                + "     |     امانت فعال: " + service.getActiveLoanCount()
                + "     |     دیرکرد: " + service.getOverdueLoanCount());
    }

    private void refreshBooksTable() {
        String query = bookSearchField.getText().trim().toLowerCase(Locale.ROOT);
        bookModel.setRowCount(0);
        for (Book book : service.getBooks()) {
            String searchable = (book.getTitle() + " " + book.getAuthor() + " " + book.getIsbn()).toLowerCase(Locale.ROOT);
            if (searchable.contains(query)) {
                bookModel.addRow(new Object[]{book.getId(), book.getTitle(), book.getAuthor(), book.getIsbn(),
                        book.getTotalCopies(), book.getAvailableCopies()});
            }
        }
    }

    private void refreshMembersTable() {
        String query = memberSearchField.getText().trim().toLowerCase(Locale.ROOT);
        memberModel.setRowCount(0);
        for (Member member : service.getMembers()) {
            String searchable = (member.getFullName() + " " + member.getStudentNumber() + " " + member.getPhone()).toLowerCase(Locale.ROOT);
            if (searchable.contains(query)) {
                memberModel.addRow(new Object[]{member.getId(), member.getFullName(), member.getStudentNumber(), member.getPhone()});
            }
        }
    }

    private void refreshLoansTable() {
        loanModel.setRowCount(0);
        List<Loan> allLoans = service.getLoans();
        for (int i = allLoans.size() - 1; i >= 0; i--) {
            Loan loan = allLoans.get(i);
            Book book = service.findBookById(loan.getBookId());
            Member member = service.findMemberById(loan.getMemberId());
            String bookName = book == null ? "کتاب حذف‌شده" : book.getTitle();
            String memberName = member == null ? "عضو حذف‌شده" : member.getFullName();
            String returnDate = loan.getReturnDate() == null ? "—" : formatDate(loan.getReturnDate());
            String status = loan.isReturned() ? "بازگردانده شده"
                    : (loan.getOverdueDays() > 0 ? "دارای دیرکرد" : "امانت فعال");
            String fine = loan.getFineToman() == 0 ? "۰" : String.format(Locale.US, "%,d", loan.getFineToman());
            loanModel.addRow(new Object[]{loan.getId(), bookName, memberName, formatDate(loan.getLoanDate()),
                    formatDate(loan.getDueDate()), returnDate, status, fine});
        }
    }

    private void refreshCombos() {
        Book previousBook = (Book) bookComboBox.getSelectedItem();
        Member previousMember = (Member) memberComboBox.getSelectedItem();
        List<Book> availableBooks = service.getBooks().stream().filter(b -> b.getAvailableCopies() > 0).toList();
        List<Member> members = service.getMembers();
        bookComboBox.setModel(new DefaultComboBoxModel<>(availableBooks.toArray(new Book[0])));
        memberComboBox.setModel(new DefaultComboBoxModel<>(members.toArray(new Member[0])));
        if (previousBook != null) {
            for (int i = 0; i < bookComboBox.getItemCount(); i++) {
                if (bookComboBox.getItemAt(i).getId() == previousBook.getId()) { bookComboBox.setSelectedIndex(i); break; }
            }
        }
        if (previousMember != null) {
            for (int i = 0; i < memberComboBox.getItemCount(); i++) {
                if (memberComboBox.getItemAt(i).getId() == previousMember.getId()) { memberComboBox.setSelectedIndex(i); break; }
            }
        }
    }

    private String formatDate(LocalDate date) { return DATE_FORMAT.format(date); }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "پیام", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message == null || message.isBlank() ? "عملیات انجام نشد." : message,
                "خطا", JOptionPane.ERROR_MESSAGE);
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, "تأیید عملیات",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
