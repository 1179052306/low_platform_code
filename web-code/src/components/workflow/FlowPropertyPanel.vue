<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  ElTabs,
  ElTabPane,
  ElForm,
  ElFormItem,
  ElInput,
  ElSelect,
  ElOption,
  ElButton,
  ElSwitch,
} from 'element-plus'
import { t } from '@/store/lang'
import type {
  WorkflowDef,
  WorkflowNode,
  WorkflowParam,
  StartCondition,
  ApprovalMode,
  GatewayType,
  WorkflowType,
} from '@/types/workflow'

const props = defineProps<{
  workflow: WorkflowDef
  node: WorkflowNode | null
  roles: string[]
  billList: { code: string; name: string }[]
}>()

const emit = defineEmits<{
  (e: 'updateNode', node: WorkflowNode): void
  (e: 'updateWorkflow', wf: WorkflowDef): void
  (e: 'deleteNode'): void
}>()

const activeTab = ref('info')

const node = computed(() => props.node)

function updateNode(field: keyof WorkflowNode, value: unknown) {
  if (!node.value) return
  emit('updateNode', { ...node.value, [field]: value })
}

function updateWf(field: keyof WorkflowDef, value: unknown) {
  emit('updateWorkflow', { ...props.workflow, [field]: value })
}

/** 流程参数增删 */
function addParam() {
  const params = [...(props.workflow.params || []), { name: '', type: 'string' }]
  updateWf('params', params)
}
function updateParam(idx: number, field: keyof WorkflowParam, value: unknown) {
  const params = [...(props.workflow.params || [])]
  params[idx] = { ...params[idx], [field]: value }
  updateWf('params', params)
}
function removeParam(idx: number) {
  const params = (props.workflow.params || []).filter((_, i) => i !== idx)
  updateWf('params', params)
}

/** 启动条件增删 */
function addStartCondition() {
  const conds = [...(props.workflow.control?.startByBillAction || []), { allowed: true }]
  updateWf('control', { ...props.workflow.control, startByBillAction: conds })
}
function updateStartCondition(idx: number, field: keyof StartCondition, value: unknown) {
  const conds = [...(props.workflow.control?.startByBillAction || [])]
  conds[idx] = { ...conds[idx], [field]: value }
  updateWf('control', { ...props.workflow.control, startByBillAction: conds })
}
function removeStartCondition(idx: number) {
  const conds = (props.workflow.control?.startByBillAction || []).filter((_, i) => i !== idx)
  updateWf('control', { ...props.workflow.control, startByBillAction: conds })
}

/** 事件启动增删 */
function addEvent() {
  const events = [...(props.workflow.control?.startByEvent || []), '']
  updateWf('control', { ...props.workflow.control, startByEvent: events })
}
function updateEvent(idx: number, value: string) {
  const events = [...(props.workflow.control?.startByEvent || [])]
  events[idx] = value
  updateWf('control', { ...props.workflow.control, startByEvent: events })
}
function removeEvent(idx: number) {
  const events = (props.workflow.control?.startByEvent || []).filter((_, i) => i !== idx)
  updateWf('control', { ...props.workflow.control, startByEvent: events })
}

const paramTypes: WorkflowParam['type'][] = ['string', 'number', 'boolean', 'date', 'object']
const approvalModes: { value: ApprovalMode; label: string }[] = [
  { value: 'countersign', label: '会签' },
  { value: 'orSign', label: '或签' },
  { value: 'sequential', label: '依次审批' },
]
const gatewayTypes: { value: GatewayType; label: string }[] = [
  { value: 'exclusive', label: '排他网关' },
  { value: 'parallel', label: '并行网关' },
  { value: 'inclusive', label: '包容网关' },
]
const workflowTypes: { value: WorkflowType; label: string }[] = [
  { value: 'bizFlow', label: '业务流' },
  { value: 'approvalFlow', label: '审批流' },
  { value: 'dataFlow', label: '数据流' },
]
const messageTypes: NonNullable<WorkflowNode['messageType']>[] = ['email', 'sms', 'im', 'webhook']

/** 网关分支增删改 */
function addBranch() {
  if (!node.value) return
  const branches = [...(node.value.branches || []), { id: `br_${Date.now()}`, condition: '', name: '' }]
  updateNode('branches', branches)
}
function updateBranch(idx: number, field: string, value: string) {
  if (!node.value) return
  const branches = [...(node.value.branches || [])]
  branches[idx] = { ...branches[idx], [field]: value }
  updateNode('branches', branches)
}
function removeBranch(idx: number) {
  if (!node.value) return
  const branches = (node.value.branches || []).filter((_, i) => i !== idx)
  updateNode('branches', branches)
}
</script>

<template>
  <div class="flow-prop-panel">
    <ElTabs v-model="activeTab" class="flow-prop-tabs">
      <!-- 流程信息 / 节点属性 -->
      <ElTabPane :label="t('流程信息')" name="info">
        <!-- 选中节点时显示节点属性 -->
        <template v-if="node">
          <div class="flow-prop-section-title">{{ t('节点属性') }}</div>
          <ElForm label-width="80px" size="small">
            <ElFormItem :label="t('名称')">
              <ElInput :model-value="node.name" @update:model-value="(v: string) => updateNode('name', v)" />
            </ElFormItem>
            <ElFormItem :label="t('描述')">
              <ElInput
                type="textarea"
                :rows="2"
                :model-value="node.description || ''"
                @update:model-value="(v: string) => updateNode('description', v)"
              />
            </ElFormItem>

            <!-- 人工节点 -->
            <template v-if="node.type === 'userTask'">
              <ElFormItem :label="t('审批角色')">
                <ElSelect
                  :model-value="node.approverRoles || []"
                  multiple
                  filterable
                  style="width: 100%"
                  @update:model-value="(v: string[]) => updateNode('approverRoles', v)"
                >
                  <ElOption v-for="r in roles" :key="r" :label="r" :value="r" />
                </ElSelect>
              </ElFormItem>
              <ElFormItem :label="t('审批人')">
                <ElSelect
                  :model-value="node.approverUsers || []"
                  multiple
                  filterable
                  allow-create
                  style="width: 100%"
                  @update:model-value="(v: string[]) => updateNode('approverUsers', v)"
                />
              </ElFormItem>
              <ElFormItem :label="t('审批方式')">
                <ElSelect
                  :model-value="node.approvalMode"
                  style="width: 100%"
                  @update:model-value="(v: ApprovalMode) => updateNode('approvalMode', v)"
                >
                  <ElOption v-for="m in approvalModes" :key="m.value" :label="t(m.label)" :value="m.value" />
                </ElSelect>
              </ElFormItem>
            </template>

            <!-- 网关节点 -->
            <template v-if="node.type === 'gateway' || node.type === 'exceptionGateway'">
              <ElFormItem :label="t('网关类型')" v-if="node.type === 'gateway'">
                <ElSelect
                  :model-value="node.gatewayType || 'exclusive'"
                  style="width: 100%"
                  @update:model-value="(v: GatewayType) => updateNode('gatewayType', v)"
                >
                  <ElOption v-for="g in gatewayTypes" :key="g.value" :label="t(g.label)" :value="g.value" />
                </ElSelect>
              </ElFormItem>
              <ElFormItem :label="t('条件表达式')">
                <ElInput
                  type="textarea"
                  :rows="2"
                  :model-value="node.conditionExpr || ''"
                  placeholder="如: amount > 10000"
                  @update:model-value="(v: string) => updateNode('conditionExpr', v)"
                />
              </ElFormItem>
              <!-- 分支列表（排他/包容网关） -->
              <ElFormItem :label="t('分支条件')" v-if="node.gatewayType !== 'parallel'">
                <div class="gateway-branch-list">
                  <div v-for="(br, idx) in (node.branches || [])" :key="idx" class="gateway-branch-row">
                    <ElInput
                      :model-value="br.name"
                      size="small"
                      style="width: 80px"
                      :placeholder="t('名称')"
                      @update:model-value="(v: string) => updateBranch(idx, 'name', v)"
                    />
                    <ElInput
                      :model-value="br.condition"
                      size="small"
                      style="flex: 1"
                      :placeholder="t('条件')"
                      @update:model-value="(v: string) => updateBranch(idx, 'condition', v)"
                    />
                    <ElButton type="danger" link size="small" @click="removeBranch(idx)">×</ElButton>
                  </div>
                  <ElButton size="small" @click="addBranch">+ {{ t('新增分支') }}</ElButton>
                </div>
              </ElFormItem>
            </template>

            <!-- 自动节点 -->
            <template v-if="node.type === 'autoTask'">
              <ElFormItem :label="t('执行逻辑')">
                <ElInput
                  type="textarea"
                  :rows="3"
                  :model-value="node.executeExpr || ''"
                  placeholder="插件标识或表达式"
                  @update:model-value="(v: string) => updateNode('executeExpr', v)"
                />
              </ElFormItem>
            </template>

            <!-- 单据节点 -->
            <template v-if="node.type === 'billTask'">
              <ElFormItem :label="t('单据')">
                <ElSelect
                  :model-value="node.billCode"
                  filterable
                  style="width: 100%"
                  @update:model-value="(v: string) => updateNode('billCode', v)"
                >
                  <ElOption v-for="b in billList" :key="b.code" :label="b.name" :value="b.code" />
                </ElSelect>
              </ElFormItem>
              <ElFormItem :label="t('操作')">
                <ElSelect
                  :model-value="node.billAction || ''"
                  filterable
                  allow-create
                  style="width: 100%"
                  @update:model-value="(v: string) => updateNode('billAction', v)"
                >
                  <ElOption label="创建" value="create" />
                  <ElOption label="提交" value="submit" />
                  <ElOption label="审核" value="audit" />
                  <ElOption label="反审" value="unaudit" />
                  <ElOption label="删除" value="delete" />
                </ElSelect>
              </ElFormItem>
            </template>

            <!-- 消息节点 -->
            <template v-if="node.type === 'messageTask'">
              <ElFormItem :label="t('消息类型')">
                <ElSelect
                  :model-value="node.messageType || 'email'"
                  style="width: 100%"
                  @update:model-value="(v: string) => updateNode('messageType', v)"
                >
                  <ElOption label="邮件" value="email" />
                  <ElOption label="短信" value="sms" />
                  <ElOption label="IM" value="im" />
                  <ElOption label="Webhook" value="webhook" />
                </ElSelect>
              </ElFormItem>
              <ElFormItem :label="t('接收人')">
                <ElSelect
                  :model-value="node.messageReceivers || []"
                  multiple
                  filterable
                  allow-create
                  style="width: 100%"
                  @update:model-value="(v: string[]) => updateNode('messageReceivers', v)"
                />
              </ElFormItem>
              <ElFormItem :label="t('模板ID')">
                <ElInput
                  :model-value="node.messageTemplate || ''"
                  @update:model-value="(v: string) => updateNode('messageTemplate', v)"
                />
              </ElFormItem>
            </template>

            <!-- 等待节点 -->
            <template v-if="node.type === 'waitTask'">
              <ElFormItem :label="t('等待时长')">
                <ElInput
                  :model-value="String(node.waitDuration ?? '')"
                  placeholder="毫秒"
                  @update:model-value="(v: string) => updateNode('waitDuration', Number(v) || 0)"
                />
              </ElFormItem>
              <ElFormItem :label="t('等待条件')">
                <ElInput
                  type="textarea"
                  :rows="2"
                  :model-value="node.waitCondition || ''"
                  placeholder="留空则按时长等待"
                  @update:model-value="(v: string) => updateNode('waitCondition', v)"
                />
              </ElFormItem>
            </template>

            <!-- 广播节点 -->
            <template v-if="node.type === 'broadcastTask'">
              <ElFormItem :label="t('事件标识')">
                <ElInput
                  :model-value="node.broadcastEvent || ''"
                  placeholder="如: order.created"
                  @update:model-value="(v: string) => updateNode('broadcastEvent', v)"
                />
              </ElFormItem>
            </template>

            <!-- 子流程节点 -->
            <template v-if="node.type === 'subProcess'">
              <ElFormItem :label="t('子流程ID')">
                <ElInput
                  :model-value="node.subProcessId || ''"
                  @update:model-value="(v: string) => updateNode('subProcessId', v)"
                />
              </ElFormItem>
              <ElFormItem :label="t('参数映射')">
                <ElInput
                  type="textarea"
                  :rows="3"
                  :model-value="JSON.stringify(node.subProcessParams || {}, null, 2)"
                  placeholder='{"parentField": "childParam"}'
                  @update:model-value="(v: string) => { try { updateNode('subProcessParams', JSON.parse(v)) } catch { /* 忽略解析错误 */ } }"
                />
              </ElFormItem>
            </template>

            <!-- RPA节点 -->
            <template v-if="node.type === 'rpaTask'">
              <ElFormItem :label="t('RPA流程')">
                <ElInput
                  :model-value="node.rpaFlowId || ''"
                  placeholder="机器人流程标识"
                  @update:model-value="(v: string) => updateNode('rpaFlowId', v)"
                />
              </ElFormItem>
            </template>

            <ElFormItem>
              <ElButton
                type="danger"
                size="small"
                :disabled="node.type === 'start' || node.type === 'end'"
                @click="emit('deleteNode')"
              >
                {{ t('删除节点') }}
              </ElButton>
            </ElFormItem>
          </ElForm>
        </template>

        <!-- 未选中节点时显示流程基本信息 -->
        <template v-else>
          <div class="flow-prop-section-title">{{ t('流程信息') }}</div>
          <ElForm label-width="80px" size="small">
            <ElFormItem :label="t('流程名称')">
              <ElInput :model-value="workflow.name" @update:model-value="(v: string) => updateWf('name', v)" />
            </ElFormItem>
            <ElFormItem :label="t('流程编码')">
              <ElInput :model-value="workflow.code" @update:model-value="(v: string) => updateWf('code', v)" />
            </ElFormItem>
            <ElFormItem :label="t('流程类型')">
              <ElSelect
                :model-value="workflow.workflowType || 'approvalFlow'"
                style="width: 100%"
                @update:model-value="(v: WorkflowType) => updateWf('workflowType', v)"
              >
                <ElOption v-for="w in workflowTypes" :key="w.value" :label="t(w.label)" :value="w.value" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem :label="t('关联单据')">
              <ElSelect
                :model-value="workflow.billCode"
                filterable
                clearable
                style="width: 100%"
                @update:model-value="(v: string) => updateWf('billCode', v)"
              >
                <ElOption v-for="b in billList" :key="b.code" :label="b.name" :value="b.code" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem :label="t('描述')">
              <ElInput
                type="textarea"
                :rows="3"
                :model-value="workflow.description || ''"
                @update:model-value="(v: string) => updateWf('description', v)"
              />
            </ElFormItem>
            <ElFormItem :label="t('启用')">
              <ElSwitch
                :model-value="workflow.enabled"
                @update:model-value="(v: string | number | boolean) => updateWf('enabled', Boolean(v))"
              />
            </ElFormItem>
          </ElForm>
        </template>
      </ElTabPane>

      <!-- 流程控制 -->
      <ElTabPane :label="t('流程控制')" name="control">
        <div class="flow-prop-section-title">{{ t('按单据操作启动') }}</div>
        <div
          v-for="(cond, idx) in workflow.control?.startByBillAction || []"
          :key="idx"
          class="flow-prop-row"
        >
          <ElSelect
            :model-value="cond.billCode"
            filterable
            placeholder="单据"
            style="width: 100px"
            @update:model-value="(v: string) => updateStartCondition(idx, 'billCode', v)"
          >
            <ElOption v-for="b in billList" :key="b.code" :label="b.name" :value="b.code" />
          </ElSelect>
          <ElInput
            :model-value="cond.condition"
            placeholder="条件"
            style="flex: 1"
            @update:model-value="(v: string) => updateStartCondition(idx, 'condition', v)"
          />
          <ElSwitch
            :model-value="cond.allowed"
            @update:model-value="(v: string | number | boolean) => updateStartCondition(idx, 'allowed', Boolean(v))"
          />
          <ElButton type="danger" size="small" circle @click="removeStartCondition(idx)">
            ×
          </ElButton>
        </div>
        <ElButton size="small" plain style="width: 100%; margin-top: 6px" @click="addStartCondition">
          + {{ t('添加启动条件') }}
        </ElButton>

        <div class="flow-prop-section-title" style="margin-top: 16px">{{ t('按事件启动') }}</div>
        <div
          v-for="(evt, idx) in workflow.control?.startByEvent || []"
          :key="idx"
          class="flow-prop-row"
        >
          <ElInput
            :model-value="evt"
            placeholder="事件标识"
            style="flex: 1"
            @update:model-value="(v: string) => updateEvent(idx, v)"
          />
          <ElButton type="danger" size="small" circle @click="removeEvent(idx)">
            ×
          </ElButton>
        </div>
        <ElButton size="small" plain style="width: 100%; margin-top: 6px" @click="addEvent">
          + {{ t('添加事件') }}
        </ElButton>
      </ElTabPane>

      <!-- 流程参数 -->
      <ElTabPane :label="t('流程参数')" name="params">
        <div
          v-for="(p, idx) in workflow.params || []"
          :key="idx"
          class="flow-prop-param"
        >
          <ElForm label-width="60px" size="small">
            <ElFormItem :label="t('名称')">
              <ElInput :model-value="p.name" @update:model-value="(v: string) => updateParam(idx, 'name', v)" />
            </ElFormItem>
            <ElFormItem :label="t('类型')">
              <ElSelect
                :model-value="p.type"
                style="width: 100%"
                @update:model-value="(v: WorkflowParam['type']) => updateParam(idx, 'type', v)"
              >
                <ElOption v-for="pt in paramTypes" :key="pt" :label="pt" :value="pt" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem :label="t('默认值')">
              <ElInput :model-value="String(p.defaultValue ?? '')" @update:model-value="(v: string) => updateParam(idx, 'defaultValue', v)" />
            </ElFormItem>
            <ElFormItem :label="t('说明')">
              <ElInput :model-value="p.description || ''" @update:model-value="(v: string) => updateParam(idx, 'description', v)" />
            </ElFormItem>
          </ElForm>
          <ElButton type="danger" size="small" circle class="flow-prop-param-del" @click="removeParam(idx)">
            ×
          </ElButton>
        </div>
        <ElButton size="small" plain style="width: 100%" @click="addParam">
          + {{ t('添加参数') }}
        </ElButton>
      </ElTabPane>
    </ElTabs>
  </div>
</template>

<style scoped>
.flow-prop-panel {
  height: 100%;
  overflow-y: auto;
  background: #fff;
}
.flow-prop-tabs {
  padding: 0 8px;
}
.flow-prop-section-title {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  padding: 8px 0 6px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 8px;
}
.flow-prop-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.flow-prop-param {
  position: relative;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px 8px 0;
  margin-bottom: 8px;
}
.flow-prop-param-del {
  position: absolute;
  top: 4px;
  right: 4px;
}
.gateway-branch-list { display: flex; flex-direction: column; gap: 6px; width: 100%; }
.gateway-branch-row { display: flex; align-items: center; gap: 4px; }
</style>