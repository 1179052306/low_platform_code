# 网关 / 认证 / 系统管理（RBAC）后端设计

> 版本：v1.2（已并入两轮风险评审与决策：D1 fail-closed / D2 角色变更即踢下线 / D3 多端并存 / D4 P1 即加网关签名 / D5 应用包随包导出权限资产 / D6 用户物理删+级联校验；第二轮新增：A6 错误语义契约、A7 应用包扩展、B10 CurrentUserContext 全平台契约、B11 异步线程上下文、B12 Redis 隔离、B13/B14 删除级联、B15 username 大小写、B16 会话索引 ZSET、B17 工作流审批人打通）
> 范围：bcd-gateway（网关微服务）、bcd-auth（认证+系统管理微服务）、bcd-code（现有低代码服务的最小侵入改造）
> 技术基线：JDK8、Spring Boot 2.3.12、Spring Cloud Hoxton.SR12（仅用 Gateway 模块，不用 Nacos）、PostgreSQL、Redis(localhost:8004)、fastjson2
> 配套：《gateway-auth-rbac-frontend-design.md》《gateway-auth-rbac-risk-review.md》

---

## 1. 整体架构

```
浏览器 (web-code, Vue3)
    │  http://localhost:8000  (所有 /api 请求统一走网关)
    ▼
┌─────────────────────────────────────────────┐
│  bcd-gateway  :8000  (Spring Cloud Gateway) │
│  · 静态路由（不依赖 Nacos，后续可切）        │
│  · JWT 验签 + 登出黑名单校验                 │
│  · 清洗伪造 X-User-* 头，注入可信用户头      │
│  · CORS / 统一 401、403 JSON 返回           │
└──────────┬──────────────────┬───────────────┘
           │ /api/auth/**     │ /api/sys/**        其余 /api/**（含 /api/lowcode/**）
           ▼                  ▼                    ▼
   ┌──────────────┐   ┌──────────────┐    ┌──────────────────┐
   │ bcd-auth     │   │ bcd-auth     │    │ bcd-code :3015   │
   │ :8010        │   │ (同一服务)    │    │ 现有低代码服务    │
   │ 登录/令牌/    │   │ 用户/角色/    │    │ 业务 CRUD/引擎    │
   │ 第三方登录位  │   │ 菜单/权限管理 │    │ bizCode×action   │
   └──────┬───────┘   └──────────────┘    │ 业务权限(现有)    │
          │                               └──────────────────┘
          ▼
   PostgreSQL :5432/postgres（三服务共用同一库）
   Redis      :8004（refreshToken / 黑名单 / 在线会话 / 登录失败计数）
```

**服务与端口**

| 服务 | 端口 | 新建/改造 | 说明 |
|---|---|---|---|
| bcd-gateway | 8000 | 新建（复用空壳 bcd-wms-gateway 目录改名或新建模块） | 唯一入口，浏览器只暴露 8000 |
| bcd-auth | 8010 | 新建（复用空壳 bcd-wms-auth 目录改名或新建模块） | 认证 + 系统管理 |
| bcd-code | 3015 | 最小侵入改造 | 现有低代码服务，不做大改 |

> 静态路由决策：网关 yml 写死 `uri: http://localhost:3015` / `http://localhost:8010`，本地 Windows（WinSW）部署无外部依赖；未来接 Nacos 只需把 uri 换成 `lb://服务名` 并补 bootstrap.yml，路由规则不动。

**两层权限体系的分工（与现有平台协同，不推翻已有设计）**

| 层 | 管什么 | 数据 | 执行点 |
|---|---|---|---|
| RBAC（新，bcd-auth） | 平台功能入口：用户能看到哪些菜单/页面/按钮 | lc_user / lc_role / lc_menu 及关联表 | 网关验身份 + 前端动态菜单/v-perm + auth 服务接口注解校验 |
| 业务权限（现有，bcd-code） | 业务数据操作：bizCode×action 越权防护、字段级读写 | lc_permission_gateway / lc_permission_field / lowcode_page_perm（全部保留复用） | bcd-code 现有 PermissionService / PagePermService |

角色编码 `role_code` 是两层体系的关联键（lc_role.code 现有表已存在，直接复用）。

---

## 2. bcd-gateway 设计

### 2.1 模块

- `com.api.gateway.GatewayApplication`（SB 2.3.12 + spring-cloud-starter-gateway 2.2.x，WebFlux）
- 依赖：gateway、spring-boot-starter-data-redis-reactive、jjwt、fastjson2、lombok；无 Nacos、无 bootstrap.yml

### 2.2 路由表（application.yml）

```yaml
server:
  port: 8000
spring:
  cloud:
    gateway:
      routes:
        - id: auth-route
          uri: http://localhost:8010
          predicates:
            - Path=/api/auth/**
        - id: sys-route
          uri: http://localhost:8010
          predicates:
            - Path=/api/sys/**
        - id: lowcode-route
          uri: http://localhost:3015
          predicates:
            - Path=/api/**
          # 兜底路由，排在最后；/api/auth、/api/sys 已被上面两条更具体的规则优先匹配
  redis:
    host: localhost
    port: 8004
auth:
  jwt:
    secret: ${AUTH_JWT_SECRET:请替换为至少32字节的共享密钥字符串}
```

### 2.3 全局过滤器链

| 过滤器 | 顺序 | 职责 |
|---|---|---|
| `CleanupHeaderFilter` | -100 | 删除外部传入的 `X-User-Id` / `X-User-Name` / `X-Role-Codes` / `X-Gw-Sign` / `X-Device-Id`，防伪造 |
| `AuthFilter`（GlobalFilter） | 0 | ① 白名单直接放行；② 取 `Authorization: Bearer xxx`；③ HS256 验签 + 过期校验（启动时校验密钥 ≥32 字节，不足拒绝启动）；④ **Reactive** 查 Redis 黑名单 `auth:blacklist:{jti}`（**fail-closed：Redis 不可用时非白名单请求一律 401"认证服务不可用"**）；⑤ 注入可信头 + `X-Gw-Sign` 签名；⑥ 失败返回统一 401 JSON |
| `LogFilter` | 最低 | 记录 userId、path、耗时（logback） |

> WebFlux 代码规范：AuthFilter 全程响应式（ReactiveStringRedisTemplate），禁止阻塞调用（同步 RedisTemplate/JDBC）；网关 pom 严查依赖树，不得引入 spring-boot-starter-web 及 servlet 传递依赖。

**网关签名（D4 决策，P1 实现）**：AuthFilter 注入

```
X-Gw-Sign = HMAC-SHA256("{uid}|{timestamp}|{path}", GW_SECRET)
X-Gw-Time = 签名时刻的毫秒时间戳
```

- `GW_SECRET` 独立于 JWT secret，网关/bcd-code/bcd-auth 三处同值（环境变量 `GW_SIGN_SECRET`）。
- bcd-code/bcd-auth 侧网关签名过滤器校验签名 + 时间窗 ±60s（防重放）；`lowcode.security.local-debug=true` 时跳过校验（本地直连调试场景）。
- 效果：即使 3015/8010 端口对局域网暴露，伪造 X-User-* 头无有效签名依然 401。

**Redis 健康监控**：网关每 30s ping 一次 Redis，连续 3 次失败打 ERROR 日志（配合 WinSW 依赖编排：bcd-gateway 依赖 Redis 服务先启动）。

**Redis 隔离与存活保证（B12）**：auth 的 key 使用独立 DB index（`spring.redis.database` 与 bcd-code 现有缓存隔离，避免互相驱逐/清库误伤）；P1 部署核查 maxmemory-policy（目标 noeviction 或确认内存充足）与 AOF/RDB 持久化（Redis 重启 = refresh 丢失全站重登，需持久化兜底）。blacklist/refresh/online 等全部 auth 前缀 key 的 TTL 计算加 5 分钟 buffer（防 Redis 与 JVM 时钟偏差，C8）。

### 2.6 错误语义契约（A6，前后端共同遵守）

| 错误类别 | 传递方式 | 示例 |
|---|---|---|
| 认证失败（未登录/过期/黑名单/签名无效） | **HTTP 401** + ResMsg body | `{success:false, code:401, message:"令牌无效或已过期"}` |
| 授权失败（@RequirePerm 不通过） | **HTTP 403** + ResMsg body | `{success:false, code:403, message:"无操作权限"}` |
| 业务失败 | 沿用 bcd-code 现有 ResMsg 模式（前端 extractPayload 多格式兼容） | `{success:false, code:xxx, message:"..."}` |

前端检测点：fetch 对 4xx 不抛异常，401/403 判断必须在 httpRequest 统一出口按 `status` 字段做（详见前端设计 2.1），网关返回体与 bcd-code 业务错误同为 ResMsg 结构，一套解析。

**白名单**（免登录可达，集中配置在 yml 便于增删）：

```yaml
auth:
  whitelist:
    - /api/auth/login
    - /api/auth/refresh
    - /api/auth/captcha          # 验证码（预留）
    - /api/auth/third/**         # 第三方登录回调（预留）
    - /actuator/health
```

**401 统一返回结构**（与业务服务错误结构对齐，避免前端多套路处理）：

```json
{ "success": false, "code": 401, "message": "令牌无效或已过期", "data": null }
```

### 2.4 透传协议（网关 → 后端服务的可信契约）

| Header | 来源 | 消费方 |
|---|---|---|
| `X-User-Id` | JWT claim `uid` | bcd-code CurrentUserFilter、auth 服务 |
| `X-User-Name` | JWT claim `uname` | 日志、审计 |
| `X-Role-Codes` | JWT claim `roles`（逗号分隔） | bcd-code 业务权限（role_code 匹配 lc_permission_gateway） |
| `X-Device-Id` | JWT claim `deviceId` | 多端会话识别、在线列表 |
| `X-Gw-Sign` / `X-Gw-Time` | 网关 HMAC 签名 | bcd-code/bcd-auth 网关签名过滤器（防伪造，见 2.3） |

> 安全边界：三重防线——① 3015/8010 生产仅监听 127.0.0.1（server.address）或防火墙封入站；② 网关签名校验（代码层，P1 即实现）；③ `local-debug=true` 生产强关（启动打 ERROR 日志 + health 暴露 `localDebugEnabled` 字段）。本地开发保留直连调试通道（见 4.3）。

### 2.5 大文件与超时配置（附件上传/应用包导入场景）

- 上传路由（`/api/lowcode/attachment/**`、packages 相关）单独配置 `response-timeout`（如 300s），避免全局默认 30s 掐断大文件。
- 全局：`spring.cloud.gateway.httpclient.connect-timeout=5s`、默认 `response-timeout=60s`。
- AuthFilter 只读 header 不读 body，不缓冲请求体（Netty 流式转发大文件无内存问题）；**禁止**在上传路径配置 ReadBody/ModifyRequestBody 类过滤器。

---

## 3. bcd-auth 设计

### 3.1 包结构

```
com.api.auth
├── AuthApplication.java            # 端口 8010，无注册中心
├── config/                         # RedisConfig、WebConfig(CORS 仅网关用则免)、Jackson
├── security/
│   ├── JwtService                  # 签发/验签（HS256，jjwt 0.9.1）
│   ├── PasswordService             # BCrypt（spring-security-crypto 单包，不引入全套 Security）
│   └── LoginGuard                  # 登录失败计数（Redis auth:fail:{username}，5 次锁 10 分钟）
├── provider/
│   ├── AuthProvider                # 接口：String type(); AuthResult authenticate(JSONObject credentials);
│   ├── PasswordAuthProvider        # 本期实现：账号密码
│   ├── OAuth2Provider              # 预留：外部统一登录平台（授权码模式）
│   ├── WechatWorkProvider          # 预留：企业微信扫码
│   ├── FeishuProvider              # 预留：飞书扫码
│   └── DingTalkProvider            # 预留：钉钉扫码
├── controller/
│   ├── LoginController             # /api/auth/**：login、logout、refresh、captcha、third/*
│   ├── UserController              # /api/sys/user/**
│   ├── RoleController              # /api/sys/role/**
│   ├── MenuController              # /api/sys/menu/**
│   └── ProfileController           # /api/sys/profile/**：当前用户信息/菜单/改密
├── service/                        # UserService、RoleService、MenuService（JdbcTemplate + queryAndMap 驼峰映射，与 bcd-code 模式一致）
├── dao/                            # JdbcTemplate 封装
└── common/                         # 统一返回 ResMsg（结构对齐 bcd-code）
```

数据访问：`spring-boot-starter-jdbc` + JdbcTemplate，查询结果用与 bcd-code SystemService 相同的 queryAndMap 驼峰映射模式，不引入 MyBatis（保持技术栈一致、依赖最小）。

### 3.2 认证流程（JWT + Redis，多端并存）

```
登录   POST /api/auth/login {username, password, deviceId}
       （deviceId 由前端生成 UUID 存 localStorage，首次访问生成；不传则服务端按"web-default"处理）
       → LoginGuard 失败计数检查（auth:fail:{username}，≥5 锁 10 分钟）
       → PasswordAuthProvider.authenticate
          → lc_user 查用户 + 状态校验（enabled）
          → BCrypt 密码比对
       → 查 lc_user_role 得角色列表（登录查询统一 `WHERE lower(username)=?`，B15 大小写不敏感）
       → 签发 accessToken = JWT{uid, uname, roles, deviceId, jti=UUID, exp=now+2h}
                refreshToken = 随机 UUID（不透明令牌，与 deviceId 绑定）
       → Redis 写入（多端并存，D3 决策）：
            auth:refresh:{userId}:{deviceId} = refreshToken        TTL 7d(+5min buffer)
            auth:online:{jti}            = 会话JSON(uid/deviceId/ip/ua/loginTime) TTL 2h
            auth:user-sessions:{userId}  = **ZSET**（member=jti, score=过期毫秒时间戳；B16 幽灵成员自愈）
       → 失败计数 auth:fail:{username}:{ip}（C7：username+IP 组合计数，防恶意锁他人账号）
       → 返回 {accessToken, refreshToken, expiresIn, user:{...}, permCodes:[...]}

请求   浏览器带 Authorization: Bearer {accessToken} → 网关验签+黑名单+签名 → 透传 → 后端服务

刷新   POST /api/auth/refresh {refreshToken, deviceId}
       → Redis auth:refresh:{userId}:{deviceId} 比对
       → 签发新 accessToken（轮换 jti，旧的写黑名单；user-sessions 索引同步换）

登出   POST /api/auth/logout（仅登出当前 deviceId 的会话，其他设备不受影响）
       → 当前 jti 写入 auth:blacklist:{jti}（TTL = 剩余有效期）
       → 删除 auth:refresh:{userId}:{deviceId}，从 auth:user-sessions:{userId} 移除 jti
       → 前端清 token 跳登录页
```

**角色变更即踢下线（D2 决策，分级生效策略）**

| 变更操作 | 生效策略 | 实现 |
|---|---|---|
| 用户-角色变更（assignRoles / 建用户 / 禁用） | **即时**：该用户所有在线会话写黑名单，强制重登录 | 遍历 `auth:user-sessions:{userId}` ZSET（先 ZREMRANGEBYSCORE 清过期幽灵成员）中 jti → 逐个 sadd 黑名单 → 删索引 |
| 角色-菜单变更（assignMenus） | 按钮权限 10min 内生效（删 `auth:perm:{roleCode}` 缓存）；菜单树对在线用户下次登录生效（菜单树登录时拉取） | 删权限缓存；管理页提示"已登录用户重新登录后菜单刷新" |
| 角色删除（B13 级联） | 即时：清 5 张引用表 + 删权限缓存 + 该角色下用户踢下线 + 拒删 ADMIN | lc_user_role / lc_role_menu / lc_permission_gateway / lc_permission_field（+ lowcode_page_perm 若引用） |
| 用户禁用/删除 | 即时踢下线（同上） | 同 assignRoles 路径 |

**Redis Key 清单**

| Key | Value | TTL | 用途 |
|---|---|---|---|
| `auth:refresh:{userId}:{deviceId}` | refreshToken | 7d+5min | 多端刷新令牌（每设备独立） |
| `auth:blacklist:{jti}` | 1 | = JWT 剩余有效期+5min | 登出/踢人后令牌立即失效 |
| `auth:online:{jti}` | 会话 JSON | 2h+5min | 在线会话详情（uid/deviceId/ip/ua/loginTime） |
| `auth:user-sessions:{userId}` | **ZSET**（jti→过期时间戳） | 2h+5min | 用户会话索引（踢人/在线列表用；操作前 ZREMRANGEBYSCORE 自愈清理） |
| `auth:fail:{username}:{ip}` | 计数 | 10 min | 防爆破，≥5 锁定（组合维度防 victim lockout） |
| `auth:perm:{roleCode}` | 权限码集合 | 10 min | @RequirePerm 校验缓存 |
| `auth:captcha:{captchaId}` | 验证码答案 | 2 min | 预留 |

**JWT 规格**：HS256 共享密钥（gateway 与 auth 配置同值，`AUTH_JWT_SECRET` 环境变量注入，默认值仅本地开发用；**启动时校验 ≥32 字节否则拒绝启动**）；accessToken 2h；claims：`uid`、`uname`、`roles`、`deviceId`、`jti`、`iat`、`exp`。选 HS256 而非 RS256 的理由：只有两个验签方（gateway/auth），对称密钥配置最简；未来服务增多再切 RS256（auth 持私钥、网关持公钥，旧壳里 public.key/jwt.jks 的思路可复用）。jjwt 0.9.1 依赖 JDK8 自带 JAXB，JDK9+ 会 NoClassDefFoundError——所有 jjwt 调用收敛在 `JwtService` 单类内，未来升 0.11.x 只改一个类（技术债记录于风险评审 B5）。

### 3.3 第三方登录扩展设计（回应未来接统一登录/企业微信/飞书/钉钉）

核心思想：**认证方式插件化 + 身份绑定表解耦**。

```
第三方登录回调 POST /api/auth/third/{type}/callback {code|ticket}
  → ThirdPartyController 按路由参数选 provider（Spring 注入 List<AuthProvider> 建 type→bean 映射）
  → provider.authenticate：
      ① 用 code 换外部系统用户标识（各平台 SDK/appid+secret 换 user_ticket，各 provider 自己实现）
      ② 查 lc_user_identity(identity_type, external_user_id)
         ├─ 命中 → 取 user_id → 走统一签发流程（同密码登录后半段）
         └─ 未命中 → 返回 needBind:true + 临时绑定令牌(5min)
                     → 用户补账号密码/注册 → POST /api/auth/third/bind 建立绑定
```

新增第三方时：实现一个 Provider 类 + yml 加该平台的 appid/secret 配置，LoginController/表结构零改动。各 Provider 本期只留接口与占位实现（返回"未配置"），不引入任何平台 SDK。

### 3.4 表设计（DDL）

命名遵循现有系统表规范：小写、`lc_` 前缀、`id VARCHAR(64)`（UUID 字符串，规避前端 JS 精度问题，与 lc_role 现状一致）、时间列 `VARCHAR(64)` 存 ISO-8601 字符串（与 lowcode_tables.sql 全部现有表风格一致）。

```sql
-- ============ 用户 ============
CREATE TABLE IF NOT EXISTS lc_user (
    id            VARCHAR(64) PRIMARY KEY,
    username      VARCHAR(128) NOT NULL,
    password      VARCHAR(128) NOT NULL,              -- BCrypt(60字符)
    real_name     VARCHAR(128),
    email         VARCHAR(128),
    mobile        VARCHAR(32),
    avatar        VARCHAR(512),
    status        VARCHAR(16) DEFAULT 'enabled',      -- enabled/disabled/locked
    last_login_at VARCHAR(64),
    created_at    VARCHAR(64),
    updated_at    VARCHAR(64)
);
-- 大小写不敏感唯一（B15：Admin/admin 不允许为两个账号；达梦/金仓/PG 均支持表达式索引，SQLite 不支持则退化为普通唯一索引+登录 lower 查询兜底）
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_username_lower ON lc_user (lower(username));

-- ============ 用户-角色（复用现有 lc_role.code） ============
CREATE TABLE IF NOT EXISTS lc_user_role (
    id         VARCHAR(64) PRIMARY KEY,
    user_id    VARCHAR(64) NOT NULL,
    role_code  VARCHAR(128) NOT NULL,
    created_at VARCHAR(64),
    UNIQUE(user_id, role_code)
);
CREATE INDEX IF NOT EXISTS idx_user_role_user ON lc_user_role (user_id);

-- ============ 菜单（目录/页面/按钮 三级） ============
CREATE TABLE IF NOT EXISTS lc_menu (
    id              VARCHAR(64) PRIMARY KEY,
    parent_id       VARCHAR(64),                      -- 顶级为 NULL 或 '0'
    menu_type       VARCHAR(16) NOT NULL,             -- dir / menu / button
    menu_name       VARCHAR(128) NOT NULL,            -- 默认语言兜底值（name_key 未命中时显示）
    name_key        VARCHAR(255),                     -- 多语言 key（关联 lowcode_lang_text，接入现有 i18n 体系）
    menu_code       VARCHAR(128) NOT NULL UNIQUE,     -- 唯一编码，如 system.user
    route_path      VARCHAR(255),                     -- menu 类型：前端路由路径
    component       VARCHAR(255),                     -- menu 类型：见下方双模式说明
    permission_code VARCHAR(128),                     -- button 类型：权限码，如 system:user:create
    icon            VARCHAR(64),
    sort_no         INTEGER DEFAULT 0,
    visible         BOOLEAN DEFAULT TRUE,
    status          VARCHAR(16) DEFAULT 'enabled',
    created_at      VARCHAR(64),
    updated_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_menu_parent ON lc_menu (parent_id);
```

**component 双模式（打通低代码配置页面）**：

| component 值 | 含义 | 前端路由处理 |
|---|---|---|
| `vue:system/UserManageView` | 手写 Vue 页面 | `viewModules['system/UserManageView']` 动态导入 |
| `page:{pageCode}` | 低代码设计器配置页面（存 lowcode_page 表，运行时渲染器渲染） | 跳转现有页面渲染路由并携带 pageCode |

前缀判 `vue:` / `page:` 分流；裸路径默认按 `vue:` 处理。菜单管理页的组件选择用**弹窗选择**（列出 views 组件 + 页面管理已配置页面两组），不手填。

**多语言（A1 修订）**：前端渲染 `t(m.nameKey ?? m.menuName)`，走现有 lowcode_lang 体系（后台下发语言资源），不引入前端 i18n 插件；菜单管理页提供 name_key 选择/新建。时间类字段统一 `yyyy-MM-dd'T'HH:mm:ss.SSS` 格式常量（字符串排序安全）。

```sql

**复用现有表（零迁移）**：`lc_role`（角色）、`lc_permission_gateway`（bizCode×action 业务权限）、`lc_permission_field`（字段权限）、`lowcode_page_perm`（页面权限）。

**初始化与存量迁移（B1 对策）**：

- 建表：bcd-auth 自带 `AuthTableInitializer`（仿照 bcd-code 的 LowcodeTableInitializer：启动读 db/sys_tables.sql，IF NOT EXISTS 幂等），不依赖 bcd-code 启动顺序。
- admin 用户：**ApplicationRunner 代码初始化**（不硬编码 BCrypt 哈希在 SQL 里）——无 admin 则插入，密码取 `auth.init.admin-password`（默认 Admin@123），首启日志提示修改。
- 存量角色：首次启用 RBAC 时给 `lc_role` 存量角色默认授权"基础菜单集"（工作台/设计器入口），管理员再按需收紧；避免存量角色用户登录白屏。
- 存量数据扫描：实施 P1 前对 `lowcode_page` 存量配置做 `LIKE '%/api/auth%'` 扫描（设计器页面配置里可能存了旧 URL，路径改名影响）。

### 3.5 接口清单

**认证类 `/api/auth`（LoginController）**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/login | 账号密码登录（body 含 deviceId，多端并存），返回双令牌+用户+权限码 |
| POST | /api/auth/logout | 登出（仅当前 deviceId 会话，其他设备不受影响） |
| POST | /api/auth/refresh | 刷新 accessToken（body 含 deviceId） |
| GET | /api/auth/captcha | 图形验证码（P2，防爆破二道防线） |
| POST | /api/auth/third/{type}/callback | 第三方回调（预留占位） |
| POST | /api/auth/third/bind | 绑定第三方身份（预留占位） |

**系统管理 `/api/sys`（均需登录；写操作再校验权限码）**

| 分组 | 方法 路径 | 说明 |
|---|---|---|
| 用户 | GET /api/sys/user/list?keyword&page&size | 分页列表（queryAndMap 驼峰） |
| | POST /api/sys/user/create | 新增（权限码 system:user:create） |
| | POST /api/sys/user/update | 修改基本信息 |
| | POST /api/sys/user/changeStatus | 启用/禁用 |
| | POST /api/sys/user/delete | 物理删除（D6：先踢会话+级联清关联表，日志表留字符串快照） |
| | POST /api/sys/user/resetPassword | 管理员重置密码 |
| | POST /api/sys/user/changePassword | 本人改密（Profile） |
| | POST /api/sys/user/assignRoles | 分配角色（全量覆盖） |
| 角色 | GET /api/sys/role/list | 角色列表（复用 lc_role；前端现有角色管理接口在 bcd-code /api/lowcode/role，本期迁移说明见 §5.3） |
| | POST /api/sys/role/create / update / delete | 角色 CRUD |
| | GET /api/sys/role/menus?roleCode | 查角色已授权菜单 id 集 |
| | POST /api/sys/role/assignMenus | 角色分配菜单（全量覆盖） |
| | GET /api/sys/role/users?roleCode | 角色下用户 |
| 菜单 | GET /api/sys/menu/tree | 全量菜单树（管理端） |
| | POST /api/sys/menu/create / update / delete | 菜单 CRUD（删除校验：有子节点/被角色引用则拒） |
| 个人 | GET /api/sys/profile/me | 当前用户信息（从 X-User-Id 取，不信任前端参数） |
| | GET /api/sys/profile/menus | **当前用户**的菜单树（登录后动态菜单数据源，返回 nameKey 供 t() 渲染） |
| | GET /api/sys/profile/perms | 当前用户按钮权限码集合 |
| 在线 | GET /api/sys/online/list | 在线会话列表（按 用户×设备 维度，P3） |
| | POST /api/sys/online/kick | 踢指定会话/用户全部会话（写黑名单，P3） |
| 操作日志 | GET /api/sys/oplog/list | 系统管理操作审计（P2，见下方 lc_op_log） |

**接口权限校验实现**：自定义注解 `@RequirePerm("system:user:create")` + HandlerInterceptor。拦截器从网关头取角色 → 查该角色的按钮权限码集合（Redis 缓存 `auth:perm:{roleCode}` TTL 10min，角色改权限时删除缓存）→ 匹配注解值。管理员角色（`ADMIN`）短路放行。用户角色变更/禁用时按 3.2 的"变更即踢下线"执行。

**用户删除语义（D6 拍板：物理删 + 级联校验）**：

- 接口 `POST /api/sys/user/delete`：①先踢该用户全部在线会话（黑名单）；②校验无未完结在线会话后清 `lc_user_role`/`lc_user_identity`；③`lc_op_log`/`lc_login_log`/审计表**保留 user_id 字符串快照不级联**（历史可追溯，页面展示时按 username 快照冗余列显示）；④删除前二次确认（前端 ElMessageBox）。

**系统管理操作审计（C4，P2）**：用户/角色/菜单的写操作（create/update/delete/assign/resetPassword/kick）记入

```sql
CREATE TABLE IF NOT EXISTS lc_op_log (
    id          VARCHAR(64) PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL,
    username    VARCHAR(128),
    module      VARCHAR(64),            -- user / role / menu / online
    action      VARCHAR(64),            -- create / update / delete / assignRoles / ...
    params_digest VARCHAR(1024),        -- 参数摘要（脱敏：密码字段不落库）
    success     BOOLEAN,
    message     VARCHAR(255),
    ip          VARCHAR(64),
    operated_at VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_op_log_time ON lc_op_log (operated_at DESC);
```

与已确认的 `lc_data_audit_log`（业务数据 UPDATE 审计）职责分工：lc_op_log 记"谁改了权限/账号"，lc_data_audit_log 记"谁改了业务数据"。

---

## 4. bcd-code 改造（最小侵入）

### 4.1 AuthController 路径冲突处理（必须改）

bcd-code 现有 `perm/AuthController` 挂在 `/api/auth`，与网关 `/api/auth/** → 8010` 冲突。将其改为 `/api/lowcode/auth`，与同包其它控制器（/api/lowcode/perm、/api/lowcode/permission、/api/lowcode/role）前缀统一。前端调用处同步改（见前端设计文档 §2.5）。

### 4.2 CurrentUserFilter（新增）

`com.api.lowcode.common` 下新增：

- `CurrentUserFilter`（@Component，order 最前）：读 `X-User-Id`/`X-User-Name`/`X-Role-Codes` 头 → 塞 `CurrentUserContext`（ThreadLocal）；请求结束清理。
- `CurrentUserContext`：`getUserId()` / `getUserName()` / `getRoleCodes()`，并提供 `getDebugDefaultUser()` 降级。

### 4.3 本地直连调试通道（保留现有开发体验）

bcd-code 允许绕过网关直连 `localhost:3015` 调试（无 X-User-* 头、无 X-Gw-Sign）。配置开关：

```yaml
lowcode:
  security:
    local-debug: true   # 无用户头且签名校验失败时按内置 admin(角色 ADMIN) 处理；生产必须 false
```

- `local-debug=true` 时：启动打 ERROR 级告警日志（"本地调试模式开启，禁止用于生产"）；`/actuator/health` 暴露 `localDebugEnabled: true` 字段供巡检脚本发现误配。
- 签名校验逻辑：优先验 X-Gw-Sign（时间窗 ±60s）；local-debug=true 且签名缺失/无效才走降级 admin；两者都不满足返回 401。
- 生产部署 3015/8010 设置 `server.address: 127.0.0.1`（WinSW 同机部署网关不受影响），并加 Windows 防火墙入站规则，双保险。

### 4.4 权限数据源接入

- `PermissionService.getPermissionMeta(userId)`：userId 参数缺省时取 `CurrentUserContext`（保留显式参数以便兼容现有调用）。
- 业务权限校验（bizCode×action×role 匹配 lc_permission_gateway）在 CrudExecutor 入口继续执行，角色集合从 CurrentUserContext 取，替代原先按前端传 userId 的信任模式。
- 网关只管"你是谁"（认证），bcd-code 管"你对业务数据能干什么"（授权）——职责分离，现有 lc_permission_* 三表与 PagePermService 逻辑零改动。

### 4.5 依赖变更

bcd-code 的 pom 无需新增 Redis/JWT 依赖（令牌在网关验，bcd-code 只读头）；仅新增上述两个类 + AuthController 一行注解改动。

---

## 5. 与现有体系的关键协同点汇总

1. **角色编码贯通**：JWT roles → X-Role-Codes → lc_permission_gateway.role_code / lc_user_role.role_code，一处定义全局使用。
2. **权限分层（术语固化）**：**功能权限**（RBAC 新体系，permCode 如 system:user:create，管菜单/按钮/系统接口）与**业务权限**（现有体系，bizCode×action，管业务数据行级操作与字段读写，前端 hasActionPermission）并存，命名与代码注释统一用这两个术语，避免混淆。
3. **CurrentUserContext = 全平台唯一用户上下文来源（B10 契约）**：审计专项（lc_data_audit_log 的 created_by/updated_by 填充）、导入专项（业务表 created_by）、工作流 initiator、lc_op_log.user_id 一律从它取值；各专项实施时禁止另建用户来源。异步线程（导入线程池/@Scheduled）ThreadLocal 为空——任务表落库存 createdBy，执行开始时显式 set、finally 清理（B11，禁止 InheritableThreadLocal——线程池复用会串用户）。
4. **应用包扩展（D5 拍板：随包导出权限资产）**：`lc_app_package` 增加 `menu_codes` TEXT（JSON：菜单定义+角色-菜单授权关系）；导出时随包附带；导入时按 menu_code 幂等 upsert，目标环境缺失的角色自动创建（enabled=false 待管理员确认启用）。lc_permission_gateway/field（业务权限）是否随包另定（现有应用包只含 page_ids/meta_keys）。
5. **工作流审批人打通（B17，P3 项）**：lc_workflow_task.assignee/initiator 已存在——initiator 由后端从 CurrentUserContext 自动填充（禁止前端传）；审批人选择器数据源接 /api/sys/user/list；TodoView"我的待办"按 assignee=当前用户过滤。
6. **返回结构统一**：auth/gateway 的错误返回与 bcd-code ResMsg 结构对齐（success/code/message/data），前端一套解析；认证/授权错误走 HTTP 401/403 状态码（见 2.6 错误语义契约）。
7. **前端取数路径**：网关转发保持 body 原样透传，不额外包一层响应（历史 result.data.data.data 三层嵌套来自旧网关包装，新网关不做包装，直透后端返回；前端 extractPayload 已多格式兼容，风险评审已验证）。

---

## 6. 多角色评估（两轮汇总）

| 角色 | 结论 |
|---|---|
| 架构师 | 静态路由起步→Nacos 平滑迁移（uri 换 lb://+bootstrap）；auth 包结构 provider/system 分层，未来第三方登录或拆 system 服务都不动表结构；bcd-code 几乎零侵入；CurrentUserContext 定为全平台唯一用户上下文（B10），审计/导入/工作流三个专项复用不另起炉灶 |
| 安全 | BCrypt(强度10)；网关清洗伪造头+HMAC 签名（P1）+仅 8000 对外；JWT 2h 短效 + jti 黑名单即登出即失效；防爆破计数锁定（username+IP 组合维度防 victim lockout）；验证码预留；密钥环境变量注入不进 git+默认密钥检测告警（C9）；local-debug 生产必须关 |
| DBA | 7 张新表 + 4 张复用零迁移；小写 lc_ 前缀；id VARCHAR(64) UUID 避 JS 精度问题；username 函数唯一索引（lower()）防大小写双账号；角色/用户删除级联完整定义（B13/B14）；日志表 180 天清理策略；同库同 schema 无跨库事务问题 |
| 后端 | JdbcTemplate+queryAndMap 与 bcd-code 模式一致；jjwt 0.9.1 父 pom 已管理（调用收敛 JwtService 单类，JDK 升级只改一处）；BCrypt 只引 spring-security-crypto 单包；user-sessions 用 ZSET 自愈防膨胀（B16）；错误语义契约明确（2.6） |
| 运维 | WinSW 三个服务：bcd-gateway 依赖 Redis 先启，auth/code 乱序可起；部署核查清单：Redis 策略/持久化/独立 DB index、防火墙规则、时钟（单机免检）、logback log.path 显式定义、health 巡检；node 冒烟脚本可回归 |
| 测试/QA | P1 冒烟脚本 7 场景（含停 Redis fail-closed 演练）；单测重点：JwtService 过期/篡改/缺 claim、LoginGuard 计数窗口、BCrypt、踢人遍历逻辑 |
| 前端 | fetch 语义适配（A6：401 检测在统一出口而非 error 回调）；extractPayload 多格式兼容验证过；动态菜单 addRoute+静态路由保留；刷新单飞防竞态 |
| 集成兼容 | 应用包纳入权限资产（D5）；工作流 assignee/initiator 接用户体系（B17，P3）；导入/审计专项用户上下文契约已定（B10/B11）；第三方登录 provider 插件化（P4） |

---

## 7. 实施阶段

| 阶段 | 内容 | 验收 |
|---|---|---|
| P1 登录闭环 | DDL+ApplicationRunner 初始化（admin/存量角色基础授权）；bcd-auth（login/logout/refresh+deviceId 多端+JWT+Redis 独立 DB index）；bcd-gateway（路由+鉴权过滤器+**X-Gw-Sign 签名**+fail-closed+上传路由超时）；前端真实登录+401 统一出口跳转+刷新单飞；bcd-code（AuthController 改路径+CurrentUserFilter+网关签名过滤器+local-debug 告警）；**node 冒烟回归脚本**（tools/：登录→访问→登出→401→伪造头→401→刷新轮换→多端并存→停 Redis fail-closed 演练） | 浏览器走 8000 登录 → 携 token 访问低代码接口 → 伪造头无签名 401 → 登出后 401 → 冒烟脚本全绿 |
| P2 系统管理 | /api/sys 用户（含物理删级联）/角色（删除级联 5 表+拒删 ADMIN）/菜单 CRUD+分配接口+**lc_op_log 操作审计**；前端三个管理页面 | 管理员完成建用户/授权/建菜单全流程；操作留痕可查；删除无孤儿数据 |
| P3 权限闭环 | 动态菜单（profile/menus→addRoute，**component 双模式支持低代码页面**）；v-perm 按钮显隐；@RequirePerm 拦截器；在线会话管理+踢人（用户×设备维度）；登录日志；**工作流审批人打通**（initiator 后端填充+审批人选择器接用户表+TodoView 过滤） | 新用户按角色只见授权菜单；越权按钮/接口被拦；改角色即时踢下线 |
| P4 第三方登录 | AuthProvider 各平台实现+绑定流程+管理员绑定解绑页面；**应用包权限资产导出导入（D5，可与 P3 并行）** | 按实际选型平台联调（企业微信/飞书/钉钉/统一登录平台）；应用包导入后 RBAC 配置可用 |

前端部分见《gateway-auth-rbac-frontend-design.md》（本文不内联前端实现）。

> 多数据库说明：本期 DDL 按 PostgreSQL 交付（与现有 lowcode_tables.sql 同风格）；7 库方言统一（BOOLEAN/IF NOT EXISTS 差异）是平台既有课题，auth 表列入该课题后续改造，不在本次范围（风险评审 B9）。