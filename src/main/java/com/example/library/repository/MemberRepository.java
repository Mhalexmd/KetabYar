package com.example.library.repository;

import com.example.library.model.Member;

import java.util.List;

/** Data-access contract for members; implemented by the SQLite layer. */
public interface MemberRepository {
    List<Member> findAll();

    Member findById(int id);

    /** Checks whether the student number is already used by another member (excluding the given member id). */
    boolean existsByStudentNumber(String studentNumber, int excludeMemberId);

    /** Inserts the member and returns the generated database id. */
    int insert(String fullName, String studentNumber, String phone);

    boolean update(int id, String fullName, String studentNumber, String phone);

    boolean deleteById(int id);
}
