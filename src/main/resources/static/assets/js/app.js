/**
 * 校园失物招领 · 前端入口
 * 极简 hash 路由 + 登录守卫，无构建、无依赖。
 */

import { session, go, toast, onUnauthorized, errorState } from './core.js';
import * as student from './views.student.js';
import * as admin from './views.admin.js';

const appEl = document.getElementById('app');
const topbar = document.getElementById('topbar');
const nav = document.getElementById('nav');
const userbox = document.getElementById('userbox');

const routes = [
    { path: '/login', view: student.loginView, pub: true, bare: true, title: '登录' },
    { path: '/items', view: student.listView, title: '失物招领' },
    { path: '/items/:id', view: student.detailView, title: '信息详情' },
    { path: '/items/:id/edit', view: student.editView, title: '编辑信息' },
    { path: '/publish', view: student.publishView, title: '发布信息' },
    { path: '/my/items', view: student.myItemsView, title: '我的发布' },
    { path: '/my/claims', view: student.myClaimsView, title: '我的认领' },
    { path: '/admin', view: admin.dashboardView, admin: true, title: '管理后台' },
    { path: '/admin/items', view: admin.itemsView, admin: true, title: '信息审核' },
    { path: '/admin/claims', view: admin.claimsView, admin: true, title: '认领处理' },
    { path: '/admin/logs', view: admin.logsView, admin: true, title: '操作日志' }
];

function segments(path) {
    return path.split('/').filter(Boolean);
}

function matchRoute(path) {
    const parts = segments(path);
    for (const route of routes) {
        const pattern = segments(route.path);
        if (pattern.length !== parts.length) continue;
        const params = {};
        let ok = true;
        for (let i = 0; i < pattern.length; i++) {
            const p = pattern[i];
            if (p.charAt(0) === ':') {
                params[p.slice(1)] = decodeURIComponent(parts[i]);
            } else if (p !== parts[i]) {
                ok = false;
                break;
            }
        }
        if (ok) return { route, params };
    }
    return null;
}

function parseHash() {
    const raw = location.hash.replace(/^#/, '') || '/items';
    const index = raw.indexOf('?');
    const path = index === -1 ? raw : raw.slice(0, index);
    const query = {};
    if (index !== -1) {
        new URLSearchParams(raw.slice(index + 1)).forEach((value, key) => {
            query[key] = value;
        });
    }
    return { path: path || '/items', query };
}

function navLink(href, text, active) {
    return '<a href="' + href + '"' + (active ? ' class="active" aria-current="page"' : '') + '>' + text + '</a>';
}

function renderTopbar(route, path) {
    if (route.bare) {
        topbar.hidden = true;
        return;
    }
    topbar.hidden = false;

    const links = [
        navLink('#/items', '首页', path === '/items' || (path.startsWith('/items/') && !path.endsWith('/edit'))),
        navLink('#/publish', '发布', path === '/publish'),
        navLink('#/my/items', '我的发布', path === '/my/items'),
        navLink('#/my/claims', '我的认领', path === '/my/claims')
    ];
    if (session.isAdmin) {
        links.push('<span class="nav-sep" aria-hidden="true"></span>');
        links.push(navLink('#/admin', '管理后台', path.startsWith('/admin')));
    }
    nav.innerHTML = links.join('');

    const user = session.user || {};
    const initial = (user.username || '?').charAt(0).toUpperCase();
    userbox.innerHTML =
        '<div class="user-chip">' +
        '<span class="avatar" aria-hidden="true">' + initial + '</span>' +
        '<span class="user-names"><span class="user-name">' + escapeHtml(user.username || '') + '</span>' +
        '<span class="user-role">' + (user.role === 'ADMIN' ? '管理员' : '学生') + '</span></span>' +
        '</div>' +
        '<button class="btn btn-sm btn-ghost" data-logout>退出</button>';

    userbox.querySelector('[data-logout]').addEventListener('click', () => {
        session.clear();
        toast('已退出登录', 'info');
        go('/login');
    });
}

function escapeHtml(value) {
    return String(value === null || value === undefined ? '' : value)
        .replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}

function renderForbidden() {
    topbar.hidden = false;
    renderTopbar({ bare: false }, '/admin');
    appEl.innerHTML =
        '<div class="empty" style="margin-top:40px">' +
        '<div class="empty-icon">🚫</div>' +
        '<h3>没有访问权限</h3>' +
        '<p>管理后台只对管理员开放。当前账号是学生，请用 admin 账号登录后再试。</p>' +
        '<a class="btn btn-primary" href="#/items">回到首页</a>' +
        '</div>';
}

async function render() {
    const { path, query } = parseHash();
    const matched = matchRoute(path);

    if (!matched) {
        go('/items');
        return;
    }

    const route = matched.route;

    if (!route.pub && !session.token) {
        go('/login');
        return;
    }
    if (route.admin && !session.isAdmin) {
        renderForbidden();
        return;
    }

    renderTopbar(route, path);
    document.title = (route.title ? route.title + ' · ' : '') + '校园失物招领';
    window.scrollTo({ top: 0, behavior: 'auto' });

    try {
        await route.view({ el: appEl, params: matched.params, query });
    } catch (err) {
        console.error('[route]', path, err);
        appEl.innerHTML = errorState(err && err.message ? err.message : '页面渲染出错');
    }
}

onUnauthorized(() => {
    toast('登录已过期，请重新登录', 'warn');
    go('/login');
});

window.addEventListener('hashchange', render);

if (!location.hash) {
    location.hash = '#/items';
} else {
    render();
}
