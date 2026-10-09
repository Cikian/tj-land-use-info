<template>
  <a-modal
    :visible="visible"
    :width="'100%'"
    :footer="null"
    :closable="true"
    :destroyOnClose="true"
    wrapClassName="escalation-detail-modal"
    :bodyStyle="{ padding: '0' }"
    @cancel="close">
    <a-spin :spinning="loading" class="escalation-detail">
      <template v-if="detail">
        <!-- ============ 页头 ============ -->
        <header class="escalation-detail__head">
          <div class="escalation-detail__head-main">
            <div class="escalation-detail__no">{{ detail.projectNo }}</div>
            <div class="escalation-detail__name" :title="detail.projectName">{{ detail.projectName }}</div>
          </div>
          <div class="escalation-detail__head-bar">
            <div class="escalation-detail__tags">
              <a-tag :color="statusColor(detail.status)">{{ detail.status }}</a-tag>
              <a-tag v-if="detail.argResult" :color="argResultColor(detail.argResult)">
                论证结果：{{ dictLabel(DICT.argResult, detail.argResult, detail.argResult_dictText) }}
              </a-tag>
              <a-tag v-else>论证结果：未登记</a-tag>
              <a-tag color="blue">{{ detail.materialCount || 0 }} 个材料</a-tag>
              <a-tag color="blue">{{ records.length }} 条意见记录</a-tag>
            </div>
            <div class="escalation-detail__head-actions">
              <a-button v-has="'land:escalation:edit'" icon="edit" @click="$emit('edit', detail)">编辑</a-button>
              <a-button icon="printer" @click="handlePrint">打印</a-button>
              <a-button icon="reload" :loading="loading" @click="load(detail.id)">刷新</a-button>
            </div>
          </div>
        </header>

        <!-- ============ 页签 ============ -->
        <a-tabs v-model="activeTab" class="escalation-detail__tabs">
          <!-- ---------- 基本信息 ---------- -->
          <a-tab-pane key="base" tab="基本信息">
            <div class="escalation-detail__panel">
              <div class="escalation-detail__section-title">项目基本信息</div>
              <a-descriptions class="detail-desc" size="small" bordered :column="{ xxl: 3, xl: 3, lg: 2, md: 2, sm: 1, xs: 1 }">
                <a-descriptions-item label="项目编号">{{ detail.projectNo }}</a-descriptions-item>
                <a-descriptions-item label="项目名称">{{ detail.projectName || '—' }}</a-descriptions-item>
                <a-descriptions-item label="申报单位">{{ detail.declareDept || '—' }}</a-descriptions-item>
                <a-descriptions-item label="项目类型">
                  {{ dictLabel(DICT.projectType, detail.projectType, detail.projectType_dictText) || '—' }}
                </a-descriptions-item>
                <a-descriptions-item label="项目规模">{{ detail.projectScale || '—' }}</a-descriptions-item>
                <a-descriptions-item label="总投资（亿元）">{{ investmentText(detail.totalInvestment) }}</a-descriptions-item>
                <a-descriptions-item label="建设地点">{{ detail.buildLocation || '—' }}</a-descriptions-item>
                <a-descriptions-item label="申报时间">{{ detail.declareDate || '—' }}</a-descriptions-item>
                <a-descriptions-item label="项目概述">{{ detail.projectSummary || '—' }}</a-descriptions-item>
              </a-descriptions>

              <!--
                ★ 设计文档 5.4：第 9–16 项是独立的「关联地块信息（选填）」分组，
                  不与「项目基本信息」混排（论证对象是项目，地块只是可选上下文）。
              -->
              <div class="escalation-detail__section-title">
                关联地块信息（选填）
                <span class="escalation-detail__section-hint">论证对象是项目，地块信息仅作上下文</span>
              </div>
              <a-descriptions class="detail-desc" size="small" bordered :column="{ xxl: 3, xl: 3, lg: 2, md: 2, sm: 1, xs: 1 }">
                <a-descriptions-item label="行政区划">{{ detail.xzqh || '—' }}</a-descriptions-item>
                <a-descriptions-item label="功能区">{{ detail.gnq || '—' }}</a-descriptions-item>
                <a-descriptions-item label="地块名称">{{ detail.dkmc || '—' }}</a-descriptions-item>
                <a-descriptions-item label="地块面积（㎡）">{{ detail.dkArea || '—' }}</a-descriptions-item>
                <a-descriptions-item label="规划用地性质">{{ detail.ghydxz || '—' }}</a-descriptions-item>
                <a-descriptions-item label="是否土地整理项目">{{ dictLabel(DICT.yn, detail.tdzlProject) || '—' }}</a-descriptions-item>
                <a-descriptions-item label="关联出让宗地编号">{{ detail.crzdbh || '—' }}</a-descriptions-item>
                <a-descriptions-item label="关联配套项目">{{ detail.ptxmmc || '—' }}</a-descriptions-item>
              </a-descriptions>

              <div class="escalation-detail__section-title">提级论证信息</div>
              <a-descriptions class="detail-desc" size="small" bordered :column="{ xxl: 3, xl: 3, lg: 2, md: 2, sm: 1, xs: 1 }">
                <a-descriptions-item label="提级论证事由">{{ detail.argReason || '—' }}</a-descriptions-item>
                <a-descriptions-item label="提级论证依据">{{ detail.argBasis || '—' }}</a-descriptions-item>
                <a-descriptions-item label="必要性说明">{{ detail.argNecessity || '—' }}</a-descriptions-item>
                <a-descriptions-item label="可行性说明">{{ detail.argFeasibility || '—' }}</a-descriptions-item>
                <a-descriptions-item label="论证事项内容">{{ detail.argContent || '—' }}</a-descriptions-item>
              </a-descriptions>

              <div class="escalation-detail__section-title">
                论证结果与办理状态
                <span class="escalation-detail__section-hint">流程在线下办理，以下为人工登记</span>
              </div>
              <a-descriptions class="detail-desc" size="small" bordered :column="{ xxl: 3, xl: 3, lg: 2, md: 2, sm: 1, xs: 1 }">
                <a-descriptions-item label="办理状态">
                  <a-tag :color="statusColor(detail.status)">{{ detail.status }}</a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="论证结果">
                  <a-tag v-if="detail.argResult" :color="argResultColor(detail.argResult)">{{ detail.argResult }}</a-tag>
                  <span v-else class="escalation-detail__muted">未登记</span>
                </a-descriptions-item>
                <a-descriptions-item label="论证结论">{{ detail.argConclusion || '—' }}</a-descriptions-item>
                <a-descriptions-item label="论证组织单位">{{ detail.argOrg || '—' }}</a-descriptions-item>
                <a-descriptions-item label="论证会日期">{{ detail.argMeetingDate || '—' }}</a-descriptions-item>
                <a-descriptions-item label="论证完成日期">{{ detail.argDate || '—' }}</a-descriptions-item>
                <a-descriptions-item label="论证专家名单">{{ detail.argExpertList || '—' }}</a-descriptions-item>
                <a-descriptions-item label="材料数 / 意见记录数">
                  {{ detail.materialCount || 0 }} 个 / {{ records.length }} 条
                </a-descriptions-item>
                <a-descriptions-item label="创建信息">{{ joinInfo(detail.createBy, detail.createTime) }}</a-descriptions-item>
                <!-- 最后一项不写 :span（见设计文档 5.7 与 scripts/verify-sfc.js 的 a-descriptions 体检） -->
                <a-descriptions-item label="备注">{{ detail.remark || '—' }}</a-descriptions-item>
              </a-descriptions>
            </div>
          </a-tab-pane>

          <!-- ---------- 提级论证材料 ---------- -->
          <a-tab-pane key="materials" :tab="`提级论证材料（${detail.materialCount || 0}）`">
            <div class="escalation-detail__panel">
              <escalation-material-table
                ref="materialTable"
                :projectId="detail.id"
                :disabled="true" />
            </div>
          </a-tab-pane>

          <!-- ---------- 审核意见记录（append-only，时间倒序） ---------- -->
          <a-tab-pane key="records" :tab="`审核意见记录（${records.length}）`">
            <div class="escalation-detail__panel">
              <a-empty v-if="!records.length" description="还没有登记过审核意见" />
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
                      v-for="(file, index) in parseAttachments(item.attachmentIds)"
                      :key="file.path"
                      class="record-list__file"
                      @click="openAttachment(file.path)">
                      <a-icon type="paper-clip" />
                      {{ file.name || `附件 ${index + 1}` }}
                    </a>
                  </div>
                </li>
              </ul>
            </div>
          </a-tab-pane>

          <!-- ---------- 登记审核意见（与审批页共用同一面板） ---------- -->
          <a-tab-pane key="register" tab="登记审核意见">
            <div class="escalation-detail__panel escalation-detail__panel--register">
              <escalation-record-panel
                ref="recordPanel"
                :projectId="detail.id"
                :currentStatus="detail.status"
                @ok="handleRecordOk" />
            </div>
          </a-tab-pane>
        </a-tabs>
      </template>
    </a-spin>
  </a-modal>
</template>

<script>
  import { getFileAccessHttpUrl } from '@/api/manage'
  import EscalationMaterialTable from './EscalationMaterialTable'
  import EscalationRecordPanel from './EscalationRecordPanel'
  import {
    DICT,
    actionColor,
    argResultColor,
    dictText,
    queryProjectById,
    queryRecordList,
    statusColor
  } from '@/api/land/escalation'

  /**
   * 提级论证项目详情（只读全屏弹窗，设计文档 5.2）
   *
   * 三个页签 + 一个登记页签：基本信息（含独立的「关联地块信息（选填）」分组）/
   * 提级论证材料（只读，可下载）/ 审核意见记录（append-only，时间倒序）/ 登记审核意见。
   * 登记页签复用 EscalationRecordPanel，与「提级论证审批」页完全同一套交互。
   *
   * ★ a-descriptions 的 column 与 span 写法照档案详情页（设计文档 5.7）：
   *   column 用响应式对象、所有 item 都写 span=1（不显式写 :span），最后一项尤其不能写。
   */
  export default {
    name: 'EscalationDetailModal',
    components: { EscalationMaterialTable, EscalationRecordPanel },
    data () {
      return {
        DICT,
        visible: false,
        loading: false,
        activeTab: 'base',
        detail: null,
        records: []
      }
    },
    methods: {
      statusColor,
      argResultColor,
      actionColor,
      /**
       * 打开详情
       * @param {object} record 列表行（至少要有 id）
       * @param {string} [tab] 指定落在哪个页签（台账的「材料下载」直达 materials）
       */
      open (record, tab) {
        if (!record || !record.id) {
          this.$message.warning('请选择要查看的项目')
          return
        }
        this.visible = true
        this.activeTab = tab || 'base'
        this.detail = null
        this.records = []
        this.load(record.id)
      },
      load (id) {
        this.loading = true
        return queryProjectById(id).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            this.visible = false
            return
          }
          this.detail = res.result || null
          // 详情里若已带意见记录就直接用，否则再拉一次记录接口
          const inline = (this.detail && this.detail.records) || null
          if (inline) {
            this.records = inline
          } else {
            this.loadRecords()
          }
        }).finally(() => {
          this.loading = false
        })
      },
      loadRecords () {
        if (!this.detail) {
          return Promise.resolve()
        }
        return queryRecordList(this.detail.id).then(res => {
          if (res.success) {
            this.records = res.result || []
          }
        })
      },
      handleRecordOk () {
        // 登记成功后同步刷新意见列表、材料数与主表状态
        this.loadRecords()
        if (this.$refs.materialTable) {
          this.$refs.materialTable.refresh()
        }
        return queryProjectById(this.detail.id).then(res => {
          if (res.success && res.result) {
            this.detail = Object.assign({}, this.detail, res.result)
          }
        })
      },
      handlePrint () {
        window.print()
      },
      close () {
        this.visible = false
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
      investmentText (value) {
        if (value === null || value === undefined || value === '') {
          return '—'
        }
        const num = Number(value)
        return isNaN(num) ? value : num.toFixed(4).replace(/\.?0+$/, '')
      },
      joinInfo (a, b) {
        const parts = [a, b].filter(v => v !== null && v !== undefined && v !== '')
        return parts.length ? parts.join(' · ') : '—'
      },
      /** attachment_ids 是逗号分隔的存储路径；文件名从路径末段取 */
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

  .escalation-detail {
    display: block;
    height: 100%;

    &__head {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 16px 24px 14px;
      background: #f8fafc;
      border-bottom: 1px solid @border-color;
    }

    &__head-main {
      min-width: 0;
      /* 给右上角 antd 关闭按钮的 56×56 点击区留出净空 */
      padding-right: 48px;
    }

    &__head-bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 10px 16px;
      min-width: 0;
    }

    &__no {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 13px;
      letter-spacing: 1px;
      color: #2e7cf6;
    }

    &__name {
      margin: 2px 0 0;
      font-size: 18px;
      font-weight: 600;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__tags {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
      min-width: 0;

      /deep/ .ant-tag {
        margin: 0;
      }
    }

    &__head-actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      margin-left: auto;
      gap: 8px;

      /deep/ .ant-btn {
        flex: none;
        white-space: nowrap;
      }
    }

    &__tabs {
      padding: 0 24px 24px;

      /deep/ .ant-tabs-bar {
        margin-bottom: 16px;
      }
    }

    &__panel {
      min-height: 320px;

      &--register {
        max-width: 760px;
      }
    }

    &__section-title {
      display: flex;
      align-items: baseline;
      gap: 8px;
      margin: 4px 0 10px;
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;

      &:not(:first-child) {
        margin-top: 22px;
      }
    }

    &__section-hint {
      font-size: 12px;
      font-weight: 400;
      color: @text-weak;
    }

    &__muted {
      color: @text-weak;
    }
  }

  /*
    信息表（a-descriptions，bordered）：table-layout 必须固定，
    否则长文本（项目概述 / 论证事由）会把标签列挤到只剩两三个字宽。
    写法与 ArchiveDetailModal.vue 一致（设计文档 5.7）。
  */
  .escalation-detail /deep/ .detail-desc {
    .ant-descriptions-view > table {
      table-layout: fixed;
    }

    .ant-descriptions-item-label,
    .ant-descriptions-item-content {
      padding: 10px 12px;
      vertical-align: middle;
      word-break: break-word;
    }

    .ant-descriptions-item-label {
      width: 140px;
      color: @text-muted;
      font-weight: 400;
    }
  }

  /* ---------------- 意见记录列表 ---------------- */
  .record-list {
    margin: 0;
    padding: 0;
    list-style: none;

    &__item {
      padding: 12px 14px;
      margin-bottom: 10px;
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
      margin-bottom: 6px;
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
      color: #0f172a;
      font-weight: 500;
    }

    &__opinion {
      font-size: 13px;
      line-height: 21px;
      color: #334155;
      white-space: pre-wrap;
      word-break: break-word;
    }

    &__files {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-top: 8px;
    }

    &__file {
      font-size: 12px;
    }
  }
</style>

<!--
  全屏弹窗的样式必须放在非 scoped 的 style 里：
  a-modal 渲染到 body 下，wrapClassName 所在的节点不在本组件 DOM 树内，
  scoped 属性选择器匹配不到（与 ArchiveDetailModal.vue 的处理一致）。
-->
<style lang="less">
  .escalation-detail-modal {
    top: 0;
    padding-bottom: 0;

    .ant-modal {
      top: 0;
      max-width: 100%;
      padding-bottom: 0;
      margin: 0;
    }

    .ant-modal-content {
      display: flex;
      flex-direction: column;
      height: 100vh;
      border-radius: 0;
    }

    .ant-modal-body {
      flex: 1 1 auto;
      min-height: 0;
      overflow: auto;
      padding: 0;
    }

    .ant-modal-close {
      color: #64789a;
      transition: color 0.2s ease;

      &:hover,
      &:focus {
        color: #2e7cf6;
      }
    }
  }
</style>
