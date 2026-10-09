<template>
  <a-card :bordered="false" class="facility-entry">
    <!-- ============ 页头 ============ -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">配套地块数据录入</h2>
        <p class="page-head__desc">
          方案 2.3.1（三）第 2 项。<b>1 宗地 N 配套项目</b> + <b>六大阶段 29 环节进度录入</b>。
          先选出让宗地，再维护它下面的配套项目；每个配套项目可逐环节填报进度，
          阶段状态由所属环节自动汇总（「不涉及」的环节从完成度分母里剔除）。
          预计结束时间按<b>工作日</b>（剔除周末与节假日日历）自动推算，逾期环节会标红。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button
          v-has="'land:data:facility'"
          type="primary"
          icon="plus"
          :disabled="!currentLand"
          @click="handleAddFacility">
          新增配套项目
        </a-button>
        <a-button icon="reload" :loading="loading" @click="refresh">刷新</a-button>
      </div>
    </div>

    <!-- ============ 第一步：选宗地 ============ -->
    <div class="land-picker">
      <div class="land-picker__label">
        <span class="land-picker__step">第一步</span>
        选择出让宗地
      </div>
      <a-select
        v-model="selectedCrzdbh"
        class="land-picker__select"
        show-search
        allow-clear
        placeholder="输入出让宗地编号或地块名称搜索（至少 1 个字）"
        :filter-option="false"
        :not-found-content="landSearching ? undefined : '没有匹配的宗地，请先到「经营性用地信息录入」新增'"
        @search="handleLandSearch"
        @change="handleLandChange">
        <a-spin v-if="landSearching" slot="notFoundContent" size="small" />
        <a-select-option v-for="item in landOptions" :key="item.crzdbh" :value="item.crzdbh">
          {{ item.label || (item.crzdbh + '（' + (item.dkmc || '未命名') + '）') }}
        </a-select-option>
      </a-select>

      <div v-if="currentLand" class="land-picker__info">
        <a-tag color="blue">{{ currentLand.crzdbh }}</a-tag>
        <span class="land-picker__name">{{ currentLand.dkmc || '未填写地块名称' }}</span>
        <span class="land-picker__meta">{{ currentLand.xzqh || '未填区划' }}</span>
        <span class="land-picker__meta">{{ currentLand.xmfl || '未填分类' }}</span>
        <span class="land-picker__count">已有 <b>{{ facilities.length }}</b> 个配套项目</span>
      </div>
      <div v-else class="land-picker__empty">
        <a-icon type="info-circle" />
        ★ 配套项目必须挂到已有宗地：后端会做宗地存在性校验。
        旧系统不校验，实测产生了 60 行「孤儿配套」—— 这些配套在按宗地关联的档案、收发文、台账里永远挂不上。
      </div>
    </div>

    <!-- ============ 未选宗地时的引导 ============ -->
    <a-empty v-if="!currentLand" class="facility-empty" description="请先在上方选择一个出让宗地">
      <span slot="description">
        请先在上方选择一个出让宗地，再维护它的配套项目与环节进度
      </span>
    </a-empty>

    <!-- ============ 第二步：配套项目列表 ============ -->
    <template v-else>
      <div class="facility-section">
        <div class="section-head">
          <span class="section-head__title">第二步 · 配套项目</span>
          <span class="section-head__hint">
            点「环节进度」进入 29 环节录入；阶段状态由该阶段下的事项自动汇总
          </span>
        </div>

        <a-table
          rowKey="id"
          size="small"
          :columns="facilityColumns"
          :dataSource="facilities"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1500 }">
          <template slot="ptxmmc" slot-scope="text, record">
            <a class="cell-link" @click="handleDetail(record)">{{ text }}</a>
          </template>
          <template slot="ptsslb" slot-scope="text">
            <a-tag v-if="text">{{ text }}</a-tag>
            <span v-else>—</span>
          </template>
          <template slot="processStat" slot-scope="text">
            <div v-if="text" class="stat-cell">
              <a-progress
                :percent="text.percent || 0"
                :stroke-width="6"
                :show-info="false"
                class="stat-cell__bar" />
              <span class="stat-cell__text">
                已完成 {{ text.finished || 0 }} / 已填 {{ text.filled || 0 }}
              </span>
              <a-tag v-if="text.overdue" color="red">逾期 {{ text.overdue }}</a-tag>
            </div>
            <span v-else>—</span>
          </template>
          <template slot="stageStatus" slot-scope="text">
            <span v-if="text && text.length" class="stage-chips">
              <a-tooltip v-for="stage in text" :key="stage.stageId" :title="stageTooltip(stage)">
                <span class="stage-chip" :class="stageClass(stage.status)">{{ stage.stageName }}</span>
              </a-tooltip>
            </span>
            <span v-else>—</span>
          </template>
          <template slot="action" slot-scope="text, record">
            <a-button size="small" type="link" icon="partition" @click="handleProcess(record)">环节进度</a-button>
            <a-button size="small" type="link" icon="eye" @click="handleDetail(record)">详情</a-button>
            <a-button v-has="'land:data:facility'" size="small" type="link" icon="edit" @click="handleEditFacility(record)">编辑</a-button>
            <a-button
              v-has="'land:data:facility'"
              size="small"
              type="link"
              class="is-danger"
              icon="delete"
              @click="handleRemoveFacility(record)">移除</a-button>
          </template>
        </a-table>
      </div>
    </template>

    <!-- ============ 第三步：29 环节进度（选中配套后展开） ============ -->
    <div v-if="processFacility" class="process-section">
      <div class="section-head">
        <span class="section-head__title">
          第三步 · 环节进度
          <span class="section-head__facility">{{ processFacility.ptxmmc }}</span>
        </span>
        <div class="section-head__actions">
          <a-button size="small" icon="reload" :loading="treeLoading" @click="loadProcessTree">刷新进度</a-button>
          <a-button size="small" icon="close" @click="closeProcess">收起</a-button>
        </div>
      </div>

      <!-- 顶层汇总 -->
      <div v-if="tree" class="process-summary">
        <div class="process-summary__item">
          <div class="process-summary__label">整体完成度</div>
          <a-progress
            :percent="tree.percent || 0"
            :stroke-color="progressColor(tree.percent)"
            class="process-summary__progress" />
        </div>
        <div class="process-summary__item">
          <div class="process-summary__label">已填环节</div>
          <div class="process-summary__value">{{ tree.filledItems }} / {{ tree.totalItems }}</div>
        </div>
        <div class="process-summary__item">
          <div class="process-summary__label">已完成</div>
          <div class="process-summary__value is-ok">{{ tree.finishedItems }}</div>
        </div>
        <div class="process-summary__item">
          <div class="process-summary__label">进行中</div>
          <div class="process-summary__value is-running">{{ tree.runningItems }}</div>
        </div>
        <div class="process-summary__item">
          <div class="process-summary__label">不涉及</div>
          <div class="process-summary__value is-muted">{{ tree.notInvolvedItems }}</div>
        </div>
        <div class="process-summary__item">
          <div class="process-summary__label">逾期</div>
          <div class="process-summary__value" :class="tree.overdueItems ? 'is-error' : ''">
            {{ tree.overdueItems }}
          </div>
        </div>
      </div>

      <!-- 日历时效性提示 -->
      <a-alert
        v-if="calendarNotice"
        class="process-alert"
        type="warning"
        show-icon
        :message="calendarNotice" />

      <!-- 六大阶段 -->
      <a-collapse v-if="tree" v-model="activeStages" class="process-collapse">
        <a-collapse-panel v-for="stage in tree.stages" :key="stage.stageId">
          <template slot="header">
            <span class="stage-head">
              <a-tag :color="processStatusColor(stage.status)">{{ stage.status }}</a-tag>
              <span class="stage-head__name">{{ stage.stageName }}</span>
              <span class="stage-head__meta">
                已填 {{ stage.filledCount }} / {{ stage.itemCount }}
                <template v-if="stage.processTime">· 标准时长 {{ stage.processTime }} 工作日</template>
                <template v-if="stage.overdueCount">
                  · <span class="is-error">逾期 {{ stage.overdueCount }} 项</span>
                </template>
              </span>
            </span>
            <a-progress
              :percent="stage.percent || 0"
              :stroke-width="4"
              :show-info="false"
              class="stage-head__bar" />
          </template>

          <a-table
            rowKey="lcId"
            size="small"
            :columns="processColumns"
            :dataSource="stage.items"
            :pagination="false"
            :row-class-name="() => 'process-row'">
            <template slot="lcName" slot-scope="text, record">
              <span :class="{ 'is-unfilled': !record.filled }">{{ text }}</span>
              <a-tag v-if="!record.filled" class="unfilled-tag">未填报</a-tag>
            </template>
            <template slot="lcqk" slot-scope="text">
              <a-tag :color="processStatusColor(text)">{{ text || '未开启' }}</a-tag>
            </template>
            <template slot="zhuguan" slot-scope="text, record">
              <span class="cell-sub">{{ record.zgbm || '—' }}</span>
            </template>
            <template slot="date" slot-scope="text, record, index">
              <span v-if="index === 0">{{ text || '—' }}</span>
              <span v-else>{{ text || '—' }}</span>
            </template>
            <template slot="overdue" slot-scope="text, record">
              <a-tag v-if="record.overdue" color="red">逾期 {{ record.overdueDays }} 天</a-tag>
              <span v-else class="cell-sub">—</span>
            </template>
            <template slot="action" slot-scope="text, record">
              <a-button
                v-has="'land:data:facility'"
                size="small"
                type="link"
                icon="edit"
                @click="handleEditProcess(stage, record)">
                {{ record.filled ? '修改' : '填报' }}
              </a-button>
              <a-button
                v-if="record.filled"
                v-has="'land:data:facility'"
                size="small"
                type="link"
                class="is-danger"
                @click="handleRemoveProcess(record)">
                撤回
              </a-button>
            </template>
          </a-table>
        </a-collapse-panel>
      </a-collapse>

      <a-empty v-else-if="!treeLoading" description="没有取到环节配置，请检查流程配置表是否已初始化" />
    </div>

    <!-- ============ 弹窗 ============ -->
    <facility-form-modal ref="facilityForm" @ok="loadFacilities" />
    <facility-detail-modal ref="facilityDetail" @open-process="handleProcessById" />
    <process-item-modal ref="processItem" :pt-id="processFacility ? processFacility.id : null" :pt-name="processFacility ? processFacility.ptxmmc : ''" @ok="loadProcessTree" />
  </a-card>
</template>

<script>
  import FacilityFormModal from './modules/FacilityFormModal'
  import FacilityDetailModal from './modules/FacilityDetailModal'
  import ProcessItemModal from './modules/ProcessItemModal'
  import {
    queryFacilityByLand,
    deleteFacility,
    queryProcessTree,
    deleteProcess
  } from '@/api/land/facilityAdmin'
  import { queryLandOptions } from '@/api/land/landData'

  /**
   * 配套地块数据录入（方案 2.3.1（三）第 2 项）
   *
   * 三步式页面：选宗地 → 维护配套项目 → 录入 29 环节进度。
   *
   * ★ 为什么坚持「先选宗地」这一层，而不是直接给一张全量配套列表：
   *   配套项目的业务语义是「宗地下的一个配套子项」，脱离宗地看列表，
   *   用户面对 1433 行既不知道从哪里下手，也看不出哪个宗地的配套还没做。
   *   所以入口按「宗地 → 配套 → 环节」的层级走，与业务的心智模型一致。
   *
   * ★ 阶段汇总口径（与后端 ProcessStatus 同一实现，前端只展示不重算）：
   *   「不涉及」的环节从完成度分母里剔除 —— 否则某阶段 3 个事项里 2 个不涉及、
   *   1 个已完成时，完成度会长期停在 33% 而看起来像没干完。
   *   阶段状态与完成度一律用后端返回的值，前端**不重新计算**，
   *   这样才能保证「列表页看到的百分数」与「导出/统计里的百分数」是同一个数。
   */
  export default {
    name: 'FacilityEntry',
    components: { FacilityFormModal, FacilityDetailModal, ProcessItemModal },
    data () {
      return {
        loading: false,
        treeLoading: false,
        // ---- 宗地 ----
        selectedCrzdbh: undefined,
        currentLand: null,
        landOptions: [],
        landSearching: false,
        // ---- 配套 ----
        facilities: [],
        facilityColumns: [
          { title: '配套项目名称', dataIndex: 'ptxmmc', width: 220, fixed: 'left', scopedSlots: { customRender: 'ptxmmc' } },
          { title: '配套设施类别', dataIndex: 'ptsslb', width: 110, scopedSlots: { customRender: 'ptsslb' } },
          { title: '建设性质', dataIndex: 'jsxx', width: 90, customRender: text => text || '—' },
          { title: '道路等级', dataIndex: 'dldj', width: 110, customRender: text => text || '—' },
          { title: '长度(米)', dataIndex: 'cd', width: 100, customRender: text => (text === null || text === undefined ? '—' : text) },
          { title: '投资估算(万元)', dataIndex: 'tzgs', width: 120, customRender: text => (text === null || text === undefined ? '—' : text) },
          { title: '建设单位', dataIndex: 'jsdw', width: 160, customRender: text => text || '—' },
          { title: '环节进度', dataIndex: 'processStat', width: 200, scopedSlots: { customRender: 'processStat' } },
          { title: '六大阶段', dataIndex: 'stageStatus', width: 220, scopedSlots: { customRender: 'stageStatus' } },
          { title: '操作', dataIndex: 'action', width: 260, fixed: 'right', scopedSlots: { customRender: 'action' } }
        ],
        // ---- 环节 ----
        processFacility: null,
        tree: null,
        activeStages: [],
        calendarNotice: '',
        processColumns: [
          { title: '环节名称', dataIndex: 'lcName', width: 260, scopedSlots: { customRender: 'lcName' } },
          { title: '主管部门', dataIndex: 'zgbm', width: 220, scopedSlots: { customRender: 'zhuguan' } },
          { title: '标准时长', dataIndex: 'processTime', width: 90, customRender: text => (text ? text + ' 工作日' : '—') },
          { title: '环节情况', dataIndex: 'lcqk', width: 100, scopedSlots: { customRender: 'lcqk' } },
          { title: '开始时间', dataIndex: 'lckssj', width: 110, scopedSlots: { customRender: 'date' } },
          { title: '预计结束', dataIndex: 'yjjssj', width: 110, scopedSlots: { customRender: 'date' } },
          { title: '实际结束', dataIndex: 'lcjssj', width: 110, scopedSlots: { customRender: 'date' } },
          { title: '存在问题', dataIndex: 'jtwt', width: 200, customRender: text => text || '—' },
          { title: '逾期', dataIndex: 'overdue', width: 110, scopedSlots: { customRender: 'overdue' } },
          { title: '操作', dataIndex: 'action', width: 150, fixed: 'right', scopedSlots: { customRender: 'action' } }
        ]
      }
    },
    methods: {
      refresh () {
        if (this.currentLand) {
          this.loadFacilities()
          if (this.processFacility) {
            this.loadProcessTree()
          }
        }
      },

      // ---------------- 宗地搜索 ----------------

      /**
       * 宗地远程搜索。
       *
       * ★ 用远程搜索而不是一次性拉全表：宗地有 848 条，全拉下来
       *   既慢又让用户在下拉里翻不到底。远程搜索按关键字查，返回最多 50 条。
       */
      handleLandSearch (keyword) {
        const value = (keyword || '').trim()
        if (!value) {
          this.landOptions = []
          return
        }
        this.landSearching = true
        queryLandOptions({ keyword: value, limit: 50 }).then(res => {
          this.landOptions = res.success && res.result ? res.result : []
        }).catch(() => {
          this.landOptions = []
        }).finally(() => {
          this.landSearching = false
        })
      },
      handleLandChange (crzdbh) {
        if (!crzdbh) {
          this.currentLand = null
          this.facilities = []
          this.closeProcess()
          return
        }
        // 从已加载的选项里带出宗地信息（避免再查一次详情接口）
        const hit = this.landOptions.filter(item => item.crzdbh === crzdbh)[0]
        this.currentLand = hit || { crzdbh }
        this.closeProcess()
        this.loadFacilities()
      },

      // ---------------- 配套项目 ----------------

      loadFacilities () {
        if (!this.currentLand) {
          return
        }
        this.loading = true
        queryFacilityByLand(this.currentLand.crzdbh).then(res => {
          this.facilities = res.success && res.result ? res.result : []
        }).catch(() => {
          this.facilities = []
        }).finally(() => {
          this.loading = false
        })
      },
      handleAddFacility () {
        if (!this.currentLand) {
          this.$message.warning('请先选择出让宗地')
          return
        }
        this.$refs.facilityForm.open(null, this.currentLand)
      },
      handleEditFacility (record) {
        this.$refs.facilityForm.open(record.id, this.currentLand)
      },
      handleDetail (record) {
        this.$refs.facilityDetail.open(record.id)
      },
      handleRemoveFacility (record) {
        const self = this
        this.$confirm({
          title: `确认移除配套项目「${record.ptxmmc}」吗？`,
          content: '移除是软删，之后可在「数据更新与移除」里恢复；该配套下的环节进度会保留。',
          okText: '移除',
          okType: 'danger',
          cancelText: '取消',
          onOk () {
            return deleteFacility(record.id).then(res => {
              if (!res.success) {
                self.$message.error(res.message)
                return
              }
              self.$message.success('已移除')
              self.loadFacilities()
              if (self.processFacility && self.processFacility.id === record.id) {
                self.closeProcess()
              }
            })
          }
        })
      },

      // ---------------- 29 环节进度 ----------------

      handleProcess (record) {
        this.processFacility = record
        this.loadProcessTree()
      },
      /** 详情弹窗里点「查看环节进度」时由子组件回调 */
      handleProcessById (id) {
        const hit = this.facilities.filter(item => item.id === id)[0]
        if (hit) {
          this.handleProcess(hit)
        }
      },
      closeProcess () {
        this.processFacility = null
        this.tree = null
        this.calendarNotice = ''
        this.activeStages = []
      },
      loadProcessTree () {
        if (!this.processFacility) {
          return
        }
        this.treeLoading = true
        queryProcessTree(this.processFacility.id).then(res => {
          if (!res.success) {
            this.$message.error(res.message || '环节进度加载失败')
            this.tree = null
            return
          }
          this.tree = res.result
          // 默认只展开「有进度」或「进行中」的阶段，避免一屏铺开 6 个阶段、
          // 每个阶段 4~8 行，用户得滚很久才看到自己要填的那个
          this.activeStages = (this.tree.stages || [])
            .filter(stage => stage.filledCount > 0 || stage.status === '进行中')
            .map(stage => stage.stageId)
          this.updateCalendarNotice()
        }).catch(() => {
          this.tree = null
        }).finally(() => {
          this.treeLoading = false
        })
      },
      /**
       * 节假日日历时效性提示。
       *
       * ★ 为什么要提示：t_non_working_day 只有中心按年维护才有数据，
       *   未维护的年份会退化为「周末 + 内置法定节假日估算」，
       *   预计结束时间会与实际放假安排有偏差。不说清楚，
       *   中心会以为系统算错了。
       */
      updateCalendarNotice () {
        if (!this.tree) {
          this.calendarNotice = ''
          return
        }
        const years = {}
        this.tree.stages.forEach(stage => {
          (stage.items || []).forEach(item => {
            if (item.yjjssj) {
              years[String(item.yjjssj).substring(0, 4)] = true
            }
          })
        })
        const list = Object.keys(years)
        if (!list.length) {
          this.calendarNotice = ''
          return
        }
        this.calendarNotice = `已有预计结束时间的年份：${list.join('、')}。` +
          '预计结束时间按「开始日 + 环节标准时长（工作日）」推算，' +
          '工作日会剔除周末与「非工作日日历」中维护的节假日。' +
          '若某些年份的日历尚未维护，结果会按周末 + 内置法定节假日估算，请以中心发布的放假安排为准。'
      },
      handleEditProcess (stage, record) {
        this.$refs.processItem.open(stage, record, this.tree ? this.tree.ptId : null)
      },
      handleRemoveProcess (record) {
        const self = this
        if (!this.processFacility) {
          return
        }
        this.$confirm({
          title: `确认撤回「${record.lcName}」的环节进度吗？`,
          content: '撤回后该环节回到「未开启」，已填的开始/结束时间与问题描述都会清除（会留一条变更履历）。',
          okText: '撤回',
          okType: 'danger',
          cancelText: '取消',
          onOk () {
            return deleteProcess(self.processFacility.id, record.lcId).then(res => {
              if (!res.success) {
                self.$message.error(res.message)
                return
              }
              self.$message.success('已撤回')
              self.loadProcessTree()
            })
          }
        })
      },

      // ---------------- 展示辅助 ----------------

      processStatusColor (status) {
        if (status === '已完成') {
          return 'green'
        }
        if (status === '进行中') {
          return 'blue'
        }
        if (status === '不涉及') {
          return undefined
        }
        return undefined
      },
      stageClass (status) {
        if (status === '已完成') {
          return 'is-done'
        }
        if (status === '进行中') {
          return 'is-running'
        }
        if (status === '不涉及') {
          return 'is-na'
        }
        return 'is-todo'
      },
      stageTooltip (stage) {
        const parts = [`${stage.stageName}：${stage.status}`]
        parts.push(`已填 ${stage.filledCount} / ${stage.itemCount} 项`)
        parts.push(`完成度 ${stage.percent}%`)
        if (stage.overdueCount) {
          parts.push(`逾期 ${stage.overdueCount} 项`)
        }
        return parts.join('　')
      },
      progressColor (percent) {
        const value = Number(percent) || 0
        if (value >= 100) {
          return '#10b981'
        }
        if (value >= 50) {
          return '#2e7cf6'
        }
        return '#f59e0b'
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .facility-entry {
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
        max-width: 980px;
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
        gap: 8px;
      }
    }

    /* ---------- 第一步：选宗地 ---------- */
    .land-picker {
      padding: 12px 16px;
      margin-bottom: 14px;
      background: #f8fafc;
      border: 1px solid @border-color;
      border-radius: 8px;

      &__label {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 8px;
        font-size: 13px;
        font-weight: 600;
        color: #0f172a;
      }

      &__step {
        padding: 0 8px;
        font-size: 12px;
        font-weight: 400;
        line-height: 20px;
        color: #fff;
        background: #2e7cf6;
        border-radius: 10px;
      }

      &__select {
        min-width: 420px;
        max-width: 640px;
      }

      &__info {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        align-items: center;
        margin-top: 10px;
        font-size: 13px;
      }

      &__name {
        font-weight: 600;
        color: #0f172a;
      }

      &__meta {
        color: @text-muted;
      }

      &__count {
        color: @text-weak;

        b {
          color: #2e7cf6;
        }
      }

      &__empty {
        display: flex;
        gap: 8px;
        margin-top: 10px;
        font-size: 12px;
        line-height: 20px;
        color: @text-weak;
      }
    }

    .facility-empty {
      padding: 40px 0;
    }

    /* ---------- 区块标题 ---------- */
    .section-head {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      justify-content: space-between;
      gap: 10px;
      margin-bottom: 10px;

      &__title {
        font-size: 14px;
        font-weight: 600;
        color: #0f172a;
      }

      &__facility {
        margin-left: 8px;
        font-size: 13px;
        font-weight: 400;
        color: #2e7cf6;
      }

      &__hint {
        font-size: 12px;
        color: @text-weak;
      }

      &__actions {
        display: flex;
        gap: 8px;
      }
    }

    .facility-section {
      margin-bottom: 16px;
    }

    .cell-link {
      color: #2e7cf6;
    }

    .cell-sub {
      font-size: 12px;
      color: @text-weak;
    }

    .stat-cell {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      align-items: center;

      &__bar {
        width: 70px;
        margin: 0;
      }

      &__text {
        font-size: 12px;
        color: @text-muted;
      }
    }

    /* 阶段小标签：六个阶段挤在一格里，用短名 + 悬浮看详情 */
    .stage-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 3px;
    }

    .stage-chip {
      display: inline-block;
      max-width: 66px;
      padding: 0 4px;
      overflow: hidden;
      font-size: 11px;
      line-height: 18px;
      color: @text-muted;
      text-overflow: ellipsis;
      white-space: nowrap;
      background: #f1f5f9;
      border-radius: 3px;

      &.is-done {
        color: #065f46;
        background: #d1fae5;
      }

      &.is-running {
        color: #1e40af;
        background: #dbeafe;
      }

      &.is-na {
        color: @text-weak;
        background: #f1f5f9;
      }
    }

    /* ---------- 第三步：环节进度 ---------- */
    .process-section {
      padding: 12px 16px;
      margin-top: 16px;
      border: 1px solid @border-color;
      border-radius: 8px;
    }

    .process-summary {
      display: flex;
      flex-wrap: wrap;
      gap: 24px;
      align-items: center;
      padding: 10px 12px;
      margin-bottom: 10px;
      background: #f8fafc;
      border-radius: 8px;

      &__item {
        min-width: 90px;
      }

      &__label {
        font-size: 12px;
        color: @text-weak;
      }

      &__value {
        font-family: 'DIN Alternate', 'Bebas Neue', monospace;
        font-size: 18px;
        line-height: 22px;
        color: #0f172a;

        &.is-ok {
          color: #10b981;
        }

        &.is-running {
          color: #2e7cf6;
        }

        &.is-muted {
          color: @text-weak;
        }

        &.is-error {
          color: #ef4444;
        }
      }

      &__progress {
        width: 200px;
        margin: 0;
      }
    }

    .process-alert {
      margin-bottom: 10px;
    }

    .process-collapse {
      /deep/ .ant-collapse-header {
        padding: 8px 12px !important;
      }

      /deep/ .ant-collapse-content-box {
        padding: 0 !important;
      }
    }

    .stage-head {
      display: inline-flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;

      &__name {
        font-size: 13px;
        font-weight: 600;
        color: #0f172a;
      }

      &__meta {
        font-size: 12px;
        font-weight: 400;
        color: @text-weak;
      }

      &__bar {
        display: inline-block;
        width: 120px;
        margin: 0 0 0 12px;
        vertical-align: middle;
      }
    }

    .is-error {
      color: #ef4444;
    }

    .is-unfilled {
      color: @text-muted;
    }

    .unfilled-tag {
      margin-left: 6px;
      font-size: 11px;
      transform: scale(0.9);
    }

    .is-danger {
      color: #ff4d4f;
    }
  }
</style>
