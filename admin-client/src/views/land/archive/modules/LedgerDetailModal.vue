<template>
  <a-modal
    :title="null"
    :width="1080"
    :visible="visible"
    :footer="null"
    :maskClosable="true"
    wrapClassName="ledger-detail-modal"
    @cancel="handleClose">
    <a-spin :spinning="loading">
      <div v-if="ledger" class="ledger-detail">
        <!-- ============ 页头 ============ -->
        <header class="ledger-detail__head">
          <div class="ledger-detail__head-main">
            <div class="ledger-detail__no">{{ ledger.ledgerNo }}</div>
            <h3 class="ledger-detail__name">{{ ledger.roadName }}</h3>
          </div>
          <div class="ledger-detail__head-bar">
            <div class="ledger-detail__tags">
              <a-tag :color="statusColor(ledger.status)">{{ ledger.status || '未验收' }}</a-tag>
              <a-tag v-if="ledger.acceptanceResult" :color="acceptanceResultColor(ledger.acceptanceResult)">
                {{ ledger.acceptanceResult }}
              </a-tag>
              <a-tag :color="materialTagColor">{{ materialText }}</a-tag>
              <a-tag v-if="ledger.xzqh">{{ ledger.xzqh }}</a-tag>
              <a-tag v-else-if="ledger.gnq">{{ ledger.gnq }}</a-tag>
            </div>
            <div class="ledger-detail__head-actions">
              <a-dropdown v-if="ledger && ledger.id">
                <a-button v-has="'land:ledger:edit'" size="small" icon="swap">
                  状态变更 <a-icon type="down" />
                </a-button>
                <a-menu slot="overlay" @click="handleStatusMenu">
                  <a-menu-item v-for="item in statuses" :key="item" :disabled="item === ledger.status">
                    {{ item }}
                  </a-menu-item>
                </a-menu>
              </a-dropdown>
              <a-button v-has="'land:ledger:edit'" size="small" icon="edit" @click="handleEdit">编辑</a-button>
              <a-button v-has="'land:ledger:archive'" size="small" icon="link" @click="openArchivePicker">关联档案</a-button>
              <a-button size="small" icon="printer" @click="$emit('print', ledger)">打印</a-button>
              <a-button size="small" icon="reload" :loading="loading" @click="reload">刷新</a-button>
            </div>
          </div>
        </header>

        <!-- ============ 一致性提示（状态与资料矛盾 / 宗地待核对） ============ -->
        <a-alert
          v-if="warningText"
          class="ledger-detail__alert"
          type="warning"
          showIcon
          :message="warningText" />

        <!-- ============ 一、基本信息 ============ -->
        <section class="ledger-detail__block">
          <div class="ledger-detail__block-title">
            基本信息
            <span class="ledger-detail__block-extra">
              <a v-if="ledger.facilityId" @click="handleFilterBy({ facilityId: ledger.facilityId })">按该配套项目查台账</a>
              <a-divider v-if="ledger.facilityId && ledger.crzdbh" type="vertical" />
              <a v-if="ledger.crzdbh" @click="handleFilterBy({ crzdbh: ledger.crzdbh })">按该宗地查台账</a>
            </span>
          </div>
          <!--
            ★ a-descriptions 用数字 column，且所有 item 都不写 :span。
              原因见《档案管理-实现说明》7.6：column 写成响应式对象时首次渲染会按 3 列切，
              而末项写 :span 对渲染毫无影响却会让记账溢出并抛出 antd 告警。
          -->
          <a-descriptions :column="2" bordered size="small" class="ledger-detail__desc">
            <a-descriptions-item label="台账编号">{{ ledger.ledgerNo || '—' }}</a-descriptions-item>
            <a-descriptions-item label="状态">{{ ledger.status || '未验收' }}</a-descriptions-item>
            <a-descriptions-item label="道路名称">{{ ledger.roadName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="配套设施类别">{{ ledger.ptsslb || '—' }}</a-descriptions-item>
            <a-descriptions-item label="行政区划">{{ ledger.xzqh || '—' }}</a-descriptions-item>
            <a-descriptions-item label="功能区">{{ ledger.gnq || '—' }}</a-descriptions-item>
            <a-descriptions-item label="出让宗地编号">{{ ledger.crzdbh || '—' }}</a-descriptions-item>
            <a-descriptions-item label="地块名称">{{ ledger.dkmc || '—' }}</a-descriptions-item>
            <a-descriptions-item label="道路等级">{{ ledger.dldj || '—' }}</a-descriptions-item>
            <a-descriptions-item label="关联配套项目">{{ ledger.ptxmmc || '—' }}</a-descriptions-item>
            <a-descriptions-item label="建设单位">{{ ledger.jsdw || '—' }}</a-descriptions-item>
            <a-descriptions-item label="施工单位">{{ ledger.sgdw || '—' }}</a-descriptions-item>
            <a-descriptions-item label="监理单位">{{ ledger.jldw || '—' }}</a-descriptions-item>
            <a-descriptions-item label="验收类型">{{ ledger.acceptanceType || '—' }}</a-descriptions-item>
            <a-descriptions-item label="验收单编号">{{ ledger.acceptanceNo || '—' }}</a-descriptions-item>
            <a-descriptions-item label="验收日期">{{ ledger.acceptanceDate || '—' }}</a-descriptions-item>
            <a-descriptions-item label="验收组织单位">{{ ledger.acceptanceOrg || '—' }}</a-descriptions-item>
            <a-descriptions-item label="验收结果">{{ ledger.acceptanceResult || '—' }}</a-descriptions-item>
            <a-descriptions-item label="实际竣工日期">{{ ledger.completeDate || '—' }}</a-descriptions-item>
            <a-descriptions-item label="移交单位">{{ ledger.handoverUnit || '—' }}</a-descriptions-item>
            <a-descriptions-item label="接收管养单位">{{ ledger.receiveUnit || '—' }}</a-descriptions-item>
            <a-descriptions-item label="移交日期">{{ ledger.handoverDate || '—' }}</a-descriptions-item>
            <a-descriptions-item label="备注">{{ ledger.remark || '—' }}</a-descriptions-item>
            <a-descriptions-item label="迁移来源配套项目ID">{{ ledger.sourceFacilityId || '（人工新增）' }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <!-- ============ 二、★13 类资料 ============ -->
        <section class="ledger-detail__block">
          <div class="ledger-detail__block-title">
            验收及移交资料
            <span class="ledger-detail__block-extra">{{ materialText }}</span>
          </div>
          <div class="ledger-detail__materials">
            <div
              v-for="item in materials"
              :key="item.key"
              class="ledger-detail__material"
              :class="{ 'is-missing': Number(ledger[item.key]) !== 1 }"
              :title="item.source">
              <a-icon :type="Number(ledger[item.key]) === 1 ? 'check-circle' : 'close-circle'" />
              <span class="ledger-detail__material-seq">{{ padSeq(item.seq) }}</span>
              <span class="ledger-detail__material-label">{{ item.label }}</span>
            </div>
          </div>
          <div v-if="missingLabels.length" class="ledger-detail__missing">
            待补资料（{{ missingLabels.length }}）：{{ missingLabels.join('、') }}
          </div>
        </section>

        <!-- ============ 三、关联档案 ============ -->
        <section class="ledger-detail__block">
          <div class="ledger-detail__block-title">
            关联档案
            <span class="ledger-detail__block-extra">
              资料原件统一存放于档案管理模块，台账只记录关联
            </span>
          </div>
          <a-table
            rowKey="id"
            size="small"
            :columns="archiveColumns"
            :dataSource="ledger.relatedArchives || []"
            :pagination="false"
            :locale="{ emptyText: '该道路下暂无档案；请先到「档案维护」上传档案，再回来点「关联档案」' }">
            <template slot="archiveNo" slot-scope="text, record">
              <a @click="handleOpenArchive(record)">{{ text }}</a>
            </template>
            <template slot="archiveStatus" slot-scope="text">
              <a-tag :color="archiveStatusColor(text)">{{ text }}</a-tag>
            </template>
            <template slot="isPrimary" slot-scope="text, record">
              <a-tag v-if="record.id === ledger.archiveId" color="green">已关联</a-tag>
              <span v-else class="ledger-detail__muted">—</span>
            </template>
            <template slot="archiveAction" slot-scope="text, record">
              <a v-has="'land:ledger:archive'" @click="handleLink(record)">设为台账关联档案</a>
            </template>
          </a-table>
        </section>
      </div>
    </a-spin>

    <!-- ============ 关联档案选择弹窗 ============ -->
    <a-modal
      title="选择要关联的档案"
      :width="860"
      :visible="pickerVisible"
      :footer="null"
      @cancel="pickerVisible = false">
      <div class="ledger-detail__picker-search">
        <a-input-search
          v-model="pickerKeyword"
          placeholder="按档案号 / 档案名称 / 配套项目名称搜索"
          enterButton="搜索"
          allowClear
          @search="loadArchiveOptions" />
        <a-checkbox v-model="pickerOnlySameProject" @change="loadArchiveOptions">
          只看同一配套项目/宗地下的档案
        </a-checkbox>
      </div>
      <a-table
        rowKey="id"
        size="small"
        :columns="archiveColumns"
        :dataSource="pickerOptions"
        :loading="pickerLoading"
        :pagination="false"
        :locale="{ emptyText: '没有找到可关联的档案' }">
        <template slot="archiveStatus" slot-scope="text">
          <a-tag :color="archiveStatusColor(text)">{{ text }}</a-tag>
        </template>
        <template slot="isPrimary" slot-scope="text, record">
          <a-tag v-if="record.id === ledger.archiveId" color="green">已关联</a-tag>
        </template>
        <template slot="archiveAction" slot-scope="text, record">
          <a @click="handleLink(record)">关联</a>
        </template>
      </a-table>
    </a-modal>
  </a-modal>
</template>

<script>
  import {
    LEDGER_MATERIALS_FALLBACK,
    LEDGER_STATUS,
    MATERIAL_TOTAL,
    acceptanceResultColor,
    archiveQueryUrl,
    changeLedgerStatus,
    linkLedgerArchive,
    missingMaterialLabels,
    pickArchives,
    queryLedgerById,
    statusColor,
    statusMaterialWarning,
    unlinkLedgerArchive
  } from '@/api/land/ledger'

  /**
   * 道路设施验收及移交资料台账 - 详情弹窗
   *
   * 三块：基本信息（a-descriptions 两列）/ 13 类资料矩阵 / 关联档案。
   *
   * ★ 资料原件口径（需求方确认）：台账不存文件，只记 archive_id 指针；
   *   「关联档案」页签列出该道路下的全部档案（配套项目ID 优先、宗地编号兜底），
   *   可以把其中任意一个设为台账的关联档案（已关联的显示绿标签）。
   */
  export default {
    name: 'LedgerDetailModal',
    data () {
      return {
        visible: false,
        loading: false,
        ledger: null,
        materials: LEDGER_MATERIALS_FALLBACK,
        statuses: LEDGER_STATUS,
        pickerVisible: false,
        pickerLoading: false,
        pickerKeyword: '',
        pickerOnlySameProject: true,
        pickerOptions: [],
        archiveColumns: [
          { title: '档案号', dataIndex: 'archiveNo', width: 130, scopedSlots: { customRender: 'archiveNo' } },
          { title: '档案名称', dataIndex: 'archiveName', width: 240, ellipsis: true, customRender: text => text || '—' },
          { title: '档案年度', dataIndex: 'archiveYear', width: 90, customRender: text => text || '—' },
          { title: '归档日期', dataIndex: 'archiveDate', width: 105, customRender: text => text || '—' },
          { title: '文件数', dataIndex: 'fileCount', width: 80, customRender: text => text || 0 },
          { title: '状态', dataIndex: 'status', width: 90, scopedSlots: { customRender: 'archiveStatus' } },
          { title: '台账关联', width: 100, scopedSlots: { customRender: 'isPrimary' } },
          { title: '操作', width: 150, scopedSlots: { customRender: 'archiveAction' } }
        ]
      }
    },
    computed: {
      materialText () {
        const count = this.ledger && this.ledger.materialCount !== null && this.ledger.materialCount !== undefined
          ? Number(this.ledger.materialCount) : 0
        return `资料 ${count}/${MATERIAL_TOTAL}`
      },
      materialTagColor () {
        const count = this.ledger && this.ledger.materialCount ? Number(this.ledger.materialCount) : 0
        if (count >= MATERIAL_TOTAL) {
          return 'green'
        }
        return count === 0 ? 'red' : 'blue'
      },
      missingLabels () {
        return missingMaterialLabels(this.ledger)
      },
      /**
       * 一致性提示：① 状态与资料矛盾（如「已移交」却没有移交文件）；
       * ② 迁移时出让宗地在 t_land 里没匹配上（remark 以「迁移提示」开头）。
       * 两者都是「需要人来核对」的事，所以并成一条黄色提示。
       */
      warningText () {
        if (!this.ledger) {
          return ''
        }
        const parts = []
        const conflict = statusMaterialWarning(this.ledger, this.materials)
        if (conflict) {
          parts.push(conflict)
        }
        if (this.ledger.remark && this.ledger.remark.indexOf('迁移提示') === 0) {
          parts.push(this.ledger.remark)
        }
        return parts.join('；')
      }
    },
    methods: {
      statusColor,
      acceptanceResultColor,
      /** 由父组件调用：打开某条台账的详情 */
      open (record) {
        if (!record || !record.id) {
          return
        }
        this.visible = true
        this.ledger = Object.assign({}, record, { relatedArchives: [] })
        this.loadDetail(record.id)
      },
      reload () {
        if (this.ledger && this.ledger.id) {
          this.loadDetail(this.ledger.id)
        }
      },
      loadDetail (id) {
        this.loading = true
        queryLedgerById(id).then(res => {
          if (res.success && res.result) {
            this.ledger = res.result
          } else {
            this.$message.warning(res.message || '未找到对应的台账记录')
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '加载台账详情失败')
        }).finally(() => {
          this.loading = false
        })
      },
      handleClose () {
        this.visible = false
        this.pickerVisible = false
        this.ledger = null
      },
      handleEdit () {
        this.$emit('edit', this.ledger)
      },
      /** 状态变更（下拉菜单，改完刷新详情） */
      handleStatusMenu (args) {
        const status = args && args.key ? args.key : args
        if (!status || !this.ledger || !this.ledger.id || status === this.ledger.status) {
          return
        }
        changeLedgerStatus(this.ledger.id, status).then(res => {
          if (res.success) {
            this.$message.success(`状态已变更为「${status}」`)
            this.loadDetail(this.ledger.id)
            this.$emit('status-change', this.ledger, status)
          } else {
            this.$message.warning(res.message)
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '状态变更失败')
        })
      },
      /**
       * 按条件回台账列表（台账 → 台账 的联动：同一配套项目/同一宗地下的其它道路）。
       *
       * ★ 用事件交给父页面处理，而不是自己 `$router.push` 到当前路由：
       *   跳到「同一个路由，只换 query」时 Vue Router 不会重建组件，
       *   台账页的 created 不会重跑，条件就丢了（常见坑）。
       *   交给父页面直接改查询条件并刷新，行为确定。
       */
      handleFilterBy (params) {
        this.visible = false
        this.$emit('filter', params)
      },
      /** 打开档案查询页并带上档案号（台账 → 档案 的下钻，新窗口以保留台账上下文） */
      handleOpenArchive (record) {
        window.open(archiveQueryUrl(record), '_blank')
      },
      archiveStatusColor (status) {
        if (status === '已归档') {
          return 'green'
        }
        if (status === '审核中') {
          return 'orange'
        }
        if (status === '归档中') {
          return 'blue'
        }
        return undefined
      },
      // ------------------------------------------------------------------
      // 关联档案
      // ------------------------------------------------------------------
      openArchivePicker () {
        this.pickerVisible = true
        this.pickerKeyword = ''
        this.loadArchiveOptions()
      },
      loadArchiveOptions () {
        const params = { limit: 50 }
        if (!this.pickerOnlySameProject) {
          if (this.pickerKeyword) {
            params.keyword = this.pickerKeyword
          }
        } else {
          if (this.ledger && this.ledger.facilityId) {
            params.facilityId = this.ledger.facilityId
          } else if (this.ledger && this.ledger.crzdbh) {
            params.crzdbh = this.ledger.crzdbh
          }
          if (this.pickerKeyword) {
            params.keyword = this.pickerKeyword
          }
        }
        this.pickerLoading = true
        pickArchives(params).then(res => {
          this.pickerOptions = res && res.success ? (res.result || []) : []
        }).catch(() => {
          this.pickerOptions = []
        }).finally(() => {
          this.pickerLoading = false
        })
      },
      handleLink (record) {
        if (!this.ledger || !this.ledger.id) {
          return
        }
        linkLedgerArchive(this.ledger.id, record.id).then(res => {
          if (res.success) {
            this.$message.success('关联成功！')
            this.pickerVisible = false
            this.loadDetail(this.ledger.id)
          } else {
            this.$message.warning(res.message)
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '关联失败')
        })
      },
      handleUnlink () {
        if (!this.ledger || !this.ledger.id) {
          return
        }
        unlinkLedgerArchive(this.ledger.id).then(res => {
          if (res.success) {
            this.$message.success('已取消关联！')
            this.loadDetail(this.ledger.id)
          } else {
            this.$message.warning(res.message)
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '取消关联失败')
        })
      },
      padSeq (seq) {
        return seq < 10 ? `0${seq}` : `${seq}`
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;

  .ledger-detail {
    &__head {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    &__head-main {
      /* 给 antd 的 × 关闭按钮（绝对定位 56×56 点击区）留出净空 */
      padding-right: 48px;
    }

    &__no {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 14px;
      letter-spacing: 1px;
      color: #2e7cf6;
    }

    &__name {
      margin: 4px 0 0;
      font-size: 18px;
      font-weight: 600;
      color: #0f172a;
    }

    &__head-bar {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
    }

    &__tags {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 6px;

      /deep/ .ant-tag {
        margin: 0;
      }
    }

    &__head-actions {
      /* ★ 用 margin-left:auto 而不是 space-between：换行后依然右对齐（见 docs 7.4） */
      margin-left: auto;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;

      /deep/ .ant-btn {
        flex: none;
        white-space: nowrap;
      }
    }

    &__block {
      margin-top: 16px;
    }

    &__block-title {
      display: flex;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 10px;
      margin-bottom: 10px;
      padding-left: 8px;
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
      border-left: 3px solid #2e7cf6;
    }

    &__block-extra {
      font-size: 12px;
      font-weight: 400;
      color: #94a3b8;
    }

    /* 信息表：固定 table-layout，避免「迁移来源配套项目ID」这类长标签被压成两行（见 docs 7.4） */
    &__desc {
      /deep/ .ant-descriptions-item-label {
        width: 150px;
      }

      /deep/ .ant-descriptions-view > table {
        table-layout: fixed;
      }

      /deep/ .ant-descriptions-item-content {
        word-break: break-word;
      }
    }

    /* 13 类资料矩阵 */
    &__materials {
      display: flex;
      flex-wrap: wrap;
      border-top: 1px solid @border-color;
      border-left: 1px solid @border-color;
    }

    &__material {
      display: flex;
      align-items: center;
      width: 25%;
      padding: 8px 10px;
      box-sizing: border-box;
      font-size: 13px;
      color: #0f172a;
      border-right: 1px solid @border-color;
      border-bottom: 1px solid @border-color;
      background: #f6fef9;

      &.is-missing {
        color: #94a3b8;
        background: #fff;
      }

      /deep/ .anticon {
        margin-right: 6px;
        color: #10b981;
      }

      &.is-missing /deep/ .anticon {
        color: #d9d9d9;
      }
    }

    &__material-seq {
      margin-right: 6px;
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 12px;
      color: #94a3b8;
    }

    &__material-label {
      flex: 1 1 auto;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__missing {
      margin-top: 8px;
      font-size: 12px;
      line-height: 18px;
      color: #d48806;
    }

    &__muted {
      color: #94a3b8;
    }

    &__alert {
      margin-top: 12px;
    }

    &__picker-search {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 12px;

      /deep/ .ant-input-search {
        flex: 1 1 auto;
      }
    }
  }

  @media (max-width: 992px) {
    .ledger-detail__material {
      width: 50%;
    }
  }
</style>
