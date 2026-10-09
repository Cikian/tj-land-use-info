<template>
  <a-modal
    :title="null"
    :width="1000"
    :visible="visible"
    :footer="null"
    :maskClosable="true"
    wrapClassName="handover-detail-modal"
    @cancel="handleClose">
    <a-spin :spinning="loading">
      <div v-if="handover" class="handover-detail">
        <!-- ============ 页头 ============ -->
        <header class="handover-detail__head">
          <div class="handover-detail__head-main">
            <div class="handover-detail__no">{{ handover.handoverNo }}</div>
            <h3 class="handover-detail__name">{{ handover.roadName }}</h3>
          </div>
          <div class="handover-detail__head-bar">
            <div class="handover-detail__tags">
              <a-tag :color="statusColor(handover.status)">{{ handover.status || '待移交' }}</a-tag>
              <a-tag v-if="handover.handoverType" color="blue">{{ handover.handoverType }}</a-tag>
              <a-tag v-if="handover.archiveId" color="green">已关联档案</a-tag>
              <a-tag v-if="handover.xzqh">{{ handover.xzqh }}</a-tag>
              <a-tag v-else-if="handover.gnq">{{ handover.gnq }}</a-tag>
            </div>
            <div class="handover-detail__head-actions">
              <a-dropdown v-if="handover.id">
                <a-button v-has="'land:handover:edit'" size="small" icon="swap">
                  状态变更 <a-icon type="down" />
                </a-button>
                <a-menu slot="overlay" @click="handleStatusMenu">
                  <a-menu-item v-for="item in statuses" :key="item" :disabled="item === handover.status">
                    {{ item }}
                  </a-menu-item>
                </a-menu>
              </a-dropdown>
              <a-button v-has="'land:handover:edit'" size="small" icon="edit" @click="$emit('edit', handover)">编辑</a-button>
              <a-button v-has="'land:handover:archive'" size="small" icon="link" @click="openArchivePicker">关联档案</a-button>
              <a-button size="small" icon="printer" @click="$emit('print', handover)">打印</a-button>
              <a-button size="small" icon="reload" :loading="loading" @click="reload">刷新</a-button>
            </div>
          </div>
        </header>

        <!-- ============ 业务提示 ============ -->
        <a-alert
          v-for="(item, index) in warnings"
          :key="index"
          class="handover-detail__alert"
          type="warning"
          showIcon
          :message="item" />

        <!-- ============ 基本信息 ============ -->
        <!-- ★ a-descriptions 用数字 column，且所有 item 都不写 :span（见《档案管理-实现说明》7.6） -->
        <section class="handover-detail__block">
          <div class="handover-detail__block-title">
            基本信息
            <span class="handover-detail__block-extra">
              <a v-if="handover.facilityId" @click="handleFilterBy({ facilityId: handover.facilityId })">按该配套项目查</a>
              <a-divider v-if="handover.facilityId && handover.crzdbh" type="vertical" />
              <a v-if="handover.crzdbh" @click="handleFilterBy({ crzdbh: handover.crzdbh })">按该宗地查</a>
            </span>
          </div>
          <a-descriptions :column="2" bordered size="small" class="handover-detail__desc">
            <a-descriptions-item label="移交编号">{{ handover.handoverNo || '—' }}</a-descriptions-item>
            <a-descriptions-item label="状态">{{ handover.status || '待移交' }}</a-descriptions-item>
            <a-descriptions-item label="道路名称">{{ handover.roadName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="道路编号">{{ handover.roadCode || '—' }}</a-descriptions-item>
            <a-descriptions-item label="道路等级">{{ handover.dldj || '—' }}</a-descriptions-item>
            <a-descriptions-item label="配套设施类别">{{ handover.ptsslb || '—' }}</a-descriptions-item>
            <a-descriptions-item label="起点">{{ handover.startPoint || '—' }}</a-descriptions-item>
            <a-descriptions-item label="终点">{{ handover.endPoint || '—' }}</a-descriptions-item>
            <a-descriptions-item label="长度(米)">{{ handover.lengthM === null || handover.lengthM === undefined ? '—' : handover.lengthM }}</a-descriptions-item>
            <a-descriptions-item label="红线宽度(米)">{{ handover.redLineWidth === null || handover.redLineWidth === undefined ? '—' : handover.redLineWidth }}</a-descriptions-item>
            <a-descriptions-item label="行政区划">{{ handover.xzqh || '—' }}</a-descriptions-item>
            <a-descriptions-item label="功能区">{{ handover.gnq || '—' }}</a-descriptions-item>
            <a-descriptions-item label="出让宗地编号">{{ handover.crzdbh || '—' }}</a-descriptions-item>
            <a-descriptions-item label="地块名称">{{ handover.dkmc || '—' }}</a-descriptions-item>
            <a-descriptions-item label="移交类型">{{ handover.handoverType || '—' }}</a-descriptions-item>
            <a-descriptions-item label="协议编号">{{ handover.agreementNo || '待补录' }}</a-descriptions-item>
            <a-descriptions-item label="协议名称">{{ handover.agreementName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="协议签订日期">{{ handover.agreementDate || '待补录' }}</a-descriptions-item>
            <a-descriptions-item label="建设单位">{{ handover.buildUnit || '—' }}</a-descriptions-item>
            <a-descriptions-item label="接收管养单位">{{ handover.receiveUnit || '—' }}</a-descriptions-item>
            <a-descriptions-item label="实际移交日期">{{ handover.handoverDate || '待补录' }}</a-descriptions-item>
            <a-descriptions-item label="养护起始日期">{{ handover.maintenanceStart || '—' }}</a-descriptions-item>
            <a-descriptions-item label="养护截止日期">{{ handover.maintenanceEnd || '—' }}</a-descriptions-item>
            <a-descriptions-item label="备注">{{ handover.remark || '—' }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <!-- ============ 关联档案 ============ -->
        <section class="handover-detail__block">
          <div class="handover-detail__block-title">
            关联档案
            <span class="handover-detail__block-extra">
              协议扫描件与移交单统一存放于档案管理模块，本事项只记录关联
            </span>
          </div>
          <a-table
            rowKey="id"
            size="small"
            :columns="archiveColumns"
            :dataSource="handover.relatedArchives || []"
            :pagination="false"
            :locale="{ emptyText: '该项目下暂无档案；请先到「档案维护」上传，再回来点「关联档案」' }">
            <template slot="archiveNo" slot-scope="text, record">
              <a @click="handleOpenArchive(record)">{{ text }}</a>
            </template>
            <template slot="archiveStatus" slot-scope="text">
              <a-tag :color="archiveStatusColor(text)">{{ text }}</a-tag>
            </template>
            <template slot="isPrimary" slot-scope="text, record">
              <a-tag v-if="record.id === handover.archiveId" color="green">已关联</a-tag>
              <span v-else class="handover-detail__muted">—</span>
            </template>
            <template slot="archiveAction" slot-scope="text, record">
              <a v-has="'land:handover:archive'" @click="handleLink(record)">设为本事项关联档案</a>
            </template>
          </a-table>
        </section>
      </div>
    </a-spin>

    <!-- ============ 关联档案选择弹窗 ============ -->
    <a-modal
      title="选择要关联的档案"
      :width="840"
      :visible="pickerVisible"
      :footer="null"
      @cancel="pickerVisible = false">
      <div class="handover-detail__picker-search">
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
          <a-tag v-if="record.id === handover.archiveId" color="green">已关联</a-tag>
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
    HANDOVER_STATUS,
    changeHandoverStatus,
    linkHandoverArchive,
    maintenanceWarning,
    missingAgreement,
    pickHandoverArchives,
    queryHandoverById,
    statusColor
  } from '@/api/land/handover'

  /**
   * 道路交付及养护协议移交事项 - 详情弹窗
   *
   * 三块：业务提示（协议待补录 / 养护期到期 / 宗地待核对）、基本信息、关联档案。
   *
   * ★ 附件口径与台账一致：协议扫描件、移交单挂档案管理模块，本事项只记 archive_id 指针；
   *   「关联档案」页签列出同一配套项目/宗地下的全部档案，可把其中之一设为关联档案。
   */
  export default {
    name: 'HandoverDetailModal',
    data () {
      return {
        visible: false,
        loading: false,
        handover: null,
        statuses: HANDOVER_STATUS,
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
          { title: '本事项关联', width: 110, scopedSlots: { customRender: 'isPrimary' } },
          { title: '操作', width: 170, scopedSlots: { customRender: 'archiveAction' } }
        ]
      }
    },
    computed: {
      /** 业务提示（协议待补录 / 养护期到期 / 宗地待核对），可多条 */
      warnings () {
        const list = []
        if (!this.handover) {
          return list
        }
        if (missingAgreement(this.handover)) {
          list.push('协议信息待补录：协议编号与协议签订日期均为空（迁移自旧库「是否移交=是」，旧库没有协议字段）')
        }
        const maintenance = maintenanceWarning(this.handover)
        if (maintenance) {
          list.push(maintenance)
        }
        if (this.handover.remark && this.handover.remark.indexOf('无匹配记录') >= 0) {
          list.push('出让宗地待核对：该宗地编号在 t_land 中无匹配记录')
        }
        return list
      }
    },
    methods: {
      statusColor,
      open (record) {
        if (!record || !record.id) {
          return
        }
        this.visible = true
        this.handover = Object.assign({}, record, { relatedArchives: [] })
        this.loadDetail(record.id)
      },
      reload () {
        if (this.handover && this.handover.id) {
          this.loadDetail(this.handover.id)
        }
      },
      loadDetail (id) {
        this.loading = true
        queryHandoverById(id).then(res => {
          if (res.success && res.result) {
            this.handover = res.result
          } else {
            this.$message.warning(res.message || '未找到对应的移交事项')
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '加载详情失败')
        }).finally(() => {
          this.loading = false
        })
      },
      handleClose () {
        this.visible = false
        this.pickerVisible = false
        this.handover = null
      },
      handleStatusMenu (args) {
        const status = args && args.key ? args.key : args
        if (!status || !this.handover || !this.handover.id || status === this.handover.status) {
          return
        }
        changeHandoverStatus(this.handover.id, status).then(res => {
          if (res.success) {
            this.$message.success(`状态已变更为「${status}」`)
            this.loadDetail(this.handover.id)
            this.$emit('status-change', this.handover, status)
          } else {
            this.$message.warning(res.message)
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '状态变更失败')
        })
      },
      /** 按条件回列表（用事件交给父页面，避免跳到同一路由时组件不重建） */
      handleFilterBy (params) {
        this.visible = false
        this.$emit('filter', params)
      },
      /** 打开档案查询页（带档案号下钻） */
      handleOpenArchive (record) {
        const query = []
        if (record && record.archiveNo) {
          query.push(`archiveNo=${encodeURIComponent(record.archiveNo)}`)
        } else if (record && record.ptxmmc) {
          query.push(`ptxmmc=${encodeURIComponent(record.ptxmmc)}`)
        }
        window.open(`/land/archive/query${query.length ? '?' + query.join('&') : ''}`, '_blank')
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
      openArchivePicker () {
        this.pickerVisible = true
        this.pickerKeyword = ''
        this.loadArchiveOptions()
      },
      loadArchiveOptions () {
        const params = { limit: 50 }
        if (this.pickerOnlySameProject) {
          if (this.handover && this.handover.facilityId) {
            params.facilityId = this.handover.facilityId
          } else if (this.handover && this.handover.crzdbh) {
            params.crzdbh = this.handover.crzdbh
          }
        }
        if (this.pickerKeyword) {
          params.keyword = this.pickerKeyword
        }
        this.pickerLoading = true
        pickHandoverArchives(params).then(res => {
          this.pickerOptions = res && res.success ? (res.result || []) : []
        }).catch(() => {
          this.pickerOptions = []
        }).finally(() => {
          this.pickerLoading = false
        })
      },
      handleLink (record) {
        if (!this.handover || !this.handover.id) {
          return
        }
        linkHandoverArchive(this.handover.id, record.id).then(res => {
          if (res.success) {
            this.$message.success('关联成功！')
            this.pickerVisible = false
            this.loadDetail(this.handover.id)
          } else {
            this.$message.warning(res.message)
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '关联失败')
        })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;

  .handover-detail {
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

    &__alert {
      margin-top: 12px;
    }

    &__desc {
      /deep/ .ant-descriptions-item-label {
        width: 140px;
      }

      /deep/ .ant-descriptions-view > table {
        table-layout: fixed;
      }

      /deep/ .ant-descriptions-item-content {
        word-break: break-word;
      }
    }

    &__muted {
      color: #94a3b8;
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
</style>
