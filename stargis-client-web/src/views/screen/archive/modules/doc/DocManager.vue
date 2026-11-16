<template>
  <!--
    DocManager 收发文管理（方案 2.3.2 第 9 项）
    ===============================================================
    「收发文管理」这一个二级菜单下包含两个互相独立的业务：
      · 收文管理：登记 → 承办 → 办结，支持转办 / 退回，办结后可归档
      · 发文管理：纯信息记录台账，登记后可直接归档，没有流转

    两者共用「归档到档案管理」的弹窗、附件表与详情弹窗，
    所以这里只用一个页签壳把它们分开，而不是拆成两个顶级菜单
    （避免档案管理下出现五个以上的并列项）。

    结构与档案页上层保持一致：页签放在面板标题栏内，
    下方模块用 keep-alive 缓存（只缓存**组件实例**，省掉重复拉字典）。
    ★ 检索条件、分页**不保留**：没有下钻参数时页签壳会调本组件的 resetFilters()，
      再由它转发给当前子面板 —— 缓存下来的旧条件会变成隐性过滤（P2-01）。
  -->
  <div class="doc-manager">
    <screen-panel class="doc-manager__tabs-panel" :bar="false">
      <template #title>
        <screen-tabs v-model="activeTab" :tabs="tabs" />
      </template>
      <template #extra>
        <span class="doc-manager__tip">
          <screen-icon name="info" :size="13" />
          收文有流转（转办 / 退回 / 办结），发文是纯台账；两者的附件都会成为归档后的卷内文件
        </span>
      </template>
    </screen-panel>

    <div class="doc-manager__body">
      <keep-alive>
        <!--
          drill 必须转发：收文/发文归档成功后要「查看档案」，由子面板 $emit('drill', {archiveId})，
          页签壳（archive/index.vue）的 handleDrill 才能切到档案维护并打开档案详情。
          子组件的事件不会自己冒到页签壳上，少这一行就是「点了查看档案没反应」。
        -->
        <component
          :is="currentPanel"
          ref="panel"
          @drill="$emit('drill', $event)"
          @open-statistics="$emit('open-statistics')"
        />
      </keep-alive>
    </div>
  </div>
</template>

<script>
import { ScreenPanel, ScreenTabs, ScreenIcon } from '@/components/screen'
import DocReceivePanel from './DocReceivePanel.vue'
import DocSendPanel from './DocSendPanel.vue'

const PANELS = {
  receive: DocReceivePanel,
  send: DocSendPanel
}

export default {
  name: 'DocManager',
  components: { ScreenPanel, ScreenTabs, ScreenIcon },
  props: {
    /** 初始子页签：receive 收文 / send 发文 */
    defaultTab: { type: String, default: 'receive' }
  },
  data () {
    return {
      activeTab: PANELS[this.defaultTab] ? this.defaultTab : 'receive',
      tabs: [
        { key: 'receive', label: '收文管理' },
        { key: 'send', label: '发文管理' }
      ]
    }
  },
  computed: {
    currentPanel () {
      return PANELS[this.activeTab] || DocReceivePanel
    }
  },
  methods: {
    /** 供外部（路由 / 父组件）切换子页签 */
    setTab (key) {
      if (PANELS[key]) this.activeTab = key
    },

    /**
     * 转发「清空检索条件」给当前子面板（P2-01 契约）。
     *
     * ★ 为什么必须在本层暴露：页签壳（archive/index.vue）的 `$refs.panel` 是本组件，
     *   它只认本层的 resetFilters；收文 / 发文面板再各自有一份实现，
     *   不转发的话「切回收发文管理」时旧条件仍然生效，而页签壳以为已经清过了。
     */
    resetFilters () {
      const panel = this.$refs.panel
      if (panel && typeof panel.resetFilters === 'function') {
        panel.resetFilters()
      }
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.doc-manager {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  &__tabs-panel {
    flex: 0 0 auto;
  }

  &__tip {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    white-space: nowrap;
  }

  &__body {
    flex: 1 1 0;
    min-height: 0;
  }
}

// 与档案页顶部的页签条同样压到 42px，保持两层页签的视觉节奏一致
.doc-manager__tabs-panel /deep/ .screen-panel__head {
  height: 42px;
  padding-left: 8px;
}

// 说明文案在窄屏会挤掉页签，直接隐藏（信息不是必需的）
@media (max-width: 1600px) {
  .doc-manager__tip {
    display: none;
  }
}
</style>
