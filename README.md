# 校园失物招领 · School-Finding

完整项目打包在 **School-Finding.zip** 里，解压后是标准的 Spring Boot 工程。

## 包含什么

- 后端：Spring Boot 3.2.5 + MyBatis + MySQL，23 个接口
- 前端：静态单页应用（学生端 + 管理端，11 个页面），随后端一起发布，不需要 Node
- 数据库：`sql/init.sql` 建库建表 + 种子数据（8 条信息、3 条认领）
- 脚本：`start.bat` / `stop.bat` / `db.bat`
- 文档：`README.md`、`功能清单.md`、`docs/screenshots/` 界面截图

## 三步跑起来

```powershell
# 1. 建库（密码换成你自己的）
mysql -uroot -p你的密码 < School-Finding/sql/init.sql

# 2. 启动
cd School-Finding
mvn spring-boot:run
```

3. 打开 <http://localhost:8080/>，学生账号 `student1 / 123456`，管理员 `admin / 123456`

需要 JDK 17+、MySQL 8、Maven（也可以直接用 IDEA 打开运行 `SchoolFindingApplication`）。

详细说明（接口清单、状态机、匹配规则、常见问题）见压缩包里的 `README.md`。