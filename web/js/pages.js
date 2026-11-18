/* Page-specific behaviour. The active page is selected by <body data-page="...">. */

document.addEventListener('DOMContentLoaded', () => {
    const page = document.body.dataset.page;
    setupCommon(page);
    const handlers = {
        dashboard: initDashboard,
        books: initBooks,
        members: initMembers,
        loans: initLoans,
        reports: initReports
    };
    if (handlers[page]) {
        Promise.resolve(handlers[page]()).catch(e => toast(e.message, 'error'));
    }
});

/* ---------------- small local helpers ---------------- */
function emptyRow(cols, title, text, icon = 'search') {
    return `<tr><td colspan="${cols}">${emptyState(icon, title, text)}</td></tr>`;
}

function refreshIcons(node) {
    if (typeof hydrateIcons === 'function') hydrateIcons(node || document);
}

function setText(id, value) {
    const node = el(id);
    if (node) node.textContent = value;
}

function setWidth(id, percent) {
    const node = el(id);
    if (node) node.style.width = Math.max(0, Math.min(100, percent)) + '%';
}

function statusBadge(member) {
    return member.active ? badge('Active', 'success') : badge('Inactive', 'neutral');
}

function fineText(value) {
    return Number(value) > 0 ? money(value) + ' fine' : '';
}

/* ==================================================================
   Dashboard
   ================================================================== */
async function initDashboard() {
    const d = await apiGet('/dashboard');
    const values = {
        statBooks: d.books,
        statCopies: d.availableCopies,
        statMembers: d.members,
        statActive: d.activeLoans,
        statLoans: d.totalLoans,
        statFines: money(d.fines),
        statOverdue: d.overdue,
        heroTitles: d.books,
        heroAvailable: d.availableCopies,
        heroOverdue: d.overdue
    };
    Object.entries(values).forEach(([id, value]) => setText(id, value));

    setText('ruleLoanDays', d.loanPeriodDays);
    setText('ruleMaxLoans', d.maxLoansPerMember);
    setText('ruleFine', money(d.finePerDay) + ' per started day');
    setText('statBooksFoot', d.books + ' distinct titles in the catalogue');
    setText('statCopiesFoot', 'of ' + (d.books ? 'active stock' : 'stock') + ' available to lend');
    setText('statMembersFoot', d.members + ' registered card holders');

    const stockTotal = d.availableCopies + d.activeLoans;
    setWidth('meterBooks', Math.min(100, d.books * 10));
    setWidth('meterCopies', stockTotal ? d.availableCopies / stockTotal * 100 : 0);
    setWidth('meterMembers', Math.min(100, d.members * 12));
    setWidth('meterActive', Math.min(100, d.activeLoans * 18));

    const alert = el('overdueAlert');
    if (alert) {
        if (d.overdue > 0) {
            alert.className = 'callout danger';
            alert.innerHTML = `<span data-icon="alert" data-size="18"></span><span><strong>${d.overdue} overdue loan${d.overdue === 1 ? '' : 's'}</strong>
                <span>One or more borrowed books are past their due date. Review the loan register now.</span></span>`;
            setText('healthBadge', 'Needs attention');
            el('healthBadge').className = 'badge danger';
        } else {
            alert.className = 'callout success';
            alert.innerHTML = `<span data-icon="checkCircle" data-size="18"></span><span><strong>All loans are on track</strong>
                <span>There are no overdue books at the moment.</span></span>`;
            setText('healthBadge', 'Healthy');
            el('healthBadge').className = 'badge success';
        }
        refreshIcons(alert);
    }

    const loans = await apiGet('/loans?view=active');
    renderRecentLoans(loans);
}

function renderRecentLoans(loans) {
    const body = el('recentLoansBody');
    if (!body) return;
    if (!loans.length) {
        body.innerHTML = emptyRow(5, 'No active loans', 'Issue a book and it will appear here.', 'swap');
    } else {
        body.innerHTML = loans.slice(0, 6).map(l => `
            <tr>
                <td class="num"><strong>#${l.id}</strong></td>
                <td><div class="cell-primary">${cover(l.bookTitle, l.genre)}
                    <span><strong>${esc(l.bookTitle || ('Book #' + l.bookId))}</strong><span class="sub">Loan #${l.id}</span></span></div></td>
                <td><div class="cell-primary">${avatar(l.memberName, true)}
                    <span><strong>${esc(l.memberName || ('Member #' + l.memberId))}</strong><span class="sub">${esc(l.memberNo)}</span></span></div></td>
                <td class="num">${fmtDate(l.dueAt)}</td>
                <td>${loanBadge(l)}</td>
            </tr>`).join('');
    }
    refreshIcons(body);
}

/* ==================================================================
   Books
   ================================================================== */
let allBooks = [];
let bookFilter = 'all';

async function initBooks() {
    const search = el('bookSearch');
    const termFromUrl = queryParam('q') || '';
    if (termFromUrl) search.value = termFromUrl;
    await loadBooks(search.value);

    search.addEventListener('input', debounce(() => loadBooks(search.value), 180));

    el('addBookBtn').addEventListener('click', () => {
        showFormModal({
            title: 'Add a new book',
            submitLabel: 'Add book',
            icon: 'book',
            fields: [
                { name: 'isbn', label: 'ISBN', placeholder: '978-0134685991' },
                { name: 'title', label: 'Title', placeholder: 'Effective Java' },
                { name: 'author', label: 'Author', placeholder: 'Joshua Bloch' },
                { name: 'publisher', label: 'Publisher', required: false },
                { name: 'publicationYear', label: 'Publication year', type: 'number', min: 1500, max: 2100, required: false },
                { name: 'genre', label: 'Genre', required: false, placeholder: 'Programming' },
                { name: 'totalCopies', label: 'Number of copies', type: 'number', min: 1, value: 1 }
            ],
            onSubmit: async values => {
                await apiPost('/books', values);
                toast('Book added successfully');
                await loadBooks(el('bookSearch').value);
            }
        });
    });

    document.querySelectorAll('#bookChips .chip').forEach(chip => {
        chip.addEventListener('click', () => {
            bookFilter = chip.dataset.filter;
            document.querySelectorAll('#bookChips .chip').forEach(c => c.classList.toggle('active', c === chip));
            renderBooks(el('bookSearch').value);
        });
    });

    document.querySelectorAll('[data-layout]').forEach(button => {
        button.addEventListener('click', () => {
            const grid = button.dataset.layout === 'grid';
            el('booksTableWrap').style.display = grid ? 'none' : '';
            el('booksGrid').style.display = grid ? 'grid' : 'none';
            document.querySelectorAll('[data-layout]').forEach(b => b.classList.toggle('btn-primary', b === button));
        });
    });

    const handleActions = async e => {
        const btn = e.target.closest('button[data-action]');
        if (!btn) return;
        const id = Number(btn.dataset.id);
        const book = allBooks.find(b => b.id === id);
        if (!book) return;

        if (btn.dataset.action === 'copies') {
            showFormModal({
                title: 'Update copies — ' + book.title,
                submitLabel: 'Update',
                icon: 'edit',
                fields: [{
                    name: 'totalCopies', label: 'Total copies', type: 'number', min: 1, value: book.totalCopies,
                    help: (book.totalCopies - book.availableCopies) + ' copy/copies are currently on loan.'
                }],
                onSubmit: async values => {
                    await apiPost('/books/update', { id, totalCopies: values.totalCopies });
                    toast('Copies updated');
                    await loadBooks(el('bookSearch').value);
                }
            });
        } else if (btn.dataset.action === 'delete') {
            showConfirm({
                title: 'Delete this book?',
                message: `This will permanently remove <strong>${esc(book.title)}</strong>. ` +
                         'Books with loan history cannot be deleted.',
                confirmLabel: 'Delete book',
                onConfirm: async () => {
                    await apiPost('/books/delete', { id });
                    toast('Book deleted');
                    await loadBooks(el('bookSearch').value);
                }
            });
        }
    };
    el('booksBody').addEventListener('click', handleActions);
    el('booksGrid').addEventListener('click', handleActions);
}

async function loadBooks(term = '') {
    const catalogue = await apiGet('/books');
    const q = term.trim().toLowerCase();
    allBooks = q
        ? catalogue.filter(b => [b.title, b.author, b.isbn, b.genre]
            .some(v => String(v || '').toLowerCase().includes(q)))
        : catalogue;

    const totalCopies = catalogue.reduce((sum, b) => sum + b.totalCopies, 0);
    const available = catalogue.reduce((sum, b) => sum + b.availableCopies, 0);
    setText('bookStatTitles', catalogue.length);
    setText('bookStatCopies', totalCopies);
    setText('bookStatAvailable', available);
    setText('bookStatOnLoan', totalCopies - available);

    renderBooks(term);
}

function filteredBooks() {
    if (bookFilter === 'available') return allBooks.filter(b => b.availableCopies > 0);
    if (bookFilter === 'low') return allBooks.filter(b => b.availableCopies > 0 && b.availableCopies <= 1);
    if (bookFilter === 'out') return allBooks.filter(b => b.availableCopies === 0);
    return allBooks;
}

function renderBooks(term = '') {
    const books = filteredBooks();
    const count = el('bookCount');
    if (count) {
        count.textContent = books.length + ' book(s)' + (term ? ' matching “' + term + '”' : '');
        count.className = 'badge neutral';
    }
    setText('chipAll', allBooks.length);
    setText('chipAvailable', allBooks.filter(b => b.availableCopies > 0).length);
    setText('chipLow', allBooks.filter(b => b.availableCopies > 0 && b.availableCopies <= 1).length);
    setText('chipOut', allBooks.filter(b => b.availableCopies === 0).length);

    const body = el('booksBody');
    if (!books.length) {
        body.innerHTML = emptyRow(6, 'No books found', 'Try another search, change the filter or add a new book.');
    } else {
        body.innerHTML = books.map(bookRow).join('');
    }
    refreshIcons(body);

    const grid = el('booksGrid');
    if (grid) {
        grid.innerHTML = books.length ? books.map(bookCard).join('')
            : emptyState('bookOpen', 'No books found', 'Try another search or add a new title.');
        refreshIcons(grid);
    }
}

function bookStock(b) {
    const pct = b.totalCopies ? Math.round(b.availableCopies / b.totalCopies * 100) : 0;
    const kind = b.availableCopies === 0 ? ' out' : b.availableCopies <= 1 ? ' low' : '';
    return `<span class="stock${kind}"><b>${b.availableCopies}</b> / ${b.totalCopies}
        <span class="stock-bar"><i style="width:${pct}%"></i></span></span>`;
}

function bookActions(b) {
    return `<div class="actions">
        <a class="btn btn-sm" href="loans.html?book=${b.id}" title="Issue this book"><span data-icon="arrow" data-size="14"></span> Issue</a>
        <button class="btn btn-sm" data-action="copies" data-id="${b.id}" title="Update copies"><span data-icon="edit" data-size="14"></span> Copies</button>
        <button class="btn btn-sm btn-danger" data-action="delete" data-id="${b.id}" title="Delete book"><span data-icon="trash" data-size="14"></span></button>
    </div>`;
}

function bookRow(b) {
    return `<tr>
        <td class="num">#${b.id}</td>
        <td><div class="cell-primary">${cover(b.title, b.genre)}
            <span><strong>${esc(b.title)}</strong><span class="sub">${esc(b.author)}${b.publisher ? ' · ' + esc(b.publisher) : ''}</span></span>
        </div></td>
        <td class="num">${esc(b.isbn)}</td>
        <td>${b.genre ? `<span class="genre-pill" style="background:var(--primary-soft);color:var(--primary);padding:3px 9px;border-radius:99px;font-size:10px;font-weight:700">${esc(b.genre)}</span>` : badge('Uncategorised')}</td>
        <td>${bookStock(b)}</td>
        <td>${bookActions(b)}</td>
    </tr>`;
}

function bookCard(b) {
    return `<article class="book-card">
        <div class="top">${cover(b.title, b.genre)}
            <div><h3 class="title">${esc(b.title)}</h3><div class="author">${esc(b.author)}</div></div>
        </div>
        <div class="meta-row"><span>${b.genre ? `<span class="genre-pill">${esc(b.genre)}</span>` : badge('Uncategorised')}</span><span class="num">ISBN ${esc(b.isbn)}</span></div>
        <div class="meta-row"><span>Availability</span>${bookStock(b)}</div>
        <div class="card-actions">${bookActions(b)}</div>
    </article>`;
}

/* ==================================================================
   Members
   ================================================================== */
let allMembers = [];
let memberFilter = 'all';

async function initMembers() {
    const search = el('memberSearch');
    const termFromUrl = queryParam('q') || '';
    if (termFromUrl) search.value = termFromUrl;
    await loadMembers(search.value);

    search.addEventListener('input', debounce(() => loadMembers(search.value), 180));

    el('addMemberBtn').addEventListener('click', () => {
        showFormModal({
            title: 'Register a new member',
            submitLabel: 'Add member',
            icon: 'users',
            fields: [
                { name: 'membershipNo', label: 'Membership number', placeholder: 'M-1005' },
                { name: 'fullName', label: 'Full name', placeholder: 'Asha Sharma' },
                { name: 'email', label: 'Email', type: 'email', required: false },
                { name: 'phone', label: 'Phone', required: false },
                { name: 'address', label: 'Address', type: 'textarea', full: true, required: false }
            ],
            onSubmit: async values => {
                await apiPost('/members', values);
                toast('Member registered');
                await loadMembers(el('memberSearch').value);
            }
        });
    });

    document.querySelectorAll('#memberChips .chip').forEach(chip => {
        chip.addEventListener('click', () => {
            memberFilter = chip.dataset.filter;
            document.querySelectorAll('#memberChips .chip').forEach(c => c.classList.toggle('active', c === chip));
            renderMembers(el('memberSearch').value);
        });
    });

    el('membersBody').addEventListener('click', async e => {
        const btn = e.target.closest('button[data-action]');
        if (!btn) return;
        const id = Number(btn.dataset.id);
        const member = allMembers.find(m => m.id === id);
        if (!member) return;

        if (btn.dataset.action === 'history') {
            try {
                const loans = await apiGet('/loans/member?memberId=' + id);
                const rows = loans.length ? loans.map(l => `
                    <tr>
                        <td><div class="cell-primary">${cover(l.bookTitle)}<strong>${esc(l.bookTitle || ('Book #' + l.bookId))}</strong></div></td>
                        <td class="num">${fmtDate(l.issuedAt)}</td>
                        <td class="num">${fmtDate(l.dueAt)}</td>
                        <td>${loanBadge(l)}</td>
                        <td class="num">${money(l.fine)}</td>
                    </tr>`).join('')
                    : emptyRow(5, 'No loans yet', 'This member has not borrowed any books.', 'history');
                showHtmlModal('Loan history — ' + member.fullName, `
                    <div class="table-shell"><table><thead><tr>
                        <th>Book</th><th>Issued</th><th>Due</th><th>Status</th><th>Fine</th>
                    </tr></thead><tbody>${rows}</tbody></table></div>`, true, 'history');
                refreshIcons(document.getElementById('appModalBody'));
            } catch (err) { toast(err.message, 'error'); }
        } else if (btn.dataset.action === 'status') {
            const active = !member.active;
            showConfirm({
                title: (active ? 'Activate' : 'Deactivate') + ' member?',
                message: member.active
                    ? `<strong>${esc(member.fullName)}</strong> will not be able to borrow books until reactivated. Members with active loans cannot be deactivated.`
                    : `<strong>${esc(member.fullName)}</strong> will be able to borrow books again.`,
                confirmLabel: active ? 'Activate' : 'Deactivate',
                danger: !active,
                onConfirm: async () => {
                    await apiPost('/members/status', { id, active: String(active) });
                    toast(active ? 'Member activated' : 'Member deactivated');
                    await loadMembers(el('memberSearch').value);
                }
            });
        } else if (btn.dataset.action === 'delete') {
            showConfirm({
                title: 'Delete this member?',
                message: `This will permanently remove <strong>${esc(member.fullName)}</strong>. Members with loan history cannot be deleted.`,
                confirmLabel: 'Delete member',
                onConfirm: async () => {
                    await apiPost('/members/delete', { id });
                    toast('Member deleted');
                    await loadMembers(el('memberSearch').value);
                }
            });
        }
    });
}

async function loadMembers(term = '') {
    const directory = await apiGet('/members');
    const q = term.trim().toLowerCase();
    allMembers = q
        ? directory.filter(m => [m.fullName, m.membershipNo, m.email, m.phone]
            .some(v => String(v || '').toLowerCase().includes(q)))
        : directory;

    const activeCount = directory.filter(m => m.active).length;
    setText('memberStatTotal', directory.length);
    setText('memberStatActive', activeCount);
    setText('memberStatInactive', directory.length - activeCount);

    renderMembers(term);
}

function renderMembers(term = '') {
    const members = memberFilter === 'all' ? allMembers
        : allMembers.filter(m => memberFilter === 'active' ? m.active : !m.active);

    const count = el('memberCount');
    if (count) {
        count.textContent = members.length + ' member(s)' + (term ? ' matching “' + term + '”' : '');
        count.className = 'badge neutral';
    }
    setText('memberChipAll', allMembers.length);
    setText('memberChipActive', allMembers.filter(m => m.active).length);
    setText('memberChipInactive', allMembers.filter(m => !m.active).length);

    const body = el('membersBody');
    if (!members.length) {
        body.innerHTML = emptyRow(6, 'No members found', 'Try another search, change the filter or register a member.', 'users');
    } else {
        body.innerHTML = members.map(m => `
            <tr>
                <td class="num">#${m.id}</td>
                <td><div class="cell-primary">${avatar(m.fullName)}
                    <span><strong>${esc(m.fullName)}</strong><span class="sub">${esc(m.membershipNo)}</span></span></div></td>
                <td>${esc(m.email || '—')}<span class="sub">${esc(m.phone || '')}</span></td>
                <td class="num">${fmtDate(m.joinedAt)}</td>
                <td>${statusBadge(m)}</td>
                <td><div class="actions">
                    <button class="btn btn-sm" data-action="history" data-id="${m.id}" title="Loan history"><span data-icon="history" data-size="14"></span></button>
                    <button class="btn btn-sm" data-action="status" data-id="${m.id}"><span data-icon="refresh" data-size="14"></span> ${m.active ? 'Deactivate' : 'Activate'}</button>
                    <button class="btn btn-sm btn-danger" data-action="delete" data-id="${m.id}" title="Delete member"><span data-icon="trash" data-size="14"></span></button>
                </div></td>
            </tr>`).join('');
    }
    refreshIcons(body);
}

/* ==================================================================
   Loans (issue / return)
   ================================================================== */
let currentLoanView = 'active';

async function initLoans() {
    await Promise.all([loadLoanOptions(), loadLoanRules(), loadLoans('active')]);

    ['issueBook', 'issueMember'].forEach(id => {
        el(id).addEventListener('change', updateIssuePreview);
    });
    updateIssuePreview();

    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener('click', async () => {
            currentLoanView = tab.dataset.view;
            document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t === tab));
            try { await loadLoans(currentLoanView); } catch (e) { toast(e.message, 'error'); }
        });
    });

    el('issueForm').addEventListener('submit', async e => {
        e.preventDefault();
        const button = el('issueSubmit');
        button.disabled = true;
        try {
            const result = await apiPost('/loans', {
                bookId: el('issueBook').value,
                memberId: el('issueMember').value
            });
            toast('Book issued — due ' + fmtDate(result.dueAt));
            await Promise.all([loadLoanOptions(), loadLoans('active')]);
            document.querySelector('.tab[data-view="active"]').click();
            updateIssuePreview();
        } catch (err) {
            toast(err.message, 'error');
        } finally {
            button.disabled = false;
            loadLoanOptions().catch(() => {});
        }
    });

    el('loansBody').addEventListener('click', async e => {
        const btn = e.target.closest('button[data-action="return"]');
        if (!btn) return;
        const id = Number(btn.dataset.id);
        const title = btn.dataset.title;
        showConfirm({
            title: 'Return this book?',
            message: `Mark <strong>${esc(title)}</strong> as returned. Any overdue fine will be calculated automatically and the copy will be added back to stock.`,
            confirmLabel: 'Return book',
            danger: false,
            icon: 'checkCircle',
            onConfirm: async () => {
                const result = await apiPost('/loans/return', { loanId: id });
                toast(result.ok);
                await Promise.all([loadLoanOptions(), loadLoans(currentLoanView)]);
            }
        });
    });
}

function updateIssuePreview() {
    const bookId = el('issueBook').value;
    const memberId = el('issueMember').value;
    const book = el('issueBook').selectedOptions[0];
    const member = el('issueMember').selectedOptions[0];
    const preview = el('issuePreview');
    if (!preview) return;
    const ready = bookId && memberId;
    preview.classList.toggle('filled', !!ready);
    setText('previewTitle', ready ? (book.textContent.split(' — ')[0] + ' → ' + member.textContent.split(' (')[0]) : 'Choose a book and member');
    setText('previewSub', ready
        ? 'Due date and fine policy will be applied automatically'
        : 'A due date will be calculated automatically');
    const icon = preview.querySelector('.sp-icon');
    if (icon) icon.dataset.icon = ready ? 'checkCircle' : 'bookOpen';
    refreshIcons(preview);
}

async function loadLoanOptions() {
    const [books, members] = await Promise.all([apiGet('/books'), apiGet('/members')]);
    const bookSelect = el('issueBook');
    const preserveBook = bookSelect.value || queryParam('book');
    bookSelect.innerHTML = '<option value="">Choose a book…</option>' + books.map(b => `
        <option value="${b.id}"${String(b.id) === String(preserveBook) ? ' selected' : ''}>
            ${esc(b.title)} — ${b.availableCopies}/${b.totalCopies} available
        </option>`).join('');

    const memberSelect = el('issueMember');
    const preserveMember = memberSelect.value;
    const activeMembers = members.filter(m => m.active);
    memberSelect.innerHTML = activeMembers.length
        ? '<option value="">Choose a member…</option>' + activeMembers.map(m => `
            <option value="${m.id}"${String(m.id) === String(preserveMember) ? ' selected' : ''}>
                ${esc(m.fullName)} (${esc(m.membershipNo)})
            </option>`).join('')
        : '<option value="">No active members — register one first</option>';
    memberSelect.disabled = activeMembers.length === 0;
    el('issueSubmit').disabled = activeMembers.length === 0 || books.length === 0;
}

async function loadLoanRules() {
    const d = await apiGet('/dashboard');
    const text = `Loan period ${d.loanPeriodDays} days · maximum ${d.maxLoansPerMember} loans per member · ${money(d.finePerDay)} per started overdue day`;
    setText('loanRules', text);
    setText('loanRulesFine', money(d.finePerDay) + ' per started overdue day');
}

async function loadLoans(view) {
    const loans = await apiGet('/loans?view=' + view);
    const d = await apiGet('/dashboard');
    const body = el('loansBody');

    setText('tabActiveCount', d.activeLoans);
    setText('tabOverdueCount', d.overdue);
    setText('tabHistoryCount', d.totalLoans);
    setText('loanStatActive', d.activeLoans);
    setText('loanStatOverdue', d.overdue);
    setText('loanStatHistory', d.totalLoans);

    if (!loans.length) {
        const messages = {
            active: ['No active loans', 'Issue a book using the form above.', 'swap'],
            overdue: ['No overdue books', 'Everything is returned on time.', 'checkCircle'],
            history: ['No loan history', 'Completed loans will appear here.', 'history']
        };
        const m = messages[view] || messages.active;
        body.innerHTML = emptyRow(7, m[0], m[1], m[2]);
    } else {
        body.innerHTML = loans.map(l => `
            <tr>
                <td class="num"><strong>#${l.id}</strong></td>
                <td><div class="cell-primary">${cover(l.bookTitle)}
                    <span><strong>${esc(l.bookTitle || ('Book #' + l.bookId))}</strong><span class="sub">Loan #${l.id}</span></span></div></td>
                <td><div class="cell-primary">${avatar(l.memberName, true)}
                    <span><strong>${esc(l.memberName || ('Member #' + l.memberId))}</strong><span class="sub">${esc(l.memberNo)}</span></span></div></td>
                <td class="num">${fmtDate(l.issuedAt)}</td>
                <td class="num">${fmtDate(l.dueAt)}</td>
                <td>${loanBadge(l)}${fineText(l.fine) ? `<span class="sub">${fineText(l.fine)}</span>` : ''}</td>
                <td>${l.active
                    ? `<button class="btn btn-sm btn-teal" data-action="return" data-id="${l.id}" data-title="${esc(l.bookTitle || 'this book')}"><span data-icon="check" data-size="14"></span> Return</button>`
                    : '<span style="color:var(--muted)">—</span>'}</td>
            </tr>`).join('');
    }
    refreshIcons(body);
}

/* ==================================================================
   Reports
   ================================================================== */
async function initReports() {
    const [d, books, overdue, history] = await Promise.all([
        apiGet('/dashboard'),
        apiGet('/books'),
        apiGet('/loans?view=overdue'),
        apiGet('/loans?view=history&limit=15')
    ]);

    const values = {
        reportBooks: d.books,
        reportCopies: d.availableCopies,
        reportMembers: d.members,
        reportActive: d.activeLoans,
        reportOverdue: d.overdue,
        reportFines: money(d.fines),
        reportHeroLoans: d.totalLoans,
        reportHeroFines: money(d.fines),
        overdueBadge: d.overdue + ' overdue'
    };
    Object.entries(values).forEach(([id, value]) => setText(id, value));

    const totalCopies = books.reduce((sum, b) => sum + b.totalCopies, 0);
    const available = books.reduce((sum, b) => sum + b.availableCopies, 0);
    const onLoan = Math.max(0, d.activeLoans - d.overdue);
    const availPct = totalCopies ? available / totalCopies * 100 : 0;
    const loanPct = totalCopies ? onLoan / totalCopies * 100 : 0;
    const overduePct = totalCopies ? d.overdue / totalCopies * 100 : 0;
    const endLoan = Math.min(100, availPct + loanPct);
    const endOverdue = Math.min(100, endLoan + overduePct);

    setText('availabilityPct', Math.round(availPct) + '% available');
    setText('availabilityDonutValue', Math.round(availPct) + '%');
    setText('legendAvailable', available);
    setText('legendOnLoan', onLoan);
    setText('legendOverdue', d.overdue);
    setText('availabilityText', `${available} of ${totalCopies} copies available · ${onLoan} on loan · ${d.overdue} overdue`);
    setWidth('availabilityBar', availPct);

    const donut = el('availabilityDonut');
    if (donut) {
        donut.style.background = `conic-gradient(var(--success) 0 ${availPct}%, `
            + `var(--warning) ${availPct}% ${endLoan}%, `
            + `var(--danger) ${endLoan}% ${endOverdue}%, var(--surface-3) ${endOverdue}% 100%)`;
    }

    const genres = {};
    books.forEach(b => { const g = b.genre || 'Uncategorised'; genres[g] = (genres[g] || 0) + 1; });
    const genreEntries = Object.entries(genres).sort((a, b) => b[1] - a[1]);
    const max = Math.max(1, ...genreEntries.map(e => e[1]));
    const palette = ['', 'teal', 'amber', 'rose'];
    if (el('genreBars')) {
        el('genreBars').innerHTML = genreEntries.length ? genreEntries.map(([name, count], i) => `
            <div class="bar-row">
                <span class="bar-label" title="${esc(name)}">${esc(name)}</span>
                <span class="bar-track"><i class="bar-fill ${palette[i % palette.length]}" style="width:${count / max * 100}%"></i></span>
                <span class="bar-value">${count}</span>
            </div>`).join('') : emptyState('bookOpen', 'No books yet', 'Add a book to build this breakdown.', 'book');
    }

    const returned = Math.max(0, d.totalLoans - d.activeLoans);
    if (el('statusBars')) {
        const rows = [
            ['Active', d.activeLoans, '', d.totalLoans],
            ['Overdue', d.overdue, 'amber', d.totalLoans],
            ['Returned', returned, 'teal', d.totalLoans]
        ];
        el('statusBars').innerHTML = rows.map(([name, count, color, total]) => `
            <div class="bar-row">
                <span class="bar-label">${name}</span>
                <span class="bar-track"><i class="bar-fill ${color}" style="width:${total ? count / total * 100 : 0}%"></i></span>
                <span class="bar-value">${count}</span>
            </div>`).join('');
    }

    const overdueBody = el('overdueReportBody');
    overdueBody.innerHTML = overdue.length ? overdue.map(l => `
        <tr>
            <td class="num"><strong>#${l.id}</strong></td>
            <td><div class="cell-primary">${cover(l.bookTitle)}<strong>${esc(l.bookTitle || ('Book #' + l.bookId))}</strong></div></td>
            <td><div class="cell-primary">${avatar(l.memberName, true)}<strong>${esc(l.memberName || ('Member #' + l.memberId))}</strong></div></td>
            <td class="num">${fmtDate(l.dueAt)}</td>
            <td>${loanBadge(l)}</td>
        </tr>`).join('') : emptyRow(5, 'Nothing overdue', 'All borrowed books are within their loan period.', 'checkCircle');
    refreshIcons(overdueBody);

    const historyBody = el('historyReportBody');
    historyBody.innerHTML = history.length ? history.map(l => `
        <tr>
            <td class="num"><strong>#${l.id}</strong></td>
            <td><div class="cell-primary">${cover(l.bookTitle)}<strong>${esc(l.bookTitle || ('Book #' + l.bookId))}</strong></div></td>
            <td><div class="cell-primary">${avatar(l.memberName, true)}<strong>${esc(l.memberName || ('Member #' + l.memberId))}</strong></div></td>
            <td class="num">${fmtDate(l.issuedAt)}</td>
            <td class="num">${fmtDate(l.returnedAt || l.dueAt)}</td>
            <td>${loanBadge(l)}</td>
            <td class="num">${money(l.fine)}</td>
        </tr>`).join('') : emptyRow(7, 'No loans recorded', 'Issue a book to build up loan history.', 'history');
    refreshIcons(historyBody);

    refreshIcons(el('genreBars'));
    refreshIcons(el('statusBars'));

    const print = () => window.print();
    ['printReportBtn', 'printHeroBtn'].forEach(id => {
        const button = el(id);
        if (button) button.addEventListener('click', print);
    });
}
