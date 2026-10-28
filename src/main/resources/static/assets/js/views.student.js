/**
 * 校园失物招领 · 学生端视图
 * 登录注册 / 公开列表 / 详情与匹配 / 发布编辑 / 我的发布 / 我的认领
 */

import { api, session, toast, go, openModal, confirmBox, bindPager, errorState } from './core.js';
import {
    esc, escLines, relTime, fmtTime, fmtDate, typePill, itemStatusPill, claimStatusPill,
    itemCard, emptyState, loadingHtml, pagerHtml, toInputTime, fromInputTime,
    CATEGORIES, itemStatusText
} from './tpl.js';

const str = v => (v === null || v === undefined ? '' : String(v).trim());

function pageHead(title, sub, actions) {
    return '<div class="page-head">' +
        '<div><h1 class="page-title">' + esc(title) + '</h1>' +
        (sub ? '<p class="page-sub">' + sub + '</p>' : '') + '</div>' +
        (actions ? '<div class="row">' + actions + '</div>' : '') +
        '</div>';
}

function field(label, control, opts) {
    const o = opts || {};
    return '<div class="field' + (o.span ? ' span-2' : '') + '">' +
        '<label>' + esc(label) + (o.required ? '<span class="req">*</span>' : '') + '</label>' +
        control +
        (o.hint ? '<span class="hint">' + esc(o.hint) + '</span>' : '') +
        '</div>';
}

function categoryDatalist() {
    return '<datalist id="category-list">' +
        CATEGORIES.map(c => '<option value="' + esc(c) + '"></option>').join('') +
        '</datalist>';
}

/* ================================ 登录 / 注册 ================================ */

export async function loginView(ctx) {
    if (session.token) {
        go('/items');
        return;
    }

    ctx.el.innerHTML =
        '<div class="auth"><div class="auth-grid">' +
        '<section class="auth-art">' +
        '<div class="brand" style="color:#fff"><span class="brand-mark" style="background:rgba(255,255,255,.18);box-shadow:none">' +
        '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">' +
        '<circle cx="11" cy="11" r="7"></circle><path d="m20 20-3.5-3.5"></path></svg></span>' +
        '<span class="brand-text">校园失物招领</span></div>' +
        '<h1>丢了东西？<br>捡到东西？<br>都在这里对接。</h1>' +
        '<p>发布失物或拾物 → 管理员审核 → 系统按类别、标题、地点、时间算相似度推荐 → 线上认领 → 审核通过后线下交接。</p>' +
        '<div class="auth-badges">' +
        '<div class="auth-badge"><i>✓</i><span>公开信息自动脱敏，联系方式只有本人和管理员可见</span></div>' +
        '<div class="auth-badge"><i>✓</i><span>同一物品只会有一份认领申请通过</span></div>' +
        '<div class="auth-badge"><i>✓</i><span>审核、撤销、交接全程留操作日志</span></div>' +
        '</div>' +
        '<div class="demo">演示账号（先跑 <b>sql/init.sql</b>）<br>' +
        '<b>student1 / 123456</b> · 学生　<b>student2 / 123456</b> · 学生<br>' +
        '<b>admin / 123456</b> · 管理员</div>' +
        '</section>' +

        '<section class="auth-form">' +
        '<div><h2 class="auth-title">欢迎回来</h2>' +
        '<p class="auth-sub">登录后即可发布、认领和管理信息</p></div>' +
        '<div class="tabs" id="auth-tabs">' +
        '<button type="button" data-tab="login" class="active">登录</button>' +
        '<button type="button" data-tab="register">注册</button>' +
        '</div>' +

        '<form id="login-form" class="stack">' +
        field('用户名', '<input class="input" name="username" required autocomplete="username" placeholder="student1">') +
        field('密码', '<input class="input" name="password" type="password" required autocomplete="current-password" placeholder="123456">') +
        '<button class="btn btn-primary btn-block" type="submit">登录</button>' +
        '</form>' +

        '<form id="register-form" class="stack hidden">' +
        field('用户名', '<input class="input" name="username" required minlength="4" maxlength="20" autocomplete="username" placeholder="4~20 位，字母或数字">', { hint: '注册后默认为学生角色' }) +
        field('密码', '<input class="input" name="password" type="password" required minlength="6" maxlength="32" autocomplete="new-password" placeholder="6~32 位">') +
        field('确认密码', '<input class="input" name="confirm" type="password" required minlength="6" maxlength="32" autocomplete="new-password" placeholder="再输一次">') +
        field('联系方式', '<input class="input" name="contact" maxlength="100" placeholder="手机号或微信号（选填）">') +
        '<button class="btn btn-primary btn-block" type="submit">注册并登录</button>' +
        '</form>' +
        '</section></div></div>';

    const tabs = ctx.el.querySelector('#auth-tabs');
    const loginForm = ctx.el.querySelector('#login-form');
    const registerForm = ctx.el.querySelector('#register-form');

    tabs.addEventListener('click', e => {
        const btn = e.target.closest('button[data-tab]');
        if (!btn) return;
        tabs.querySelectorAll('button').forEach(b => b.classList.toggle('active', b === btn));
        const isLogin = btn.dataset.tab === 'login';
        loginForm.classList.toggle('hidden', !isLogin);
        registerForm.classList.toggle('hidden', isLogin);
        (isLogin ? loginForm : registerForm).querySelector('input').focus();
    });

    loginForm.addEventListener('submit', async e => {
        e.preventDefault();
        const btn = loginForm.querySelector('button[type="submit"]');
        btn.disabled = true;
        btn.textContent = '登录中…';
        try {
            const vo = await api('/api/auth/login', {
                method: 'POST',
                body: { username: str(loginForm.username.value), password: loginForm.password.value }
            });
            session.save(vo);
            toast('欢迎回来，' + vo.username);
            go('/items');
        } catch (err) {
            toast(err.message, 'error');
            btn.disabled = false;
            btn.textContent = '登录';
        }
    });

    registerForm.addEventListener('submit', async e => {
        e.preventDefault();
        const username = str(registerForm.username.value);
        const password = registerForm.password.value;
        if (username.length < 4 || username.length > 20) {
            toast('用户名长度需为 4~20 位', 'warn');
            return;
        }
        if (password.length < 6 || password.length > 32) {
            toast('密码长度需为 6~32 位', 'warn');
            return;
        }
        if (password !== registerForm.confirm.value) {
            toast('两次输入的密码不一致', 'warn');
            return;
        }
        const btn = registerForm.querySelector('button[type="submit"]');
        btn.disabled = true;
        btn.textContent = '注册中…';
        try {
            const vo = await api('/api/auth/register', {
                method: 'POST',
                body: { username, password, contact: str(registerForm.contact.value) || null }
            });
            session.save(vo);
            toast('注册成功，已自动登录');
            go('/items');
        } catch (err) {
            toast(err.message, 'error');
            btn.disabled = false;
            btn.textContent = '注册并登录';
        }
    });
}

/* ================================ 公开列表 ================================ */

export async function listView(ctx) {
    const el = ctx.el;
    const q = ctx.query || {};
    const type = q.type === 'LOST' || q.type === 'FOUND' ? q.type : '';
    const filters = {
        type,
        category: str(q.category),
        location: str(q.location),
        keyword: str(q.keyword),
        page: Math.max(1, parseInt(q.page, 10) || 1),
        size: 9
    };

    const toHash = over => {
        const next = Object.assign({}, filters, over || {});
        return '/items' + buildListQuery(next);
    };

    el.innerHTML =
        pageHead('失物招领',
            '共 <b id="total-count">…</b> 条已公开信息，按发布时间倒序',
            '<a class="btn btn-primary" href="#/publish">＋ 发布信息</a>') +
        '<form class="toolbar" id="filter-form">' +
        '<div class="seg" id="type-seg">' +
        '<button type="button" data-type="" class="' + (type === '' ? 'active' : '') + '">全部</button>' +
        '<button type="button" data-type="LOST" class="' + (type === 'LOST' ? 'active' : '') + '">失物</button>' +
        '<button type="button" data-type="FOUND" class="' + (type === 'FOUND' ? 'active' : '') + '">拾物</button>' +
        '</div>' +
        '<input class="input w-key" name="keyword" value="' + esc(filters.keyword) + '" placeholder="搜索标题或描述" aria-label="关键词">' +
        '<input class="input" name="location" value="' + esc(filters.location) + '" placeholder="地点" aria-label="地点">' +
        '<input class="input" name="category" list="category-list" value="' + esc(filters.category) + '" placeholder="类别" aria-label="类别">' +
        categoryDatalist() +
        '<button class="btn btn-primary" type="submit">筛选</button>' +
        '<a class="btn btn-ghost" href="#/items">重置</a>' +
        '</form>' +
        '<div id="list-area">' + loadingHtml('正在加载…') + '</div>';

    const listArea = el.querySelector('#list-area');

    let data;
    try {
        data = await api('/api/items', {
            query: {
                type: filters.type,
                category: filters.category,
                location: filters.location,
                keyword: filters.keyword,
                page: filters.page,
                size: filters.size
            }
        });
    } catch (err) {
        listArea.innerHTML = errorState(err.message);
        return;
    }

    el.querySelector('#total-count').textContent = data.total;

    if (!data.records.length) {
        listArea.innerHTML = emptyState('🔍', '还没有符合条件的信息',
            '换个关键词或清空筛选看看，也可以自己发布一条失物/拾物。',
            '<a class="btn btn-primary" href="#/publish">发布信息</a>');
        return;
    }

    listArea.innerHTML = '<div class="grid">' + data.records.map(itemCard).join('') + '</div>' +
        pagerHtml({ page: data.page, size: data.size, total: data.total });

    el.querySelector('#type-seg').addEventListener('click', e => {
        const btn = e.target.closest('button[data-type]');
        if (!btn) return;
        go(toHash({ type: btn.dataset.type, page: 1 }));
    });

    el.querySelector('#filter-form').addEventListener('submit', e => {
        e.preventDefault();
        const form = e.currentTarget;
        go(toHash({
            keyword: str(form.keyword.value),
            location: str(form.location.value),
            category: str(form.category.value),
            page: 1
        }));
    });

    bindPager(el, page => go(toHash({ page })));
}

function buildListQuery(f) {
    const params = new URLSearchParams();
    if (f.type) params.set('type', f.type);
    if (f.category) params.set('category', f.category);
    if (f.location) params.set('location', f.location);
    if (f.keyword) params.set('keyword', f.keyword);
    if (f.page && f.page > 1) params.set('page', f.page);
    const qs = params.toString();
    return qs ? '?' + qs : '';
}

/* ================================ 详情 ================================ */

export async function detailView(ctx) {
    const el = ctx.el;
    const id = ctx.params.id;

    el.innerHTML = loadingHtml('正在加载信息…');

    let item;
    try {
        item = await api('/api/items/' + encodeURIComponent(id));
    } catch (err) {
        el.innerHTML = errorState(err.message);
        return;
    }

    const me = session.user;
    const isOwner = !!me && item.publisherId === me.id;
    const isAdmin = session.isAdmin;
    const canEdit = isOwner && (item.status === 'PENDING' || item.status === 'REJECTED');
    const canClose = isOwner && item.status === 'PUBLIC';
    const canReopen = isOwner && item.status === 'CLOSED';
    const canClaim = !isOwner && item.status === 'PUBLIC';
    const canSeeMatches = item.status === 'PUBLIC' || isOwner || isAdmin;

    let matches = [];
    if (canSeeMatches) {
        try {
            matches = await api('/api/items/' + id + '/matches');
        } catch (err) {
            matches = [];
        }
    }

    const meta = [
        ['类别', esc(item.category || '—')],
        ['地点', esc(item.location || '—')],
        ['发生时间', esc(fmtTime(item.happenTime)) + (relTime(item.happenTime) ? ' · ' + esc(relTime(item.happenTime)) : '')],
        item.color ? ['颜色', esc(item.color)] : null,
        ['发布者', esc(item.publisherName || '—')],
        ['发布时间', esc(fmtTime(item.createdAt))]
    ].filter(Boolean);

    const metaHtml = meta.map(pair =>
        '<div><dt>' + pair[0] + '</dt><dd>' + pair[1] + '</dd></div>'
    ).join('');

    const sideInfo = [
        ['当前状态', itemStatusPill(item.status)],
        item.contact ? ['联系方式', '<span class="mono">' + esc(item.contact) + '</span>'] : null,
        item.reviewOpinion ? ['审核意见', esc(item.reviewOpinion)] : null
    ].filter(Boolean);

    const actions = [
        canClaim ? '<button class="btn btn-primary" data-act="claim">我要认领</button>' : '',
        canEdit ? '<button class="btn btn-ghost" data-act="edit">编辑并重新送审</button>' : '',
        canClose ? '<button class="btn btn-ghost" data-act="close">关闭信息</button>' : '',
        canReopen ? '<button class="btn btn-ghost" data-act="reopen">重新发布</button>' : '',
        canSeeMatches ? '<button class="btn btn-soft" data-act="matches">查看匹配推荐</button>' : ''
    ].filter(Boolean);

    const matchHtml = matches.length
        ? '<div class="match-list">' + matches.map(m =>
            '<div class="match">' +
            '<div class="score" style="--p:' + Math.max(0, Math.min(100, m.score)) + '"><span>' + m.score + '</span></div>' +
            '<div class="match-body">' +
            '<div class="match-title"><a href="#/items/' + m.itemId + '">' + esc(m.title) + '</a></div>' +
            '<div class="item-meta">' + typePill(m.type) +
            '<span>📍 ' + esc(m.location || '—') + '</span>' +
            '<span>🕒 ' + esc(relTime(m.happenTime)) + '</span></div>' +
            '<div class="reasons">' + (m.reasons || []).map(r => '<span class="reason">' + esc(r) + '</span>').join('') + '</div>' +
            '</div></div>').join('') + '</div>'
        : '<div class="note">暂时没有 60 分以上的匹配信息。等更多同学发布后可以再来看。</div>';

    el.innerHTML =
        '<div class="crumb"><a href="#/items">失物招领</a><span>/</span><span>详情</span></div>' +
        '<div class="detail">' +
        '<div class="detail-main">' +
        '<section class="card card-pad detail-head">' +
        '<div class="row">' + typePill(item.type) + itemStatusPill(item.status) +
        '<span class="muted small">编号 #' + item.id + '</span></div>' +
        '<h1 class="detail-title">' + esc(item.title) + '</h1>' +
        '<dl class="meta-grid">' + metaHtml + '</dl>' +
        '</section>' +

        '<section class="card"><div class="card-head"><span class="card-title">详细描述</span></div>' +
        '<div class="card-body"><div class="prose">' + escLines(item.description) + '</div></div></section>' +

        (item.verifyQuestion && item.status === 'PUBLIC'
            ? '<div class="note note-info"><div><strong>认领验证问题：</strong>' + esc(item.verifyQuestion) +
            '<div class="small muted" style="margin-top:4px">提交认领时一并回答，管理员会据此核对身份。</div></div></div>'
            : '') +

        (item.reviewOpinion && item.status === 'REJECTED'
            ? '<div class="note note-warn"><div><strong>审核意见：</strong>' + esc(item.reviewOpinion) +
            '<div class="small" style="margin-top:4px">按意见修改后可以重新提交审核。</div></div></div>'
            : '') +

        (canSeeMatches
            ? '<section class="card" id="matches"><div class="card-head">' +
            '<div><div class="card-title">匹配推荐</div>' +
            '<div class="small muted">规则相似度（满分 100，≥60 分展示，最多 5 条），不是找回概率</div></div>' +
            (matches.length ? '<span class="pill pill-brand">' + matches.length + ' 条</span>' : '') +
            '</div><div class="card-body">' + matchHtml + '</div></section>'
            : '') +
        '</div>' +

        '<aside class="detail-side">' +
        (actions.length
            ? '<section class="card card-pad stack"><div class="card-title">可执行操作</div>' +
            '<div class="action-stack">' + actions.join('') + '</div></section>'
            : '') +
        '<section class="card card-pad stack"><div class="card-title">' +
        (isOwner ? '我的信息' : '信息概览') + '</div>' +
        sideInfo.map(pair => '<div class="row-between"><span class="muted small">' + pair[0] + '</span><span>' + pair[1] + '</span></div>').join('') +
        (isOwner ? '<div class="divider"></div><div class="small muted">这是你发布的信息，公开列表中不会显示你的联系方式。</div>' : '') +
        '</section>' +
        '</aside></div>';

    const onAct = (name, handler) => {
        const btn = el.querySelector('[data-act="' + name + '"]');
        if (btn) btn.addEventListener('click', handler);
    };

    onAct('claim', () => openClaimModal(item));
    onAct('matches', () => {
        const section = el.querySelector('#matches');
        if (section) section.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
    onAct('edit', () => go('/items/' + item.id + '/edit'));
    onAct('close', async () => {
        const ok = await confirmBox({
            title: '关闭这条信息？',
            message: '关闭后信息会从公开列表下架，之后可以随时重新发布并再次送审。',
            okText: '确认关闭',
            danger: true
        });
        if (!ok) return;
        try {
            await api('/api/items/' + item.id + '/close', { method: 'POST' });
            toast('已关闭');
            detailView(ctx);
        } catch (err) {
            toast(err.message, 'error');
        }
    });
    onAct('reopen', async () => {
        const ok = await confirmBox({
            title: '重新发布？',
            message: '信息会回到待审核状态，管理员通过后重新公开。',
            okText: '重新发布'
        });
        if (!ok) return;
        try {
            await api('/api/items/' + item.id + '/reopen', { method: 'POST' });
            toast('已重新提交审核');
            detailView(ctx);
        } catch (err) {
            toast(err.message, 'error');
        }
    });
}

function openClaimModal(item) {
    openModal({
        title: '提交认领申请',
        width: 580,
        body:
            '<div class="stack">' +
            '<div class="note note-info"><div>你正在认领：<strong>' + esc(item.title) + '</strong>' +
            '<div class="small muted" style="margin-top:3px">ID #' + item.id + ' · ' + esc(item.location || '') + '</div></div></div>' +
            (item.verifyQuestion
                ? '<div class="note"><div><strong>验证问题：</strong>' + esc(item.verifyQuestion) + '</div></div>'
                : '') +
            field('证明文字', '<textarea class="textarea" name="proof" maxlength="1000" rows="4" ' +
                'placeholder="写清楚只有失主/拾主才知道的细节，例如颜色、划痕、贴纸、里面装了什么"></textarea>',
                { required: true, hint: '管理员会拿它和物品的隐藏特征核对，不超过 1000 字' }) +
            field('验证问题回答', '<input class="input" name="answer" maxlength="100" placeholder="选填，最多 100 字">') +
            '</div>',
        footer: '<button class="btn btn-ghost" data-close>取消</button>' +
            '<button class="btn btn-primary" data-submit>提交申请</button>',
        onMount(panel, close) {
            const proof = panel.querySelector('[name="proof"]');
            const answer = panel.querySelector('[name="answer"]');
            const btn = panel.querySelector('[data-submit]');
            setTimeout(() => proof.focus(), 60);

            btn.addEventListener('click', async () => {
                if (!str(proof.value)) {
                    toast('证明文字不能为空', 'warn');
                    proof.focus();
                    return;
                }
                btn.disabled = true;
                btn.textContent = '提交中…';
                try {
                    await api('/api/items/' + item.id + '/claims', {
                        method: 'POST',
                        body: { proof: str(proof.value), answer: str(answer.value) || null }
                    });
                    close();
                    toast('认领申请已提交，等待管理员审核');
                    go('/my/claims');
                } catch (err) {
                    toast(err.message, 'error');
                    btn.disabled = false;
                    btn.textContent = '提交申请';
                }
            });
        }
    });
}

/* ================================ 发布 / 编辑 ================================ */

export async function publishView(ctx) {
    renderItemForm(ctx, null);
}

export async function editView(ctx) {
    const el = ctx.el;
    el.innerHTML = loadingHtml('正在加载…');
    let item;
    try {
        item = await api('/api/items/' + encodeURIComponent(ctx.params.id));
    } catch (err) {
        el.innerHTML = errorState(err.message);
        return;
    }
    const me = session.user;
    if (!me || item.publisherId !== me.id) {
        el.innerHTML = errorState('只能编辑自己发布的信息');
        return;
    }
    if (item.status !== 'PENDING' && item.status !== 'REJECTED') {
        el.innerHTML = pageHead('无法编辑', '') +
            '<div class="note note-warn"><div>当前状态是「' + esc(itemStatusText(item.status)) +
            '」，只有待审核或已驳回的信息可以编辑。已公开的信息请先关闭，再重新发布。</div></div>' +
            '<div class="row" style="margin-top:16px"><a class="btn btn-ghost" href="#/items/' + item.id + '">返回详情</a></div>';
        return;
    }
    renderItemForm(ctx, item);
}

function renderItemForm(ctx, item) {
    const editing = !!(item && item.id);
    const data = item || { type: 'FOUND' };
    const nowLocal = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    const timeValue = data.happenTime ? toInputTime(data.happenTime) : nowLocal;

    ctx.el.innerHTML =
        '<div class="crumb"><a href="#/my/items">我的发布</a><span>/</span><span>' +
        (editing ? '编辑信息' : '发布信息') + '</span></div>' +
        pageHead(editing ? '编辑并重新送审' : '发布失物 / 拾物',
            editing
                ? '保存后状态会回到「待审核」，管理员重新通过后才会公开'
                : '提交后进入待审核，管理员通过后才会出现在公开列表') +
        '<form id="item-form" class="stack" style="max-width:880px">' +
        '<section class="card card-pad"><div class="form-grid">' +
        '<div class="field span-2"><label>信息类型<span class="req">*</span></label>' +
        '<div class="seg" id="type-seg">' +
        '<button type="button" data-type="LOST" class="' + (data.type === 'LOST' ? 'active' : '') + '">失物 · 我丢了东西</button>' +
        '<button type="button" data-type="FOUND" class="' + (data.type === 'FOUND' ? 'active' : '') + '">拾物 · 我捡到东西</button>' +
        '</div><input type="hidden" name="type" value="' + esc(data.type || 'FOUND') + '"></div>' +

        field('标题', '<input class="input" name="title" required maxlength="100" value="' + esc(data.title || '') +
            '" placeholder="一句话说清丢/捡了什么，例如：白色充电宝">', { required: true, span: true }) +

        field('类别', '<input class="input" name="category" required list="category-list" maxlength="50" value="' +
            esc(data.category || '') + '" placeholder="例如：电子设备">' + categoryDatalist(), { required: true }) +

        field('地点', '<input class="input" name="location" required maxlength="100" value="' +
            esc(data.location || '') + '" placeholder="例如：第三教学楼302教室">', { required: true }) +

        field('发生时间', '<input class="input" name="happenTime" type="datetime-local" required value="' +
            esc(timeValue) + '">', { required: true, hint: '丢失或捡到的时间' }) +

        field('颜色', '<input class="input" name="color" maxlength="30" value="' + esc(data.color || '') +
            '" placeholder="选填，例如：黑色">', { hint: '颜色不参与打分，只写进匹配理由' }) +

        field('联系方式', '<input class="input" name="contact" required maxlength="100" value="' +
            esc(data.contact || '') + '" placeholder="手机号 / 微信号">', { required: true, hint: '不会出现在公开列表' }) +

        field('详细描述', '<textarea class="textarea" name="description" required maxlength="1000" rows="5" ' +
            'placeholder="描述物品特征、当时的情形、现在放在哪里等">' + esc(data.description || '') + '</textarea>',
            { required: true, span: true, hint: '最多 1000 字' }) +
        '</div></section>' +

        '<section class="card card-pad"><div class="card-title" style="margin-bottom:4px">核验信息（选填）</div>' +
        '<p class="small muted" style="margin-bottom:14px">认领时用来核对身份，公开列表里不会出现。</p>' +
        '<div class="form-grid">' +
        field('隐藏特征', '<input class="input" name="hiddenFeature" maxlength="200" value="' +
            esc(data.hiddenFeature || '') + '" placeholder="只有真正的失主/拾主才知道的细节">', { span: true }) +
        field('验证问题', '<input class="input" name="verifyQuestion" maxlength="200" value="' +
            esc(data.verifyQuestion || '') + '" placeholder="例如：充电宝背面贴了什么？">') +
        field('验证答案', '<input class="input" name="verifyAnswer" maxlength="200" value="' +
            esc(data.verifyAnswer || '') + '" placeholder="仅管理员可见">') +
        '</div></section>' +

        '<div class="form-actions">' +
        '<a class="btn btn-ghost" href="' + (editing ? '#/items/' + data.id : '#/my/items') + '">取消</a>' +
        '<button class="btn btn-primary" type="submit">' + (editing ? '保存并重新送审' : '提交审核') + '</button>' +
        '</div></form>';

    const form = ctx.el.querySelector('#item-form');
    const seg = ctx.el.querySelector('#type-seg');
    const typeInput = form.querySelector('[name="type"]');

    seg.addEventListener('click', e => {
        const btn = e.target.closest('button[data-type]');
        if (!btn) return;
        typeInput.value = btn.dataset.type;
        seg.querySelectorAll('button').forEach(b => b.classList.toggle('active', b === btn));
    });

    form.addEventListener('submit', async e => {
        e.preventDefault();
        const btn = form.querySelector('button[type="submit"]');
        const body = {
            type: typeInput.value,
            title: str(form.title.value),
            description: str(form.description.value),
            category: str(form.category.value),
            location: str(form.location.value),
            happenTime: fromInputTime(form.happenTime.value),
            color: str(form.color.value) || null,
            contact: str(form.contact.value),
            hiddenFeature: str(form.hiddenFeature.value) || null,
            verifyQuestion: str(form.verifyQuestion.value) || null,
            verifyAnswer: str(form.verifyAnswer.value) || null
        };
        if (!body.happenTime) {
            toast('请选择发生时间', 'warn');
            form.happenTime.focus();
            return;
        }
        btn.disabled = true;
        btn.textContent = editing ? '保存中…' : '提交中…';
        try {
            if (editing) {
                await api('/api/items/' + data.id, { method: 'PUT', body });
                toast('已保存，重新进入待审核');
            } else {
                await api('/api/items', { method: 'POST', body });
                toast('发布成功，等待管理员审核');
            }
            go('/my/items');
        } catch (err) {
            toast(err.message, 'error');
            btn.disabled = false;
            btn.textContent = editing ? '保存并重新送审' : '提交审核';
        }
    });
}

/* ================================ 我的发布 ================================ */

export async function myItemsView(ctx) {
    const el = ctx.el;
    const status = str(ctx.query.status);
    const page = Math.max(1, parseInt(ctx.query.page, 10) || 1);

    const chips = [
        { value: '', text: '全部' },
        { value: 'PENDING', text: '待审核' },
        { value: 'PUBLIC', text: '已公开' },
        { value: 'REJECTED', text: '已驳回' },
        { value: 'CLOSED', text: '已关闭' },
        { value: 'CLAIMED', text: '已领取' }
    ].map(c => '<button type="button" class="chip' + (status === c.value ? ' active' : '') +
        '" data-status="' + c.value + '">' + c.text + '</button>').join('');

    el.innerHTML = pageHead('我的发布', '这里只有你自己能看到全部字段（含联系方式）',
        '<a class="btn btn-primary" href="#/publish">＋ 发布信息</a>') +
        '<div class="chips" id="status-chips" style="margin-bottom:16px">' + chips + '</div>' +
        '<div id="list-area">' + loadingHtml() + '</div>';

    el.querySelector('#status-chips').addEventListener('click', e => {
        const btn = e.target.closest('button[data-status]');
        if (!btn) return;
        go('/my/items' + (btn.dataset.status ? '?status=' + btn.dataset.status : ''));
    });

    const area = el.querySelector('#list-area');

    let data;
    try {
        data = await api('/api/me/items', { query: { status, page, size: 10 } });
    } catch (err) {
        area.innerHTML = errorState(err.message);
        return;
    }

    if (!data.records.length) {
        area.innerHTML = emptyState('📝', '这里还空着', '发布一条失物或拾物信息，管理员通过后就会出现在公开列表。',
            '<a class="btn btn-primary" href="#/publish">去发布</a>');
        return;
    }

    const rows = data.records.map(it => {
        const acts = ['<a class="btn btn-sm btn-ghost" href="#/items/' + it.id + '">详情</a>'];
        if (it.status === 'PENDING' || it.status === 'REJECTED') {
            acts.push('<button class="btn btn-sm btn-soft" data-edit="' + it.id + '">编辑</button>');
        }
        if (it.status === 'PUBLIC') {
            acts.push('<button class="btn btn-sm btn-ghost" data-close="' + it.id + '">关闭</button>');
        }
        if (it.status === 'CLOSED') {
            acts.push('<button class="btn btn-sm btn-soft" data-reopen="' + it.id + '">重新发布</button>');
        }
        return '<tr>' +
            '<td class="cell-title"><a href="#/items/' + it.id + '">' + esc(it.title) + '</a>' +
            (it.status === 'REJECTED' && it.reviewOpinion
                ? '<div class="small" style="color:var(--danger);margin-top:2px">' + esc(it.reviewOpinion) + '</div>'
                : '') + '</td>' +
            '<td>' + typePill(it.type) + '</td>' +
            '<td>' + itemStatusPill(it.status) + '</td>' +
            '<td>' + esc(it.location || '—') + '</td>' +
            '<td class="nowrap muted small">' + esc(fmtTime(it.happenTime)) + '</td>' +
            '<td><div class="row-actions">' + acts.join('') + '</div></td>' +
            '</tr>';
    }).join('');

    area.innerHTML = '<div class="card table-wrap"><table class="table">' +
        '<thead><tr><th>标题</th><th>类型</th><th>状态</th><th>地点</th><th>发生时间</th><th>操作</th></tr></thead>' +
        '<tbody>' + rows + '</tbody></table></div>' +
        pagerHtml({ page: data.page, size: data.size, total: data.total });

    bindPager(el, p => go('/my/items' + buildStatusQuery(status, p)));

    area.addEventListener('click', async e => {
        const edit = e.target.closest('[data-edit]');
        if (edit) {
            go('/items/' + edit.dataset.edit + '/edit');
            return;
        }
        const close = e.target.closest('[data-close]');
        if (close) {
            const ok = await confirmBox({
                title: '关闭这条信息？',
                message: '关闭后从公开列表下架，可随时重新发布。',
                okText: '确认关闭',
                danger: true
            });
            if (!ok) return;
            try {
                await api('/api/items/' + close.dataset.close + '/close', { method: 'POST' });
                toast('已关闭');
                myItemsView(ctx);
            } catch (err) {
                toast(err.message, 'error');
            }
            return;
        }
        const reopen = e.target.closest('[data-reopen]');
        if (reopen) {
            try {
                await api('/api/items/' + reopen.dataset.reopen + '/reopen', { method: 'POST' });
                toast('已重新提交审核');
                myItemsView(ctx);
            } catch (err) {
                toast(err.message, 'error');
            }
        }
    });
}

function buildStatusQuery(status, page) {
    const params = new URLSearchParams();
    if (status) params.set('status', status);
    if (page && page > 1) params.set('page', page);
    const qs = params.toString();
    return qs ? '?' + qs : '';
}

/* ================================ 我的认领 ================================ */

export async function myClaimsView(ctx) {
    const el = ctx.el;
    el.innerHTML = pageHead('我的认领', '只显示你自己提交的申请') + '<div id="list-area">' + loadingHtml() + '</div>';

    const area = el.querySelector('#list-area');
    let claims;
    try {
        claims = await api('/api/me/claims');
    } catch (err) {
        area.innerHTML = errorState(err.message);
        return;
    }

    if (!claims.length) {
        area.innerHTML = emptyState('🙋', '还没有提交过认领',
            '在公开列表里找到自己的东西，进入详情页点「我要认领」。',
            '<a class="btn btn-primary" href="#/items">去看看</a>');
        return;
    }

    area.innerHTML = '<div class="stack">' + claims.map(c => {
        const title = c.itemStatus === 'PUBLIC'
            ? '<a href="#/items/' + c.itemId + '">' + esc(c.itemTitle) + '</a>'
            : esc(c.itemTitle);
        return '<article class="card claim-card">' +
            '<div class="claim-head">' +
            '<div class="row"><span class="claim-title">' + title + '</span>' +
            typePill(c.itemType) + claimStatusPill(c.status) + '</div>' +
            '<span class="muted small">提交于 ' + esc(fmtTime(c.createdAt)) + '</span>' +
            '</div>' +
            '<div class="claim-body">' +
            '<div class="claim-block"><div class="k">我的证明</div><div class="v">' + escLines(c.proof) + '</div></div>' +
            '<div class="claim-block"><div class="k">验证问题回答</div><div class="v">' +
            (c.answer ? escLines(c.answer) : '<span class="muted">未填写</span>') + '</div></div>' +
            '</div>' +
            (c.reviewOpinion
                ? '<div class="note' + (c.status === 'REJECTED' || c.status === 'REVOKED' ? ' note-warn' : '') +
                '"><div><strong>处理意见：</strong>' + esc(c.reviewOpinion) + '</div></div>'
                : '') +
            (c.status === 'HANDED'
                ? '<div class="note note-ok"><div>已完成交接' + (c.handoverByName ? '（经办：' + esc(c.handoverByName) + '）' : '') + '</div></div>'
                : '') +
            (c.status === 'PENDING'
                ? '<div class="row"><button class="btn btn-sm btn-danger" data-cancel="' + c.id + '">取消申请</button></div>'
                : '') +
            '</article>';
    }).join('') + '</div>';

    area.addEventListener('click', async e => {
        const btn = e.target.closest('[data-cancel]');
        if (!btn) return;
        const ok = await confirmBox({
            title: '取消认领申请？',
            message: '取消后可以重新提交，历史记录会保留。',
            okText: '取消申请',
            danger: true
        });
        if (!ok) return;
        try {
            await api('/api/claims/' + btn.dataset.cancel + '/cancel', { method: 'POST' });
            toast('已取消申请');
            myClaimsView(ctx);
        } catch (err) {
            toast(err.message, 'error');
        }
    });
}
