# School-Finding 校园失物招领

学生发布失物或拾物，管理员审核，系统按规则计算相似度并推荐候选，再完成认领审核与实际交接。

项目采用 Spring Boot 3.2.5、MyBatis、MySQL 8 和原生 JavaScript 单页应用。前端随服务发布，开发和部署均可使用同一个可执行 JAR。

## 本机运行

需要 JDK 17+、Maven 3.9+、MySQL 8 和现代浏览器。Windows 脚本会查找 JAVA_HOME、MAVEN_HOME、MYSQL_HOME、PATH 以及已有的本机工具目录。

```powershell
# 构建并启动独立本机数据库和应用
.\scripts\run-local.ps1 -NoBrowser

# 查看健康状态
.\scripts\run-local.ps1 -Action Status

# 停止应用，保留数据
.\scripts\run-local.ps1 -Action Stop
```

也可以使用根目录的 start.bat、stop.bat 和 db.bat。

| 地址 | 用途 |
|---|---|
| http://127.0.0.1:8080/ | 学生端及管理端 |
| http://127.0.0.1:8080/api/health | 应用标识、数据库和表结构健康检查 |
| http://127.0.0.1:8080/doc.html | Knife4j 接口文档 |

首次启动会创建项目专用 MySQL 实例，默认监听 127.0.0.1:3307。业务库为 school_finding_local。脚本根据进程启动时间、命令行和状态文件识别本项目管理的进程。

初始业务表为空。管理员用户名和随机密码保存在本机 `.local/admin-credentials.txt`，学生通过注册页面创建账号。直接使用 Maven 启动时，可以通过 ADMIN_USERNAME 和 ADMIN_PASSWORD 设置首次初始化管理员，已有管理员账号保留原密码。

数据库文件、上传照片、日志和本机凭据位于 `.local/`。这部分文件已加入 Git 忽略规则，重启或重新构建后继续使用。备份时一并保存数据库备份和上传照片目录。

## 使用现有 MySQL

在自己的 MySQL 中创建数据库后，选中目标库，依次执行 `sql/init.sql` 和 `sql/migrate.sql`。前者创建缺失的表，后者补充已有版本的照片字段。两份脚本均可重复执行并保留现有数据。

随后通过环境变量配置连接并启动。

```powershell
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3306'
$env:DB_NAME = 'school_finding'
$env:DB_USERNAME = '你的数据库用户名'
$env:DB_PASSWORD = '你的数据库密码'
$env:ADMIN_USERNAME = 'admin'
$env:ADMIN_PASSWORD = '首次管理员密码，10至64个字符'
mvn spring-boot:run
```

| 配置 | 说明 |
|---|---|
| DB_HOST / DB_PORT / DB_NAME | 数据库主机、端口和库名 |
| DB_USERNAME / DB_PASSWORD | 数据库凭据 |
| ADMIN_USERNAME / ADMIN_PASSWORD | 首次创建管理员的凭据 |
| UPLOAD_DIRECTORY | 照片存储目录，默认 .local/uploads |
| app.token.expire-hours | 登录 token 有效期，默认24小时 |

本机一键脚本使用自己的专用实例和凭据。连接现有 MySQL 时使用 Maven 或可执行 JAR，并提供上述环境变量。

## 页面与能力

| 路由 | 页面及功能 |
|---|---|
| #/login | 登录、学生注册 |
| #/items | 失物拾物列表、关键词、类别、地点、类型筛选和分页 |
| #/items/:id | 真实照片、详情、匹配分数和理由、认领入口 |
| #/publish | 发布失物或拾物、上传照片、设置验证问题 |
| #/items/:id/edit | 待审核或已驳回信息编辑重投 |
| #/my/items | 个人发布记录、状态筛选、关闭和重新发布 |
| #/my/claims | 本人认领记录、证明、处理结果、取消待审申请 |
| #/profile | 个人资料查询和联系方式保存 |
| #/admin | 真实统计、类别与地点分布、最近日志 |
| #/admin/items | 发布审核及隐藏特征核对 |
| #/admin/claims | 认领审核、撤销和实际交接确认 |
| #/admin/logs | 操作日志查询 |

上传接口 `POST /api/uploads` 接受 multipart 的 file 字段，支持5 MB以内的 JPEG 和 PNG，校验图像内容与像素尺寸，重新编码后写入磁盘。物品通过 imageUrl 关联当前用户上传的照片，文件元数据记录在 uploaded_image 表中。

个人资料通过 `PUT /api/me` 保存。退出调用 `POST /api/auth/logout` 撤销当前 token。

所有列表、匹配和统计都读取数据库。空库和无匹配时显示对应空状态，接口失败时提供错误反馈。

## 业务规则

信息发布后为 PENDING，审核通过变为 PUBLIC，完成实际交接后变为 CLAIMED。审核驳回为 REJECTED，发布者关闭为 CLOSED。

认领依次经过 PENDING、APPROVED、HANDED，分别对应待审、审核通过和已交接。待审申请可取消或驳回，已通过但未交接的申请可由管理员撤销。

- 公开查询隐藏联系方式、隐藏特征和验证参考答案，发布者及管理员按权限查看。
- 每个用户对同一物品最多保留一份有效申请。
- 同一物品最多有一个审核通过的申请，交接操作同步更新申请、物品和日志。
- 已有通过申请的物品须先撤销申请再关闭。关闭信息和完成交接时自动结束其他待审申请。
- 重复交接返回业务错误，交接日志只产生一次。
- 状态变更在数据库事务中统一先锁物品，再锁申请，处理并发审批、提交、取消和关闭。

匹配采用100分制，类别40分、标题关键词30分、地点20分、时间10分。24小时内时间项得10分，72小时内得5分。达到60分的候选按分数取前5条，并显示计算理由。分数表示规则相似度。颜色显示在理由中。

登录 token 保存在服务内存中，重启服务后重新登录。前端保存当前会话，刷新时向服务端核对身份。

## 构建与测试

```powershell
mvn package -DskipTests
java -jar target/school-finding-1.0.0.jar
```

Java 集成测试使用独立的 school_finding_test 数据库。使用本机脚本创建的实例时，可在 PowerShell 中加载本机凭据后执行测试。

```powershell
. .\scripts\mysql-local.ps1
$c = Get-LocalDatabaseCredentials
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3307'
$env:DB_USERNAME = 'school_finding_app'
$env:DB_PASSWORD = $c.AppPassword
mvn test
```

浏览器端到端测试使用独立服务8081及测试库。

```powershell
.\scripts\run-local.ps1 -Port 8081 -DatabaseName school_finding_test -UploadDirectory .local/test-uploads -SkipBuild -NoBrowser
python scripts/verify-flow.py --help
python scripts/verify-flow.py --base-url http://127.0.0.1:8081
```

测试通过真实注册、上传、发布、审核和交接接口创建隔离测试记录。测试脚本依赖 Python 3、playwright 和 pymysql，浏览器使用已安装的 Playwright Chromium。具体执行范围和结果见 `docs/test-report.md`。截图来自隔离测试服务中的实际操作。

## 工程目录

```text
src/main/java/com/project/        认证、业务服务、接口与数据库映射
src/main/resources/mapper/        MyBatis SQL
src/main/resources/static/        静态页面、样式与前端逻辑
src/test/                        单元及真实数据库集成测试
sql/init.sql                     空业务表初始化
sql/migrate.sql                  已有版本无损迁移
scripts/                         Windows环境管理及端到端验收
postman/                         参数化接口调试集合
.local/                          本机运行数据，Git忽略
docs/                            开发安排、测试记录及截图
```

School-Finding.zip 提供相同版本的源码包，Git 仓库中的展开源码可直接用于开发和审查。
