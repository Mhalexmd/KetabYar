package com.example.library.model;

public class Member {
    private final int id;
    private String fullName;
    private String studentNumber;
    private String phone;

    public Member(int id, String fullName, String studentNumber, String phone) {
        this.id = id;
        this.fullName = fullName;
        this.studentNumber = studentNumber;
        this.phone = phone;
    }

    public int getId() { return id; }
    public String getFullName() { return fullName; }
    public String getStudentNumber() { return studentNumber; }
    public String getPhone() { return phone; }

    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }
    public void setPhone(String phone) { this.phone = phone; }

    @Override
    public String toString() {
        return id + " - " + fullName + " (" + studentNumber + ")";
    }
}
