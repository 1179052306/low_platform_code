/**
 * 数据字典类型定义（FE-S10 P2 数据字典/枚举统一管理）。
 *
 * 字典 + 字典项两级结构，供表单控件引用字典做下拉选项。
 */

/** 字典定义 */
export interface DataDict {
  /** 字典编码（唯一，如 'order_status'） */
  code: string
  /** 字典名称 */
  name: string
  /** 描述 */
  description?: string
  /** 是否启用 */
  enabled: boolean

}

/** 字典项 */
export interface DataDictItem {
  /** 项 ID */
  id: string
  /** 所属字典编码 */
  dictCode: string
  /** 项值（存储值） */
  value: string
  /** 项标签（显示文本） */
  label: string
  /** 描述 */
  description?: string
  /** 是否启用 */
  enabled: boolean
  /** 排序号 */
  sortNo: number
}

/** 字典 + 字典项（管理界面用） */
export interface DataDictWithItems extends DataDict {
  items: DataDictItem[]
}