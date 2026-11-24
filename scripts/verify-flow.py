#!/usr/bin/env python3
"""在隔离测试实例执行真实 API 与浏览器验收，凭据只写入被忽略的 .local。"""
import argparse
import concurrent.futures
import datetime as dt
import json
from pathlib import Path
import secrets
import struct
import sys
import traceback
import urllib.error
import urllib.parse
import urllib.request
import zlib

ROOT = Path(__file__).resolve().parents[1]
CHECKS = []


def check(condition, label):
    if not condition:
        raise AssertionError(label)
    CHECKS.append(label)
    print("通过 " + label, flush=True)


class Client:
    def __init__(self, base, token=""):
        self.base = base.rstrip("/")
        self.token = token

    def call(self, path, method="GET", body=None, expected=0):
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = self.token
        payload = None
        if body is not None:
            payload = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(self.base + urllib.parse.quote(path, safe="/?=&%"), payload, headers, method=method)
        try:
            response = urllib.request.urlopen(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        result = json.loads(response.read())
        if expected is not None and result["code"] != expected:
            # 响应错误信息不包含请求体、令牌或密码。
            raise AssertionError(f"{method} {path} 返回 {result['code']}，预期 {expected}，{result.get('msg')}")
        return result if expected is None else result.get("data")

    def upload(self, content, filename="item.png", mime="image/png", expected=0, field="file"):
        boundary = "SchoolFinding" + secrets.token_hex(12)
        payload = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{filename}\"\r\nContent-Type: {mime}\r\n\r\n".encode()
                   + content + f"\r\n--{boundary}--\r\n".encode())
        headers = {"Content-Type": "multipart/form-data; boundary=" + boundary}
        if self.token:
            headers["Authorization"] = self.token
        request = urllib.request.Request(self.base + "/api/uploads", payload, headers)
        try:
            response = urllib.request.urlopen(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        result = json.loads(response.read())
        check(result["code"] == expected, "图片上传响应符合预期 " + str(expected))
        return result.get("data")


def admin_credentials(path):
    text = path.read_text(encoding="utf-8-sig")
    try:
        values = json.loads(text)
    except json.JSONDecodeError:
        values = {}
        for line in text.splitlines():
            for separator in ("=", ":", "："):
                if separator in line:
                    key, value = line.split(separator, 1)
                    values[key.strip()] = value.strip()
                    break
    normalized = {key.lower(): value for key, value in values.items()}
    username = next((normalized[k] for k in ("admin_username", "username", "用户名", "账号") if k in normalized), "admin")
    password = next((normalized[k] for k in ("admin_password", "password", "密码") if k in normalized), None)
    if not password:
        raise RuntimeError("管理员凭据文件缺少 password/ADMIN_PASSWORD/密码 字段")
    return username, password


def login(base, username, password):
    data = Client(base).call("/api/auth/login", "POST", {"username": username, "password": password})
    return Client(base, data["token"])


def png_fixture():
    """生成实际解码和重编码的 PNG，测试不依赖外部图片或网络。"""
    width, height = 640, 480
    pixels = bytearray()
    for y in range(height):
        pixels.append(0)
        for x in range(width):
            wallet = 160 < x < 480 and 130 < y < 350
            strap = 390 < x < 510 and 205 < y < 270
            pixels.extend((153, 99, 55) if wallet or strap else (232, 239, 232))
    def chunk(kind, content):
        return struct.pack(">I", len(content)) + kind + content + struct.pack(">I", zlib.crc32(kind + content) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(bytes(pixels))) + chunk(b"IEND", b"")


def item_body(title, kind="FOUND", image=None):
    return {"type": kind, "title": title, "description": "图书馆二楼找到的棕色钱包，已交值班室保管。",
            "category": "生活用品", "location": "图书馆二楼", "happenTime": "2026-11-20 10:30:00",
            "color": "棕色", "contact": "qa-campus-contact", "hiddenFeature": "内侧有蓝色小熊贴纸",
            "verifyQuestion": "内侧贴纸是什么图案？", "verifyAnswer": "蓝色小熊", "imageUrl": image}


def navigate(page, base, route):
    target = base + "/#" + route
    if page.url == target:
        page.reload()
    else:
        page.goto(target)
    page.wait_for_load_state("networkidle")


def ui_login(page, base, username, password):
    navigate(page, base, "/login")
    form = page.locator("#login-form")
    form.locator('[name="username"]').fill(username)
    form.locator('[name="password"]').fill(password)
    with page.expect_response(lambda response: response.url.endswith("/api/auth/login")) as result:
        form.get_by_role("button", name="登录", exact=True).click()
    check(result.value.json()["code"] == 0, "浏览器登录成功")
    page.wait_for_url("**/#/items")
    page.wait_for_load_state("networkidle")


def capture(page, path):
    page.wait_for_function("document.querySelectorAll('#toasts .toast').length === 0")
    page.screenshot(path=str(path), full_page=True)


def ui_register(page, base, username, password):
    navigate(page, base, "/login")
    page.locator('[data-tab="register"]').click()
    form = page.locator("#register-form")
    for name, value in {"username": username, "password": password, "confirm": password, "contact": "qa-original-contact"}.items():
        form.locator(f'[name="{name}"]').fill(value)
    with page.expect_response(lambda response: response.url.endswith("/api/auth/register")) as result:
        form.get_by_role("button", name="注册并登录").click()
    check(result.value.json()["code"] == 0, "浏览器注册并自动登录")
    page.wait_for_url("**/#/items")
    page.wait_for_load_state("networkidle")
    return Client(base, result.value.json()["data"]["token"])


def ui_publish(page, base, body, fixture):
    navigate(page, base, "/publish?type=" + body["type"])
    with page.expect_response(lambda response: response.url.endswith("/api/uploads")) as uploaded:
        page.locator("#item-photo").set_input_files(str(fixture))
    check(uploaded.value.json()["code"] == 0, "浏览器真实PNG上传")
    page.get_by_text("照片已上传，提交信息后保存", exact=True).wait_for()
    form = page.locator("#item-form")
    for name, value in body.items():
        if name in ("type", "imageUrl"):
            continue
        form.locator(f'[name="{name}"]').fill(value.replace(" ", "T")[:16] if name == "happenTime" else value)
    with page.expect_response(lambda response: response.url.endswith("/api/items") and response.request.method == "POST") as created:
        form.get_by_role("button", name="提交审核").click()
    result = created.value.json()
    check(result["code"] == 0, "浏览器带图发布进入待审核")
    page.wait_for_url("**/#/my/items")
    return result["data"]


def ui_approve_item(page, base, item_id):
    navigate(page, base, "/admin/items?status=PENDING")
    page.locator(f'[data-pass="{item_id}"]').click()
    with page.expect_response(lambda response: response.url.endswith(f"/api/admin/items/{item_id}/review")) as reviewed:
        page.get_by_role("button", name="通过并公开", exact=True).click()
    check(reviewed.value.json()["code"] == 0, "浏览器管理员审核信息")
    page.wait_for_load_state("networkidle")


def browser_secondary_flow(args, state, owner, applicant, admin_page, fixture):
    """检查驳回重投、关闭重开和取消申请的可操作页面入口。"""
    extra = ui_publish(owner, args.base_url, item_body(state["prefix"] + " 补充描述钱包"), fixture)
    item_id = extra["id"]
    navigate(admin_page, args.base_url, "/admin/items?status=PENDING")
    admin_page.locator(f'[data-reject="{item_id}"]').click()
    admin_page.locator("#prompt-input").fill("请补充保管地点")
    with admin_page.expect_response(lambda response: response.url.endswith(f"/api/admin/items/{item_id}/review")) as rejected:
        admin_page.get_by_role("dialog").get_by_role("button", name="确定", exact=True).click()
    check(rejected.value.json()["data"]["status"] == "REJECTED", "浏览器驳回信息并保存意见")
    navigate(owner, args.base_url, "/my/items")
    owner.locator(f'[data-edit="{item_id}"]').click()
    owner.locator('#item-form [name="description"]').fill("钱包已放置图书馆值班室，核对后可领取。")
    with owner.expect_response(lambda response: response.url.endswith(f"/api/items/{item_id}") and response.request.method == "PUT") as edited:
        owner.get_by_role("button", name="保存并重新送审", exact=True).click()
    check(edited.value.json()["data"]["status"] == "PENDING", "浏览器编辑驳回信息并重新送审")
    ui_approve_item(admin_page, args.base_url, item_id)
    navigate(owner, args.base_url, "/my/items")
    owner.locator(f'[data-close="{item_id}"]').click()
    with owner.expect_response(lambda response: response.url.endswith(f"/api/items/{item_id}/close")) as closed:
        owner.get_by_role("button", name="确认关闭", exact=True).click()
    check(closed.value.json()["data"]["status"] == "CLOSED", "浏览器关闭自己的公开信息")
    with owner.expect_response(lambda response: response.url.endswith(f"/api/items/{item_id}/reopen")) as reopened:
        owner.locator(f'[data-reopen="{item_id}"]').click()
    check(reopened.value.json()["data"]["status"] == "PENDING", "浏览器重新发布回到待审核")
    navigate(owner, args.base_url, f"/items/{state['lost']}")
    owner.locator('[data-act="claim"]').click()
    owner.locator('[name="proof"]').fill("我找到棕色钱包，内侧有蓝色小熊贴纸。")
    owner.locator('[name="answer"]').fill("蓝色小熊")
    with owner.expect_response(lambda response: response.url.endswith(f"/api/items/{state['lost']}/claims")) as submitted:
        owner.locator("[data-submit]").click()
    check(submitted.value.json()["code"] == 0, "浏览器通过失物详情提交找到物品线索")
    claim_id = submitted.value.json()["data"]["id"]
    navigate(owner, args.base_url, "/my/claims")
    owner.locator(f'[data-cancel="{claim_id}"]').click()
    with owner.expect_response(lambda response: response.url.endswith(f"/api/claims/{claim_id}/cancel")) as cancelled:
        owner.get_by_role("dialog", name="取消认领申请？").get_by_role("button", name="取消申请", exact=True).click()
    check(cancelled.value.json()["code"] == 0, "浏览器取消待审申请")
    navigate(applicant, args.base_url, "/admin")
    applicant.get_by_text("没有访问权限", exact=True).wait_for()
    check(True, "学生直达管理路由展示权限反馈")
    navigate(applicant, args.base_url, "/items/999999999")
    applicant.get_by_text("信息不存在", exact=True).wait_for()
    check(True, "不存在的详情展示真实错误反馈")


def browser_flow(args, state, admin_user, admin_password, fixture):
    from playwright.sync_api import sync_playwright
    screenshots = ROOT / "docs/screenshots"
    screenshots.mkdir(parents=True, exist_ok=True)
    errors = []
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        contexts = [browser.new_context(viewport={"width": 1440, "height": 1000}) for _ in range(3)]
        owner, applicant, admin_page = [context.new_page() for context in contexts]
        for page in (owner, applicant, admin_page):
            page.on("pageerror", lambda error: errors.append(str(error)))
            page.on("console", lambda message: errors.append(message.text) if message.type == "error" else None)
        navigate(owner, args.base_url, "/login")
        capture(owner, screenshots / "login.png")
        owner.locator('#login-form [name="username"]').fill(state["owner"])
        owner.locator('#login-form [name="password"]').fill("invalid-login-password")
        with owner.expect_response(lambda response: response.url.endswith("/api/auth/login")) as invalid_login:
            owner.locator('#login-form button[type="submit"]').click()
        check(invalid_login.value.json()["code"] == 401, "浏览器错误登录凭据得到真实失败反馈")
        student = ui_register(owner, args.base_url, state["owner"], state["password"])
        claimant = ui_register(applicant, args.base_url, state["applicant"], state["password"])
        check(student.call("/api/me/items")["total"] == 0 and claimant.call("/api/me/claims") == [], "新注册账号发布与认领均为空")
        ui_login(admin_page, args.base_url, admin_user, admin_password)
        found = ui_publish(owner, args.base_url, item_body(state["prefix"] + " 棕色钱包"), fixture)
        lost = ui_publish(applicant, args.base_url, item_body(state["prefix"] + " 棕色钱包", "LOST"), fixture)
        state.update(found=found["id"], lost=lost["id"], image=found["imageUrl"])
        ui_approve_item(admin_page, args.base_url, found["id"])
        ui_approve_item(admin_page, args.base_url, lost["id"])
        navigate(applicant, args.base_url, "/items")
        capture(applicant, screenshots / "items.png")
        applicant.locator('[name="keyword"]').fill("绝不匹配的筛选词" + secrets.token_hex(4))
        applicant.get_by_role("button", name="筛选", exact=True).click()
        applicant.get_by_text("还没有符合条件的信息", exact=True).wait_for()
        applicant.get_by_role("link", name="重置", exact=True).click()
        applicant.locator(".item-card").first.wait_for()
        check(applicant.locator('[name="keyword"]').input_value() == "", "无结果筛选及重置恢复真实列表")
        navigate(applicant, args.base_url, f"/items/{found['id']}")
        applicant.locator('[data-act="claim"]').wait_for()
        check(applicant.locator("img").first.evaluate("image => image.complete && image.naturalWidth > 0"), "详情图片真实加载")
        matches = claimant.call(f"/api/items/{found['id']}/matches")
        check(any(row["itemId"] == lost["id"] for row in matches), "真实失物与拾物匹配推荐")
        capture(applicant, screenshots / "detail.png")
        applicant.locator('[data-act="claim"]').click()
        applicant.locator('[name="proof"]').fill("我的棕色钱包，内侧有蓝色小熊贴纸。")
        applicant.locator('[name="answer"]').fill("蓝色小熊")
        with applicant.expect_response(lambda response: response.url.endswith(f"/api/items/{found['id']}/claims")) as claimed:
            applicant.locator("[data-submit]").click()
        check(claimed.value.json()["code"] == 0, "浏览器提交真实认领")
        claim_id = claimed.value.json()["data"]["id"]
        state["claim"] = claim_id
        navigate(admin_page, args.base_url, "/admin/claims")
        admin_page.locator(f'[data-detail="{claim_id}"]').click()
        admin_page.get_by_text("认领申请详情", exact=True).wait_for()
        capture(admin_page, screenshots / "admin-claim.png")
        admin_page.get_by_role("dialog", name="认领申请详情").locator("[data-pass]").click()
        with admin_page.expect_response(lambda response: response.url.endswith(f"/api/admin/claims/{claim_id}/review")) as approved:
            admin_page.get_by_role("dialog", name="通过这份认领申请？").get_by_role("button", name="通过", exact=True).click()
        check(approved.value.json()["code"] == 0, "浏览器核验并通过认领")
        navigate(admin_page, args.base_url, "/admin/claims?status=APPROVED")
        admin_page.locator(f'[data-handover="{claim_id}"]').click()
        with admin_page.expect_response(lambda response: response.url.endswith(f"/api/admin/claims/{claim_id}/handover")) as handed:
            admin_page.get_by_role("button", name="确认交接", exact=True).click()
        check(handed.value.json()["data"]["status"] == "HANDED", "浏览器交接完成")
        navigate(applicant, args.base_url, "/my/claims")
        applicant.get_by_text("已完成交接", exact=False).wait_for()
        browser_secondary_flow(args, state, owner, applicant, admin_page, fixture)
        navigate(owner, args.base_url, "/profile")
        owner.locator('#profile-form [name="contact"]').fill("qa-updated-contact")
        with owner.expect_response(lambda response: response.url.endswith("/api/me") and response.request.method == "PUT") as profile:
            owner.locator('#profile-form button[type="submit"]').click()
        check(profile.value.json()["data"]["contact"] == "qa-updated-contact", "浏览器修改本人资料")
        owner.reload(wait_until="networkidle")
        check(owner.locator('#profile-form [name="contact"]').input_value() == "qa-updated-contact", "刷新恢复已保存资料")
        navigate(admin_page, args.base_url, "/admin")
        capture(admin_page, screenshots / "admin-dashboard.png")
        for page, routes in ((owner, ["/items", "/publish", "/my/items", "/my/claims", "/profile", f"/items/{found['id']}"]),
                             (admin_page, ["/admin", "/admin/items", "/admin/claims", "/admin/logs"])):
            page.set_viewport_size({"width": 375, "height": 812})
            for route in routes:
                navigate(page, args.base_url, route)
                check(page.evaluate("document.documentElement.scrollWidth <= innerWidth"), "375px无横向溢出 " + route)
            if page == owner:
                navigate(page, args.base_url, "/items")
                capture(page, screenshots / "mobile.png")
        old_token = owner.evaluate("localStorage.getItem('sf.token')")
        owner.locator("[data-logout]").click()
        owner.wait_for_url("**/#/login")
        Client(args.base_url, old_token).call("/api/me", expected=401)
        check(True, "浏览器退出后服务端撤销原令牌")
        check(not errors, "正常浏览器流程控制台无错误 " + ",".join(errors[:3]))
        browser.close()
    return login(args.base_url, state["owner"], state["password"]), claimant


def api_checks(args, state, admin, owner, applicant, fixture):
    public = Client(args.base_url)
    public.call("/api/items", expected=401)
    owner.call("/api/admin/statistics", expected=403)
    check(True, "未登录与学生访问管理接口均受限")
    profile = owner.call("/api/me")
    check("password" not in profile, "当前用户响应不返回密码哈希")
    owner.call("/api/me", "PUT", {"contact": "x" * 101}, expected=400)
    public.call("/api/auth/register", "POST", {"username": " a  ", "password": state["password"]}, expected=400)
    owner.call("/api/items", "POST", {}, expected=400)
    owner.call("/api/items", "POST", dict(item_body("未来时间"), happenTime="2099-01-01 01:00:00"), expected=400)
    owner.call("/api/items", "POST", dict(item_body("坏时间"), happenTime="nonsense"), expected=400)
    owner.call("/api/items", "POST", dict(item_body("单边问题"), verifyAnswer=None), expected=400)
    check(True, "资料超长、缺失字段、未来时间、坏时间及不完整验证信息返回400")
    owner.upload(b"<script>alert(1)</script>", "fake.png", expected=400)
    owner.upload(b"", expected=400)
    owner.upload(fixture.read_bytes(), expected=400, field="wrong-field")
    owner.upload(b"x" * (5 * 1024 * 1024 + 1), expected=400)
    image = owner.upload(fixture.read_bytes())["url"]
    applicant.call("/api/items", "POST", item_body("他人图片", image=image), expected=400)
    owner.call("/api/items", "POST", item_body("远程图片", image="https://example.com/image.png"), expected=400)
    check(True, "禁止引用他人照片与任意外部图片")
    item = owner.call("/api/items", "POST", item_body(state["prefix"] + " 并发钱包", image=image))
    item_id = item["id"]
    applicant.call(f"/api/items/{item_id}", expected=404)
    applicant.call(f"/api/items/{item_id}", "PUT", item_body("越权编辑"), expected=403)
    owner.call(f"/api/items/{item_id}/close", "POST", expected=400)
    admin.call(f"/api/admin/items/{item_id}/review", "POST", {"pass": False, "opinion": ""}, expected=400)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        item_reviews = list(pool.map(lambda _: admin.call(f"/api/admin/items/{item_id}/review", "POST", {"pass": True, "opinion": "验收公开"}, expected=None), range(2)))
    check(sorted(row["code"] for row in item_reviews) == [0, 400], "同一信息并发审核仅一个成功")
    detail = applicant.call(f"/api/items/{item_id}")
    check(all(not detail.get(field) for field in ("contact", "hiddenFeature", "verifyAnswer")), "他人公开详情隐私脱敏")
    listed = applicant.call("/api/items?size=50")["records"]
    check(all(not row.get(field) for row in listed for field in ("contact", "hiddenFeature", "verifyAnswer")), "公开列表隐私脱敏")
    owner.call(f"/api/items/{item_id}/claims", "POST", {"proof": "本人不能认领", "answer": "蓝色小熊"}, expected=400)
    applicant.call(f"/api/items/{item_id}/claims", "POST", {"proof": "遗漏回答"}, expected=400)
    claim_body = {"proof": "真实并发认领证明", "answer": "蓝色小熊"}
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        submissions = list(pool.map(lambda _: applicant.call(f"/api/items/{item_id}/claims", "POST", claim_body, expected=None), range(2)))
    check(sorted(row["code"] for row in submissions) == [0, 400], "同人同物并发提交仅一个成功")
    first = next(row["data"] for row in submissions if row["code"] == 0)
    outsider_name = state["prefix"] + "c"
    outsider_data = public.call("/api/auth/register", "POST", {"username": outsider_name, "password": state["password"]})
    outsider = Client(args.base_url, outsider_data["token"])
    second = outsider.call(f"/api/items/{item_id}/claims", "POST", claim_body)
    outsider.call(f"/api/claims/{first['id']}/cancel", "POST", expected=403)
    private_claim = applicant.call("/api/me/claims")
    check(all(not row.get(key) for row in private_claim for key in ("itemHiddenFeature", "itemVerifyAnswer")), "申请人列表不泄露参考答案和隐藏特征")
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        approvals = list(pool.map(lambda claim: admin.call(f"/api/admin/claims/{claim['id']}/review", "POST", {"pass": True, "opinion": "并发核验"}, expected=None), [first, second]))
    check(sorted(row["code"] for row in approvals) == [0, 400], "不同申请并发审批仅一个通过")
    winner = next(row["data"] for row in approvals if row["code"] == 0)
    owner.call(f"/api/items/{item_id}/close", "POST", expected=400)
    check(True, "已通过申请阻止发布者关闭")
    admin.call(f"/api/admin/claims/{winner['id']}/revoke?opinion=验收撤销", "POST")
    owner.call(f"/api/items/{item_id}/close", "POST")
    owner.call(f"/api/items/{item_id}/reopen", "POST")
    admin.call(f"/api/admin/items/{item_id}/review", "POST", {"pass": True, "opinion": "重开核验"})
    reopened_claim = applicant.call(f"/api/items/{item_id}/claims", "POST", claim_body)
    applicant.call(f"/api/claims/{reopened_claim['id']}/cancel", "POST")
    check(True, "撤销、关闭自动结束待审申请、重开送审和取消申请")
    # 审批与关闭真实竞争，两个事务必须得到一致结果。
    racing_claim = applicant.call(f"/api/items/{item_id}/claims", "POST", claim_body)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        closing = pool.submit(owner.call, f"/api/items/{item_id}/close", "POST", None, None)
        approving = pool.submit(admin.call, f"/api/admin/claims/{racing_claim['id']}/review", "POST", {"pass": True}, None)
        race_results = [closing.result(), approving.result()]
    check(sorted(row["code"] for row in race_results) == [0, 400], "关闭与审批并发只允许一个状态转移成功")
    actual_item = admin.call(f"/api/items/{item_id}")
    actual_claim = admin.call(f"/api/admin/claims/{racing_claim['id']}")
    check((actual_item["status"], actual_claim["status"]) in [("CLOSED", "REJECTED"), ("PUBLIC", "APPROVED")], "关闭审批竞争后数据库状态一致")
    if "claim" in state:
        before = admin.call("/api/admin/logs?limit=200")
        admin.call(f"/api/admin/claims/{state['claim']}/handover", "POST", expected=400)
        after = admin.call("/api/admin/logs?limit=200")
        check(len(before) == len(after), "重复交接拒绝且不新增日志")
    statistics = admin.call("/api/admin/statistics")
    check(all(key in statistics for key in ("publicFoundCount", "claimedFoundCount", "pendingClaimCount", "categoryCounts", "locationCounts")), "统计与图表数据来自真实接口")


def database_checks(args, state):
    import pymysql
    credentials = json.loads((ROOT / ".local/mysql/credentials.json").read_text(encoding="utf-8-sig"))
    connection = pymysql.connect(host="127.0.0.1", port=3307, user="root", password=credentials["RootPassword"], database="school_finding_test", charset="utf8mb4")
    with connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT contact,password FROM sys_user WHERE username=%s", (state["owner"],))
            row = cursor.fetchone()
            check(row is not None and row[1].startswith("$2") and row[1] != state["password"], "数据库保存BCrypt密码哈希")
            if "claim" in state:
                check(row[0] == "qa-updated-contact", "数据库独立验证资料保存")
                cursor.execute("SELECT status,image_url FROM item WHERE id=%s", (state["found"],))
                check(cursor.fetchone() == ("CLAIMED", state["image"]), "数据库独立验证物品交接和照片关联")
                cursor.execute("SELECT status FROM claim WHERE id=%s", (state["claim"],))
                check(cursor.fetchone()[0] == "HANDED", "数据库独立验证认领交接")
                cursor.execute("SELECT COUNT(*) FROM op_log WHERE action='CLAIM_HANDOVER' AND target_id=%s", (state["claim"],))
                check(cursor.fetchone()[0] == 1, "数据库仅有一条交接日志")
            cursor.execute("SELECT COUNT(*) FROM item WHERE type='FOUND' AND status='PUBLIC'")
            public_found = cursor.fetchone()[0]
            cursor.execute("SELECT COUNT(*) FROM item WHERE type='FOUND' AND status='CLAIMED'")
            claimed_found = cursor.fetchone()[0]
    return public_found, claimed_found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8081", help="隔离测试实例地址，默认8081")
    parser.add_argument("--admin-credentials", type=Path, default=ROOT / ".local/admin-credentials.txt")
    parser.add_argument("--api-only", action="store_true", help="只运行真实API与数据库检查")
    parser.add_argument("--persistence-only", action="store_true", help="服务重启后重新登录检查上次浏览器流程持久化")
    args = parser.parse_args()
    if not args.base_url.rstrip("/").endswith(":8081"):
        parser.error("验收只允许隔离测试端口8081，避免写入业务库")
    username, password = admin_credentials(args.admin_credentials)
    state_path = ROOT / (".local/qa-api-state.json" if args.api_only else ".local/qa-state.json")
    state_path.parent.mkdir(exist_ok=True)
    health = Client(args.base_url).call("/api/health")
    check(health["database"] == "UP", "真实数据库健康检查")
    admin = login(args.base_url, username, password)
    if args.persistence_only:
        state = json.loads(state_path.read_text(encoding="utf-8"))
        owner = login(args.base_url, state["owner"], state["password"])
        check(owner.call("/api/me")["contact"] == "qa-updated-contact", "重启后重新登录资料持久化")
        check(owner.call(f"/api/items/{state['found']}")["status"] == "CLAIMED", "重启后物品状态持久化")
        check(admin.call(f"/api/admin/claims/{state['claim']}")["status"] == "HANDED", "重启后交接记录持久化")
        with urllib.request.urlopen(args.base_url + state["image"], timeout=10) as photo:
            check(photo.status == 200 and photo.read().startswith(b"\x89PNG"), "重启后照片文件持久化")
    else:
        stamp = dt.datetime.now().strftime("%m%d%H%M%S")
        prefix = "qa" + stamp + secrets.token_hex(2)
        state = {"prefix": prefix, "owner": prefix + "a", "applicant": prefix + "b", "password": secrets.token_urlsafe(16)}
        fixture = ROOT / ".local/qa-wallet.png"
        fixture.write_bytes(png_fixture())
        if args.api_only:
            accounts = []
            for name in (state["owner"], state["applicant"]):
                account = Client(args.base_url).call("/api/auth/register", "POST", {"username": name, "password": state["password"]})
                accounts.append(Client(args.base_url, account["token"]))
            owner, applicant = accounts
        else:
            owner, applicant = browser_flow(args, state, username, password, fixture)
        state_path.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")
        api_checks(args, state, admin, owner, applicant, fixture)
    db_public, db_claimed = database_checks(args, state)
    stats = admin.call("/api/admin/statistics")
    check(stats["publicFoundCount"] == db_public and stats["claimedFoundCount"] == db_claimed, "统计数字与独立数据库查询一致")
    result_path = ROOT / ".local/qa-results.json"
    result_path.write_text(json.dumps({"time": dt.datetime.now().isoformat(), "mode": "persistence" if args.persistence_only else "api" if args.api_only else "full", "checks": CHECKS}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"验收成功，共 {len(CHECKS)} 项。测试数据保留在隔离库，结果保存在 .local/qa-results.json。")


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    try:
        main()
    except Exception as failure:
        print("验收失败 " + str(failure), file=sys.stderr)
        traceback.print_exc()
        sys.exit(1)
