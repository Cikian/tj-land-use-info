<template>
  <!--
    ArchiveMaintain 档案维护（方案 2.3.2 第 2 项）
    --------------------------------
    「在已上传的配套项目中添加档案信息，并记录相关操作」——
    新增/编辑弹窗里的配套项目只能从已有记录里选，不能手工输入新项目名；
    所有写操作都会在后端写 t_archive_log 操作记录（详情页「操作记录」页签可查）。

    ★ 本组件同时承担方案 2.3.2 第 3 项「档案查询」：
      原 ArchiveQuery.vue 与本组件共用同一套检索条件、同一个列表、同一个接口，
      差别只有「只读 + 条件默认全展开 + 每页 20 条」，在大屏上属于同一个功能的两种用法，
      因此合并成一个页签（侧栏少一个几乎一样的入口），查询能力一项没减：
        · 检索条件仍是全部 13 项，点「展开更多条件」即可（默认只露 4 项常用条件）；
        · 导出仍是同一份条件口径（按条件导出 ZIP）；
        · 档案统计 / 台账 / 移交的下钻落点改到本页签（见 applyDrill / openDetailById）。
      管理端的「档案查询」菜单**保留不动** —— 那里是权限边界（查询角色只有
      land:archive:list + export，不该看到增删改），大屏端不做按钮级权限。

    ★ P2-07 两种列表视图（同一份数据、同一套条件，切换不重新学一遍操作）：
      「按项目聚合」（默认）：树形 项目 → 案卷 → 材料类别 → 文件，
          第一层就能看到材料类别摘要、文件数与总大小，不必逐条进详情；
      「案卷平铺」：原来的逐卷列表，批量选择 / 批量删除仍在这里做。
      聚合视图的分页以**项目**为单位（后端 /land/archive/projectPage 负责），
      不是把全部档案拉到前端再分组 —— 那样数据一多就会把浏览器拖死。

    布局：
      上排：检索条件（可折叠）
      下排：列表面板（工具条 + 表格或树 + 分页），列表吃掉剩余高度
  -->
  <div class="archive-maintain">
    <screen-panel class="archive-maintain__search" title="档案检索" collapsible>
      <archive-search-form
        ref="search"
        :initial-query="searchInitialQuery"
        @search="handleSearch"
      />
    </screen-panel>

    <screen-panel class="archive-maintain__list" title="档案列表">
      <template #extra>
        <span class="archive-maintain__total">
          共 <b>{{ isProjectView ? projectTotal : total }}</b>
          {{ isProjectView ? '个项目' : '个档案' }}
          <template v-if="!isProjectView && selectedRowKeys.length">
            · 已选 <b class="is-accent">{{ selectedRowKeys.length }}</b> 项
          </template>
          <!-- 「当前筛选」必须一直可见：条件被清空时也要能看出「现在是全量」，
               否则用户会以为列表被什么隐藏条件过滤了 -->
          <span class="archive-maintain__filter">当前筛选：{{ currentFilterText }}</span>
        </span>

        <screen-radio-group
          v-model="viewMode"
          :options="VIEW_OPTIONS"
          size="sm"
          aria-label="列表视图"
          @change="handleViewModeChange"
        />

        <screen-button
          type="primary"
          size="sm"
          icon="plus"
          @click="handleAdd"
        >
          新增档案
        </screen-button>

        <screen-button
          size="sm"
          icon="download"
          :disabled="!hasAnyData"
          @click="handleExport"
        >
          按条件导出 ZIP
        </screen-button>

        <screen-popconfirm
          v-if="selectedRowKeys.length"
          title="删除后不可恢复，确定批量删除选中的档案吗？"
          :description="`共 ${selectedRowKeys.length} 个档案及其卷内文件会被删除`"
          width="286"
          @confirm="handleBatchDelete"
        >
          <screen-button type="danger" size="sm" icon="trash">
            批量删除
          </screen-button>
        </screen-popconfirm>

        <screen-button size="sm" icon="reload" :loading="loading" @click="loadData">刷新</screen-button>
      </template>

      <!-- ================= 视图一：按项目聚合（树形） ================= -->
      <div v-if="isProjectView" class="archive-maintain__tree" :class="{ 'is-loading': loading }">
        <p v-if="!projectRows.length && !loading" class="archive-maintain__empty">
          {{ emptyTreeText }}
        </p>

        <article
          v-for="project in projectRows"
          :key="project.projectKey"
          class="archive-maintain__project"
        >
          <button
            type="button"
            class="archive-maintain__project-head"
            :aria-expanded="isExpanded(project.projectKey) ? 'true' : 'false'"
            @click="toggleProject(project)"
          >
            <screen-icon :name="isExpanded(project.projectKey) ? 'chevron-down' : 'chevron-right'" :size="12" />
            <span class="archive-maintain__project-name" :title="project.ptxmmc || ''">
              {{ project.ptxmmc || '未关联项目' }}
            </span>
            <span class="archive-maintain__project-sub">
              {{ project.crzdbh || '—' }}
              <template v-if="project.xzqh"> · {{ project.xzqh }}</template>
            </span>

            <!-- 类别摘要 / 案卷数 / 文件数 / 总大小：第一层就要能判断「这一卷齐不齐」 -->
            <span class="archive-maintain__project-cats" :title="project.categoryNames || ''">
              {{ project.categoryNames || '未分类' }}
            </span>
            <span class="archive-maintain__project-count">
              案卷 {{ project.archiveCount }} · 文件 {{ project.fileCount || 0 }} ·
              {{ formatSize(project.totalSize) }}
            </span>
          </button>

          <div v-if="isExpanded(project.projectKey)" class="archive-maintain__project-body">
            <p v-if="projectLoading[project.projectKey]" class="archive-maintain__muted">正在加载案卷…</p>
            <p
              v-else-if="!(project.caseFiles || []).length"
              class="archive-maintain__muted"
            >
              该项目下还没有案卷
            </p>

            <section
              v-for="caseFile in project.caseFiles || []"
              :key="caseFile.id"
              class="archive-maintain__case"
            >
              <header class="archive-maintain__case-head">
                <button type="button" class="archive-maintain__link" @click="handleDetail(caseFile)">
                  {{ caseFile.archiveNo || '（无档案号）' }} {{ caseFile.archiveName || '' }}
                </button>
                <span class="archive-maintain__case-status">{{ caseFile.status || '—' }}</span>
                <span class="archive-maintain__muted">{{ (caseFile.files || []).length }} 个文件</span>
              </header>

              <div
                v-for="group in groupFilesByCategory(caseFile)"
                :key="group.name"
                class="archive-maintain__category"
              >
                <span class="archive-maintain__category-name" :title="group.name">{{ group.name }}</span>
                <span class="archive-maintain__category-count">{{ group.files.length }}</span>
                <ul class="archive-maintain__files">
                  <li v-for="file in group.files" :key="file.id" :title="file.fileName">
                    <span class="archive-maintain__file-name">{{ file.fileName }}</span>
                    <em class="archive-maintain__file-size">{{ formatSize(file.fileSize) }}</em>
                  </li>
                </ul>
              </div>

              <p
                v-if="!(caseFile.files || []).length"
                class="archive-maintain__muted"
              >
                该案卷还没有卷内文件
              </p>
            </section>
          </div>
        </article>
      </div>

      <!-- ================= 视图二：案卷平铺（可批量操作） ================= -->
      <archive-table
        v-else
        :data-source="dataSource"
        :loading="loading"
        selectable
        :selected-row-keys="selectedRowKeys"
        @select-change="handleSelectChange"
        @detail="handleDetail"
        @edit="handleEdit"
        @delete="handleDelete"
        @action="handleTableAction"
      />

      <template #footer>
        <screen-pagination
          :current="pagination.current"
          :page-size="pagination.pageSize"
          :total="isProjectView ? projectTotal : pagination.total"
          :page-size-options="pagination.pageSizeOptions"
          @change="handlePageChange"
        />
      </template>
    </screen-panel>

    <archive-form-modal ref="formModal" @ok="handleSaved" />
    <archive-detail-modal
      ref="detailModal"
      @edit="handleEdit"
      @export="handleExportOne"
      @status-changed="handleStatusChanged"
    />
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenButton,
  ScreenPagination,
  ScreenPopconfirm,
  ScreenRadioGroup,
  ScreenIcon
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import ArchiveSearchForm from './ArchiveSearchForm.vue'
import ArchiveTable from './ArchiveTable.vue'
import ArchiveFormModal from './ArchiveFormModal.vue'
import ArchiveDetailModal from './ArchiveDetailModal.vue'
import {
  queryArchiveList,
  queryArchiveById,
  queryArchiveProjectPage,
  queryArchiveProjectCaseFiles,
  deleteArchive,
  deleteArchiveBatch,
  exportArchiveZip
} from '@/api/land/archive'
import { defaultPagination, formatSize } from '../constants'

/** 列表视图（默认按项目聚合：测试要的是「不必逐条进详情就能看到类别」） */
const VIEW_OPTIONS = [
  { value: 'project', label: '按项目聚合' },
  { value: 'flat', label: '案卷平铺' }
]

export default {
  name: 'ArchiveMaintain',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenPagination,
    ScreenPopconfirm,
    ScreenRadioGroup,
    ScreenIcon,
    ArchiveSearchForm,
    ArchiveTable,
    ArchiveFormModal,
    ArchiveDetailModal
  },
  props: {
    /**
     * 外部（统计页下钻 / 台账与移交点「查看档案」/ 路由 query）带进来的初始条件。
     * 只有首次创建时生效；组件已被 keep-alive 缓存后由父级调 applyDrill。
     */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
      loading: false,
      viewMode: 'project',
      viewOptions: VIEW_OPTIONS,
      query: {},
      /** 平铺视图数据 */
      dataSource: [],
      total: 0,
      selectedRowKeys: [],
      pagination: defaultPagination(10),
      /** 聚合视图数据（一页 = 一页项目） */
      projectRows: [],
      projectTotal: 0,
      /** 已展开的项目 key → true（用对象而不是数组，模板里判断 O(1)） */
      expandedProjects: {},
      /** 正在加载案卷的项目 key → true */
      projectLoading: {}
    }
  },
  computed: {
    /**
     * 给检索表单的初始条件。
     * ★ archiveId 是「直接打开某份档案详情」的深链参数（台账/移交点「查看档案」用），
     *   不是检索条件 —— 混进表单会被「查询/导出」原样带给后端。
     */
    searchInitialQuery () {
      const query = Object.assign({}, this.initialQuery || {})
      delete query.archiveId
      return query
    },
    isProjectView () {
      return this.viewMode === 'project'
    },
    /**
     * 任意一种视图里有数据就为 true。
     * 导出按钮不能只看 flat 的 total：聚合视图下 total 恒为 0，
     * 按钮会被误禁用（用户明明看到列表里有项目却导不出）。
     */
    hasAnyData () {
      return this.isProjectView ? this.projectTotal > 0 : this.total > 0
    },
    /** 「当前筛选」：把生效条件说成人话，清空时明确显示「全部」 */
    currentFilterText () {
      const query = this.query || {}
      const parts = []
      if (query.ptxmmc) parts.push(`项目≈${query.ptxmmc}`)
      if (query.crzdbh) parts.push(`宗地≈${query.crzdbh}`)
      if (query.archiveNo) parts.push(`档案号≈${query.archiveNo}`)
      if (query.archiveName) parts.push(`档案名称≈${query.archiveName}`)
      if (query.categoryId || query.categoryPath) parts.push('已按档案类别过滤')
      if (query.fileName) parts.push(`文件名≈${query.fileName}`)
      if (query.xzqh) parts.push(`行政区划=${query.xzqh}`)
      if (query.archiveYear) parts.push(`年度=${query.archiveYear}`)
      if (query.responsibleDept) parts.push(`责任部门=${query.responsibleDept}`)
      if (query.responsibleUser) parts.push(`负责人≈${query.responsibleUser}`)
      if (query.status) parts.push(`状态=${query.status}`)
      if (query.secretLevel) parts.push(`密级=${query.secretLevel}`)
      if (query.sourceType) parts.push(`来源=${query.sourceType}`)
      if (query.beginDate || query.endDate) {
        parts.push(`归档日期 ${query.beginDate || ''} ~ ${query.endDate || ''}`)
      }
      return parts.length ? parts.join('，') : '全部'
    },
    emptyTreeText () {
      const hasFilter = Object.keys(this.query || {}).length > 0
      return hasFilter
        ? '没有符合条件的项目，试试放宽检索条件'
        : '还没有登记任何档案，点右上角「新增档案」开始'
    }
  },
  created () {
    // archiveId 是「直接打开某份档案详情」的深链参数（台账/移交点「查看档案」用），
    // 不是检索条件：从列表查询里摘掉，列表按普通条件加载；详情在 DOM 就绪后单独打开。
    const initial = Object.assign({}, this.initialQuery || {})
    const detailId = initial.archiveId
    delete initial.archiveId
    this.query = initial
    this.loadData()
    if (detailId) {
      this.$nextTick(() => this.openDetailById(detailId))
    }
  },
  methods: {
    formatSize,

    /* ---------------- 数据 ---------------- */

    /** 按当前视图取数（两种视图共用同一份 query 与页码） */
    loadData () {
      return this.isProjectView ? this.loadProjectPage() : this.loadFlatPage()
    },

    loadFlatPage () {
      this.loading = true
      const params = Object.assign({}, this.query, {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize
      })

      return queryArchiveList(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '档案列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })

          // 当前页被删空时自动回退一页，避免用户停在空白页以为数据没了
          if (!this.dataSource.length && this.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadFlatPage()
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    loadProjectPage () {
      this.loading = true
      // 分页以「项目」为单位：pageNo/pageSize 数的是项目数，不是案卷数
      return queryArchiveProjectPage(this.query, {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize
      })
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '档案聚合视图加载失败')
            return
          }
          const page = res.result || {}
          const rows = page.records || []
          this.projectTotal = page.total || 0
          // 项目行刷新后，案卷缓存已经过期：保存可能改状态/文件，新增也可能落进已有项目。
          // 只重拉当前仍展开的项目，既保证详情与列表同源，也避免整页预取。
          this.projectRows = rows
          this.projectRows.forEach((row) => {
            if (row.projectKey && this.expandedProjects[row.projectKey]) {
              this.loadProjectCaseFiles(row)
            }
          })
          if (!rows.length && this.projectTotal > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadProjectPage()
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 聚合视图：展开 / 收起 ---------------- */

    isExpanded (projectKey) {
      return !!this.expandedProjects[projectKey]
    },

    /**
     * 展开一个项目 → 懒加载该项目的案卷（含卷内文件）。
     *
     * <p>为什么懒加载：一个项目可能挂着十几卷、上百个文件，
     * 首屏把每页 10 个项目的全部案卷与文件都拉下来，接口和渲染都会明显变慢；
     * 而用户其实一次只关心一两个项目。
     */
    toggleProject (project) {
      if (!project || !project.projectKey) return
      const key = project.projectKey
      if (this.expandedProjects[key]) {
        // 只收起，不清缓存：再次展开不必重新请求
        this.$set(this.expandedProjects, key, false)
        return
      }
      this.$set(this.expandedProjects, key, true)
      if ((project.caseFiles || []).length) return
      this.loadProjectCaseFiles(project)
    },

    loadProjectCaseFiles (project) {
      const key = project.projectKey
      // Vue 2 里给响应式对象新增键必须走 $set，直接赋值不会触发渲染
      this.$set(this.projectLoading, key, true)
      return queryArchiveProjectCaseFiles(this.query, key)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '该项目的案卷加载失败')
            return
          }
          this.$set(project, 'caseFiles', res.result || [])
        })
        .catch(() => {
          toast.error('该项目的案卷加载失败，请稍后重试')
        })
        .finally(() => {
          this.$set(this.projectLoading, key, false)
        })
    },

    /** 把一份案卷的卷内文件按「材料类别」分组（树形第三层） */
    groupFilesByCategory (caseFile) {
      const files = (caseFile && caseFile.files) || []
      const groups = []
      const indexByName = {}
      files.forEach((file) => {
        const name = file.categoryName || '未分类'
        if (indexByName[name] === undefined) {
          indexByName[name] = groups.length
          groups.push({ name, files: [] })
        }
        groups[indexByName[name]].files.push(file)
      })
      return groups
    },

    handleViewModeChange (mode) {
      this.pagination = Object.assign({}, this.pagination, { current: 1 })
      this.selectedRowKeys = []
      if (mode === 'flat') {
        this.dataSource = []
      } else {
        this.projectRows = []
        this.expandedProjects = {}
      }
      this.loadData()
    },

    /* ---------------- 检索 ---------------- */

    handleSearch (query) {
      this.query = query || {}
      this.pagination.current = 1
      this.selectedRowKeys = []
      this.loadData()
    },

    /**
     * 清空检索条件、页码、选中与展开态（P2-01 统一契约）。
     *
     * ★ 为什么必须由本组件提供而不是让页签壳直接改表单：
     *   隐藏条件（categoryPath、archiveId 这类表单上看不见的字段）存在本组件的 query 里，
     *   只清表单文字会留下「看起来没条件、实际还在过滤」的隐性筛子。
     *   这里同时清三处：表单（setQuery 空对象）、本组件 query、分页/选中/展开。
     */
    resetFilters () {
      this.query = {}
      this.pagination = Object.assign({}, this.pagination, { current: 1, total: 0 })
      this.selectedRowKeys = []
      this.dataSource = []
      this.projectRows = []
      this.projectTotal = 0
      this.expandedProjects = {}
      if (this.$refs.search && typeof this.$refs.search.setQuery === 'function') {
        this.$refs.search.setQuery({})
      }
      return this.loadData()
    },

    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadData()
    },

    handleSelectChange (keys) {
      this.selectedRowKeys = keys || []
    },

    /**
     * 表格内的通用 action 事件。
     * 档案号列（type=link）点击等价于「查看详情」，这里做一次转发，
     * 让用户点档案号也能直接打开详情，不必去点操作列的「详情」。
     */
    handleTableAction (row, col) {
      if (col && col.key === 'archiveNo') {
        this.handleDetail(row)
      }
    },

    handleAdd () {
      this.$refs.formModal.showAdd()
    },

    handleEdit (record) {
      // 详情弹窗的「编辑」事件带的是完整 detail 对象，列表行也是同构数据
      if (!record || !record.id) return
      this.$refs.formModal.showEdit(record)
    },

    /**
     * 保存成功（新增 / 编辑）：列表与**已打开的详情**都要刷新（P1-15）。
     *
     * <p>只刷列表会出现「列表已是新值、详情还是旧值」——这正是测试看到的
     * 「详情状态与列表不一致」。详情弹窗暴露 refresh()（不重置页签、不重开弹窗）。
     */
    handleSaved (result) {
      // 新增不能被保存前的状态/类别/项目过滤和旧页码藏起来；编辑仍保留用户当前检索上下文。
      if (result && result.created) {
        this.query = {}
        this.pagination = Object.assign({}, this.pagination, { current: 1 })
        this.selectedRowKeys = []
        this.expandedProjects = {}
        if (this.$refs.search && typeof this.$refs.search.setQuery === 'function') {
          this.$refs.search.setQuery({})
        }
      }
      this.loadData()
      const detail = this.$refs.detailModal
      if (detail && typeof detail.refresh === 'function') detail.refresh()
    },

    /** 详情里改完状态：列表 / 聚合视图都要跟着变（详情自己已重拉） */
    handleStatusChanged () {
      this.loadData()
    },

    handleDetail (record) {
      this.$refs.detailModal.open(record)
    },

    /**
     * 按 id 直接打开档案详情（台账 / 移交点「查看档案」下钻用）。
     *
     * <p>★ 为什么不只传 archiveNo 让列表过滤：台账里点「查看档案」，用户要看的是
     * 那份档案的卷内文件（并直接预览），不是「再筛一次列表、再点一行」。
     * 这里按 id 拉详情并打开同一个 ArchiveDetailModal，少两步操作。
     *
     * <p>★ 为什么要有 _openingDetailId 去重：首次下钻时面板是新建的，
     * created() 与父级的 handleDrill 会在相邻两个 tick 各调一次本方法（同一份档案），
     * 不去重就会白拉两次详情、开两次弹窗。
     */
    openDetailById (id) {
      if (!id) return
      if (this._openingDetailId === id) return
      this._openingDetailId = id
      queryArchiveById(id)
        .then((res) => {
          if (!res || !res.success || !res.result) {
            toast.error((res && res.message) || '打开档案详情失败')
            return
          }
          this.$refs.detailModal.open(res.result)
        })
        .catch(() => {
          toast.error('打开档案详情失败，请稍后重试')
        })
        .finally(() => {
          this._openingDetailId = null
        })
    },

    /**
     * 按条件下钻（统计页「查看档案」/ 台账「查看档案」/ 路由 query 带条件进入）。
     * 同时把条件写回检索表单，用户能看到是「按什么筛出来的」，
     * 而不是面对一个结果被悄悄过滤却看不出原因的列表。
     * @param {{ptxmmc?: string, crzdbh?: string, archiveNo?: string, facilityId?: string}} params
     */
    applyDrill (params) {
      const query = Object.assign({}, params || {})
      delete query.archiveId
      this.query = query
      this.pagination.current = 1
      this.selectedRowKeys = []
      this.expandedProjects = {}
      if (this.$refs.search && this.$refs.search.setQuery) {
        this.$refs.search.setQuery(query)
      }
      this.loadData()
    },

    handleDelete (record) {
      deleteArchive(record.id).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || '删除失败')
          return
        }
        toast.success(res.message || '删除成功')
        this.selectedRowKeys = this.selectedRowKeys.filter((key) => key !== record.id)
        this.loadData()
      })
    },

    handleBatchDelete () {
      if (!this.selectedRowKeys.length) return
      deleteArchiveBatch(this.selectedRowKeys).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || '批量删除失败')
          return
        }
        toast.success(res.message || '批量删除成功')
        this.selectedRowKeys = []
        this.loadData()
      })
    },

    /** 按当前检索条件导出（与列表用的是同一份条件，避免「看到的」和「导出的」不一致） */
    handleExport () {
      const query = this.$refs.search ? this.$refs.search.getQuery() : this.query
      exportArchiveZip(query)
      toast.info('已开始导出，请稍候…')
    },

    /** 详情页按项目导出：优先用配套项目，其次用宗地编号 */
    handleExportOne (record) {
      exportArchiveZip({
        facilityId: record.facilityId || undefined,
        crzdbh: record.facilityId ? undefined : record.crzdbh || undefined
      })
      toast.info('已开始按项目导出，请稍候…')
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.archive-maintain {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  // 检索条件按内容高度，列表吃掉剩余高度
  &__search {
    flex: 0 0 auto;
  }

  &__list {
    // flex-basis 必须是 0：用 auto 会按内容撑开，导致正文超出模块高度、分页被挤出可见区
    flex: 1 1 0;
    min-height: 0;
  }

  &__total {
    margin-right: var(--screen-space-3);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    white-space: nowrap;

    b {
      margin: 0 2px;
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      font-size: var(--screen-font-sm);
      color: var(--screen-text);
    }

    b.is-accent {
      color: var(--screen-accent);
    }
  }

  &__filter {
    margin-left: 10px;
    color: var(--screen-accent-soft);
  }

  /* ---------------- 聚合视图（树） ---------------- */
  &__tree {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-thumb {
      background: var(--screen-border);
      border-radius: var(--screen-radius-pill);
    }

    &.is-loading {
      opacity: 0.55;
    }
  }

  &__empty {
    margin: 0;
    padding: var(--screen-space-5) 0;
    text-align: center;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-mute);
  }

  &__project {
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
    background: var(--screen-row-alt);
  }

  &__project-head {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    width: 100%;
    padding: var(--screen-space-2) var(--screen-space-3);
    font-family: inherit;
    font-size: var(--screen-font-sm);
    color: var(--screen-text);
    text-align: left;
    background: none;
    border: 0;
    cursor: pointer;
    .screen-focus-ring();

    &:hover {
      background: var(--screen-elevate);
    }
  }

  &__project-name {
    flex: 0 1 260px;
    min-width: 0;
    font-weight: 600;
    .screen-ellipsis();
  }

  &__project-sub {
    flex: 0 0 auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    white-space: nowrap;
  }

  /* 类别摘要：树形第一层的核心信息，给它更多宽度并允许换行 */
  &__project-cats {
    flex: 1 1 240px;
    min-width: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.5;
    color: var(--screen-accent-soft);
    .screen-ellipsis();
  }

  &__project-count {
    flex: 0 0 auto;
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    white-space: nowrap;
  }

  &__project-body {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    padding: 0 var(--screen-space-3) var(--screen-space-3) var(--screen-space-5);
  }

  &__case {
    padding: var(--screen-space-2) var(--screen-space-3);
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
  }

  &__case-head {
    display: flex;
    align-items: center;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
    padding-bottom: 4px;
    border-bottom: 1px dashed var(--screen-divider);
  }

  &__case-status {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
  }

  /* 材料类别（树形第三层） */
  &__category {
    display: flex;
    align-items: baseline;
    gap: var(--screen-space-2);
    flex-wrap: wrap;
    padding: 5px 0 5px var(--screen-space-3);
    border-left: 2px solid var(--screen-border-soft);
    margin-top: var(--screen-space-1);
  }

  &__category-name {
    flex: 0 0 auto;
    max-width: 260px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__category-count {
    flex: 0 0 auto;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    color: var(--screen-accent);
  }

  /* 文件（树形第四层）：横向排，超出换行，避免树越高越长 */
  &__files {
    display: flex;
    flex-wrap: wrap;
    gap: 4px var(--screen-space-3);
    flex: 1 1 auto;
    min-width: 0;
    margin: 0;
    padding: 0;
    list-style: none;

    li {
      display: inline-flex;
      align-items: baseline;
      gap: 5px;
      max-width: 320px;
      font-size: var(--screen-font-xs);
      color: var(--screen-text-sub);
    }
  }

  &__file-name {
    min-width: 0;
    .screen-ellipsis();
  }

  &__file-size {
    flex: 0 0 auto;
    font-style: normal;
    color: var(--screen-text-mute);
  }

  &__muted {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__link {
    .screen-link-action();
  }
}

// 工具条上的按钮较多，窄屏允许换行而不是被压扁
.archive-maintain__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 1400px) {
  .archive-maintain__project-sub {
    display: none;
  }
}
</style>
