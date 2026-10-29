# 网关 / 认证 / 系统管理（RBAC）前端设计

> 版本：v1.2（已并入两轮风险评审与决策；重点修订：2.1 改为 fetch 语义统一出口检测 401/403（A6），会话过期提示、记住用户名、审批人选择器接用户表（P3））
> 范围：web-code（Vue3 + Element Plus + DevExtreme 现有前端）
> 配套后端：《gateway-auth-rbac-backend-design.md》《gateway-auth-rbac-risk-review.md》

---

## 1. 改动总览

| # | 位置 | 改动 | 新建/修改 |
|---|---|---|---|
| 1 | vite.config.* | dev 代理 **P1 分流**：`/api/auth`、`/api/sys` → `http://localhost:8000`（网关），其余 `/api` 暂留 `3015`；P3 权限闭环后全量切 8000（渐进切换、随时可退） | 修改 |
| 2 | utils/request.ts | 请求统一带 `Authorization: Bearer`；401/403 统一处理（**全局刷新单飞**：模块级 refreshingPromise，并发 401 共享同一次 refresh，防轮换竞态互踢） | 修改 |
| 3 | store/auth.ts | login() 从模拟实现改为真实 `POST /api/auth/login`；补 logout/refresh/permCodes/menus/deviceId 状态；**deviceId 本地生成**（首次 UUID 存 localStorage，多端并存标识） | 修改 |
| 4 | views/LoginView.vue | 删模拟延迟；真实调用；错误提示用 Element Plus（不用原生 alert） | 修改 |
| 5 | router/index.ts | 路由守卫校验 token；登录后动态菜单 → addRoute 注册业务路由 | 修改 |
| 6 | views/HomeView.vue | 静态菜单数组 → 后端菜单树渲染（保留 el-splitter 左右布局与拖拽） | 修改 |
| 7 | api/auth.ts（新建） | 登录/登出/刷新/个人信息/菜单/权限码 封装 | 新建 |
| 8 | api/system.ts（新建） | 用户/角色/菜单管理接口封装 | 新建 |
| 9 | views/system/UserManageView.vue | 用户管理页 | 新建 |
| 10 | views/system/RoleManageView.vue | 角色管理页 | 新建 |
| 11 | views/system/MenuManageView.vue | 菜单管理页 | 新建 |
| 12 | directives/perm.ts + utils/permission.ts | v-perm 按钮权限指令 + hasPerm() | 新建 |
| 13 | api/registry.ts | API 治理白名单补 `/api/auth/**`、`/api/sys/**` | 修改 |
| 14 | 旧调用点 | `/api/auth/permissions`（bcd-code 页面权限元数据接口改路径）→ `/api/lowcode/auth/permissions` | 修改（全局搜索替换，预计 1-2 处） |

---

## 2. 各部分设计

### 2.1 请求层（utils/request.ts）——fetch 语义适配（A6 修订）

**关键事实**：httpRequest 是 `window.fetch` 封装，返回 `{ok, status, data}`——**fetch 对 4xx/5xx 不抛异常**。若不在统一出口检测，网关 401 的 JSON body 会被 extractPayload 解析成空数组，页面"静默空列表"而不跳登录。axios 式 `response error` 拦截器在此封装中**不存在**，勿按 axios 模式实现。

```ts
// httpRequest 内部，拿到 resp 之后、return 之前（统一出口）：
if (resp.status === 401) {
  // 全局刷新单飞（防并发轮换竞态）：
  // refreshingPromise ??= POST /api/auth/refresh {refreshToken, deviceId}
  //   .finally(() => refreshingPromise = null)
  // 成功 → 用新 token 重放当前请求（每请求仅重放一次）
  // 失败 → 清 store → toast '登录已过期，请重新登录' → router.push('/login?redirect=当前路径')
}
if (resp.status === 403) {
  ElMessage.error((data as any)?.message || '无操作权限')
  // 返回统一空结果（业务代码按现有模式处理），不跳转
}
```

- token 存储：`localStorage`（accessToken + refreshToken + deviceId，key：`lc_auth`）；页面刷新后 store 从 localStorage 恢复（store 已有持久化模式参考 api-store）。
- deviceId：首次访问生成 UUID 存 localStorage（多端并存标识，登录/刷新/登出均携带；登出仅清当前设备会话）。
- 错误语义契约（与后端文档 2.6 对齐）：401/403 走 HTTP 状态码（统一出口检测）；业务错误沿用 bcd-code ResMsg 模式（success:false），现有多格式解包不动。
- 统一错误提示合并展示（遵循现有"多错误合并、禁原生弹框"约定）。

### 2.2 登录（LoginView + store/auth）

```
onLogin():
  POST /api/auth/login {username, password, deviceId}
  成功 → store 存 {accessToken, refreshToken, user, permCodes}
        → GET /api/sys/profile/menus 拉菜单树 → 转路由表 → addRoute
        → router.push(route.query.redirect || '/home')
  失败 → errorMessage 展示后端 message（"用户名或密码错误"/"账号已锁定"等）
```

- "记住用户名"复选框（localStorage 存 username，不存密码）——P1 顺带实现。

- `store/auth.ts` 现有 `login(username, password): AuthSession` 签名保留（注释里"后台接口就绪后由真实请求取代"即本次落地），内部改 async，LoginView 的调用与错误处理已就绪，只删 `setTimeout` 模拟。
- 登录页多语言 `loadModule('login')` 现有机制不动；后端返回的 message 走 `t()`（后端语言资源由现有 lang 体系下发，前端不引入 i18n 插件）。

### 2.3 路由守卫 + 动态菜单（router/index.ts）

```
router.beforeEach(to):
  if (to.path === '/login') return true
  if (!token) return '/login?redirect=' + to.fullPath
  if (!dynamicRoutesAdded) {
    menus = await GET /api/sys/profile/menus        // 后端已按角色过滤的树
    for m of flatten(menus, type==='menu'):
      router.addRoute('Layout', {
        path: m.routePath,                          // 如 /system/user
        component: viewModules[m.component],        // 如 'system/UserManageView'
        meta: { title: m.menuName, requiresPerm: true }
      })
    dynamicRoutesAdded = true
    return to.fullPath                              // 重新进入触发匹配
  }
  return true
```

- `viewModules`：`import.meta.glob('@/views/**/*.vue')` 生成 component 字符串 → 组件 的映射表。
- **component 双模式**（后端 A2 修订）：`lc_menu.component` 支持 `vue:system/UserManageView`（手写页面，viewModules 动态导入）与 `page:{pageCode}`（低代码配置页面，跳现有页面渲染路由携带 pageCode）两种前缀；裸路径默认按 vue: 处理——菜单可挂设计器产出的业务页面。
- **菜单名多语言**：渲染 `t(m.nameKey ?? m.menuName)`，走现有 lang 体系。
- **路由冲突校验**：动态 route_path 与静态路由保留名单冲突时跳过并 console.warn（后端保存时也校验，双保险）。
- 现有静态路由（设计器/预览等工作台页面）**保留为固定路由**（登录即可用，菜单树里同名的"工作台/设计器"目录用于展示入口）；新增系统管理页面走动态路由。两种并存，后续逐步迁移。
- 按钮权限：`v-perm="'system:user:create'"`（指令内部查 store.permCodes，无权限移除元素）；工具函数 `hasPerm(code)` 供 JS 逻辑用（如表格操作列按钮数组过滤）。注意与既有 `hasActionPermission`（业务权限 bizCode×action）术语区分：**功能权限管界面、业务权限管数据**，两套并存不混用。

### 2.4 主页菜单（HomeView）

- 数据源由写死的菜单数组改为 `store.auth.menus`（登录时已拉取）。
- 渲染：`dir` → 可折叠分组；`menu` → 点击 `router.push(routePath)` 在右侧内容区 SPA 打开（遵循现有"菜单点击右侧内容区渲染、不整页刷新"约定）；`button` 类型不渲染。
- 布局维持现状：`el-splitter` 左菜单/右内容可拖拽调整，扁平化直角风格不变。

### 2.5 旧接口路径切换

`GET /api/auth/permissions`（页面权限元数据，bcd-code 提供，路径将改为 `/api/lowcode/auth/permissions`）：**已验证前端调用点 1 处（store/auth.ts:25）**，同步替换；API 治理白名单（api/registry.ts）补新路径。另需扫描数据库 lowcode_page 存量配置中的 `/api/auth` URL（设计器页面配置存库，风险评审 B1）。

### 2.6 系统管理三个页面（新建，views/system/）

统一风格：扁平化、直角、卡片带边框、区块间明确分隔线、紧凑排版；表格 + 工具栏 + 高级查询 + 弹窗编辑（与现有 SystemManageView 交互模式一致）；禁用原生 alert/confirm，确认用 ElMessageBox（注意弹窗 z-index 约定）；图标用文本符号不引 @element-plus/icons-vue。

**UserManageView（用户管理）**
- 表格列：用户名/姓名/角色（多个 tag）/状态/最近登录时间/操作（修改、分配角色、重置密码、启用|禁用）
- 工具栏：关键字搜索 + 新增按钮（`v-perm="'system:user:create'"`）
- 新增/修改弹窗：el-form（用户名、姓名、邮箱、手机号、状态；新增含初始密码）前端即时校验
- 分配角色弹窗：角色多选（el-select multiple，数据 /api/sys/role/list）
- 重置密码：确认框 → 调接口 → ElMessage 展示新初始密码（后端生成）

**RoleManageView（角色管理）**
- 表格列：角色编码/名称/启用/操作（修改、删除、分配菜单、查看用户）
- 分配菜单弹窗：el-tree（数据 /api/sys/menu/tree，勾选回显 /api/sys/role/menus），父子联动，保存调 assignMenus
- 现有 bcd-code `/api/lowcode/role` 的角色管理页面入口收敛到本页（表是同一张 lc_role）；原有"网关权限/字段权限"管理页保留在原处不动，仅角色基础信息维护迁到本页

**MenuManageView（菜单管理）**
- 左侧菜单树 + 右侧详情/编辑表单（el-splitter 可拖拽，遵循布局偏好）
- 表单项随 menu_type 动态：dir（名称/名称多语言 key/编码/图标/排序/可见）；menu（+路由路径/**组件选择**）；button（+权限码）
- **组件选择用弹窗选择**（遵循"配置选择不手填"偏好）：弹窗两组数据——views 下手写组件（vue: 前缀）+ 页面管理中已配置的低代码页面（page: 前缀），选中回填
- 新增子项按钮挂在树上选中节点的操作区；删除前校验提示（后端校验兜底）

**OpLogView（操作日志，P2 后置可选）**
- 管理操作审计列表（lc_op_log）：时间/操作人/模块/动作/结果/IP，按时间倒序分页；与"数据修改历史"入口并列在系统管理下，术语区分为"操作日志（权限管理）"vs"修改历史（业务数据）"

**工作流审批人选择器（P3，B17 打通项）**
- 流程设计器的审批人配置与待办分配，数据源从现状（无用户体系，值来源不明）切换为 `/api/sys/user/list`（弹窗选择用户，回填 lc_user.id）
- TodoView"我的待办"过滤条件按后端 CurrentUserContext 对齐（前端不再随意传用户名）

### 2.7 API 治理白名单（api/registry.ts）

新增白名单条目：`/api/auth/login`、`/api/auth/refresh`、`/api/auth/logout`、`/api/sys/**`、`/api/lowcode/auth/**`（替换原 `/api/auth/permissions`）。

---

## 3. 多角色评估

| 角色 | 结论 |
|---|---|
| 前端 | 改动集中在请求头/守卫/登录页三处现有文件 + 三个新页面；静态路由保留不破坏设计器直访；动态菜单用 addRoute 增量注册，风险可控 |
| 安全 | token 不放 URL；401 静默刷新仅一次防死循环；permCodes 由后端下发、前端只做显隐（真正的拦截在后端 @RequirePerm） |
| 交互 | 沿用平台既有规范：扁平化、弹窗编辑、SPA 右侧打开、多语言 t()、无原生弹框、表格默认交互与现有管理页一致 |
| 兼容 | vite 代理指向从 3015 → 8000，开发环境后端需先起 gateway；直连调试场景（绕网关）可临时改回代理目标 |

---

## 4. 实施阶段（与后端文档对齐）

| 阶段 | 前端内容 |
|---|---|
| P1 登录闭环 | #1 vite 代理**分流**（/api/auth、/api/sys→8000，其余→3015，P3 再全量切）、#2 请求头+401 刷新单飞、#3 store/auth 真实化+deviceId、#4 LoginView、#7 api/auth.ts、#14 旧路径替换+存量数据扫描 |
| P2 系统管理 | #8 api/system.ts、#9/#10/#11 三个管理页面（含操作日志页可选） |
| P3 权限闭环 | #1 代理全量切 8000、#5 守卫+动态路由（component 双模式+nameKey 多语言）、#6 HomeView 菜单树、#12 v-perm、#13 白名单、在线会话管理页 |
| P4 第三方登录 | LoginView 增加"企业微信/飞书/钉钉/统一平台"登录入口（按后端 provider 配置动态显示）；扫码/跳转组件按选定平台再设计 |