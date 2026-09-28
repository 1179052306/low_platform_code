/**
 * DevExtreme 及自定义控件的事件元数据
 * 定义每种控件类型支持的可用事件
 */

/** 事件定义 */
export interface EventConfig {
  /** 事件名（Vue 模板中 @ 后面的部分，如 click / value-changed） */
  name: string
  /** 中文标签 */
  label: string
  /** 事件说明 */
  description?: string
}

/** 通用事件（所有控件都可能有的事件） */
const COMMON_EVENTS: EventConfig[] = [
  { name: 'click', label: '点击', description: '鼠标点击控件时触发' },
  { name: 'dblclick', label: '双击', description: '鼠标双击控件时触发' },
  { name: 'mouseenter', label: '鼠标移入', description: '鼠标进入控件区域时触发' },
  { name: 'mouseleave', label: '鼠标移出', description: '鼠标离开控件区域时触发' },
  { name: 'focusIn', label: '获得焦点', description: '控件获得焦点时触发' },
  { name: 'focusOut', label: '失去焦点', description: '控件失去焦点时触发' },
]

/** 按钮事件 */
const BUTTON_EVENTS: EventConfig[] = [
  { name: 'click', label: '点击', description: '按钮点击时触发' },
]

/** 值改变事件（表单类控件通用） */
const VALUE_CHANGE_EVENTS: EventConfig[] = [
  { name: 'valueChanged', label: '值改变', description: '控件值发生变化时触发' },
  { name: 'focusIn', label: '获得焦点', description: '控件获得焦点时触发' },
  { name: 'focusOut', label: '失去焦点', description: '控件失去焦点时触发' },
  { name: 'enterKey', label: '回车键', description: '按下回车键时触发' },
  { name: 'keyDown', label: '按键按下', description: '按下键盘按键时触发' },
  { name: 'keyUp', label: '按键释放', description: '释放键盘按键时触发' },
]

/** 数据表格事件 */
const DATAGRID_EVENTS: EventConfig[] = [
  { name: 'rowClick', label: '行点击', description: '点击数据行时触发' },
  { name: 'rowDblClick', label: '行双击', description: '双击数据行时触发' },
  { name: 'cellClick', label: '单元格点击', description: '点击单元格时触发' },
  { name: 'cellDblClick', label: '单元格双击', description: '双击单元格时触发' },
  { name: 'selectionChanged', label: '选择改变', description: '选中行变化时触发' },
  { name: 'contentReady', label: '内容就绪', description: '内容渲染完成时触发' },
  { name: 'rowInserting', label: '插入行前', description: '插入数据行之前触发' },
  { name: 'rowInserted', label: '插入行后', description: '插入数据行之后触发' },
  { name: 'rowUpdating', label: '更新行前', description: '更新数据行之前触发' },
  { name: 'rowUpdated', label: '更新行后', description: '更新数据行之后触发' },
  { name: 'rowRemoving', label: '删除行前', description: '删除数据行之前触发' },
  { name: 'rowRemoved', label: '删除行后', description: '删除数据行之后触发' },
  { name: 'initNewRow', label: '新建行', description: '初始化新行时触发' },
  { name: 'editorPreparing', label: '编辑器准备', description: '编辑器准备创建时触发' },
  { name: 'storeUpdate', label: '数据保存', description: '远程数据源保存修改行时调用，参数：key, values。返回 Promise' },
  { name: 'storeInsert', label: '数据新增', description: '远程数据源新增行时调用，参数：values。返回 Promise，需 resolve 新记录 key' },
  { name: 'storeRemove', label: '数据删除', description: '远程数据源删除行时调用，参数：key。返回 Promise' },
]

/** 图表事件 */
const CHART_EVENTS: EventConfig[] = [
  { name: 'pointClick', label: '数据点点击', description: '点击图表数据点时触发' },
  { name: 'pointHoverChanged', label: '数据点悬停', description: '数据点悬停状态变化时触发' },
  { name: 'seriesClick', label: '系列点击', description: '点击图表系列时触发' },
  { name: 'seriesHoverChanged', label: '系列悬停', description: '系列悬停状态变化时触发' },
  { name: 'legendClick', label: '图例点击', description: '点击图例项时触发' },
  { name: 'contentReady', label: '内容就绪', description: '图表渲染完成时触发' },
]

/** 列表/树形控件事件 */
const LIST_EVENTS: EventConfig[] = [
  { name: 'itemClick', label: '项点击', description: '点击列表项时触发' },
  { name: 'itemDblClick', label: '项双击', description: '双击列表项时触发' },
  { name: 'selectionChanged', label: '选择改变', description: '选中项变化时触发' },
  { name: 'contentReady', label: '内容就绪', description: '内容渲染完成时触发' },
]

/** 导航控件事件 */
const NAV_EVENTS: EventConfig[] = [
  { name: 'selectionChanged', label: '选择改变', description: '选中项变化时触发' },
  { name: 'itemClick', label: '项点击', description: '点击项时触发' },
  { name: 'contentReady', label: '内容就绪', description: '内容渲染完成时触发' },
]

/** 弹层控件事件 */
const OVERLAY_EVENTS: EventConfig[] = [
  { name: 'showing', label: '显示中', description: '弹出层正在显示时触发' },
  { name: 'shown', label: '已显示', description: '弹出层显示完成后触发' },
  { name: 'hiding', label: '隐藏中', description: '弹出层正在隐藏时触发' },
  { name: 'hidden', label: '已隐藏', description: '弹出层隐藏完成后触发' },
]

/** 自定义图片控件事件 */
const IMAGE_EVENTS: EventConfig[] = [
  { name: 'load', label: '加载完成', description: '图片加载完成时触发' },
  { name: 'error', label: '加载失败', description: '图片加载失败时触发' },
  { name: 'click', label: '点击', description: '点击图片时触发' },
]

/** 自定义链接控件事件 */
const LINK_EVENTS: EventConfig[] = [
  { name: 'click', label: '点击', description: '点击链接时触发' },
]

/** 自定义 iframe 控件事件 */
const IFRAME_EVENTS: EventConfig[] = [
  { name: 'load', label: '加载完成', description: '页面加载完成时触发' },
  { name: 'error', label: '加载失败', description: '页面加载失败时触发' },
]

/** 事件元数据注册表：type -> 该控件支持的可用事件列表 */
export const dxEvents: Record<string, EventConfig[]> = {
  // ---- 操作 ----
  'dx-button': BUTTON_EVENTS,
  'dx-button-group': [
    { name: 'selectionChanged', label: '选择改变', description: '按钮组选中项变化时触发' },
    { name: 'itemClick', label: '按钮点击', description: '点击按钮组中的按钮时触发' },
  ],
  'dx-speed-dial-action': BUTTON_EVENTS,
  'dx-progress-bar': [
    { name: 'complete', label: '完成', description: '进度达到100%时触发' },
    { name: 'valueChanged', label: '值改变', description: '进度值变化时触发' },
  ],

  // ---- 表单 ----
  'dx-text-box': VALUE_CHANGE_EVENTS,
  'dx-text-area': VALUE_CHANGE_EVENTS,
  'dx-number-box': VALUE_CHANGE_EVENTS,
  'dx-check-box': [
    { name: 'valueChanged', label: '值改变', description: '勾选状态变化时触发' },
  ],
  'dx-switch': [
    { name: 'valueChanged', label: '值改变', description: '开关状态变化时触发' },
  ],
  'dx-slider': [
    { name: 'valueChanged', label: '值改变', description: '滑块值变化时触发' },
  ],
  'dx-range-slider': [
    { name: 'valueChanged', label: '值改变', description: '范围值变化时触发' },
  ],
  'dx-calendar': [
    { name: 'valueChanged', label: '值改变', description: '选中日期变化时触发' },
  ],
  'dx-date-box': VALUE_CHANGE_EVENTS,
  'dx-date-range-box': [
    { name: 'valueChanged', label: '值改变', description: '日期范围变化时触发' },
  ],
  'dx-color-box': VALUE_CHANGE_EVENTS,
  'dx-select-box': [
    { name: 'valueChanged', label: '值改变', description: '选中值变化时触发' },
    { name: 'selectionChanged', label: '选择改变', description: '选择项变化时触发' },
  ],
  'dx-lookup': [
    { name: 'valueChanged', label: '值改变', description: '选中值变化时触发' },
  ],
  'dx-tag-box': [
    { name: 'valueChanged', label: '值改变', description: '选中标签变化时触发' },
    { name: 'selectionChanged', label: '选择改变', description: '选择项变化时触发' },
  ],
  'dx-autocomplete': VALUE_CHANGE_EVENTS,
  'dx-radio-group': [
    { name: 'valueChanged', label: '值改变', description: '选中项变化时触发' },
  ],
  'dx-drop-down-button': [
    { name: 'itemClick', label: '项点击', description: '点击下拉项时触发' },
    { name: 'opened', label: '已展开', description: '下拉菜单展开后触发' },
    { name: 'closed', label: '已收起', description: '下拉菜单收起后触发' },
  ],
  'dx-drop-down-box': [
    { name: 'valueChanged', label: '值改变', description: '值变化时触发' },
    { name: 'opened', label: '已展开', description: '下拉框展开后触发' },
    { name: 'closed', label: '已收起', description: '下拉框收起后触发' },
  ],
  'dx-file-uploader': [
    { name: 'valueChanged', label: '值改变', description: '文件列表变化时触发' },
    { name: 'uploadStarted', label: '开始上传', description: '文件开始上传时触发' },
    { name: 'uploadError', label: '上传错误', description: '上传出错时触发' },
    { name: 'uploaded', label: '上传完成', description: '文件上传完成时触发' },
    { name: 'progress', label: '上传进度', description: '上传进度变化时触发' },
  ],
  'dx-html-editor': VALUE_CHANGE_EVENTS,
  'dx-recurrence-editor': VALUE_CHANGE_EVENTS,

  // ---- 数据 ----
  'dx-data-grid': DATAGRID_EVENTS,
  'dx-tree-list': [
    { name: 'rowClick', label: '行点击', description: '点击数据行时触发' },
    { name: 'selectionChanged', label: '选择改变', description: '选中行变化时触发' },
    { name: 'contentReady', label: '内容就绪', description: '内容渲染完成时触发' },
    { name: 'itemExpanded', label: '节点展开', description: '节点展开时触发' },
    { name: 'itemCollapsed', label: '节点收起', description: '节点收起时触发' },
  ],
  'dx-list': LIST_EVENTS,
  'dx-tree-view': [
    { name: 'itemClick', label: '项点击', description: '点击树节点时触发' },
    { name: 'itemDblClick', label: '项双击', description: '双击树节点时触发' },
    { name: 'selectionChanged', label: '选择改变', description: '选中节点变化时触发' },
    { name: 'itemExpanded', label: '节点展开', description: '节点展开时触发' },
    { name: 'itemCollapsed', label: '节点收起', description: '节点收起时触发' },
    { name: 'contentReady', label: '内容就绪', description: '内容渲染完成时触发' },
  ],
  'dx-pivot-grid': [
    { name: 'cellClick', label: '单元格点击', description: '点击单元格时触发' },
    { name: 'contentReady', label: '内容就绪', description: '内容渲染完成时触发' },
  ],
  'dx-scheduler': [
    { name: 'appointmentClick', label: '日程点击', description: '点击日程项时触发' },
    { name: 'appointmentDblClick', label: '日程双击', description: '双击日程项时触发' },
    { name: 'appointmentAdding', label: '日程添加前', description: '添加日程之前触发' },
    { name: 'appointmentAdded', label: '日程添加后', description: '添加日程之后触发' },
    { name: 'appointmentUpdating', label: '日程更新前', description: '更新日程之前触发' },
    { name: 'appointmentUpdated', label: '日程更新后', description: '更新日程之后触发' },
    { name: 'appointmentDeleting', label: '日程删除前', description: '删除日程之前触发' },
    { name: 'appointmentDeleted', label: '日程删除后', description: '删除日程之后触发' },
  ],
  'dx-gantt': [
    { name: 'taskClick', label: '任务点击', description: '点击任务时触发' },
    { name: 'taskDblClick', label: '任务双击', description: '双击任务时触发' },
    { name: 'selectionChanged', label: '选择改变', description: '选中任务变化时触发' },
  ],
  'dx-file-manager': [
    { name: 'currentDirectoryChanged', label: '目录切换', description: '当前目录变化时触发' },
    { name: 'selectedFileOpened', label: '文件打开', description: '打开文件时触发' },
    { name: 'itemCreated', label: '创建项', description: '创建文件/文件夹时触发' },
    { name: 'itemDeleted', label: '删除项', description: '删除文件/文件夹时触发' },
    { name: 'itemRenamed', label: '重命名', description: '重命名文件/文件夹时触发' },
  ],

  // ---- 图表 ----
  'dx-chart': CHART_EVENTS,
  'dx-pie-chart': CHART_EVENTS,
  'dx-polar-chart': CHART_EVENTS,
  'dx-bar-gauge': [
    { name: 'tooltipCustomized', label: '提示框自定义', description: '提示框自定义时触发' },
  ],
  'dx-funnel': [
    { name: 'itemClick', label: '项点击', description: '点击漏斗项时触发' },
    { name: 'itemHoverChanged', label: '项悬停', description: '漏斗项悬停状态变化时触发' },
  ],
  'dx-sankey': [
    { name: 'nodeClick', label: '节点点击', description: '点击节点时触发' },
    { name: 'linkClick', label: '连线点击', description: '点击连线时触发' },
  ],

  // ---- 导航 ----
  'dx-tabs': NAV_EVENTS,
  'dx-tab-panel': [
    ...NAV_EVENTS,
    { name: 'itemTitleRendered', label: '标题渲染', description: '标签标题渲染时触发' },
  ],
  'dx-accordion': [
    { name: 'itemClick', label: '项点击', description: '点击折叠项时触发' },
    { name: 'selectionChanged', label: '选择改变', description: '选中项变化时触发' },
    { name: 'contentReady', label: '内容就绪', description: '内容渲染完成时触发' },
  ],
  'dx-menu': [
    { name: 'itemClick', label: '菜单项点击', description: '点击菜单项时触发' },
    { name: 'selectionChanged', label: '选择改变', description: '选中项变化时触发' },
    { name: 'itemHoverChanged', label: '项悬停', description: '菜单项悬停状态变化时触发' },
  ],
  'dx-toolbar': [
    { name: 'itemClick', label: '项点击', description: '点击工具栏项时触发' },
  ],
  'dx-gallery': [
    { name: 'selectionChanged', label: '选择改变', description: '选中项变化时触发' },
    { name: 'contentReady', label: '内容就绪', description: '内容渲染完成时触发' },
  ],
  'dx-pagination': [
    { name: 'pageChanged', label: '页码改变', description: '页码变化时触发' },
  ],
  'dx-chat': [
    { name: 'messageEntered', label: '消息发送', description: '发送消息时触发' },
  ],

  // ---- 弹层 ----
  'dx-popup': OVERLAY_EVENTS,
  'dx-toast': [
    { name: 'shown', label: '已显示', description: '提示显示后触发' },
    { name: 'hidden', label: '已隐藏', description: '提示隐藏后触发' },
  ],
  'dx-load-panel': OVERLAY_EVENTS,

  // ---- 自定义控件 ----
  'image': IMAGE_EVENTS,
  'link': LINK_EVENTS,
  'iframe': IFRAME_EVENTS,
  'dx-base-data': [
    { name: 'valueChanged', label: '值改变', description: '选中基础资料值变化时触发' },
    { name: 'selectionChanged', label: '选择改变', description: '选中记录变化时触发' },
  ],

  // ---- 布局控件（也支持基本事件） ----
  'el-row': COMMON_EVENTS,
  'el-splitter': [
    { name: 'resize', label: '面板调整', description: '分割面板大小调整时触发' },
    { name: 'resizeStart', label: '开始调整', description: '开始调整面板大小时触发' },
    { name: 'resizeEnd', label: '结束调整', description: '结束调整面板大小时触发' },
  ],
}

/** 获取某控件类型的可用事件列表 */
export function getEvents(type: string): EventConfig[] {
  return dxEvents[type] ?? []
}
