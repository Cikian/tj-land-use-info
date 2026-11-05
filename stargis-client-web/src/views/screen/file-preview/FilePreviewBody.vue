<template>
  <!--
    FilePreviewBody 文件预览内容区（纯展示，不含弹窗壳）
    --------------------------------
    ★ 2026-10-22 起**所有文件统一走 kkFileView**，所以这里只剩一个 iframe：
      图片、PDF、Office/WPS、ZIP、CAD、音视频、文本……都由 kkFileView 渲染。
      前端不再按扩展名选择 <img> / <iframe> / fetch+<pre>，避免出现
      「PDF 直连被 Content-Disposition 变成下载」「ZIP 直连浏览器不会列目录」
      这类与预览服务行为不一致的问题。

    父组件只负责把 **kkFileView 的 onlinePreview 地址** 传进来；
    不支持/转换失败由 kkFileView 自己的错误页反馈，业务侧不再提前判死。
  -->
  <div class="file-preview-body">
    <div class="file-preview-body__frame">
      <iframe
        v-if="url"
        :src="url"
        class="file-preview-body__iframe"
        :title="fileName || '文件预览'"
      />
      <div v-else class="file-preview-body__fallback">
        <screen-empty
          text="在线预览服务未就绪"
          description="未配置 kkFileView 在线预览服务（前端 VUE_APP_ONLINE_BASE_URL），请联系管理员；也可以先下载后本地打开。"
        />
      </div>
    </div>
  </div>
</template>

<script>
import { ScreenEmpty } from '@/components/screen'

export default {
  name: 'FilePreviewBody',
  components: { ScreenEmpty },
  props: {
    /** 仅用于界面标签，不再决定渲染器 */
    mode: { type: String, default: 'online' },
    /** kkFileView 的 onlinePreview 地址 */
    url: { type: String, default: '' },
    fileName: { type: String, default: '' }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.file-preview-body {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  height: 100%;

  &__frame {
    display: flex;
    flex: 1 1 auto;
    min-height: 0;
    background: #ffffff;
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
    overflow: hidden;
  }

  &__iframe {
    flex: 1 1 auto;
    width: 100%;
    /* 预览弹窗整体放大后，iframe 跟着撑高；68vh 在 1080p 下约 730px */
    min-height: 68vh;
    border: 0;
    background: #ffffff;
  }

  &__fallback {
    display: flex;
    flex: 1 1 auto;
    align-items: center;
    justify-content: center;
    min-height: 320px;
    padding: var(--screen-space-6);
  }
}
</style>
