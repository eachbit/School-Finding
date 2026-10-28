/**
 * 校园失物招领 · 纯渲染工具
 * 这里只有「数据 → HTML 字符串」的纯函数，不碰 DOM，便于自测。
 */

export const CATEGORIES = ['雨具', '电子设备', '箱包', '证件', '钥匙', '书籍', '衣物', '卡片', '其他'];

export const ITEM_STATUS = {
    PENDING: { text: '待审核', tone: 'warn' },
    PUBLIC: { text: '已公开', tone: 'ok' },
    REJECTED: { text: '已驳回', tone: 'danger' },
    CLOSED: { text: '已关闭', tone: 'muted' },
    CLAIMED: { text: '已领取', tone: 'brand' }
};

export const CLAIM_STATUS = {
    PENDING: { text: '待审核', tone: 'warn' },
    APPROVED: { text: '已通过', tone: 'brand' },
    REJECTED: { text: '已驳回', tone: 'danger' },
    CANCELLED: { text: '已取消', tone: 'muted' },
    HANDED: { text: '已交接', tone: 'ok' },
    REVOKED: { text: '已撤销', tone: 'danger' }
};

export const ACTION_LABEL = {
    ITEM_REVIEW: '信息审核',
    ITEM_CLOSE: '信息关闭',
    CLAIM_SUBMIT: '提交认领',
    CLAIM_CANCEL: '取消认领',
    CLAIM_REVIEW: '认领审核',
    CLAIM_REVOKE: '撤销认领',
    CLAIM_HANDOVER: '交接确认'
};

export const TARGET_LABEL = { ITEM: '物品', CLAIM: '申请', USER: '用户' };

const ESC_MAP = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

/** 转义，所有用户输入进 HTML 前必须经过它 */
export function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value).replace(/[&<>"']/g, ch => ESC_MAP[ch]);
}

/** 保留换行的文本 */
export function escLines(value) {
    return esc(value).replace(/\r?\n/g, '<br>');
}

export function empty(value) {
    return value === null || value === undefined || String(value).trim() === '';
}

/* ------------------------------ 时间 ------------------------------ */

function parseTime(value) {
    if (!value) return null;
    const normalized = String(value).trim().replace(' ', 'T');
    const d = new Date(normalized);
    return Number.isNaN(d.getTime()) ? null : d;
}

/** 2026-10-22 09:30 */
export function fmtTime(value) {
    if (!value) return '—';
    return String(value).slice(0, 16);
}

/** 2026-10-22 */
export function fmtDate(value) {
    if (!value) return '—';
    return String(value).slice(0, 10);
}

/** 3 小时前 */
export function relTime(value) {
    const d = parseTime(value);
    if (!d) return '';
    const diff = Date.now() - d.getTime();
    if (diff < 0) return '刚刚';
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return '刚刚';
    if (minutes < 60) return minutes + ' 分钟前';
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return hours + ' 小时前';
    const days = Math.floor(hours / 24);
    if (days < 30) return days + ' 天前';
    return fmtDate(value);
}

/** 后端格式 → datetime-local 需要的格式 */
export function toInputTime(value) {
    if (!value) return '';
    return String(value).replace(' ', 'T').slice(0, 16);
}

/** datetime-local → 后端要求的 yyyy-MM-dd HH:mm:ss */
export function fromInputTime(value) {
    if (!value) return '';
    const text = String(value).replace('T', ' ');
    return text.length === 16 ? text + ':00' : text.slice(0, 19);
}

/* ------------------------------ 徽章 ------------------------------ */

export function typePill(type) {
    return type === 'LOST'
        ? '<span class="pill pill-lost"><span class="dot"></span>失物</span>'
        : '<span class="pill pill-found"><span class="dot"></span>拾物</span>';
}

export function typeText(type) {
    return type === 'LOST' ? '失物' : '拾物';
}

export function itemStatusPill(status) {
    const meta = ITEM_STATUS[status] || { text: status || '未知', tone: 'muted' };
    return '<span class="pill pill-' + meta.tone + '">' + esc(meta.text) + '</span>';
}

export function claimStatusPill(status) {
    const meta = CLAIM_STATUS[status] || { text: status || '未知', tone: 'muted' };
    return '<span class="pill pill-' + meta.tone + '">' + esc(meta.text) + '</span>';
}

export function itemStatusText(status) {
    return (ITEM_STATUS[status] || { text: status || '未知' }).text;
}

export function claimStatusText(status) {
    return (CLAIM_STATUS[status] || { text: status || '未知' }).text;
}

export function actionLabel(action) {
    return ACTION_LABEL[action] || action || '操作';
}

/* ------------------------------ 片段 ------------------------------ */

export function loadingHtml(text) {
    return '<div class="loading"><span class="spinner"></span><span>' + esc(text || '正在加载…') + '</span></div>';
}

export function emptyState(icon, title, desc, actionHtml) {
    return '<div class="empty">' +
        '<div class="empty-icon">' + esc(icon || '🗂️') + '</div>' +
        '<h3>' + esc(title) + '</h3>' +
        (desc ? '<p>' + esc(desc) + '</p>' : '') +
        (actionHtml || '') +
        '</div>';
}

export function pagerHtml(state) {
    const total = Number(state.total) || 0;
    const size = Number(state.size) || 10;
    const page = Number(state.page) || 1;
    const pages = Math.max(1, Math.ceil(total / size));
    const from = total === 0 ? 0 : (page - 1) * size + 1;
    const to = Math.min(total, page * size);

    const buttons = [];
    buttons.push('<button data-page="' + (page - 1) + '"' + (page <= 1 ? ' disabled' : '') + '>上一页</button>');

    let start = Math.max(1, page - 2);
    const end = Math.min(pages, start + 4);
    start = Math.max(1, Math.min(start, end - 4));
    for (let i = start; i <= end; i++) {
        buttons.push('<button data-page="' + i + '" class="' + (i === page ? 'active' : '') + '">' + i + '</button>');
    }
    buttons.push('<button data-page="' + (page + 1) + '"' + (page >= pages ? ' disabled' : '') + '>下一页</button>');

    return '<div class="pager">' +
        '<span class="muted small">共 ' + total + ' 条' + (total ? '，当前 ' + from + '–' + to : '') + '</span>' +
        '<div class="pager-btns">' + buttons.join('') + '</div>' +
        '</div>';
}

export function itemCard(item) {
    const id = item.id;
    const foot = item.publisherName
        ? esc(item.publisherName) + ' · ' + esc(fmtDate(item.createdAt))
        : '发布于 ' + esc(fmtDate(item.createdAt));
    return '<article class="item-card">' +
        '<div class="item-card-top">' + typePill(item.type) +
        '<span class="item-cat">' + esc(item.category || '未分类') + '</span></div>' +
        '<h3 class="item-title"><a href="#/items/' + id + '">' + esc(item.title) + '</a></h3>' +
        '<p class="item-desc">' + esc(item.description) + '</p>' +
        '<div class="item-meta">' +
        '<span>📍 ' + esc(item.location || '地点未填') + '</span>' +
        '<span>🕒 ' + esc(relTime(item.happenTime)) + '</span>' +
        '</div>' +
        '<div class="item-foot">' +
        '<span class="nowrap">' + foot + '</span>' +
        '<a href="#/items/' + id + '">查看详情 →</a>' +
        '</div>' +
        '</article>';
}

export function scoreRing(score) {
    const value = Math.max(0, Math.min(100, Number(score) || 0));
    return '<div class="score" style="--p:' + value + '"><span>' + value + '</span></div>';
}

export function categoryOptions(selected) {
    return CATEGORIES.map(c =>
        '<option value="' + esc(c) + '"' + (c === selected ? ' selected' : '') + '></option>'
    ).join('');
}

export function statusChips(options, active, counts) {
    return options.map(opt => {
        const count = counts && counts[opt.value] !== undefined ? counts[opt.value] : null;
        return '<button type="button" class="chip' + (String(active || '') === String(opt.value) ? ' active' : '') + '"' +
            ' data-status="' + esc(opt.value) + '">' + esc(opt.text) +
            (count === null ? '' : ' <span class="n">' + count + '</span>') +
            '</button>';
    }).join('');
}
