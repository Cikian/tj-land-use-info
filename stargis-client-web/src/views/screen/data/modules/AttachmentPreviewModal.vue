<template>
  <!--
    AttachmentPreviewModal 附件预览
    --------------------------------
    预览方式**完全由后端的 `previewMode` 决定**，前端不做任何扩展名猜测：
    扩展名归一化（大写、多个点、无扩展名）与预览方式的对应关系只应该有一份实现，
    就在 `AttachmentController` 的 `previewMode` 里。前端另猜一套迟早与后端不一致，
    表现是「同一个文件在这个页面能看、在那个页面说打不开」。

      image    → <img>，静态资源地址（/sys/common/static 是 anon 放行的，不需要 token）
      pdf      → <iframe>，同上
      text     → fetch 回来塞进 <pre>（.txt/.md/.csv/.xml 这类）
      office   → 明确告知「该格式不支持在线预览，请下载查看」+ 下载按钮
      download → 同上

    ★ 为什么 office 不硬上「在线预览」：
      浏览器没有原生的 doc/xls 渲染能力，旧系统用 iframe 套 office 在线预览服务，
      结果是内网环境下十次有八次是空白页，用户以为「文件坏了」。
      老实说「请下载查看」比一个永远转圈的加载框好。

    ★ 下载必须走 `/land/data/attachment/download?id=`（token 在 query 上）：
      服务端要按这个入口统计下载次数。用静态资源地址虽然也能拿到文件，
      但下载次数永远是 0，「这份要件到底看没看过」就答不出来了。

    公开方法：
      open(record)  传列表行（含 id / fileName）；内部再取一次 previewUrl
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="1080"
    :show-footer="false"
    :body-max-height="'calc(100vh - 180px)'"
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
        <!-- ---------- 图片 ---------- -->
        <div v-if="mode === 'image'" class="attachment-preview__image">
          <img v-if="url" :src="url" :alt="fileName || '图片附件'" class="attachment-preview__img" />
          <p v-else class="attachment-preview__empty">取不到预览地址，请下载查看。</p>
        </div>

        <!-- ---------- PDF ---------- -->
        <div v-else-if="mode === 'pdf'" class="attachment-preview__frame">
          <!--
            ★ 这里用原生 <iframe>（不是 ScreenPopover / ScreenModal 嵌套）：
              PDF 阅读器需要完整的文档流上下文，套在任何 transform/overflow 容器里
              都可能只渲染第一页。所以给它一个确定高度的框。
          -->
          <iframe
            v-if="url"
            :src="url"
            class="attachment-preview__pdf"
            :title="fileName || 'PDF 附件'"
          />
          <p v-else class="attachment-preview__empty">取不到预览地址，请下载查看。</p>
        </div>

        <!-- ---------- 纯文本 ---------- -->
        <div v-else-if="mode === 'text'" class="attachment-preview__text">
          <p v-if="textError" class="attachment-preview__empty" role="alert">{{ textError }}</p>
          <pre v-else class="attachment-preview__pre">{{ textContent || '（空文件）' }}</pre>
        </div>

        <!-- ---------- Office / 只能下载 ---------- -->
        <div v-else class="attachment-preview__fallback">
          <screen-empty
            :text="fallbackTitle"
            :description="fallbackDesc"
          />
          <screen-button type="primary" icon="download" :disabled="!record || !record.id" @click="handleDownload">
            下载查看
          </screen-button>
        </div>
      </screen-loading>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenButton,
  ScreenTag,
  ScreenIcon,
  ScreenLoading,
  ScreenEmpty
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import {
  queryAttachmentPreviewUrl,
  buildAttachmentDownloadUrl,
  buildPreviewHttpUrl
} from '@/api/land/attachment'
import { formatSize, previewModeText, isPreviewableMode } from '../constants'

/** 文本预览的体积上限：超过就只提示下载，避免把几 MB 的 csv 塞进 DOM 卡死页面 */
const TEXT_MAX_BYTES = 1024 * 1024

export default {
  name: 'AttachmentPreviewModal',
  components: {
    ScreenModal,
    ScreenButton,
    ScreenTag,
    ScreenIcon,
    ScreenLoading,
    ScreenEmpty
  },
  data () {
    return {
      visible: false,
      loading: false,
      record: null,
      info: null,
      /** 完整静态资源地址（由 /previewUrl 的 storePath 拼出来） */
      url: '',
      mode: 'download',
      textContent: '',
      textError: ''
    }
  },
  computed: {
    fileName () {
      return (this.info && this.info.fileName) || (this.record && this.record.fileName) || ''
    },
    title () {
      return this.fileName ? `附件预览 · ${this.fileName}` : '附件预览'
    },
    fallbackTitle () {
      return this.mode === 'office' ? 'Office 文档不支持在线预览' : '该格式不支持在线预览'
    },
    fallbackDesc () {
      return '该格式不支持在线预览，请下载查看。（浏览器没有原生的 Office 渲染能力，内网环境下第三方在线预览服务多数不可用，因此这里如实告知而不是给一个永远转圈的加载框。）'
    }
  },
  methods: {
    formatSize,
    previewModeText,
    isPreviewableMode,

    /* ---------------- 对外入口 ---------------- */

    open (record) {
      if (!record || !record.id) {
        toast.warning('缺少附件主键，无法预览')
        return
      }
      this.record = record
      this.info = null
      this.url = ''
      this.mode = record.previewMode || 'download'
      this.textContent = ''
      this.textError = ''
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
          if (this.mode === 'text') {
            this.loadText(info)
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    /**
     * 文本内容单独取。
     * ★ 用原生 fetch 而不是 axios：静态资源接口返回的是纯文本，
     *   走 axios 项目实例会被中台拦截器掺和（并且它只带 X-Access-Token，
     *   而静态资源是 anon 的，不需要任何请求头）。
     * ★ 超限不取：几 MB 的 csv 塞进 DOM 会把页面卡死，
     *   这种情况提示下载比「打开了但动不了」好。
     */
    loadText (info) {
      const size = Number((this.record && this.record.fileSize) || 0)
      if (size > TEXT_MAX_BYTES) {
        this.textError = '文件较大（超过 1MB），为避免页面卡顿不再在线渲染，请下载查看。'
        return
      }
      if (!this.url) {
        this.textError = '取不到预览地址，请下载查看。'
        return
      }
      window
        .fetch(this.url)
        .then((response) => {
          if (!response.ok) {
            throw new Error(`HTTP ${response.status}`)
          }
          return response.text()
        })
        .then((text) => {
          // 后端可能返回很长的内容，这里仍然截一刀，双保险
          this.textContent = text.length > 200000 ? `${text.slice(0, 200000)}\n\n…（内容过长，已截断，完整内容请下载查看）` : text
        })
        .catch((error) => {
          this.textError = `文本读取失败（${(error && error.message) || '未知错误'}），请下载查看。`
        })
    },

    /* ---------------- 操作 ---------------- */

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
