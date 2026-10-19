<script setup lang="ts">
/**
 * ElFileManagerBox — 替换 DxFileManager
 * 左侧 ElTree 目录树（懒加载）+ 右侧 ElTable 文件列表 + ElBreadcrumb 路径 + ElUpload 上传。
 * fileSystemProvider 契约：
 *   { getItems(parentPath?: string): Promise<FileItem[]> | FileItem[] }
 *   FileItem = { name, isDirectory, path, size?, modifiedDate?, extension? }
 */
import { computed, ref, watch } from 'vue'
import { ElTree, ElTable, ElTableColumn, ElBreadcrumb, ElBreadcrumbItem, ElUpload, ElButton, ElMessage } from 'element-plus'

interface FileItem {
  name: string
  isDirectory?: boolean
  path: string
  size?: number
  modifiedDate?: string
  extension?: string
}

interface FsProvider {
  getItems(parentPath?: string): Promise<FileItem[]> | FileItem[]
  upload?(parentPath: string, file: File): Promise<any>
}

const props = defineProps<{
  fileSystemProvider?: FsProvider
  allowedFileExtensions?: string[]
  readOnly?: boolean
  height?: number | string
  uploadUrl?: string
}>()

const emit = defineEmits<{
  itemClick: [e: { item: FileItem }]
  fileUploaded: [e: { file: any; parentPath: string }]
  valueChanged: [e: { value: string }]
}>()

const currentPath = ref<string>('')
const items = ref<FileItem[]>([])
const loading = ref(false)

interface DirNode { id: string; label: string; isLeaf: boolean; path: string }
const treeData = ref<DirNode[]>([])
const treeProps = { label: 'label', isLeaf: 'isLeaf' }

async function getProvider(): Promise<FsProvider | null> {
  return props.fileSystemProvider ?? null
}

async function loadDir(path: string): Promise<FileItem[]> {
  const p = await getProvider()
  if (!p?.getItems) return []
  return await p.getItems(path)
}

async function refresh() {
  loading.value = true
  try {
    items.value = await loadDir(currentPath.value)
  } finally {
    loading.value = false
  }
}

async function loadRootTree() {
  const p = await getProvider()
  if (!p?.getItems) { treeData.value = []; return }
  const root = await p.getItems('')
  treeData.value = root
    .filter((it) => it.isDirectory)
    .map((it) => ({ id: it.path, label: it.name, isLeaf: false, path: it.path }))
}
watch(() => props.fileSystemProvider, loadRootTree, { immediate: true })
watch(() => props.fileSystemProvider, refresh, { immediate: true })

async function loadNode(node: any, resolve: (data: DirNode[]) => void) {
  const children = await loadDir(node.data?.path ?? node.path ?? '')
  resolve(children.filter((it) => it.isDirectory).map((it) => ({
    id: it.path, label: it.name, isLeaf: false, path: it.path,
  })))
}

const pathSegments = computed(() => {
  if (!currentPath.value) return []
  return currentPath.value.split('/').filter(Boolean)
})

function navigate(idx: number) {
  if (idx < 0) currentPath.value = ''
  else currentPath.value = pathSegments.value.slice(0, idx + 1).join('/')
  refresh()
}

function onNodeClick(data: DirNode) {
  currentPath.value = data.path
  refresh()
}

function rowClick(row: FileItem) {
  emit('itemClick', { item: row })
  if (row.isDirectory) {
    currentPath.value = row.path
    refresh()
  }
}

function formatSize(size?: number): string {
  if (size == null) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function beforeUpload(file: File): boolean {
  if (props.allowedFileExtensions?.length) {
    const ext = file.name.split('.').pop()?.toLowerCase()
    if (!ext || !props.allowedFileExtensions.map((e) => e.toLowerCase()).includes(ext)) {
      ElMessage.error(`仅支持 ${props.allowedFileExtensions.join(', ')} 格式`)
      return false
    }
  }
  return true
}

function onUploadSuccess(response: any, file: any) {
  emit('fileUploaded', { file: { ...file, response }, parentPath: currentPath.value })
  refresh()
}

const heightStyle = computed(() => {
  const h = props.height
  return h == null ? '400px' : (typeof h === 'number' ? `${h}px` : h)
})
</script>

<template>
  <div class="el-file-manager-box">
    <ElBreadcrumb separator="/" class="fm-breadcrumb">
      <ElBreadcrumbItem @click="navigate(-1)">根目录</ElBreadcrumbItem>
      <ElBreadcrumbItem
        v-for="(seg, i) in pathSegments"
        :key="i"
        @click="navigate(i)"
      >{{ seg }}</ElBreadcrumbItem>
    </ElBreadcrumb>
    <div class="fm-body" :style="{ height: heightStyle }">
      <div class="fm-tree">
        <ElTree
          :data="treeData"
          :props="treeProps"
          node-key="id"
          lazy
          :load="loadNode"
          highlight-current
          @node-click="onNodeClick"
        />
      </div>
      <div class="fm-table">
        <ElTable
          :data="items"
          size="small"
          border
          @row-click="rowClick"
        >
          <ElTableColumn prop="name" label="名称" min-width="180">
            <template #default="{ row }">
              <span :class="{ 'fm-dir': row.isDirectory }">{{ row.name }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="大小" width="100" align="right">
            <template #default="{ row }">{{ formatSize(row.size) }}</template>
          </ElTableColumn>
          <ElTableColumn prop="modifiedDate" label="修改时间" width="160" />
          <ElTableColumn label="类型" width="80">
            <template #default="{ row }">{{ row.isDirectory ? '文件夹' : (row.extension ?? '文件') }}</template>
          </ElTableColumn>
        </ElTable>
      </div>
    </div>
    <div v-if="!readOnly" class="fm-upload">
      <ElUpload
        :action="uploadUrl ?? '#'"
        :before-upload="beforeUpload"
        :on-success="onUploadSuccess"
        :show-file-list="false"
        multiple
      >
        <ElButton size="small" type="primary">上传到当前目录</ElButton>
      </ElUpload>
    </div>
  </div>
</template>

<style scoped>
.el-file-manager-box {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  padding: 8px;
  box-sizing: border-box;
  background: var(--el-bg-color, #fff);
}
.fm-breadcrumb {
  padding: 4px 0;
}
.fm-body {
  display: flex;
  gap: 8px;
  overflow: hidden;
}
.fm-tree {
  width: 220px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter, #ebeef5);
  overflow: auto;
  padding-right: 4px;
}
.fm-table {
  flex: 1;
  overflow: auto;
}
.fm-dir {
  color: var(--el-color-primary, #409eff);
  font-weight: 500;
  cursor: pointer;
}
.fm-upload {
  display: flex;
  justify-content: flex-end;
}
</style>