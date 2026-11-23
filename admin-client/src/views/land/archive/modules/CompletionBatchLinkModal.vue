<template>
  <a-modal
    title="批量关联扫描件"
    :width="900"
    :visible="visible"
    :confirmLoading="saving"
    okText="确认批量关联"
    cancelText="取消"
    :maskClosable="false"
    @ok="handleSubmit"
    @cancel="handleClose">
    <div class="completion-batch-link">
      <div class="completion-batch-link__selected">
        <span>已选 {{ records.length }} 条历史档案</span>
        <a-tag v-for="item in records.slice(0, 5)" :key="item.id">{{ item.archiveNo || item.projectName }}</a-tag>
        <span v-if="records.length > 5" class="completion-batch-link__muted">等 {{ records.length }} 条</span>
      </div>
      <a-alert
        type="info"
        showIcon
        message="选择一个扫描件档案，关联到全部已选历史档案"
        description="每条记录分别保留操作人和时间留痕；任一条校验或保存失败，整批都不会保存。" />

      <a-input-search
        v-model="keyword"
        class="completion-batch-link__search"
        placeholder="按档案号 / 档案名称 / 配套项目名称搜索"
        enterButton="搜索"
        allowClear
        @search="loadOptions" />

      <div v-if="selectedArchive" class="completion-batch-link__target">
        目标档案：<a-tag color="green">{{ selectedArchive.archiveNo }} · {{ selectedArchive.archiveName }}</a-tag>
      </div>

      <a-table
        rowKey="id"
        size="small"
        :columns="columns"
        :dataSource="options"
        :loading="loading"
        :pagination="false"
        :scroll="{ y: 360 }"
        :locale="{ emptyText: '没有找到可关联的档案' }">
        <template slot="archiveStatus" slot-scope="text">
          <a-tag :color="archiveStatusColor(text)">{{ text || '—' }}</a-tag>
        </template>
        <template slot="action" slot-scope="text, record">
          <a-button
            size="small"
            :type="selectedArchiveId === record.id ? 'primary' : 'default'"
            @click="selectArchive(record)">
            {{ selectedArchiveId === record.id ? '已选择' : '选择' }}
          </a-button>
        </template>
      </a-table>
    </div>
  </a-modal>
</template>

<script>
  import {
    archiveStatusColor,
    linkCompletionArchivesBatch,
    pickArchives
  } from '@/api/land/completion'

  /** 多选历史档案后，把它们整批关联到同一个扫描件档案；单关联入口仍在详情中。 */
  export default {
    name: 'CompletionBatchLinkModal',
    data () {
      return {
        visible: false,
        loading: false,
        saving: false,
        records: [],
        keyword: '',
        options: [],
        selectedArchiveId: '',
        selectedArchive: null,
        columns: [
          { title: '档案号', dataIndex: 'archiveNo', width: 150, customRender: text => text || '—' },
          { title: '档案名称', dataIndex: 'archiveName', width: 250, ellipsis: true, customRender: text => text || '—' },
          { title: '配套项目', dataIndex: 'ptxmmc', width: 190, ellipsis: true, customRender: text => text || '—' },
          { title: '状态', dataIndex: 'status', width: 90, scopedSlots: { customRender: 'archiveStatus' } },
          { title: '操作', width: 90, align: 'center', scopedSlots: { customRender: 'action' } }
        ]
      }
    },
    methods: {
      archiveStatusColor,
      open (records) {
        const rows = (records || []).filter(item => item && item.id)
        if (!rows.length) {
          this.$message.warning('请先选择要关联的历史档案')
          return
        }
        this.records = rows
        this.keyword = ''
        this.options = []
        this.selectedArchiveId = ''
        this.selectedArchive = null
        this.visible = true
        this.loadOptions()
      },
      loadOptions () {
        this.loading = true
        const params = { limit: 100 }
        if (this.keyword) {
          params.keyword = this.keyword
        }
        pickArchives(params).then(res => {
          if (res && res.success) {
            this.options = res.result || []
          } else {
            this.options = []
            this.$message.warning((res && res.message) || '档案检索失败')
          }
        }).catch(e => {
          this.options = []
          this.$message.error(this.errorText(e, '档案检索失败'))
        }).finally(() => {
          this.loading = false
        })
      },
      selectArchive (record) {
        this.selectedArchiveId = record.id
        this.selectedArchive = record
      },
      handleSubmit () {
        if (!this.selectedArchiveId) {
          this.$message.warning('请选择要关联的扫描件档案')
          return
        }
        this.saving = true
        linkCompletionArchivesBatch(this.records.map(item => item.id), this.selectedArchiveId).then(res => {
          if (!res || !res.success) {
            this.$message.warning((res && res.message) || '批量关联失败，整批未保存')
            return
          }
          const result = res.result || {}
          this.$message.success(res.message || `已批量关联 ${result.successCount || this.records.length} 条历史档案`)
          this.visible = false
          this.$emit('changed', result)
        }).catch(e => {
          this.$message.error(this.errorText(e, '批量关联失败，整批未保存'))
        }).finally(() => {
          this.saving = false
        })
      },
      handleClose () {
        if (!this.saving) {
          this.visible = false
        }
      },
      errorText (e, fallback) {
        return (e && e.response && e.response.data && e.response.data.message) ||
          (e && e.message) || fallback
      }
    }
  }
</script>

<style lang="less" scoped>
  .completion-batch-link {
    display: flex;
    flex-direction: column;
    gap: 12px;

    &__selected,
    &__target {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 6px;
      color: #475569;
    }

    &__search {
      width: 100%;
    }

    &__muted {
      color: #94a3b8;
    }
  }
</style>
