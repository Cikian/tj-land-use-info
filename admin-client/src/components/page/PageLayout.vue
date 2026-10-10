<template>
  <div
    :style="!$route.meta.pageHeader ? 'margin: -10px -24px 0;' : null"
    class="page-layout-root"
  >
    <!-- pageHeader , route meta hideHeader:true on hide -->
    <page-header v-if="!$route.meta.pageHeader" :title="title" :logo="logo" :avatar="avatar">
      <slot slot="action" name="action"></slot>
      <slot slot="content" name="headerContent"></slot>
      <div slot="content" v-if="!this.$slots.headerContent && desc">
        <p style="font-size: 14px;color: rgba(0,0,0,.65)">{{ desc }}</p>
        <div class="link">
          <template v-for="(link, index) in linkList">
            <a :key="index" :href="link.href">
              <a-icon :type="link.icon"/>
              <span>{{ link.title }}</span>
            </a>
          </template>
        </div>
      </div>
      <slot slot="extra" name="extra"></slot>
      <div slot="pageMenu">
        <div class="page-menu-search" v-if="search">
          <a-input-search style="width: 80%; max-width: 522px;" placeholder="请输入..." size="large" enterButton="搜索" />
        </div>
        <div class="page-menu-tabs" v-if="tabs && tabs.items">
          <!-- @change="callback" :activeKey="activeKey" -->
          <a-tabs :tabBarStyle="{margin: 0}" @change="tabs.callback" :activeKey="tabs.active()">
            <a-tab-pane v-for="item in tabs.items" :tab="item.title" :key="item.key"></a-tab-pane>
          </a-tabs>
        </div>
      </div>
    </page-header>
    <div class="content">
      <div :class="['page-header-index-wide']">
        <slot></slot>
      </div>
    </div>
  </div>
</template>

<script>
  import PageHeader from './PageHeader'

  export default {
    name: "LayoutContent",
    components: {
      PageHeader
    },
    // ['desc', 'logo', 'title', 'avatar', 'linkList', 'extraImage']
    props: {
      desc: {
        type: String,
        default: null
      },
      logo: {
        type: String,
        default: null
      },
      title: {
        type: String,
        default: null
      },
      avatar: {
        type: String,
        default: null
      },
      linkList: {
        type: Array,
        default: null
      },
      extraImage: {
        type: String,
        default: null
      },
      search: {
        type: Boolean,
        default: false
      },
      tabs: {
        type: Object,
        default: () => {}
      }
    },
    methods: {
    }
  }
</script>

<style lang="less" scoped>
  .content {
    margin: 24px 24px 0;

    .link {
      margin-top: 16px;

      &:not(:empty) {
        margin-bottom: 16px;
      }
      a {
        margin-right: 32px;
        height: 24px;
        line-height: 24px;
        display: inline-block;

        i {
          font-size: 24px;
          margin-right: 8px;
          vertical-align: middle;
        }
        span {
          height: 24px;
          line-height: 24px;
          display: inline-block;
          vertical-align: middle;
        }
      }
    }
  }
  .page-menu-search {
    text-align: center;
    margin-bottom: 16px;
  }
  .page-menu-tabs {
    margin-top: 48px;
  }
  .page-header[data-v-6740ec88] {
    margin: 0px 24px 0;
  }

  /*
   * ★ 页面根：铺满内容区，并**由它自己承担滚动**。
   *
   *   为什么要这样：应用外壳已锁死视口高度（GlobalLayout 的 .layout），
   *   如果页面根不限定高度、只是自然增长，内容就会溢出外壳被裁掉（看不见也滚不到）。
   *   让页面根撑满剩余高度 + overflow-y:auto，
   *   滚动条就只出现一次 —— 在页面内容区，而不是整个文档。
   *
   *   display:flex + flex-direction:column + min-height:0 是**必须**的：
   *   flex 子项默认 min-height:auto 会按内容撑开，导致 overflow 失效
   *   （flex 布局里最常踩的一条）。
   *   height:100% 与 max-height:100% 一起给，是为了不论父级是
   *   「确定高度」还是「flex 剩余空间」都能兜住。
   */
  .page-layout-root {
    display: flex;
    flex-direction: column;
    height: 100%;
    max-height: 100%;
    min-height: 0;
    overflow-y: auto;
    overflow-x: hidden;
  }

  /* 内容区跟着长而不是被压扁 */
  .page-layout-root > .content {
    flex: 1 1 auto;
    min-height: 0;
  }
</style>
