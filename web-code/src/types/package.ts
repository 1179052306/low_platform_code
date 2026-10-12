/**
 * 应用包类型定义（FE-S5 方向4）。
 * 从 types/schema.ts 拆出，保持设计文档文件结构对齐。
 */

/** 应用包定义 */
export interface AppPackage {
  /** 包 ID */
  id: string
  /** 包名称 */
  name: string
  /** 版本号 */
  version: string
  /** 包含的页面 ID 列表 */
  pageIds: string[]
  /** 包含的元数据键列表 */
  metaKeys: string[]
  /** 描述 */
  description?: string
  /** 创建时间 */
  createdAt: string
  /** 更新时间 */
  updatedAt: string
}