# 系统管理页面配置化（Dogfooding）前端设计

> 版本：v1.0（方案稿，待确认后实施）
> 配套后端：《gateway-auth-rbac-dogfooding-backend-design.md》（种子/桥接/元数据在后端文档，本文不重复）
> 原则：**前端零新增页面代码**——5 个管理页全部由 PageSchema 配置产出；前端只做"删除手写页 + 既有渲染链路微调"。

---

## 1. 改动总览

| # | 位置 | 改动 | 类型 |
|---|---|---|---|
| 1 | views/system/（5 个 Vue） | **删除** UserManageView/RoleManageView/MenuManageView/OnlineManageView/OpLogView | 删除 |
| 2 | api/system.ts | 保留（自定义按钮事件仍调这些 URL 的注册服务；管理页自身不再直接 import） | 不动 |
| 3 | router/index.ts | RESERVED_PATHS 移除 /system/* 五项（动态路由不再注册 vue: 页面，改由菜单 page: 跳 /preview） | 微调 |
| 4 | views/PreviewView.vue | 确认 pageId 路由参数渲染配置页（既有能力，验证即可） | 验证 |
| 5 | store/auth.ts | `loadMenus`/动态菜单无变化（page: 前缀走 /preview?pageId= 既有分支） | 验证 |
| 6 | registry 白名单 | 无需改——自定义服务注册进 lowcode_api_registry（DB 种子），前端启动 loadApiRegistry 自动加载 | 验证 |

> 手写页删除后 system/ 目录清空；MenuManageView 的"组件选择弹窗（vue:/page: 双模式）"随页面一起删除——**菜单管理页自己也变为配置页**（page:page_sys_menu），组件字段在表单里是普通文本输入（运维场景低频，输入 pageId 可接受；后续可在表单控件层做选择弹窗增强，不在本期）。

## 2. 页面 Schema 中的权限与交互要点

1. **按钮显隐 = permCode**：schema 的 toolbar.items[].permCode 填 `system:user:create` 等（与 lc_menu button 型权限码一致）——平台渲染时按 lowcode_page_perm/登录权限码过滤，替代手写页的 v-perm/hasPerm。
2. **新增/修改弹窗**：预设 add/edit 按钮（openPage `__current__` form 模式）→ PageModalHost 渲染 form 槽位 → 保存走 gatewayCall（EXTERNAL_API 桥）→ 关闭自动 grid.refresh()。编辑回填：passSelectedRow → 表单控件按 dbField 匹配。
3. **行级多操作（分配角色/重置密码/启用禁用/踢下线等）**：自定义工具栏按钮 + 沙盒事件 `grid.getSelectedRowsData()` 取勾选行 → `api.call` → `grid.refresh()`。分配菜单/查看用户需要**二级弹窗选择交互**：
   - 分配菜单：按钮事件 → api.call('sysRoleMenus') 拿已授权 id → **openPage 弹本页 form？不合适（是树勾选）** → 采用平台 PageModal 弹"菜单管理配置页（sys_menu 的 table 模式）+ selectMode 多选"——勾选节点集即授权集，保存按钮自定义事件调 sysRoleAssignMenus。菜单树多选若 selectMode 只支持表格行勾选，则菜单 schema 的 table 槽位用 data-grid 行勾选（勾选行=菜单记录，含父子平铺行）→ 可用。
   - 分配角色：同样弹 sys_role table 模式 selectMode 多选。
4. **高级查询/行筛选/分页/导出**：data-grid 既有配置项直出（advancedSearch:true、filterRow.visible:true、paging.pageSize:15、预设导出下拉按钮 visible:true）——满足"都用平台表格控件+高级查询+行筛选"的要求，且是配置产出。

## 3. 交互降级清单（相对手写页，需用户知悉）

| 手写页能力 | 配置页方案 | 差异 |
|---|---|---|
| 行内"操作列"五连按钮 | 勾选行 + 工具栏按钮（平台标准交互） | 多一步勾选；符合平台业务页一致性 |
| 状态列彩色 tag / 角色多 tag | 列 format 配置（文本映射 enabled→启用）或接受纯文本 | 彩色 tag 需列 cellTemplate 高级能力（配置页无 UI），首期文本 |
| 在线会话 10s 自动轮询 | 工具栏"刷新"按钮手动 | 运维低频，可接受 |
| 密码重置结果弹窗展示新密码 | api.showMessage 展示 | 等价 |
| 菜单管理左树右表布局 | data-grid 平铺（类型列区分 dir/menu/button + parent_id 列） | 层级感弱；后续可给 sys_menu 加树形表格（vxe grid treeConfig，列配置扩展，二期） |

## 4. 验证清单（D3 实施时逐项）

1. 菜单点击 → /preview?pageId=page_sys_user → 表格加载（分页 15/行筛选/高级查询/列宽调整/导出）；
2. 新增弹窗 → 填表 → 保存 → BCrypt 入库（查 lc_user.password $2a$ 前缀）→ 表格刷新出现新行；
3. 勾选行 → 修改 → 回填 → 保存（角色变更后原会话被踢）；
4. 自定义按钮：重置密码（弹新密码）/ 启用禁用 / 分配角色（弹 sys_role 多选）/ 踢下线；
5. 越权：非 ADMIN 账号（无 system:user:create 权限码）→ 新增按钮不显示 + 后端 @RequirePerm 403；
6. 删除手写页后 build 通过、无残留 import。