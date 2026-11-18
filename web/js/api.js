/* AruLibrary — shared API client, UI helpers and shell behaviour. */

const API_BASE = '/api';

/* ---------------- HTTP ---------------- */
async function apiGet(path) {
    const response = await fetch(API_BASE + path, { headers: { 'Accept': 'application/json' } });
    return parseResponse(response);
}

async function apiPost(path, data = {}) {
    const body = new URLSearchParams();
    Object.entries(data).forEach(([key, value]) => {
        if (value !== null && value !== undefined && value !== '') body.append(key, value);
    });
    const response = await fetch(API_BASE + path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=utf-8' },
        body: body.toString()
    });
    return parseResponse(response);
}

async function parseResponse(response) {
    let payload;
    try {
        payload = await response.json();
    } catch (e) {
        throw new Error('Server returned an invalid response (HTTP ' + response.status + ')');
    }
    if (!response.ok || payload.error) {
        throw new Error(payload.error || 'Request failed (HTTP ' + response.status + ')');
    }
    return payload;
}

/* ---------------- formatting ---------------- */
function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value).replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[c]);
}

function el(id) { return document.getElementById(id); }

function queryParam(name) {
    return new URLSearchParams(location.search).get(name);
}

function debounce(fn, wait = 200) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), wait);
    };
}

function fmtDate(iso, withTime = false) {
    if (!iso) return '<span style="color:var(--muted)">—</span>';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return esc(iso);
    const date = d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    if (!withTime) return esc(date);
    return esc(date + ' · ' + d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }));
}

// Indian Rupees with Indian digit grouping (₹1,00,000.00).
const INR_FORMATTER = new Intl.NumberFormat('en-IN', {
    style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2
});
function money(value) { return INR_FORMATTER.format(Number(value || 0)); }

/* ---------------- visual helpers ---------------- */
const GRADIENTS = [
    'linear-gradient(135deg,#6366f1,#8b5cf6)', 'linear-gradient(135deg,#0d9488,#14b8a6)',
    'linear-gradient(135deg,#f59e0b,#f97316)', 'linear-gradient(135deg,#ec4899,#f43f5e)',
    'linear-gradient(135deg,#0284c7,#6366f1)', 'linear-gradient(135deg,#7c3aed,#db2777)',
    'linear-gradient(135deg,#059669,#0d9488)', 'linear-gradient(135deg,#b45309,#ea580c)'
];
function colorFor(text) {
    let hash = 0;
    for (const c of String(text || '')) hash = (hash * 31 + c.charCodeAt(0)) >>> 0;
    return GRADIENTS[hash % GRADIENTS.length];
}
function initials(text, max = 2) {
    return String(text || '?').trim().split(/\s+/).slice(0, max).map(w => w[0]).join('').toUpperCase();
}
function avatar(name, small = false) {
    return `<span class="avatar${small ? ' sm' : ''}" style="background:${colorFor(name)}">${esc(initials(name))}</span>`;
}
function cover(title, genre) {
    return `<span class="cover" style="background:${colorFor(genre || title)}">${esc(initials(title, 1))}</span>`;
}
function badge(text, kind = 'neutral') {
    return `<span class="badge ${kind}">${esc(text)}</span>`;
}
function loanBadge(loan) {
    if (!loan.active) return badge('Returned', 'neutral');
    return loan.overdue ? badge('Overdue', 'danger') : badge('Active', 'success');
}
function skeletonRows(rows = 5, cols = 5) {
    return Array.from({ length: rows }, () => '<tr>' + Array.from({ length: cols }, () =>
        '<td><div class="skeleton line" style="width:' + (35 + Math.random() * 55) + '%"></div></td>').join('') + '</tr>').join('');
}
function emptyState(icon, title, text, action = '') {
    return `<div class="empty-state"><div class="empty-icon" data-icon="${icon}" data-size="26"></div>
        <strong>${esc(title)}</strong><p>${esc(text)}</p>${action}</div>`;
}

/* ---------------- toasts ---------------- */
function toast(message, type = 'success') {
    let area = document.querySelector('.toast-area');
    if (!area) {
        area = document.createElement('div');
        area.className = 'toast-area';
        document.body.appendChild(area);
    }
    const item = document.createElement('div');
    item.className = 'toast ' + type;
    const icon = type === 'error' ? 'alert' : type === 'info' ? 'sparkle' : 'checkCircle';
    item.innerHTML = `<span data-icon="${icon}" data-size="17"></span><span>${esc(message)}</span>`;
    area.appendChild(item);
    if (typeof hydrateIcons === 'function') hydrateIcons(item);
    setTimeout(() => {
        item.classList.add('out');
        setTimeout(() => item.remove(), 220);
    }, 3800);
}

/* ---------------- modal ---------------- */
function modalRoot() {
    let root = document.getElementById('appModal');
    if (!root) {
        root = document.createElement('div');
        root.id = 'appModal';
        root.className = 'backdrop';
        root.innerHTML = `
            <div class="modal" id="appModalBox" role="dialog" aria-modal="true">
                <div class="modal-head">
                    <h3 id="appModalTitle"></h3>
                    <button type="button" class="modal-close" id="appModalClose" aria-label="Close"><span data-icon="close" data-size="18"></span></button>
                </div>
                <div class="modal-body" id="appModalBody"></div>
                <div class="modal-foot" id="appModalFoot"></div>
            </div>`;
        document.body.appendChild(root);
        root.addEventListener('click', e => { if (e.target === root) closeModal(); });
        el('appModalClose').addEventListener('click', closeModal);
        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && root.classList.contains('show')) closeModal();
        });
    }
    return root;
}

function closeModal() {
    const root = document.getElementById('appModal');
    if (root) root.classList.remove('show');
}

function openModal(title, bodyHtml, footHtml = '', wide = false, icon = null) {
    const root = modalRoot();
    const heading = icon
        ? `<span data-icon="${icon}" data-size="19" style="display:flex;color:var(--primary)"></span><span>${esc(title)}</span>`
        : esc(title);
    el('appModalTitle').innerHTML = `<span style="display:flex;align-items:center;gap:10px">${heading}</span>`;
    el('appModalBody').innerHTML = bodyHtml;
    el('appModalFoot').innerHTML = footHtml;
    el('appModalBox').classList.toggle('wide', wide);
    root.classList.add('show');
    if (typeof hydrateIcons === 'function') hydrateIcons(root);
}

function fieldHtml(f) {
    const name = esc(f.name);
    const value = f.value === null || f.value === undefined ? '' : String(f.value);
    const required = f.required === false ? '' : ' required';
    const cls = 'field' + (f.full ? ' full' : '');
    let control;
    if (f.type === 'select') {
        const options = (f.options || []).map(o => {
            const selected = String(o.value) === value ? ' selected' : '';
            return `<option value="${esc(o.value)}"${selected}>${esc(o.label)}</option>`;
        }).join('');
        control = `<select class="select" data-field="${name}"${required}>${options}</select>`;
    } else if (f.type === 'textarea') {
        control = `<textarea class="input" rows="3" data-field="${name}" placeholder="${esc(f.placeholder || '')}">${esc(value)}</textarea>`;
    } else {
        control = `<input class="input" type="${esc(f.type || 'text')}" data-field="${name}"
            value="${esc(value)}" placeholder="${esc(f.placeholder || '')}"
            ${f.min !== undefined ? 'min="' + esc(f.min) + '"' : ''}
            ${f.max !== undefined ? 'max="' + esc(f.max) + '"' : ''}${required}>`;
    }
    return `<div class="${cls}"><label>${esc(f.label)}</label>${control}
        ${f.help ? `<div class="help">${esc(f.help)}</div>` : ''}</div>`;
}

/**
 * Shows a form modal. onSubmit(values) may be async; return false to keep the
 * dialog open, anything else closes it. Errors are shown as a toast.
 */
function showFormModal({ title, fields, submitLabel = 'Save', onSubmit, wide = false, icon = 'plus' }) {
    openModal(title,
        `<form id="appModalForm"><div class="form-grid">${fields.map(fieldHtml).join('')}</div></form>`,
        `<button type="button" class="btn" id="appModalCancel">Cancel</button>
         <button type="button" class="btn btn-primary" id="appModalSubmit"><span data-icon="check" data-size="15"></span>${esc(submitLabel)}</button>`,
        wide, icon);

    el('appModalCancel').addEventListener('click', closeModal);
    el('appModalSubmit').addEventListener('click', async () => {
        const form = el('appModalForm');
        if (!form.reportValidity()) return;
        const values = {};
        form.querySelectorAll('[data-field]').forEach(input => { values[input.dataset.field] = input.value.trim(); });
        const button = el('appModalSubmit');
        button.disabled = true;
        button.textContent = 'Working...';
        try {
            const result = await onSubmit(values);
            if (result !== false) closeModal();
            else { button.disabled = false; button.textContent = submitLabel; }
        } catch (e) {
            toast(e.message, 'error');
            button.disabled = false;
            button.textContent = submitLabel;
        }
    });
    const first = el('appModalForm').querySelector('input, select, textarea');
    if (first) first.focus();
}

function showConfirm({ title, message, confirmLabel = 'Confirm', danger = true, onConfirm }) {
    openModal(title,
        `<div class="confirm-icon ${danger ? '' : 'info'}" data-icon="${danger ? 'alert' : 'checkCircle'}" data-size="23"></div>
         <div style="color:var(--text-2);font-size:13px">${message}</div>`,
        `<button type="button" class="btn" id="appModalCancel">Cancel</button>
         <button type="button" class="btn ${danger ? 'btn-danger' : 'btn-teal'}" id="appModalSubmit">${esc(confirmLabel)}</button>`,
        false, danger ? 'alert' : 'checkCircle');
    el('appModalCancel').addEventListener('click', closeModal);
    el('appModalSubmit').addEventListener('click', async () => {
        const button = el('appModalSubmit');
        button.disabled = true;
        try {
            await onConfirm();
            closeModal();
        } catch (e) {
            toast(e.message, 'error');
            button.disabled = false;
        }
    });
}

function showHtmlModal(title, html, wide = true, icon = 'bookOpen') {
    openModal(title, html,
        `<button type="button" class="btn" id="appModalCancel">Close</button>`, wide, icon);
    el('appModalCancel').addEventListener('click', closeModal);
}

/* ---------------- theme + shell ---------------- */
function applyTheme(theme) {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('library-theme', theme);
    const btn = el('themeBtn');
    if (btn) {
        btn.innerHTML = `<span data-icon="${theme === 'dark' ? 'sun' : 'moon'}" data-size="18"></span>`;
        btn.title = theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode';
        if (typeof hydrateIcons === 'function') hydrateIcons(btn);
    }
}

function setupGlobalSearch() {
    const input = el('globalSearch');
    const results = el('searchResults');
    if (!input || !results) return;

    const hide = () => results.classList.remove('show');
    const run = debounce(async () => {
        const q = input.value.trim();
        if (q.length < 2) { hide(); return; }
        try {
            const [books, members] = await Promise.all([
                apiGet('/books?q=' + encodeURIComponent(q)),
                apiGet('/members?q=' + encodeURIComponent(q))
            ]);
            let html = '';
            if (books.length) {
                html += '<div class="search-group">Books</div>' + books.slice(0, 4).map(b =>
                    `<button class="search-item" data-go="books.html?q=${encodeURIComponent(b.title)}">
                        ${cover(b.title, b.genre)}
                        <span><span class="si-title">${esc(b.title)}</span><span class="si-sub">${esc(b.author)} · ${b.availableCopies}/${b.totalCopies} available</span></span>
                     </button>`).join('');
            }
            if (members.length) {
                html += '<div class="search-group">Members</div>' + members.slice(0, 4).map(m =>
                    `<button class="search-item" data-go="members.html?q=${encodeURIComponent(m.fullName)}">
                        ${avatar(m.fullName, true)}
                        <span><span class="si-title">${esc(m.fullName)}</span><span class="si-sub">${esc(m.membershipNo)} · ${m.active ? 'Active' : 'Inactive'}</span></span>
                     </button>`).join('');
            }
            results.innerHTML = html || '<div class="search-empty">No matches found</div>';
            results.classList.add('show');
            if (typeof hydrateIcons === 'function') hydrateIcons(results);
        } catch (e) { /* search is non-critical */ }
    }, 220);

    input.addEventListener('input', run);
    input.addEventListener('keydown', e => {
        if (e.key === 'Enter' && input.value.trim()) {
            location.href = 'books.html?q=' + encodeURIComponent(input.value.trim());
        }
    });
    results.addEventListener('click', e => {
        const item = e.target.closest('[data-go]');
        if (item) location.href = item.dataset.go;
    });
    document.addEventListener('click', e => {
        if (!e.target.closest('.global-search')) hide();
    });
}

/** Common shell setup — called by every page. */
function setupCommon(page) {
    document.querySelectorAll('.nav a').forEach(link => {
        if (link.dataset.page === page) link.classList.add('active');
    });

    applyTheme(localStorage.getItem('library-theme') ||
        (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'));

    const themeBtn = el('themeBtn');
    if (themeBtn) themeBtn.addEventListener('click', () =>
        applyTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'));

    const menuBtn = el('menuBtn');
    const sidebar = el('sidebar');
    const overlay = el('mobileOverlay');
    const closeNav = () => { sidebar?.classList.remove('open'); overlay?.classList.remove('show'); };
    if (menuBtn) menuBtn.addEventListener('click', () => {
        sidebar?.classList.toggle('open');
        overlay?.classList.toggle('show');
    });
    if (overlay) overlay.addEventListener('click', closeNav);
    document.querySelectorAll('.nav a').forEach(a => a.addEventListener('click', closeNav));

    const today = el('todayText');
    if (today) today.textContent = new Date().toLocaleDateString('en-GB', {
        weekday: 'short', day: 'numeric', month: 'short', year: 'numeric'
    });

    const reset = el('resetDataBtn');
    if (reset) {
        reset.addEventListener('click', () => showConfirm({
            title: 'Reset sample data?',
            message: 'This reloads the schema and sample records, removing all current books, members and loans. This cannot be undone.',
            confirmLabel: 'Reset data',
            onConfirm: async () => {
                await apiPost('/setup');
                toast('Sample data reloaded');
                setTimeout(() => location.reload(), 450);
            }
        }));
    }

    const bell = el('alertBtn');
    if (bell) bell.addEventListener('click', async () => {
        try {
            const d = await apiGet('/dashboard');
            toast(d.overdue ? d.overdue + ' overdue loan(s) need attention' : 'Everything is up to date',
                d.overdue ? 'error' : 'success');
        } catch (e) { toast(e.message, 'error'); }
    });

    setupGlobalSearch();

    document.addEventListener('keydown', e => {
        if (e.key === '/' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName)) {
            e.preventDefault();
            el('globalSearch')?.focus();
        }
    });
}
