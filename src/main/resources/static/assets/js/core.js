/**
 * 校园失物招领 · 前端核心
 * 会话存储、统一请求、弹窗与轻提示。无第三方依赖。
 */

const TOKEN_KEY = 'sf.token';
const USER_KEY = 'sf.user';

export class ApiError extends Error {
    constructor(code, msg) {
        super(msg || '请求失败');
        this.name = 'ApiError';
        this.code = code;
    }
}

let unauthorizedHandler = null;

/** 由 app.js 注册：token 失效时统一跳登录 */
export function onUnauthorized(fn) {
    unauthorizedHandler = fn;
}

export const session = {
    get token() {
        return localStorage.getItem(TOKEN_KEY) || '';
    },
    get user() {
        try {
            const raw = localStorage.getItem(USER_KEY);
            return raw ? JSON.parse(raw) : null;
        } catch (e) {
            return null;
        }
    },
    get isAdmin() {
        const u = this.user;
        return !!u && u.role === 'ADMIN';
    },
    save(vo) {
        localStorage.setItem(TOKEN_KEY, vo.token);
        localStorage.setItem(USER_KEY, JSON.stringify({
            id: vo.userId,
            username: vo.username,
            role: vo.role,
            contact: vo.contact
        }));
    },
    clear() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
    }
};

/** 跳转到某个 hash 路由（重复跳同一地址也会重新渲染） */
export function go(path) {
    const target = path.charAt(0) === '#' ? path : '#' + path;
    if (location.hash === target) {
        window.dispatchEvent(new Event('hashchange'));
    } else {
        location.hash = target;
    }
}

/** 给分页按钮绑定回调 */
export function bindPager(root, onPage) {
    root.querySelectorAll('.pager-btns button[data-page]').forEach(btn => {
        btn.addEventListener('click', () => {
            if (btn.disabled) return;
            const page = Number(btn.dataset.page);
            if (page >= 1) onPage(page);
        });
    });
}

export function buildQuery(query = {}) {
    const params = new URLSearchParams();
    Object.keys(query).forEach(key => {
        const value = query[key];
        if (value === undefined || value === null || String(value).trim() === '') return;
        params.set(key, value);
    });
    const qs = params.toString();
    return qs ? '?' + qs : '';
}

/**
 * 统一请求。后端永远返回 {code, msg, data}，code !== 0 视为失败。
 * @returns {Promise<any>} data 部分
 */
export async function api(path, options = {}) {
    const { method = 'GET', query, body } = options;
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (session.token) headers.Authorization = session.token;

    let res;
    try {
        res = await fetch(path + buildQuery(query), {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
    } catch (e) {
        throw new ApiError(-1, '连不上后端服务，请确认 Spring Boot 已启动（默认 8080 端口）');
    }

    const text = await res.text();
    let payload = null;
    try {
        payload = text ? JSON.parse(text) : null;
    } catch (e) {
        throw new ApiError(-1, '服务端返回了无法解析的内容');
    }
    if (!payload || typeof payload.code !== 'number') {
        throw new ApiError(-1, '服务端响应格式异常');
    }
    if (payload.code !== 0) {
        if (payload.code === 401) {
            session.clear();
            if (unauthorizedHandler) unauthorizedHandler();
        }
        throw new ApiError(payload.code, payload.msg || '请求失败');
    }
    return payload.data;
}

/* ------------------------------ 轻提示 ------------------------------ */

export function toast(message, type = 'ok') {
    const root = document.getElementById('toasts');
    if (!root) return;
    const el = document.createElement('div');
    el.className = 'toast toast-' + type;
    el.innerHTML = '<span class="toast-dot"></span><span></span>';
    el.lastElementChild.textContent = String(message == null ? '' : message);
    root.appendChild(el);
    while (root.children.length > 3) root.removeChild(root.firstElementChild);
    requestAnimationFrame(() => el.classList.add('in'));
    setTimeout(() => {
        el.classList.remove('in');
        setTimeout(() => el.remove(), 220);
    }, type === 'error' ? 4600 : 3000);
}

/* ------------------------------ 弹窗 ------------------------------ */

const modalStack = [];

if (typeof document !== 'undefined') {
    document.addEventListener('keydown', e => {
        if (e.key !== 'Escape') return;
        const top = modalStack[modalStack.length - 1];
        if (top) top();
    });
}

/**
 * 打开一个弹窗（支持叠层：确认框会盖在详情弹窗之上）。
 * @param {{title: string, body: string, footer?: string, width?: number,
 *          onMount?: (root: HTMLElement, close: () => void) => void,
 *          onClose?: () => void}} opts
 * @returns {() => void} 关闭函数
 */
export function openModal(opts) {
    const root = document.getElementById('modal-root');
    const width = opts.width || 560;

    const mask = document.createElement('div');
    mask.className = 'modal-mask';
    mask.innerHTML =
        '<div class="modal" role="dialog" aria-modal="true" style="max-width:' + width + 'px">' +
        '<header class="modal-head"><h3></h3><button class="icon-btn" data-close aria-label="关闭">×</button></header>' +
        '<div class="modal-body"></div>' +
        '<footer class="modal-foot"></footer>' +
        '</div>';
    root.appendChild(mask);

    const panel = mask.querySelector('.modal');
    mask.querySelector('.modal-head h3').textContent = opts.title || '';
    mask.querySelector('.modal-body').innerHTML = opts.body || '';
    mask.querySelector('.modal-foot').innerHTML = opts.footer || '';

    document.body.classList.add('modal-open');

    let closed = false;
    const close = () => {
        if (closed) return;
        closed = true;
        const index = modalStack.indexOf(close);
        if (index !== -1) modalStack.splice(index, 1);
        mask.remove();
        if (modalStack.length === 0) document.body.classList.remove('modal-open');
        if (opts.onClose) opts.onClose();
    };

    modalStack.push(close);

    mask.addEventListener('mousedown', e => {
        if (e.target === mask) close();
    });
    mask.querySelectorAll('[data-close]').forEach(btn => btn.addEventListener('click', close));

    if (opts.onMount) opts.onMount(panel, close);
    return close;
}

/** 确认框，返回 Promise<boolean> */
export function confirmBox(opts) {
    const danger = !!opts.danger;
    return new Promise(resolve => {
        let settled = false;
        const done = value => {
            if (settled) return;
            settled = true;
            resolve(value);
        };
        openModal({
            title: opts.title || '确认操作',
            width: 430,
            body: '<p class="confirm-text"></p>',
            footer: '<button class="btn btn-ghost" data-cancel>取消</button>' +
                '<button class="btn ' + (danger ? 'btn-danger' : 'btn-primary') + '" data-ok></button>',
            onMount(panel, close) {
                panel.querySelector('.confirm-text').textContent = opts.message || '';
                panel.querySelector('[data-ok]').textContent = opts.okText || '确定';
                panel.querySelector('[data-ok]').onclick = () => {
                    done(true);
                    close();
                };
                panel.querySelector('[data-cancel]').onclick = () => {
                    done(false);
                    close();
                };
            },
            onClose() {
                done(false);
            }
        });
    });
}

/** 单行/多行输入框，返回 Promise<string|null> */
export function promptBox(opts) {
    const multiline = !!opts.multiline;
    return new Promise(resolve => {
        let settled = false;
        const done = value => {
            if (settled) return;
            settled = true;
            resolve(value);
        };
        openModal({
            title: opts.title || '请输入',
            width: 470,
            body:
                '<div class="field">' +
                '<label for="prompt-input"></label>' +
                (multiline
                    ? '<textarea id="prompt-input" class="textarea" maxlength="200" rows="3"></textarea>'
                    : '<input id="prompt-input" class="input" maxlength="200" />') +
                '<span class="hint">最多 200 字</span>' +
                '</div>',
            footer: '<button class="btn btn-ghost" data-cancel>取消</button>' +
                '<button class="btn btn-primary" data-ok>确定</button>',
            onMount(panel, close) {
                const label = panel.querySelector('label');
                const input = panel.querySelector('#prompt-input');
                label.textContent = opts.label || '';
                input.placeholder = opts.placeholder || '';
                input.value = opts.value || '';
                setTimeout(() => input.focus(), 30);

                const submit = () => {
                    const value = input.value.trim();
                    if (opts.required && !value) {
                        input.setAttribute('aria-invalid', 'true');
                        input.focus();
                        toast('这一项必须填写', 'warn');
                        return;
                    }
                    done(value);
                    close();
                };
                panel.querySelector('[data-ok]').onclick = submit;
                panel.querySelector('[data-cancel]').onclick = () => close();
                input.addEventListener('keydown', e => {
                    if (e.key === 'Enter' && !multiline) {
                        e.preventDefault();
                        submit();
                    }
                });
            },
            onClose() {
                done(null);
            }
        });
    });
}

/** 把一段 HTML 渲染为错误页 */
export function errorState(message) {
    return '<div class="empty"><div class="empty-icon">⚠️</div>' +
        '<h3>加载失败</h3><p></p>' +
        '<a class="btn btn-ghost" href="#/items">回到首页</a></div>'
        .replace('<p></p>', '<p>' + String(message).replace(/[&<>"']/g, c => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[c])) + '</p>');
}
