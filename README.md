# Library Management System (Java + PostgreSQL)

A complete, runnable library management system with **both a browser interface
and a console application**. It uses plain JDBC (no framework), real PostgreSQL
transactions, and a schema with constraints, foreign keys and indexes. Both
front ends call the same `LibraryService`, so every business rule applies in
both places.

---

## Features

**Web interface (new)**
- Dashboard with live statistics and quick actions
- Books page: search, add, change copies, delete, one-click issue
- Members page: search, register, activate/deactivate, delete, loan history
- Issue & Return page: issue form, active/overdue/history tabs, return button
- Reports page: collection health, genre and loan-status charts, print-ready tables
- JSON API (`/api/...`) built on `LibraryService` — no third-party web framework

**Books**
- Add / search / list books, change the number of copies, delete
- ISBN uniqueness and case-insensitive search by ISBN, title or author
- Live `available_copies` counter

**Members**
- Add / search / list / activate / deactivate / delete members
- Membership-number uniqueness

**Loans**
- Issue a copy to a member (14-day loan period)
- Return a copy with automatic overdue fine calculation (`₹1.00` per started day)
- Active-loan, overdue and full-history reports
- All issue/return operations are transactional (money and copy counts can never drift)

**Business rules enforced in `LibraryService`**
- Inactive members cannot borrow
- At most 5 concurrent loans per member
- A member cannot borrow the same title twice at the same time
- Copies cannot go below zero or above the total
- Books/members with loan history cannot be deleted
- Members with active loans cannot be deactivated

---

## Project structure

```
.
├── pom.xml                       # Maven build (optional)
├── config.properties.example     # DB connection template
├── sql/
│   ├── schema.sql                # tables, constraints, indexes (re-runnable)
│   └── seed.sql                  # sample books and members
├── lib/
│   └── postgresql-42.7.4.jar     # JDBC driver (auto-downloaded if missing)
├── web/                          # browser front end (served directly, no build step)
│   ├── index.html                # dashboard
│   ├── books.html                # catalogue management
│   ├── members.html              # member management
│   ├── loans.html                # issue / return / loan register
│   ├── reports.html              # summary and reports
│   ├── css/styles.css
│   └── js/{api,pages}.js         # API helpers + page logic
├── scripts/
│   ├── build.sh                  # compile with javac (no Maven required)
│   ├── run.sh                    # launch the app
│   ├── web.sh                    # launch the browser interface on port 8080
│   ├── verify.sh                 # build + automated end-to-end self check
│   └── setup-db.sh               # optional: start a local PostgreSQL cluster
└── src/main/java/com/library/
    ├── Main.java                 # entry point / CLI argument handling
    ├── config/AppConfig.java
    ├── db/DatabaseManager.java
    ├── model/{Book,Member,Loan}.java
    ├── repository/{Book,Member,Loan}Repository.java
    ├── service/{LibraryService,LibraryException}.java
    ├── ui/ConsoleUI.java
    ├── web/{WebServer,Json}.java # built-in HTTP server + JSON API
    └── verify/SelfCheck.java     # 24 automated assertions
```

---

## Prerequisites

- Java JDK 17 or newer (`java -version`, `javac -version`)
- PostgreSQL 12 or newer, running and reachable
- Either Maven **or** nothing else — `scripts/build.sh` only needs `javac`

---

## Quick start

### 1. Set up PostgreSQL

**If you already have PostgreSQL:** create a database and set the connection
details (see *Configuration* below):

```bash
createdb library_db
createdb library_test_db     # only needed for the self-check
```

**If you don't:** the bundled helper initialises a throwaway local cluster
(`./pgdata`, port 5432, user `postgres`, password `postgres`) and creates both
databases:

```bash
./scripts/setup-db.sh
```

### 2. Configure the connection

Either export environment variables (highest priority):

```bash
export DB_URL=jdbc:postgresql://127.0.0.1:5432/library_db
export DB_USER=postgres
export DB_PASSWORD=postgres
```

or copy the template and edit it:

```bash
cp config.properties.example config.properties
```

Defaults already match the bundled `setup-db.sh`.

### 3. Build

```bash
./scripts/build.sh          # javac only
```

or, with Maven:

```bash
mvn clean package
```

### 4. Run

```bash
./scripts/run.sh            # interactive menu
```

On a brand-new database the app creates the tables and loads sample data
automatically. You can also do it explicitly:

```bash
./scripts/run.sh --setup
```

Other options:

```bash
./scripts/run.sh --self-check   # automated verification
./scripts/run.sh --web          # start the browser interface (port 8080)
./scripts/run.sh --web 9000     # ...on a custom port
./scripts/run.sh --help
```

With Maven: `mvn exec:java -Dexec.args="--self-check"`.

---

## Web interface

Start the built-in HTTP server and open the address it prints:

```bash
./scripts/web.sh          # build (if needed) + open http://localhost:8080/
```

or directly:

```bash
./scripts/run.sh --web
```

Options:

| Setting | Default | Purpose |
|---|---|---|
| Port | `8080` | Second argument to `--web`, or `WEB_PORT` env var |
| Bind host | `0.0.0.0` | `WEB_HOST` env var (use `127.0.0.1` to restrict to this machine) |

No web framework, npm install or build step is needed for the front end: the
pages in `web/` are served straight from disk. The server uses a single worker
thread because `DatabaseManager` owns one shared JDBC connection.

### Pages

| Page | What you can do |
|---|---|
| `index.html` | Live dashboard: books, copies, members, active loans, overdue alert, quick actions |
| `books.html` | Search, add, edit copies, delete, jump to issue |
| `members.html` | Search, register, activate/deactivate, delete, view loan history |
| `loans.html` | Issue a book; browse active/overdue/history; return with automatic fine |
| `reports.html` | Collection summary, availability, genre/status charts, printable tables |

### JSON API

All requests use form-encoded data; responses are JSON. Errors return HTTP 400
with `{"error": "..."}`.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/dashboard` | All summary counters and loan rules |
| GET | `/api/books?q=...` | List or search books |
| POST | `/api/books` | Add a book |
| POST | `/api/books/update` | Change total copies |
| POST | `/api/books/delete` | Delete a book (blocked if it has loan history) |
| GET | `/api/members?q=...` | List or search members |
| POST | `/api/members` | Register a member |
| POST | `/api/members/status` | Activate / deactivate |
| POST | `/api/members/delete` | Delete a member |
| GET | `/api/loans?view=active\|overdue\|history` | Loan lists |
| GET | `/api/loans/member?memberId=...` | One member's loan history |
| POST | `/api/loans` | Issue a book |
| POST | `/api/loans/return` | Return a loan and compute the fine |
| POST | `/api/setup` | Reload schema + sample data |

### Suggested browser demonstration

1. Open `http://localhost:8080/` — point out the live counters.
2. Go to **Books** → **Add book** — fill the form and show the new row.
3. Go to **Members** → **Add member** — register a test member.
4. Go to **Issue & Return** → select the new book and member → **Issue book**.
5. Show the active loan in the register, then press **Return** — fine is ₹0.00 when on time.
6. Open **Reports** → use **Print report** if a hard copy is wanted.

---

## How to check that it works

### Check 1 — automated end-to-end self test (recommended, one command)

```bash
./scripts/verify.sh
```

It builds the project, resets `library_test_db`, then exercises adding books and
members, searching, duplicate detection, issuing, returning, overdue fines,
protective rules and reports. Expected output ends with:

```
RESULT: 24 passed, 0 failed
```

It uses a **separate database**, so your `library_db` data is never touched.

### Check 2 — interactive smoke test

```bash
./scripts/run.sh
```

Then try this sequence at the menus:

1. `5` → `1` — collection summary (expect 6 books, 14 copies, 4 members)
2. `1` → `2` → type `java` (expect "Effective Java", "Clean Code", …)
3. `3` — issue book id `1` to member id `1` (note the loan id)
4. `5` → `2` — the book is now listed as ACTIVE and its available copies dropped
5. `4` — enter that loan id (expect "Returned on time - no fine")
6. `5` → `3` — overdue list

### Check 3 — query PostgreSQL directly

```bash
PGPASSWORD=postgres psql -h 127.0.0.1 -U postgres -d library_db \
  -c "SELECT b.title, b.available_copies, b.total_copies FROM books b ORDER BY b.id;"
```

```bash
PGPASSWORD=postgres psql -h 127.0.0.1 -U postgres -d library_db \
  -c "SELECT l.id, b.title, m.full_name, l.due_at, l.returned_at, l.fine_amount
      FROM loans l JOIN books b ON b.id=l.book_id
      JOIN members m ON m.id=l.member_id ORDER BY l.id;"
```

A successful issue/return cycle shows one row with `returned_at` filled in and
the book's `available_copies` restored.

### Check 4 — Maven path

```bash
mvn clean package
DB_URL=jdbc:postgresql://127.0.0.1:5432/library_test_db \
  mvn exec:java -Dexec.args="--self-check"
```

---

## Running in this workspace

This sandbox has the JDK, Maven and PostgreSQL installed but not on `PATH`, and
PostgreSQL is already running on `127.0.0.1:5432`. Use:

```bash
export PATH=/tmp/opencode/tools/jdk-25.0.5+8/bin:/tmp/opencode/tools/apache-maven-3.9.9/bin:$PATH
cd /home/user/library-management-system
./scripts/verify.sh
./scripts/run.sh
```

Direct SQL checks:

```bash
PGPASSWORD=postgres /usr/lib/postgresql/18/bin/psql -h 127.0.0.1 -U postgres -d library_db \
  -c "SELECT COUNT(*) AS books FROM books;"
```

---

## Deploy to Render

The repository includes a one-click **Render Blueprint** (`render.yaml`) and a
`Dockerfile`, so Render builds the Java app, provisions PostgreSQL and wires the
connection string automatically.

### 1. Push the latest code to GitHub

Render deploys from Git, so the `render.yaml` commit must be on GitHub first:

```bash
git push -u origin main
```

### 2. Create the Blueprint

1. Sign in at <https://dashboard.render.com>.
2. Click **New → Blueprint**.
3. Connect the GitHub account and select
   `surjit12334-hue/library-management-system`.
4. Use branch `main` and click **Apply**.

Render creates:

- Web service **`arulibrary`** (Docker, free plan, Singapore)
- PostgreSQL database **`arulibrary-db`**
- Environment variable `DB_URL` from the database connection string

### 3. Verify the deployment

- Health check: `https://arulibrary.onrender.com/api/health`
- Browser UI: `https://arulibrary.onrender.com/`

On first boot the app detects an empty database and loads `sql/schema.sql` and
`sql/seed.sql` automatically. No manual migration step is needed.

### 4. Add a custom domain (optional)

In the web service → **Settings → Custom Domains → Add Custom Domain**:

- Add `arulibrary.in` (Render also adds `www.arulibrary.in`).
- At your DNS provider, point the domain to the CNAME/ALIAS target shown by
  Render. Wait for the certificate to be issued, then use `https://arulibrary.in`.

> Free Render web services sleep after ~15 minutes of inactivity, so the first
> request after a gap may take a few seconds.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Connection refused` | Start PostgreSQL (`./scripts/setup-db.sh`) and check `DB_URL` port. |
| `password authentication failed` | Check `DB_PASSWORD` / `config.properties`. Bundled cluster uses `postgres`. |
| `PostgreSQL JDBC driver not found` | Run `./scripts/build.sh` (it downloads the jar) or `mvn clean package`. |
| `javac: command not found` | Install a JDK (not just a JRE) or set `export JAVAC=/path/to/javac`. |
| `relation "books" does not exist` | Run `./scripts/run.sh --setup`. |
| `cannot delete ... has loan history` | This is intentional: delete history is protected. Use a fresh DB or `--setup`. |

---

## Design notes

- `DatabaseManager` owns one shared JDBC connection (safe for this single-threaded CLI).
- Repositories contain only SQL; `LibraryService` owns all rules and transaction
  boundaries (`setAutoCommit(false)` → commit / rollback).
- `Loan` join queries denormalise book/member names purely for display.
- Fines are stored as `NUMERIC(10,2)` — never floating point.
- All money is displayed in **Indian Rupees (₹)** with `en-IN` digit grouping
  (for example ₹1,00,000.00); formatting is centralised in `util/Money.java`
  for Java output and `Intl.NumberFormat` in `web/js/api.js` for the browser.
- Schema constraints (`CHECK`, `UNIQUE`, `FK`) are the last line of defence even
  if the service layer is bypassed.
