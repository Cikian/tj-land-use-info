<template>
  <a-table
    :rowKey="'id'"
    :columns="columns"
    :dataSource="dataSource"
    :loading="loading"
    :pagination="pagination"
    :rowSelection="selectable ? { selectedRowKeys: selectedRowKeys, onChange: handleSelectChange } : undefined"
    :scroll="{ x: 2300, y: scrollY }"
    size="middle"
    @change="handleTableChange">
    <!-- 序号：由后端按当前查询条件连续编号（分页偏移 + 行号） -->
    <template slot="seq" slot-scope="text">
      <span class="completion-table__seq">{{ text || '-' }}</span>
    </template>

    <template slot="archiveNo" slot-scope="text, record">
      <a class="completion-table__no" @click="$emit('detail', record)">{{ text }}</a>
    </template>

    <template slot="projectName" slot-scope="text, record">
      <div class="completion-table__name" :title="text">{{ text }}</div>
      <div v-if="record.projectCode" class="completion-table__sub">项目编号：{{ record.projectCode }}</div>
    </template>

    <template slot="investAmount" slot-scope="text">
      <span v-if="text || text === 0" class="completion-table__num">{{ formatAmount(text) }}</span>
      <span v-else class="completion-table__muted">—</span>
    </template>

    <template slot="digitizeStatus" slot-scope="text, record">
      <a-tag :color="digitizeStatusColor(text)">{{ text || '未数字化' }}</a-tag>
      <!-- 「已数字化但没挂扫描件」是最需要业务注意的矛盾记录，这里直接标出来 -->
      <a-tooltip v-if="isContradictory(record)" title="状态已数字化，但还没有关联任何扫描件档案，请核查">
        <a-icon class="completion-table__warn" type="warning" />
      </a-tooltip>
    </template>

    <template slot="digitizeProgress" slot-scope="text, record">
      <a-progress
        :percent="percentOf(record)"
        :strokeColor="dotColor(record.digitizeStatus)"
        size="small" />
    </template>

    <template slot="pageCount" slot-scope="text">
      <span v-if="text || text === 0" class="completion-table__num">{{ formatCount(text) }}</span>
      <span v-else class="completion-table__muted">—</span>
    </template>

    <template slot="scanDpi" slot-scope="text">
      <span v-if="text">{{ text }} DPI</span>
      <span v-else class="completion-table__muted">—</span>
    </template>

    <template slot="archiveCount" slot-scope="text, record">
      <span v-if="text" class="completion-table__link" @click="$emit('link', record)">
        {{ text }} 份<template v-if="record.archiveId">（已关联）</template>
      </span>
      <a v-else class="completion-table__muted-link" @click="$emit('link', record)">未挂扫描件</a>
    </template>

    <template slot="action" slot-scope="text, record">
      <a v-has="'land:completion:list'" @click="$emit('detail', record)">详情</a>
      <template v-if="!readonly">
        <span v-has="'land:completion:edit'">
          <a-divider type="vertical" />
          <a @click="$emit('link', record)">关联扫描件</a>
          <a-divider type="vertical" />
          <a @click="$emit('edit', record)">编辑</a>
        </span>
        <span v-has="'land:completion:delete'">
          <a-divider type="vertical" />
          <a-popconfirm
            title="删除后不可恢复（不影响档案与配套项目），确定删除该历史档案吗？"
            okText="确定"
            cancelText="取消"
            @confirm="$emit('remove', record)">
            <a class="completion-table__danger">删除</a>
          </a-popconfirm>
        </span>
      </template>
    </template>
  </a-table>
</template>

<script>
  import {
    archiveStatusColor,
    digitizeDotColor,
    digitizePercent,
    digitizeStatusColor,
    formatAmount,
    formatCount
  } from '@/api/land/completion'

  /**
   * 竣工验收项目历史工程资料数字化档案 - 高密度档案表格
   *
   * 列表列按「一盘家底」的顺序排：
   *   标识（序号/档案编号）→ 项目（名称/行政区/类型/参建单位）→ 时间（竣工/验收）
   *   → 投资与保管 → ★数字化（状态/进度条/页数/DPI/扫描件数）→ 操作。
   *
   * 两个业务友好的细节：
   *   1. 「数字化状态」列对「已数字化但没挂扫描件」的矛盾记录打黄色警告图标
   *      （这是历史档案数字化推进中最常见的假完成）；
   *   2. 「扫描件」列直接显示「N 份（已关联）」或「未挂扫描件」，点一下就去关联。
   *
   * 档案页与查询页共用本组件：查询页传 readonly 隐藏编辑/删除入口。
   */
  export default {
    name: 'CompletionTable',
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
      /** 只读模式：隐藏关联扫描件/编辑/删除入口 */
      readonly: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        columns: [
          { title: '序号', dataIndex: 'seq', width: 64, fixed: 'left', scopedSlots: { customRender: 'seq' } },
          { title: '档案编号', dataIndex: 'archiveNo', width: 150, fixed: 'left', scopedSlots: { customRender: 'archiveNo' } },
          { title: '历史项目名称', dataIndex: 'projectName', width: 260, ellipsis: true, scopedSlots: { customRender: 'projectName' } },
          { title: '行政区划', dataIndex: 'xzqh', width: 96, customRender: text => text || '—' },
          { title: '项目类型', dataIndex: 'projectType', width: 110, customRender: text => text || '—' },
          { title: '建设单位', dataIndex: 'buildUnit', width: 180, ellipsis: true, customRender: text => text || '—' },
          { title: '施工单位', dataIndex: 'constructUnit', width: 180, ellipsis: true, customRender: text => text || '—' },
          { title: '竣工日期', dataIndex: 'completeDate', width: 108, customRender: text => text || '—' },
          { title: '验收日期', dataIndex: 'acceptanceDate', width: 108, customRender: text => text || '—' },
          { title: '投资额(万元)', dataIndex: 'investAmount', width: 120, align: 'right', scopedSlots: { customRender: 'investAmount' } },
          { title: '保管期限', dataIndex: 'retention', width: 100, customRender: text => text || '—' },
          { title: '数字化状态', dataIndex: 'digitizeStatus', width: 130, scopedSlots: { customRender: 'digitizeStatus' } },
          { title: '数字化进度', dataIndex: 'digitizePercent', width: 130, scopedSlots: { customRender: 'digitizeProgress' } },
          { title: '总页数', dataIndex: 'pageCount', width: 100, align: 'right', scopedSlots: { customRender: 'pageCount' } },
          { title: '扫描分辨率', dataIndex: 'scanDpi', width: 110, scopedSlots: { customRender: 'scanDpi' } },
          { title: '扫描件', dataIndex: 'archiveCount', width: 130, scopedSlots: { customRender: 'archiveCount' } },
          { title: '操作', width: 210, fixed: 'right', scopedSlots: { customRender: 'action' } }
        ]
      }
    },
    methods: {
      digitizeStatusColor,
      archiveStatusColor,
      formatAmount,
      formatCount,
      handleTableChange (pagination) {
        this.$emit('change', pagination)
      },
      handleSelectChange (selectedRowKeys, selectedRows) {
        this.$emit('select-change', selectedRowKeys, selectedRows)
      },
      /** 进度百分比：优先用后端算好的 digitizePercent，取不到再本地折算（口径一致） */
      percentOf (record) {
        if (!record) {
          return 0
        }
        const fromServer = record.digitizePercent
        if (fromServer !== null && fromServer !== undefined) {
          return Number(fromServer)
        }
        return digitizePercent(record.digitizeStatus)
      },
      dotColor (status) {
        return digitizeDotColor(status)
      },
      /** 矛盾记录：状态已数字化，但一个扫描件都没挂 */
      isContradictory (record) {
        return !!record && record.digitizeStatus === '已数字化' && !record.archiveId
      }
    }
  }
</script>

<style lang="less" scoped>
  .completion-table {
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

    &__sub {
      margin-top: 2px;
      font-size: 12px;
      color: #94a3b8;
    }

    &__num {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      color: #0f172a;
    }

    &__warn {
      margin-left: 4px;
      color: #d48806;
    }

    &__link {
      color: #2e7cf6;
      cursor: pointer;
    }

    &__muted-link {
      color: #94a3b8;

      &:hover {
        color: #2e7cf6;
      }
    }

    &__muted {
      color: #94a3b8;
      font-size: 12px;
    }

    &__danger {
      color: #ff4d4f;
    }

    /deep/ .ant-progress-line {
      margin: 0;
      font-size: 0;
    }

    /deep/ .ant-progress-text {
      font-size: 12px;
    }
  }
</style>
