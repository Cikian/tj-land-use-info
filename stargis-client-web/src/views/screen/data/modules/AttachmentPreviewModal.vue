<template>
  <!--
    AttachmentPreviewModal 附件预览
    --------------------------------
    预览方式**完全由后端的 `previewMode` 决定**，前端不做任何扩展名猜测：
    扩展名归一化（大写、多个点、无扩展名）与预览方式的对应关系只应该有一份实现，
    就在 `AttachmentController` 的 `previewMode` 里。前端另猜一套迟早与后端不一致，
    表现是「同一个文件在这个页面能看、在那个页面说打不开」。

    ★ 2026-10-22 起**所有文件统一走 kkFileView**（含 PDF、图片、ZIP、文本）：
      浏览器直接打开 PDF 会被后端的 Content-Disposition 变成下载，ZIP 直连也不会
      列目录；交给 kkFileView 才能正常显示，不支持的格式也由它自己的错误页反馈。
      previewMode 现在只用于页头「预览方式」标签。

    ★ 为什么 Office/WPS 不自己渲染：
      浏览器没有原生的 doc/xls/wps 渲染能力。本系统统一接 kkFileView：
      服务端用 LibreOffice 转 PDF/图片，前端 iframe 展示，地址由
      `@/api/land/filePreview` 的 buildOnlinePreviewUrl() 拼装。
      若 kkFileView 的 X-Frame-Options 拦住 iframe，用户可点「新窗口打开」
      （同一个地址），不会出现「只能看到一个永远转圈的加载框」。

    ★ 下载必须走 `/land/data/attachment/download?id=`（token 在 query 上）：
      服务端要按这个入口统计下载次数。用静态资源地址虽然也能拿到文件，
      但下载次数永远是 0，「这份要件到底看没看过」就答不出来了。

    公开方法：
      open(record)  传列表行（含 id / fileName）；内部再取一次 previewUrl
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
      <div class="attachment-preview__tags">
        <screen-tag v-if="info && info.fileTypeText" tone="muted" size="sm">{{ info.fileTypeText }}</screen-tag>
        <screen-tag v-if="record" tone="muted" size="sm">{{ formatSize(record.fileSize) }}</screen-tag>
        <screen-tag tone="info" size="sm">{{ previewModeText(mode) }}</screen-tag>
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
        :disabled="!record || !record.id"
        @click="handleDownload"
      >
        下载原文件
      </screen-button>
    </template>

    <div class="attachment-preview">
      <screen-loading
        class="attachment-preview__content"
        :loading="loading"
        text="正在解析附件预览方式…"
        :overlay="false"
      >
        <!--
          ★ 预览内容区抽到 FilePreviewBody：档案、收发文、提级材料共用。
            ★ 2026-10-22 起所有文件统一走 kkFileView（PDF/ZIP 直连都有问题），
              这里只负责把静态地址换成 onlinePreview 地址。
        -->
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
  queryAttachmentPreviewUrl,
  buildAttachmentDownloadUrl,
  buildPreviewHttpUrl
} from '@/api/land/attachment'
import { buildOnlinePreviewUrl } from '@/api/land/filePreview'
import { formatSize, previewModeText } from '../constants'
import FilePreviewBody from '@/views/screen/file-preview/FilePreviewBody.vue'

export default {
  name: 'AttachmentPreviewModal',
  components: {
    ScreenModal,
    ScreenButton,
    ScreenTag,
    ScreenLoading,
    FilePreviewBody
  },
  data () {
    return {
      visible: false,
      loading: false,
      record: null,
      info: null,
      /** 完整静态资源地址（由 /previewUrl 的 storePath 拼出来，下载按钮用） */
      url: '',
      mode: 'online'
    }
  },
  computed: {
    fileName () {
      return (this.info && this.info.fileName) || (this.record && this.record.fileName) || ''
    },
    title () {
      return this.fileName ? `附件预览 · ${this.fileName}` : '附件预览'
    },
    /**
     * 实际交给 FilePreviewBody 的地址。
     * ★ 所有文件都换成 kkFileView 的 onlinePreview 地址：
     *   PDF 直连会被后端 Content-Disposition 变成下载，ZIP 直连不会列目录。
     */
    displayUrl () {
      return buildOnlinePreviewUrl(this.url, this.fileName)
    },
    newWindowUrl () {
      return this.displayUrl || this.url
    }
  },
  methods: {
    formatSize,
    previewModeText,

    /* ---------------- 对外入口 ---------------- */

    open (record) {
      if (!record || !record.id) {
        toast.warning('缺少附件主键，无法预览')
        return
      }
      this.record = record
      this.info = null
      this.url = ''
      this.mode = record.previewMode || 'online'
      this.visible = true
      this.load(record.id)
    },

    /**
     * 取预览地址。
     * ★ 即使列表行里已经带了 previewMode 与 url，也要再调一次：
     *   列表可能是几分钟前拉的，文件可能已经被替换；而且列表的 url 字段
     *   在某些查询路径下不会被后端补齐（只有 /byBiz 与 /list 才 enrich）。
     *   以 /previewUrl 的返回为准，是唯一可靠的口径。
     */
    load (id) {
      this.loading = true
      queryAttachmentPreviewUrl(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '取预览地址失败')
            this.visible = false
            return
          }
          const info = res.result || {}
          this.info = info
          this.mode = info.previewMode || this.mode
          this.url = buildPreviewHttpUrl(info.storePath) || ''
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 操作 ---------------- */

    /**
     * 新窗口打开。
     * ★ Office/WPS 的 iframe 可能被 kkFileView 的 X-Frame-Options / 反向代理拦截，
     *   这里用同一个地址新开窗口，既能让用户看到内容，也能快速判断是「服务不可用」
     *   还是「只是不能嵌在弹窗里」。
     */
    openNewWindow () {
      const url = this.newWindowUrl
      if (!url) {
        toast.warning('取不到可打开的预览地址')
        return
      }
      window.open(url, '_blank')
    },

    /**
     * 下载。
     * ★ 走 `/land/data/attachment/download?id=` 并把 token 拼在 query 上，
     *   这样服务端能统计下载次数；直接开静态资源地址则次数永远是 0。
     */
    handleDownload () {
      const record = this.record
      if (!record || !record.id) {
        toast.warning('该附件还没有落库，无法下载')
        return
      }
      window.open(buildAttachmentDownloadUrl(record.id), '_blank')
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

.attachment-preview {
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

  &__image {
    display: flex;
    align-items: center;
    justify-content: center;
    flex: 1 1 auto;
    min-height: 0;
    padding: var(--screen-space-3);
    background: rgba(6, 20, 40, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
    overflow: auto;
    .screen-scrollbar();
  }

  &__img {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
  }

  &__frame {
    display: flex;
    flex: 1 1 auto;
    min-height: 0;
    background: rgba(6, 20, 40, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
    overflow: hidden;
  }

  &__pdf {
    flex: 1 1 auto;
    width: 100%;
    min-height: 560px;
    border: 0;
    // PDF 阅读器自带白底，这里给白底避免暗色主题下边缘出现黑框
    background: #ffffff;
  }

  &__text {
    display: flex;
    flex: 1 1 auto;
    min-height: 0;
    background: rgba(6, 20, 40, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
    overflow: auto;
    .screen-scrollbar();
  }

  &__pre {
    margin: 0;
    padding: var(--screen-space-3);
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-sm);
    line-height: 1.7;
    color: var(--screen-text);
    white-space: pre-wrap;
    word-break: break-all;
  }

  &__fallback {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--screen-space-4);
    flex: 1 1 auto;
    min-height: 320px;
    padding: var(--screen-space-6);
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
  }

  &__empty {
    margin: 0;
    padding: var(--screen-space-6);
    font-size: var(--screen-font-sm);
    color: var(--screen-text-mute);
    text-align: center;
  }
}
</style>
