<template>
  <a-modal
    :title="null"
    :width="1080"
    :visible="visible"
    :footer="null"
    :maskClosable="true"
    wrapClassName="completion-detail-modal"
    @cancel="handleClose">
    <a-spin :spinning="loading">
      <div v-if="archive" class="completion-detail">
        <!-- ============ 页头 ============ -->
        <header class="completion-detail__head">
          <div class="completion-detail__head-main">
            <div class="completion-detail__no">{{ archive.archiveNo }}</div>
            <h3 class="completion-detail__name">{{ archive.projectName }}</h3>
          </div>
          <div class="completion-detail__head-bar">
            <div class="completion-detail__tags">
              <a-tag :color="digitizeStatusColor(archive.digitizeStatus)">
                {{ archive.digitizeStatus || '未数字化' }}
              </a-tag>
              <a-tag v-if="archive.projectType">{{ archive.projectType }}</a-tag>
              <a-tag v-if="archive.retention">保管：{{ archive.retention }}</a-tag>
              <a-tag v-if="archive.xzqh">{{ archive.xzqh }}</a-tag>
              <a-tag :color="archive.archiveId ? 'green' : undefined">
                {{ archive.archiveId ? '已挂扫描件' : '未挂扫描件' }}
              </a-tag>
            </div>
            <div class="completion-detail__head-actions">
              <a-dropdown v-has="'land:completion:edit'">
                <a-button size="small" icon="swap">
                  变更数字化状态 <a-icon type="down" />
                </a-button>
                <a-menu slot="overlay" @click="handleStatusClick">
                  <a-menu-item v-for="item in statuses" :key="item" :disabled="item === archive.digitizeStatus">
                    {{ item }}
                  </a-menu-item>
                </a-menu>
              </a-dropdown>
              <a-button v-has="'land:completion:edit'" size="small" icon="link" @click="openArchivePicker">关联扫描件</a-button>
              <a-button v-has="'land:completion:edit'" size="small" icon="edit" @click="handleEdit">编辑</a-button>
              <a-button size="small" icon="reload" :loading="loading" @click="reload">刷新</a-button>
            </div>
          </div>
        </header>

        <!-- 数字化进度概览 -->
        <div class="completion-detail__progress">
          <a-progress
            :percent="archive.digitizePercent === null || archive.digitizePercent === undefined
              ? digitizePercent(archive.digitizeStatus) : archive.digitizePercent"
            :strokeColor="dotColor(archive.digitizeStatus)" />
          <span class="completion-detail__progress-text">
            总页数 {{ archive.pageCount === null || archive.pageCount === undefined ? '—' : formatCount(archive.pageCount) }}
            ｜ 文件数 {{ archive.fileCount === null || archive.fileCount === undefined ? '—' : formatCount(archive.fileCount) }}
            ｜ 扫描分辨率 {{ archive.scanDpi ? archive.scanDpi + ' DPI' : '—' }}
          </span>
        </div>

        <!-- ============ 一、历史项目与参建单位 ============ -->
        <section class="completion-detail__block">
          <div class="completion-detail__block-title">历史项目与参建单位</div>
          <!--
            ★ a-descriptions 用数字 column，且所有 item 都不写 :span。
              原因见《档案管理-实现说明》7.6：column 写成响应式对象时首次渲染会按 3 列切，
              而末项写 :span 对渲染毫无影响却会让记账溢出并抛出 antd 告警。
          -->
          <a-descriptions :column="2" bordered size="small" class="completion-detail__desc">
            <a-descriptions-item label="档案编号">{{ archive.archiveNo || '—' }}</a-descriptions-item>
            <a-descriptions-item label="项目编号">{{ archive.projectCode || '—' }}</a-descriptions-item>
            <a-descriptions-item label="历史项目名称">{{ archive.projectName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="行政区划">{{ archive.xzqh || '—' }}</a-descriptions-item>
            <a-descriptions-item label="项目类型">{{ archive.projectType || '—' }}</a-descriptions-item>
            <a-descriptions-item label="保管期限">{{ archive.retention || '—' }}</a-descriptions-item>
            <a-descriptions-item label="建设单位">{{ archive.buildUnit || '—' }}</a-descriptions-item>
            <a-descriptions-item label="施工单位">{{ archive.constructUnit || '—' }}</a-descriptions-item>
            <a-descriptions-item label="设计单位">{{ archive.designUnit || '—' }}</a-descriptions-item>
            <a-descriptions-item label="监理单位">{{ archive.superviseUnit || '—' }}</a-descriptions-item>
            <a-descriptions-item label="开工日期">{{ archive.startDate || '—' }}</a-descriptions-item>
            <a-descriptions-item label="竣工日期">{{ archive.completeDate || '—' }}</a-descriptions-item>
            <a-descriptions-item label="验收日期">{{ archive.acceptanceDate || '—' }}</a-descriptions-item>
            <a-descriptions-item label="投资额（万元）">
              {{ archive.investAmount === null || archive.investAmount === undefined ? '—' : formatAmount(archive.investAmount) }}
            </a-descriptions-item>
            <a-descriptions-item label="关联配套项目">{{ archive.ptxmmc || '—' }}</a-descriptions-item>
            <a-descriptions-item label="出让宗地编号">{{ archive.crzdbh || '—' }}</a-descriptions-item>
            <a-descriptions-item label="地块名称">{{ archive.dkmc || '—' }}</a-descriptions-item>
            <a-descriptions-item label="配套设施类别">{{ archive.ptsslb || '—' }}</a-descriptions-item>
            <a-descriptions-item label="备注">{{ archive.remark || '—' }}</a-descriptions-item>
            <a-descriptions-item label="最近更新">{{ archive.updateTime || archive.createTime || '—' }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <!-- ============ 二、★数字化信息 ============ -->
        <section class="completion-detail__block">
          <div class="completion-detail__block-title">
            数字化信息
            <span class="completion-detail__block-extra">数字化状态 + 页数 + 扫描分辨率是本模块的追踪重点</span>
          </div>
          <a-descriptions :column="2" bordered size="small" class="completion-detail__desc">
            <a-descriptions-item label="数字化状态">{{ archive.digitizeStatus || '未数字化' }}</a-descriptions-item>
            <a-descriptions-item label="数字化完成日期">{{ archive.digitizeDate || '—' }}</a-descriptions-item>
            <a-descriptions-item label="数字化加工单位">{{ archive.digitizeOrg || '—' }}</a-descriptions-item>
            <a-descriptions-item label="扫描分辨率">{{ archive.scanDpi ? archive.scanDpi + ' DPI' : '—' }}</a-descriptions-item>
            <a-descriptions-item label="总页数">
              {{ archive.pageCount === null || archive.pageCount === undefined ? '—' : formatCount(archive.pageCount) }}
            </a-descriptions-item>
            <a-descriptions-item label="文件数">
              {{ archive.fileCount === null || archive.fileCount === undefined ? '—' : formatCount(archive.fileCount) }}
            </a-descriptions-item>
            <a-descriptions-item label="关联扫描件数">{{ archive.archiveCount || 0 }}</a-descriptions-item>
            <a-descriptions-item label="创建信息">
              {{ (archive.createBy || '—') + ' / ' + (archive.createTime || '—') }}
            </a-descriptions-item>
          </a-descriptions>

          <a-alert
            v-if="isContradictory"
            class="completion-detail__alert"
            type="warning"
            showIcon
            message="状态为「已数字化」，但还没有关联任何扫描件档案"
            description="请到「档案维护」确认扫描件是否已上传；若已上传，点上方「关联扫描件」挂上。" />
        </section>

        <!-- ============ 三、关联扫描件 ============ -->
        <section class="completion-detail__block">
          <div class="completion-detail__block-title">
            关联扫描件
            <span class="completion-detail__block-extra">
              扫描件本体存放于档案管理模块，本档案只记录关联
            </span>
          </div>
          <a-table
            rowKey="id"
            size="small"
            :columns="archiveColumns"
            :dataSource="archive.relatedArchives || []"
            :pagination="false"
            :locale="{ emptyText: '该历史项目下暂无档案；请先到「档案维护」上传扫描件，再回来点「关联扫描件」' }">
            <template slot="archiveStatus" slot-scope="text">
              <a-tag :color="archiveStatusColor(text)">{{ text }}</a-tag>
            </template>
            <template slot="isPrimary" slot-scope="text, record">
              <a-tag v-if="record.id === archive.archiveId" color="green">已关联</a-tag>
              <span v-else class="completion-detail__muted">—</span>
            </template>
            <template slot="archiveAction" slot-scope="text, record">
              <a v-has="'land:completion:edit'" @click="handleLink(record)">设为扫描件</a>
              <template v-if="record.id === archive.archiveId">
                <a-divider type="vertical" />
                <a-popconfirm
                  title="取消关联只是解除指针，不会删除档案本身，确定吗？"
                  okText="确定"
                  cancelText="取消"
                  @confirm="handleUnlink">
                  <a class="completion-detail__danger">取消关联</a>
                </a-popconfirm>
              </template>
            </template>
          </a-table>
        </section>
      </div>
    </a-spin>

    <!-- ============ 挑扫描件弹窗 ============ -->
    <a-modal
      title="选择要关联的扫描件档案"
      :width="860"
      :visible="pickerVisible"
      :footer="null"
      @cancel="pickerVisible = false">
      <div class="completion-detail__picker-search">
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
          <a-tag v-if="record.id === archive.archiveId" color="green">已关联</a-tag>
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
    DIGITIZE_STATUS,
    archiveStatusColor,
    changeDigitizeStatus,
    digitizeDotColor,
    digitizePercent,
    digitizeStatusColor,
    formatAmount,
    formatCount,
    linkCompletionArchive,
    pickArchives,
    queryCompletionById,
    unlinkCompletionArchive
  } from '@/api/land/completion'

  /**
   * 竣工验收项目历史工程资料数字化档案 - 详情弹窗
   *
   * 三块：历史项目与参建单位 / ★数字化信息 / 关联扫描件。
   *
   * 两个业务友好的设计：
   *  1. 数字化进度条放在页头下方，一眼看出这条档案做到哪一步；
   *  2. 「已数字化但未关联扫描件」时给出黄色告警 —— 这是历史档案数字化最典型的假完成，
   *     与本模块列表页的黄色警告图标是同一判断口径。
   *
   * ★ 扫描件口径：本模块不存文件，只记 archive_id 指针；
   *   「关联扫描件」页签列出该历史项目下的全部档案（配套项目ID 优先、宗地编号兜底），
   *   可以把其中任意一个设为扫描件（已关联的显示绿标签，并可取消关联）。
   */
  export default {
    name: 'CompletionDetailModal',
    data () {
      return {
        visible: false,
        loading: false,
        archive: null,
        statuses: DIGITIZE_STATUS,
        pickerVisible: false,
        pickerLoading: false,
        pickerKeyword: '',
        pickerOnlySameProject: true,
        pickerOptions: [],
        archiveColumns: [
          { title: '档案号', dataIndex: 'archiveNo', width: 130, customRender: text => text || '—' },
          { title: '档案名称', dataIndex: 'archiveName', width: 240, ellipsis: true, customRender: text => text || '—' },
          { title: '档案年度', dataIndex: 'archiveYear', width: 90, customRender: text => text || '—' },
          { title: '归档日期', dataIndex: 'archiveDate', width: 105, customRender: text => text || '—' },
          { title: '文件数', dataIndex: 'fileCount', width: 80, customRender: text => text || 0 },
          { title: '状态', dataIndex: 'status', width: 90, scopedSlots: { customRender: 'archiveStatus' } },
          { title: '本档案关联', width: 110, scopedSlots: { customRender: 'isPrimary' } },
          { title: '操作', width: 150, scopedSlots: { customRender: 'archiveAction' } }
        ]
      }
    },
    computed: {
      /** 「已数字化但没挂扫描件」的矛盾记录 */
      isContradictory () {
        return !!this.archive && this.archive.digitizeStatus === '已数字化' && !this.archive.archiveId
      }
    },
    methods: {
      digitizeStatusColor,
      archiveStatusColor,
      digitizePercent,
      formatAmount,
      formatCount,
      dotColor (status) {
        return digitizeDotColor(status)
      },
      /** 由父组件调用：打开某条档案的详情 */
      open (record) {
        if (!record || !record.id) {
          return
        }
        this.visible = true
        this.archive = Object.assign({}, record, { relatedArchives: [] })
        this.loadDetail(record.id)
      },
      reload () {
        if (this.archive && this.archive.id) {
          this.loadDetail(this.archive.id)
        }
      },
      loadDetail (id) {
        this.loading = true
        queryCompletionById(id).then(res => {
          if (res.success && res.result) {
            this.archive = res.result
          } else {
            this.$message.warning(res.message || '未找到对应的历史档案记录')
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '加载档案详情失败')
        }).finally(() => {
          this.loading = false
        })
      },
      handleClose () {
        this.visible = false
        this.pickerVisible = false
        this.archive = null
      },
      handleEdit () {
        this.$emit('edit', this.archive)
      },
      /** 变更数字化状态（下拉菜单点击） */
      handleStatusClick ({ key }) {
        if (!this.archive || !this.archive.id || key === this.archive.digitizeStatus) {
          return
        }
        changeDigitizeStatus(this.archive.id, key).then(res => {
          if (res.success) {
            this.$message.success(`数字化状态已变更为「${key}」`)
            this.loadDetail(this.archive.id)
          } else {
            this.$message.warning(res.message)
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '状态变更失败')
        })
      },
      // ------------------------------------------------------------------
      // 关联扫描件
      // ------------------------------------------------------------------
      openArchivePicker () {
        this.pickerVisible = true
        this.pickerKeyword = ''
        this.loadArchiveOptions()
      },
      loadArchiveOptions () {
        const params = { limit: 50 }
        if (this.pickerOnlySameProject) {
          if (this.archive && this.archive.facilityId) {
            params.facilityId = this.archive.facilityId
          } else if (this.archive && this.archive.crzdbh) {
            params.crzdbh = this.archive.crzdbh
          }
        }
        if (this.pickerKeyword) {
          params.keyword = this.pickerKeyword
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
        if (!this.archive || !this.archive.id) {
          return
        }
        linkCompletionArchive(this.archive.id, record.id).then(res => {
          if (res.success) {
            this.$message.success('关联成功！')
            this.pickerVisible = false
            this.loadDetail(this.archive.id)
          } else {
            this.$message.warning(res.message)
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '关联失败')
        })
      },
      handleUnlink () {
        if (!this.archive || !this.archive.id) {
          return
        }
        unlinkCompletionArchive(this.archive.id).then(res => {
          if (res.success) {
            this.$message.success('已取消关联！')
            this.loadDetail(this.archive.id)
          } else {
            this.$message.warning(res.message)
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '取消关联失败')
        })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;

  .completion-detail {
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

    &__progress {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-top: 12px;
      padding: 10px 14px;
      background: #f8fafc;
      border: 1px solid @border-color;
      border-radius: 8px;

      /deep/ .ant-progress {
        flex: none;
        width: 220px;
      }
    }

    &__progress-text {
      font-size: 12px;
      color: @text-muted;
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
      margin-top: 10px;
    }

    /* 信息表：固定 table-layout，避免长标签被压成两行（见 docs 7.4） */
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

    &__danger {
      color: #ff4d4f;
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
