<template>
  <!--
    FilePreviewModal 通用文件预览弹窗（大屏）
    --------------------------------
    档案卷内文件、收发文附件、提级论证材料共用。数据管理的附件预览弹窗
    （AttachmentPreviewModal）也复用同一个内容区 FilePreviewBody。

    对外契约：
      this.$refs.previewModal.open({
        fileName, fileExt, fileSize,
        previewMode,     // 优先用后端给的；没有时按扩展名兜底
        url,             // **完整可访问地址**（相对路径请调用方先拼好）
        downloadUrl      // 可选；不传则用 url
      })

    预览方式：
      image / pdf → 直接 iframe/img
      office      → kkFileView（doc/docx/xls/xlsx/ppt/pptx/wps/et/dps/ofd …）
      text        → fetch 后 <pre>
      download    → 提示下载

    ★ 为什么 office 要「新窗口打开」兜底：
      部分 kkFileView 部署会带 X-Frame-Options / 反向代理限制，iframe 被拦时
      页面只会一片空白。新窗口打开走的是同一个地址，能立刻区分「服务坏了」
      还是「只是不能嵌」。
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="1400"
    :show-footer="false"
    :body-max-height="'calc(100vh - 130px)'"
    @cancel="handleClose"
  >
    <template #head-extra>
      <div class="file-preview-modal__tags">
        <screen-tag v-if="fileExt" tone="muted" size="sm">{{ fileExt.toUpperCase() }}</screen-tag>
        <screen-tag v-if="sizeText !== '—'" tone="muted" size="sm">{{ sizeText }}</screen-tag>
        <screen-tag tone="info" size="sm">{{ modeText }}</screen-tag>
      </div>

      <screen-button
        size="sm"
        icon="external-link"
        :disabled="!newWindowUrl"
        @click="openNewWindow"
      >
        新窗口打开
      </screen-button>
      <screen-button
        size="sm"
        icon="download"
        :disabled="!downloadUrl"
        @click="handleDownload"
      >
        下载
      </screen-button>
    </template>

    <div class="file-preview-modal">
      <screen-loading
        class="file-preview-modal__content"
        :loading="loading"
        text="正在打开预览…"
        :overlay="false"
      >
        <file-preview-body
          :mode="mode"
          :url="displayUrl"
          :file-name="fileName"
        />
      </screen-loading>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenButton,
  ScreenTag,
  ScreenLoading
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import {
  buildOnlinePreviewUrl,
  resolvePreviewMode,
  previewModeText,
  formatFileSize
} from '@/api/land/filePreview'
import FilePreviewBody from './FilePreviewBody.vue'

export default {
  name: 'FilePreviewModal',
  components: { ScreenModal, ScreenButton, ScreenTag, ScreenLoading, FilePreviewBody },
  data () {
    return {
      visible: false,
      loading: false,
      file: null,
      mode: 'online',
      /** 文件直链（下载按钮用） */
      directUrl: '',
      /** 实际交给 iframe 的地址：**所有文件都是 kkFileView 地址** */
      displayUrl: '',
      downloadUrl: ''
    }
  },
  computed: {
    fileName () {
      return (this.file && this.file.fileName) || ''
    },
    fileExt () {
      return (this.file && this.file.fileExt) || ''
    },
    sizeText () {
      return formatFileSize(this.file && this.file.fileSize)
    },
    modeText () {
      return previewModeText(this.mode)
    },
    title () {
      return this.fileName ? `文件预览 · ${this.fileName}` : '文件预览'
    },
    /** 能新窗口打开的地址：优先 kkFileView/直链，没有再退回文件直链 */
    newWindowUrl () {
      return this.displayUrl || this.directUrl
    }
  },
  methods: {
    /**
     * 打开预览。
     * ★ 2026-10-22 起**所有文件统一走 kkFileView**：PDF 直连会被后端的
     *   Content-Disposition 变成下载，ZIP 直连浏览器不会列目录；交给 kkFileView
     *   才能正常显示，不支持的格式也由它自己的错误页反馈。
     */
    open (file) {
      const value = file || {}
      this.file = value
      this.mode = value.previewMode || resolvePreviewMode(value.fileExt || value.fileName)
      this.directUrl = value.url || ''
      this.displayUrl = buildOnlinePreviewUrl(this.directUrl, value.fileName)
      this.downloadUrl = value.downloadUrl || this.directUrl
      this.visible = true
    },

    openNewWindow () {
      const url = this.newWindowUrl
      if (!url) {
        toast.warning('取不到可打开的预览地址')
        return
      }
      window.open(url, '_blank')
    },

    handleDownload () {
      if (!this.downloadUrl) {
        toast.warning('取不到下载地址')
        return
      }
      window.open(this.downloadUrl, '_blank')
      toast.info('已开始下载，请稍候…')
    },

    handleClose () {
      this.visible = false
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.file-preview-modal {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;

  &__tags {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  /** ScreenLoading 的根节点不是弹性子项，必须显式撑满（README 踩坑 9） */
  &__content {
    display: flex;
    flex-direction: column;
    flex: 1 1 auto;
    min-height: 0;
  }

  &__hint {
    flex: 0 0 auto;
    margin: var(--screen-space-2) 0 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    line-height: 1.6;
  }
}
</style>
