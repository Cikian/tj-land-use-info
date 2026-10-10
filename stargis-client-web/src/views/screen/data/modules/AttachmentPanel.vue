<template>
  <!--
    AttachmentPanel 配套附件管理（方案 2.3.1（三）5.3.5）
    --------------------------------
    「上传、查询、预览、下载」四件事收在一个面板里：
      上排：检索条件（业务类型 / 文件名 / 附件类型 / 业务可读键 + 更多）
      中排：概览条（总数 / 总大小 / 类型数，来自 /attachment/summary）
      下排：列表面板（工具条 + 表格 + 分页）

    ★ 为什么概览条要单独拉一个接口而不是从当前页累加：
      列表是分页的，从当前页累加出来的「总数 10」会随翻页跳变，
      用户会以为数据在变。summary 是**全量口径**，与列表条件无关（只按 bizType 过滤）。

    ★ 上传时预置归属：从配套详情跳过来（drill: { bizType, bizId, bizKey }）时，
      上传弹窗里的业务类型与主键已经填好，用户只需要选文件 —— 少三步操作。

    事件：
      drill (payload)  跳到别的面板
  -->
  <div class="attachment-panel">
    <screen-panel class="attachment-panel__search" title="附件检索" collapsible>
      <attachment-search-form
        ref="search"
        :initial-query="initialQuery"
        @search="handleSearch"
      />
    </screen-panel>

    <!--
      scrollable：内容超出时由面板内容区自己滚动。
      ★ 必须显式打开：ScreenPanel 的 scrollable 默认是 false，而面板外框是
        overflow: hidden —— 不打开的话内容高了只会被裁掉，**无法滚动**
        （实测就是这个现象：展开目录树后下半截看不见也滚不到）。
    -->
    <screen-panel class="attachment-panel__list" title="附件管理" scrollable>
      <template #extra>
        <span class="attachment-panel__overview">
          <template v-if="viewMode === 'tree'">
            共 <b>{{ treeSummary.files }}</b> 个 · <b>{{ formatSize(treeSummary.size) }}</b>
            · <b>{{ treeProjects.length }}</b> 个项目
          </template>
          <template v-else>
            共 <b>{{ summary.num }}</b> 个 · <b>{{ formatSize(summary.totalSize) }}</b>
            · <b>{{ summary.typeCount || 0 }}</b> 类
            <template v-if="selectedRowKeys.length">
              · 已选 <b class="is-accent">{{ selectedRowKeys.length }}</b> 个
            </template>
          </template>
        </span>

        <!-- 视图切换：目录树（按项目/材料类型）与平铺列表（可按文件名等检索） -->
        <screen-tabs
          v-model="viewMode"
          :tabs="viewOptions"
          @change="handleViewChange"
        />

        <screen-button type="primary" size="sm" icon="upload" @click="handleUpload">上传附件</screen-button>

        <screen-button size="sm" icon="reload" :loading="loading" @click="handleRefresh">
          刷新
        </screen-button>
      </template>

      <!-- ---------- 目录树视图 ---------- -->
      <template v-if="viewMode === 'tree'">
        <attachment-tree-view
          :projects="treeProjects"
          :loading="loading"
          @upload="handleUploadToType"
          @upload-project="handleUploadToProject"
          @preview="handlePreview"
          @download="handleDownload"
          @remove="handleRemove"
        />
      </template>

      <!-- ---------- 平铺列表视图 ---------- -->
      <template v-else>
        <attachment-table
          :data-source="dataSource"
          :loading="loading"
          selectable
          :selected-row-keys="selectedRowKeys"
          @select-change="handleSelectChange"
          @preview="handlePreview"
          @download="handleDownload"
          @remove="handleRemove"
        />
      </template>

      <!--
        分页放 footer 而不是内容流里。
        ★ 为什么：目录树展开后内容会很高，分页若跟在内容后面就会被推出面板外
          （实测正是这个现象 —— 超出屏幕后滚不到底）。
          放进 footer 后 footer 固定在面板底部，滚动只发生在内容区。
        ★ v-if 写在插槽**内容**里而不是插槽 template 上：
          template 上的 v-if 只是不渲染，插槽本身仍然存在，
          会让 ScreenPanel 的 $slots.footer 为真 → 目录树视图下多出一条空底栏。
      -->
      <template #footer>
        <screen-pagination
          v-if="viewMode === 'flat'"
          :current="pagination.current"
          :page-size="pagination.pageSize"
          :total="pagination.total"
          :page-size-options="pagination.pageSizeOptions"
          @change="handlePageChange"
        />
      </template>
    </screen-panel>

    <attachment-upload-modal ref="uploadModal" @ok="handleUploaded" />
    <attachment-preview-modal ref="previewModal" />
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenButton,
  ScreenPagination,
  ScreenTabs
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import AttachmentSearchForm from './AttachmentSearchForm.vue'
import AttachmentTable from './AttachmentTable.vue'
import AttachmentTreeView from './AttachmentTreeView.vue'
import AttachmentUploadModal from './AttachmentUploadModal.vue'
import AttachmentPreviewModal from './AttachmentPreviewModal.vue'
import {
  queryAttachmentPage,
  queryAttachmentSummary,
  queryAttachmentTreeByProject,
  deleteAttachment,
  buildAttachmentDownloadUrl
} from '@/api/land/attachment'
import { defaultPagination, compactQuery, formatSize } from '../constants'

export default {
  name: 'AttachmentPanel',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenPagination,
    ScreenTabs,
    AttachmentSearchForm,
    AttachmentTable,
    AttachmentTreeView,
    AttachmentUploadModal,
    AttachmentPreviewModal
  },
  props: {
    /**
     * 下钻条件（从配套详情跳过来时带 bizType / bizId / bizKey）。
     * ★ bizId 是唯一 id、只用于过滤；bizKey 是可读名称（宗地编号 / 配套项目名称），
     *   落在检索面板的「所属对象」里显示给用户看。
     */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
      /**
       * 视图模式。
       * ★ 默认目录树：本次需求就是要「按项目 → 材料类型」展示，
       *   平铺列表用于「按文件名/类型/时间检索」这种树做不了的事，
       *   所以它降级为可切换的辅助视图。
       */
      viewMode: 'tree',
      viewOptions: [
        { key: 'tree', label: '目录树' },
        { key: 'flat', label: '平铺列表' }
      ],
      loading: false,
      query: Object.assign({}, this.initialQuery || {}),
      dataSource: [],
      selectedRowKeys: [],
      pagination: defaultPagination(10),
      summary: { num: 0, totalSize: 0, readableSize: '', typeCount: 0 },
      /** 跨项目树：[{ bizId, bizKey, totalFiles, totalSize, typeCount, tree }] */
      treeProjects: []
    }
  },
  computed: {
    /** 树模式下的总览（从项目列表汇总，避免再拉一次 summary） */
    treeSummary () {
      let files = 0
      let size = 0
      this.treeProjects.forEach((project) => {
        files += Number(project.totalFiles || 0)
        size += Number(project.totalSize || 0)
      })
      return { files, size }
    }
  },
  created () {
    this.loadCurrentView()
  },
  methods: {
    formatSize,

    /* ---------------- 数据 ---------------- */

    /** 按当前视图加载数据（两个视图的数据源不同，切换时各自加载） */
    loadCurrentView () {
      if (this.viewMode === 'tree') {
        return this.loadTree()
      }
      return this.loadData()
    },

    /**
     * 跨项目附件树。
     * ★ 只按 bizType 过滤，不带其它检索条件：目录树的语义是「有哪些项目、
     *   每个项目有哪些材料」——把文件名之类的条件也带上，会把树剪得七零八落
     *   （某个项目只剩一个类型、另一个项目整体消失），反而看不出结构。
     *   需要按文件名等精细检索时切到「平铺列表」。
     */
    loadTree () {
      this.loading = true
      const bizType = String(this.query.bizType || '').trim()
      return queryAttachmentTreeByProject(bizType || undefined)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '附件目录树加载失败')
            return
          }
          this.treeProjects = res.result || []
        })
        .catch(() => {
          // 请求层已提示
        })
        .finally(() => {
          this.loading = false
        })
    },

    loadData () {
      this.loading = true
      const params = Object.assign(compactQuery(this.query), {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize
      })

      return queryAttachmentPage(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '附件列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.pagination = Object.assign({}, this.pagination, {
            total: Number(page.total || 0)
          })

          // 当前页被删空时自动回退一页，避免用户停在空白页以为数据没了
          if (!this.dataSource.length && this.pagination.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadData()
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    /**
     * 概览（全量口径）。
     * ★ 只按 bizType 过滤、不带其它条件：它回答的是「这个业务下一共有多少附件」，
     *   而不是「当前筛选下有多少」—— 后者从分页 total 就能看到，不必再拉一次。
     */
    loadSummary () {
      const bizType = String(this.query.bizType || '').trim()
      return queryAttachmentSummary(bizType || undefined)
        .then((res) => {
          if (!res || !res.success) return
          this.summary = res.result || this.summary
        })
        .catch(() => {
          // 概览是辅助信息，失败就保持上一次的值（不弹提示，避免噪音）
        })
    },

    /* ---------------- 检索与分页 ---------------- */

    /**
     * 切换视图。
     * ★ 两个视图的数据源不同（树是跨项目全量、列表是分页 + 检索条件），
     *   所以切换时必须各自加载一次，不能共用缓存。
     */
    handleViewChange (mode) {
      this.viewMode = mode
      this.selectedRowKeys = []
      this.pagination.current = 1
      this.loadCurrentView()
    },

    handleRefresh () {
      this.loadCurrentView()
      this.loadSummary()
    },

    handleSearch (query) {
      this.query = query || {}
      this.pagination.current = 1
      this.selectedRowKeys = []
      this.loadCurrentView()
      this.loadSummary()
    },

    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadData()
    },

    handleSelectChange (keys) {
      this.selectedRowKeys = keys || []
    },

    /* ---------------- 上传 ---------------- */

    /** 工具条上的「上传附件」：用当前检索条件预置归属 */
    handleUpload () {
      this.$refs.uploadModal.open({
        bizType: this.query.bizType || 'facility',
        bizId: this.query.bizId || '',
        bizKey: this.query.bizKey || ''
      })
    },

    /**
     * 上传到某个材料类型（树上的「上传到该类型」）。
     * ★ 材料类型**由分组定死**，用户不需要再选 —— 这正是本次需求的核心便利点。
     */
    handleUploadToType (group) {
      this.$refs.uploadModal.open({
        bizType: group.bizType || this.query.bizType || 'facility',
        bizId: group.bizId || '',
        bizKey: group.bizKey || '',
        fileType: group.fileType || ''
      })
    },

    /** 上传到某个项目（项目头上的按钮）：只预置归属，材料类型在弹窗里选 */
    handleUploadToProject (project) {
      this.$refs.uploadModal.open({
        bizType: project.bizType || this.query.bizType || 'facility',
        bizId: project.bizId || '',
        bizKey: project.bizKey || ''
      })
    },

    handleUploaded () {
      this.pagination.current = 1
      this.loadCurrentView()
      this.loadSummary()
    },

    handlePreview (record) {
      this.$refs.previewModal.open(record)
    },

    /**
     * 下载走 `/land/data/attachment/download?id=` 并把 token 拼在 query 上：
     * 服务端按这个入口统计下载次数（用静态资源地址虽然也能拿到文件，但次数永远是 0）。
     */
    handleDownload (record) {
      if (!record || !record.id) {
        toast.warning('该附件还没有落库，无法下载')
        return
      }
      window.open(buildAttachmentDownloadUrl(record.id), '_blank')
      toast.info('已开始下载，请稍候…')
    },

    handleRemove (record) {
      if (!record || !record.id) return
      deleteAttachment(record.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '删除失败')
            return
          }
          toast.success(res.message || '已删除')
          this.selectedRowKeys = this.selectedRowKeys.filter((key) => key !== record.id)
          this.loadData()
          this.loadSummary()
        })
        .catch(() => {
          // 请求层已提示
        })
    },

    /* ---------------- 对外（父组件调用） ---------------- */

    /**
     * 应用下钻条件（从配套详情点「附件」跳过来）。
     * ★ 面板被 keep-alive 缓存后 created 不会再跑，必须由父组件显式调用。
     */
    applyDrill (query) {
      const next = query || {}
      if (this.$refs.search) {
        this.$refs.search.setQuery(next)
        this.query = this.$refs.search.getQuery()
      } else {
        this.query = compactQuery(next)
      }
      this.pagination.current = 1
      this.selectedRowKeys = []
      this.loadData()
      this.loadSummary()
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.attachment-panel {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  &__search {
    flex: 0 0 auto;
  }

  &__list {
    // flex-basis 必须是 0：用 auto 会按内容撑开，导致分页被挤出可见区
    flex: 1 1 0;
    min-height: 0;
  }

  &__overview {
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
}

// 工具条上的按钮较多，窄屏允许换行而不是被压扁
.attachment-panel__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
