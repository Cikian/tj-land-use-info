<template>
  <a-card :bordered="false" class="attachment-list">
    <!-- ============ 页头 ============ -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">配套附件管理</h2>
        <p class="page-head__desc">
          方案 2.3.1（三）第 5 项。统一管理<b>宗地 / 配套项目 / 环节进度</b>三类业务对象的附件：
          上传、按业务与类型查询、在线预览、下载、删除。
          附件类型走字典可扩展（旧的 4 个固定槽位 file01~file04 已废弃），查询与预览全部<b>查库</b>——
          旧系统靠递归扫描磁盘目录找文件，目录一改名数据就丢了；
          预览也不再清空临时目录（并发时会互相删文件），而是后端给相对路径、前端拼静态地址。
          <b>下载走后端接口</b>，这样下载次数才统计得到。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:data:attachment'" type="primary" icon="upload" @click="handleUpload">上传附件</a-button>
        <a-button icon="reload" :loading="loading" @click="refresh">刷新</a-button>
      </div>
    </div>

    <!-- ============ 概览 ============ -->
    <div class="attach-stats">
      <a-row :gutter="12">
        <a-col v-for="card in statCards" :key="card.key" :xs="12" :sm="8" :md="6">
          <div class="stat-card">
            <div class="stat-card__label">{{ card.label }}</div>
            <div class="stat-card__value">{{ card.value }}</div>
            <div class="stat-card__extra">{{ card.extra }}</div>
          </div>
        </a-col>
      </a-row>
      <div v-if="distribution.length" class="attach-dist">
        <span class="attach-dist__title">按类型分布（{{ summaryScopeText }}）：</span>
        <span v-for="item in distribution" :key="item.fileType" class="attach-dist__item">
          {{ item.fileTypeText || fileTypeLabel(item.fileType) }}
          <b>{{ item.num }}</b>
          <span class="attach-dist__size">{{ item.readableSize || formatSize(item.totalSize) }}</span>
        </span>
      </div>
    </div>

    <!-- ============ 查询条件 ============ -->
    <a-form layout="inline" class="attach-search">
      <a-row :gutter="16" type="flex">
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="业务类型">
            <a-select v-model="query.bizType" placeholder="全部" :options="bizTypeOptions" @change="handleBizTypeChange" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="业务对象">
            <a-select
              v-model="query.bizId"
              show-search
              allow-clear
              :disabled="!query.bizType || query.bizType === 'process'"
              :placeholder="bizPlaceholder"
              :filter-option="false"
              :default-active-first-option="false"
              :not-found-content="bizLoading ? '搜索中…' : '输入编号 / 名称后搜索'"
              @search="handleBizSearch"
              @change="handleBizChange">
              <a-select-option v-for="item in bizOptions" :key="item.id" :value="item.id">
                {{ optionLabel(item) }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="附件类型">
            <a-select v-model="query.fileType" placeholder="全部" allow-clear :options="fileTypeOptions" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="文件名">
            <a-input
              v-model="query.keyword"
              placeholder="模糊匹配"
              allow-clear
              @pressEnter="handleSearch" />
          </a-form-item>
        </a-col>

        <!-- ============ 展开后的更多条件 ============ -->
        <template v-if="expanded">
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="上传人账号">
              <a-input
                v-model="query.uploadBy"
                placeholder="精确匹配（如 zhangsan）"
                allow-clear
                @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="8">
            <a-form-item label="上传时间">
              <a-range-picker
                v-model="uploadRange"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :placeholder="['开始日期', '结束日期']"
                @change="handleUploadDateChange" />
            </a-form-item>
          </a-col>
        </template>

        <a-col :xs="24" class="attach-search__actions">
          <a-button type="primary" icon="search" @click="handleSearch">查询</a-button>
          <a-button icon="reload" @click="handleReset">重置</a-button>
          <a-button type="link" @click="expanded = !expanded">
            {{ expanded ? '收起' : '更多条件' }}
            <a-icon :type="expanded ? 'up' : 'down'" />
          </a-button>
          <span class="attach-search__hint">
            ★ 上传时间按<b>业务日期</b>筛（后端把 yyyy-MM-dd 补成当天 00:00:00 ~ 23:59:59），
            所以「筛今天」能看到今天上传的全部文件；概览卡片的统计口径跟随「业务类型」条件
          </span>
        </a-col>
      </a-row>
    </a-form>

    <!-- ============ 附件列表 ============ -->
    <a-table
      row-key="id"
      size="small"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: 1500 }"
      @change="handleTableChange">
      <template slot="fileName" slot-scope="text, record">
        <a class="attachment-list__link" @click="handlePreview(record)">{{ text || '—' }}</a>
      </template>
      <template slot="fileType" slot-scope="text, record">
        <a-tag>{{ record.fileTypeText || fileTypeLabel(text) }}</a-tag>
      </template>
      <template slot="biz" slot-scope="text, record">
        <div class="cell-main">
          <a-tag :color="bizTypeColor(record.bizType)">{{ bizTypeText(record.bizType) }}</a-tag>
          {{ record.bizKey || '—' }}
        </div>
      </template>
      <template slot="fileSize" slot-scope="text, record">
        {{ record.readableSize || formatSize(text) }}
      </template>
      <template slot="uploader" slot-scope="text, record">
        {{ record.uploadName || record.uploadBy || '—' }}
      </template>
      <template slot="uploadTime" slot-scope="text">
        {{ text || '—' }}
      </template>
      <template slot="action" slot-scope="text, record">
        <a @click="handlePreview(record)">预览</a>
        <span v-has="'land:data:attachment'">
          <a-divider type="vertical" />
          <a @click="handleDownload(record)">下载</a>
          <a-divider type="vertical" />
          <a class="attachment-list__danger" @click="handleDelete(record)">删除</a>
        </span>
      </template>
    </a-table>

    <!-- ============ 上传 / 预览 ============ -->
    <attachment-upload-modal ref="uploadModal" @ok="handleUploaded" />
    <attachment-preview-modal ref="previewModal" />
  </a-card>
</template>

<script>
  import AttachmentUploadModal from './modules/AttachmentUploadModal'
  import AttachmentPreviewModal from './modules/AttachmentPreviewModal'
  import {
    ATTACH_TYPES_FALLBACK,
    BIZ_TYPE_OPTIONS,
    bizTypeText,
    deleteAttachment,
    downloadAttachment,
    formatSize,
    queryAllowedTypes,
    queryAttachmentPage,
    queryAttachmentSummary,
    queryTypeDistribution
  } from '@/api/land/attachment'
  import { bizTypeColor } from '@/api/land/dataRecycle'
  import { queryLandOptions, searchFacilityOptions } from '@/api/land/landData'

  /**
   * 配套附件管理（方案 2.3.1（三）第 5 项）
   *
   * ★ 为什么这一页值得单独存在（旧实现的问题逐条对上）：
   *   1. 旧系统附件类型写死 4 个槽位（file01~file04），第五类资料就没地方放；
   *      这里 fileType 走字典白名单，类型由后端给出（`queryAllowedTypes`）。
   *   2. 旧系统查附件靠递归扫描磁盘目录 —— 目录改名、换机器就查不到，
   *      而库里其实什么都没有。这里一律查库。
   *   3. 旧系统预览前清空整个 temp 目录，两个人同时预览会互相删掉对方的文件。
   *      这里预览 = 「后端给相对存储路径 + 前端拼静态地址」，不落任何临时文件。
   *   4. 旧系统没有大小 / 上传人 / 上传时间 / 下载次数。这里都是库里的列，直接展示。
   *
   * ★ 为什么「下载」必须走后端接口而不是静态地址另存为：
   *   下载次数（downloadCount）是「这份资料到底有没有被人用过」的唯一证据，
   *   只有走 `/attachment/download` 服务端才能累加。直接用静态地址下载既不计次，
   *   也绕过了服务端的路径越界校验。
   *
   * ★ 为什么概览卡片跟随「业务类型」条件：
   *   附件总量在宗地/配套/环节之间没有可比性；用户切到「配套项目」时想看的是
   *   「配套附件有多少、占多大」，而不是全库总量。所以 `queryAttachmentSummary(bizType)`
   *   与列表用同一个 bizType。
   *
   * ★ 为什么业务对象是「先选业务类型再远程搜索」：
   *   宗地与配套是两张表、两个接口（`queryLandOptions` / `searchFacilityOptions`），
   *   不选类型就无从知道该查哪张表；而且必须输入关键词才查 —— 一次拉回几百条既慢又容易选错。
   */
  export default {
    name: 'AttachmentList',
    components: { AttachmentUploadModal, AttachmentPreviewModal },
    data () {
      return {
        loading: false,
        rows: [],
        total: 0,
        expanded: false,
        // ---- 查询条件 ----
        query: this.buildEmptyQuery(),
        uploadRange: [],
        /**
         * 业务类型筛选下拉。
         *
         * ★ 为什么要在 `BIZ_TYPE_OPTIONS` 前面补一个「全部」：
         *   附件模块的这个常量是给**表单必选**场景用的（没有空值项），
         *   而查询条件必须允许「不限」，否则用户一进页面就被迫先选一个业务类型。
         *   补的只是空值项，三类的取值/文案仍然来自同一个常量，不会漂移。
         */
        bizTypeOptions: [{ value: '', label: '全部' }].concat(BIZ_TYPE_OPTIONS),
        // ---- 业务对象远程搜索 ----
        bizOptions: [],
        bizLoading: false,
        // ---- 附件类型 ----
        allowedTypes: [],
        // ---- 概览 ----
        summary: { num: 0, readableSize: '0 B', typeCount: 0 },
        distribution: [],
        // ---- 分页 ----
        pagination: {
          current: 1,
          pageSize: 10,
          total: 0,
          size: 'small',
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: total => `共 ${total} 份附件`
        },
        columns: [
          { title: '文件名', dataIndex: 'fileName', width: 260, fixed: 'left', scopedSlots: { customRender: 'fileName' } },
          { title: '附件类型', dataIndex: 'fileType', width: 150, scopedSlots: { customRender: 'fileType' } },
          { title: '业务对象', dataIndex: 'bizKey', width: 240, scopedSlots: { customRender: 'biz' } },
          { title: '大小', dataIndex: 'fileSize', width: 100, align: 'right', scopedSlots: { customRender: 'fileSize' } },
          { title: '上传人', dataIndex: 'uploadName', width: 110, scopedSlots: { customRender: 'uploader' } },
          { title: '上传时间', dataIndex: 'uploadTime', width: 165, scopedSlots: { customRender: 'uploadTime' } },
          { title: '下载次数', dataIndex: 'downloadCount', width: 100, align: 'right', customRender: text => (text === null || text === undefined ? 0 : text) },
          { title: '操作', dataIndex: 'action', width: 170, fixed: 'right', scopedSlots: { customRender: 'action' } }
        ]
      }
    },
    computed: {
      fileTypeOptions () {
        if (this.allowedTypes.length) {
          return this.allowedTypes
        }
        return ATTACH_TYPES_FALLBACK
      },
      bizPlaceholder () {
        if (!this.query.bizType) {
          return '请先选择业务类型'
        }
        if (this.query.bizType === 'process') {
          return '环节进度附件请按其业务主键查询'
        }
        return this.query.bizType === 'land' ? '输入出让宗地编号 / 地块名称搜索' : '输入配套项目名称搜索'
      },
      summaryScopeText () {
        if (!this.query.bizType) {
          return '全部业务'
        }
        return bizTypeText(this.query.bizType)
      },
      statCards () {
        return [
          {
            key: 'num',
            label: `附件总数（${this.summaryScopeText}）`,
            value: this.summary.num || 0,
            extra: '跟随上方「业务类型」条件，与列表同一口径'
          },
          {
            key: 'size',
            label: '占用空间',
            value: this.summary.readableSize || formatSize(this.summary.totalSize),
            extra: '按登记的字节数累加（同一份文件被多条业务引用时会计多次）'
          },
          {
            key: 'type',
            label: '已使用类型数',
            value: this.summary.typeCount || 0,
            extra: `允许的类型共 ${this.fileTypeOptions.length} 类`
          },
          {
            key: 'page',
            label: '本页记录',
            value: this.rows.length,
            extra: `当前筛选共 ${this.total} 条，第 ${this.pagination.current} 页`
          }
        ]
      }
    },
    mounted () {
      this.loadAllowedTypes()
      this.loadData()
      this.loadSummary()
    },
    methods: {
      formatSize: formatSize,
      bizTypeText: bizTypeText,
      bizTypeColor: bizTypeColor,

      buildEmptyQuery () {
        return {
          bizType: '',
          bizId: undefined,
          bizKey: '',
          fileType: undefined,
          keyword: '',
          uploadBy: '',
          beginDate: undefined,
          endDate: undefined
        }
      },
      loadAllowedTypes () {
        queryAllowedTypes().then(res => {
          if (res.success && res.result && res.result.length) {
            // 后端给的是 {value, text}（不是字典的 {value,label}），统一成 label 供下拉使用
            this.allowedTypes = res.result.map(item => ({ value: item.value, label: item.text || item.label }))
          }
        }).catch(() => { /* 接口不可用时用本地兜底清单（与字典逐字一致） */ })
      },
      fileTypeLabel (code) {
        const hit = this.fileTypeOptions.filter(item => item.value === code)[0]
        return hit ? hit.label : (code || '—')
      },

      // ---------------- 查询 ----------------

      loadData () {
        this.loading = true
        queryAttachmentPage(this.buildParams()).then(res => {
          if (res.success && res.result) {
            this.rows = res.result.records || []
            this.total = Number(res.result.total || 0)
            this.pagination.total = this.total
          } else {
            this.rows = []
            this.total = 0
            this.pagination.total = 0
            if (res.message) {
              this.$message.warning(res.message)
            }
          }
        }).catch(e => {
          this.rows = []
          this.total = 0
          this.pagination.total = 0
          this.$message.error((e && e.message) || '查询附件失败')
        }).finally(() => {
          this.loading = false
        })
      },
      buildParams () {
        return {
          bizType: this.query.bizType,
          bizId: this.query.bizId,
          bizKey: this.query.bizKey,
          fileType: this.query.fileType,
          keyword: this.query.keyword,
          uploadBy: this.query.uploadBy,
          beginDate: this.query.beginDate,
          endDate: this.query.endDate,
          pageNo: this.pagination.current,
          pageSize: this.pagination.pageSize
        }
      },
      loadSummary () {
        queryAttachmentSummary(this.query.bizType).then(res => {
          if (res.success && res.result) {
            this.summary = res.result
          }
        }).catch(() => { /* 概览失败不影响主流程 */ })
        queryTypeDistribution(this.query.bizType).then(res => {
          this.distribution = res.success && res.result ? res.result : []
        }).catch(() => {
          this.distribution = []
        })
      },
      handleSearch () {
        this.pagination.current = 1
        this.loadData()
        this.loadSummary()
      },
      handleReset () {
        this.query = this.buildEmptyQuery()
        this.uploadRange = []
        this.bizOptions = []
        this.handleSearch()
      },
      handleUploadDateChange (values) {
        this.query.beginDate = values && values.length === 2 ? values[0] : undefined
        this.query.endDate = values && values.length === 2 ? values[1] : undefined
      },
      handleTableChange (pagination) {
        this.pagination.current = pagination.current
        this.pagination.pageSize = pagination.pageSize
        this.loadData()
      },
      refresh () {
        this.loadData()
        this.loadSummary()
      },

      // ---------------- 业务对象远程搜索 ----------------

      handleBizTypeChange () {
        // 换业务类型后原来选中的业务对象已不属于当前类型，必须清掉
        this.query.bizId = undefined
        this.query.bizKey = ''
        this.bizOptions = []
        // 概览卡片的统计口径跟随业务类型；这里连带列表一起刷新，
        // 否则会出现「卡片上是配套的数量、列表还是全部业务的记录」这种自相矛盾的显示
        this.handleSearch()
      },
      handleBizSearch (keyword) {
        this.bizOptions = []
        if (!this.query.bizType || this.query.bizType === 'process' || !keyword) {
          return
        }
        this.bizLoading = true
        const fetcher = this.query.bizType === 'land' ? queryLandOptions : searchFacilityOptions
        fetcher({ keyword, limit: 50 }).then(res => {
          this.bizOptions = res && res.success ? (res.result || []) : []
        }).catch(() => {
          this.bizOptions = []
        }).finally(() => {
          this.bizLoading = false
        })
      },
      handleBizChange (value) {
        const hit = this.bizOptions.filter(item => item.id === value)[0]
        if (!hit) {
          this.query.bizId = value
          this.query.bizKey = ''
          return
        }
        this.query.bizId = hit.id
        this.query.bizKey = this.query.bizType === 'facility' ? hit.ptxmmc : hit.crzdbh
      },
      optionLabel (item) {
        if (this.query.bizType === 'facility') {
          return item.ptxmmc + (item.crzdbh ? `（${item.crzdbh}）` : '')
        }
        return (item.crzdbh || '') + (item.dkmc ? `（${item.dkmc}）` : '')
      },

      // ---------------- 行操作 ----------------

      handleUpload () {
        this.$refs.uploadModal.open()
      },
      handleUploaded () {
        this.refresh()
      },
      handlePreview (record) {
        this.$refs.previewModal.open(record)
      },
      handleDownload (record) {
        downloadAttachment(record.id, record.fileName)
        this.$message.success('已开始下载')
        // 下载次数由服务端累加，刷新列表才能看到新值（不刷新会让用户以为没统计上）
        this.loadData()
      },
      handleDelete (record) {
        const self = this
        this.$confirm({
          title: `确认删除附件「${record.fileName || ''}」吗？`,
          content: '删除是逻辑删除：记录会从列表中消失，磁盘上的文件保留（同一份文件可能被多条业务引用）。',
          okText: '删除',
          okType: 'danger',
          cancelText: '取消',
          onOk () {
            return deleteAttachment(record.id).then(res => {
              if (!res.success) {
                self.$message.warning(res.message || '删除失败')
                return
              }
              self.$message.success('已删除')
              self.refresh()
            }).catch(e => {
              self.$message.error((e && e.message) || '删除失败')
            })
          }
        })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;
  @primary: #2e7cf6;

  .attachment-list {
    /*
     * ★ 用 /deep/ 而不是 :deep()：本工程是 Vue2 + vue-loader 15，:deep() 是 Vue3 写法，
     *   在 less 编译阶段不会被识别成深度选择器，这条 padding 规则会静默失效。
     */
    /deep/ .ant-card-body {
      padding: 16px;
    }

    .page-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      padding-bottom: 14px;
      margin-bottom: 14px;
      border-bottom: 1px solid @border-color;

      &__title {
        margin: 0 0 6px;
        font-size: 18px;
        font-weight: 600;
        color: #0f172a;
      }

      &__desc {
        max-width: 960px;
        margin: 0;
        font-size: 12px;
        line-height: 20px;
        color: @text-muted;

        b {
          color: #0f172a;
        }
      }

      &__actions {
        display: flex;
        flex: 0 0 auto;
        flex-wrap: wrap;
        gap: 8px;
      }
    }

    .attach-stats {
      margin-bottom: 12px;
    }

    .stat-card {
      padding: 10px 12px;
      margin-bottom: 12px;
      background: #fff;
      border: 1px solid @border-color;
      border-radius: 8px;

      &__label {
        font-size: 12px;
        color: @text-weak;
      }

      &__value {
        margin: 4px 0 2px;
        font-family: 'DIN Alternate', 'Bebas Neue', monospace;
        font-size: 22px;
        line-height: 26px;
        color: #0f172a;
      }

      &__extra {
        font-size: 11px;
        line-height: 16px;
        color: @text-weak;
      }
    }

    .attach-dist {
      padding: 8px 12px;
      font-size: 12px;
      line-height: 20px;
      color: @text-muted;
      background: #f8fafc;
      border: 1px solid @border-color;
      border-radius: 8px;

      &__title {
        color: #0f172a;
      }

      &__item {
        margin-right: 14px;
        white-space: nowrap;

        b {
          margin-left: 4px;
          font-family: 'DIN Alternate', 'Bebas Neue', monospace;
          font-size: 14px;
          color: @primary;
        }
      }

      &__size {
        margin-left: 4px;
        color: @text-weak;
      }
    }

    .attach-search {
      padding: 10px 12px 0;
      margin-bottom: 12px;
      background: #f8fafc;
      border: 1px solid @border-color;
      border-radius: 8px;

      /deep/ .ant-form-item {
        margin-bottom: 10px;
      }

      &__actions {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        align-items: center;
        margin-bottom: 10px;
      }

      &__hint {
        flex: 1 1 auto;
        min-width: 260px;
        font-size: 12px;
        line-height: 18px;
        color: @text-weak;

        b {
          color: #0f172a;
        }
      }
    }

    .cell-main {
      color: #0f172a;
    }

    &__link {
      color: @primary;
      word-break: break-all;
    }

    &__danger {
      color: #cf1322;
    }
  }
</style>
