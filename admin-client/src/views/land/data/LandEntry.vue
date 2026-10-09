<template>
  <a-card :bordered="false" class="land-entry">
    <!-- ============ 页头 ============ -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">经营性用地信息录入</h2>
        <p class="page-head__desc">
          方案 2.3.1（三）第 1 项。逐条录入出让宗地（<b>34 个业务字段</b>，按标识与分类 / 出让与面积 /
          受让人与交付 / 四至 / 土地整理 / 配套与图形 / 录入信息分组）。
          <b>「出让宗地编号」是整条数据线的业务主键</b> —— 配套项目、档案、收发文、道路台账都按它关联，
          因此录入时实时查重，重复编号不允许保存。
          这里的<b>移除是软删</b>：数据不会被物理删除，可到「数据更新与移除」里恢复，
          每次新增 / 修改 / 移除都会写入字段级变更履历。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:data:land'" type="primary" icon="plus" @click="handleAdd">新增宗地</a-button>
        <a-button
          v-has="'land:data:land'"
          icon="delete"
          :disabled="!selectedRowKeys.length"
          @click="handleBatchRemove">批量移除{{ selectedRowKeys.length ? '（' + selectedRowKeys.length + '）' : '' }}</a-button>
        <a-button icon="reload" :loading="loading" @click="refresh">刷新</a-button>
      </div>
    </div>

    <!-- ============ 指标卡 ============ -->
    <div class="entry-stats">
      <a-row :gutter="12">
        <a-col v-for="card in statCards" :key="card.key" :xs="12" :sm="8" :md="6">
          <div class="stat-card">
            <div class="stat-card__label">{{ card.label }}</div>
            <div class="stat-card__value">{{ card.value }}</div>
            <div class="stat-card__extra">{{ card.extra }}</div>
          </div>
        </a-col>
      </a-row>
    </div>

    <!-- ============ 查询条件 ============ -->
    <a-form layout="inline" class="land-search">
      <a-row :gutter="16" type="flex">
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="出让宗地编号">
            <a-input
              v-model="query.crzdbh"
              placeholder="模糊匹配"
              allow-clear
              @pressEnter="handleSearch" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="地块名称">
            <a-input
              v-model="query.dkmc"
              placeholder="模糊匹配"
              allow-clear
              @pressEnter="handleSearch" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="行政区划">
            <a-select v-model="query.xzqh" placeholder="全部" allow-clear :options="xzqhOptions" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="项目分类">
            <a-select v-model="query.xmfl" placeholder="全部" allow-clear :options="xmflOptions" />
          </a-form-item>
        </a-col>

        <!-- ============ 展开后的更多条件 ============ -->
        <template v-if="expanded">
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="受让人">
              <a-input
                v-model="query.srr"
                placeholder="模糊匹配"
                allow-clear
                @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="出让时间">
              <a-range-picker
                v-model="crsjRange"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :placeholder="['开始日期', '结束日期']"
                @change="handleCrsjChange" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="有无配套">
              <a-select v-model="query.hasFacility" placeholder="全部" :options="hasFacilityOptions" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="配套名称 / 编号">
              <a-input
                v-model="query.facilityKeyword"
                placeholder="按配套反查宗地"
                allow-clear
                @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="规划用地性质">
              <a-input
                v-model="query.ghydxz"
                placeholder="模糊匹配"
                allow-clear
                @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
        </template>

        <a-col :xs="24" class="land-search__actions">
          <a-button type="primary" icon="search" @click="handleSearch">查询</a-button>
          <a-button icon="reload" @click="handleReset">重置</a-button>
          <a-button type="link" @click="expanded = !expanded">
            {{ expanded ? '收起' : '更多条件' }}
            <a-icon :type="expanded ? 'up' : 'down'" />
          </a-button>
          <span class="land-search__hint">
            ★ 「有无配套」按配套表是否存在该宗地编号判断：
            选「无配套」筛出的就是<b>还没有任何配套记录的宗地</b>（孤儿宗地），
            是催办配套落实最常用的一个条件
          </span>
        </a-col>
      </a-row>
    </a-form>

    <!-- ============ 列表 ============ -->
    <a-table
      row-key="id"
      size="small"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="pagination"
      :row-selection="rowSelection"
      :scroll="{ x: 1650 }"
      @change="handleTableChange">
      <template slot="crzdbh" slot-scope="text, record">
        <a class="land-entry__link" @click="handleDetail(record)">{{ text || '—' }}</a>
      </template>
      <template slot="xmfl" slot-scope="text">
        <a-tag v-if="text" :color="text === '市级项目' ? 'blue' : 'green'">{{ text }}</a-tag>
        <span v-else>—</span>
      </template>
      <template slot="ptsfqq" slot-scope="text">
        <a-tag v-if="text" :color="text === '是' ? 'green' : 'orange'">{{ text }}</a-tag>
        <span v-else>—</span>
      </template>
      <template slot="crj" slot-scope="text">
        {{ numberText(text) }}
      </template>
      <template slot="beizhu" slot-scope="text">
        <span class="land-entry__remark">{{ text || '—' }}</span>
      </template>
      <template slot="action" slot-scope="text, record">
        <a @click="handleDetail(record)">详情</a>
        <span v-has="'land:data:land'">
          <a-divider type="vertical" />
          <a @click="handleEdit(record)">编辑</a>
          <a-divider type="vertical" />
          <a class="land-entry__danger" @click="handleRemove(record)">移除</a>
        </span>
      </template>
    </a-table>

    <!-- ============ 表单 / 详情 / 履历 ============ -->
    <land-form-modal ref="formModal" @ok="handleSaved" />
    <land-detail-modal ref="detailModal" @history="handleHistory" />
    <land-history-modal ref="historyModal" />

    <!-- ============ 移除原因（选填） ============ -->
    <a-modal
      :title="removeIds.length > 1 ? `批量移除 ${removeIds.length} 条宗地` : '移除宗地'"
      :width="560"
      :visible="removeVisible"
      :confirm-loading="removing"
      :mask-closable="false"
      ok-text="确认移除"
      cancel-text="取消"
      @ok="handleRemoveConfirm"
      @cancel="removeVisible = false">
      <a-alert
        type="warning"
        show-icon
        message="移除是软删：记录仍保留在库里，可在「数据更新与移除」页恢复；宗地编号被移除后可以重新录入同一编号。" />
      <div class="remove-form">
        <div class="remove-form__label">
          移除原因<span class="remove-form__optional">（选填，会写进变更履历，便于事后核对为什么移除）</span>
        </div>
        <a-textarea
          v-model="removeReason"
          :rows="3"
          :max-length="200"
          placeholder="例如：重复录入，已用编号 津西青西（挂）2022-004 重新登记" />
      </div>
    </a-modal>
  </a-card>
</template>

<script>
  import LandFormModal from './modules/LandFormModal'
  import LandDetailModal from './modules/LandDetailModal'
  import LandHistoryModal from './modules/LandHistoryModal'
  import { getDictItems } from '@/components/dict/JDictSelectUtil'
  import {
    deleteLand,
    deleteLandBatch,
    queryLandPage
  } from '@/api/land/landAdmin'

  /**
   * 经营性用地信息录入（方案 2.3.1（三）第 1 项）
   *
   * 本页面负责四件事：
   *  1. 查询：「有无配套」这个三态条件是本页最有业务价值的筛选项 ——
   *     选「无配套」即「还没有任何配套记录的宗地」，是催办配套落实的待办清单；
   *     反过来「有配套 + 配套名称关键词」可以按配套反查宗地（配套台账上只写了路名、
   *     没人记得是哪块地时用这个）。
   *  2. 录入：新增 / 编辑走同一个弹窗（34 个字段分 7 组），编号唯一性前后端各校一次。
   *  3. 移除：软删 + 可选原因。原因写进变更履历，因为「为什么移除」是事后最常被问到、
   *     而数据本身永远答不上来的问题。
   *  4. 留痕：新增/修改/移除全部记字段级履历，列表里点「详情 → 变更履历」可查。
   *
   * ★ 权限只用 `land:data:land` 一个码，挂在「新增 / 批量移除 / 行内编辑 / 行内移除」这些
   *   **写操作**上；查询与详情不加 v-has。后端也是同一个码（见 LandAdminController 的注释：
   *   「能进这个录入页的人就能录入」，拆细只增加授权负担，真正的边界是菜单能不能打开）。
   *
   * ★ 为什么「批量移除」放在页头而不是工具栏：
   *   它靠表格的勾选驱动，禁用态（未勾选）本身就是提示；放在页头与「新增宗地」并列，
   *   两个写操作入口在同一处，用户不用先去表格上方找工具栏。
   */
  export default {
    name: 'LandEntry',
    components: { LandFormModal, LandDetailModal, LandHistoryModal },
    data () {
      return {
        loading: false,
        rows: [],
        total: 0,
        selectedRowKeys: [],
        expanded: false,
        // ---- 查询条件 ----
        query: this.buildEmptyQuery(),
        crsjRange: [],
        hasFacilityOptions: [
          { value: '', label: '全部' },
          { value: 'true', label: '有配套项目' },
          { value: 'false', label: '无配套项目（待落实）' }
        ],
        /**
         * 项目分类兜底：与 sql/data/01_data_dict.sql 的 land_project_type 逐字一致。
         * ★ 为什么要兜底：字典接口不可用时下拉会空掉，连「市级项目」都选不出来，
         *   查询条件就废了；而这两个值是后端写死校验的，写死在前端不会漂移。
         */
        xmflOptions: [
          { value: '市级项目', label: '市级项目' },
          { value: '区级项目', label: '区级项目' }
        ],
        xzqhOptions: [],
        // ---- 分页 ----
        pagination: {
          current: 1,
          pageSize: 10,
          total: 0,
          size: 'small',
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: total => `共 ${total} 条宗地`
        },
        columns: [
          { title: '出让宗地编号', dataIndex: 'crzdbh', width: 220, fixed: 'left', scopedSlots: { customRender: 'crzdbh' } },
          { title: '地块名称', dataIndex: 'dkmc', width: 180, customRender: text => text || '—' },
          { title: '行政区划', dataIndex: 'xzqh', width: 100, customRender: text => text || '—' },
          { title: '项目分类', dataIndex: 'xmfl', width: 110, scopedSlots: { customRender: 'xmfl' } },
          { title: '规划用地性质', dataIndex: 'ghydxz', width: 150, customRender: text => text || '—' },
          { title: '出让金（亿元）', dataIndex: 'crj', width: 120, align: 'right', scopedSlots: { customRender: 'crj' } },
          { title: '出让时间', dataIndex: 'crsj', width: 110, customRender: text => text || '—' },
          { title: '受让人', dataIndex: 'srr', width: 190, customRender: text => text || '—' },
          { title: '配套是否齐全', dataIndex: 'ptsfqq', width: 110, scopedSlots: { customRender: 'ptsfqq' } },
          { title: '备注', dataIndex: 'beizhu', width: 180, scopedSlots: { customRender: 'beizhu' } },
          { title: '操作', dataIndex: 'action', width: 170, fixed: 'right', scopedSlots: { customRender: 'action' } }
        ],
        // ---- 移除 ----
        removeVisible: false,
        removing: false,
        removeIds: [],
        removeLabel: '',
        removeReason: ''
      }
    },
    computed: {
      rowSelection () {
        return {
          selectedRowKeys: this.selectedRowKeys,
          onChange: this.handleSelectChange
        }
      },
      pageXmflCount () {
        return this.rows.filter(item => item.xmfl === '市级项目').length
      },
      statCards () {
        return [
          {
            key: 'total',
            label: '宗地总数',
            value: this.total,
            extra: '当前查询条件下的有效宗地（不含已移除）'
          },
          {
            key: 'page',
            label: '本页记录',
            value: this.rows.length,
            extra: `第 ${this.pagination.current} 页，每页 ${this.pagination.pageSize} 条`
          },
          {
            key: 'xmfl',
            label: '本页市级项目',
            value: this.pageXmflCount,
            extra: `本页区级项目 ${this.rows.length - this.pageXmflCount} 条（仅统计当前页）`
          },
          {
            key: 'selected',
            label: '已勾选',
            value: this.selectedRowKeys.length,
            extra: '可批量移除；移除是软删，可在「数据更新与移除」恢复'
          }
        ]
      }
    },
    created () {
      this.loadDicts()
    },
    mounted () {
      this.loadData()
    },
    methods: {
      buildEmptyQuery () {
        return {
          crzdbh: '',
          dkmc: '',
          xzqh: undefined,
          xmfl: undefined,
          ghydxz: '',
          srr: '',
          crsjBegin: undefined,
          crsjEnd: undefined,
          // '' 表示「不限」，查询时转成 undefined（后端拿不到 hasFacility 就不加这个条件）
          hasFacility: '',
          facilityKeyword: ''
        }
      },
      loadDicts () {
        getDictItems('land_xzqh').then(items => {
          if (items && items.length) {
            this.xzqhOptions = items.map(item => ({ value: item.value, label: item.label || item.text }))
          }
        }).catch(() => { /* 区划只是筛选项，取不到时不阻断查询 */ })
        getDictItems('land_project_type').then(items => {
          if (items && items.length) {
            this.xmflOptions = items.map(item => ({ value: item.value, label: item.label || item.text }))
          }
        }).catch(() => { /* 保留兜底的两个值 */ })
      },

      // ---------------- 查询 ----------------

      /**
       * 组装查询参数。
       *
       * ★ hasFacility 的三态：'' = 不限（必须转成 undefined，否则会被 compact 当成
       *   「有值」发给后端，后端 Boolean 解析空串会报 400）；'true' / 'false' 转布尔。
       */
      buildParams () {
        return {
          crzdbh: this.query.crzdbh,
          dkmc: this.query.dkmc,
          xzqh: this.query.xzqh,
          xmfl: this.query.xmfl,
          ghydxz: this.query.ghydxz,
          srr: this.query.srr,
          crsjBegin: this.query.crsjBegin,
          crsjEnd: this.query.crsjEnd,
          hasFacility: this.query.hasFacility === '' ? undefined : this.query.hasFacility === 'true',
          facilityKeyword: this.query.facilityKeyword,
          pageNo: this.pagination.current,
          pageSize: this.pagination.pageSize
        }
      },
      loadData () {
        this.loading = true
        queryLandPage(this.buildParams()).then(res => {
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
          this.$message.error((e && e.message) || '查询失败')
        }).finally(() => {
          this.loading = false
        })
      },
      handleSearch () {
        this.pagination.current = 1
        // 换条件后原来的勾选已经不属于当前结果集，继续留着「批量移除」会误删
        this.selectedRowKeys = []
        this.loadData()
      },
      handleReset () {
        this.query = this.buildEmptyQuery()
        this.crsjRange = []
        this.handleSearch()
      },
      handleCrsjChange (values) {
        this.query.crsjBegin = values && values.length === 2 ? values[0] : undefined
        this.query.crsjEnd = values && values.length === 2 ? values[1] : undefined
      },
      handleTableChange (pagination) {
        this.pagination.current = pagination.current
        this.pagination.pageSize = pagination.pageSize
        this.loadData()
      },
      refresh () {
        this.loadData()
      },
      handleSelectChange (keys) {
        this.selectedRowKeys = keys
      },
      /** 数值列：0 是合法值，不能和「没填」混为一谈 */
      numberText (value) {
        if (value === null || value === undefined || value === '') {
          return '—'
        }
        return value
      },

      // ---------------- 新增 / 编辑 / 详情 ----------------

      handleAdd () {
        this.$refs.formModal.open(null)
      },
      handleEdit (record) {
        this.$refs.formModal.open(record)
      },
      handleSaved () {
        this.loadData()
      },
      handleDetail (record) {
        this.$refs.detailModal.open(record.id)
      },
      handleHistory (payload) {
        // 详情弹窗不自己开履历（会变成弹窗里再弹一层），把 id 抛上来由页面打开
        this.$refs.historyModal.open(payload.id, payload.crzdbh)
      },

      // ---------------- 移除 ----------------

      handleRemove (record) {
        this.removeIds = [record.id]
        this.removeLabel = record.crzdbh || record.dkmc || ''
        this.removeReason = ''
        this.removeVisible = true
      },
      handleBatchRemove () {
        if (!this.selectedRowKeys.length) {
          return
        }
        this.removeIds = this.selectedRowKeys.slice()
        this.removeLabel = `${this.removeIds.length} 条宗地`
        this.removeReason = ''
        this.removeVisible = true
      },
      handleRemoveConfirm () {
        if (!this.removeIds.length) {
          this.removeVisible = false
          return
        }
        const reason = (this.removeReason || '').trim()
        this.removing = true
        if (this.removeIds.length === 1) {
          deleteLand(this.removeIds[0], reason).then(res => {
            if (!res.success) {
              this.$message.warning(res.message || '移除失败')
              return
            }
            this.$message.success(res.message || `已移除「${this.removeLabel}」`)
            this.finishRemove()
          }).catch(e => {
            this.$message.error((e && e.message) || '移除失败')
          }).finally(() => {
            this.removing = false
          })
          return
        }
        deleteLandBatch(this.removeIds, reason).then(res => {
          if (!res.success) {
            this.$message.warning(res.message || '批量移除失败')
            return
          }
          const detail = res.result || {}
          if (detail.failCount) {
            // ★ 批量移除是「逐条软删 + 逐条留痕」，允许部分成功；
            //   必须把失败条数说清楚，否则用户以为整批都移除了
            this.$warning({
              title: `部分移除失败：成功 ${detail.successCount || 0} 条、失败 ${detail.failCount} 条`,
              content: (detail.failures || []).map(item => item.reason).join('；') || '请刷新后重试'
            })
            this.finishRemove()
            return
          }
          this.$message.success(res.message || `已移除 ${detail.successCount || this.removeIds.length} 条宗地`)
          this.finishRemove()
        }).catch(e => {
          this.$message.error((e && e.message) || '批量移除失败')
        }).finally(() => {
          this.removing = false
        })
      },
      finishRemove () {
        this.removeVisible = false
        this.removeIds = []
        this.removeReason = ''
        this.selectedRowKeys = []
        this.loadData()
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;
  @primary: #2e7cf6;

  .land-entry {
    /*
     * ★ 用 /deep/ 而不是 :deep()：本工程是 Vue2 + vue-loader 15，
     *   :deep() 是 Vue3 的写法，在 less 编译阶段不会被识别成深度选择器，
     *   这条 padding 规则会静默失效（表现为卡片内边距忽大忽小）。
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

    .entry-stats {
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

    .land-search {
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

    &__link {
      font-family: 'Consolas', 'Monaco', monospace;
      color: @primary;
    }

    &__danger {
      color: #cf1322;
    }

    &__remark {
      font-size: 12px;
      color: @text-muted;
    }

    .remove-form {
      margin-top: 12px;

      &__label {
        margin-bottom: 6px;
        font-size: 13px;
        color: #0f172a;
      }

      &__optional {
        margin-left: 6px;
        font-size: 12px;
        color: @text-weak;
      }
    }
  }
</style>
