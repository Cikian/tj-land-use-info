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
        <div v-for="project in projects" :key="project.bizId" class="attachment-tree__project">
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
              <span class="attachment-tree__project-name">{{ project.bizKey || '未命名' }}</span>
            </button>

            <span class="attachment-tree__badge">
              {{ project.totalFiles }} 个 · {{ formatSize(project.totalSize) }} · {{ project.typeCount }} 类
            </span>

            <span v-if="editable" class="attachment-tree__ops">
              <button
                type="button"
                class="attachment-tree__link"
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
import { formatSize } from '@/views/screen/data/constants'
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
    /** 是否显示上传 / 删除按钮。详情页只读时传 false */
    editable: { type: Boolean, default: true },
    emptyText: { type: String, default: '还没有附件' }
  },
  data () {
    return {
      /** 收起的项目 id（默认全展开） */
      collapsedProjects: []
    }
  },
  computed: {
    groups () {
      return (this.tree && this.tree.groups) || []
    }
  },
  watch: {
    /**
     * 项目集合变了（切换检索条件 / 刷新）就恢复全展开。
     * ★ 只在集合真的变化时重置，避免用户收起某个项目后一次刷新被强行展开。
     */
    projects: {
      handler (next) {
        const ids = (next || []).map(p => p.bizId)
        if (!this.collapsedProjects.every(id => ids.indexOf(id) > -1)) {
          this.collapsedProjects = []
        }
      }
    }
  },
  methods: {
    formatSize,

    /** 某个项目下要渲染的材料类型分组 */
    groupListOf (project) {
      return (project && project.tree && project.tree.groups) || []
    },

    isProjectExpanded (bizId) {
      return this.collapsedProjects.indexOf(bizId) < 0
    },

    toggleProject (bizId) {
      const index = this.collapsedProjects.indexOf(bizId)
      if (index > -1) {
        this.collapsedProjects.splice(index, 1)
      } else {
        this.collapsedProjects.push(bizId)
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

  &__loading,
  &__empty {
    margin: 0;
    padding: var(--screen-space-3) 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    text-align: center;
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
