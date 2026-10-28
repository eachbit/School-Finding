/**
 * 校园失物招领 · 管理端视图
 * 数据概览 / 信息审核 / 认领处理（审核、撤销、交接）/ 操作日志
 */

import { api, toast, go, openModal, confirmBox, promptBox, bindPager, errorState } from './core.js';
import {
    esc, escLines, fmtTime, relTime, typePill, typeText, itemStatusPill, claimStatusPill,
    emptyState, loadingHtml, pagerHtml, actionLabel, TARGET_LABEL, itemStatusText
} from './tpl.js';

const str = v => (v === null || v === undefined ? '' : String(v).trim());

function pageHead(title, sub, actions) {
    return '<div class="page-head">' +
        '<div><h1 class="page-title">' + esc(title) + '</h1>' +
        (sub ? '<p class="page-sub">' + sub + '</p>' : '') + '</div>' +
        (actions ? '<div class="row">' + actions + '</div>' : '') +
        '</div>';
}

function kv(rows) {
    return '<dl class="kv">' + rows.filter(Boolean).map(pair =>
        '<dt>' + esc(pair[0]) + '</dt><dd>' + pair[1] + '</dd>'
    ).join('') + '</dl>';
}

function block(label, value, fallback) {
    return '<div class="claim-block"><div class="k">' + esc(label) + '</div><div class="v">' +
        (value ? escLines(value) : '<span class="muted">' + esc(fallback || '未填写') + '</span>') +
        '</div></div>';
}

/* ================================ 概览 ================================ */

export async function dashboardView(ctx) {
    const el = ctx.el;
    el.innerHTML = pageHead('管理后台', '审核信息、处理认领、确认交接') + loadingHtml('正在汇总数据…');

    let stats;
    let logs;
    try {
        const result = await Promise.all([
            api('/api/admin/statistics'),
            api('/api/admin/logs', { query: { limit: 6 } })
        ]);
        stats = result[0];
        logs = result[1];
    } catch (err) {
        el.innerHTML = errorState(err.message);
        return;
    }

    el.innerHTML = pageHead('管理后台', '审核信息、处理认领、确认交接') +
        '<div class="stats">' +
        '<div class="stat"><div class="stat-label">📦 公开拾物</div>' +
        '<div class="stat-value">' + stats.publicFoundCount + '</div>' +
        '<div class="stat-foot">状态为已公开的拾物信息</div></div>' +
        '<div class="stat"><div class="stat-label">✅ 已领取拾物</div>' +
        '<div class="stat-value">' + stats.claimedFoundCount + '</div>' +
        '<div class="stat-foot">已完成交接的拾物</div></div>' +
        '<div class="stat"><div class="stat-label">⏳ 待审核认领</div>' +
        '<div class="stat-value">' + stats.pendingClaimCount + '</div>' +
        '<div class="stat-foot">需要你处理的认领申请</div></div>' +
        '</div>' +

        '<div class="detail" style="margin-top:20px">' +
        '<div class="detail-main">' +
        '<section class="card"><div class="card-head"><span class="card-title">最近操作</span>' +
        '<a class="btn btn-sm btn-ghost" href="#/admin/logs">全部日志</a></div>' +
        '<div class="card-body">' +
        (logs.length ? '<div class="timeline">' + logs.map(logItem).join('') + '</div>'
            : '<div class="note">还没有任何操作记录。</div>') +
        '</div></section>' +
        '</div>' +

        '<aside class="detail-side">' +
        '<section class="card card-pad stack"><div class="card-title">快捷操作</div>' +
        '<div class="action-stack">' +
        '<a class="btn btn-primary" href="#/admin/items?status=PENDING">审核待审信息</a>' +
        '<a class="btn btn-ghost" href="#/admin/claims?status=PENDING">处理待审认领</a>' +
        '<a class="btn btn-ghost" href="#/admin/claims?status=APPROVED">确认交接</a>' +
        '<a class="btn btn-soft" href="#/items">浏览公开列表</a>' +
        '</div></section>' +
        '<section class="card card-pad stack"><div class="card-title">审核口径</div>' +
        '<div class="small muted">信息通过后才会进入公开列表；驳回必须填写意见，发布者改完可重新送审。' +
        '认领通过前先核对「申请人证明」与「物品隐藏特征 / 参考答案」。</div></section>' +
        '</aside></div>';
}

function logItem(log) {
    return '<div class="tl-item"><div class="tl-dot"></div><div>' +
        '<div class="tl-head"><span class="tl-title">' + esc(actionLabel(log.action)) + '</span>' +
        '<span class="pill pill-muted">' + esc(TARGET_LABEL[log.targetType] || log.targetType || '') +
        ' #' + esc(log.targetId) + '</span></div>' +
        '<div class="tl-meta">' + esc(log.operatorName || '—') + ' · ' + esc(fmtTime(log.createdAt)) +
        (relTime(log.createdAt) ? ' · ' + esc(relTime(log.createdAt)) : '') + '</div>' +
        (log.opinion ? '<div class="tl-opinion">' + esc(log.opinion) + '</div>' : '') +
        '</div></div>';
}

/* ================================ 信息审核 ================================ */

const ITEM_FILTERS = [
    { value: 'PENDING', text: '待审核' },
    { value: 'PUBLIC', text: '已公开' },
    { value: 'REJECTED', text: '已驳回' },
    { value: 'CLOSED', text: '已关闭' },
    { value: 'CLAIMED', text: '已领取' },
    { value: 'ALL', text: '全部' }
];

export async function itemsView(ctx) {
    const el = ctx.el;
    const q = ctx.query || {};
    const status = ITEM_FILTERS.some(f => f.value === q.status) ? q.status : 'PENDING';
    const type = q.type === 'LOST' || q.type === 'FOUND' ? q.type : '';
    const keyword = str(q.keyword);
    const page = Math.max(1, parseInt(q.page, 10) || 1);

    const toHash = over => {
        const next = Object.assign({ status, type, keyword, page }, over || {});
        const params = new URLSearchParams();
        params.set('status', next.status);
        if (next.type) params.set('type', next.type);
        if (next.keyword) params.set('keyword', next.keyword);
        if (next.page > 1) params.set('page', next.page);
        return '/admin/items?' + params.toString();
    };

    const chips = ITEM_FILTERS.map(f =>
        '<button type="button" class="chip' + (status === f.value ? ' active' : '') +
        '" data-status="' + f.value + '">' + f.text + '</button>').join('');

    el.innerHTML = pageHead('信息审核', '这里能看到联系方式、隐藏特征和验证答案',
        '<a class="btn btn-ghost" href="#/admin">返回概览</a>') +
        '<div class="chips" id="status-chips" style="margin-bottom:14px">' + chips + '</div>' +
        '<form class="toolbar" id="filter-form">' +
        '<select class="select" name="type" aria-label="类型">' +
        '<option value="">全部类型</option>' +
        '<option value="LOST"' + (type === 'LOST' ? ' selected' : '') + '>失物</option>' +
        '<option value="FOUND"' + (type === 'FOUND' ? ' selected' : '') + '>拾物</option>' +
        '</select>' +
        '<input class="input w-key" name="keyword" value="' + esc(keyword) + '" placeholder="搜索标题或描述" aria-label="关键词">' +
        '<button class="btn btn-primary" type="submit">筛选</button>' +
        '<a class="btn btn-ghost" href="#/admin/items?status=PENDING">重置</a>' +
        '</form>' +
        '<div id="table-area">' + loadingHtml() + '</div>';

    el.querySelector('#status-chips').addEventListener('click', e => {
        const btn = e.target.closest('button[data-status]');
        if (!btn) return;
        go(toHash({ status: btn.dataset.status, page: 1 }));
    });

    el.querySelector('#filter-form').addEventListener('submit', e => {
        e.preventDefault();
        const form = e.currentTarget;
        go(toHash({ type: form.type.value, keyword: str(form.keyword.value), page: 1 }));
    });

    const area = el.querySelector('#table-area');
    let data;
    try {
        data = await api('/api/admin/items', {
            query: { status: status === 'ALL' ? '' : status, type, keyword, page, size: 10 }
        });
    } catch (err) {
        area.innerHTML = errorState(err.message);
        return;
    }

    if (!data.records.length) {
        area.innerHTML = emptyState('🗂️', '没有符合条件的信息', '换个状态或清空关键词再试试。');
        return;
    }

    const rows = data.records.map(it => {
        const acts = ['<button class="btn btn-sm btn-ghost" data-detail="' + it.id + '">详情</button>'];
        if (it.status === 'PENDING') {
            acts.push('<button class="btn btn-sm btn-ok" data-pass="' + it.id + '">通过</button>');
            acts.push('<button class="btn btn-sm btn-danger" data-reject="' + it.id + '">驳回</button>');
        }
        return '<tr>' +
            '<td class="cell-title"><a href="#/items/' + it.id + '">' + esc(it.title) + '</a>' +
            '<div class="small muted">' + esc(it.category || '—') + ' · #' + it.id + '</div></td>' +
            '<td>' + typePill(it.type) + '</td>' +
            '<td>' + itemStatusPill(it.status) + '</td>' +
            '<td>' + esc(it.publisherName || '—') + '</td>' +
            '<td class="nowrap muted small">' + esc(fmtTime(it.createdAt)) + '</td>' +
            '<td><div class="row-actions">' + acts.join('') + '</div></td>' +
            '</tr>';
    }).join('');

    area.innerHTML = '<div class="card table-wrap"><table class="table">' +
        '<thead><tr><th>标题</th><th>类型</th><th>状态</th><th>发布者</th><th>发布时间</th><th>操作</th></tr></thead>' +
        '<tbody>' + rows + '</tbody></table></div>' +
        pagerHtml({ page: data.page, size: data.size, total: data.total });

    bindPager(el, p => go(toHash({ page: p })));

    const reload = () => itemsView(ctx);

    area.addEventListener('click', async e => {
        const detail = e.target.closest('[data-detail]');
        if (detail) {
            await openItemModal(Number(detail.dataset.detail), reload);
            return;
        }
        const pass = e.target.closest('[data-pass]');
        if (pass) {
            const ok = await confirmBox({
                title: '通过这条信息？',
                message: '通过后信息会立即出现在公开列表，所有登录学生都能看到（联系方式仍隐藏）。',
                okText: '通过并公开'
            });
            if (!ok) return;
            try {
                await api('/api/admin/items/' + pass.dataset.pass + '/review', {
                    method: 'POST',
                    body: { pass: true, opinion: '信息完整，通过' }
                });
                toast('已通过并公开');
                reload();
            } catch (err) {
                toast(err.message, 'error');
            }
            return;
        }
        const reject = e.target.closest('[data-reject]');
        if (reject) {
            const opinion = await promptBox({
                title: '驳回这条信息',
                label: '审核意见（必填，发布者能看到）',
                placeholder: '例如：描述过于简略，请补充物品特征后重新提交',
                multiline: true,
                required: true
            });
            if (opinion === null) return;
            try {
                await api('/api/admin/items/' + reject.dataset.reject + '/review', {
                    method: 'POST',
                    body: { pass: false, opinion }
                });
                toast('已驳回');
                reload();
            } catch (err) {
                toast(err.message, 'error');
            }
        }
    });
}

async function openItemModal(id, reload) {
    let item;
    try {
        item = await api('/api/items/' + id);
    } catch (err) {
        toast(err.message, 'error');
        return;
    }

    const footerActions = item.status === 'PENDING'
        ? '<button class="btn btn-danger" data-reject>驳回</button>' +
        '<button class="btn btn-primary" data-pass>通过并公开</button>'
        : '<button class="btn btn-ghost" data-close>关闭</button>';

    openModal({
        title: '信息详情',
        width: 680,
        body:
            '<div class="stack">' +
            '<div class="row">' + typePill(item.type) + itemStatusPill(item.status) +
            '<span class="muted small">#' + item.id + '</span></div>' +
            '<h3 style="font-size:18px">' + esc(item.title) + '</h3>' +
            '<div class="prose">' + escLines(item.description) + '</div>' +
            kv([
                ['类别', esc(item.category || '—')],
                ['地点', esc(item.location || '—')],
                ['发生时间', esc(fmtTime(item.happenTime))],
                ['颜色', item.color ? esc(item.color) : '<span class="muted">未填</span>'],
                ['发布者', esc(item.publisherName || '—')],
                ['联系方式', esc(item.contact || '—')],
                item.reviewOpinion ? ['审核意见', esc(item.reviewOpinion)] : null
            ]) +
            '<div class="claim-body" style="grid-template-columns:1fr 1fr">' +
            block('隐藏特征', item.hiddenFeature, '未填写') +
            block('验证问题', item.verifyQuestion, '未设置') +
            block('参考答案', item.verifyAnswer, '未设置') +
            '</div></div>',
        footer: footerActions,
        onMount(panel, close) {
            const doPass = async () => {
                const ok = await confirmBox({
                    title: '通过这条信息？',
                    message: '通过后立即公开。',
                    okText: '通过'
                });
                if (!ok) return;
                try {
                    await api('/api/admin/items/' + item.id + '/review', {
                        method: 'POST',
                        body: { pass: true, opinion: '信息完整，通过' }
                    });
                    toast('已通过并公开');
                    close();
                    reload();
                } catch (err) {
                    toast(err.message, 'error');
                }
            };
            const doReject = async () => {
                const opinion = await promptBox({
                    title: '驳回这条信息',
                    label: '审核意见（必填）',
                    multiline: true,
                    required: true
                });
                if (opinion === null) return;
                try {
                    await api('/api/admin/items/' + item.id + '/review', {
                        method: 'POST',
                        body: { pass: false, opinion }
                    });
                    toast('已驳回');
                    close();
                    reload();
                } catch (err) {
                    toast(err.message, 'error');
                }
            };
            const passBtn = panel.querySelector('[data-pass]');
            const rejectBtn = panel.querySelector('[data-reject]');
            if (passBtn) passBtn.addEventListener('click', doPass);
            if (rejectBtn) rejectBtn.addEventListener('click', doReject);
        }
    });
}

/* ================================ 认领处理 ================================ */

const CLAIM_FILTERS = [
    { value: 'PENDING', text: '待审核' },
    { value: 'APPROVED', text: '已通过' },
    { value: 'HANDED', text: '已交接' },
    { value: 'REJECTED', text: '已驳回' },
    { value: 'REVOKED', text: '已撤销' },
    { value: 'CANCELLED', text: '已取消' },
    { value: 'ALL', text: '全部' }
];

export async function claimsView(ctx) {
    const el = ctx.el;
    const q = ctx.query || {};
    const status = CLAIM_FILTERS.some(f => f.value === q.status) ? q.status : 'PENDING';
    const itemId = str(q.itemId);
    const page = Math.max(1, parseInt(q.page, 10) || 1);

    const toHash = over => {
        const next = Object.assign({ status, itemId, page }, over || {});
        const params = new URLSearchParams();
        params.set('status', next.status);
        if (next.itemId) params.set('itemId', next.itemId);
        if (next.page > 1) params.set('page', next.page);
        return '/admin/claims?' + params.toString();
    };

    const chips = CLAIM_FILTERS.map(f =>
        '<button type="button" class="chip' + (status === f.value ? ' active' : '') +
        '" data-status="' + f.value + '">' + f.text + '</button>').join('');

    el.innerHTML = pageHead('认领处理', '核对证明与隐藏特征，通过后到线下交接',
        '<a class="btn btn-ghost" href="#/admin">返回概览</a>') +
        '<div class="chips" id="status-chips" style="margin-bottom:14px">' + chips + '</div>' +
        '<form class="toolbar" id="filter-form">' +
        '<input class="input" name="itemId" value="' + esc(itemId) + '" inputmode="numeric" placeholder="按物品 ID 筛选" aria-label="物品 ID">' +
        '<button class="btn btn-primary" type="submit">筛选</button>' +
        '<a class="btn btn-ghost" href="#/admin/claims?status=PENDING">重置</a>' +
        '</form>' +
        '<div id="table-area">' + loadingHtml() + '</div>';

    el.querySelector('#status-chips').addEventListener('click', e => {
        const btn = e.target.closest('button[data-status]');
        if (!btn) return;
        go(toHash({ status: btn.dataset.status, page: 1 }));
    });

    el.querySelector('#filter-form').addEventListener('submit', e => {
        e.preventDefault();
        go(toHash({ itemId: str(e.currentTarget.itemId.value), page: 1 }));
    });

    const area = el.querySelector('#table-area');
    const reload = () => claimsView(ctx);

    let data;
    try {
        data = await api('/api/admin/claims', {
            query: {
                status: status === 'ALL' ? '' : status,
                itemId: itemId && /^\d+$/.test(itemId) ? itemId : '',
                page,
                size: 10
            }
        });
    } catch (err) {
        area.innerHTML = errorState(err.message);
        return;
    }

    if (!data.records.length) {
        area.innerHTML = emptyState('📭', '没有符合条件的申请', '切换状态看看，或者等学生提交新的认领。');
        return;
    }

    const rows = data.records.map(c =>
        '<tr>' +
        '<td class="cell-title"><a href="#/items/' + c.itemId + '">' + esc(c.itemTitle) + '</a>' +
        '<div class="small muted">物品 #' + c.itemId + ' · ' + typeText(c.itemType) + '</div></td>' +
        '<td>' + esc(c.applicantName || '—') + '</td>' +
        '<td>' + claimStatusPill(c.status) + '</td>' +
        '<td class="nowrap muted small">' + esc(fmtTime(c.createdAt)) + '</td>' +
        '<td><div class="row-actions">' +
        '<button class="btn btn-sm btn-ghost" data-detail="' + c.id + '">详情</button>' +
        (c.status === 'PENDING'
            ? '<button class="btn btn-sm btn-ok" data-pass="' + c.id + '">通过</button>' +
            '<button class="btn btn-sm btn-danger" data-reject="' + c.id + '">驳回</button>'
            : '') +
        (c.status === 'APPROVED'
            ? '<button class="btn btn-sm btn-primary" data-handover="' + c.id + '">交接</button>'
            : '') +
        '</div></td></tr>').join('');

    area.innerHTML = '<div class="card table-wrap"><table class="table">' +
        '<thead><tr><th>物品</th><th>申请人</th><th>状态</th><th>提交时间</th><th>操作</th></tr></thead>' +
        '<tbody>' + rows + '</tbody></table></div>' +
        pagerHtml({ page: data.page, size: data.size, total: data.total });

    bindPager(el, p => go(toHash({ page: p })));

    area.addEventListener('click', async e => {
        const detail = e.target.closest('[data-detail]');
        if (detail) {
            await openClaimModal(Number(detail.dataset.detail), reload);
            return;
        }
        const pass = e.target.closest('[data-pass]');
        if (pass) {
            await approveClaim(Number(pass.dataset.pass), reload);
            return;
        }
        const reject = e.target.closest('[data-reject]');
        if (reject) {
            await rejectClaim(Number(reject.dataset.reject), reload);
            return;
        }
        const handover = e.target.closest('[data-handover]');
        if (handover) {
            await handoverClaim(Number(handover.dataset.handover), reload);
        }
    });
}

async function approveClaim(id, reload) {
    const ok = await confirmBox({
        title: '通过这份认领申请？',
        message: '通过后物品仍可继续交接；同一物品只能有一份通过的申请，请确保已核对证明与隐藏特征。',
        okText: '通过'
    });
    if (!ok) return;
    try {
        await api('/api/admin/claims/' + id + '/review', { method: 'POST', body: { pass: true, opinion: '核对无误，通过' } });
        toast('已通过申请');
        reload();
    } catch (err) {
        toast(err.message, 'error');
    }
}

async function rejectClaim(id, reload) {
    const opinion = await promptBox({
        title: '驳回这份认领申请',
        label: '驳回理由（必填，申请人能看到）',
        placeholder: '例如：证明与隐藏特征不符，请补充细节',
        multiline: true,
        required: true
    });
    if (opinion === null) return;
    try {
        await api('/api/admin/claims/' + id + '/review', { method: 'POST', body: { pass: false, opinion } });
        toast('已驳回申请');
        reload();
    } catch (err) {
        toast(err.message, 'error');
    }
}

async function handoverClaim(id, reload) {
    const ok = await confirmBox({
        title: '确认已完成线下交接？',
        message: '确认后申请变为「已交接」，物品变为「已领取」，不能重复确认。',
        okText: '确认交接'
    });
    if (!ok) return;
    try {
        await api('/api/admin/claims/' + id + '/handover', { method: 'POST' });
        toast('交接完成');
        reload();
    } catch (err) {
        toast(err.message, 'error');
    }
}

async function revokeClaim(id, reload) {
    const opinion = await promptBox({
        title: '撤销这份已通过的申请',
        label: '撤销原因（必填）',
        placeholder: '例如：申请人放弃领取',
        multiline: true,
        required: true
    });
    if (opinion === null) return;
    try {
        await api('/api/admin/claims/' + id + '/revoke', { method: 'POST', query: { opinion } });
        toast('已撤销申请');
        reload();
    } catch (err) {
        toast(err.message, 'error');
    }
}

async function openClaimModal(id, reload) {
    let claim;
    try {
        claim = await api('/api/admin/claims/' + id);
    } catch (err) {
        toast(err.message, 'error');
        return;
    }

    let footer = '<button class="btn btn-ghost" data-close>关闭</button>';
    if (claim.status === 'PENDING') {
        footer = '<button class="btn btn-ghost" data-close>关闭</button>' +
            '<button class="btn btn-danger" data-reject>驳回</button>' +
            '<button class="btn btn-primary" data-pass>通过申请</button>';
    } else if (claim.status === 'APPROVED') {
        footer = '<button class="btn btn-ghost" data-close>关闭</button>' +
            '<button class="btn btn-danger" data-revoke>撤销</button>' +
            '<button class="btn btn-primary" data-handover>交接确认</button>';
    }

    openModal({
        title: '认领申请详情',
        width: 720,
        body:
            '<div class="stack">' +
            '<div class="row">' + claimStatusPill(claim.status) + typePill(claim.itemType) +
            '<span class="muted small">申请 #' + claim.id + ' · 物品 #' + claim.itemId + '</span></div>' +
            '<h3 style="font-size:18px">' + esc(claim.itemTitle) + '</h3>' +
            kv([
                ['申请人', esc(claim.applicantName || '—')],
                ['物品状态', itemStatusPill(claim.itemStatus)],
                ['提交时间', esc(fmtTime(claim.createdAt))],
                ['审核人', esc(claim.reviewerName || '—')],
                ['交接人', esc(claim.handoverByName || '—')],
                claim.reviewOpinion ? ['处理意见', esc(claim.reviewOpinion)] : null
            ]) +
            '<div class="claim-body" style="grid-template-columns:1fr 1fr">' +
            block('申请人证明', claim.proof) +
            block('验证问题回答', claim.answer, '未填写') +
            block('物品隐藏特征', claim.itemHiddenFeature, '未设置') +
            block('验证参考答案', claim.itemVerifyAnswer, '未设置') +
            '</div>' +
            (claim.itemVerifyQuestion
                ? '<div class="note"><div><strong>验证问题：</strong>' + esc(claim.itemVerifyQuestion) + '</div></div>'
                : '') +
            '</div>',
        footer,
        onMount(panel, close) {
            const bind = (sel, fn) => {
                const btn = panel.querySelector(sel);
                if (btn) btn.addEventListener('click', fn);
            };
            bind('[data-pass]', async () => {
                close();
                await approveClaim(claim.id, reload);
            });
            bind('[data-reject]', async () => {
                close();
                await rejectClaim(claim.id, reload);
            });
            bind('[data-revoke]', async () => {
                close();
                await revokeClaim(claim.id, reload);
            });
            bind('[data-handover]', async () => {
                close();
                await handoverClaim(claim.id, reload);
            });
        }
    });
}

/* ================================ 操作日志 ================================ */

export async function logsView(ctx) {
    const el = ctx.el;
    const limit = [20, 50, 100].includes(Number(ctx.query.limit)) ? Number(ctx.query.limit) : 50;

    el.innerHTML = pageHead('操作日志', '最近 ' + limit + ' 条审核与交接记录',
        '<a class="btn btn-ghost" href="#/admin">返回概览</a>') +
        '<div class="toolbar">' +
        '<div class="seg" id="limit-seg">' +
        [20, 50, 100].map(n => '<button type="button" data-limit="' + n + '" class="' +
            (n === limit ? 'active' : '') + '">最近 ' + n + ' 条</button>').join('') +
        '</div></div>' +
        '<div id="log-area">' + loadingHtml() + '</div>';

    el.querySelector('#limit-seg').addEventListener('click', e => {
        const btn = e.target.closest('button[data-limit]');
        if (btn) go('/admin/logs?limit=' + btn.dataset.limit);
    });

    const area = el.querySelector('#log-area');
    let logs;
    try {
        logs = await api('/api/admin/logs', { query: { limit } });
    } catch (err) {
        area.innerHTML = errorState(err.message);
        return;
    }

    area.innerHTML = logs.length
        ? '<div class="card card-pad"><div class="timeline">' + logs.map(logItem).join('') + '</div></div>'
        : emptyState('🧾', '还没有日志', '审核信息、提交认领、确认交接后这里会出现记录。');
}
