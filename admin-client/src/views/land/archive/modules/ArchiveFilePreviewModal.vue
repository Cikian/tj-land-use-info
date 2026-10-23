<template>
  <a-modal
    :title="null"
    :width="1200"
    :visible="visible"
    :footer="null"
    :mask-closable="false"
    wrap-class-name="archive-file-preview-modal"
    @cancel="handleClose">
    <div class="archive-file-preview">
      <!-- ============ 页头：文件信息 + 操作 ============ -->
      <header class="archive-file-preview__head">
        <div class="archive-file-preview__head-main">
          <div class="archive-file-preview__name" :title="fileName">{{ fileName || '文件预览' }}</div>
          <div class="archive-file-preview__meta">
            <a-tag v-if="fileExt">{{ fileExt.toUpperCase() }}</a-tag>
            <span v-if="sizeText" class="archive-file-preview__size">{{ sizeText }}</span>
            <span class="archive-file-preview__mode">{{ previewModeText(mode) }}</span>
          </div>
        </div>
        <div class="archive-file-preview__head-actions">
          <a-button
            size="small"
            icon="export"
            :disabled="!previewTarget"
            @click="handleOpenInNewWindow">新窗口打开</a-button>
          <a-button
            size="small"
            type="primary"
            icon="download"
            :disabled="!downloadTarget"
            @click="handleDownload">下载</a-button>
          <a-button size="small" icon="close" @click="handleClose">关闭</a-button>
        </div>
      </header>

      <!--
        所有文件统一由 kkFileView 在线预览；首次打开可能需等待数秒。
        iframe 被 X-Frame-Options 拦截时页面会空白，此时用页头的「新窗口打开」。
      -->
      <p v-if="onlinePreviewUrl" class="archive-file-preview__hint">
        文件由 kkFileView 在线预览，首次打开可能需要等待数秒；若显示空白，请点「新窗口打开」或下载查看。
      </p>

      <!-- ============ 统一由 kkFileView 预览 ============ -->
      <div v-if="!fileUrl" class="archive-file-preview__body">
        <a-empty description="没有取到文件地址，无法预览">
          <a-button v-if="downloadTarget" type="primary" icon="download" @click="handleDownload">下载文件</a-button>
        </a-empty>
      </div>
      <div v-else class="archive-file-preview__body archive-file-preview__body--frame">
        <iframe
          v-if="onlinePreviewUrl"
          class="archive-file-preview__frame"
          :src="onlinePreviewUrl"
          :title="fileName" />
        <a-empty v-else description="未配置在线预览服务（VUE_APP_ONLINE_BASE_URL），请联系管理员">
          <a-button v-if="downloadTarget" type="primary" icon="download" @click="handleDownload">下载文件</a-button>
        </a-empty>
      </div>
    </div>
  </a-modal>
</template>

<script>
  import {
    buildOnlinePreviewUrl,
    previewModeText,
    resolvePreviewMode
  } from '@/api/land/filePreview'

  /**
   * 档案卷内文件预览弹窗。
   *
   * ★ 入参 file 的形状（由调用方组装，`url` 与 `downloadUrl` 都必须是完整地址）：
   *   { fileName, fileExt, fileSize, readableSize, previewMode, url, downloadUrl }
   *   - `url` 由后端 previewInfo 返回的相对路径经 `getFileAccessHttpUrl` 拼成；
   *   - `downloadUrl` 由调用方拼（带 token），弹窗本身不依赖 archive API，方便复用。
   *
   * ★ 2026-10-22 起**所有文件统一走 kkFileView**：
   *   浏览器直接打开 PDF 会被后端 Content-Disposition 变成下载，ZIP 直连也不会列目录；
   *   交给 kkFileView 才能正常显示，不支持的格式也由它自己的错误页反馈。
   *   iframe 被 X-Frame-Options 拦截时用「新窗口打开」兜底。
   */
  export default {
    name: 'ArchiveFilePreviewModal',
    data () {
      return {
        visible: false,
        file: {}
      }
    },
    computed: {
      fileName () {
        return this.file.fileName || ''
      },
      /** 扩展名只用于页头标签；历史数据可能没有 fileExt，用文件名兜底 */
      fileExt () {
        const ext = this.file.fileExt
        if (ext) {
          return String(ext).replace(/^\./, '')
        }
        const name = this.file.fileName || ''
        const dot = name.lastIndexOf('.')
        return dot >= 0 ? name.substring(dot + 1) : ''
      },
      fileUrl () {
        return this.file.url || ''
      },
      /** 预览方式：优先用后端算好的，只有临时文件/缺失时才按扩展名兜底 */
      mode () {
        if (this.file.previewMode) {
          return this.file.previewMode
        }
        return resolvePreviewMode(this.file.fileExt || this.file.fileName)
      },
      sizeText () {
        return this.file.readableSize || this.formatSize(this.file.fileSize)
      },
      /** 所有文件都交给 kkFileView */
      onlinePreviewUrl () {
        return buildOnlinePreviewUrl(this.fileUrl, this.fileName)
      },
      /** 新窗口打开的目标 */
      previewTarget () {
        return this.onlinePreviewUrl
      },
      downloadTarget () {
        return this.file.downloadUrl || this.fileUrl
      }
    },
    methods: {
      previewModeText: previewModeText,

      /** @param {object} file 见组件头部的形状说明 */
      open (file) {
        this.file = file || {}
        this.visible = true
      },
      handleClose () {
        this.visible = false
      },
      handleOpenInNewWindow () {
        if (!this.previewTarget) {
          this.$message.warning('没有可打开的文件地址')
          return
        }
        window.open(this.previewTarget, '_blank')
      },
      handleDownload () {
        if (!this.downloadTarget) {
          this.$message.warning('没有可下载的文件地址')
          return
        }
        // downloadUrl 由调用方拼好（含 token），这里只负责打开；不要用 axios 拉 blob
        window.open(this.downloadTarget, '_blank')
      },
      formatSize (bytes) {
        const value = Number(bytes)
        if (!value) {
          return '—'
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
  @border-color: #e2e8f0;
  @text-weak: #94a3b8;

  .archive-file-preview {
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

    &__size,
    &__mode {
      color: @text-weak;
    }

    &__head-actions {
      display: flex;
      flex: 0 0 auto;
      gap: 8px;
    }

    &__hint {
      margin: -2px 0 10px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;
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

      &--frame {
        display: block;
        padding: 0;
        background: #fff;
        border: 0;
      }

      &--text {
        display: block;
        background: #fff;
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
