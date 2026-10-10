<template>
  <!--
    AttachmentTreeView 附件目录树
    --------------------------------
    两种用法（二选一）：

    ① 单业务对象（地块/配套详情弹窗内）—— 传 tree：
         项建批复文件      3 个 · 12.4 MB
           立项批复.pdf
         道路规划          1 个 · 3.1 MB
           道路规划图.dwg

    ② 多项目（附件管理页）—— 传 projects：
         ▾ 义安路（永顺道-永尚道）   29 个 · 88.4 MB · 5 类
             项建批复文件             1 个 · 1.2 MB
             专业管理意见             9 个 · 31.0 MB
         ▸ 秀清路（光兴道-光谷道）    3 个 · 4.1 MB · 2 类

    ★ 为什么附件管理页要按项目分组：
      那个页面是**跨项目**的（能筛所有宗地/配套的附件），
      而「目录 = 材料类型」只在单个项目内部有意义 —— 不同项目的
      「道路规划」是两个不同的目录，混在一棵树下会看不出归属。

    ★ 空类型不进树（后端只返回有文件的类型）。

    事件：
      upload (group)            上传到某个材料类型
      upload-project (project)  上传到整个项目（材料类型在弹窗里选）
      preview / download / remove (file)
  -->
  <div class="attachment-tree">
    <div v-if="loading" class="attachment-tree__loading">加载中…</div>

    <template v-else>
      <!-- ① 多项目模式 -->
      <template v-if="projects.length">
        <!-- ★ key 带上 bizType：不限归属类型时 land 与 facility 会同时出现，
                 同一个 bizId 理论上可能撞（两者来自不同的表） -->
        <div v-for="project in projects" :key="`${project.bizType}:${project.bizId}`" class="attachment-tree__project">
          <div class="attachment-tree__project-head">
            <button
              type="button"
              class="attachment-tree__project-toggle"
              :aria-expanded="isProjectExpanded(project.bizId) ? 'true' : 'false'"
              :title="project.bizKey"
              @click="toggleProject(project.bizId)"
            >
              <screen-icon :name="isProjectExpanded(project.bizId) ? 'chevron-down' : 'chevron-right'" :size="12" />
              <screen-icon name="layers" :size="13" class="attachment-tree__project-icon" />
              <!-- 归属类型标签：不限类型时同一棵树下混着宗地与配套，不标出来分不清 -->
              <span v-if="project.bizType" class="attachment-tree__type">
                {{ bizTypeText(project.bizType) }}
              </span>
              <span class="attachment-tree__project-name">{{ project.bizKey || '未命名' }}</span>
            </button>

            <span class="attachment-tree__badge">
              {{ project.totalFiles }} 个 · {{ formatSize(project.totalSize) }} · {{ project.typeCount }} 类
            </span>

            <span v-if="editable" class="attachment-tree__ops">
              <button
                type="button"
                class="attachment-tree__link"
                title="上传到该项目（在弹窗里选择文件或文件夹）"
                @click.stop="$emit('upload-project', project)"
              >上传到该项目</button>
            </span>
          </div>

          <div v-if="isProjectExpanded(project.bizId)" class="attachment-tree__project-body">
            <attachment-type-group
              v-for="group in groupListOf(project)"
              :key="group.key"
              :group="group"
              :editable="editable"
              @upload="$emit('upload', $event)"
              @preview="$emit('preview', $event)"
              @download="$emit('download', $event)"
              @remove="$emit('remove', $event)"
            />
          </div>
        </div>

        <!--
          「显示更多」而不是分页。
          ★ 树视图里分页是**不可用**的：分页会把某个项目截成两半
            （第 1 页有它的 3 个类型、第 2 页有另外 2 个），
            树的结构就断了 —— 而树的价值正是「一眼看全这个项目有什么」。
            所以这里用「载入更多项目」：已加载的项目保持完整，只是继续往后取。
          ★ 只在真的还有更多时才出现，避免无意义的按钮。
        -->
        <button
          v-if="hasMore"
          type="button"
          class="attachment-tree__more"
          :disabled="loading"
          @click="$emit('load-more')"
        >
          显示更多项目（已显示 {{ projects.length }} 个）
        </button>
      </template>

      <!-- ② 单业务对象模式 -->
      <template v-else>
        <attachment-type-group
          v-for="group in groups"
          :key="group.key"
          :group="group"
          :editable="editable"
          @upload="$emit('upload', $event)"
          @preview="$emit('preview', $event)"
          @download="$emit('download', $event)"
          @remove="$emit('remove', $event)"
        />

        <p v-if="!groups.length" class="attachment-tree__empty">{{ emptyText }}</p>
      </template>
    </template>
  </div>
</template>

<script>
import { ScreenIcon } from '@/components/screen'
import { formatSize, bizTypeText } from '@/views/screen/data/constants'
import AttachmentTypeGroup from './AttachmentTypeGroup.vue'

export default {
  name: 'AttachmentTreeView',
  components: { ScreenIcon, AttachmentTypeGroup },
  props: {
    /**
     * 单业务对象的树：后端 /attachment/tree 的 result
     * { bizType, bizKey, groups:[{key,fileType,fileTypeName,files,fileCount,totalSize}],
     *   totalFiles, totalSize, typeCount }
     */
    tree: { type: Object, default: () => ({ groups: [] }) },
    /**
     * 多项目的树：后端 /attachment/byProject 的 result
     * [{ bizId, bizKey, tree:{ groups:[...], totalFiles, totalSize, typeCount } }, ...]
     */
    projects: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    /** 后端还有没有更多项目（配合 load-more 事件做「显示更多」） */
    hasMore: { type: Boolean, default: false },
    /** 是否显示上传 / 删除按钮。详情页只读时传 false */
    editable: { type: Boolean, default: true },
    emptyText: { type: String, default: '还没有附件' }
  },
  data () {
    return {
      /**
       * **已展开**的项目 id。
       * ★ 为什么记「展开的」而不是「收起的」：默认要全收起，
       *   记收起集合就得在数据到达时把每个 id 都塞进去；
       *   记展开集合时初始为空数组，天然就是全收起。
       */
      expandedProjects: []
    }
  },
  computed: {
    groups () {
      return (this.tree && this.tree.groups) || []
    }
  },
  methods: {
    formatSize,
    bizTypeText,

    /** 某个项目下要渲染的材料类型分组 */
    groupListOf (project) {
      return (project && project.tree && project.tree.groups) || []
    },

    isProjectExpanded (bizId) {
      return this.expandedProjects.indexOf(bizId) > -1
    },

    toggleProject (bizId) {
      const index = this.expandedProjects.indexOf(bizId)
      if (index > -1) {
        this.expandedProjects.splice(index, 1)
      } else {
        this.expandedProjects.push(bizId)
      }
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.attachment-tree {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-2);
  min-width: 0;

  /*
   * ★ 这里**故意不做**自己的滚动，交给面板内容区（ScreenPanel scrollable）。
   *   之前给树加了 max-height + overflow-y: auto，结果是两个滚动容器打架：
   *   树被自己的 max-height 截断，而面板内容区又几乎不溢出（不显示自己的滚动条），
   *   表现就是「内容被切掉、又找不到滚动条」。
   *   只留一个滚动容器，行为才是可预期的。
   */

  &__loading,
  &__empty {
    margin: 0;
    padding: var(--screen-space-3) 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    text-align: center;
  }

  /* 「显示更多项目」——详见模板里的说明：树视图不能用分页 */
  &__more {
    align-self: center;
    margin-top: var(--screen-space-2);
    padding: 4px var(--screen-space-3);
    font-family: inherit;
    font-size: var(--screen-font-xs);
    color: var(--screen-accent-soft);
    background: rgba(103, 178, 255, 0.1);
    border: 1px solid rgba(103, 178, 255, 0.28);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: background var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover:not(:disabled) {
      background: rgba(103, 178, 255, 0.2);
    }

    &:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  }

  &__project {
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
    overflow: hidden;
  }

  &__project-head {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    padding: 7px var(--screen-space-2);
    background: rgba(255, 255, 255, 0.03);
    transition: background var(--screen-duration) var(--screen-ease);

    &:hover {
      background: rgba(255, 255, 255, 0.06);

      .attachment-tree__ops {
        opacity: 1;
      }
    }
  }

  &__project-toggle {
    flex: 0 1 auto;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    min-width: 0;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    font-weight: 600;
    color: var(--screen-text);
    background: none;
    border: 0;
    cursor: pointer;
    .screen-focus-ring();
  }

  &__project-icon {
    flex: 0 0 auto;
    color: var(--screen-accent-bright);
  }

  &__project-name {
    min-width: 0;
    .screen-ellipsis();
  }

  /* 归属类型标签（不限类型时同树下混着宗地与配套） */
  &__type {
    flex: 0 0 auto;
    padding: 0 5px;
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-accent-soft);
    background: rgba(103, 178, 255, 0.12);
    border-radius: var(--screen-radius-sm);
  }

  &__project-body {
    padding: 0 var(--screen-space-2) var(--screen-space-2);
  }

  &__badge {
    flex: 0 0 auto;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__ops {
    flex: 0 0 auto;
    margin-left: auto;
    opacity: 0;
    transition: opacity var(--screen-duration) var(--screen-ease);
  }

  &__link {
    .screen-link-action();

    &.is-danger {
      color: var(--screen-danger);
    }
  }
}

@media (max-width: 1400px) {
  .attachment-tree__project-head {
    flex-wrap: wrap;
  }
}
</style>
