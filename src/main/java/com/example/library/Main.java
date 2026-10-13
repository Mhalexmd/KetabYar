package com.example.library;

import com.example.library.db.Database;
import com.example.library.repository.SqliteBookRepository;
import com.example.library.repository.SqliteLoanRepository;
import com.example.library.repository.SqliteMemberRepository;
import com.example.library.service.LibraryService;
import com.example.library.ui.MainFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        String databasePath = args.length > 0 ? args[0] : "library.db";
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Use Swing's default look and feel if the system look and feel is unavailable.
            }

            Font persianFont = new Font("Tahoma", Font.PLAIN, 13);
            String[] fontKeys = {"Label.font", "Button.font", "TextField.font", "PasswordField.font",
                    "TextArea.font", "Table.font", "TableHeader.font", "ComboBox.font", "TabbedPane.font",
                    "OptionPane.messageFont", "OptionPane.buttonFont"};
            for (String key : fontKeys) UIManager.put(key, persianFont);

            Database database;
            LibraryService service;
            try {
                database = new Database(Path.of(databasePath));
                service = new LibraryService(
                        new SqliteBookRepository(database),
                        new SqliteMemberRepository(database),
                        new SqliteLoanRepository(database),
                        database);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(null,
                        "راه‌اندازی دیتابیس ناموفق بود:\n" + ex.getMessage(),
                        "خطای اجرا", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
                return;
            }

            MainFrame frame = new MainFrame(service);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    database.close();
                }
            });
            frame.setVisible(true);
        });
    }
}
