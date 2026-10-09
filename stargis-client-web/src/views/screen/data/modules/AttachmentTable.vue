<template>
  <!--
    AttachmentTable 附件列表表格
    --------------------------------
    纯展示组件：不发任何请求，交互通过事件抛给父组件。

    ★ 文件名列就是预览入口（type=slot 里的按钮），不额外占一格「预览」四个字：
      附件列表里用户第一眼找的就是文件名，点它看内容是最自然的手势。
      操作列仍保留「预览」文字入口，因为键盘用户与读屏用户需要可预测的按钮名。

    ★ 大小用后端给的 readableSize，取不到才用 formatSize 兜底：
      「2.31 MB」这个人话是后端按同一口径算的（与导出、统计一致）；
      前端再算一遍容易出现「列表说 2.3 MB、详情说 2.31 MB」。
      `Attachment` 后端**没有** readableSize 这个字段时，这里也能兜住。

    ★ 下载次数是留痕数据不是装饰：它回答「这份要件对面到底看没看过」。

    事件：
      preview  (row)  预览
      download (row)  下载（父组件负责走带 token 的下载地址）
      remove   (row)  删除
  -->
  <screen-data-table
    :columns="columns"
    :data="dataSource"
    :loading="loading"
    row-key="id"
    :min-width="1500"
    :selectable="selectable"
    :selected-keys="selectedRowKeys"
    :empty-text="emptyText"
    @select-change="handleSelectChange"
  >
    <!-- 文件名：主行 + 业务可读键副行（同一附件在不同业务下同名很常见） -->
    <template #fileName="{ row }">
      <button
        type="button"
        class="attachment-table__link"
        :title="row.fileName || ''"
        @click.stop="$emit('preview', row)"
      >
        {{ row.fileName || '—' }}
      </button>
      <span v-if="row.bizKey" class="attachment-table__sub" :title="row.bizKey">
        {{ bizTypeText(row.bizType) }} · {{ row.bizKey }}
      </span>
    </template>

    <template #fileType="{ row }">
      <screen-tag tone="muted" size="sm">{{ row.fileTypeText || row.fileType || '—' }}</screen-tag>
    </template>

    <template #fileSize="{ row }">
      <span class="attachment-table__num">{{ row.readableSize || formatSize(row.fileSize) }}</span>
    </template>

    <template #fileExt="{ row }">
      <span class="attachment-table__ext">{{ (row.fileExt || resolveExt(row.fileName) || '—').toUpperCase() }}</span>
    </template>

    <template #uploadName="{ row }">
      {{ row.uploadName || row.uploadBy || '—' }}
    </template>

    <template #uploadTime="{ row }">
      <span class="attachment-table__time">{{ formatTime(row.uploadTime) }}</span>
    </template>

    <template #downloadCount="{ row }">
      <span class="attachment-table__num" :class="{ 'is-muted': !row.downloadCount }">
        {{ row.downloadCount || 0 }}
      </span>
    </template>

    <template #previewable="{ row }">
      <screen-tag :tone="isPreviewableMode(row.previewMode) ? 'success' : 'muted'" size="sm">
        {{ previewModeText(row.previewMode) }}
      </screen-tag>
    </template>

    <template #action="{ row }">
      <span class="attachment-table__actions">
        <button type="button" class="attachment-table__link" @click.stop="$emit('preview', row)">预览</button>
        <button type="button" class="attachment-table__link" @click.stop="$emit('download', row)">下载</button>

        <template v-if="!readonly">
          <screen-popconfirm
            title="删除后该文件不再出现在列表里（磁盘文件保留，同一文件可能被多条业务引用），确定删除吗？"
            :description="row.fileName || ''"
            width="340"
            @confirm="$emit('remove', row)"
          >
            <button type="button" class="attachment-table__link is-danger">删除</button>
          </screen-popconfirm>
        </template>
      </span>
    </template>
  </screen-data-table>
</template>

<script>
import { ScreenDataTable, ScreenTag, ScreenPopconfirm } from '@/components/screen'
import {
  formatSize,
  formatTime,
  previewModeText,
  isPreviewableMode,
  bizTypeText,
  resolveExt
} from '../constants'

export default {
  name: 'AttachmentTable',
  components: { ScreenDataTable, ScreenTag, ScreenPopconfirm },
  props: {
    dataSource: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    selectable: { type: Boolean, default: false },
    selectedRowKeys: { type: Array, default: () => [] },
    readonly: { type: Boolean, default: false },
    emptyText: { type: String, default: '暂无附件' }
  },
  data () {
    return {
      columns: [
        { key: 'fileName', title: '文件名 / 归属', width: 320, type: 'slot' },
        { key: 'fileTypeText', title: '附件类型', width: 170, type: 'slot', align: 'center' },
        { key: 'fileSize', title: '大小', width: 110, type: 'slot', align: 'right' },
        { key: 'fileExt', title: '格式', width: 90, type: 'slot', align: 'center' },
        { key: 'uploadName', title: '上传人', width: 120, type: 'slot' },
        { key: 'uploadTime', title: '上传时间', width: 170, type: 'slot' },
        { key: 'previewable', title: '预览方式', width: 130, type: 'slot', align: 'center' },
        { key: 'downloadCount', title: '下载次数', width: 110, type: 'slot', align: 'right' },
        { key: 'action', title: '操作', width: 180, type: 'slot', align: 'center' }
      ]
    }
  },
  methods: {
    formatSize,
    formatTime,
    previewModeText,
    isPreviewableMode,
    bizTypeText,
    resolveExt,
    handleSelectChange (keys, rows) {
      this.$emit('select-change', keys, rows)
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.attachment-table {
  &__sub {
    display: block;
    margin-top: 1px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  &__num {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text);

    &.is-muted {
      color: var(--screen-text-mute);
    }
  }

  &__ext {
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
  }

  &__time {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
    justify-content: center;
  }

  &__link {
    .screen-link-action();

    // 文件名作为主入口，给一档更大的字号
    &:not(.is-danger):first-child {
      font-size: var(--screen-font-sm);
    }
  }
}
</style>
