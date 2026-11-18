package com.library.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class Loan {
    private int id;
    private int bookId;
    private int memberId;
    private Timestamp issuedAt;
    private Timestamp dueAt;
    private Timestamp returnedAt;
    private BigDecimal fineAmount;
    private String note;

    // Denormalised fields filled in by join queries, used for display only.
    private String bookTitle;
    private String memberName;
    private String memberNo;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBookId() {
        return bookId;
    }

    public void setBookId(int bookId) {
        this.bookId = bookId;
    }

    public int getMemberId() {
        return memberId;
    }

    public void setMemberId(int memberId) {
        this.memberId = memberId;
    }

    public Timestamp getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Timestamp issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Timestamp getDueAt() {
        return dueAt;
    }

    public void setDueAt(Timestamp dueAt) {
        this.dueAt = dueAt;
    }

    public Timestamp getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(Timestamp returnedAt) {
        this.returnedAt = returnedAt;
    }

    public BigDecimal getFineAmount() {
        return fineAmount;
    }

    public void setFineAmount(BigDecimal fineAmount) {
        this.fineAmount = fineAmount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getMemberNo() {
        return memberNo;
    }

    public void setMemberNo(String memberNo) {
        this.memberNo = memberNo;
    }

    public boolean isActive() {
        return returnedAt == null;
    }

    @Override
    public String toString() {
        return String.format("#%d | %s | %s (%s) | issued %s | due %s | %s | fine %s",
                id,
                bookTitle == null ? "book#" + bookId : bookTitle,
                memberName == null ? "member#" + memberId : memberName,
                memberNo == null ? "-" : memberNo,
                issuedAt, dueAt,
                isActive() ? "ACTIVE" : "returned " + returnedAt,
                fineAmount);
    }
}
