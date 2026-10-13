package com.example.library.model;

public class Book {
    private final int id;
    private String title;
    private String author;
    private String isbn;
    private int totalCopies;
    private int availableCopies;

    public Book(int id, String title, String author, String isbn, int totalCopies) {
        this(id, title, author, isbn, totalCopies, totalCopies);
    }

    public Book(int id, String title, String author, String isbn, int totalCopies, int availableCopies) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getIsbn() { return isbn; }
    public int getTotalCopies() { return totalCopies; }
    public int getAvailableCopies() { return availableCopies; }

    public void setTitle(String title) { this.title = title; }
    public void setAuthor(String author) { this.author = author; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public void setTotalCopies(int totalCopies) { this.totalCopies = totalCopies; }
    public void setAvailableCopies(int availableCopies) { this.availableCopies = availableCopies; }

    @Override
    public String toString() {
        return id + " - " + title + " (موجود: " + availableCopies + ")";
    }
}
