# 系统管理页面配置化（Dogfooding）后端设计

> 版本：v1.0（方案稿，待确认后实施）
> 定位：网关/认证/RBAC 体系的收尾改造——**5 个系统管理页面从手写 Vue 改为平台自身页面开发功能搭建的配置页面**（PageSchema 存 lowcode_page，菜单 `page:{pageId}` 挂载，运行时渲染器渲染），平台吃自己的狗粮。
> 配套：《gateway-auth-rbac-dogfooding-frontend-design.md》
> 已验证能力基础：探索 + 代码级确认（见文末"验证记录"）

---

## 1. 总体架构

```
浏览器登录 → 菜单（lc_menu.component = page:page_sys_user）
        → 路由跳 /preview?pageId=page_sys_user → 运行时渲染器加载 PageSchema
        → data-grid（bizSchemas.table 槽位）

数据流（读写分离，各走最优通道）：
┌───────────────────────────────────────────────────────────────┐
│ 查询：gatewayCall(bizCode='sys_user', 'query')                │
│   → bcd-code /api/lowcode/gateway → CrudExecutor 直查 lc_user │
│   → 免费获得：分页/行筛选/高级查询/列排序/列宽调整/导出          │
├───────────────────────────────────────────────────────────────┤
│ 写操作：表单弹窗保存/删除按钮 → gatewayCall('sys_user',       │
│   'create'|'update'|'delete')                                 │
│   → GenericGatewayController → ExternalApiExecutor            │
│   → POST http://localhost:7011/api/sys/user/create            │
│   → bcd-system 业务逻辑（BCrypt/级联清理/踢会话）全部保留       │
└───────────────────────────────────────────────────────────────┘
```

**核心决策——bizCode 与物理表分离**：页面 tableName 用专用 bizCode（`sys_user`/`sys_role`/`sys_menu`/`sys_online`/`sys_oplog`），`lc_biz_meta.main_table` 指向真实表（`lc_user`/`lc_role`/`lc_menu`/`lc_login_log`/`lc_op_log`）。好处：

1. 与业务表命名空间隔离，管理员日后在设计器修改系统页并保存时，MetaSyncService 的 ensure 四 action 只影响 `sys_user` 这个壳 bizCode，不会污染；
2. `lc_field_meta` 可独立声明字段元数据（如 lc_user 的 password 不注册——查询不泄露哈希、表单弹窗无密码回显）。

## 2. 种子数据（bcd-system 启动初始化器新增 SystemPageSeeder）

> 为什么放 bcd-system 不放 lowcode_tables.sql：页面 schema JSON 很大（每页约 6-10KB），SQL 种子文件不可维护；且页面/菜单归系统管理域。放 bcd-system 的 `SystemPageSeeder`（CommandLineRunner @Order(2)，在 SystemTableInitializer 之后），Java 里构建 JSONObject 写库（helper），**按 page_id 幂等（存在即跳过，不覆盖用户后续设计器修改）**。

### 2.1 每页双模式 schema 设计

5 个页面统一骨架（pageType: 'basedata'，走 form/table 双槽位）：

**form 槽位**（新增/修改弹窗，openPage bizMode:'form'）：

| 页面 | 表单字段（label → dbField，控件类型） |
|---|---|
| sys_user | 用户名*（text，username）/ 初始密码（text-password，password，仅新增显示）/ 姓名（real_name）/ 邮箱（real email）/ 手机号（mobile）/ 角色（multi-select，roleCodes，lookup 远程 sys_role） |
| sys_role | 角色编码*（code，修改禁用）/ 角色名称（name）/ 启用（switch，enabled） |
| sys_menu | 菜单类型*（radio：dir/menu/button）/ 菜单名称*（menu_name）/ 多语言Key（name_key）/ 菜单编码*（menu_code）/ 上级（parent_id，下拉树占位→下拉）/ 路由路径（route_path，menu 型）/ 组件（component，menu 型）/ 权限码（permission_code，button 型）/ 排序（number，sort_no） |
| sys_online | （无 form——只读页面，工具栏不放新增/修改） |
| sys_oplog | （无 form——只读页面） |

**table 槽位**（data-grid，dataSourceType:'remote'，advancedSearch:true）：

| 页面 | 列（caption ← dataField） | 工具栏 items |
|---|---|---|
| sys_user | 用户名/姓名/角色(逗号串)/邮箱/手机号/状态/最近登录 | add*（openPage form）、edit*（passSelectedRow）、delete*、分配角色（自定义按钮）、重置密码（自定义按钮）、启用禁用（自定义按钮） |
| sys_role | 角色编码/名称/启用/用户数/菜单数/创建时间 | add、edit、delete、分配菜单（自定义按钮）、查看用户（自定义按钮） |
| sys_menu | 名称/类型/编码/路由/权限码/排序/可见 | add、edit、delete |
| sys_online | 用户名/设备/IP/登录时间/浏览器 | 踢下线（自定义按钮） |
| sys_oplog | 时间/操作人/模块/动作/参数摘要/结果/IP | （仅刷新/导出） |

*add/edit/delete 为平台预设按钮（ToolbarItemsEditorDialog 的 defaultOpenPageForPreset：add → openPage `__current__` form 模式；edit → passSelectedRow: true）；delete 预设走 gatewayCall delete（EXTERNAL_API 桥）。

**自定义按钮事件**（onClick 沙盒，`e, api, grid` 签名，全部已验证可用）示例——"重置密码"：

```js
const sel = grid.getSelectedRowsData()
if (!sel || sel.length !== 1) { api.showMessage('请勾选一行', 'warning'); return }
if (!await api.confirm('确定重置用户「' + sel[0].username + '」的密码？')) return
const r = await api.call('sysUserResetPwd', { data: { id: sel[0].id } })
if (r.ok) { api.showMessage('新密码：' + (r.data && r.data.data)) ; grid.refresh() }
```

（`sysUserResetPwd` 为注册进 `lowcode_api_registry` 的自定义服务，见 2.3；`api.showMessage/confirm` 平台已有。）

### 2.2 元数据种子（lc_biz_meta / lc_field_meta / lc_action_meta）

每个 bizCode 三张表配置：

**action 配置矩阵**（核心）：

| bizCode | action | strategyType | 说明 |
|---|---|---|---|
| sys_user | query | （空=SINGLE/CrudExecutor） | 分页查 lc_user |
| sys_user | create | **EXTERNAL_API** | ext_api_url: http://localhost:7011/api/sys/user/create |
| sys_user | update | **EXTERNAL_API** | …/api/sys/user/update（含角色变更踢会话） |
| sys_user | delete | **EXTERNAL_API** | …/api/sys/user/delete（级联清理+踢会话） |
| sys_role | query / create / update / delete | 同上 | …/api/sys/role/{query 无，create,save→create,delete} |
| sys_menu | query / create / update / delete | 同上 | …/api/sys/menu/** |
| sys_online | query | SINGLE | 直查 lc_login_log 近期成功登录（或保留现有 online/list） |
| sys_oplog | query | SINGLE | 直查 lc_op_log |

> 角色的"分配菜单"“查看用户”、用户的"分配角色"、在线会话的"踢下线"：**不走 gateway action**，走自定义按钮 onClick → `api.call` 直调 bcd-system（这些是多对象操作，弹窗选择交互复杂，事件代码 + PageModal 选择模式更合适）。

**字段元数据**（lc_field_meta，以 sys_user 为例）：username/real_name/email/mobile/status/last_login_at（查询与表格列用）；**password 不注册**（查询不泄露 BCrypt 哈希；create 的密码经表单弹窗 payload 由 bcd-system 加密）。

### 2.3 前端 API 服务注册（lowcode_api_registry 种子）

自定义按钮事件直调的服务（bcd-system 既有接口，JSON body——`api.call` 默认 JSON 已验证）：

| serviceId | url | method |
|---|---|---|
| sysUserResetPwd | /api/sys/user/resetPassword | POST |
| sysUserChangeStatus | /api/sys/user/changeStatus | POST |
| sysUserAssignRoles | /api/sys/user/assignRoles | POST |
| sysRoleAssignMenus | /api/sys/role/assignMenus | POST |
| sysRoleMenus | /api/sys/role/menus | GET |
| sysRoleUsers | /api/sys/role/users | GET |
| sysOnlineKick | /api/sys/online/kick | POST |
| sysMenuTree | /api/sys/menu/tree | GET |

### 2.4 菜单数据修正（bcd-system SystemTableInitializer）

lc_menu 种子中 5 个菜单的 component：`vue:system/UserManageView` → **`page:page_sys_user`**（其余 4 页同理）。菜单管理页的组件选择弹窗本身已有"低代码配置页面"分组（MenuManageView 双模式选择器），运维一致。

## 3. 后端改动清单（2 处必改 + 1 处建议）

### 3.1 【必改】ExternalApiExecutor：写操作响应透传（防吞业务错误）

现状：`extractPath` 按 dataPath/totalPath 抽 `data`+`totalCount`——为 query 设计；bcd-system 失败返回 HTTP 200 + `ResMsg{res:false}`，经抽取后 res/msg 丢失 → **前端拿到"成功"**。

改法（bcd-code ExternalApiExecutor.execute）：action 为非 query（或 `ext_data_path="__RAW__"` 哨兵值）时，直接校验响应体 `res==false` 则抛 RuntimeException(msg)，否则把响应体原样返回（走 gateway 标准 ResMsg 通道，前端 `gatewayCall` 现有的 res 检查即生效）。约 +15 行。

### 3.2 【必改】服务间鉴权桥：ExternalApiExecutor 注入内部签名头

现状：RestTemplate 转发不带任何凭证 → bcd-system 资源服务器（local-debug=false 时）401。

改法：
1. ExternalApiExecutor 转发前注入 `X-Gw-Sign/X-Gw-Time/X-User-Id/X-User-Name/X-Role-Codes`（HMAC 用共享 `GW_SIGN_SECRET`——三服务已同值；用户身份从 bcd-code 的 CurrentUserContext 取——网关已注入）；
2. bcd-system 新增 `InternalSignFilter`（@Order(1)，先于资源服务器）：校验内部签名有效 → 直接填充 SecurityContext（以 X-User-Id/X-Role-Codes 构造 OAuth2Authentication 风格认证对象）→ @RequirePerm 照常工作（角色/权限码来自签名头声明）。**生产 local-debug=false 时该通道是唯一合法的服务间调用方式**。

### 3.3 【建议】sys_online 的 query 数据源

在线会话数据在 Redis（auth:user-sessions ZSET + auth:online:*），不在关系表。两种方案：
- A（推荐，零后端改动）：sys_online 的 query 走 EXTERNAL_API → bcd-system `/api/sys/online/list`（已有，返回数组）；ExternalApiExecutor 的 dataPath/totalPath 对数组响应做兼容（res.data 是数组时 data=数组、totalCount=length，改动并入 3.1）。
- B：bcd-code 侧直查 Redis——引入重复实现，否决。

### 3.4 bcd-system 新增 SystemPageSeeder

（见 2 节）构建 5 页 PageSchema + 元数据 + API 注册种子 + 菜单 component 修正，全部幂等。页面 schema 在 Java 里以常量类 `SystemPageSchemas.java` 维护（JSONObject 构建，约 600 行；或 JSON 资源文件 + ClassPathResource 读取，**推荐后者**：src/main/resources/db/system-pages/*.json 5 个文件，可读可 diff）。

## 4. 兼容与风险

| # | 风险 | 对策 |
|---|---|---|
| R1 | 管理员日后在设计器改系统页并保存 → MetaSyncService 对 sys_user ensure create/update/delete（SINGLE 版本），与 EXTERNAL_API 种子并存时优先级混乱 | 确认 MetaSync ensure 语义（"不存在才插"则无冲突）；若会覆盖，MetaSyncService 跳过 `sys_` 前缀 bizCode（+3 行） |
| R2 | gateway 前端预校验 hasActionPermission 未配置默认放行 + 后端主链无 action 强制校验（探索确认的既有缺口） | 本期不扩大：系统页写操作最终防线在 bcd-system 的 @RequirePerm（3.2 签名桥携带角色）；action 级后端强校验作为独立安全任务立项 |
| R3 | 表格模式保存不建表（BusinessTableSyncService 仅 form 模式）——系统页指向的 lc_* 表已由 sys_tables.sql 建好 | 无影响 |
| R4 | delete 预设按钮走 gatewayCall(bizCode,'delete',{[keyExpr]:key})，payload 只有主键 → bcd-system delete 接口收 {id} 匹配 ✓ | 无需改；update payload 为全量表单值，bcd-system update 接口按字段取用 ✓ |
| R5 | 唯一性校验：PageModalItem 保存前 validateUnique 走 gateway query（查 username 重复）→ 依赖 sys_user query 的 filterData | query 是 SINGLE 走 CrudExecutor 支持 filterData ✓；`__uniqueChecks` 字段 bcd-system create/update 接口需忽略未知字段（fastjson2 JSONObject 不校验未知键，天然兼容） |
| R6 | 手写 5 个 Vue 视图删除后，v-perm/hasPerm 按钮显隐机制失效 | 配置页面按钮的 `permCode` 字段（ToolbarItemConfig 已支持 permCode）在渲染时由平台权限体系控制（PagePermService ensurePermCodes 已把页面权限码入 lowcode_page_perm）——列/按钮权限码配进 schema |
| R7 | 在线会话页 10 秒自动刷新（手写版有） | data-grid 无自动刷新配置项 → 用页面级自定义事件定时器（平台 onMounted 事件若有）或接受手动刷新（工具栏 refresh 预设按钮）。**接受手动刷新**（运维页低频操作） |

## 5. 实施阶段

| 阶段 | 内容 | 验收 |
|---|---|---|
| D1 后端桥 | 3.1 ExternalApiExecutor 透传+数组兼容；3.2 内部签名头注入 + bcd-system InternalSignFilter；3.3 方案A | 单测：gateway create→bcd-system 收到签名头与角色；res:false 抛错到前端 |
| D2 种子 | SystemPageSeeder（5 个 schema JSON 资源文件 + 元数据 + API 注册 + 菜单修正）；R1 MetaSync 跳过 sys_ 前缀 | bcd-system 重启后 lowcode_page 有 5 页；菜单 component=page:*；MetaRegistry 加载 5 bizCode |
| D3 前端收尾 | （见前端设计文档）删除 5 个手写 Vue + 路由 RESERVED_PATHS 清理 + vite 代理确认 | 浏览器：菜单进配置页 → 查询分页/行筛选/高级查询 → 新增弹窗保存 → 自定义按钮全链路 |
| D4 回归 | 冒烟脚本 + build | 8/8 + build ✓ |

## 6. 验证记录（方案依据的代码级事实）

| 事实 | 位置 |
|---|---|
| api.call(serviceId,{data}) 默认 JSON body | api/index.ts apiCall：非 formData 分支 JSON.stringify |
| 按钮沙盒签名 (e, api, grid)，grid= LcTable 实例（暴露 getSelectedRowsData/refresh） | RendererNode.vue:513-521；LcTable.vue:1754 defineExpose |
| add/edit 预设 openPage 弹本页 form 模式、edit passSelectedRow | ToolbarItemsEditorDialog.vue:154-157 |
| ExternalApiExecutor POST 整体 payload JSON 转发；ext_data_path 空串透传整个响应 | ExternalApiExecutor.java:46-54/87-91 |
| 转发不带凭证（RestTemplate 裸调用） | ExternalApiExecutor.java 全文 |
| extractPath 为 query 设计，写操作 res/msg 丢失 | ExternalApiExecutor.java:77-83 |
| bizCode/main_table 分离能力（lc_biz_meta.main_table） | lowcode_tables.sql lc_biz_meta DDL |
| data-grid 远程三分支：serviceId / remoteUrl / 网关 tableName | RendererNode.vue:1527-1543 |
| gatewayCall 前端 res:false 抛错 | api/index.ts:164-177 |
| lowcode_api_registry 注册结构（id/url/method/permCode） | registry.ts:16-30；ApiRegistryService.java |