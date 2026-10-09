<template>
  <a-table
    :rowKey="'id'"
    :columns="columns"
    :dataSource="dataSource"
    :loading="loading"
    :pagination="pagination"
    :rowSelection="selectable ? { selectedRowKeys: selectedRowKeys, onChange: handleSelectChange } : undefined"
    :scroll="{ x: scrollX, y: scrollY }"
    size="middle"
    @change="handleTableChange">
    <!-- 序号：由后端按当前查询条件连续编号（分页偏移 + 行号） -->
    <template slot="seq" slot-scope="text">
      <span class="ledger-table__seq">{{ text || '-' }}</span>
    </template>

    <template slot="ledgerNo" slot-scope="text, record">
      <a class="ledger-table__no" @click="$emit('detail', record)">{{ text }}</a>
    </template>

    <template slot="roadName" slot-scope="text, record">
      <div class="ledger-table__name" :title="text">{{ text }}</div>
      <div v-if="isMigratedOrphan(record)" class="ledger-table__warn">
        <a-icon type="warning" /> 出让宗地在宗地表里没有匹配记录
      </div>
      <div v-else-if="warningOf(record)" class="ledger-table__warn">
        <a-icon type="warning" /> {{ warningOf(record) }}
      </div>
    </template>

    <template slot="xzqh" slot-scope="text, record">
      <span v-if="text">{{ text }}</span>
      <span v-else-if="record.gnq" class="ledger-table__muted">{{ record.gnq }}</span>
      <span v-else class="ledger-table__muted">—</span>
    </template>

    <template slot="acceptanceResult" slot-scope="text">
      <a-tag v-if="text" :color="acceptanceResultColor(text)">{{ text }}</a-tag>
      <span v-else class="ledger-table__muted">—</span>
    </template>

    <!-- ★13 列资料矩阵：仅在「显示资料矩阵」开关打开时出现 -->
    <template slot="materialFlag" slot-scope="text">
      <a-icon v-if="Number(text) === 1" type="check-circle" class="ledger-table__yes" />
      <a-icon v-else type="close-circle" class="ledger-table__no-flag" />
    </template>

    <template slot="materialCount" slot-scope="text, record">
      <a-tooltip :title="materialTip(record)">
        <div class="ledger-table__material">
          <span class="ledger-table__material-num" :class="materialClass(record)">
            {{ materialSummary(record) }}
          </span>
          <a-progress
            :percent="materialPercent(record)"
            :showInfo="false"
            :strokeColor="materialColor(record)"
            size="small" />
        </div>
      </a-tooltip>
    </template>

    <template slot="status" slot-scope="text, record">
      <a-tag :color="statusColor(text)">{{ text || '未验收' }}</a-tag>
      <!-- 状态变更：行内下拉，走 POST /land/archive/ledger/status -->
      <a-dropdown v-if="!readonly" :trigger="['click']">
        <a v-has="'land:ledger:edit'" class="ledger-table__status-change">
          变更 <a-icon type="down" />
        </a>
        <a-menu slot="overlay" @click="args => handleStatusChange(record, args)">
          <a-menu-item v-for="item in statuses" :key="item" :disabled="item === record.status">
            {{ item }}
          </a-menu-item>
        </a-menu>
      </a-dropdown>
    </template>

    <template slot="action" slot-scope="text, record">
      <a v-has="'land:ledger:list'" @click="$emit('detail', record)">详情</a>
      <span>
        <a-divider type="vertical" />
        <a @click="$emit('print', record)">打印</a>
      </span>
      <template v-if="!readonly">
        <span v-has="'land:ledger:archive'">
          <a-divider type="vertical" />
          <a @click="$emit('link', record)">关联档案</a>
        </span>
        <span v-has="'land:ledger:edit'">
          <a-divider type="vertical" />
          <a @click="$emit('edit', record)">编辑</a>
        </span>
        <span v-has="'land:ledger:delete'">
          <a-divider type="vertical" />
          <a-popconfirm
            title="删除后不可恢复（不影响档案与配套项目），确定删除该台账吗？"
            okText="确定"
            cancelText="取消"
            @confirm="$emit('remove', record)">
            <a class="ledger-table__danger">删除</a>
          </a-popconfirm>
        </span>
      </template>
    </template>
  </a-table>
</template>

<script>
  import {
    LEDGER_MATERIALS_FALLBACK,
    LEDGER_STATUS,
    MATERIAL_TOTAL,
    acceptanceResultColor,
    materialSummary,
    missingMaterialLabels,
    statusColor,
    statusMaterialWarning
  } from '@/api/land/ledger'

  /**
   * 道路设施验收及移交资料台账 - 高密度台账表格
   *
   * 清单 §6.3.7 要求：「这是台账性质，所以表格密度要高，字段以是否有某资料的勾选矩阵为主」。
   * 本组件用两种形态同时满足这两个要求：
   *   · 默认形态：`size="middle"` + 横向滚动，把台账列一次排开不换行；
   *     「资料」列显示「已归集 x/13」+ 进度条，鼠标悬停给出「还缺哪几类」；
   *   · **矩阵形态**（`showMaterials`）：在「资料」列前插入 **13 列逐项勾选√/×**，
   *     用于「一眼核对哪类资料普遍没交」的场景。列很宽，所以做成开关而不是默认打开；
   *     13 列定义来自 `materials` 属性（页面从后端 /materials 拉取），本地常量兜底。
   *
   * 另外两处台账专属交互：
   *   · **状态变更**（行内下拉，4 个状态）—— 台账是人工登记的，状态改起来必须一步到位；
   *   · **一致性提示** —— 状态与资料自相矛盾时（如「已移交」却没有移交文件）在道路名称下方黄色提示，
   *     规则见 `statusMaterialWarning`。只提示不阻断（线下先移交、后补资料是常态）。
   *
   * 台账页与查询页共用本组件：查询页传 readonly 隐藏编辑/删除/关联/状态变更入口。
   */
  export default {
    name: 'LedgerTable',
    props: {
      dataSource: {
        type: Array,
        default: () => []
      },
      loading: {
        type: Boolean,
        default: false
      },
      pagination: {
        type: [Object, Boolean],
        default: () => ({})
      },
      /**
       * 表体滚动高度（px），由父页面按「视口剩余高度」算好后传入。
       * ★ 不设它时 antd 不限制表体高度 → 整页随行数变高，
       *   鼠标滚轮滚的是整个文档，检索条件、指标卡、表头全被滚走（用户实际反馈的问题）。
       *   设了它以后只有表体滚动，表头固定。
       */
      scrollY: {
        type: Number,
        default: 520
      },
      selectable: {
        type: Boolean,
        default: false
      },
      selectedRowKeys: {
        type: Array,
        default: () => []
      },
      /** 只读模式：隐藏关联档案/编辑/删除/状态变更入口 */
      readonly: {
        type: Boolean,
        default: false
      },
      /** 是否展开 13 列资料矩阵（默认只显示「资料 x/13」汇总列） */
      showMaterials: {
        type: Boolean,
        default: false
      },
      /** 13 类资料定义（页面从后端拉取；不传则用本地兜底） */
      materials: {
        type: Array,
        default: () => LEDGER_MATERIALS_FALLBACK
      }
    },
    data () {
      return {
        statuses: LEDGER_STATUS
      }
    },
    computed: {
      /** 13 列矩阵展开后会宽很多，滚动宽度随之变化 */
      scrollX () {
        return this.showMaterials ? 3400 : 2200
      },
      columns () {
        const columns = [
          { title: '序号', dataIndex: 'seq', width: 64, fixed: 'left', scopedSlots: { customRender: 'seq' } },
          { title: '台账编号', dataIndex: 'ledgerNo', width: 140, fixed: 'left', scopedSlots: { customRender: 'ledgerNo' } },
          { title: '道路名称', dataIndex: 'roadName', width: 260, ellipsis: true, scopedSlots: { customRender: 'roadName' } },
          { title: '行政区划', dataIndex: 'xzqh', width: 96, scopedSlots: { customRender: 'xzqh' } },
          { title: '出让宗地编号', dataIndex: 'crzdbh', width: 180, ellipsis: true, customRender: text => text || '—' },
          { title: '地块名称', dataIndex: 'dkmc', width: 180, ellipsis: true, customRender: text => text || '—' },
          { title: '设施类别', dataIndex: 'ptsslb', width: 100, customRender: text => text || '—' },
          { title: '验收类型', dataIndex: 'acceptanceType', width: 110, customRender: text => text || '—' },
          { title: '验收日期', dataIndex: 'acceptanceDate', width: 108, customRender: text => text || '—' },
          { title: '验收结果', dataIndex: 'acceptanceResult', width: 100, scopedSlots: { customRender: 'acceptanceResult' } },
          { title: '竣工日期', dataIndex: 'completeDate', width: 108, customRender: text => text || '—' },
          { title: '移交日期', dataIndex: 'handoverDate', width: 108, customRender: text => text || '—' },
          { title: '接收管养单位', dataIndex: 'receiveUnit', width: 150, ellipsis: true, customRender: text => text || '—' }
        ]
        if (this.showMaterials) {
          this.materials.forEach(item => {
            columns.push({
              title: `${this.padSeq(item.seq)} ${item.label}`,
              dataIndex: item.key,
              width: 104,
              scopedSlots: { customRender: 'materialFlag' }
            })
          })
        }
        columns.push(
          { title: '资料', dataIndex: 'materialCount', width: 130, scopedSlots: { customRender: 'materialCount' } },
          { title: '状态', dataIndex: 'status', width: 130, scopedSlots: { customRender: 'status' } },
          { title: '操作', width: 230, fixed: 'right', scopedSlots: { customRender: 'action' } }
        )
        return columns
      }
    },
    methods: {
      statusColor,
      acceptanceResultColor,
      materialSummary,
      handleTableChange (pagination) {
        this.$emit('change', pagination)
      },
      handleSelectChange (selectedRowKeys, selectedRows) {
        this.$emit('select-change', selectedRowKeys, selectedRows)
      },
      /** a-menu 的 click 回调参数是 { key, keyPath, item, domEvent } */
      handleStatusChange (record, args) {
        const status = args && args.key ? args.key : args
        if (!status || status === record.status) {
          return
        }
        this.$emit('status-change', record, status)
      },
      /** 迁移脚本留下的「宗地未匹配」提示（remark 以「迁移提示」开头） */
      isMigratedOrphan (record) {
        return !!(record && record.remark && record.remark.indexOf('迁移提示') === 0)
      },
      /** 状态与资料的一致性提示（迁移孤儿优先显示，两者不叠加） */
      warningOf (record) {
        return statusMaterialWarning(record, this.materials)
      },
      /** 资料进度百分比 */
      materialPercent (record) {
        const count = record && record.materialCount ? Number(record.materialCount) : 0
        return Math.round((count / MATERIAL_TOTAL) * 100)
      },
      materialColor (record) {
        const count = record && record.materialCount ? Number(record.materialCount) : 0
        if (count >= MATERIAL_TOTAL) {
          return '#10b981'
        }
        if (count === 0) {
          return '#ef4444'
        }
        return '#2e7cf6'
      },
      materialClass (record) {
        const count = record && record.materialCount ? Number(record.materialCount) : 0
        if (count >= MATERIAL_TOTAL) {
          return 'is-full'
        }
        return count === 0 ? 'is-empty' : ''
      },
      /** 悬停提示：已归集与还缺哪几类 */
      materialTip (record) {
        const missing = missingMaterialLabels(record)
        if (!missing.length) {
          return `13 类资料已全部归集（${MATERIAL_TOTAL}/${MATERIAL_TOTAL}）`
        }
        return `已归集 ${MATERIAL_TOTAL - missing.length}/${MATERIAL_TOTAL}，还缺：${missing.join('、')}`
      },
      padSeq (seq) {
        return seq < 10 ? `0${seq}` : `${seq}`
      }
    }
  }
</script>

<style lang="less" scoped>
  .ledger-table {
    &__seq {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      color: #94a3b8;
    }

    &__no {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      letter-spacing: 0.5px;
    }

    &__name {
      font-weight: 500;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__warn {
      margin-top: 2px;
      font-size: 12px;
      line-height: 16px;
      color: #d48806;
    }

    &__muted {
      color: #94a3b8;
      font-size: 12px;
    }

    &__danger {
      color: #ff4d4f;
    }

    &__status-change {
      margin-left: 6px;
      font-size: 12px;
    }

    &__yes {
      color: #10b981;
    }

    &__no-flag {
      color: #d9d9d9;
    }

    &__material {
      display: flex;
      flex-direction: column;
      line-height: 1.2;
    }

    &__material-num {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 13px;
      color: #2e7cf6;

      &.is-full {
        color: #10b981;
      }

      &.is-empty {
        color: #ef4444;
      }
    }

    /deep/ .ant-progress-line {
      margin: 2px 0 0;
      font-size: 0;
    }
  }
</style>
