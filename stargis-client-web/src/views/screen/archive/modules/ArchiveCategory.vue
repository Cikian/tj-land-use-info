<template>
  <!--
    ArchiveCategory 档案类别管理（方案 2.3.2 第 1 项）
    --------------------------------
    左树 + 右详情。类别是一棵「主键路径（path）」树：
      - 只有**末级类别**可以挂卷内文件，所以叶子节点在树上用不同图标区分；
      - 停用会级联停用整棵子树，因此停用前会明确提示影响范围；
      - 拖拽既改排序也改上级，落库分两步：先 edit（改 parentId）再 sort（重排 sortNo）。

    为什么拖拽要「先本地乐观移动、再落库」：
      拖完等接口回来再刷新会让整棵树闪一下，体验很差；所以先在本地把树改好，
      接口失败时再重新拉取整棵树纠正（reloadTree），保证界面最终与后端一致。
  -->
  <div class="archive-category">
    <!-- ================= 左：类别树 ================= -->
    <screen-panel class="archive-category__tree-panel" title="类别树">
      <template #extra>
        <screen-button
          type="primary"
          size="sm"
          icon="plus"
          @click="handleAddRoot"
        >
          新建一级类别
        </screen-button>
        <screen-button size="sm" icon="reload" :loading="loading" @click="loadTree">刷新</screen-button>
      </template>

      <div class="archive-category__tools">
        <screen-input
          v-model="keyword"
          size="sm"
          icon="search"
          clearable
          placeholder="搜索类别名称 / 编码 / 别名"
          aria-label="搜索档案类别"
          @enter="loadTree"
          @clear="loadTree"
        />
        <screen-radio-group
          v-model="statusFilter"
          size="sm"
          :options="statusFilterOptions"
          aria-label="按启用状态筛选类别"
          @change="loadTree"
        />
        <button type="button" class="archive-category__toggle" @click="toggleExpandAll">
          {{ isAllExpanded ? '收起全部' : '展开全部' }}
        </button>
      </div>

      <div class="archive-category__tree-wrap">
        <screen-tree
          v-if="treeData.length"
          :nodes="treeData"
          :selected-keys="selectedKeys"
          :expanded-keys.sync="expandedKeys"
          draggable
          :draggable-guard="draggableGuard"
          :highlight-keyword="keyword"
          aria-label="档案类别树"
          @select="handleSelect"
          @drop="handleDrop"
        />
        <screen-empty
          v-else
          :text="isFiltering ? '没有匹配的类别' : '还没有档案类别'"
          :description="isFiltering ? '试试清空搜索词或切换状态筛选' : '点击右上角「新建一级类别」开始搭建类别体系'"
          size="sm"
        />
      </div>

      <template #footer>
        <span class="archive-category__stats">
          共 <b>{{ stats.total }}</b> 个类别
          · 一级 <b>{{ stats.root }}</b>
          · 启用 <b>{{ stats.enabled }}</b>
          <template v-if="stats.disabled"> · 停用 <b class="is-warn">{{ stats.disabled }}</b></template>
        </span>
      </template>
    </screen-panel>

    <!-- ================= 右：类别详情 ================= -->
    <screen-panel class="archive-category__detail-panel" :bar="false">
      <template #title>
        <span class="archive-category__detail-title">
          <screen-icon name="layers" :size="14" />
          {{ selected ? '类别详情' : '请选择左侧类别' }}
        </span>
      </template>

      <!--
        具名插槽必须直接挂在组件根层，不能包在 v-if 里
        （Vue 2.6 会报 "v-slot can only appear at the root level"），
        所以这里把 v-if 下沉到插槽内容以及默认插槽各自处理。
      -->
      <template #extra>
        <template v-if="selected">
          <screen-button
            size="sm"
            icon="plus"
            :disabled="selected.status === 0"
            @click="handleAddChild"
          >
            新增子类别
          </screen-button>
          <screen-button
            size="sm"
            icon="edit"
            @click="handleEdit(selected)"
          >
            编辑
          </screen-button>
          <screen-button
            size="sm"
            icon="arrow-up"
            :disabled="!canMoveUp"
            aria-label="上移"
            @click="moveSibling(-1)"
          >
            上移
          </screen-button>
          <screen-button
            size="sm"
            icon="arrow-down"
            :disabled="!canMoveDown"
            aria-label="下移"
            @click="moveSibling(1)"
          >
            下移
          </screen-button>
          <screen-button
            size="sm"
            :icon="selected.status === 1 ? 'lock' : 'check'"
            :loading="statusLoading"
            @click="handleToggleStatus"
          >
            {{ selected.status === 1 ? '停用' : '启用' }}
          </screen-button>

          <screen-popconfirm
            :title="deleteTitle"
            :description="deleteDisabledReason"
            width="300"
            @confirm="handleDelete"
          >
            <screen-button
              type="danger"
              size="sm"
              icon="trash"
              :disabled="!!deleteDisabledReason"
            >
              移除
            </screen-button>
          </screen-popconfirm>
        </template>
      </template>

      <!-- 默认插槽：选中类别时展示详情 -->
      <template v-if="selected">
        <!-- 详情头部：全路径 + 状态 -->
        <div class="archive-category__head">
          <h3 class="archive-category__head-name">{{ selected.fullPathName || selected.name }}</h3>
          <div class="archive-category__head-tags">
            <screen-tag :tone="selected.status === 1 ? 'success' : 'muted'" size="sm">
              {{ selected.status === 1 ? '启用' : '停用' }}
            </screen-tag>
            <screen-tag :tone="selected.isLeaf === 1 ? 'info' : 'muted'" size="sm">
              {{ selected.isLeaf === 1 ? '末级类别（可挂文件）' : `含 ${childCount} 个子类别` }}
            </screen-tag>
            <screen-tag v-if="showArchiveCount" tone="accent" size="sm">
              本类别文件数 {{ selected.archiveCount === null || selected.archiveCount === undefined ? '—' : selected.archiveCount }}
            </screen-tag>
          </div>
        </div>

        <screen-descriptions :items="detailItems" :columns="3" :bordered="true" label-width="96px" />

        <!-- 直接子类别 -->
        <section class="archive-category__children">
          <h4 class="archive-category__children-title">
            直接子类别
            <span class="archive-category__children-sub">共 {{ childCount }} 个</span>
          </h4>

          <screen-data-table
            v-if="childRows.length"
            :columns="childColumns"
            :data="childRows"
            row-key="id"
            :min-width="720"
            :animated="false"
            :max-height="220"
          >
            <template #childStatus="{ row }">
              <screen-tag :tone="row.status === 1 ? 'success' : 'muted'" size="sm">
                {{ row.status === 1 ? '启用' : '停用' }}
              </screen-tag>
            </template>

            <template #childArchive="{ row }">
              {{ row.archiveCount === null || row.archiveCount === undefined ? '—' : row.archiveCount }}
            </template>

            <template #childAction="{ row }">
              <span class="archive-category__child-actions">
                <button type="button" class="archive-category__link" @click="selectNode(row.id)">进入</button>
                <button
                  type="button"
                  class="archive-category__link"
                  @click="handleEdit(row)"
                >
                  编辑
                </button>
              </span>
            </template>
          </screen-data-table>

          <p v-else class="archive-category__leaf-hint">
            <screen-icon name="info" :size="13" />
            这是末级类别，没有子类别。挂档案时请选到这一级。
          </p>
        </section>
      </template>

      <screen-empty
        v-else
        text="未选择类别"
        description="在左侧点击任意类别查看详情，或使用拖拽调整排序与上级。"
      />
    </screen-panel>

    <category-form-modal ref="formModal" @ok="loadTree" />
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenButton,
  ScreenInput,
  ScreenRadioGroup,
  ScreenTree,
  ScreenTag,
  ScreenEmpty,
  ScreenDescriptions,
  ScreenDataTable,
  ScreenPopconfirm,
  ScreenIcon,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import CategoryFormModal from './CategoryFormModal.vue'
import {
  queryArchiveCategoryTree,
  editArchiveCategory,
  deleteArchiveCategory,
  sortArchiveCategory,
  changeArchiveCategoryStatus,
} from '@/api/land/archiveCategory'

/** 深拷贝树（只保留需要写回的字段，避免把后端返回的冗余字段一起搬来搬去） */
function cloneTree (nodes) {
  return (nodes || []).map((node) => ({
    id: node.id,
    name: node.name,
    code: node.code,
    aliasName: node.aliasName,
    note: node.note,
    parentId: node.parentId,
    status: node.status,
    sortNo: node.sortNo,
    archiveCount: node.archiveCount,
    isLeaf: node.isLeaf,
    hasChildren: node.hasChildren,
    children: cloneTree(node.children),
  }))
}

/** 从树里摘掉一个节点并返回它 */
function detachNode (nodes, id) {
  for (let i = 0; i < nodes.length; i += 1) {
    if (nodes[i].id === id) {
      return nodes.splice(i, 1)[0]
    }
    const hit = detachNode(nodes[i].children || [], id)
    if (hit) return hit
  }
  return null
}

/** 把节点插到指定父节点下的指定位置；parentId 为空表示插到根 */
function attachNode (nodes, parentId, index, node) {
  const list = parentId ? findNodeIn(nodes, parentId).children : nodes
  const position = index === null || index === undefined || index < 0 ? list.length : Math.min(index, list.length)
  list.splice(position, 0, node)
}

function findNodeIn (nodes, id) {
  for (let i = 0; i < (nodes || []).length; i += 1) {
    if (nodes[i].id === id) return nodes[i]
    const hit = findNodeIn(nodes[i].children || [], id)
    if (hit) return hit
  }
  return null
}

/** 重算 hasChildren / isLeaf / parentId，并刷新排序号 */
function recalcFlags (nodes, parentId) {
  (nodes || []).forEach((node, index) => {
    node.parentId = parentId || null
    node.sortNo = index + 1
    const kids = node.children || []
    node.hasChildren = kids.length ? 1 : 0
    node.isLeaf = kids.length ? 0 : 1
    recalcFlags(kids, node.id)
  })
}

export default {
  name: 'ArchiveCategory',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenInput,
    ScreenRadioGroup,
    ScreenTree,
    ScreenTag,
    ScreenEmpty,
    ScreenDescriptions,
    ScreenDataTable,
    ScreenPopconfirm,
    ScreenIcon,
    CategoryFormModal,
  },
  data () {
    return {
      loading: false,
      statusLoading: false,
      keyword: '',
      /** 'all' | 1 启用 | 0 停用 */
      statusFilter: 'all',
      statusFilterOptions: [
        { value: 'all', label: '全部' },
        { value: 1, label: '启用' },
        { value: 0, label: '停用' },
      ],
      keywordTimer: null,
      rawTree: [],
      nodeIndex: {},
      expandedKeys: [],
      expandInitialized: false,
      selectedKeys: [],
      childColumns: [
        { key: 'sortNo', title: '排序', width: 60, align: 'center' },
        { key: 'name', title: '子类别名称', width: 200, ellipsis: true },
        { key: 'code', title: '编码', width: 110, formatter: (value) => value || '—' },
        { key: 'childStatus', title: '状态', width: 80, type: 'slot', align: 'center' },
        { key: 'childArchive', title: '文件数', width: 80, type: 'slot', align: 'center' },
        { key: 'childAction', title: '操作', width: 110, type: 'slot', align: 'center' },
      ],
    }
  },
  computed: {
    /** 交给 ScreenTree 的节点：补 key / title / isLeaf，并带上用于展示的附加字段 */
    treeData () {
      return this.rawTree
    },
    flatNodes () {
      const list = []
      const walk = (nodes) => {
        (nodes || []).forEach((node) => {
          list.push(node)
          walk(node.children)
        })
      }
      walk(this.rawTree)
      return list
    },
    allParentKeys () {
      return this.flatNodes.filter((node) => node.children && node.children.length).map((node) => node.id)
    },
    isAllExpanded () {
      return this.allParentKeys.length > 0 && this.allParentKeys.every((key) => this.expandedKeys.indexOf(key) > -1)
    },
    selected () {
      return this.selectedKeys.length ? this.nodeIndex[this.selectedKeys[0]] || null : null
    },
    childCount () {
      return this.selected && this.selected.children ? this.selected.children.length : 0
    },
    /** 子类别表格：克隆一份并去掉 children，避免表格把树渲染成嵌套表 */
    childRows () {
      return (this.selected && this.selected.children ? this.selected.children : []).map((row) => {
        const copy = Object.assign({}, row)
        delete copy.children
        return copy
      })
    },
    /** 同级兄弟（用于上移/下移） */
    siblingList () {
      if (!this.selected) return []
      const parentId = this.selected.parentId
      if (!parentId) return this.rawTree
      const parent = this.nodeIndex[parentId]
      return parent && parent.children ? parent.children : []
    },
    selectedIndex () {
      if (!this.selected) return -1
      return this.siblingList.findIndex((node) => node.id === this.selected.id)
    },
    canMoveUp () {
      return this.selectedIndex > 0
    },
    canMoveDown () {
      return this.selectedIndex > -1 && this.selectedIndex < this.siblingList.length - 1
    },
    stats () {
      const list = this.flatNodes
      return {
        total: list.length,
        root: this.rawTree.length,
        enabled: list.filter((node) => node.status === 1).length,
        disabled: list.filter((node) => node.status !== 1).length,
      }
    },
    showArchiveCount () {
      return this.flatNodes.some((node) => node.archiveCount !== null && node.archiveCount !== undefined)
    },
    isFiltering () {
      return !!this.keyword || this.statusFilter !== 'all'
    },
    /** 移除前的阻塞原因：有子类别或有档案时不建议直接删（后端也会拒绝） */
    deleteDisabledReason () {
      if (!this.selected) return ''
      if (this.childCount) return `该类别下存在 ${this.childCount} 个子类别，请先移除子类别`
      const count = this.selected.archiveCount
      if (count) return `该类别下存在 ${count} 份档案，无法移除`
      return ''
    },
    deleteTitle () {
      if (!this.selected) return ''
      const name = this.selected.fullPathName || this.selected.name
      return `确定移除类别「${name}」吗？`
    },
    detailItems () {
      const node = this.selected || {}
      return [
        { key: 'name', label: '类别名称', value: node.name },
        { key: 'code', label: '类别编码', value: node.code },
        { key: 'aliasName', label: '别名/拼音码', value: node.aliasName },
        { key: 'level', label: '层级', value: node.level ? `第 ${node.level} 级` : '—' },
        { key: 'sortNo', label: '同级排序', value: node.sortNo },
        { key: 'childCount', label: '直接子类别', value: `${this.childCount} 个` },
        {
          key: 'archiveCount',
          label: '本类别文件数',
          value: node.archiveCount === null || node.archiveCount === undefined ? '—' : node.archiveCount,
        },
        { key: 'createInfo', label: '创建信息', value: this.joinInfo(node.createTime, node.createBy) },
        { key: 'updateInfo', label: '最后更新', value: this.joinInfo(node.updateTime, node.updateBy) },
        { key: 'note', label: '类别说明', value: node.note, span: 3 },
      ]
    },
  },
  watch: {
    // 输入即搜（300ms 防抖），避免每敲一个字就打一次接口
    keyword () {
      clearTimeout(this.keywordTimer)
      this.keywordTimer = setTimeout(() => this.loadTree(), 300)
    },
  },
  created () {
    this.loadTree()
  },
  beforeDestroy () {
    clearTimeout(this.keywordTimer)
  },
  methods: {
    /* ---------------- 数据 ---------------- */

    loadTree () {
      this.loading = true
      const params = {}
      if (this.keyword) params.keyword = this.keyword
      if (this.statusFilter !== 'all') params.status = this.statusFilter

      return queryArchiveCategoryTree(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '类别树加载失败')
            return
          }
          this.rawTree = res.result || []
          this.reindex()
          this.syncExpandedKeys()
          this.syncSelected()
        })
        .finally(() => {
          this.loading = false
        })
    },

    /** 建立 id → 节点索引，并补齐 parentId / 全路径名（后端仅在详情接口给这些字段） */
    reindex () {
      const index = {}
      const walk = (nodes, parentId, parentPath) => {
        (nodes || []).forEach((node) => {
          node.parentId = node.parentId || parentId || null
          node.fullPathName = parentPath ? `${parentPath} / ${node.name}` : node.name
          index[node.id] = node
          walk(node.children, node.id, node.fullPathName)
        })
      }
      walk(this.rawTree, null, '')
      this.nodeIndex = index
    },

    /** 首次加载时展开根节点，之后保留用户自己的展开状态 */
    syncExpandedKeys () {
      if (this.expandInitialized) {
        // 过滤掉已经不存在的 key，避免筛选后残留无效展开项
        this.expandedKeys = this.expandedKeys.filter((key) => !!this.nodeIndex[key])
        return
      }
      this.expandedKeys = this.rawTree.map((node) => node.id)
      this.expandInitialized = true
    },

    /** 选中的节点可能已被筛选掉或删除，此时退回未选中状态 */
    syncSelected () {
      if (this.selectedKeys.length && !this.nodeIndex[this.selectedKeys[0]]) {
        this.selectedKeys = []
      }
    },

    /* ---------------- 树交互 ---------------- */

    handleSelect (key) {
      this.selectedKeys = key ? [key] : []
    },
    selectNode (id) {
      this.selectedKeys = [id]
      // 展开到该节点所在的分支，保证「进入子类别」后能看到它
      const parent = this.nodeIndex[id] && this.nodeIndex[id].parentId
      if (parent && this.expandedKeys.indexOf(parent) === -1) {
        this.expandedKeys = this.expandedKeys.concat([parent])
      }
    },
    toggleExpandAll () {
      this.expandedKeys = this.isAllExpanded ? [] : this.allParentKeys.slice()
    },

    /**
     * 拖拽禁止规则：不能拖到自己身上，也不能拖进自己的后代。
     * 组件的 draggableGuard 会在拖拽过程中调用，返回 false 时不允许落点。
     */
    draggableGuard (dragNode, dropNode) {
      if (!dragNode || !dropNode || dragNode.id === dropNode.id) return false
      return !this.isDescendant(dragNode.id, dropNode.id)
    },

    /** dropId 是否是 id 的后代 */
    isDescendant (id, dropId) {
      const node = this.nodeIndex[id]
      if (!node || !node.children) return false
      return this.containsId(node.children, dropId)
    },
    containsId (nodes, id) {
      return (nodes || []).some((node) => node.id === id || this.containsId(node.children, id))
    },

    /**
     * 拖拽落点：先本地乐观移动，再落库。
     * position: 'before' | 'inside' | 'after'
     */
    handleDrop ({ dragKey, dropKey, position }) {
      if (!dragKey || !dropKey || dragKey === dropKey) return
      const dragNode = this.nodeIndex[dragKey]
      const dropNode = this.nodeIndex[dropKey]
      if (!dragNode || !dropNode) return
      if (this.isDescendant(dragKey, dropKey)) {
        toast.warning('不能把类别移动到它自己的子类别下')
        return
      }

      const previousParentId = dragNode.parentId || null
      let nextParentId
      let insertIndex

      if (position === 'inside') {
        nextParentId = dropKey
        insertIndex = null // 追加到末尾
      } else {
        nextParentId = dropNode.parentId || null
        const siblings = nextParentId ? (this.nodeIndex[nextParentId].children || []) : this.rawTree
        const dropIndex = siblings.findIndex((node) => node.id === dropKey)
        insertIndex = dropIndex + (position === 'after' ? 1 : 0)
      }

      // ---- 本地乐观移动 ----
      const next = cloneTree(this.rawTree)
      const moved = detachNode(next, dragKey)
      if (!moved) return
      attachNode(next, nextParentId, insertIndex, moved)
      recalcFlags(next, null)
      this.rawTree = next
      this.reindex()
      this.syncSelected()
      // 拖到某个节点里面时把它展开，否则用户看不到刚拖进去的项
      if (position === 'inside' && this.expandedKeys.indexOf(dropKey) === -1) {
        this.expandedKeys = this.expandedKeys.concat([dropKey])
      }

      // ---- 落库 ----
      const tasks = []
      if (previousParentId !== nextParentId) {
        tasks.push(
          editArchiveCategory({
            id: dragKey,
            // 后端约定 '0' 表示移动到一级
            parentId: nextParentId || '0',
            name: moved.name,
            code: moved.code,
            aliasName: moved.aliasName,
            note: moved.note,
            status: moved.status,
          })
        )
      }

      const targetSiblings = nextParentId ? (findNodeIn(next, nextParentId).children || []) : next
      tasks.push(
        sortArchiveCategory({
          parentId: nextParentId || null,
          orderedIds: targetSiblings.map((node) => node.id),
        })
      )

      Promise.all(tasks)
        .then((results) => {
          const failed = results.find((res) => !res || !res.success)
          if (failed) {
            toast.error((failed && failed.message) || '排序保存失败，已恢复为服务端数据')
            // 落库失败就重新拉取，避免界面与后端长期不一致
            return this.loadTree()
          }
          toast.success('顺序已保存')
        })
        .catch(() => {
          this.loadTree()
        })
    },

    /** 上移 / 下移一个同级位置（不依赖拖拽，键盘/鼠标都可用） */
    moveSibling (delta) {
      if (!this.selected) return
      const siblings = this.siblingList.slice()
      const index = this.selectedIndex
      const target = index + delta
      if (index < 0 || target < 0 || target >= siblings.length) return

      const swapped = siblings.slice()
      const temp = swapped[index]
      swapped[index] = swapped[target]
      swapped[target] = temp

      const parentId = this.selected.parentId || null
      const next = cloneTree(this.rawTree)
      const list = parentId ? findNodeIn(next, parentId).children : next
      list.splice(0, list.length, ...swapped)
      recalcFlags(next, null)
      this.rawTree = next
      this.reindex()

      sortArchiveCategory({ parentId, orderedIds: swapped.map((node) => node.id) })
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '排序保存失败，已恢复为服务端数据')
            return this.loadTree()
          }
          toast.success('顺序已保存')
        })
        .catch(() => this.loadTree())
    },

    /* ---------------- 增删改 ---------------- */

    handleAddRoot () {
      this.$refs.formModal.show(null, null)
    },
    handleAddChild () {
      if (!this.selected) return
      this.$refs.formModal.show(null, this.selected)
    },
    handleEdit (record) {
      // 子类别表格里传的是克隆对象，用它编辑没问题（字段齐全），但别把 children 带进表单
      if (!record || !record.id) return
      this.$refs.formModal.show(record, null)
    },

    handleDelete () {
      if (!this.selected) return
      if (this.deleteDisabledReason) {
        toast.warning(this.deleteDisabledReason)
        return
      }
      deleteArchiveCategory(this.selected.id).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || '移除失败')
          return
        }
        toast.success(res.message || '移除成功')
        this.selectedKeys = []
        this.loadTree()
      })
    },

    handleToggleStatus () {
      if (!this.selected) return
      const next = this.selected.status === 1 ? 0 : 1
      const action = next === 0 ? '停用' : '启用'
      const extra = next === 0 && this.childCount ? `（会级联停用 ${this.childCount} 个子类别）` : ''

      this.statusLoading = true
      changeArchiveCategoryStatus(this.selected.id, next)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || `${action}失败`)
            return
          }
          toast.success(`${action}成功${extra}`)
          this.loadTree()
        })
        .finally(() => {
          this.statusLoading = false
        })
    },

    joinInfo (a, b) {
      const parts = [a, b].filter(Boolean)
      return parts.length ? parts.join(' · ') : '—'
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.archive-category {
  display: grid;
  // 树固定宽度，详情吃掉剩余空间
  grid-template-columns: 380px minmax(0, 1fr);
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  &__tree-panel,
  &__detail-panel {
    min-height: 0;
  }

  &__tools {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    flex-wrap: wrap;
    margin-bottom: var(--screen-space-2);

    /deep/ .screen-input {
      flex: 1 1 140px;
      min-width: 120px;
    }
  }

  &__toggle {
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    color: var(--screen-accent);
    background: none;
    border: 0;
    cursor: pointer;
    white-space: nowrap;
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent-bright);
    }
  }

  // 树在面板里吃掉剩余高度并内部滚动
  &__tree-wrap {
    display: flex;
    flex-direction: column;
    flex: 1 1 auto;
    min-height: 0;
    overflow: auto;
    .screen-scrollbar();
  }

  &__stats {
    b {
      margin: 0 2px;
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      color: var(--screen-text);
    }

    b.is-warn {
      color: var(--screen-warning);
    }
  }

  // ---------- 详情 ----------
  &__detail-title {
    display: inline-flex;
    align-items: center;
    gap: 6px;

    /deep/ .screen-icon {
      color: var(--screen-accent);
    }
  }

  &__head {
    display: flex;
    align-items: center;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
    margin-bottom: var(--screen-space-3);
    padding-bottom: var(--screen-space-2);
    border-bottom: 1px solid var(--screen-border-soft);
  }

  &__head-name {
    margin: 0;
    min-width: 0;
    font-size: var(--screen-font-lg);
    font-weight: 600;
    color: var(--screen-text);
  }

  &__head-tags {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  &__children {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    margin-top: var(--screen-space-4);
    min-height: 0;
  }

  &__children-title {
    display: flex;
    align-items: baseline;
    gap: var(--screen-space-2);
    margin: 0;
    font-size: var(--screen-font-sm);
    font-weight: 600;
    color: var(--screen-text);

    &::before {
      content: '';
      width: 3px;
      height: 12px;
      border-radius: var(--screen-radius-pill);
      background: linear-gradient(180deg, var(--screen-accent) 0%, var(--screen-accent-deep) 100%);
    }
  }

  &__children-sub {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  &__leaf-hint {
    display: flex;
    align-items: center;
    gap: 5px;
    margin: 0;
    padding: var(--screen-space-3);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    background: rgba(6, 20, 40, 0.4);
    border: 1px dashed var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
  }

  &__child-actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
    justify-content: center;
  }

  &__link {
    .screen-link-action();
  }
}

// 窄屏把树和详情改为上下排列
@media (max-width: 1280px) {
  .archive-category {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(240px, 40%) minmax(0, 1fr);
  }
}
</style>
