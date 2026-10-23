<template>
  <a-modal
    :title="null"
    :width="1200"
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
            <!--
              ★ 为什么还要「新窗口打开」：
                kkFileView 是在服务端把 Office 转成 HTML 再渲染的，部分部署会带
                X-Frame-Options，iframe 里一片空白；此时新窗口打开是唯一的兜底方式。
            -->
            <a-button
              size="small"
              icon="export"
              :disabled="!onlinePreviewUrl && !staticUrl"
              @click="handleOpenInNewWindow">新窗口打开</a-button>
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

        <!-- ============ 统一由 kkFileView 预览 ============ -->
        <div v-else class="attach-preview__body attach-preview__body--office">
          <iframe
            v-if="onlinePreviewUrl"
            class="attach-preview__frame"
            :src="onlinePreviewUrl"
            :title="fileName" />
          <a-empty v-else description="未配置在线预览服务（VUE_APP_ONLINE_BASE_URL），请联系管理员">
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
  import { buildOnlinePreviewUrl } from '@/api/land/filePreview'
  import { bizTypeColor } from '@/api/land/dataRecycle'

  /**
   * 配套附件 - 预览弹窗
   *
   * ★ 为什么预览要调一次 `/previewUrl` 接口，而不是直接用列表行里的字段：
   *   列表返回的是「列表需要的字段」，预览要的 previewMode 与相对存储路径由服务端统一算出来。
   *   前端另猜一套（按扩展名自己判断）迟早与后端不一致。
   *   这里只在**后端没给 previewMode 时**才用 `resolvePreviewMode` 兜底。
   *
   * ★ 2026-10-22 起**所有文件统一走 kkFileView**：
   *   浏览器直接打开 PDF 会被后端的 Content-Disposition 变成下载，ZIP 直连也不会列目录；
   *   交给 kkFileView 才能正常显示。不支持的格式也由 kkFileView 自己的错误页反馈，
   *   业务侧不再按扩展名提前判死。
   *   若部署侧给 kkFileView 设了 X-Frame-Options，iframe 会空白，因此页头保留「新窗口打开」。
   */
  export default {
    name: 'AttachmentPreviewModal',
    data () {
      return {
        visible: false,
        loading: false,
        record: null,
        info: null,
        loadError: ''
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
        // 历史数据可能只存了 fileName 没存 fileExt，交给 resolvePreviewMode 兼容两种入参
        const ext = this.record ? (this.record.fileExt || this.record.fileName) : ''
        return resolvePreviewMode(ext)
      },
      staticUrl () {
        const storePath = this.info ? this.info.storePath : null
        return buildStaticUrl(storePath)
      },
      /**
       * 实际用于打开文件的地址：**所有文件都交给 kkFileView**。
       * 没配置预览服务时 buildOnlinePreviewUrl 返回空串，模板据此提示管理员。
       */
      onlinePreviewUrl () {
        return buildOnlinePreviewUrl(this.staticUrl, this.fileName)
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
          } else {
            this.loadError = res.message || '取预览地址失败（附件可能已被删除）'
          }
        }).catch(e => {
          this.loadError = (e && e.message) || '取预览地址失败'
        }).finally(() => {
          this.loading = false
        })
      },
      handleOpenInNewWindow () {
        const target = this.onlinePreviewUrl
        if (!target) {
          this.$message.warning('没有取到文件地址，无法打开')
          return
        }
        window.open(target, '_blank')
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

      // kkFileView 的转换结果自带纸张底色，外层再套灰底与边框会显得脏
      &--office {
        background: #fff;
        border: 0;
      }
    }

    &__image {
      max-width: 100%;
      max-height: 60vh;
      object-fit: contain;
    }

    &__frame {
      width: 100%;
      height: 68vh;
      background: #fff;
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
