<template>
  <a-table
    :rowKey="'id'"
    :columns="columns"
    :dataSource="dataSource"
    :loading="loading"
    :pagination="pagination"
    :rowSelection="selectable ? { selectedRowKeys: selectedRowKeys, onChange: handleSelectChange } : undefined"
    :scroll="{ x: 1500 }"
    size="middle"
    @change="handleTableChange">
    <template slot="archiveNo" slot-scope="text, record">
      <a class="archive-table__no" @click="$emit('detail', record)">{{ text }}</a>
    </template>

    <template slot="archiveName" slot-scope="text, record">
      <div class="archive-table__name" :title="text">{{ text }}</div>
      <div v-if="record.sourceType && record.sourceType !== 'manual'" class="archive-table__source">
        来源：{{ sourceTypeText(record.sourceType) }}
      </div>
    </template>

    <template slot="categoryNames" slot-scope="text">
      <span v-if="text" class="archive-table__category" :title="text">{{ text }}</span>
      <span v-else class="archive-table__muted">未分类</span>
    </template>

    <template slot="fileCount" slot-scope="text, record">
      <span>{{ text || 0 }}</span>
      <span class="archive-table__muted"> / {{ formatSize(record.totalSize) }}</span>
    </template>

    <template slot="status" slot-scope="text">
      <a-tag :color="statusColor(text)">{{ text }}</a-tag>
    </template>

    <template slot="action" slot-scope="text, record">
      <a v-has="'land:archive:list'" @click="$emit('detail', record)">详情</a>
      <template v-if="!readonly">
        <span v-has="'land:archive:edit'">
          <a-divider type="vertical" />
          <a @click="$emit('edit', record)">编辑</a>
        </span>
        <span v-has="'land:archive:delete'">
          <a-divider type="vertical" />
          <a-popconfirm title="删除后不可恢复，确定删除该档案吗？" okText="确定" cancelText="取消" @confirm="$emit('delete', record)">
            <a class="archive-table__danger">删除</a>
          </a-popconfirm>
        </span>
      </template>
    </template>
  </a-table>
</template>

<script>
  /**
   * 档案列表表格（档案维护页与档案查询页共用）
   *
   * 列口径与方案 2.3.2 第 3 项的结果列一致：
   * 档案号 / 档案名称 / 档案类别 / 宗地编号 / 配套项目 / 行政区 /
   * 责任部门 / 负责人 / 归档日期 / 文件数 / 状态 / 操作。
   *
   * 注意：档案类别挂在卷内文件上，所以列表里的「档案类别」是子查询聚合出来的
   * 多个类别名（用「、」连接），不是档案自身的属性。
   */
  export default {
    name: 'ArchiveTable',
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
      /** 只读模式（档案查询页）：隐藏编辑与删除 */
      readonly: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        columns: [
          { title: '档案号', dataIndex: 'archiveNo', width: 130, fixed: 'left', scopedSlots: { customRender: 'archiveNo' } },
          { title: '档案名称', dataIndex: 'archiveName', width: 230, ellipsis: true, scopedSlots: { customRender: 'archiveName' } },
          { title: '档案类别', dataIndex: 'categoryNames', width: 220, scopedSlots: { customRender: 'categoryNames' } },
          { title: '配套项目', dataIndex: 'ptxmmc', width: 200, ellipsis: true, customRender: text => text || '—' },
          { title: '出让宗地编号', dataIndex: 'crzdbh', width: 170, ellipsis: true, customRender: text => text || '—' },
          { title: '行政区', dataIndex: 'xzqh', width: 90, customRender: text => text || '—' },
          { title: '责任部门', dataIndex: 'responsibleDept', width: 130, customRender: text => text || '—' },
          { title: '负责人', dataIndex: 'responsibleUser', width: 100, customRender: text => text || '—' },
          { title: '归档日期', dataIndex: 'archiveDate', width: 110, customRender: text => text || '—' },
          { title: '文件数', dataIndex: 'fileCount', width: 130, scopedSlots: { customRender: 'fileCount' } },
          { title: '状态', dataIndex: 'status', width: 90, scopedSlots: { customRender: 'status' } },
          { title: '操作', width: 160, fixed: 'right', scopedSlots: { customRender: 'action' } }
        ]
      }
    },
    methods: {
      handleTableChange (pagination) {
        this.$emit('change', pagination)
      },
      handleSelectChange (selectedRowKeys, selectedRows) {
        this.$emit('select-change', selectedRowKeys, selectedRows)
      },
      sourceTypeText (type) {
        if (type === 'doc_receive') {
          return '收文归档'
        }
        if (type === 'doc_send') {
          return '发文归档'
        }
        return '手工录入'
      },
      statusColor (status) {
        if (status === '已归档') {
          return 'green'
        }
        if (status === '审核中') {
          return 'orange'
        }
        if (status === '归档中') {
          return 'blue'
        }
        // ★ 兜底返回 undefined，不要写 'default'：它不是 antd 预设色，
        //   会被当成自定义色 → 白字 + 非法背景色被丢弃 → 白底白字看不见（详见 docs 7.5）
        return undefined
      },
      formatSize (bytes) {
        const value = Number(bytes)
        if (!value) {
          return '0 B'
        }
        const units = ['B', 'KB', 'MB', 'GB', 'TB']
        let size = value
        let unit = 0
        while (size >= 1024 && unit < units.length - 1) {
          size /= 1024
          unit++
        }
        return unit === 0 ? `${size} ${units[unit]}` : `${size.toFixed(1)} ${units[unit]}`
      }
    }
  }
</script>

<style lang="less" scoped>
  .archive-table {
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

    &__source {
      font-size: 12px;
      color: #94a3b8;
    }

    &__category {
      display: inline-block;
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: #334155;
    }

    &__muted {
      color: #94a3b8;
      font-size: 12px;
    }

    &__danger {
      color: #ff4d4f;
    }
  }
</style>
