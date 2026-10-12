/**
 * 审批流类型定义 — 对标金蝶业务流设计器。
 * 从 types/schema.ts 拆出，保持设计文档文件结构对齐。
 */

/** 节点类型（12种，对标金蝶业务流节点工具箱） */
export type WorkflowNodeType =
  | 'start'
  | 'end'
  | 'userTask'
  | 'billTask'
  | 'autoTask'
  | 'messageTask'
  | 'exceptionGateway'
  | 'waitTask'
  | 'broadcastTask'
  | 'gateway'
  | 'subProcess'
  | 'rpaTask'

/** 审批方式 */
export type ApprovalMode = 'countersign' | 'orSign' | 'sequential'

/** 网关类型 */
export type GatewayType = 'exclusive' | 'parallel' | 'inclusive'

/** 流程类型 */
export type WorkflowType = 'bizFlow' | 'approvalFlow' | 'dataFlow'

/** 流程节点 */
export interface WorkflowNode {
  id: string
  type: WorkflowNodeType
  name: string
  /** 节点在画布上的坐标 */
  x?: number
  y?: number
  /** 人工节点：审批人角色列表 */
  approverRoles?: string[]
  /** 人工节点：审批人用户列表 */
  approverUsers?: string[]
  /** 人工节点：审批方式 */
  approvalMode?: ApprovalMode
  /** 单据节点：关联单据编码 */
  billCode?: string
  /** 单据节点：操作类型 */
  billAction?: string
  /** 自动节点：执行逻辑（插件标识或表达式） */
  executeExpr?: string
  /** 消息节点：消息类型 */
  messageType?: 'email' | 'sms' | 'im' | 'webhook'
  /** 消息节点：接收人 */
  messageReceivers?: string[]
  /** 消息节点：消息模板ID */
  messageTemplate?: string
  /** 网关节点：网关类型 */
  gatewayType?: GatewayType
  /** 网关节点：分支条件列表 */
  branches?: { id: string; condition: string; name: string }[]
  /** 条件表达式（type=exceptionGateway 时） */
  conditionExpr?: string
  /** 等待节点：等待时长（毫秒） */
  waitDuration?: number
  /** 等待节点：等待条件 */
  waitCondition?: string
  /** 广播节点：事件标识 */
  broadcastEvent?: string
  /** 子流程：子流程ID */
  subProcessId?: string
  /** 子流程：参数传递映射 */
  subProcessParams?: Record<string, string>
  /** RPA节点：机器人流程标识 */
  rpaFlowId?: string
  /** 节点描述 */
  description?: string
}

/** 流程连线 */
export interface WorkflowTransition {
  id: string
  from: string
  to: string
  /** 条件表达式（网关分支时） */
  condition?: string
  /** 连线标签 */
  label?: string
}

/** 流程参数（流程变量定义） */
export interface WorkflowParam {
  name: string
  type: 'string' | 'number' | 'boolean' | 'date' | 'object'
  defaultValue?: unknown
  description?: string
}

/** 启动条件项 */
export interface StartCondition {
  /** 单据编码 */
  billCode?: string
  /** 启动条件表达式 */
  condition?: string
  /** 是否允许 */
  allowed: boolean
}

/** 流程控制配置 */
export interface WorkflowControl {
  /** 按单据操作启动 */
  startByBillAction?: StartCondition[]
  /** 按事件启动 */
  startByEvent?: string[]
}

/** 流程定义 */
export interface WorkflowDef {
  id: string
  code: string
  name: string
  nodes: WorkflowNode[]
  transitions: WorkflowTransition[]
  enabled: boolean
  /** 流程标识 */
  flowKey?: string
  /** 流程编码（系统生成） */
  flowCode?: string
  /** 所属组织 */
  organization?: string
  /** 关联单据 */
  billCode?: string
  /** 流程类型 */
  workflowType?: WorkflowType
  /** 流程描述 */
  description?: string
  /** 流程参数 */
  params?: WorkflowParam[]
  /** 流程控制 */
  control?: WorkflowControl
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 审批操作类型 */
export type ApprovalActionType = 'approve' | 'reject' | 'transfer' | 'countersign'

/** 审批记录 */
export interface ApprovalRecord {
  id: string
  bizCode: string
  bizId: string
  instanceId: string
  currentNode: string
  action: ApprovalActionType
  operator: string
  comment?: string
  operatedAt: string
}

/** 待办项 */
export interface TodoItem {
  id: string
  bizCode: string
  bizId: string
  title: string
  workflowName: string
  currentNode: string
  initiator: string
  createdAt: string
}

/** 节点类型元信息（工具箱展示用） */
export interface NodeTypeMeta {
  type: WorkflowNodeType
  label: string
  icon: string
  color: string
  /** 是否可拖拽到画布 */
  draggable: boolean
}

/** 12种节点类型元信息 */
export const NODE_TYPE_LIST: NodeTypeMeta[] = [
  { type: 'start', label: '开始', icon: '▶', color: '#52c41a', draggable: false },
  { type: 'end', label: '结束', icon: '●', color: '#ff4d4f', draggable: false },
  { type: 'userTask', label: '人工', icon: '👤', color: '#1890ff', draggable: true },
  { type: 'billTask', label: '单据', icon: '📄', color: '#722ed1', draggable: true },
  { type: 'autoTask', label: '自动', icon: '⚙', color: '#13c2c2', draggable: true },
  { type: 'messageTask', label: '消息', icon: '✉', color: '#faad14', draggable: true },
  { type: 'exceptionGateway', label: '异常分支', icon: '⚡', color: '#fa541c', draggable: true },
  { type: 'waitTask', label: '等待', icon: '⏱', color: '#8c8c8c', draggable: true },
  { type: 'broadcastTask', label: '广播', icon: '📡', color: '#2f54eb', draggable: true },
  { type: 'gateway', label: '网关', icon: '◆', color: '#eb2f96', draggable: true },
  { type: 'subProcess', label: '子流程', icon: '📋', color: '#a0d911', draggable: true },
  { type: 'rpaTask', label: 'RPA', icon: '🤖', color: '#fadb14', draggable: true },
]