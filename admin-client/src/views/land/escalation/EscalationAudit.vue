<template>
  <a-card :bordered="false" class="escalation-audit-page">
    <!-- 页头：菜单名是「提级论证审批」，页面内标题写「审核意见登记」并注明线下办理 -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">审核意见登记</h2>
        <p class="page-head__desc">
          <a-icon type="info-circle" class="page-head__icon" />
          流程在线下办理，此处仅登记论证结论与审核意见。意见记录只增不改不删（可追溯），
          登记意见<b>不会</b>自动推进办理状态；如需同步状态，请在右侧面板勾选「同时更新办理状态」。
        </p>
      </div>
      <div class="page-head__actions">
        <span class="page-head__filter-label">办理状态</span>
        <a-select v-model="filterStatus" allowClear placeholder="全部" style="width: 130px" @change="handleStatusFilter">
          <a-select-option v-for="item in statusOptions" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>
        <a-button icon="reload" :loading="projectLoading" @click="loadProjects">刷新</a-button>
      </div>
    </div>

    <div class="audit-layout">
      <!-- ============ 左：项目列表 ============ -->
      <div class="audit-col audit-col--left">
        <div class="audit-col__head">
          <span class="audit-col__title">项目列表</span>
          <span class="audit-col__count">共 {{ projectTotal }} 个</span>
        </div>
        <a-input-search
          v-model="keyword"
          placeholder="项目名称 / 编号"
          allowClear
          class="audit-col__search"
          @search="handleSearchProject" />
        <a-spin :spinning="projectLoading">
          <a-empty v-if="!projects.length" description="没有匹配的项目" />
          <ul v-else class="project-list">
            <li
              v-for="item in projects"
              :key="item.id"
              class="project-list__item"
              :class="{ 'project-list__item--active': current && current.id === item.id }"
              @click="handleSelect(item)">
              <div class="project-list__row">
                <span class="project-list__dot" :style="{ background: statusDotColor(item.status) }"></span>
                <span class="project-list__name" :title="item.projectName">{{ item.projectName }}</span>
              </div>
              <div class="project-list__meta">
                <span class="project-list__no">{{ item.projectNo }}</span>
                <a-tag :color="statusColor(item.status)">{{ item.status }}</a-tag>
                <a-tag v-if="item.argResult" :color="argResultColor(item.argResult)">{{ item.argResult }}</a-tag>
              </div>
            </li>
          </ul>
        </a-spin>
        <div v-if="projectTotal > pagination.pageSize" class="audit-col__pager">
          <a-pagination
            size="small"
            :current="pagination.current"
            :pageSize="pagination.pageSize"
            :total="projectTotal"
            :showSizeChanger="false"
            @change="handleProjectPageChange" />
        </div>
      </div>

      <!-- ============ 中：项目信息 / 材料 / 意见记录 ============ -->
      <div class="audit-col audit-col--middle">
        <a-empty v-if="!current" description="请从左侧选择一个项目" class="audit-col__empty" />
        <a-spin v-else :spinning="detailLoading">
          <div class="audit-block">
            <div class="audit-block__title">
              <span>{{ current.projectName }}</span>
              <a class="audit-block__link" @click="openDetail(current)">查看完整详情</a>
            </div>
            <a-descriptions class="detail-desc" size="small" bordered :column="{ xxl: 2, xl: 2, lg: 2, md: 1, sm: 1, xs: 1 }">
              <a-descriptions-item label="项目编号">{{ current.projectNo }}</a-descriptions-item>
              <a-descriptions-item label="申报单位">{{ current.declareDept || '—' }}</a-descriptions-item>
              <a-descriptions-item label="项目类型">
                {{ dictLabel(DICT.projectType, current.projectType, current.projectType_dictText) || '—' }}
              </a-descriptions-item>
              <a-descriptions-item label="总投资（亿元）">{{ current.totalInvestment || '—' }}</a-descriptions-item>
              <a-descriptions-item label="申报时间">{{ current.declareDate || '—' }}</a-descriptions-item>
              <a-descriptions-item label="建设地点">{{ current.buildLocation || '—' }}</a-descriptions-item>
              <a-descriptions-item label="办理状态">
                <a-tag :color="statusColor(current.status)">{{ current.status }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="论证结果">
                <a-tag v-if="current.argResult" :color="argResultColor(current.argResult)">{{ current.argResult }}</a-tag>
                <span v-else class="audit-muted">未登记</span>
              </a-descriptions-item>
              <a-descriptions-item label="论证组织单位">{{ current.argOrg || '—' }}</a-descriptions-item>
              <a-descriptions-item label="论证会日期">{{ current.argMeetingDate || '—' }}</a-descriptions-item>
              <a-descriptions-item label="论证完成日期">{{ current.argDate || '—' }}</a-descriptions-item>
              <a-descriptions-item label="论证结论">{{ current.argConclusion || '—' }}</a-descriptions-item>
            </a-descriptions>
          </div>

          <div class="audit-block">
            <div class="audit-block__title">
              <span>提级论证材料</span>
              <span class="audit-block__hint">共 {{ current.materialCount || 0 }} 个，可下载</span>
            </div>
            <escalation-material-table
              ref="materialTable"
              :projectId="current.id"
              :disabled="true" />
          </div>

          <div class="audit-block">
            <div class="audit-block__title">
              <span>审核意见记录</span>
              <span class="audit-block__hint">时间倒序 · append-only，共 {{ records.length }} 条</span>
            </div>
            <a-empty v-if="!records.length" description="该项目还没有登记过审核意见" />
            <ul v-else class="record-list">
              <li v-for="item in records" :key="item.id" class="record-list__item">
                <div class="record-list__head">
                  <span class="record-list__time">{{ item.recordTime || item.createTime || '—' }}</span>
                  <span class="record-list__who">{{ item.recorderName || item.recorderId || '—' }}</span>
                  <a-tag>{{ item.recordType || '审核意见' }}</a-tag>
                  <a-tag v-if="item.action" :color="actionColor(item.action)">{{ item.action }}</a-tag>
                </div>
                <div class="record-list__opinion">{{ item.opinion }}</div>
                <div v-if="parseAttachments(item.attachmentIds).length" class="record-list__files">
                  <a
                    v-for="file in parseAttachments(item.attachmentIds)"
                    :key="file.path"
                    class="record-list__file"
                    @click="openAttachment(file.path)">
                    <a-icon type="paper-clip" />{{ file.name }}
                  </a>
                </div>
              </li>
            </ul>
          </div>
        </a-spin>
      </div>

      <!-- ============ 右：登记审核意见 ============ -->
      <div class="audit-col audit-col--right">
        <div class="audit-col__head">
          <span class="audit-col__title">登记审核意见</span>
          <span v-if="current" class="audit-col__count">{{ current.projectNo }}</span>
        </div>
        <a-empty v-if="!current" description="请先从左侧选择项目" class="audit-col__empty" />
        <escalation-record-panel
          v-else
          ref="recordPanel"
          :projectId="current.id"
          :currentStatus="current.status"
          @ok="handleRecordOk" />
      </div>
    </div>

    <escalation-detail-modal ref="detail" />
  </a-card>
</template>

<script>
  import { getFileAccessHttpUrl } from '@/api/manage'
  import EscalationMaterialTable from './modules/EscalationMaterialTable'
  import EscalationRecordPanel from './modules/EscalationRecordPanel'
  import EscalationDetailModal from './modules/EscalationDetailModal'
  import {
    DICT,
    STATUS_OPTIONS,
    actionColor,
    argResultColor,
    dictText,
    loadAllDicts,
    queryProjectById,
    queryProjectList,
    queryRecordList,
    statusColor,
    statusDotColor
  } from '@/api/land/escalation'

  /**
   * 提级论证审批 —— 页面内标题为「审核意见登记」（设计文档 2.5 / 5.3）
   *
   * 菜单名保留「提级论证审批」以便与方案 2.3.3 第 4 项对号，但页面明确写「审核意见登记」
   * 并注明「流程在线下办理，此处仅登记论证结论与审核意见」（Q3 默认取值）。
   *
   * 三栏布局（对齐原型，去掉流转要素）：
   *   左  项目列表（可搜索 + 办理状态筛选）
   *   中  项目基本信息（a-descriptions）→ 提级论证材料 → 审核意见记录（时间倒序）
   *   右  登记审核意见面板（1000 字计数 / 5 个快捷短语 / 附件 ≤50MB / 默认不同步状态）
   */
  export default {
    name: 'EscalationAudit',
    components: { EscalationMaterialTable, EscalationRecordPanel, EscalationDetailModal },
    data () {
      return {
        DICT,
        statusOptions: STATUS_OPTIONS,
        keyword: '',
        filterStatus: undefined,
        projectLoading: false,
        detailLoading: false,
        projects: [],
        projectTotal: 0,
        current: null,
        records: [],
        pagination: {
          current: 1,
          pageSize: 10
        }
      }
    },
    created () {
      loadAllDicts()
      this.loadProjects()
    },
    methods: {
      statusColor,
      argResultColor,
      actionColor,
      statusDotColor,
      // ------------------------------------------------------------------
      // 左侧项目列表
      // ------------------------------------------------------------------
      loadProjects () {
        this.projectLoading = true
        const params = {
          pageNo: this.pagination.current,
          pageSize: this.pagination.pageSize
        }
        if (this.keyword) {
          params.projectName = this.keyword
        }
        if (this.filterStatus) {
          params.status = this.filterStatus
        }
        return queryProjectList(params).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            return
          }
          const page = res.result || {}
          this.projects = page.records || []
          this.projectTotal = page.total || 0
          // 选中项被筛掉时清空右侧，避免「列表里没有、右侧还显示着」
          if (this.current && !this.projects.some(item => item.id === this.current.id)) {
            this.current = null
            this.records = []
          }
        }).finally(() => {
          this.projectLoading = false
        })
      },
      handleSearchProject () {
        this.pagination.current = 1
        this.loadProjects()
      },
      handleStatusFilter () {
        this.pagination.current = 1
        this.loadProjects()
      },
      handleProjectPageChange (page) {
        this.pagination.current = page
        this.loadProjects()
      },
      handleSelect (record) {
        this.current = record
        this.records = []
        this.detailLoading = true
        queryProjectById(record.id).then(res => {
          if (res.success && res.result) {
            this.current = Object.assign({}, record, res.result)
            const inline = res.result.records
            if (inline) {
              this.records = inline
            } else {
              this.loadRecords()
            }
          } else {
            this.loadRecords()
          }
        }).finally(() => {
          this.detailLoading = false
        })
      },
      loadRecords () {
        if (!this.current) {
          return Promise.resolve()
        }
        return queryRecordList(this.current.id).then(res => {
          if (res.success) {
            this.records = res.result || []
          }
        })
      },
      /** 登记成功后：刷新意见记录、材料数、当前项目与左侧列表（状态可能被同步更新） */
      handleRecordOk () {
        const id = this.current ? this.current.id : null
        this.loadRecords()
        if (this.$refs.materialTable) {
          this.$refs.materialTable.refresh()
        }
        if (!id) {
          return
        }
        return queryProjectById(id).then(res => {
          if (res.success && res.result) {
            this.current = Object.assign({}, this.current, res.result)
          }
        }).then(() => {
          this.loadProjects()
        })
      },
      openDetail (record) {
        this.$refs.detail.open(record)
      },
      // ------------------------------------------------------------------
      // 展示工具
      // ------------------------------------------------------------------
      dictLabel (dictCode, value, dictTextFromServer) {
        if (value === null || value === undefined || value === '') {
          return ''
        }
        return dictTextFromServer || dictText(dictCode, value) || value
      },
      parseAttachments (ids) {
        if (!ids) {
          return []
        }
        return String(ids).split(',').filter(Boolean).map(path => {
          const clean = path.split('?')[0]
          const segments = clean.split('/')
          return { path: clean, name: segments[segments.length - 1] }
        })
      },
      openAttachment (path) {
        const url = getFileAccessHttpUrl(path)
        if (url) {
          window.open(url, '_blank')
        }
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .escalation-audit-page {
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
      max-width: 780px;
      font-size: 13px;
      line-height: 20px;
      color: @text-muted;

      b {
        color: #b45309;
      }
    }

    &__icon {
      margin-right: 4px;
      color: #d48806;
    }

    &__actions {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      flex-shrink: 0;
    }

    &__filter-label {
      font-size: 13px;
      color: @text-muted;
    }
  }

  /* ---------------- 三栏布局 ---------------- */
  .audit-layout {
    display: grid;
    grid-template-columns: 300px minmax(420px, 1fr) 400px;
    gap: 16px;
    align-items: start;
    margin-top: 16px;
  }

  @media (max-width: 1500px) {
    .audit-layout {
      grid-template-columns: 280px minmax(380px, 1fr) 360px;
    }
  }

  @media (max-width: 1200px) {
    .audit-layout {
      grid-template-columns: 1fr;
    }
  }

  .audit-col {
    min-width: 0;
    padding: 14px 16px;
    background: #fff;
    border: 1px solid @border-color;
    border-radius: 8px;

    &--left,
    &--middle {
      max-height: calc(100vh - 260px);
      overflow: auto;
    }

    &__head {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 10px;
    }

    &__title {
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
    }

    &__count {
      font-size: 12px;
      color: @text-weak;
    }

    &__search {
      margin-bottom: 10px;
    }

    &__pager {
      margin-top: 10px;
      text-align: right;
    }

    &__empty {
      padding: 40px 0;
    }
  }

  /* ---------------- 左侧项目列表 ---------------- */
  .project-list {
    margin: 0;
    padding: 0;
    list-style: none;

    &__item {
      padding: 8px 10px;
      margin-bottom: 6px;
      border: 1px solid transparent;
      border-radius: 6px;
      cursor: pointer;

      &:hover {
        background: #f8fafc;
      }

      &--active {
        background: #eff6ff;
        border-color: #93c5fd;
      }
    }

    &__row {
      display: flex;
      align-items: center;
      gap: 6px;
      min-width: 0;
    }

    &__dot {
      flex: none;
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }

    &__name {
      flex: 1 1 auto;
      min-width: 0;
      font-size: 13px;
      font-weight: 500;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__meta {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 6px;
      margin-top: 4px;
      padding-left: 14px;

      /deep/ .ant-tag {
        margin: 0;
        font-size: 11px;
        line-height: 16px;
      }
    }

    &__no {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 11px;
      color: @text-weak;
    }
  }

  /* ---------------- 中栏区块 ---------------- */
  .audit-block {
    margin-bottom: 18px;

    &__title {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 10px;
      margin-bottom: 10px;
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
    }

    &__hint {
      font-size: 12px;
      font-weight: 400;
      color: @text-weak;
    }

    &__link {
      font-size: 12px;
      font-weight: 400;
    }
  }

  /* a-descriptions：固定表格布局，避免长文本挤坏标签列（设计文档 5.7） */
  .audit-col /deep/ .detail-desc {
    .ant-descriptions-view > table {
      table-layout: fixed;
    }

    .ant-descriptions-item-label,
    .ant-descriptions-item-content {
      padding: 8px 10px;
      vertical-align: middle;
      word-break: break-word;
    }

    .ant-descriptions-item-label {
      width: 112px;
      color: @text-muted;
      font-weight: 400;
    }
  }

  /* ---------------- 意见记录 ---------------- */
  .record-list {
    margin: 0;
    padding: 0;
    list-style: none;

    &__item {
      padding: 10px 12px;
      margin-bottom: 8px;
      background: #fff;
      border: 1px solid @border-color;
      border-left: 3px solid #2e7cf6;
      border-radius: 6px;
    }

    &__head {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 5px;
      font-size: 12px;

      /deep/ .ant-tag {
        margin: 0;
      }
    }

    &__time {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      color: @text-muted;
    }

    &__who {
      font-weight: 500;
      color: #0f172a;
    }

    &__opinion {
      font-size: 13px;
      line-height: 20px;
      color: #334155;
      white-space: pre-wrap;
      word-break: break-word;
    }

    &__files {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-top: 6px;
    }

    &__file {
      font-size: 12px;
    }
  }

  .audit-muted {
    color: @text-weak;
  }
</style>
