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
    <template slot="seq" slot-scope="text">
      <span class="handover-table__seq">{{ text || '-' }}</span>
    </template>

    <template slot="handoverNo" slot-scope="text, record">
      <a class="handover-table__no" @click="$emit('detail', record)">{{ text }}</a>
    </template>

    <template slot="roadName" slot-scope="text, record">
      <div class="handover-table__name" :title="text">{{ text }}</div>
      <div v-if="missingAgreement(record)" class="handover-table__warn">
        <a-icon type="warning" /> 协议信息待补录
      </div>
      <div v-else-if="record.remark && record.remark.indexOf('无匹配记录') >= 0" class="handover-table__warn">
        <a-icon type="warning" /> 出让宗地待核对
      </div>
    </template>

    <template slot="startEnd" slot-scope="text, record">
      <span v-if="record.startPoint || record.endPoint">
        {{ record.startPoint || '—' }} ~ {{ record.endPoint || '—' }}
      </span>
      <span v-else class="handover-table__muted">—</span>
    </template>

    <template slot="roadLength" slot-scope="text, record">
      <span v-if="record.lengthM !== null && record.lengthM !== undefined">{{ record.lengthM }} m</span>
      <span v-else class="handover-table__muted">—</span>
      <span v-if="record.redLineWidth !== null && record.redLineWidth !== undefined" class="handover-table__muted">
        / 红线 {{ record.redLineWidth }} m
      </span>
    </template>

    <template slot="xzqh" slot-scope="text, record">
      <span v-if="text">{{ text }}</span>
      <span v-else-if="record.gnq" class="handover-table__muted">{{ record.gnq }}</span>
      <span v-else class="handover-table__muted">—</span>
    </template>

    <template slot="handoverType" slot-scope="text">
      <a-tag v-if="text" color="blue">{{ text }}</a-tag>
      <span v-else class="handover-table__muted">—</span>
    </template>

    <template slot="agreement" slot-scope="text, record">
      <div v-if="record.agreementNo">{{ record.agreementNo }}</div>
      <div v-else class="handover-table__warn">待补录</div>
      <div v-if="record.agreementDate" class="handover-table__muted">{{ record.agreementDate }}</div>
    </template>

    <template slot="maintenance" slot-scope="text, record">
      <div v-if="record.maintenanceStart || record.maintenanceEnd">
        {{ record.maintenanceStart || '—' }} ~ {{ record.maintenanceEnd || '—' }}
      </div>
      <div v-else class="handover-table__muted">—</div>
      <div v-if="maintenanceWarning(record)" class="handover-table__warn">
        <a-icon type="clock-circle" /> {{ maintenanceWarning(record) }}
      </div>
    </template>

    <template slot="status" slot-scope="text, record">
      <a-tag :color="statusColor(text)">{{ text || '待移交' }}</a-tag>
      <a-dropdown v-if="!readonly" :trigger="['click']">
        <a v-has="'land:handover:edit'" class="handover-table__status-change">
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
      <a v-has="'land:handover:list'" @click="$emit('detail', record)">详情</a>
      <span>
        <a-divider type="vertical" />
        <a @click="$emit('print', record)">打印</a>
      </span>
      <template v-if="!readonly">
        <span v-has="'land:handover:archive'">
          <a-divider type="vertical" />
          <a @click="$emit('link', record)">关联档案</a>
        </span>
        <span v-has="'land:handover:edit'">
          <a-divider type="vertical" />
          <a @click="$emit('edit', record)">编辑</a>
        </span>
        <span v-has="'land:handover:delete'">
          <a-divider type="vertical" />
          <a-popconfirm
            title="删除后不可恢复（不影响档案与配套项目），确定删除该移交事项吗？"
            okText="确定"
            cancelText="取消"
            @confirm="$emit('remove', record)">
            <a class="handover-table__danger">删除</a>
          </a-popconfirm>
        </span>
      </template>
    </template>
  </a-table>
</template>

<script>
  import {
    HANDOVER_STATUS,
    maintenanceWarning,
    missingAgreement,
    statusColor
  } from '@/api/land/handover'

  /**
   * 道路交付及养护协议移交事项 - 列表表格
   *
   * 清单 §6.3.6 的结果列：移交编号 / 道路名称 / 道路等级 / 起终点 / 长度 / 行政区 /
   * 宗地编号 / 协议编号 / 建设单位 / 接收管养单位 / 协议日期 / 移交日期 / 状态 / 操作。
   *
   * 两处业务提示（只在行内以黄色小字提示，不阻断操作）：
   *  1. 「协议信息待补录」：迁移来的 84 条只有「是否移交=是」这一个依据，
   *     协议编号/日期全是空的 —— 不提示的话这批数据会一直「看起来已移交、实际没有协议凭证」；
   *  2. 养护期到期提醒：养护截止日期已过或 90 天内到期。
   */
  export default {
    name: 'HandoverTable',
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
      readonly: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        statuses: HANDOVER_STATUS,
        columns: [
          { title: '序号', dataIndex: 'seq', width: 60, fixed: 'left', scopedSlots: { customRender: 'seq' } },
          { title: '移交编号', dataIndex: 'handoverNo', width: 140, fixed: 'left', scopedSlots: { customRender: 'handoverNo' } },
          { title: '道路名称', dataIndex: 'roadName', width: 280, ellipsis: true, scopedSlots: { customRender: 'roadName' } },
          { title: '道路等级', dataIndex: 'dldj', width: 110, customRender: text => text || '—' },
          { title: '起终点', dataIndex: 'startEnd', width: 200, ellipsis: true, scopedSlots: { customRender: 'startEnd' } },
          { title: '长度 / 红线', dataIndex: 'roadLength', width: 150, scopedSlots: { customRender: 'roadLength' } },
          { title: '行政区划', dataIndex: 'xzqh', width: 96, scopedSlots: { customRender: 'xzqh' } },
          { title: '出让宗地编号', dataIndex: 'crzdbh', width: 180, ellipsis: true, customRender: text => text || '—' },
          { title: '地块名称', dataIndex: 'dkmc', width: 170, ellipsis: true, customRender: text => text || '—' },
          { title: '移交类型', dataIndex: 'handoverType', width: 100, scopedSlots: { customRender: 'handoverType' } },
          { title: '协议编号 / 日期', dataIndex: 'agreement', width: 170, scopedSlots: { customRender: 'agreement' } },
          { title: '建设单位', dataIndex: 'buildUnit', width: 200, ellipsis: true, customRender: text => text || '—' },
          { title: '接收管养单位', dataIndex: 'receiveUnit', width: 180, ellipsis: true, customRender: text => text || '—' },
          { title: '实际移交日期', dataIndex: 'handoverDate', width: 110, customRender: text => text || '—' },
          { title: '养护期', dataIndex: 'maintenance', width: 210, scopedSlots: { customRender: 'maintenance' } },
          { title: '状态', dataIndex: 'status', width: 130, scopedSlots: { customRender: 'status' } },
          { title: '操作', width: 230, fixed: 'right', scopedSlots: { customRender: 'action' } }
        ]
      }
    },
    methods: {
      statusColor,
      missingAgreement,
      maintenanceWarning,
      handleTableChange (pagination) {
        this.$emit('change', pagination)
      },
      handleSelectChange (selectedRowKeys, selectedRows) {
        this.$emit('select-change', selectedRowKeys, selectedRows)
      },
      handleStatusChange (record, args) {
        const status = args && args.key ? args.key : args
        if (!status || status === record.status) {
          return
        }
        this.$emit('status-change', record, status)
      }
    }
  }
</script>

<style lang="less" scoped>
  .handover-table {
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
  }
</style>
