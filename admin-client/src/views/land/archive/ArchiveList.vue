<template>
  <a-card :bordered="false" class="archive-list-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">档案维护</h2>
        <p class="page-head__desc">
          把已上传的配套项目档案登记成案卷：选择出让宗地与配套项目、填写档案基本信息、上传档案文件。
          每个文件必须关联一个档案类别（末级类别）；档案号默认自动生成，也允许按历史编号手工改写。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:archive:add'" type="primary" icon="plus" @click="handleAdd">新增档案</a-button>
        <!-- 导出按钮的可用性跟随「当前视图有没有数据」：项目视图下 total（案卷数）恒为 0，
             只看它会出现「明明列表里有项目、却导不出」 -->
        <a-button v-has="'land:archive:export'" icon="download" :disabled="!hasAnyData" @click="handleExport">按条件导出 ZIP</a-button>
        <a-button v-has="'land:archive:stat'" icon="pie-chart" @click="goStatistics">档案统计</a-button>
      </div>
    </div>

    <!-- 查询条件 -->
    <archive-search-form ref="search" @search="handleSearch" />

    <!-- 列表 -->
    <div class="list-panel">
      <div class="list-panel__toolbar">
        <div class="list-panel__toolbar-left">
          <!-- 两种视图的分页口径不同（项目 / 案卷），总数必须跟着视图说，不能共用一个数字 -->
          <span v-if="isProjectView" class="list-panel__total">
            共 <b>{{ projectTotal }}</b> 个项目
          </span>
          <span v-else class="list-panel__total">
            共 <b>{{ total }}</b> 个档案
          </span>
          <span v-if="!isProjectView && selectedRowKeys.length" class="list-panel__selected">
            已选 <b>{{ selectedRowKeys.length }}</b> 项
          </span>
        </div>
        <div class="list-panel__toolbar-right">
          <!-- 默认项目视图：档案本来就挂在配套项目上，先按项目看更接近业务视角；
               案卷视图保留，批量选择/批量删除仍然只在案卷视图里做 -->
          <a-radio-group v-model="viewMode" button-style="solid" size="small" @change="handleViewChange">
            <a-radio-button value="project">项目视图</a-radio-button>
            <a-radio-button value="flat">案卷视图</a-radio-button>
          </a-radio-group>
          <a-popconfirm
            v-if="selectedRowKeys.length"
            v-has="'land:archive:delete'"
            title="删除后不可恢复，确定批量删除选中的档案吗？"
            okText="确定"
            cancelText="取消"
            @confirm="handleBatchDelete">
            <a-button type="danger" icon="delete" :loading="deleting">批量删除</a-button>
          </a-popconfirm>
          <a-button icon="reload" :loading="loading" @click="loadData">刷新</a-button>
        </div>
      </div>

      <!-- 项目视图：项目 → 案卷 → 材料类别 → 文件，案卷懒加载 -->
      <archive-project-tree
        v-if="isProjectView"
        :dataSource="projectRows"
        :loading="loading"
        :pagination="projectPagination"
        :query="query"
        @change="handleTableChange"
        @detail="handleDetail"
        @export-project="handleExportOne" />

      <!-- 案卷视图：原来的逐卷平铺列表（批量选择 / 批量删除在这里） -->
      <archive-table
        v-else
        :dataSource="dataSource"
        :loading="loading"
        :pagination="pagination"
        :selectable="true"
        :selectedRowKeys="selectedRowKeys"
        @change="handleTableChange"
        @select-change="handleSelectChange"
        @detail="handleDetail"
        @edit="handleEdit"
        @delete="handleDelete" />
    </div>

    <archive-modal ref="modal" @ok="handleSaved" />
    <archive-detail-modal ref="detail" @edit="handleEdit" @export="handleExportOne" />
  </a-card>
</template>

<script>
  import ArchiveSearchForm from './modules/ArchiveSearchForm'
  import ArchiveTable from './modules/ArchiveTable'
  import ArchiveProjectTree from './modules/ArchiveProjectTree'
  import ArchiveModal from './modules/ArchiveModal'
  import ArchiveDetailModal from './modules/ArchiveDetailModal'
  import resetOnActivate from '../mixins/resetOnActivate'
  import {
    queryArchiveList,
    queryArchiveProjectPage,
    queryArchiveById,
    deleteArchive,
    deleteArchiveBatch,
    exportArchiveZip
  } from '@/api/land/archive'

  /**
   * 档案维护（方案 2.3.2 第 2 项）
   *
   * 「可在已上传的配套项目中添加档案信息，并记录相关操作」——
   * 新增/编辑弹窗里的配套项目是下拉选择已有记录，不能手工输入新项目名；
   * 所有写操作都会在后端写 t_archive_log 操作记录。
   *
   * ★ P2-07 两种列表视图（同一份数据、同一套检索条件）：
   *   「项目视图」（默认）：项目 → 案卷 → 材料类别 → 文件，
   *       第一层就能看到材料类别摘要 / 案卷数 / 文件数 / 总大小，不必逐条进详情；
   *   「案卷视图」：原来的逐卷平铺列表，批量选择 / 批量删除仍在这里做。
   *   两种视图共用 ArchiveSearchForm 与 this.query —— 切换视图**不重置检索条件**，
   *   用户不用「切一次视图就重输一遍条件」。
   *   聚合分页以**项目**为单位（后端 /land/archive/projectPage 负责），
   *   不是把全部档案拉到前端再分组 —— 那样数据一多就会把浏览器拖死。
   */
  export default {
    name: 'ArchiveList',
    components: { ArchiveSearchForm, ArchiveTable, ArchiveProjectTree, ArchiveModal, ArchiveDetailModal },
    /*
     * 普通离开再进入时清条件、页码回 1、重查（清单 P2-01）。
     *
     * 本页 13 个检索条件全在 ArchiveSearchForm 里维护，缓存的实例上条件、页码、勾选都在；
     * 从收发文「归档」列点进来的 ?archiveId= 属于下钻，mixin 会认出来并**只重查不清条件**。
     * 契约见 mixins/resetOnActivate.js。视图模式（项目 / 案卷）不是检索条件，保留。
     */
    mixins: [resetOnActivate],
    data () {
      return {
        loading: false,
        deleting: false,
        viewMode: 'project',
        query: {},
        dataSource: [],
        total: 0,
        selectedRowKeys: [],
        /** 项目视图数据：一页 = 一页项目 */
        projectRows: [],
        projectTotal: 0,
        pagination: {
          current: 1,
          pageSize: 10,
          total: 0,
          showSizeChanger: true,
          showQuickJumper: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: total => `共 ${total} 条`
        }
      }
    },
    computed: {
      isProjectView () {
        return this.viewMode === 'project'
      },
      /**
       * 项目视图的分页器。
       * total 用后端返回的**项目总数**，不是案卷总数 ——
       * 用案卷数会让页数虚高（5 个项目、12 个案卷 → 显示 2 页，第 2 页是空的）。
       */
      projectPagination () {
        return Object.assign({}, this.pagination, {
          total: this.projectTotal,
          showTotal: total => `共 ${total} 个项目`
        })
      },
      /** 当前视图有没有数据（导出按钮的可用性要用它判断，见模板注释） */
      hasAnyData () {
        return this.isProjectView ? this.projectTotal > 0 : this.total > 0
      }
    },
    created () {
      this.loadData()
      // ★ 从收发文列表的「归档」列点过来时带 ?archiveId=…：按 id 精确打开该档案详情，
      //   而不是让用户再筛一次列表、再点一行（与收发文归档后的「查看档案」闭环对齐）。
      const archiveId = this.$route && this.$route.query ? this.$route.query.archiveId : ''
      if (archiveId) {
        this.openDetailById(archiveId)
        // 清掉这个一次性参数，避免刷新或再次进入时重复打开
        this.$router.replace({ path: this.$route.path, query: {} }).catch(() => {})
      }
    },
    methods: {
      /**
       * 保存成功（新增 / 编辑）：列表与**已打开的详情**一起刷新（P1-15）。
       *
       * 只刷列表会出现「列表已是新值、详情还是旧值」——测试看到的
       * 「详情状态与列表不一致」有一半来自这里。详情弹窗的 refresh() 不重开弹窗、不重置页签。
       */
      handleSaved () {
        this.loadData()
        if (this.$refs.detail && typeof this.$refs.detail.refresh === 'function') {
          this.$refs.detail.refresh()
        }
      },
      /** 按 id 打开档案详情（深链 / 归档后「查看档案」用） */
      openDetailById (id) {
        if (!id) {
          return
        }
        queryArchiveById(id).then(res => {
          if (res && res.success && res.result) {
            this.$refs.detail.open(res.result)
          } else {
            this.$message.warning((res && res.message) || '打开档案详情失败')
          }
        })
      },
      /** 按当前视图取数（两种视图共用同一份 query 与页码） */
      loadData () {
        return this.isProjectView ? this.loadProjectPage() : this.loadFlatPage()
      },
      /** 案卷视图：原来的逐卷分页 */
      loadFlatPage () {
        this.loading = true
        const params = Object.assign({}, this.query, {
          pageNo: this.pagination.current,
          pageSize: this.pagination.pageSize
        })
        return queryArchiveList(params).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })
          // 当前页被删空时自动回退一页，避免停在空白页
          if (!this.dataSource.length && this.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadFlatPage()
          }
        }).finally(() => {
          this.loading = false
        })
      },
      /** 项目视图：分页以「项目」为单位（pageNo/pageSize 数的是项目数） */
      loadProjectPage () {
        this.loading = true
        const params = Object.assign({}, this.query)
        return queryArchiveProjectPage(params, {
          pageNo: this.pagination.current,
          pageSize: this.pagination.pageSize
        }).then(res => {
          if (!res || !res.success) {
            this.$message.warning((res && res.message) || '项目视图加载失败')
            return
          }
          const page = res.result || {}
          this.projectRows = page.records || []
          this.projectTotal = page.total || 0
          // 与案卷视图同理：当前页被删空时回退一页，不停在空白页
          if (!this.projectRows.length && this.projectTotal > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadProjectPage()
          }
        }).finally(() => {
          this.loading = false
        })
      },
      /**
       * 切换视图。
       *
       * 页码必须回到第 1 页：两种视图的分页口径不同（项目 vs 案卷），
       * 沿用旧页码会出现「第 3 页的项目」配「第 3 页的案卷」这种错位。
       * 检索条件（this.query）与检索表单原样保留 —— 切视图不该让用户重输条件。
       *
       * ★ 只用 this.viewMode，不用 @change 的事件参数：antd 1.7 的 a-radio-group
       *   change 发的是事件对象（值是 e.target.value），不是新值本身；
       *   而 v-model 监听的是同一次点击的 input 事件，this.viewMode 此刻已是新值。
       */
      handleViewChange () {
        const mode = this.viewMode
        this.pagination = Object.assign({}, this.pagination, { current: 1 })
        // 批量操作只在案卷视图做，切走时清掉选中，避免「看不见的选中项被批量删除」
        this.selectedRowKeys = []
        if (mode === 'project') {
          this.projectRows = []
        } else {
          this.dataSource = []
        }
        this.loadData()
      },
      handleSearch (query) {
        this.query = query || {}
        this.pagination.current = 1
        this.selectedRowKeys = []
        this.loadData()
      },
      /**
       * keep-alive 重新进入时的复位入口（由 mixins/resetOnActivate.js 调用）。
       *
       * 检索条件、日期区间、类别树选中值都维护在子组件 ArchiveSearchForm 里，
       * 所以**交给它自己清**：它的 handleReset() 会重置内部 query（含 categoryId/categoryPath
       * 这类树控件的隐藏值）并 emit('search', {})，落到本页 handleSearch ——
       * 条件、页码、勾选、取数一次到位，且只发一次请求。
       * 本页再额外清一次条件/页码是兜底：万一将来表单改成 v-if 渲染导致 $refs 拿不到，
       * 也不会出现「条件已清、列表还是旧结果」。
       */
      resetSearch () {
        if (this.$refs.search && typeof this.$refs.search.handleReset === 'function') {
          this.$refs.search.handleReset()
          return
        }
        this.query = {}
        this.pagination = Object.assign({}, this.pagination, { current: 1 })
        this.selectedRowKeys = []
        this.loadData()
      },
      handleTableChange (pagination) {
        this.pagination = Object.assign({}, this.pagination, {
          current: pagination.current,
          pageSize: pagination.pageSize
        })
        this.loadData()
      },
      handleSelectChange (keys) {
        this.selectedRowKeys = keys
      },
      handleAdd () {
        this.$refs.modal.showAdd()
      },
      handleEdit (record) {
        this.$refs.modal.showEdit(record)
      },
      handleDetail (record) {
        this.$refs.detail.open(record)
      },
      handleDelete (record) {
        deleteArchive(record.id).then(res => {
          if (res.success) {
            this.$message.success(res.message || '删除成功')
            this.selectedRowKeys = this.selectedRowKeys.filter(key => key !== record.id)
            this.loadData()
          } else {
            this.$message.warning(res.message)
          }
        })
      },
      handleBatchDelete () {
        if (!this.selectedRowKeys.length) {
          return
        }
        this.deleting = true
        deleteArchiveBatch(this.selectedRowKeys).then(res => {
          if (res.success) {
            this.$message.success(res.message || '批量删除成功')
            this.selectedRowKeys = []
            this.loadData()
          } else {
            this.$message.warning(res.message)
          }
        }).finally(() => {
          this.deleting = false
        })
      },
      /** 按当前查询条件导出 */
      handleExport () {
        const query = this.$refs.search ? this.$refs.search.getQuery() : {}
        exportArchiveZip(query)
        this.$message.success('已开始导出，请稍候…')
      },
      /**
       * 按「某个项目」导出（详情页的「导出该项目档案」+ 项目视图行内的「导出该项目」共用）。
       *
       * 两个入口传进来的都是「有 facilityId / crzdbh 的数据行」：
       *   详情页是 archive 详情，项目视图行是 /projectPage 的 ArchiveProjectVO。
       * ★ 两者都可能一个都没有（历史数据只存了项目名）：这时**必须拦住**，
       *   否则导出接口收到空条件会导出全部档案 —— 用户以为导的是这一个项目。
       */
      handleExportOne (record) {
        const facilityId = (record && record.facilityId) || undefined
        const crzdbh = facilityId ? undefined : ((record && record.crzdbh) || undefined)
        if (!facilityId && !crzdbh) {
          this.$message.warning('该记录既没有关联配套项目、也没有出让宗地编号，无法按项目导出')
          return
        }
        exportArchiveZip({ facilityId, crzdbh })
        this.$message.success('已开始按项目导出，请稍候…')
      },
      goStatistics () {
        this.$router.push({ path: '/land/archive/statistics' })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;

  .archive-list-page {
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
      color: #0f172a;
    }

    &__desc {
      margin: 0;
      max-width: 820px;
      font-size: 13px;
      line-height: 20px;
      color: #475569;
    }

    &__actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      flex-shrink: 0;
    }
  }

  .list-panel {
    margin-top: 4px;

    &__toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 12px;
    }

    &__toolbar-left {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 13px;
      color: #475569;

      b {
        color: #0f172a;
      }
    }

    &__selected {
      color: #1d4ed8;
    }

    &__toolbar-right {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  @media (max-width: 576px) {
    .page-head__actions {
      width: 100%;

      /deep/ .ant-btn {
        flex: 1 1 auto;
      }
    }
  }
</style>
