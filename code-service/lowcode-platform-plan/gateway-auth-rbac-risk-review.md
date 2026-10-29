# 网关 / 认证 / RBAC 方案风险评审

> 版本：v1.0
> 评审对象：《gateway-auth-rbac-backend-design.md》《gateway-auth-rbac-frontend-design.md》
> 结论：方案总体可行，但存在 **5 项 A 级问题需修订设计后才能实施**，9 项 B 级风险需在实施阶段落地对策，6 项 C 级记录在案。

---

## A 级问题（必须先修订设计）

### A1. 菜单表没有多语言字段（违背平台既有 i18n 体系）

**问题**：`lc_menu.menu_name` 单列中文。而平台已有 `lowcode_lang` / `lowcode_lang_text` 多语言体系，且规范是"多语言由后台配合加载、覆盖面要广"。菜单名是典型的必须多语言的字段（英文用户看到中文菜单）。角色名（lc_role，存量表）同理，但新表必须一开始就做对。

**对策（修订 DDL）**：

```sql
-- lc_menu 增加：
    name_key     VARCHAR(255),     -- 多语言 key，如 menu.system.user，关联 lowcode_lang_text
    -- menu_name 保留为"默认语言兜底值"（key 未命中时显示）
```

- 前端渲染：`t(m.nameKey ?? m.menuName)`，t() 内部走现有 lang 体系，不引入前端 i18n 插件。
- 菜单管理页：名称输入 + 可选多语言 key 选择（从 lowcode_lang_text 已有 key 中选或新建）。
- 角色名：lc_role 是存量表不动，角色管理页展示层后续用 t() 包一层（P4 事项，不阻塞）。

### A2. 菜单无法挂"低代码配置页面"（核心资产没打通）

**问题**：设计中 `lc_menu.component` 只支持 Vue 组件路径（如 `system/UserManageView`），但低代码平台的核心页面是**设计器配置出来、存 `lowcode_page` 表、由渲染器运行时渲染的**，没有 Vue 文件。菜单管理若不支持挂低代码页面，对用户就是半成品。

**对策（修订 lc_menu.component 语义为双模式）**：

| component 值 | 含义 | 前端路由处理 |
|---|---|---|
| `vue:system/UserManageView` | 手写 Vue 页面 | `viewModules['system/UserManageView']` |
| `page:{pageCode}` | 低代码配置页面 | 固定跳转到现有页面渲染路由（PreviewView/运行时渲染路径），携带 pageCode |

- 首字符判 `vue:` / `page:` 前缀分流，兼容裸路径（默认按 vue: 处理）。
- 菜单管理页的"组件路径"输入改为**弹窗选择**（用户既有偏好：配置选择不用手填下拉）——列出 views 下组件 + 页面管理里的已配置页面两组可选。
- 菜单授权后，业务用户登录即可从菜单进入对应低代码页面，与"设计器产出 → 用户使用"闭环。

### A3. Redis 故障策略未定义（网关可用性悬空）

**问题**：网关验签后要查 Redis 黑名单。Redis 没启动/崩溃时网关行为未定义——默认会抛连接异常导致**全站 502**，登录白名单虽不查 Redis 但登录后写入也会失败。

**对策（需拍板，默认建议 fail-closed）**：

| 策略 | 行为 | 代价 |
|---|---|---|
| fail-closed（推荐） | Redis 不可用时非白名单请求一律 401"认证服务不可用" | Redis 成单点，但其挂掉本身就该报警 |
| fail-open | Redis 不可用时跳过黑名单校验（仅验签名） | 已登出/被踢的 token 复活，安全窗口≤2h |

- 配套：WinSW 服务依赖编排（bcd-gateway 依赖 Redis 服务，startdelay）；网关每 30s ping Redis，连续失败打 ERROR 日志便于发现。
- 用户当前 Redis 也是单点（localhost:8004），与 bcd-code 现有用法风险一致，不新增架构复杂度。

### A4. 角色变更时效不一致（JWT 2h vs 权限缓存 10min）

**问题**：JWT 内嵌 `roles` claim，2 小时有效。管理员中途给用户**加/删角色**，存量 token 里还是旧角色，最长 2 小时不生效；而**按钮权限码**走 Redis 缓存只有 10 分钟延迟。两种延迟并存，行为不可预期（收权场景尤其危险：撤了管理员角色的人还能用 2 小时）。

**对策（三选一，需拍板，推荐方案 1）**：

1. **角色变更即失效**：用户/角色权限变更时，把该用户（或该角色下所有用户）的 `auth:online:{jti}` 会话全部写黑名单 → 强制重新登录。实现简单（online 会话里有 userId/roles 索引，Redis SCAN 按 pattern 删除即可），代价是改权限会踢人下线（可接受，改权限本来就是敏感操作）。
2. 网关每请求查 Redis 取实时角色（不信任 JWT roles claim）：无时效问题，但每请求多一次 Redis 查询（~0.5ms，可接受），JWT 退化为纯身份凭证。
3. 接受 2h 延迟：实现最少，但收权不即时，不推荐。

### A5. X-User-* 头信任边界仅靠"端口不对外"约束（安全加固项缺失）

**问题**：bcd-code/bcd-auth 信任网关注入的头。Windows 防火墙默认未必拦截 3015/8010 入站，局域网内任何机器 `curl http://<主机IP>:3015 -H "X-User-Id: admin的ID"` 即可冒充任意用户。`local-debug: true` 若误带入生产则全裸奔。

**对策（修订设计，分两层）**：

- **必做**：bcd-code/bcd-auth 启动时若 `local-debug=true` 打 ERROR 级启动日志 + `/actuator/health` 暴露 `localDebugEnabled` 字段供巡检；生产部署清单写明 3015/8010 设置 `server.address: 127.0.0.1`（WinSW 同机部署网关不受影响）。
- **建议（P3 加固）**：网关注入 `X-Gw-Sign = HMAC-SHA256(uid + timestamp + path, GW_SECRET)`，bcd-code 校验该签名，伪造头彻底无效（防内网误配/防火墙疏漏）。密钥与 JWT secret 分开配置。
- 补充：Windows 防火墙实际加一条入站规则封 3015/8010/8010 之外端口，比代码层更直接，部署文档中固化为检查项。

---

## B 级风险（实施阶段对策）

### B1. 存量数据迁移缺口

- 现有 `lc_role` 已有业务角色（配了 lc_permission_gateway/field），新 RBAC 上线后这些角色**没有任何菜单授权** → 挂存量角色的用户登录后白屏。
- **对策**：初始化脚本检测——首次启用 RBAC 时，给存量所有角色默认授权"基础菜单集"（工作台/设计器入口），管理员再按需收紧；admin 角色全量。
- `AuthController` 路径 `/api/auth` → `/api/lowcode/auth`：已确认前端调用点 1 处（`web-code/src/store/auth.ts:25`），**另需检查数据库存量**：设计器页面配置（PageSchema 存库）里是否配过 `/api/auth/...` 作为事件/API 调用 URL（实施 P1 前对 lowcode_page 数据做一次 `LIKE '%/api/auth%'` 扫描）。

### B2. refreshToken 并发竞态与多端登录策略

- 并发请求同时 401 → 两个 refresh 互相踢（第一个轮换后第二个必失败）→ 偶发被登出。
- **对策**：前端全局"刷新单飞"（模块级 `refreshingPromise`，并发 401 共享同一个 promise），设计文档已提"仅一次"，实施时必须做单飞而非仅重试一次。
- `auth:refresh:{userId}` 单值 = **单端登录**（后登踢前登的刷新能力）。手机+电脑同时用会互踢。**需拍板**：单端（默认，安全性好）还是多端（改 Redis Set + 按 deviceId 区分）。

### B3. 文件上传/下载经过网关的限制

- 已确认存在 `/api/lowcode/attachment/upload`（api/registry.ts:299），应用包导入导出（packages）也是大文件场景。
- Spring Cloud Gateway 基于 Netty：需配 `spring.cloud.gateway.httpclient.connect-timeout / response-timeout`，上传路由不受全局 30s 超时影响（response-timeout 设为按路由覆盖）；注意**不要**在全局过滤器里用 ReadBody/ModifyRequestBody（会缓冲整个文件到内存），AuthFilter 只读 header 即可，天然无此问题。
- 大文件下载流式转发默认支持，无需特殊处理。
- WebSocket：前端仅 sandbox.ts 出现（非实际业务），后端有 websocket starter 但当前未见实际 endpoint，**暂不配 ws 转发**，未来启用时网关路由加 `ws://` 即可。记录在案。

### B4. WebFlux 阻塞调用陷阱（网关过滤器代码规范）

- Gateway 是 Netty EventLoop，AuthFilter 中**禁止**调用阻塞 API（StringRedisTemplate 同步方法、JDBC），否则告警甚至死锁。
- **对策**：统一用 `ReactiveStringRedisTemplate`（hasKey().flatMap(...) 风格）；jjwt 的 parse 在 EventLoop 里属 CPU 微操作（微秒级）可接受。
- 网关 pom 严查依赖树：不得引入 `spring-boot-starter-web`（与 WebFlux 冲突，启动直接报错），jjwt/fastjson2 传递依赖若有 servlet 需 exclude。

### B5. jjwt 0.9.1 与未来 JDK 升级冲突（C→B）

- jjwt 0.9.1 依赖 JDK8 自带 JAXB，**JDK9+ 直接 NoClassDefFoundError**。当前 JDK8 无碍，但平台若升级 JDK 11/17，auth+gateway 同步改 jjwt 0.11.x（API 大改：`Keys.hmacShaKeyFor` / `parserBuilder`）。
- **对策**：封装 `JwtService` 独占所有 jjwt 调用（设计已如此），升级只改一个类；记录技术债到文档。
- HS256 密钥：必须 ≥32 字节，启动时校验长度不足即拒绝启动（而不是运行时才报）。

### B6. 动态菜单与静态路由冲突 / 初始化时机

- addRoute 的 path 若与静态路由重复（如菜单配了 /home）→ 路由覆盖或告警。**对策**：菜单保存时后端校验 route_path 不在静态路由保留名单内（保留名单配置在 yml，bcd-auth 与前端共用常量）。
- admin 初始化：DDL 静态 SQL 无法生成 BCrypt（换初始密码要重发哈希）。**对策**：初始化逻辑放 bcd-auth 启动钩子（ApplicationRunner）：无 admin 用户则用代码生成 BCrypt 插入——密码可配置（`auth.init.admin-password`，默认 Admin@123，首启日志提示修改）。
- sys 表建表职责：bcd-auth 自带 initializer（仿照 bcd-code 的 LowcodeTableInitializer 读取 db/*.sql IF NOT EXISTS 模式），不依赖 bcd-code 启动，服务启动顺序解耦。

### B7. 两套权限体系的前端命名与调用混淆

- 前端已有 `hasActionPermission`（utils/permission.ts，业务权限 bizCode×action，数据来自 `/api/auth/permissions`）。新增按钮权限 `hasPerm`（system:user:create）。两套并存，命名不区分会导致后续维护混乱。
- **对策**：命名固化为"**业务权限**（bizCode×action，管数据）/ **功能权限**（permCode，管界面）"两个术语，代码注释、文档统一；`v-perm` 只查功能权限，`hasActionPermission` 维持业务权限用途不变；权限管理页面分组展示（角色管理页管功能授权，现有"网关权限/字段权限"页管业务授权，入口都收敛到系统管理菜单下并列）。

### B8. P1 切换期开发体验回退

- vite 代理一把切到 8000：网关没起则前端全挂，调试低代码功能也要先起 3 个后端进程。
- **对策**：vite 代理目标改为 `.env.development` 变量 `VITE_API_TARGET`（默认 8000，可临时改回 3015 直连）；或 P1 期间代理规则分流（`/api/auth` `/api/sys` → 8000，其余 → 3015），P3 权限闭环后再全量切 8000。推荐后者（渐进、随时可退）。

### B9. 多数据库 DDL 方言（既有债，不新增但要记录）

- 新表 DDL 沿用 PG 风格（BOOLEAN、IF NOT EXISTS、独立 CREATE INDEX）。Oracle 无 BOOLEAN、SQLServer 不支持 IF NOT EXISTS……现有 `lowcode_tables.sql` 已是同样风格（存量既有债），bcd-auth 沿用与现状一致，**不新增风险但延续限制**。
- **对策**：本期按 PG 交付（用户当前就是 PG）；平台级"7 库方言统一"是独立的既有课题（quoteIdentifier 方案已有决策），auth 表 DDL 列入该课题改造范围，不在本次 RBAC 范围内解决。

---

## C 级（记录在案）

| # | 项 | 说明 |
|---|---|---|
| C1 | 框架 EOL | SB 2.3.12/SC Hoxton 已 EOL 无补丁；网关是唯一对外入口，建议 8000 端口前再挂一层 Nginx（静态资源+TLS+限流）作为长期形态，本次不做 |
| C2 | 时间列字符串排序 | created_at VARCHAR 沿用现状；lc_login_log/审计类按时间排序的表必须全库统一 `yyyy-MM-dd'T'HH:mm:ss.SSS` 格式，否则字典序错乱。统一用 DateTimeFormatter 常量 |
| C3 | logback 日志路径 | 现有目录名 `log_path_IS_UNDEFINED` 暴露过 logback 属性未定义问题；两个新服务的 logback-spring.xml 必须显式定义 log.path 并验证生成目录名 |
| C4 | sys 管理操作审计 | 与已确认的 lc_data_audit_log 审计需求呼应：用户/角色/菜单变更属安全审计核心场景（谁改了权限）。建议 P2 给 sys 写操作补一张 `lc_op_log`（操作人/接口/参数摘要/结果），复用审计表也行，实施时定 |
| C5 | 管理操作 CSRF | 纯 Bearer token 无 cookie，天然免疫；但第三方绑定流程预留 state 参数防 CSRF（P4 时必须） |
| C6 | 密码策略 | P2 用户管理补充密码复杂度/长度校验（后端+前端双校验，遵循"尽早校验"约定） |

---

## 已排除的疑点（验证过，不构成风险）

| 疑点 | 验证结论 |
|---|---|
| 前端取数与"三层嵌套"历史问题 | `utils/request.ts` 的 extractPayload/extractList 是多格式兼容解包（数组/data/items/rows/list/results/嵌套 data.data 全兼容），网关直透 bcd-code 原生 ResMsg 结构**不会**导致前端取不到数据 |
| /api/auth 前端调用面 | 仅 `store/auth.ts:25` 一处（GET /api/auth/permissions），改名影响可控（另需扫库存量数据，见 B1） |
| 上传接口是否存在 | 存在（/api/lowcode/attachment/upload），网关配置需覆盖（见 B3） |
| WebSocket 实际使用 | 前端仅沙箱示例代码引用，暂无实际 endpoint，不阻塞 |
| Gateway 与 SB 2.3.12 兼容性 | Hoxton.SR12 对应 gateway 2.2.x 官方兼容组合，WebFlux 前提下可用 |

---

## 待用户拍板的决策点汇总

| # | 决策 | 选项 | 推荐 |
|---|---|---|---|
| D1 | Redis 故障时网关策略 | fail-closed / fail-open | fail-closed（A3） |
| D2 | 角色变更时效 | 变更即踢下线 / 每请求查实时角色 / 接受 2h 延迟 | 变更即踢下线（A4） |
| D3 | 多端登录 | 单端（后登踢前登）/ 多端并存 | 单端起步，结构预留 deviceId（B2） |
| D4 | X-User 头加固 | 仅端口隔离 + local-debug 开关 / 加 HMAC 网关签名 | P1 先端口隔离，P3 加签名（A5） |

其余（A1 多语言 key、A2 page: 前缀双模式、B1-B9 对策）为确定项，直接并入设计文档修订版。
---

# 第二轮多角色评审（对 v1.1 设计复核，2026-10-21）

> 评审角色：测试/QA、产品、运维/SRE、DBA、安全（深挖）、集成兼容（导入/审计/工作流/应用包/插件五条业务线交叉）、前端（fetch 语义）。
> 第一轮已拍板 D1-D4 已并入 v1.1，本轮新增 **2 项 A 级、8 项 B 级、7 项 C 级**。

## 本轮已验证事实（代码级确认）

| 事实 | 验证位置 | 影响 |
|---|---|---|
| 前端 httpRequest 是 window.fetch 封装，**401 不会 throw**，返回 `{ok, status, data}` | utils/request.ts:223 | 网关 401 的 JSON 会被 extractPayload 解析成**空数据**，业务代码"静默空列表"而不跳登录（A6） |
| lc_app_package 仅含 `page_ids`/`meta_keys`，**无权限资产字段** | lowcode_tables.sql:606 | 应用包跨环境交付后菜单/角色/权限全手工重建（A7） |
| lc_workflow_task 已有 `assignee`/`initiator` VARCHAR(64) 字段 | lowcode_tables.sql:452 | RBAC 上线后审批人数据源需接用户表（B17） |
| v1.1 前端设计 2.1 的伪代码是 axios 风格（response error 回调） | 前端设计文档 | 与实际 fetch 封装结构不符，实施需适配（A6 修订） |

## 新增 A 级问题

### A6. 网关 401/403 与前端 fetch 封装不匹配（已验证，必须修订前端设计 2.1）

**问题**：fetch 对 4xx/5xx 不抛异常，`httpRequest` 把 401 响应体当正常 data 返回；`extractPayload` 把网关的 `{success:false, code:401}` 解析成 `{data:[], totalCount:0}`。结果：token 过期后所有页面**静默显示空数据**，不跳登录页。且 v1.1 设计 2.1 伪代码写的是 axios 式 `response error` 回调，实际代码结构不存在该挂点。

**对策（修订前端设计）**：
- 401/403 检测放在 `httpRequest` 的**统一出口**（拿到 `resp` 后、返回 RequestResult 前）：`status===401` → 触发全局刷新单飞→重放；刷新失败 → 清 store + 跳登录。不走"错误拦截器"（fetch 封装没有这个挂点）。
- **错误语义契约**（写入前后端设计，两端共同遵守）：认证/授权错误 → HTTP 状态码（401 未登录/403 无权限），body 携带 ResMsg 结构便于展示 message；业务错误 → 沿用 bcd-code 现有模式（前端已兼容）。网关 401 body 与 bcd-code 业务错误结构同为 `{success, code, message, data}`，一套解析。

### A7. 应用包导出/导入不含权限资产（需拍板，已验证）

**问题**：平台应用包（lc_app_package）只打包页面与元数据。RBAC 落地后，"导出应用→另一个环境导入"将丢失：菜单树、角色-菜单授权、角色基础信息（角色-业务权限 lc_permission_gateway/field 是否在包里也需确认）。低代码平台的交付形态就是应用包，权限资产不迁移等于每个环境手工重配 RBAC。

**对策（二选一）**：
- **方案 1（推荐）**：应用包范围扩展——导出时附带 `menu_codes`（含菜单定义+角色授权关系）；导入时按 menu_code 幂等 upsert，目标环境不存在的角色自动创建（enabled=false 待管理员确认）。
- 方案 2：明确记录"权限资产不随包迁移"，文档写清目标环境手工配置清单。

## 新增 B 级风险

### B10. 审计/导入/工作流的用户上下文契约缺失（与三个已定稿专项的协同断点）

- 已定稿待实施的**数据审计**（lc_data_audit_log，created_by/updated_by 自动填充）与**数据导入**（业务表 created_by）都需要"当前用户"，但 v1.1 设计未声明数据来源契约。
- **修订**：`CurrentUserContext` 定为**全平台唯一用户上下文来源**——审计填充、导入填充、工作流 initiator、lc_op_log.user_id 一律从它取。审计/导入专项实施时禁止另建用户来源。
- 需要在 bcd-code 的 CrudExecutor/审计切面取值处统一改为 `CurrentUserContext.getUserId()`（具体改动点在各专项实施时落地，本设计只定契约）。

### B11. 异步线程用户上下文断链（导入线程池/@Scheduled）

- Servlet 线程里 CurrentUserFilter 塞 ThreadLocal，但**导入线程池、@Scheduled 调度线程里 ThreadLocal 为空** → 异步导入的 created_by 全空。
- **对策**：导入任务表（已定稿设计有）落库时存 created_by；异步任务执行开始时显式 `CurrentUserContext.set(task.createdBy)`，finally 清理。禁止用 InheritableThreadLocal（线程池复用线程会串用户）。此约束写入导入专项与 RBAC 设计的交叉条款。

### B12. Redis 持久化与驱逐策略未核查（auth keys 的存活保证）

- 用户现有 Redis（localhost:8004）此前当缓存用，若配置 `allkeys-lru`/`allkeys-lfu`：内存压力下 `auth:blacklist`/`auth:refresh` 会被驱逐 → 黑名单丢（登出令牌复活≤2h）、refresh 丢（静默掉线）。若 `volatile-lru` 同样驱逐带 TTL 的 auth key。
- Redis 重启且无 AOF/RDB → **全站强制重新登录**（fail-closed 下连黑名单校验都过不去）。
- **对策（部署核查清单项）**：`CONFIG GET maxmemory-policy`（目标 noeviction 或确认内存充足）、`INFO persistence`（AOF 开启或 RDB 定期）；建议 auth 使用独立 DB index（Redis select 0/1 隔离，避免与 bcd-code 现有缓存 key 互相驱逐影响）——实施 P1 时与现有 Redis 用途核对后定。

### B13. 角色删除的级联清理未定义

- `role/delete` 接口未写级联：需清 `lc_user_role`/`lc_role_menu`/`lc_permission_gateway`/`lc_permission_field`（+ `lowcode_page_perm` 中 role 引用若有）+ 删 `auth:perm:{roleCode}` 缓存 + 该角色下用户全部踢下线 + **拒删内置角色 ADMIN**（或任何仍被用户引用的角色给二次确认）。
- 表间无外键（沿用现有 lc_role 风格），级联必须代码实现——不做就是孤儿数据+权限计算错乱。

### B14. 用户删除语义缺失（建议仅禁用）

- v1.1 接口只有 changeStatus，未定义 delete。**建议：不提供物理删除，仅禁用**——lc_op_log/lc_login_log/审计记录都按 user_id 追溯，物理删用户=历史记录悬空；lc_user 表体量小（企业内数百级）无清理压力。
- 若坚持删除：校验无在线会话（先踢）、lc_user_role/lc_user_identity 级联清、日志表保留 user_id 字符串快照不级联。

### B15. username 大小写唯一性（PG 默认 case-sensitive）

- PG 默认 `Admin` 与 `admin` 是两个账号（VARCHAR 唯一约束区分大小写）。登录也区分 → 用户困惑 + 权限归属混乱。
- **对策**：`CREATE UNIQUE INDEX idx_user_username_lower ON lc_user (lower(username))`（函数索引，7 库兼容性 PG/MySQL8/Oracle 均支持表达式索引/函数索引，达梦/金仓兼容 PG 语法，SQLite 部分版本支持——实施时按 dialect 走建表脚本差异，与既有 quoteIdentifier 方言课题同路径）；登录查询统一 `WHERE lower(username)=?`。

### B16. user-sessions Set 幽灵成员膨胀

- Redis Set 成员无独立 TTL：jti 每 2h 轮换，旧 jti 成员留在 Set 里；Set 整体 TTL 每次登录续期 → **长期在线用户 Set 无限增长**（每设备每天 +12 个幽灵 jti）。
- **对策**：改 **ZSET**（score = jti 过期毫秒时间戳）：踢人/在线列表时先 `ZREMRANGEBYSCORE key -inf now` 清幽灵成员再操作，天然自愈；key 结构 `auth:user-sessions:{userId}` 不变。

### B17. 工作流审批人数据源未接用户体系（已验证字段存在）

- lc_workflow_task.assignee/initiator 目前无用户体系时存值来源不明（可能空/前端随意字符串）。RBAC 上线后：
  - initiator 由后端从 CurrentUserContext 自动填充（禁止前端传）；
  - 审批人（assignee）选择器数据源接 `/api/sys/user/list`（P3/P4 打通项）；
  - TodoView"我的待办"按 `assignee = CurrentUserContext.userId` 过滤。
- 不阻塞 P1-P2，列为 P3 打通项，避免工作流专项与用户体系各搞一套人名口径。

## 新增 C 级（记录在案）

| # | 项 | 说明 |
|---|---|---|
| C7 | 登录锁定 victim lockout | 按 username 计数可被恶意锁他人账号；改 username+IP 组合计数（同一账号不同 IP 不互相锁） |
| C8 | blacklist TTL 时钟偏差 | TTL 取值加 5min buffer，防 Redis 与 JVM 时钟差导致黑名单先过期、token 复活几秒 |
| C9 | 默认 JWT secret 告警 | 非 local-debug 模式下检测到默认密钥值启动即 ERROR 日志（与 local-debug 告警同机制） |
| C10 | 日志表清理策略 | lc_login_log/lc_op_log 无清理机制，180 天定期清理（运维脚本/WinSW 计划任务） |
| C11 | 角色超多头溢出 | >50 角色时 JWT+X-Role-Codes 超 HTTP 8KB 头限制；正常企业 <10 角色不触发，记录阈值告警即可 |
| C12 | 多机部署时钟同步 | 网关签名 ±60s 时间窗依赖三服务时钟；单机 WinSW 部署天然一致，分机部署需 NTP |
| C13 | localDebugEnabled 暴露位置 | 不放白名单化的 /actuator/health（内网可探测安全配置），移至需登录的运维接口或仅 ERROR 日志 |

## 测试/QA 角色专项建议

- P1 冒烟回归脚本（node 实现，python 不可用；放 `code-service/tools/` 或 `docs/`）：
  1. 登录（错误密码 ×5 → 锁定提示）→ 正确登录拿双令牌
  2. 带 accessToken 访问受保护接口 → 200
  3. 登出 → 重放旧 token → 期待 401（黑名单生效）
  4. 直连 3015 伪造 X-User-Id 头（无 X-Gw-Sign）→ 期待 401（签名生效）
  5. refreshToken 刷新 → 旧 accessToken 失效、新 token 可用（jti 轮换）
  6. 多端：同账号两 deviceId 登录 → 互不踢；登出 A 端 → B 端仍可用
  7. 停 Redis → 受保护接口 401"认证服务不可用"（fail-closed 演练）
- bcd-auth 单测重点：JwtService（过期/篡改/缺 claim）、LoginGuard 计数窗口、BCrypt、角色变更踢人遍历逻辑。

## 运维/SRE 角色专项建议（部署核查清单，P1 实施时逐项打勾）

| 项 | 检查内容 |
|---|---|
| Redis | maxmemory-policy / AOF|RDB / 版本（Windows 移植版 3.x 常见，确认 EXPIRE 与 ZSET 可用） |
| 防火墙 | 3015/8010 入站规则封禁；8000 对外 |
| WinSW | bcd-gateway 依赖 Redis 服务（depends + startdelay）；auth/code 无依赖可乱序 |
| 时钟 | 单机部署免检；分机部署 NTP |
| 日志 | 三服务 logback 显式定义 log.path（历史 log_path_IS_UNDEFINED 问题，C3 呼应） |
| health | 三服务 /actuator/health 纳入巡检 |

## 产品角色记录项

- 登录页"记住用户名"（localStorage 存 username，不存密码）——低成本体验项，P1 顺带。
- 会话过期跳登录时 toast"登录已过期，请重新登录"（避免静默跳转让用户困惑）——与 A6 的统一出口一起实现。

---

## 第二轮待拍板决策点

| # | 决策 | 选项 | 推荐 |
|---|---|---|---|
| D5 | 应用包是否纳入权限资产 | 方案1：随包导出菜单+角色授权（导入幂等 upsert）/ 方案2：不迁移，文档写明手工配置 | 方案1（A7） |
| D6 | 用户删除语义 | 仅禁用不物理删 / 提供物理删+级联校验 | 仅禁用（B14） |

其余 A6/B10-B17/C7-C13 为确定项（对策已明确），直接并入设计文档 v1.2 修订。