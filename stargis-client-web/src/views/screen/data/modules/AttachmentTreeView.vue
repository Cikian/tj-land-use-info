<template>
  <!--
    AttachmentTreeView 附件目录树
    --------------------------------
    把「一个业务对象下的附件」按目录结构渲染成树，目录节点显示
    文件数 / 总大小 / 空目录标记，文件节点直接带预览、下载、删除操作。

    ★ 为什么不用现成的 ScreenTree：
      ScreenTree 是「档案类别树」的单选选择器（选中一个节点、拖拽排序），
      而这里要的是**复合节点**（目录 + 文件混在一棵树里）+ 每个文件行内操作 +
      目录的增删改按钮。硬套会让两边都变形 —— 分成两个组件各自更简单。

    ★ 树数据由后端一次性返回（/attachmentDir/tree）：
      目录与文件都在同一份结构里，所以「展开即见文件」，不需要逐层请求，
      也不会出现展开后再转圈的情况。

    事件：
      preview (file)  预览附件
      download (file) 下载附件
      remove (file)   删除附件
      changed         目录或文件发生变更，父组件据此刷新
  -->
  <div class="attachment-tree">
    <!-- 工具条：目录操作 + 展开/收起 -->
    <div v-if="editable" class="attachment-tree__bar">
      <screen-button size="sm" icon="plus" @click="handleAddDir">新建目录</screen-button>
      <screen-button size="sm" icon="chevron-down" @click="expandAll">全部展开</screen-button>
      <screen-button size="sm" icon="chevron-up" @click="collapseAll">全部收起</screen-button>
    </div>

    <div v-if="loading" class="attachment-tree__loading">加载中…</div>

    <template v-else>
      <!-- 根目录：它不是"目录节点"（后端不存空路径），但文件要能看见 -->
      <ul class="attachment-tree__list" role="tree" aria-label="附件目录">
        <li v-for="file in rootFiles" :key="file.id" class="attachment-tree__row is-file">
          <span class="attachment-tree__indent" style="width: 22px" aria-hidden="true" />
          <screen-icon name="file" :size="13" class="attachment-tree__icon" />
          <span class="attachment-tree__label" :title="file.fileName">{{ file.fileName }}</span>
          <span class="attachment-tree__size">{{ file.readableSize || formatSize(file.fileSize) }}</span>
          <span class="attachment-tree__actions">
            <slot name="file-actions" :file="file">
              <button type="button" class="attachment-tree__link" @click="$emit('preview', file)">预览</button>
              <button type="button" class="attachment-tree__link" @click="$emit('download', file)">下载</button>
              <button
                v-if="editable"
                type="button"
                class="attachment-tree__link is-danger"
                @click="$emit('remove', file)"
              >删除</button>
            </slot>
          </span>
        </li>

        <attachment-tree-node
          v-for="node in nodes"
          :key="node.key"
          :node="node"
          :level="0"
          :expanded-keys="expandedKeys"
          :editable="editable"
          @toggle="handleToggle"
          @preview="$emit('preview', $event)"
          @download="$emit('download', $event)"
          @remove="$emit('remove', $event)"
          @rename-dir="handleRenameDir"
          @delete-dir="handleDeleteDir"
        >
          <template #file-actions="{ file }">
            <slot name="file-actions" :file="file" />
          </template>
        </attachment-tree-node>
      </ul>

      <p v-if="!nodes.length && !rootFiles.length" class="attachment-tree__empty">
        {{ emptyText }}
      </p>
    </template>
  </div>
</template>

<script>
import { ScreenButton, ScreenIcon } from '@/components/screen'
import { formatSize } from '@/views/screen/data/constants'
import AttachmentTreeNode from './AttachmentTreeNode.vue'

export default {
  name: 'AttachmentTreeView',
  components: {
    ScreenButton,
    ScreenIcon,
    AttachmentTreeNode
  },
  props: {
    /** 后端 /attachmentDir/tree 的 result：{ nodes, rootFiles, totalFiles, totalSize, totalDirs } */
    tree: { type: Object, default: () => ({ nodes: [], rootFiles: [] }) },
    loading: { type: Boolean, default: false },
    /** 是否显示目录维护按钮（新建 / 重命名 / 删除）。详情页只读时传 false */
    editable: { type: Boolean, default: true },
    emptyText: { type: String, default: '还没有附件' }
  },
  data () {
    return {
      /** 展开的目录 key（dir:路径）。默认全部展开，让目录结构一眼可见 */
      expandedKeys: []
    }
  },
  computed: {
    nodes () {
      return (this.tree && this.tree.nodes) || []
    },
    rootFiles () {
      return (this.tree && this.tree.rootFiles) || []
    }
  },
  watch: {
    /**
     * 数据换了（切换业务对象 / 刷新）就重新展开全部。
     * ★ 只在**节点集合变化**时重置，避免用户手动收起某个目录后
     *   一次刷新就被强行展开回去。
     */
    nodes: {
      handler (next) {
        const keys = this.collectKeys(next)
        const sameShape = keys.length === this.expandedKeys.length &&
          keys.every(k => this.expandedKeys.indexOf(k) > -1)
        if (!sameShape) {
          this.expandedKeys = keys
        }
      },
      immediate: true
    }
  },
  methods: {
    formatSize,

    /** 收集所有目录 key（供全部展开） */
    collectKeys (list, acc) {
      const result = acc || []
      ;(list || []).forEach(node => {
        if (node.children && node.children.length) {
          result.push(node.key)
          this.collectKeys(node.children, result)
        }
      })
      return result
    },

    handleToggle (key) {
      const index = this.expandedKeys.indexOf(key)
      if (index > -1) {
        this.expandedKeys.splice(index, 1)
      } else {
        this.expandedKeys.push(key)
      }
    },

    expandAll () {
      this.expandedKeys = this.collectKeys(this.nodes)
    },

    collapseAll () {
      this.expandedKeys = []
    },

    /* ---------------- 目录维护：只把意图抛给父组件，弹窗/请求由父组件做 ---------------- */

    handleAddDir () {
      this.$emit('add-dir')
    },

    handleRenameDir (node) {
      this.$emit('rename-dir', node)
    },

    handleDeleteDir (node) {
      this.$emit('delete-dir', node)
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

  &__bar {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    flex-wrap: wrap;
  }

  &__loading,
  &__empty {
    margin: 0;
    padding: var(--screen-space-3) 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    text-align: center;
  }

  &__list {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  &__row {
    padding: 5px var(--screen-space-2);
    border-radius: var(--screen-radius-sm);
    transition: background var(--screen-duration) var(--screen-ease);

    &.is-file {
      display: flex;
      align-items: center;
      gap: 6px;
      min-width: 0;

      &:hover {
        background: rgba(255, 255, 255, 0.03);
      }
    }
  }

  &__indent {
    flex: 0 0 auto;
  }

  &__icon {
    flex: 0 0 auto;
    color: var(--screen-text-mute);
  }

  &__label {
    flex: 1 1 auto;
    min-width: 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

  &__size {
    flex: 0 0 auto;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__actions {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
  }

  &__link {
    .screen-link-action();

    &.is-danger {
      color: var(--screen-danger);
    }
  }
}
</style>
