-- ============================================================
-- Sample data. Run after schema.sql.
-- Re-running is safe: ON CONFLICT skips existing ISBN/membership.
-- ============================================================

INSERT INTO books (isbn, title, author, publisher, publication_year, genre, total_copies, available_copies) VALUES
    ('978-0134685991', 'Effective Java',              'Joshua Bloch',        'Addison-Wesley', 2018, 'Programming',     3, 3),
    ('978-0596007126', 'Design Patterns',              'Erich Gamma',         'Addison-Wesley', 1994, 'Software Design', 2, 2),
    ('978-0201633610', 'Clean Code',                   'Robert C. Martin',    'Prentice Hall',  2008, 'Programming',     4, 4),
    ('978-0321125217', 'Domain-Driven Design',         'Eric Evans',          'Addison-Wesley', 2003, 'Software Design', 1, 1),
    ('978-0262033848', 'Introduction to Algorithms',   'Cormen, Leiserson',   'MIT Press',      2009, 'Computer Science',2, 2),
    ('978-0132350884', 'The Pragmatic Programmer',     'David Thomas',        'Addison-Wesley', 2019, 'Programming',     2, 2)
ON CONFLICT (isbn) DO NOTHING;

INSERT INTO members (membership_no, full_name, email, phone, address) VALUES
    ('M-1001', 'Asha Sharma',     'asha.sharma@example.com',   '555-0101', '12 Hill Street'),
    ('M-1002', 'Rahul Verma',     'rahul.verma@example.com',   '555-0102', '48 Lake Road'),
    ('M-1003', 'Priya Nair',      'priya.nair@example.com',    '555-0103', '7 Garden Lane'),
    ('M-1004', 'Daniel O''Neil',  'daniel.oneil@example.com',  '555-0104', '90 Market Square')
ON CONFLICT (membership_no) DO NOTHING;
