-- ============================================================
-- AruLibrary - PostgreSQL schema
-- Safe to re-run: drops and recreates the three tables.
-- ============================================================

BEGIN;

DROP TABLE IF EXISTS loans;
DROP TABLE IF EXISTS books;
DROP TABLE IF EXISTS members;

-- ------------------------------------------------------------
-- books
-- ------------------------------------------------------------
CREATE TABLE books (
    id                SERIAL PRIMARY KEY,
    isbn              VARCHAR(32)  NOT NULL UNIQUE,
    title             VARCHAR(255) NOT NULL,
    author            VARCHAR(255) NOT NULL,
    publisher         VARCHAR(255),
    publication_year  INTEGER,
    genre             VARCHAR(100),
    total_copies      INTEGER NOT NULL DEFAULT 1,
    available_copies  INTEGER NOT NULL DEFAULT 1,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_books_copies_non_negative CHECK (total_copies >= 0 AND available_copies >= 0),
    CONSTRAINT chk_books_available_le_total   CHECK (available_copies <= total_copies)
);

-- ------------------------------------------------------------
-- members
-- ------------------------------------------------------------
CREATE TABLE members (
    id            SERIAL PRIMARY KEY,
    membership_no VARCHAR(32)  NOT NULL UNIQUE,
    full_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) UNIQUE,
    phone         VARCHAR(50),
    address       TEXT,
    joined_at     DATE NOT NULL DEFAULT CURRENT_DATE,
    active        BOOLEAN NOT NULL DEFAULT TRUE
);

-- ------------------------------------------------------------
-- loans (one row per issue/return cycle)
-- ------------------------------------------------------------
CREATE TABLE loans (
    id           SERIAL PRIMARY KEY,
    book_id      INTEGER NOT NULL REFERENCES books(id)   ON DELETE RESTRICT,
    member_id    INTEGER NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    issued_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_at       TIMESTAMP NOT NULL,
    returned_at  TIMESTAMP,
    fine_amount  NUMERIC(10,2) NOT NULL DEFAULT 0,
    note         TEXT,
    CONSTRAINT chk_loans_due_after_issue    CHECK (due_at > issued_at),
    CONSTRAINT chk_loans_return_after_issue CHECK (returned_at IS NULL OR returned_at >= issued_at),
    CONSTRAINT chk_loans_fine_non_negative  CHECK (fine_amount >= 0)
);

-- ------------------------------------------------------------
-- indexes for the queries the application runs most often
-- ------------------------------------------------------------
CREATE INDEX idx_books_title    ON books (title);
CREATE INDEX idx_books_author   ON books (author);
CREATE INDEX idx_books_isbn     ON books (isbn);
CREATE INDEX idx_loans_book     ON loans (book_id);
CREATE INDEX idx_loans_member   ON loans (member_id);
CREATE INDEX idx_loans_active   ON loans (book_id) WHERE returned_at IS NULL;
CREATE INDEX idx_loans_due      ON loans (due_at)  WHERE returned_at IS NULL;

COMMIT;
