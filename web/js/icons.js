/* Lightweight inline SVG icon set — no external assets or icon fonts. */

const ICONS = {
    dashboard: ['M3 3h7v7H3z', 'M14 3h7v5h-7z', 'M14 10h7v11h-7z', 'M3 12h7v9H3z'],
    book: ['M4 19.5A2.5 2.5 0 0 1 6.5 17H20', 'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'],
    users: ['M17 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2', 'M9.5 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8', 'M22 21v-2a4 4 0 0 0-3-3.87', 'M16 3.1a4 4 0 0 1 0 7.8'],
    swap: ['M17 1l4 4-4 4', 'M3 11V9a4 4 0 0 1 4-4h14', 'M7 23l-4-4 4-4', 'M21 13v2a4 4 0 0 1-4 4H3'],
    chart: ['M3 3v18h18', 'M7 16l4-5 3 3 5-7'],
    search: ['M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z', 'M21 21l-4.35-4.35'],
    plus: ['M12 5v14', 'M5 12h14'],
    sun: ['M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10z', 'M12 1v2', 'M12 21v2', 'M4.2 4.2l1.4 1.4', 'M18.4 18.4l1.4 1.4', 'M1 12h2', 'M21 12h2', 'M4.2 19.8l1.4-1.4', 'M18.4 5.6l1.4-1.4'],
    moon: ['M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z'],
    bell: ['M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9', 'M13.7 21a2 2 0 0 1-3.4 0'],
    menu: ['M3 12h18', 'M3 6h18', 'M3 18h18'],
    close: ['M18 6L6 18', 'M6 6l12 12'],
    chevron: ['M9 18l6-6-6-6'],
    check: ['M20 6L9 17l-5-5'],
    trash: ['M3 6h18', 'M8 6V4h8v2', 'M19 6l-1 14H6L5 6', 'M10 11v6', 'M14 11v6'],
    edit: ['M12 20h9', 'M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z'],
    history: ['M3 12a9 9 0 1 0 3-6.7L3 8', 'M3 3v5h5', 'M12 7v5l3 2'],
    alert: ['M12 9v4', 'M12 17h.01', 'M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z'],
    print: ['M6 9V2h12v7', 'M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2', 'M6 14h12v8H6z'],
    refresh: ['M20 11a8 8 0 1 0-2.3 5.7', 'M20 4v7h-7'],
    filter: ['M4 5h16', 'M7 12h10', 'M10 19h4'],
    grid: ['M3 3h8v8H3z', 'M13 3h8v8h-8z', 'M3 13h8v8H3z', 'M13 13h8v8h-8z'],
    list: ['M8 6h13', 'M8 12h13', 'M8 18h13', 'M3 6h.01', 'M3 12h.01', 'M3 18h.01'],
    arrow: ['M5 12h14', 'M13 6l6 6-6 6'],
    box: ['M21 16V8l-9-5-9 5v8l9 5 9-5z', 'M3.3 7.3L12 12l8.7-4.7', 'M12 22V12'],
    tag: ['M20.6 13.4L12 22l-9-9V3h10l7.6 7.6a2 2 0 0 1 0 2.8z', 'M7.5 7.5h.01'],
    clock: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z', 'M12 6v6l4 2'],
    rupee: ['M6 3h12', 'M6 8h12', 'M6 3v5h9', 'M15 8l-9 13'],
    usersPlus: ['M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2', 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8', 'M19 8v6', 'M22 11h-6'],
    checkCircle: ['M22 11.1V12a10 10 0 1 1-5.9-9.1', 'M22 4L12 14l-3-3'],
    sparkle: ['M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z', 'M19 17l.7 1.8L21.5 19l-1.8.7L19 21l-.7-1.3L16.5 19l1.8-.2z'],
    calendar: ['M19 4H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z', 'M16 2v4', 'M8 2v4', 'M3 10h18'],
    bookOpen: ['M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z', 'M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z'],
    logout: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'M16 17l5-5-5-5', 'M21 12H9']
};

/** Replaces every <span data-icon="name"> with an inline SVG. */
function hydrateIcons(root = document) {
    root.querySelectorAll('[data-icon]').forEach(node => {
        const name = node.dataset.icon;
        const paths = ICONS[name] || ICONS.book;
        const size = node.dataset.size || '18';
        const width = node.dataset.stroke || '1.8';
        node.innerHTML = `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" `
            + `stroke="currentColor" stroke-width="${width}" stroke-linecap="round" stroke-linejoin="round" `
            + `aria-hidden="true">${paths.map(d => `<path d="${d}"/>`).join('')}</svg>`;
    });
}

document.addEventListener('DOMContentLoaded', () => hydrateIcons());
