<template>
  <a-card :bordered="false" class="archive-category-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">档案类别管理</h2>
        <p class="page-head__desc">
          设置档案类别树：新建一级/子级类别，支持更名、排序、拖拽调整、停用与移除。
          移除类别前请先确保该类别下没有子类别与档案。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:archiveCategory:add'" type="primary" icon="plus" @click="handleAddRoot()">新建一级类别</a-button>
        <a-button v-has="'land:archiveCategory:list'" icon="reload" :loading="loading" @click="loadTree()">刷新</a-button>
      </div>
    </div>

    <!--
      说明：档案管理模块（t_archive / t_archive_file）已落地，
      「类别下存在档案则不能移除」的前置校验已由 t_archive_file 生效，
      因此原来那条「档案校验未启用」的提示条（v-if="archiveCheckDisabled"）已不再需要。
    -->

    <div class="category-layout">
      <!-- 左：类别树 -->
      <section class="tree-panel" aria-label="档案类别树">
        <header class="tree-panel__head">
          <span class="tree-panel__title">类别树</span>
          <a-tag class="tree-panel__badge">{{ stats.total }} 个类别</a-tag>
        </header>

        <div class="tree-panel__toolbar">
          <a-input-search
            v-model="keyword"
            placeholder="搜索类别名称 / 编码 / 别名"
            allowClear
            @search="handleSearch()" />
          <div class="tree-panel__filter-row">
            <a-radio-group v-model="statusFilter" size="small" buttonStyle="solid" @change="loadTree()">
              <a-radio-button value="all">全部</a-radio-button>
              <a-radio-button :value="1">启用</a-radio-button>
              <a-radio-button :value="0">停用</a-radio-button>
            </a-radio-group>
            <a-button type="link" size="small" @click="toggleExpandAll()">
              {{ isAllExpanded ? '收起全部' : '展开全部' }}
            </a-button>
          </div>
        </div>

        <div class="tree-panel__body">
          <a-spin :spinning="loading">
            <a-empty v-if="!treeData.length" :description="emptyText" class="tree-panel__empty" />
            <a-tree
              v-else
              :treeData="treeData"
              :expandedKeys="expandedKeys"
              :selectedKeys="selectedKeys"
              :autoExpandParent="false"
              :draggable="true"
              blockNode
              @expand="onExpand"
              @select="onSelect"
              @drop="handleDrop">
              <template slot="nodeTitle" slot-scope="node">
                <span class="cat-node" :class="{ 'cat-node--off': node.status === 0 }">
                  <a-icon :type="node.isLeaf ? 'file-text' : 'folder-open'" class="cat-node__icon" />
                  <span class="cat-node__name" :title="node.name">{{ node.name }}</span>
                  <a-tag v-if="node.status === 0" class="cat-node__tag">停用</a-tag>
                  <span
                    v-if="showArchiveCount && node.archiveCount"
                    class="cat-node__count"
                    :title="`该类别下 ${node.archiveCount} 份档案`">{{ node.archiveCount }}</span>
                </span>
              </template>
            </a-tree>
          </a-spin>
        </div>

        <footer class="tree-panel__stats">
          <span><b>{{ stats.root }}</b> 个一级类别</span>
          <span class="tree-panel__stats-dot">·</span>
          <span>启用 <b>{{ stats.enabled }}</b></span>
          <span class="tree-panel__stats-dot">·</span>
          <span>停用 <b>{{ stats.disabled }}</b></span>
        </footer>
      </section>

      <!-- 右：类别详情 -->
      <section class="detail-panel" aria-label="类别详情">
        <a-empty v-if="!selected" class="detail-panel__empty">
          <template slot="description">
            <div class="detail-panel__empty-desc">请选择左侧类别查看详情</div>
            <div class="detail-panel__empty-hint">也可以直接点击「新建一级类别」开始搭建档案类别树。</div>
          </template>
        </a-empty>

        <template v-else>
          <header class="detail-panel__head">
            <div class="detail-panel__path">
              <a-icon type="apartment" class="detail-panel__path-icon" />
              <span>{{ selected.fullPathName || selected.name }}</span>
            </div>
            <div class="detail-panel__head-right">
              <a-tag :color="selected.status === 1 ? 'green' : undefined">
                {{ selected.status === 1 ? '启用中' : '已停用' }}
              </a-tag>
              <a-tag v-if="selected.isLeaf === 1" color="blue">叶子类别</a-tag>
              <a-tag v-else>含 {{ childCount }} 个子类别</a-tag>
            </div>
          </header>

          <div class="detail-panel__actions">
            <a-tooltip :title="selected.status === 0 ? '上级类别已停用，请先启用' : ''">
              <span v-has="'land:archiveCategory:add'">
                <a-button type="primary" icon="plus" :disabled="selected.status === 0" @click="handleAddChild()">
                  新增子类别
                </a-button>
              </span>
            </a-tooltip>
            <a-button v-has="'land:archiveCategory:edit'" icon="edit" @click="handleEdit()">编辑 / 更名</a-button>
            <a-button
              v-has="'land:archiveCategory:sort'"
              icon="arrow-up"
              :disabled="!canMoveUp || sorting"
              @click="moveSibling(-1)">上移</a-button>
            <a-button
              v-has="'land:archiveCategory:sort'"
              icon="arrow-down"
              :disabled="!canMoveDown || sorting"
              @click="moveSibling(1)">下移</a-button>
            <a-button v-has="'land:archiveCategory:status'" :loading="statusLoading" @click="toggleStatus()">
              {{ selected.status === 1 ? '停用' : '启用' }}
            </a-button>
            <a-tooltip :title="deleteDisabledReason">
              <span v-has="'land:archiveCategory:delete'">
                <a-popconfirm
                  title="移除后不可恢复，确定移除该类别吗？"
                  okText="确定移除"
                  cancelText="取消"
                  :disabled="!!deleteDisabledReason"
                  @confirm="handleDelete()">
                  <a-button type="danger" icon="delete" :disabled="!!deleteDisabledReason">移除</a-button>
                </a-popconfirm>
              </span>
            </a-tooltip>
          </div>

          <div class="detail-panel__body">
            <div class="detail-panel__desc">
              <a-descriptions
                size="small"
                bordered
                :column="{ xxl: 3, xl: 3, lg: 2, md: 2, sm: 1, xs: 1 }"
                class="detail-desc">
                <a-descriptions-item label="类别名称">{{ selected.name }}</a-descriptions-item>
                <a-descriptions-item label="类别编码">{{ selected.code || '—' }}</a-descriptions-item>
                <a-descriptions-item label="别名 / 拼音码">{{ selected.aliasName || '—' }}</a-descriptions-item>
                <a-descriptions-item label="层级">第 {{ selected.level }} 级</a-descriptions-item>
                <a-descriptions-item label="同级排序">{{ selected.sortNo }}</a-descriptions-item>
                <a-descriptions-item label="直接子类别">{{ childCount }}</a-descriptions-item>
                <a-descriptions-item label="本类别档案数">
                  <span v-if="showArchiveCount">{{ selected.archiveCount }}</span>
                  <a-tooltip v-else title="档案管理模块尚未落地，暂无法统计">
                    <span class="detail-desc__muted">暂不可用</span>
                  </a-tooltip>
                </a-descriptions-item>
                <a-descriptions-item label="创建信息">{{ joinInfo(selected.createBy, selected.createTime) }}</a-descriptions-item>
                <a-descriptions-item label="最后更新">{{ joinInfo(selected.updateBy, selected.updateTime) }}</a-descriptions-item>
                <a-descriptions-item label="类别说明">{{ selected.note || '—' }}</a-descriptions-item>
              </a-descriptions>
            </div>

            <div class="detail-panel__children">
              <div class="detail-panel__children-head">
                <h4>子类别（{{ childCount }}）</h4>
              </div>
              <!-- 滚动条只出现在这个框里，不带动右侧整列 -->
              <div v-if="childCount" class="detail-panel__children-body">
                <a-table
                  size="small"
                  rowKey="id"
                  :columns="childColumns"
                  :dataSource="childRows"
                  :pagination="false">
                  <template slot="childCount" slot-scope="text">
                    <span :class="{ 'detail-desc__muted': !text }">{{ text || '—' }}</span>
                  </template>
                  <template slot="childStatus" slot-scope="text">
                    <a-tag :color="text === 1 ? 'green' : undefined">{{ text === 1 ? '启用' : '停用' }}</a-tag>
                  </template>
                  <template slot="childArchive" slot-scope="text">
                    <span v-if="showArchiveCount">{{ text }}</span>
                    <span v-else class="detail-desc__muted">—</span>
                  </template>
                  <template slot="childAction" slot-scope="text, record">
                    <a @click="selectNode(record.id)">进入</a>
                    <a-divider type="vertical" />
                    <a @click="handleEdit(record)">编辑</a>
                  </template>
                </a-table>
              </div>
              <div v-else class="detail-panel__leaf">该类别没有子类别，可直接用于挂接档案。</div>
            </div>
          </div>
        </template>
      </section>
    </div>

    <archive-category-modal ref="modal" @ok="onModalOk()" />
  </a-card>
</template>

<script>
  import {
    queryArchiveCategoryTree,
    editArchiveCategory,
    deleteArchiveCategory,
    sortArchiveCategory,
    changeArchiveCategoryStatus
  } from '@/api/land/archiveCategory'
  import ArchiveCategoryModal from './modules/ArchiveCategoryModal'

  /**
   * 档案类别管理（方案 2.3.2 第 1 项）
   *
   * 交互约定：
   *  - 左树右详情；树支持拖拽排序（同级）与拖拽换上级，也提供「上移/下移」按钮作为键盘可达的替代操作。
   *  - 移除类别前做前置校验：有子类别 → 不可移除；有档案 → 不可移除（t_archive 未建表时后端跳过，页面给出说明）。
   */
  export default {
    name: 'ArchiveCategoryList',
    components: { ArchiveCategoryModal },
    data () {
      return {
        loading: false,
        sorting: false,
        statusLoading: false,
        keyword: '',
        keywordTimer: null,
        /** 'all' 表示不过滤 */
        statusFilter: 'all',
        /** 后端返回的原始类别树（保留全部字段） */
        rawTreeData: [],
        /** id -> 类别记录（含 children），用于快速定位 */
        nodeIndex: {},
        expandedKeys: [],
        expandInitialized: false,
        selectedKeys: [],
        selected: null,
        childColumns: [
          { title: '排序', dataIndex: 'sortNo', width: 56, align: 'center' },
          { title: '类别名称', dataIndex: 'name', ellipsis: true },
          { title: '类别编码', dataIndex: 'code', width: 118, customRender: text => text || '—' },
          { title: '下级', dataIndex: 'childCount', width: 60, align: 'center', scopedSlots: { customRender: 'childCount' } },
          { title: '状态', dataIndex: 'status', width: 72, scopedSlots: { customRender: 'childStatus' } },
          { title: '档案数', dataIndex: 'archiveCount', width: 72, align: 'center', scopedSlots: { customRender: 'childArchive' } },
          { title: '操作', width: 108, scopedSlots: { customRender: 'childAction' } }
        ]
      }
    },
    computed: {
      /** 转成 antd tree 需要的结构 */
      treeData () {
        return this.buildTreeNodes(this.rawTreeData)
      },
      /** 扁平化后的全部节点 */
      flatNodes () {
        const list = []
        const walk = nodes => {
          (nodes || []).forEach(node => {
            list.push(node)
            walk(node.children)
          })
        }
        walk(this.rawTreeData)
        return list
      },
      allParentKeys () {
        return this.flatNodes.filter(node => node.children && node.children.length).map(node => node.id)
      },
      isAllExpanded () {
        return this.allParentKeys.length > 0 && this.expandedKeys.length >= this.allParentKeys.length
      },
      stats () {
        const flat = this.flatNodes
        return {
          total: flat.length,
          root: this.rawTreeData.length,
          enabled: flat.filter(node => node.status === 1).length,
          disabled: flat.filter(node => node.status === 0).length
        }
      },
      /** 后端在 t_archive 未建表时不下发 archiveCount（null），据此提示校验未启用 */
      showArchiveCount () {
        return this.flatNodes.some(node => node.archiveCount !== null && node.archiveCount !== undefined)
      },
      archiveCheckDisabled () {
        return this.flatNodes.length > 0 && !this.showArchiveCount
      },
      childCount () {
        return (this.selected && this.selected.children && this.selected.children.length) || 0
      },
      /**
       * 子类别表格的数据源：只取「直接子类别」。
       *
       * 必须把 children 字段摘掉——antd 的 a-table 只要发现行数据里有 children，
       * 就会自动把它渲染成可展开的树形表格（展开箭头还会挤到「排序」这一列），
       * 结果是行数、排序号、缩进全都对不上，看起来就像坏了。
       */
      childRows () {
        const list = (this.selected && this.selected.children) || []
        return list
          .map(item => {
            const row = Object.assign({}, item)
            delete row.children
            row.childCount = (item.children && item.children.length) || 0
            return row
          })
          .sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0))
      },
      siblingList () {
        if (!this.selected) {
          return []
        }
        return this.getSiblings(this.selected)
      },
      selectedIndex () {
        if (!this.selected) {
          return -1
        }
        return this.siblingList.findIndex(node => node.id === this.selected.id)
      },
      canMoveUp () {
        return this.selectedIndex > 0
      },
      canMoveDown () {
        return this.selectedIndex > -1 && this.selectedIndex < this.siblingList.length - 1
      },
      deleteDisabledReason () {
        if (!this.selected) {
          return '请先选择类别'
        }
        if (this.childCount > 0) {
          return `该类别下存在 ${this.childCount} 个子类别，请先移除子类别`
        }
        if (this.showArchiveCount && this.selected.archiveCount > 0) {
          return `该类别下存在 ${this.selected.archiveCount} 份档案，无法移除`
        }
        return ''
      },
      emptyText () {
        if (this.isFiltering) {
          return '没有符合条件的类别'
        }
        return '还没有档案类别，点击右上角「新建一级类别」开始'
      },
      /** 当前是否启用了状态筛选 */
      isStatusFiltering () {
        return this.statusFilter === 0 || this.statusFilter === 1
      },
      /** 当前是否处于任一筛选状态 */
      isFiltering () {
        return !!this.keyword || this.isStatusFiltering
      }
    },
    watch: {
      // 关键字输入做 300ms 防抖，避免每敲一个字都请求一次
      keyword () {
        if (this.keywordTimer) {
          clearTimeout(this.keywordTimer)
        }
        this.keywordTimer = setTimeout(() => {
          this.loadTree()
        }, 300)
      }
    },
    created () {
      this.loadTree()
    },
    beforeDestroy () {
      if (this.keywordTimer) {
        clearTimeout(this.keywordTimer)
      }
    },
    methods: {
      // ------------------------------------------------------------------
      // 数据加载
      // ------------------------------------------------------------------
      /** 手动触发搜索（回车/点按钮）：取消待执行的防抖，立即查询 */
      handleSearch () {
        if (this.keywordTimer) {
          clearTimeout(this.keywordTimer)
          this.keywordTimer = null
        }
        this.loadTree()
      },
      loadTree () {
        this.loading = true
        const params = {}
        if (this.keyword) {
          params.keyword = this.keyword
        }
        if (this.isStatusFiltering) {
          params.status = this.statusFilter
        }
        return queryArchiveCategoryTree(params).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            return
          }
          this.rawTreeData = res.result || []
          this.reindex()
          this.syncExpandedKeys()
          this.syncSelected()
        }).finally(() => {
          this.loading = false
        })
      },
      reindex () {
        const index = {}
        const walk = (nodes, parentId) => {
          (nodes || []).forEach(node => {
            node.parentId = this.normalizeParent(node.parentId) || parentId || null
            index[node.id] = node
            walk(node.children, node.id)
          })
        }
        walk(this.rawTreeData, null)
        this.nodeIndex = index
      },
      syncExpandedKeys () {
        // 首次加载、或处于筛选状态时全部展开；否则保留用户当前的展开状态
        if (!this.expandInitialized || this.isFiltering) {
          this.expandedKeys = this.allParentKeys.slice()
          this.expandInitialized = true
        } else {
          this.expandedKeys = this.expandedKeys.filter(key => !!this.nodeIndex[key])
        }
      },
      syncSelected () {
        const key = this.selectedKeys.length ? this.selectedKeys[0] : null
        const node = key ? this.nodeIndex[key] : null
        if (node) {
          this.selected = node
          this.refreshDetail(node.id)
        } else {
          this.selectedKeys = []
          this.selected = null
        }
      },
      /** 详情里用到的父类别名称、全路径名称在本地按 nodeIndex 拼装，避免每次选中都发一次请求 */
      refreshDetail (id) {
        const node = this.nodeIndex[id]
        if (node && node.fullPathName === undefined) {
          // 由前端按路径本地拼装，避免额外请求
          node.fullPathName = this.buildFullPathName(node)
        }
        if (node && node.parentName === undefined) {
          const parent = node.parentId ? this.nodeIndex[node.parentId] : null
          node.parentName = parent ? parent.name : null
        }
      },
      buildFullPathName (node) {
        const names = []
        let current = node
        let guard = 0
        while (current && guard < 50) {
          names.unshift(current.name)
          current = current.parentId ? this.nodeIndex[current.parentId] : null
          guard++
        }
        return names.join(' / ')
      },
      /** 「创建人 · 创建时间」这类合并展示，两个都为空时显示 — */
      joinInfo (a, b) {
        const parts = [a, b].filter(v => v !== null && v !== undefined && v !== '')
        return parts.length ? parts.join(' · ') : '—'
      },
      // ------------------------------------------------------------------
      // 树渲染
      // ------------------------------------------------------------------
      buildTreeNodes (nodes) {
        return (nodes || []).map(record => {
          const children = Array.isArray(record.children) && record.children.length
            ? this.buildTreeNodes(record.children)
            : undefined
          return Object.assign({}, record, {
            key: record.id,
            title: record.name,
            isLeaf: !children,
            children: children,
            scopedSlots: { title: 'nodeTitle' }
          })
        })
      },
      onExpand (expandedKeys) {
        this.expandedKeys = expandedKeys
      },
      onSelect (selectedKeys) {
        this.selectedKeys = selectedKeys
        this.syncSelected()
      },
      selectNode (id) {
        this.selectedKeys = [id]
        this.syncSelected()
        // 保证被选中的节点在树上可见
        const node = this.nodeIndex[id]
        if (node && node.parentId && this.expandedKeys.indexOf(node.parentId) === -1) {
          this.expandedKeys = this.expandedKeys.concat([node.parentId])
        }
      },
      toggleExpandAll () {
        this.expandedKeys = this.isAllExpanded ? [] : this.allParentKeys.slice()
      },
      // ------------------------------------------------------------------
      // 新增 / 编辑 / 移除
      // ------------------------------------------------------------------
      handleAddRoot () {
        this.$refs.modal.show(null, null)
      },
      handleAddChild () {
        this.$refs.modal.show(null, this.selected)
      },
      /**
       * 打开编辑弹窗。
       *
       * 注意：模板里如果写成 `@click="handleEdit"`（不带括号），Vue 会把 MouseEvent 当成
       * 第一个参数传进来；事件对象里没有 id/name，弹窗就会是一张空表单。
       * 所以这里只认「带 id 的类别记录」，其它一律回退到当前选中项。
       */
      handleEdit (record) {
        const target = (record && record.id) ? record : this.selected
        if (!target || !target.id) {
          this.$message.warning('请先选择要编辑的类别')
          return
        }
        this.$refs.modal.show(target)
      },
      onModalOk () {
        this.loadTree()
      },
      handleDelete () {
        const target = this.selected
        if (!target) {
          return
        }
        deleteArchiveCategory(target.id).then(res => {
          if (res.success) {
            this.$message.success(res.message || '移除成功')
            if (this.selectedKeys.length && this.selectedKeys[0] === target.id) {
              this.selectedKeys = []
              this.selected = null
            }
            this.loadTree()
          } else {
            // 前置校验不通过时，后端返回「该类别下存在 N 份档案，无法移除」等业务提示
            this.$message.warning(res.message)
          }
        })
      },
      // ------------------------------------------------------------------
      // 排序 / 移动
      // ------------------------------------------------------------------
      getSiblings (record) {
        if (!record) {
          return []
        }
        const parentId = this.normalizeParent(record.parentId)
        if (!parentId) {
          return this.rawTreeData
        }
        const parent = this.nodeIndex[parentId]
        return (parent && parent.children) || []
      },
      moveSibling (delta) {
        const current = this.selectedIndex
        const target = current + delta
        const siblings = this.siblingList
        if (current < 0 || target < 0 || target >= siblings.length) {
          return
        }
        const orderedIds = siblings.map(node => node.id)
        const moved = orderedIds.splice(current, 1)[0]
        orderedIds.splice(target, 0, moved)
        this.persistSort(this.normalizeParent(this.selected.parentId), orderedIds)
      },
      persistSort (parentId, orderedIds) {
        this.sorting = true
        return sortArchiveCategory({ parentId: parentId, orderedIds: orderedIds })
          .then(res => {
            if (res.success) {
              this.$message.success('排序已保存')
            } else {
              this.$message.warning(res.message)
            }
          })
          .finally(() => {
            this.sorting = false
            this.loadTree()
          })
      },
      /** 拖拽：同级重排 + 跨级换上级 */
      handleDrop (info) {
        if (this.sorting) {
          return
        }
        const dragKey = info.dragNode && info.dragNode.eventKey
        const dropKey = info.node && info.node.eventKey
        if (!dragKey || !dropKey || dragKey === dropKey) {
          return
        }
        const dragRecord = this.nodeIndex[dragKey]
        if (!dragRecord) {
          return
        }
        const dropPos = (info.node.pos || '0-0').split('-')
        const dropPosition = info.dropPosition - Number(dropPos[dropPos.length - 1])

        const nextTree = JSON.parse(JSON.stringify(this.rawTreeData))
        let dragged = null
        const removeNode = list => {
          for (let i = 0; i < list.length; i++) {
            if (list[i].id === dragKey) {
              dragged = list.splice(i, 1)[0]
              return true
            }
            if (list[i].children && removeNode(list[i].children)) {
              return true
            }
          }
          return false
        }
        removeNode(nextTree)
        if (!dragged) {
          return
        }

        let targetParentId = null
        let orderedIds = []
        let inserted = false
        const insertNode = (list, parentId) => {
          for (let i = 0; i < list.length; i++) {
            const node = list[i]
            if (node.id === dropKey) {
              if (!info.dropToGap) {
                node.children = node.children || []
                node.children.push(dragged)
                targetParentId = node.id
                orderedIds = node.children.map(item => item.id)
              } else {
                list.splice(dropPosition === -1 ? i : i + 1, 0, dragged)
                targetParentId = parentId
                orderedIds = list.map(item => item.id)
              }
              inserted = true
              return true
            }
            if (node.children && insertNode(node.children, node.id)) {
              return true
            }
          }
          return false
        }
        insertNode(nextTree, null)
        if (!inserted) {
          return
        }

        const oldParentId = this.normalizeParent(dragRecord.parentId)
        const newParentId = this.normalizeParent(targetParentId)
        if (oldParentId === newParentId && orderedIds.length < 2) {
          return
        }

        // 乐观更新：先把本地树改成拖拽后的样子，失败时再回读服务端数据
        this.recalcFlags(nextTree)
        this.rawTreeData = nextTree
        this.reindex()
        this.syncSelected()

        this.sorting = true
        // 先移动（改 parent_id / path / level），再排序；两个请求必须串行，
        // 否则排序会在旧父级下校验失败。
        const moveTask = oldParentId !== newParentId
          ? editArchiveCategory({
            id: dragRecord.id,
            // 拖到顶级时用 '0' 显式表示，null 会被后端理解为「不改上级」
            parentId: newParentId || '0',
            name: dragRecord.name,
            code: dragRecord.code,
            aliasName: dragRecord.aliasName,
            note: dragRecord.note,
            status: dragRecord.status
          })
          : Promise.resolve({ success: true })

        moveTask.then(res => {
          if (res && res.success === false) {
            this.$message.warning(res.message)
            return null
          }
          return sortArchiveCategory({ parentId: newParentId, orderedIds: orderedIds })
        }).then(res => {
          if (res === null) {
            return
          }
          if (res && res.success === false) {
            this.$message.warning(res.message)
          } else {
            this.$message.success('类别树已更新')
          }
        }).catch(() => {
          // 具体错误已由请求拦截器提示
        }).finally(() => {
          this.sorting = false
          this.loadTree()
        })
      },
      recalcFlags (nodes) {
        (nodes || []).forEach(node => {
          const hasChildren = !!(node.children && node.children.length)
          node.hasChildren = hasChildren ? 1 : 0
          node.isLeaf = hasChildren ? 0 : 1
          if (node.children) {
            this.recalcFlags(node.children)
          }
        })
      },
      // ------------------------------------------------------------------
      // 启停
      // ------------------------------------------------------------------
      toggleStatus () {
        const target = this.selected
        if (!target) {
          return
        }
        const next = target.status === 1 ? 0 : 1
        this.statusLoading = true
        changeArchiveCategoryStatus(target.id, next).then(res => {
          if (res.success) {
            this.$message.success(next === 1 ? '已启用' : '已停用（含全部子类别）')
            this.loadTree()
          } else {
            this.$message.warning(res.message)
          }
        }).finally(() => {
          this.statusLoading = false
        })
      },
      // ------------------------------------------------------------------
      // 工具
      // ------------------------------------------------------------------
      normalizeParent (parentId) {
        if (parentId === undefined || parentId === null || parentId === '' || parentId === 0 || parentId === '0' || parentId === 'null') {
          return null
        }
        return parentId
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @surface-subtle: #f8fafc;
  @text-strong: #0f172a;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .archive-category-page {
    /deep/ .ant-card-body {
      padding: 20px 24px 24px;
    }
  }

  .page-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    padding-bottom: 16px;
    border-bottom: 1px solid @border-color;

    &__title {
      margin: 0 0 6px;
      font-size: 18px;
      font-weight: 600;
      color: @text-strong;
    }

    &__desc {
      margin: 0;
      max-width: 720px;
      font-size: 13px;
      line-height: 20px;
      color: @text-muted;
    }

    &__actions {
      display: flex;
      gap: 8px;
      flex-shrink: 0;
    }
  }

  .page-tip {
    margin: 16px 0 0;
  }

  /*
    整体高度固定：与树/详情的内容多少无关，切换类别、展开收起、增删类别都不会让框跳动。
    视口够高时铺满剩余空间；视口较矮时保底 420px（此时整页滚动）。
    标题+提示条+卡片内边距大约占 300px，实际项目里如发现偏高/偏矮，调这个减数即可。
  */
  .category-layout {
    display: flex;
    align-items: stretch;
    gap: 16px;
    margin-top: 16px;
    height: calc(100vh - 300px);
    min-height: 460px;
  }

  /* ---------------- 左：类别树 ---------------- */
  .tree-panel {
    display: flex;
    flex-direction: column;
    flex: 0 0 360px;
    max-width: 360px;
    height: 100%;
    background: #fff;
    border: 1px solid @border-color;
    border-radius: 8px;
    overflow: hidden;

    &__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex: none;
      height: 44px;
      padding: 0 14px;
      background: @surface-subtle;
      border-bottom: 1px solid @border-color;
    }

    &__title {
      font-size: 14px;
      font-weight: 600;
      color: @text-strong;
    }

    &__badge {
      margin: 0;
      color: @text-muted;
      background: #eef2f7;
      border-color: transparent;
    }

    &__toolbar {
      flex: none;
      padding: 12px 14px 10px;
      border-bottom: 1px solid @border-color;
    }

    &__filter-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-top: 10px;
    }

    /* ★ 树太高时在本区域内滚动，不撑高整框。
       flex 子项默认 min-height:auto，必须显式写 0，overflow 才会生效。 */
    &__body {
      flex: 1 1 auto;
      min-height: 0;
      overflow: auto;
      padding: 6px 4px;
    }

    &__empty {
      padding: 40px 8px;
    }

    &__stats {
      display: flex;
      align-items: center;
      flex: none;
      gap: 6px;
      height: 38px;
      padding: 0 14px;
      font-size: 12px;
      color: @text-muted;
      background: @surface-subtle;
      border-top: 1px solid @border-color;

      b {
        color: @text-strong;
        font-weight: 600;
      }
    }

    &__stats-dot {
      color: @text-weak;
    }
  }

  /* 树节点排版（紧凑版：单行 24px + 上下 1px 间距 = 26px/行） */
  .cat-node {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    max-width: 100%;
    min-width: 0;
    /* 图标是 inline-flex 的第一个子项，用 middle 对齐避免整个节点被顶高 */
    vertical-align: middle;

    &__icon {
      flex-shrink: 0;
      color: #7c93b3;
      font-size: 12px;
    }

    &__name {
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      line-height: 24px;
      color: @text-strong;
    }

    &__tag {
      flex-shrink: 0;
      height: 16px;
      margin: 0 0 0 1px;
      padding: 0 4px;
      font-size: 11px;
      line-height: 15px;
      color: @text-muted;
      background: #eef2f7;
      border-color: transparent;
    }

    &__count {
      flex-shrink: 0;
      min-width: 18px;
      height: 16px;
      padding: 0 5px;
      font-size: 11px;
      line-height: 16px;
      text-align: center;
      color: #1d4ed8;
      background: #e0ecff;
      border-radius: 8px;
    }

    &--off &__name {
      color: @text-weak;
    }

    &--off &__icon {
      color: #c0cad7;
    }
  }

  .tree-panel__body {
    // 行距：antd 默认 24px 行 + 上下各 4px = 32px/行，这里压到 26px
    /deep/ .ant-tree li {
      padding: 1px 0;
    }

    // 子级缩进 18px → 14px
    /deep/ .ant-tree li ul {
      padding: 0 0 0 14px;
    }

    // ★ 关键：.ant-tree-node-content-wrapper 必须保持 antd 默认的 inline-block。
    // 一旦改成 display:flex（块级），就会把前面的展开箭头挤到单独一行，行高直接翻倍。
    /deep/ .ant-tree-node-content-wrapper {
      display: inline-block;
      height: 24px;
      line-height: 24px;
      padding: 0 6px;
      border-radius: 4px;
      transition: background-color 0.15s ease;
    }

    // 展开箭头 24px → 18px；block-node 的内容宽度要同步，否则会换行
    /deep/ .ant-tree.ant-tree-block-node li .ant-tree-node-content-wrapper {
      width: ~'calc(100% - 18px)';
    }

    /deep/ .ant-tree li span.ant-tree-switcher,
    /deep/ .ant-tree li span.ant-tree-iconEle {
      width: 18px;
      height: 24px;
      line-height: 24px;
    }

    /deep/ .ant-tree-node-content-wrapper:hover {
      background-color: #f1f5fb;
    }

    /deep/ .ant-tree-node-selected {
      background-color: #e6f0ff !important;
    }

    /deep/ .ant-tree-node-selected .cat-node__name {
      font-weight: 600;
      color: #1d4ed8;
    }

    // 拖拽落点提示
    /deep/ .ant-tree-node-content-wrapper[draggable='true'] {
      cursor: grab;
    }

    /deep/ .ant-tree-drop-indicator {
      background-color: #1890ff;
    }
  }

  /* ---------------- 右：详情 ---------------- */
  .detail-panel {
    display: flex;
    flex-direction: column;
    flex: 1 1 auto;
    min-width: 0;
    height: 100%;
    overflow: hidden;
    background: #fff;
    border: 1px solid @border-color;
    border-radius: 8px;

    /* 空态占满整个固定高度，居中显示 */
    &__empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      flex: 1 1 auto;
      margin: 0;
      padding: 24px 16px;
    }

    &__empty-desc {
      color: @text-muted;
    }

    &__empty-hint {
      margin-top: 6px;
      font-size: 12px;
      color: @text-weak;
    }

    &__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex: none;
      flex-wrap: wrap;
      gap: 8px;
      padding: 12px 16px;
      background: @surface-subtle;
      border-bottom: 1px solid @border-color;
      border-radius: 8px 8px 0 0;
    }

    &__path {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 15px;
      font-weight: 600;
      color: @text-strong;
      min-width: 0;

      span {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    &__path-icon {
      color: #7c93b3;
    }

    &__head-right {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-shrink: 0;

      /deep/ .ant-tag {
        margin: 0;
      }
    }

    &__actions {
      display: flex;
      flex-wrap: wrap;
      flex: none;
      gap: 8px;
      padding: 12px 16px;
      border-bottom: 1px solid @border-color;
    }

    /* 详情内容不再整列滚动：滚动条只出现在「子类别」框里（极端窄高时才由 body 兜底） */
    &__body {
      display: flex;
      flex-direction: column;
      flex: 1 1 auto;
      min-height: 0;
      overflow: auto;
      padding: 16px;
    }

    /* 信息表：固定列宽 + 略放宽行高（antd bordered 模式默认 table-layout:auto，
       列宽会随内容长短变化，切换类别时整张表会“呼吸”，这里改成 fixed） */
    &__desc {
      flex: 0 1 auto;
      min-height: 172px;
      overflow: auto;
    }

    &__children {
      display: flex;
      flex-direction: column;
      flex: 1 1 auto;
      min-height: 0;
      margin-top: 20px;
    }

    &__children-head {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      flex: none;
      gap: 12px;
      margin-bottom: 10px;

      h4 {
        margin: 0;
        font-size: 14px;
        font-weight: 600;
        color: @text-strong;
      }
    }

    /* ★ 子类别多时，滚动条只出现在这个框里（约 1 行高度起步） */
    &__children-body {
      flex: 1 1 auto;
      min-height: 78px;
      overflow: auto;
      border: 1px solid @border-color;
      border-radius: 6px;

      /* 表头吸顶，框内滚动时仍然看得见列名 */
      /deep/ .ant-table-thead > tr > th {
        position: sticky;
        top: 0;
        z-index: 2;
      }

      /deep/ .ant-table {
        border-radius: 0;
      }
    }

    &__children-hint {
      font-size: 12px;
      color: @text-weak;
    }

    &__leaf {
      flex: none;
      padding: 20px;
      font-size: 13px;
      text-align: center;
      color: @text-muted;
      background: @surface-subtle;
      border: 1px dashed @border-color;
      border-radius: 6px;
    }
  }

  /* 信息表：固定列宽 + 行高略放宽（用根类提升优先级，避免被 antd 的 bordered 样式盖掉） */
  .archive-category-page /deep/ .detail-desc {
    .ant-descriptions-view > table {
      table-layout: fixed;
    }

    .ant-descriptions-item-label,
    .ant-descriptions-item-content {
      height: 42px;
      padding: 10px 12px;
      vertical-align: middle;
      word-break: break-word;
    }

    .ant-descriptions-item-label {
      width: 120px;
      white-space: nowrap;
      color: @text-muted;
      background: @surface-subtle;
      font-weight: 400;
    }
  }

  .detail-desc__muted {
    color: @text-weak;
  }

  /* ---------------- 响应式 ---------------- */
  /* 窄屏改为上下堆叠：整体不再固定到一个视口高度，改由各自限高 + 内部滚动 */
  @media (max-width: 1200px) {
    .category-layout {
      flex-direction: column;
      height: auto;
      min-height: 0;
    }

    .tree-panel {
      flex: none;
      max-width: none;
      height: auto;

      &__body {
        max-height: 320px;
      }
    }

    .detail-panel {
      height: auto;

      &__body {
        max-height: none;
        overflow: visible;
      }

      &__children-body {
        flex: none;
        max-height: 45vh;
      }
    }
  }

  @media (max-width: 576px) {
    .page-head__actions {
      width: 100%;

      /deep/ .ant-btn {
        flex: 1 1 auto;
      }
    }

    .detail-panel__actions {
      /deep/ .ant-btn {
        flex: 1 1 auto;
      }
    }
  }
</style>
