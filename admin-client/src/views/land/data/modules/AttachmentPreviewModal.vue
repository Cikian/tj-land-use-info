<template>
  <a-modal
    :title="null"
    :width="960"
    :visible="visible"
    :footer="null"
    :mask-closable="false"
    wrap-class-name="attachment-preview-modal"
    @cancel="handleClose">
    <a-spin :spinning="loading">
      <div class="attach-preview">
        <!-- ============ 页头 ============ -->
        <header class="attach-preview__head">
          <div class="attach-preview__head-main">
            <div class="attach-preview__name">{{ fileName || '附件预览' }}</div>
            <div class="attach-preview__meta">
              <a-tag v-if="fileTypeText">{{ fileTypeText }}</a-tag>
              <a-tag v-if="record" :color="bizTypeColor(record.bizType)">{{ bizTypeText(record.bizType) }}</a-tag>
              <span v-if="record && record.bizKey" class="attach-preview__key">{{ record.bizKey }}</span>
              <span v-if="readableSize" class="attach-preview__size">{{ readableSize }}</span>
              <span class="attach-preview__mode">{{ previewModeText(mode) }}</span>
            </div>
          </div>
          <div class="attach-preview__head-actions">
            <!-- ★ 下载永远给：预览失败、格式不支持、Office 打不开，最后都要落到「下载来看」 -->
            <a-button size="small" type="primary" icon="download" @click="handleDownload">下载</a-button>
            <a-button size="small" icon="close" @click="handleClose">关闭</a-button>
          </div>
        </header>

        <a-alert
          v-if="loadError"
          type="error"
          show-icon
          :message="loadError" />

        <!-- ============ 图片 ============ -->
        <div v-else-if="mode === 'image'" class="attach-preview__body">
          <img v-if="staticUrl" class="attach-preview__image" :src="staticUrl" :alt="fileName">
          <a-empty v-else description="没有取到文件地址，无法预览（可点「下载」试试）" />
        </div>

        <!-- ============ PDF ============ -->
        <div v-else-if="mode === 'pdf'" class="attach-preview__body">
          <iframe v-if="staticUrl" class="attach-preview__frame" :src="staticUrl" :title="fileName" />
          <a-empty v-else description="没有取到文件地址，无法预览（可点「下载」试试）" />
        </div>

        <!-- ============ 纯文本 ============ -->
        <div v-else-if="mode === 'text'" class="attach-preview__body">
          <a-alert
            v-if="textError"
            type="warning"
            show-icon
            :message="textError"
            description="文本预览走的是浏览器直接读取静态地址；若是跨域或权限问题，请改用「下载」查看。" />
          <a-spin v-else :spinning="textLoading">
            <pre class="attach-preview__text">{{ textContent || '（文件内容为空）' }}</pre>
          </a-spin>
        </div>

        <!-- ============ Office / 其它格式 ============ -->
        <div v-else class="attach-preview__body attach-preview__body--plain">
          <a-empty :description="previewModeText(mode)">
            <a-button type="primary" icon="download" @click="handleDownload">下载后用本机程序打开</a-button>
          </a-empty>
        </div>
      </div>
    </a-spin>
  </a-modal>
</template>

<script>
  import {
    bizTypeText,
    buildStaticUrl,
    downloadAttachment,
    previewModeText,
    queryPreviewUrl,
    resolvePreviewMode
  } from '@/api/land/attachment'
  import { bizTypeColor } from '@/api/land/dataRecycle'

  /**
   * 配套附件 - 预览弹窗
   *
   * ★ 为什么预览要调一次 `/previewUrl` 接口，而不是直接用列表行里的字段：
   *   列表返回的是「列表需要的字段」，预览要的预览方式（image / pdf / office / text / download）
   *   与相对存储路径由服务端按同一个白名单算出来。前端另猜一套（按扩展名自己判断）
   *   迟早与后端不一致 —— 典型症状是「后端说不能预览、前端硬要 iframe，用户看到一片空白」。
   *   这里只在**后端没给 previewMode 时**才用 `resolvePreviewMode` 兜底。
   *
   * ★ 为什么不做「临时目录 + 转 PDF」那一套：
   *   旧系统每次预览前清空整个 temp 目录，并发时会互相删文件（设计文档实测）。
   *   新方案是「后端给相对路径 + 前端拼静态地址」，`/sys/common/static/**` 是匿名放行的，
   *   图片与 PDF 可以直接塞进 img / iframe，不需要带 token，也不需要任何临时文件。
   *
   * ★ 为什么 Office 与未知格式不硬塞 iframe：
   *   浏览器不能渲染 doc/xls/ppt，iframe 打开只会触发下载或白屏。
   *   旧系统是 window.open 裸打开，用户看到的是「下载了一个文件但没打开」，以为系统坏了。
   *   这里明确说「该格式不支持在线预览」并给出下载按钮。
   *
   * ★ 文本预览用 fetch 而不是 iframe：
   *   iframe 展示 txt/csv 时中文编码、换行都不受控；fetch 成文本塞进 <pre> 既保留了
   *   原始换行，也能在失败时给出「跨域 / 权限」这种可操作的提示，而不是一片空白。
   */
  export default {
    name: 'AttachmentPreviewModal',
    data () {
      return {
        visible: false,
        loading: false,
        record: null,
        info: null,
        loadError: '',
        textContent: '',
        textError: '',
        textLoading: false
      }
    },
    computed: {
      fileName () {
        if (this.info && this.info.fileName) {
          return this.info.fileName
        }
        return this.record ? this.record.fileName : ''
      },
      fileTypeText () {
        if (this.info && this.info.fileTypeText) {
          return this.info.fileTypeText
        }
        return this.record ? this.record.fileTypeText : ''
      },
      readableSize () {
        return this.record ? (this.record.readableSize || '') : ''
      },
      /** 预览方式：优先用后端给的，后端没给才按扩展名兜底 */
      mode () {
        if (this.info && this.info.previewMode) {
          return this.info.previewMode
        }
        const ext = this.record ? this.record.fileExt : ''
        return resolvePreviewMode(ext)
      },
      staticUrl () {
        const storePath = this.info ? this.info.storePath : null
        return buildStaticUrl(storePath)
      }
    },
    methods: {
      previewModeText: previewModeText,
      bizTypeText: bizTypeText,
      bizTypeColor: bizTypeColor,

      /** @param {object} record 附件列表行（至少有 id / fileName / fileExt / bizType / bizKey） */
      open (record) {
        if (!record || !record.id) {
          return
        }
        this.record = record
        this.info = null
        this.loadError = ''
        this.textContent = ''
        this.textError = ''
        this.visible = true
        this.load()
      },
      handleClose () {
        this.visible = false
      },
      load () {
        this.loading = true
        queryPreviewUrl(this.record.id).then(res => {
          if (res.success && res.result) {
            this.info = res.result
            if (this.mode === 'text') {
              this.loadText()
            }
          } else {
            this.loadError = res.message || '取预览地址失败（附件可能已被删除）'
          }
        }).catch(e => {
          this.loadError = (e && e.message) || '取预览地址失败'
        }).finally(() => {
          this.loading = false
        })
      },
      loadText () {
        if (!this.staticUrl) {
          this.textError = '没有取到文件地址，无法读取文本内容'
          return
        }
        this.textLoading = true
        fetch(this.staticUrl).then(response => {
          if (!response.ok) {
            throw new Error(`读取文件失败（HTTP ${response.status}）`)
          }
          return response.text()
        }).then(text => {
          this.textContent = text
        }).catch(e => {
          this.textError = (e && e.message) || '读取文件内容失败'
        }).finally(() => {
          this.textLoading = false
        })
      },
      handleDownload () {
        if (!this.record) {
          return
        }
        // 走后端流式接口下载（服务端才会累加下载次数），不要直接把静态地址另存为
        downloadAttachment(this.record.id, this.fileName || this.record.fileName)
        this.$message.success('已开始下载')
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .attach-preview {
    &__head {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
      padding-bottom: 10px;
      margin-bottom: 12px;
      border-bottom: 1px solid @border-color;
    }

    &__head-main {
      min-width: 0;
    }

    &__name {
      font-size: 15px;
      font-weight: 600;
      color: #0f172a;
      word-break: break-all;
    }

    &__meta {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
      margin-top: 6px;
      font-size: 12px;
      color: @text-weak;
    }

    &__key {
      font-family: 'Consolas', 'Monaco', monospace;
      color: @text-muted;
    }

    &__size,
    &__mode {
      color: @text-weak;
    }

    &__head-actions {
      display: flex;
      flex: 0 0 auto;
      gap: 8px;
    }

    &__body {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 320px;
      max-height: 62vh;
      overflow: auto;
      background: #f8fafc;
      border: 1px solid @border-color;
      border-radius: 8px;

      &--plain {
        min-height: 200px;
      }
    }

    &__image {
      max-width: 100%;
      max-height: 60vh;
      object-fit: contain;
    }

    &__frame {
      width: 100%;
      height: 62vh;
      border: 0;
    }

    &__text {
      box-sizing: border-box;
      width: 100%;
      max-height: 60vh;
      padding: 12px 14px;
      margin: 0;
      overflow: auto;
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 12px;
      line-height: 20px;
      color: #0f172a;
      white-space: pre-wrap;
      word-break: break-all;
      text-align: left;
      background: #fff;
      border: 0;
    }
  }
</style>
