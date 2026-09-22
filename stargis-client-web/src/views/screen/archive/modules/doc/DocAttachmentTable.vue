<template>
  <!--
    DocAttachmentTable 收发文附件表（含上传）
    --------------------------------
    与旧 tj-sfw 的关键差别：旧系统把多个附件用逗号拼在一个 file_path 字段里，
    文件名里出现逗号就会被拆成两个假附件；本系统用独立附件表，一行一个附件。

    上传走 jeecg 通用上传接口 /sys/common/upload（**Java 业务后端**），
    服务端只存相对路径，避免直接依赖磁盘布局。

    v-model 契约（一行一个附件，与后端 DocAttachment 对齐）：
      { id, fileName, fileExt, fileSize, storePath, storeType, uploadTime, uploadBy, uploadName, url, readableSize }
    id 为 null 表示还没保存（新增时），保存时随主表一起提交。

    用法：
      <doc-attachment-table v-model="form.attachments" biz-path="/receive/2026/09" />
      <doc-attachment-table :value="detail.attachments" disabled />   ← 详情页只读
  -->
  <div class="doc-attachment">
    <div v-if="!disabled" class="doc-attachment__toolbar">
      <screen-upload
        ref="uploader"
        :action="uploadAction"
        :headers="uploadHeaders"
        :data="{ biz: bizPath }"
        :allowed-ext="DOC_ALLOWED_EXT"
        :allowed-ext-text="DOC_ALLOWED_EXT_TEXT"
        :max-size-mb="MAX_SIZE_MB"
        :disabled="uploading"
        button-text="上传附件"
        @success="handleUploadSuccess"
        @error="handleUploadError"
        @reject="handleUploadReject"
        @uploading-change="uploading = $event"
      />
      <span class="doc-attachment__hint">
        允许 {{ DOC_ALLOWED_EXT_TEXT }}，单文件不超过 {{ MAX_SIZE_MB }}MB；
        扫描件、公文正文与附件可以一并上传
      </span>
    </div>

    <screen-data-table
      :columns="columns"
      :data="rows"
      row-key="rowKey"
      :min-width="820"
      :max-height="disabled ? 320 : 240"
      :animated="false"
      :empty-text="disabled ? '该公文没有附件' : '还没有附件'"
    >
      <template #index="{ index }">
        <span class="doc-attachment__index">{{ padIndex(index + 1) }}</span>
      </template>

      <template #fileName="{ row }">
        <button type="button" class="doc-attachment__link" @click="handleDownload(row)">
          {{ row.fileName }}
        </button>
      </template>

      <template #fileSize="{ row }">
        {{ row.readableSize || formatSize(row.fileSize) }}
      </template>

      <template #uploader="{ row }">
        {{ row.uploadName || row.uploadBy || '—' }}
      </template>

      <template #action="{ row }">
        <span class="doc-attachment__actions">
          <button type="button" class="doc-attachment__link" @click="handleDownload(row)">下载</button>
          <template v-if="!disabled">
            <screen-popconfirm
              title="确定移除该附件吗？"
              :description="row.fileName"
              width="264"
              @confirm="handleRemove(row)"
            >
              <button type="button" class="doc-attachment__link is-danger">移除</button>
            </screen-popconfirm>
          </template>
        </span>
      </template>
    </screen-data-table>

    <p v-if="!disabled && rows.length" class="doc-attachment__foot">
      共 <b>{{ rows.length }}</b> 个附件，保存后生效
    </p>
  </div>
</template>

<script>
import Vue from 'vue'
import { ScreenDataTable, ScreenUpload, ScreenPopconfirm } from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { JEECG_ACCESS_TOKEN } from '@/store/mutation-types'
import { javaUploadUrl, getJavaFileAccessHttpUrl } from '@/api/manageJava'
import {
  DOC_ALLOWED_EXT,
  DOC_ALLOWED_EXT_TEXT,
  MAX_SIZE_MB,
  formatSize,
  resolveExt,
  nowText,
} from '../../constants'

export default {
  name: 'DocAttachmentTable',
  components: { ScreenDataTable, ScreenUpload, ScreenPopconfirm },
  props: {
    /** 附件数组（v-model） */
    value: { type: Array, default: () => [] },
    /** 上传子目录，如 /receive/2026/09 */
    bizPath: { type: String, default: '/receive' },
    /** 只读模式（详情页） */
    disabled: { type: Boolean, default: false },
  },
  data () {
    return {
      DOC_ALLOWED_EXT,
      DOC_ALLOWED_EXT_TEXT,
      MAX_SIZE_MB,
      uploading: false,
      uploadAction: javaUploadUrl(),
      /**
       * 原生上传不走 axios，没有拦截器加令牌，需要手动带上。
       * 【stargis 改造】必须用 **jeecg 自己的令牌** JEECG_ACCESS_TOKEN：
       * 中台的 ACCESS_TOKEN 在 Java 端是无效令牌，带上会直接 401。
       */
      uploadHeaders: { 'X-Access-Token': Vue.ls.get(JEECG_ACCESS_TOKEN) },
      columns: [
        { key: 'index', title: '#', width: 48, type: 'slot', align: 'center' },
        { key: 'fileName', title: '文件名', width: 280, type: 'slot' },
        { key: 'fileSize', title: '大小', width: 110, type: 'slot', align: 'right' },
        { key: 'uploader', title: '上传人', width: 120, type: 'slot' },
        { key: 'uploadTime', title: '上传时间', width: 165 },
        { key: 'action', title: '操作', width: 140, type: 'slot', align: 'center' },
      ],
    }
  },
  computed: {
    /** 稳定 rowKey：刚上传还没保存的附件没有 id */
    rows () {
      return (this.value || []).map((item, index) => Object.assign({}, item, {
        rowKey: item.id || item.storePath || `tmp-${index}`,
      }))
    },
  },
  methods: {
    formatSize,
    padIndex (index) {
      return String(index).padStart(2, '0')
    },

    /* ---------------- 上传 ---------------- */

    handleUploadSuccess ({ file, storePath }) {
      const name = file && file.name ? file.name : ''
      if (!storePath) {
        toast.error(`文件「${name}」上传成功但未返回存储路径，请检查上传接口`)
        return
      }
      const next = (this.value || []).slice()
      next.push({
        id: null,
        fileName: name,
        fileExt: resolveExt(name),
        fileSize: file && file.size ? file.size : 0,
        storePath,
        storeType: 'local',
        uploadTime: nowText(),
      })
      this.$emit('input', next)
      toast.success(`「${name}」上传成功`)
    },
    handleUploadError (message) {
      toast.error(message || '附件上传失败，请检查后端服务与上传目录权限')
    },
    handleUploadReject (reason) {
      toast.warning(reason || '附件不符合上传要求')
    },

    /* ---------------- 列表操作 ---------------- */

    handleRemove (record) {
      const next = (this.value || []).filter((item) => !this.sameRow(item, record))
      this.$emit('input', next)
    },
    /** 已保存的按 id 匹配，未保存的按存储路径匹配 */
    sameRow (item, record) {
      if (item.id && record.id) return item.id === record.id
      return item.storePath === record.storePath
    },
    /**
     * 下载 / 预览。
     * 统一走 Java 后端的静态地址（/sys/common/static 不需要令牌），
     * 因此刚上传还没保存的附件也能立刻打开。
     */
    handleDownload (record) {
      const raw = record.url || record.storePath
      if (!raw) {
        toast.warning('该附件还没有可访问的地址')
        return
      }
      const url = getJavaFileAccessHttpUrl(raw)
      if (url) {
        window.open(url, '_blank')
      } else {
        toast.warning('该附件还没有可访问的地址')
      }
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.doc-attachment {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-2);
  min-width: 0;

  &__toolbar {
    display: flex;
    align-items: center;
    gap: var(--screen-space-4);
    flex-wrap: wrap;
    padding: var(--screen-space-2) var(--screen-space-3);
    background: rgba(6, 20, 40, 0.45);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
  }

  &__hint {
    flex: 1 1 260px;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-mute);
  }

  &__index {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-mute);
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
    justify-content: center;
  }

  &__link {
    .screen-link-action();

    &.is-danger {
      color: var(--screen-danger);

      &:hover {
        color: #ffab9f;
      }
    }
  }

  &__foot {
    margin: 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);

    b {
      margin: 0 2px;
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      color: var(--screen-accent-soft);
    }
  }
}
</style>
