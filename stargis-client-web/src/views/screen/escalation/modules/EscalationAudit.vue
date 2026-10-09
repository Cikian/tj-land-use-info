<template>
  <!--
    EscalationAudit 提级论证审批 —— 页面内标题为「审核意见登记」（设计文档 2.5 / 5.3）
    --------------------------------
    菜单名保留「提级论证审批」以便与方案 2.3.3 第 4 项对号，
    但页面明确写「审核意见登记」并注明「流程在线下办理，此处仅登记论证结论与审核意见」。

    三栏布局（对齐原型，去掉流转要素）：
      左  项目列表（关键字搜索 + 办理状态筛选 + 分页）
      中  项目基本信息 → 提级论证材料 → 审核意见记录（时间倒序、append-only）
      右  登记审核意见面板（1000 字计数 / 5 个快捷短语 / 附件 ≤50MB / 默认不同步状态）

    ★ 登记意见**不会**自动推进办理状态：只有右栏勾了「同时更新办理状态」才带 status 提交，
      这是设计文档 2.3「不做意见驱动的状态流转」在前端的落点。
  -->
  <div class="escalation-audit">
    <!-- ============ 页头 ============ -->
    <header class="escalation-audit__head">
      <div class="escalation-audit__head-text">
        <h2 class="escalation-audit__title">审核意见登记</h2>
        <p class="escalation-audit__desc">
          <screen-icon name="info" :size="13" />
          流程在线下办理，此处仅登记论证结论与审核意见。意见记录只增不改不删（可追溯），
          登记意见<strong>不会</strong>自动推进办理状态；如需同步状态，请在右栏选择「同时更新办理状态」。
        </p>
      </div>

      <div class="escalation-audit__head-actions">
        <screen-select
          v-model="filterStatus"
          class="escalation-audit__status-filter"
          :options="statusOptions"
          placeholder="全部办理状态"
          aria-label="按办理状态筛选项目"
          @change="handleStatusFilter"
        />
        <screen-button icon="reload" :loading="projectLoading" @click="loadProjects">刷新</screen-button>
      </div>
    </header>

    <!-- ============ 三栏 ============ -->
    <div class="escalation-audit__layout">
      <!-- ---------- 左：项目列表 ---------- -->
      <screen-panel class="escalation-audit__col is-left" title="项目列表" scrollable>
        <template #extra>
          <span class="escalation-audit__count">共 {{ projectTotal }} 个</span>
        </template>

        <div class="escalation-audit__search">
          <screen-input
            v-model="keyword"
            icon="search"
            clearable
            placeholder="项目名称 / 编号"
            @enter="handleSearchProject"
          />
          <screen-button size="sm" icon="search" @click="handleSearchProject">查询</screen-button>
        </div>

        <screen-empty
          v-if="!projects.length && !projectLoading"
          size="sm"
          text="没有匹配的项目"
          description="换个关键字或清空办理状态筛选试试"
        />

        <ul v-else class="escalation-audit__projects">
          <li v-for="item in projects" :key="item.id">
            <button
              type="button"
              class="escalation-audit__project"
              :class="{ 'is-active': current && current.id === item.id }"
              :aria-current="current && current.id === item.id ? 'true' : undefined"
              @click="handleSelect(item)"
            >
              <span class="escalation-audit__project-name" :title="item.projectName">
                {{ item.projectName || '—' }}
              </span>
              <span class="escalation-audit__project-meta">
                <em>{{ item.projectNo || '—' }}</em>
                <screen-tag :tone="statusTone(item.status)" size="sm">{{ item.status || '未办理' }}</screen-tag>
                <screen-tag v-if="item.argResult" :tone="argResultTone(item.argResult)" size="sm">
                  {{ item.argResult }}
                </screen-tag>
              </span>
            </button>
          </li>
        </ul>

        <template #footer>
          <screen-pagination
            :current="pagination.current"
            :page-size="pagination.pageSize"
            :total="projectTotal"
            :show-size-changer="false"
            :show-quick-jumper="false"
            @change="handleProjectPageChange"
          />
        </template>
      </screen-panel>

      <!-- ---------- 中：项目信息 / 材料 / 意见记录 ---------- -->
      <screen-panel class="escalation-audit__col is-middle" :title="middleTitle" scrollable>
        <template #extra>
          <screen-button v-if="current" size="sm" icon="eye" @click="openDetail(current)">
            查看完整详情
          </screen-button>
        </template>

        <screen-empty
          v-if="!current"
          text="请从左侧选择一个项目"
          description="选中后这里会显示项目信息、材料与历史意见"
          bordered
        />

        <screen-loading
          v-else
          class="escalation-audit__middle-body"
          :loading="detailLoading"
          text="正在加载项目信息…"
          :overlay="false"
        >
          <section class="escalation-audit__block">
            <h4 class="escalation-audit__block-title">项目信息</h4>
            <screen-descriptions
              variant="flat"
              :items="currentItems"
              :columns="2"
              size="sm"
              label-width="104px"
            >
              <template #status="{ value }">
                <screen-tag :tone="statusTone(value)" size="sm">{{ value || '未办理' }}</screen-tag>
              </template>
              <template #argResult="{ value }">
                <screen-tag v-if="value" :tone="argResultTone(value)" size="sm">{{ value }}</screen-tag>
                <span v-else class="escalation-audit__muted">未登记</span>
              </template>
            </screen-descriptions>
          </section>

          <section class="escalation-audit__block">
            <h4 class="escalation-audit__block-title">
              提级论证材料
              <span class="escalation-audit__block-hint">
                共 {{ current.materialCount || 0 }} 个，可下载 / 预览
              </span>
            </h4>
            <escalation-material-table ref="materialTable" :project-id="current.id" disabled />
          </section>

          <section class="escalation-audit__block">
            <h4 class="escalation-audit__block-title">
              审核意见记录
              <span class="escalation-audit__block-hint">
                时间倒序 · 只增不改不删，共 {{ records.length }} 条
              </span>
            </h4>

            <screen-empty
              v-if="!records.length"
              size="sm"
              text="该项目还没有登记过审核意见"
              description="在右栏填写意见正文后点「保存登记」"
            />

            <ul v-else class="escalation-audit__records">
              <li v-for="item in records" :key="item.id" class="escalation-audit__record">
                <div class="escalation-audit__record-head">
                  <span class="escalation-audit__record-time">
                    {{ item.recordTime || item.createTime || '—' }}
                  </span>
                  <span class="escalation-audit__record-who">
                    {{ item.recorderName || item.recorderId || '—' }}
                  </span>
                  <screen-tag tone="muted" size="sm">{{ item.recordType || '审核意见' }}</screen-tag>
                  <screen-tag v-if="item.action" :tone="actionTone(item.action)" size="sm">
                    {{ item.action }}
                  </screen-tag>
                </div>

                <p class="escalation-audit__record-opinion">{{ item.opinion }}</p>

                <div v-if="parseAttachments(item.attachmentIds).length" class="escalation-audit__record-files">
                  <button
                    v-for="file in parseAttachments(item.attachmentIds)"
                    :key="file.path"
                    type="button"
                    class="escalation-audit__record-file"
                    @click="openAttachment(file.path)"
                  >
                    <screen-icon name="paperclip" :size="12" />{{ file.name }}
                  </button>
                </div>
              </li>
            </ul>
          </section>
        </screen-loading>
      </screen-panel>

      <!-- ---------- 右：登记审核意见 ---------- -->
      <screen-panel class="escalation-audit__col is-right" title="登记审核意见">
        <template #extra>
          <span v-if="current" class="escalation-audit__count">{{ current.projectNo }}</span>
        </template>

        <screen-empty
          v-if="!current"
          text="请先从左侧选择项目"
          description="登记面板会关联到选中的项目"
          bordered
        />

        <escalation-record-panel
          v-else
          :key="current.id"
          ref="recordPanel"
          :project-id="current.id"
          :current-status="current.status"
          @ok="handleRecordOk"
        />
      </screen-panel>
    </div>

    <escalation-detail-modal ref="detailModal" @edit="handleEditFromDetail" />
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenButton,
  ScreenPagination,
  ScreenSelect,
  ScreenInput,
  ScreenTag,
  ScreenDescriptions,
  ScreenEmpty,
  ScreenLoading,
  ScreenIcon,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { getJavaFileAccessHttpUrl } from '@/api/manageJava'
import { queryProjectList, queryProjectById, queryRecordList } from '@/api/land/escalation'
import EscalationMaterialTable from './EscalationMaterialTable.vue'
import EscalationRecordPanel from './EscalationRecordPanel.vue'
import EscalationDetailModal from './EscalationDetailModal.vue'
import {
  STATUS_OPTIONS,
  statusTone,
  argResultTone,
  actionTone,
  formatInvestment,
  parseAttachments,
  toOptions,
} from '../constants'

export default {
  name: 'EscalationAudit',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenPagination,
    ScreenSelect,
    ScreenInput,
    ScreenTag,
    ScreenDescriptions,
    ScreenEmpty,
    ScreenLoading,
    ScreenIcon,
    EscalationMaterialTable,
    EscalationRecordPanel,
    EscalationDetailModal,
  },
  data () {
    return {
      keyword: '',
      filterStatus: '',
      projectLoading: false,
      detailLoading: false,
      projects: [],
      projectTotal: 0,
      current: null,
      records: [],
      statusOptions: toOptions(STATUS_OPTIONS),
      pagination: { current: 1, pageSize: 10 },
    }
  },
  computed: {
    middleTitle () {
      return this.current ? this.current.projectName : '项目信息'
    },
    currentItems () {
      const d = this.current || {}
      return [
        { key: 'projectNo', label: '项目编号', value: d.projectNo },
        { key: 'declareDept', label: '申报单位', value: d.declareDept },
        { key: 'projectType', label: '项目类型', value: d.projectType },
        { key: 'totalInvestment', label: '总投资（亿元）', value: this.investmentText(d.totalInvestment) },
        { key: 'declareDate', label: '申报时间', value: d.declareDate },
        { key: 'buildLocation', label: '建设地点', value: d.buildLocation },
        { key: 'status', label: '办理状态', value: d.status || '未办理' },
        { key: 'argResult', label: '论证结果', value: d.argResult },
        { key: 'argOrg', label: '论证组织单位', value: d.argOrg },
        { key: 'argMeetingDate', label: '论证会日期', value: d.argMeetingDate },
        { key: 'argDate', label: '论证完成日期', value: d.argDate },
        { key: 'argConclusion', label: '论证结论', value: d.argConclusion },
      ]
    },
  },
  created () {
    this.loadProjects()
  },
  methods: {
    statusTone,
    argResultTone,
    actionTone,
    parseAttachments,

    /* ---------------- 左侧项目列表 ---------------- */

    loadProjects () {
      this.projectLoading = true
      const params = {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize,
      }
      if (this.keyword) params.projectName = this.keyword
      if (this.filterStatus) params.status = this.filterStatus

      return queryProjectList(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '项目列表加载失败')
            return
          }
          const page = res.result || {}
          this.projects = page.records || []
          this.projectTotal = page.total || 0
          // 选中项被筛掉时清空中/右两栏，避免「列表里没有、右边还显示着」
          if (this.current && !this.projects.some((item) => item.id === this.current.id)) {
            this.current = null
            this.records = []
          }
        })
        .finally(() => {
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

    handleProjectPageChange ({ current }) {
      this.pagination.current = current
      this.loadProjects()
    },

    /* ---------------- 选中项目 ---------------- */

    handleSelect (record) {
      this.current = record
      this.records = []
      this.detailLoading = true

      queryProjectById(record.id)
        .then((res) => {
          if (res && res.success && res.result) {
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
        })
        .finally(() => {
          this.detailLoading = false
        })
    },

    loadRecords () {
      if (!this.current) return Promise.resolve()
      return queryRecordList(this.current.id).then((res) => {
        if (res && res.success) {
          this.records = res.result || []
        }
      })
    },

    /** 登记成功后：刷新意见记录、材料表、当前项目与左侧列表（状态可能被同步更新） */
    handleRecordOk () {
      const id = this.current ? this.current.id : null
      this.loadRecords()
      if (this.$refs.materialTable && this.$refs.materialTable.refresh) {
        this.$refs.materialTable.refresh()
      }
      if (!id) return Promise.resolve()
      return queryProjectById(id)
        .then((res) => {
          if (res && res.success && res.result) {
            this.current = Object.assign({}, this.current, res.result)
          }
        })
        .then(() => this.loadProjects())
    },

    /* ---------------- 详情 / 编辑 ---------------- */

    openDetail (record) {
      this.$refs.detailModal.open(record)
    },

    /**
     * 详情页点「编辑」：本页没有编辑弹窗的入口（录入是「项目录入」页的职责），
     * 所以这里只给一句明确指引，不静默失败。
     */
    handleEditFromDetail (record) {
      toast.info(`请到「项目录入」页编辑该项目：${(record && record.projectNo) || ''}`)
    },

    /* ---------------- 展示工具 ---------------- */

    investmentText (value) {
      if (value === null || value === undefined || value === '') return '—'
      const text = formatInvestment(value)
      return text === '' ? '—' : text
    },

    openAttachment (path) {
      const url = getJavaFileAccessHttpUrl(path)
      if (url) {
        window.open(url, '_blank')
      } else {
        toast.warning('该附件还没有可访问的地址')
      }
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.escalation-audit {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  /* ---------------- 页头 ---------------- */
  &__head {
    flex: 0 0 auto;
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--screen-space-4);
    flex-wrap: wrap;
    padding: var(--screen-space-3) var(--screen-space-4);
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius);
    box-shadow: var(--screen-shadow-inset);
  }

  &__head-text {
    min-width: 0;
  }

  &__title {
    margin: 0 0 4px;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    color: var(--screen-text);
  }

  &__desc {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin: 0;
    max-width: 860px;
    font-size: var(--screen-font-xs);
    line-height: 1.7;
    color: var(--screen-text-sub);

    strong {
      color: var(--screen-warning);
      font-weight: 500;
    }
  }

  &__head-actions {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    flex-wrap: wrap;
    flex: none;
  }

  &__status-filter {
    width: 150px;
  }

  /* ---------------- 三栏 ---------------- */
  &__layout {
    flex: 1 1 0;
    min-height: 0;
    display: grid;
    // 左项目列表 / 中信息与记录 / 右登记面板
    grid-template-columns: 280px minmax(0, 1fr) 380px;
    gap: var(--screen-space-3);
  }

  &__col {
    min-width: 0;
    min-height: 0;

    /*
      左栏与中栏内部自己滚动（用 ScreenPanel 的 scrollable 属性，而不是改组件样式）。
      两栏的正文块都设 flex: 0 0 auto：否则 flex 容器的子项会被「压扁」而不是产生滚动。
    */
    &.is-left /deep/ .screen-panel__body > *,
    &.is-middle /deep/ .screen-panel__body > * {
      flex: 0 0 auto;
    }

    /* 细滚动条（ScreenPanel 的 is-scroll 只管 overflow，不负责滚动条外观） */
    &.is-left /deep/ .screen-panel__body,
    &.is-middle /deep/ .screen-panel__body {
      .screen-scrollbar();
    }
  }

  &__count {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  /* ---------------- 左栏项目列表 ---------------- */
  &__search {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    margin-bottom: var(--screen-space-2);

    /deep/ .screen-input {
      flex: 1 1 auto;
      min-width: 0;
    }
  }

  &__projects {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  &__project {
    display: flex;
    flex-direction: column;
    gap: 4px;
    width: 100%;
    padding: var(--screen-space-2) var(--screen-space-3);
    margin-bottom: 6px;
    font-family: inherit;
    text-align: left;
    background: transparent;
    border: 1px solid transparent;
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: background-color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      background: var(--screen-elevate);
    }

    &.is-active {
      background: var(--screen-elevate);
      border-color: var(--screen-border-strong);
    }
  }

  &__project-name {
    font-size: var(--screen-font-sm);
    font-weight: 500;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__project-meta {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;

    em {
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      font-size: var(--screen-font-xs);
      font-style: normal;
      color: var(--screen-text-mute);
    }
  }

  /* ---------------- 中栏 ---------------- */
  &__middle-body {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
    min-width: 0;
  }

  &__block {
    min-width: 0;
  }

  &__block-title {
    display: flex;
    align-items: baseline;
    gap: var(--screen-space-2);
    margin: 0 0 var(--screen-space-2);
    font-size: var(--screen-font-md);
    font-weight: 500;
    color: var(--screen-text);
  }

  &__block-hint {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  &__muted {
    color: var(--screen-text-mute);
  }

  &__records {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  &__record {
    padding: var(--screen-space-3);
    margin-bottom: var(--screen-space-2);
    background: var(--screen-row-alt);
    border: 1px solid var(--screen-border-soft);
    border-left: 3px solid var(--screen-accent);
    border-radius: var(--screen-radius-sm);
  }

  &__record-head {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
    margin-bottom: 5px;
    font-size: var(--screen-font-xs);
  }

  &__record-time {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-sub);
  }

  &__record-who {
    color: var(--screen-text);
  }

  &__record-opinion {
    margin: 0;
    font-size: var(--screen-font-sm);
    line-height: 1.7;
    color: var(--screen-text-sub);
    white-space: pre-wrap;
    word-break: break-word;
  }

  &__record-files {
    display: flex;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
    margin-top: var(--screen-space-2);
  }

  &__record-file {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    background: none;
    border: 0;
    cursor: pointer;
    .screen-link-action();
  }
}

/* 窄屏降级：三栏变一栏（大屏上基本用不到，但保证小窗口下仍可用） */
@media (max-width: 1400px) {
  .escalation-audit__layout {
    grid-template-columns: 240px minmax(0, 1fr) 320px;
  }
}

@media (max-width: 1180px) {
  .escalation-audit__layout {
    grid-template-columns: minmax(0, 1fr);
    grid-auto-rows: minmax(240px, auto);
    overflow-y: auto;
  }
}
</style>
