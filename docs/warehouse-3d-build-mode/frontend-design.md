# 3D 仓库搭建模式（类我的世界式）— 前端设计

> 目标：在现有 3D 仓库基础上，增加"搭建模式"，用户可在 3D 场景里逐级自助搭建
> 园区 → 仓库 → 楼层 → 库区 → 货架 → 库位，并点击库位查看存放物料。
> 借鉴我的世界的"自由搭建、逐级进入、所见即所得"思路，非体素方块游戏。

## 一、现状与复用

| 已有资产 | 复用方式 |
|---|---|
| `types/warehouse-3d.ts` 五级数据模型 | 直接复用，无需改类型 |
| `store/warehouse3d.ts` 的 `addArea/addRack/generateLocations` | 搭建放置时调用，不重写数据层 |
| `WarehouseRenderer` + `ParkBuilder` + `LayoutBuilder` | 渲染复用，新增 `BuildController` 做射线投射 |
| `SceneContainer` 的 `locationClick/warehouseClick` 事件 | 扩展为层级进入 + 库位物料查询 |
| `Location.productCode/status/utilization` | 库位内容展示直接用 |

**关键决策：搭建只改"交互入口"，数据层和渲染层尽量不动。**

## 二、层级进入（面包屑导航）

借鉴 MC "进入维度"思路，3D 视图有 5 个层级 scope：

```
园区(scope=park) → 仓库(scope=warehouse) → 楼层(scope=floor) → 库区(scope=area) → 货架(scope=rack)
```

### 交互
- 顶部新增 `BreadcrumbNav`：`园区A / 仓库W1 / 2F / E区`，每段可点击回上级
- 双击 3D 对象进入下一级：
  - 双击仓库楼 → scope=warehouse，渲染该仓库内部（loadLayout）
  - 双击楼层切片 → scope=floor，切到该楼层（setFloor）
  - 双击库区框 → scope=area，相机聚焦该库区
- 进入下级时相机自动 fitToView 该对象

### 状态
`store/warehouse3d.ts` 新增：
```ts
const buildScope = ref<{ level: 'park'|'warehouse'|'floor'|'area'|'rack', id: string }>({ level: 'park', id: '' })
function enterScope(level, id)  // 进入下级
function exitScope()            // 返回上级
```

## 三、搭建模式（Build Mode）

### 3.1 模式开关
- `Toolbar3D` 新增"搭建"按钮，切换 `buildMode`（与现有 `editMode` 并列，搭建模式开启时强制 editMode=true）
- 搭建模式开启：左侧弹出 `BuildPalette`（积木抽屉），鼠标变十字准星，OrbitControls 左键改为放置（右键仍可旋转视角）

### 3.2 积木抽屉 BuildPalette
按当前 scope 显示可放置的积木：

| scope | 可放置积木 |
|---|---|
| park | 仓库楼 |
| warehouse | 楼层（实际是切楼层视图，不"放置"）/ 库区框 / 障碍物 |
| floor | 库区框 / 货架 / 障碍物 |
| area | 货架 |
| rack | 库位格（一般自动生成，少用手动补） |

每个积木项：图标 + 名称 + 默认尺寸，点击选中后高亮。

### 3.3 射线投射 + 预览 + 放置（核心）

新增 `BuildController.ts`（在 `components/warehouse-3d/`）：

```
选中积木 → mousemove:
  1. raycaster 从相机投射鼠标到当前 scope 的地面 Mesh
  2. 命中点 → 吸附网格（snap to 1m 网格）→ 半透明预览 Mesh 跟随
  3. 检测碰撞（与已有对象 AABB 相交）→ 红色=不可放 / 绿色=可放
click:
  1. 若预览为绿色 → 调用 store 对应 add 方法（addArea/addRack/...）
  2. 放置后预览不消失，可连续放置同类型（按 Esc 退出）
  3. 放置后立即 reRender（store 变更触发 watch → renderer 重载）
```

### 3.4 各积木放置参数

| 积木 | 放置时参数 | 调用 |
|---|---|---|
| 仓库楼 | x,z + 默认 30×20×6m, 3层 | `addWarehouse()`（store 新增） |
| 库区框 | x,z + 默认 15×10m, 类型=storage | `addArea()` |
| 货架 | x,z + 默认 3层×10格, 朝向自动（沿长边） | `addRack()` + `generateLocations()` |
| 障碍物 | x,z + 类型选择（柱/墙） | `addObstacle()`（store 新增） |

放置后右下角浮窗提示"已放置 库区 E区 (15×10m)"，可撤销（Ctrl+Z，维护一个 undo 栈）。

### 3.5 删除/移动
- 搭建模式下单击选中对象 → 高亮 + 浮动操作条（删除/移动/复制）
- 删除：调 store 删除方法，reRender
- 移动：进入拖拽态，raycaster 跟随，松手改 x/z 后 reRender

## 四、库位内容展示

### 4.1 库位点击行为分模式
- 只读/编辑模式：点击库位 → 右侧"库位详情"tab（现有）
- 搭建模式：点击库位 → 同上 + 弹出 `LocationStockPanel`

### 4.2 LocationStockPanel（库位物料面板）
- 弹窗或右侧抽屉，显示该库位存放的物料清单：

```
库位 R-E01-2-3  状态：满  利用率：100%
─────────────────────────────
物料编码    物料名称      数量    单位    入库时间
SKU001     螺丝M6       500     个      2026-10-20
SKU002     垫片D6       200     个      2026-10-21
```

- 库位颜色按 `status`：empty=灰 / partial=黄 / full=绿 / locked=红 / reserved=蓝
- `LegendPanel` 扩展这些状态色图例

### 4.3 Mock 数据
`store/warehouse3d.ts` 新增：
```ts
const mockLocationStock: Record<string, StockItem[]> = { ... }
function getLocationStock(locationId): StockItem[]  // 先查 mock，后续替换为 API
```
mock 数据覆盖几种状态：空库位、部分满、满、锁定，方便演示。

## 五、新增/改动文件清单

### 新增
| 文件 | 职责 |
|---|---|
| `components/warehouse-3d/BuildController.ts` | 射线投射+预览+放置核心逻辑 |
| `components/warehouse-3d/BuildPalette.vue` | 积木抽屉 UI |
| `components/warehouse-3d/BreadcrumbNav.vue` | 层级面包屑 |
| `components/warehouse-3d/LocationStockPanel.vue` | 库位物料面板 |
| `components/warehouse-3d/ObjectActionBar.vue` | 选中对象浮动操作条 |

### 改动
| 文件 | 改动 |
|---|---|
| `store/warehouse3d.ts` | 加 buildScope/buildMode/undoStack/mockStock + addWarehouse/addObstacle/enterScope/exitScope |
| `types/warehouse-3d.ts` | 加 StockItem 类型 |
| `WarehouseRenderer.ts` | 暴露 ground mesh 给 BuildController；加 setBuildMode 控制 OrbitControls 左键 |
| `SceneContainer.vue` | 透传 buildMode + 暴露 enterScope 事件 |
| `Warehouse3DView.vue` | 集成 BuildPalette/BreadcrumbNav/LocationStockPanel |
| `Toolbar3D.vue` | 加"搭建"按钮 |

## 六、交互流程图

```
进入页面 → 默认只读看园区
  └─ 点"搭建" → 进搭建模式，左侧出积木抽屉
       ├─ 选"仓库楼" → 地面投射预览 → 点击放置 → 园区多一栋楼
       ├─ 双击某仓库楼 → 进仓库内部 scope=warehouse
       │    ├─ 选"库区框" → 放置库区
       │    ├─ 选"货架" → 放置货架（自动生成库位）
       │    └─ 双击库区 → scope=area，聚焦该库区
       └─ 任意时候点库位 → 弹物料面板看放了啥
```

## 七、不做的事（明确边界）

- 不做 MC 体素方块/挖掘/合成系统
- 不做第一人称漫游（用 OrbitControls 上帝视角即可）
- 不做物料入库操作（只展示，入库走 WMS 业务系统）
- 后端接口本轮不接（mock 数据），下一轮再接真实库存

## 八、验证方式

1. `npm run build` 通过（AGENTS.md 要求）
2. 手动验证：进搭建模式 → 放一个仓库 → 进去放库区 → 放货架 → 看到自动生成的库位 → 点库位看到 mock 物料
3. 撤销/删除/面包屑回退均可用