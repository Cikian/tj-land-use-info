<template>
  <screen-modal
    :visible.sync="visible"
    title="批量关联扫描件"
    :width="920"
    ok-text="确认批量关联"
    cancel-text="取消"
    :confirm-loading="saving"
    :body-max-height="'calc(100vh - 200px)'"
    @ok="handleSubmit"
    @cancel="handleClose"
  >
    <div class="completion-batch-link">
      <div class="completion-batch-link__selected">
        <span>已选 {{ records.length }} 条历史档案</span>
        <screen-tag v-for="item in records.slice(0, 5)" :key="item.id" size="sm" tone="info">
          {{ item.archiveNo || item.projectName }}
        </screen-tag>
        <span v-if="records.length > 5" class="completion-batch-link__muted">等 {{ records.length }} 条</span>
      </div>
      <p class="completion-batch-link__hint">
        选择一个扫描件档案，关联到全部已选历史档案。每条分别保留操作留痕；任一条失败，整批不保存。
      </p>

      <div class="completion-batch-link__search">
        <screen-input
          id="completion-batch-link-keyword"
          v-model="keyword"
          clearable
          placeholder="按档案号 / 档案名称 / 配套项目名称搜索"
          @enter="loadOptions"
        />
        <screen-button type="primary" icon="search" :loading="loading" @click="loadOptions">搜索</screen-button>
      </div>

      <div v-if="selectedArchive" class="completion-batch-link__target">
        目标档案
        <screen-tag tone="success" size="sm">{{ selectedArchive.archiveNo }} · {{ selectedArchive.archiveName }}</screen-tag>
      </div>

      <screen-data-table
        :columns="columns"
        :data="options"
        row-key="id"
        :loading="loading"
        :min-width="820"
        :animated="false"
        empty-text="没有找到可关联的档案"
      >
        <template #archiveStatus="{ row }">
          <screen-tag :tone="statusTone(row.status)" size="sm">{{ row.status || '—' }}</screen-tag>
        </template>
        <template #action="{ row }">
          <screen-button
            size="sm"
            :type="selectedArchiveId === row.id ? 'primary' : 'default'"
            @click="selectArchive(row)"
          >
            {{ selectedArchiveId === row.id ? '已选择' : '选择' }}
          </screen-button>
        </template>
      </screen-data-table>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenButton,
  ScreenDataTable,
  ScreenInput,
  ScreenTag
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { statusTone } from '../../constants'
import { linkCompletionArchivesBatch, pickArchives } from '@/api/land/completion'

/** 多选历史档案整批关联到一个扫描件；详情中的单关联与取消关联保持不变。 */
export default {
  name: 'CompletionBatchLinkModal',
  components: { ScreenModal, ScreenButton, ScreenDataTable, ScreenInput, ScreenTag },
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
        { key: 'archiveNo', title: '档案号', width: 150 },
        { key: 'archiveName', title: '档案名称', width: 250, ellipsis: true },
        { key: 'ptxmmc', title: '配套项目', width: 190, ellipsis: true },
        { key: 'archiveStatus', title: '状态', width: 96, type: 'slot' },
        { key: 'action', title: '操作', width: 100, type: 'slot', align: 'center' }
      ]
    }
  },
  methods: {
    statusTone,
    open (records) {
      const rows = (records || []).filter((item) => item && item.id)
      if (!rows.length) {
        toast.warning('请先选择要关联的历史档案')
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
      if (this.keyword) params.keyword = this.keyword
      pickArchives(params)
        .then((res) => {
          if (!res || !res.success) {
            this.options = []
            toast.error((res && res.message) || '档案检索失败')
            return
          }
          this.options = res.result || []
        })
        .catch((error) => {
          this.options = []
          toast.error(this.errorText(error, '档案检索失败'))
        })
        .finally(() => {
          this.loading = false
        })
    },
    selectArchive (row) {
      this.selectedArchiveId = row.id
      this.selectedArchive = row
    },
    handleSubmit () {
      if (!this.selectedArchiveId) {
        toast.warning('请选择要关联的扫描件档案')
        return
      }
      this.saving = true
      linkCompletionArchivesBatch(this.records.map((item) => item.id), this.selectedArchiveId)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '批量关联失败，整批未保存')
            return
          }
          const result = res.result || {}
          toast.success(res.message || `已批量关联 ${result.successCount || this.records.length} 条历史档案`)
          this.visible = false
          this.$emit('changed', result)
        })
        .catch((error) => {
          toast.error(this.errorText(error, '批量关联失败，整批未保存'))
        })
        .finally(() => {
          this.saving = false
        })
    },
    handleClose () {
      if (!this.saving) this.visible = false
    },
    errorText (error, fallback) {
      const response = error && error.response
      return (response && response.data && response.data.message) || (error && error.message) || fallback
    }
  }
}
</script>

<style scoped lang="less">
.completion-batch-link {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

  &__selected,
  &__target {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--screen-space-2);
    color: var(--screen-text-sub);
  }

  &__hint {
    margin: 0;
    padding: var(--screen-space-2) var(--screen-space-3);
    color: var(--screen-text-sub);
    background: var(--screen-row-alt);
    border-left: 3px solid var(--screen-info);
  }

  &__search {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);

    /deep/ .screen-input {
      flex: 1 1 auto;
      min-width: 0;
    }
  }

  &__muted {
    color: var(--screen-text-mute);
  }
}
</style>
