<template>
  <a-table
    :rowKey="'id'"
    :columns="columns"
    :dataSource="dataSource"
    :loading="loading"
    :pagination="pagination"
    :size="mode === 'ledger' ? 'small' : 'middle'"
    :rowSelection="selectable ? { selectedRowKeys: selectedRowKeys, onChange: handleSelectChange } : undefined"
    :scroll="{ x: mode === 'ledger' ? 2180 : 1560 }"
    @change="handleTableChange">
    <!-- 序号（台账列） -->
    <template slot="index" slot-scope="text, record, index">{{ index + 1 }}</template>

    <!-- 项目编号：点击进详情 -->
    <template slot="projectNo" slot-scope="text, record">
      <a class="escalation-table__no" @click="$emit('detail', record)">{{ text }}</a>
    </template>

    <!-- 项目名称 + 申报单位副行（台账列） -->
    <template slot="projectName" slot-scope="text, record">
      <div class="escalation-table__name" :title="text">{{ text }}</div>
      <div v-if="mode === 'ledger' && record.buildLocation" class="escalation-table__sub">
        建设地点：{{ record.buildLocation }}
      </div>
    </template>

    <!-- 项目类型（字典文本，后端给了 _dictText 优先用） -->
    <template slot="projectType" slot-scope="text, record">
      {{ dictLabel(DICT.projectType, text, record.projectType_dictText) || '—' }}
    </template>

    <!-- 总投资（亿元） -->
    <template slot="totalInvestment" slot-scope="text">
      <span v-if="text === null || text === undefined || text === ''" class="escalation-table__muted">—</span>
      <span v-else class="escalation-table__number">{{ formatInvestment(text) }}</span>
    </template>

    <!-- 办理状态 -->
    <template slot="status" slot-scope="text, record">
      <a-tag :color="statusColor(text)">{{ text }}</a-tag>
      <!-- 设计文档 10-Risk7：已办结但没登记论证结果时给黄色提示（轻量校验，不强制） -->
      <a-tooltip v-if="text === '已办结' && !record.argResult" title="已办结但未登记论证结果">
        <a-icon type="warning" class="escalation-table__warn" />
      </a-tooltip>
    </template>

    <!-- 论证结果（与办理状态分开的独立标签） -->
    <template slot="argResult" slot-scope="text, record">
      <a-tag v-if="text" :color="argResultColor(text)">{{ dictLabel(DICT.argResult, text, record.argResult_dictText) }}</a-tag>
      <span v-else class="escalation-table__muted">未登记</span>
    </template>

    <!-- 材料数 -->
    <template slot="materialCount" slot-scope="text">
      <span class="escalation-table__number">{{ text || 0 }}</span>
    </template>

    <!-- 最新审核意见摘要 -->
    <template slot="latestOpinion" slot-scope="text, record">
      <div v-if="text" class="escalation-table__opinion" :title="text">{{ opinionSummary(text, opinionLimit) }}</div>
      <span v-else class="escalation-table__muted">暂无</span>
      <div v-if="record.latestOpinionBy || record.latestOpinionTime" class="escalation-table__sub">
        {{ joinInfo(record.latestOpinionBy, record.latestOpinionTime) }}
      </div>
    </template>

    <template slot="updateTime" slot-scope="text, record">
      {{ text || record.createTime || '—' }}
    </template>

    <template slot="action" slot-scope="text, record">
      <template v-if="mode === 'ledger'">
        <a v-has="'land:escalation:list'" @click="$emit('detail', record)">查看</a>
        <span v-has="'land:escalation:download'">
          <a-divider type="vertical" />
          <a @click="$emit('materials', record)">材料下载</a>
        </span>
        <a-divider type="vertical" />
        <a @click="$emit('print', record)">打印</a>
      </template>
      <template v-else>
        <a v-has="'land:escalation:list'" @click="$emit('detail', record)">详情</a>
        <span v-if="!readonly" v-has="'land:escalation:edit'">
          <a-divider type="vertical" />
          <a @click="$emit('edit', record)">编辑</a>
        </span>
        <span v-if="!readonly" v-has="'land:escalation:delete'">
          <a-divider type="vertical" />
          <a-popconfirm title="删除后不可恢复，确定删除该项目吗？" okText="确定" cancelText="取消" @confirm="$emit('delete', record)">
            <a class="escalation-table__danger">删除</a>
          </a-popconfirm>
        </span>
      </template>
    </template>
  </a-table>
</template>

<script>
  import {
    DICT,
    statusColor,
    argResultColor,
    dictText,
    opinionSummary
  } from '@/api/land/escalation'

  /**
   * 提级论证项目表格（录入 / 查询 / 台账三个页面共用）
   *
   * mode = 'default'：查询统计与录入页的 10 列（设计文档 5.5「列表列」）；
   * mode = 'ledger' ：台账页的 14 列（设计文档 5.6，冻结前 3 列：序号 / 项目编号 / 项目名称）。
   *
   * ★ 标签用色见设计文档 5.7：a-tag 的 color 只允许 antd 预设色，
   *   「未办理」没有强调色时返回 undefined 走默认灰，绝不写 'default'。
   */
  export default {
    name: 'EscalationTable',
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
      selectable: {
        type: Boolean,
        default: false
      },
      selectedRowKeys: {
        type: Array,
        default: () => []
      },
      /** 只读模式（查询页）：隐藏编辑与删除 */
      readonly: {
        type: Boolean,
        default: false
      },
      /** 'default' 列表 | 'ledger' 台账 14 列 */
      mode: {
        type: String,
        default: 'default'
      },
      /** 台账「最新审核意见摘要」列的截断长度 */
      opinionLimit: {
        type: Number,
        default: 40
      }
    },
    data () {
      return {
        DICT
      }
    },
    computed: {
      columns () {
        if (this.mode === 'ledger') {
          return [
            { title: '序号', width: 60, align: 'center', fixed: 'left', scopedSlots: { customRender: 'index' } },
            { title: '项目编号', dataIndex: 'projectNo', width: 150, fixed: 'left', scopedSlots: { customRender: 'projectNo' } },
            { title: '项目名称', dataIndex: 'projectName', width: 240, fixed: 'left', ellipsis: true, scopedSlots: { customRender: 'projectName' } },
            { title: '申报单位', dataIndex: 'declareDept', width: 200, ellipsis: true, customRender: text => text || '—' },
            { title: '行政区划', dataIndex: 'xzqh', width: 100, customRender: (text, record) => record.xzqh || record.gnq || '—' },
            { title: '项目类型', dataIndex: 'projectType', width: 120, scopedSlots: { customRender: 'projectType' } },
            { title: '总投资（亿元）', dataIndex: 'totalInvestment', width: 120, align: 'right', scopedSlots: { customRender: 'totalInvestment' } },
            { title: '申报时间', dataIndex: 'declareDate', width: 110, customRender: text => text || '—' },
            { title: '材料数', dataIndex: 'materialCount', width: 80, align: 'right', scopedSlots: { customRender: 'materialCount' } },
            { title: '办理状态', dataIndex: 'status', width: 110, scopedSlots: { customRender: 'status' } },
            { title: '论证结果', dataIndex: 'argResult', width: 130, scopedSlots: { customRender: 'argResult' } },
            { title: '最新审核意见摘要', dataIndex: 'latestOpinion', width: 260, scopedSlots: { customRender: 'latestOpinion' } },
            { title: '最近更新时间', dataIndex: 'updateTime', width: 160, scopedSlots: { customRender: 'updateTime' } },
            { title: '操作', width: 190, fixed: 'right', scopedSlots: { customRender: 'action' } }
          ]
        }
        return [
          { title: '项目编号', dataIndex: 'projectNo', width: 150, fixed: 'left', scopedSlots: { customRender: 'projectNo' } },
          { title: '项目名称', dataIndex: 'projectName', width: 240, fixed: 'left', ellipsis: true, scopedSlots: { customRender: 'projectName' } },
          { title: '申报单位', dataIndex: 'declareDept', width: 190, ellipsis: true, customRender: text => text || '—' },
          { title: '项目类型', dataIndex: 'projectType', width: 120, scopedSlots: { customRender: 'projectType' } },
          { title: '总投资（亿元）', dataIndex: 'totalInvestment', width: 120, align: 'right', scopedSlots: { customRender: 'totalInvestment' } },
          { title: '申报时间', dataIndex: 'declareDate', width: 110, customRender: text => text || '—' },
          { title: '办理状态', dataIndex: 'status', width: 110, scopedSlots: { customRender: 'status' } },
          { title: '论证结果', dataIndex: 'argResult', width: 130, scopedSlots: { customRender: 'argResult' } },
          { title: '材料数', dataIndex: 'materialCount', width: 80, align: 'right', scopedSlots: { customRender: 'materialCount' } },
          { title: '操作', width: 170, fixed: 'right', scopedSlots: { customRender: 'action' } }
        ]
      }
    },
    methods: {
      statusColor,
      argResultColor,
      handleTableChange (pagination) {
        this.$emit('change', pagination)
      },
      handleSelectChange (selectedRowKeys, selectedRows) {
        this.$emit('select-change', selectedRowKeys, selectedRows)
      },
      /** 字典文本：后端 @Dict 给的 _dictText 优先，其次查本地字典缓存，最后原样返回 */
      dictLabel (dictCode, value, dictTextFromServer) {
        if (!value) {
          return ''
        }
        return dictTextFromServer || dictText(dictCode, value) || value
      },
      formatInvestment (value) {
        const num = Number(value)
        if (isNaN(num)) {
          return value
        }
        return num.toFixed(4).replace(/\.?0+$/, '')
      },
      joinInfo (a, b) {
        const parts = [a, b].filter(v => v !== null && v !== undefined && v !== '')
        return parts.length ? parts.join(' · ') : ''
      },
      opinionSummary
    }
  }
</script>

<style lang="less" scoped>
  .escalation-table {
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

    &__sub {
      font-size: 12px;
      color: #94a3b8;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__opinion {
      color: #334155;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__number {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      color: #0f172a;
    }

    &__muted {
      color: #94a3b8;
    }

    &__warn {
      margin-left: 6px;
      color: #d48806;
    }

    &__danger {
      color: #ff4d4f;
    }
  }
</style>
