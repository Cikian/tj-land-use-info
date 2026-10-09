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

    <screen-panel class="attachment-panel__list" title="附件列表">
      <template #extra>
        <span class="attachment-panel__overview">
          共 <b>{{ summary.num }}</b> 个 · <b>{{ summary.readableSize || formatSize(summary.totalSize) }}</b>
          · <b>{{ summary.typeCount || 0 }}</b> 类
          <template v-if="selectedRowKeys.length">
            · 已选 <b class="is-accent">{{ selectedRowKeys.length }}</b> 个
          </template>
        </span>

        <screen-button type="primary" size="sm" icon="upload" @click="handleUpload">上传附件</screen-button>

        <screen-button size="sm" icon="reload" :loading="loading" @click="loadSummary(); loadData()">
          刷新
        </screen-button>
      </template>

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

      <template #footer>
        <screen-pagination
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
  ScreenPagination
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import AttachmentSearchForm from './AttachmentSearchForm.vue'
import AttachmentTable from './AttachmentTable.vue'
import AttachmentUploadModal from './AttachmentUploadModal.vue'
import AttachmentPreviewModal from './AttachmentPreviewModal.vue'
import {
  queryAttachmentPage,
  queryAttachmentSummary,
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
    AttachmentSearchForm,
    AttachmentTable,
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
      loading: false,
      query: Object.assign({}, this.initialQuery || {}),
      dataSource: [],
      selectedRowKeys: [],
      pagination: defaultPagination(10),
      summary: { num: 0, totalSize: 0, readableSize: '', typeCount: 0 }
    }
  },
  created () {
    this.loadData()
    this.loadSummary()
  },
  methods: {
    formatSize,

    /* ---------------- 数据 ---------------- */

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

    handleSearch (query) {
      this.query = query || {}
      this.pagination.current = 1
      this.selectedRowKeys = []
      this.loadData()
      this.loadSummary()
    },

    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadData()
    },

    handleSelectChange (keys) {
      this.selectedRowKeys = keys || []
    },

    /* ---------------- 行操作 ---------------- */

    handleUpload () {
      // 用当前检索条件预置归属：用户已经按某个配套筛过一次时，上传多半就是给它传
      this.$refs.uploadModal.open({
        bizType: this.query.bizType || 'facility',
        bizId: this.query.bizId || '',
        bizKey: this.query.bizKey || ''
      })
    },

    handleUploaded () {
      this.pagination.current = 1
      this.loadData()
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
