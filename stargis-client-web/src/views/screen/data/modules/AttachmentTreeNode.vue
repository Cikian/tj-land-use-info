<template>
  <!--
    AttachmentTreeNode 附件树的单个目录节点（**递归组件**）
    ------------------------------------------------------
    自己渲染「目录行 + 子目录 + 本目录文件」，子目录再次挂载自己。

    ★ 为什么拆成独立组件：目录是任意层级的嵌套结构，
      在一个扁平组件里靠循环模拟递归会让缩进、展开状态、事件透传全部缠在一起。
      递归组件是最自然的表达，也让「展开/收起」的状态天然只由父级统一持有
      （expandedKeys 一份数据，避免每层各存一份导致状态不同步）。

    ★ 目录行本身是按钮（可键盘操作）：用原生 <button> 而不是 div+click，
      这样 Tab/Enter/Space/读屏都不需要额外补 ARIA。
  -->
  <li class="attachment-tree-node">
    <div
      class="attachment-tree-node__dir"
      :style="{ paddingLeft: `${indent}px` }"
      role="treeitem"
      :aria-expanded="hasChildren ? String(expanded) : undefined"
      :aria-level="level + 1"
    >
      <!-- 展开箭头：没有子节点时不渲染，避免出现一个点了没反应的按钮 -->
      <button
        v-if="hasChildren"
        type="button"
        class="attachment-tree-node__twisty"
        :aria-label="expanded ? '收起' : '展开'"
        @click.stop="$emit('toggle', node.key)"
      >
        <screen-icon :name="expanded ? 'chevron-down' : 'chevron-right'" :size="12" />
      </button>
      <span v-else class="attachment-tree-node__twisty is-placeholder" aria-hidden="true" />

      <!-- 目录名：整行可点，点目录名也是展开/收起（比只点箭头好点得多） -->
      <button
        type="button"
        class="attachment-tree-node__name"
        :title="node.dirPath"
        @click="$emit('toggle', node.key)"
      >
        <screen-icon :name="expanded ? 'folder-open' : 'folder'" :size="13" />
        <span class="attachment-tree-node__text">{{ node.label }}</span>
      </button>

      <!-- 角标：这一支（含子目录）有多少文件、多大 -->
      <span class="attachment-tree-node__badge">
        {{ node.fileCount || 0 }} 个 · {{ formatSize(node.totalSize) }}
      </span>
      <span v-if="node.empty" class="attachment-tree-node__hint">空目录</span>

      <!-- 目录维护：只读模式下不渲染 -->
      <span v-if="editable" class="attachment-tree-node__ops">
        <button type="button" class="attachment-tree-node__link" @click.stop="$emit('rename-dir', node)">
          重命名
        </button>
        <button
          type="button"
          class="attachment-tree-node__link is-danger"
          @click.stop="$emit('delete-dir', node)"
        >删除</button>
      </span>
    </div>

    <!-- 展开时才渲染子节点：层级深时能省掉大量无意义的 DOM -->
    <template v-if="expanded">
      <ul class="attachment-tree-node__children" role="group">
        <!-- 本目录下的文件 -->
        <li v-for="file in files" :key="file.id" class="attachment-tree-node__file">
          <div class="attachment-tree-node__file-row" :style="{ paddingLeft: `${indent + 22}px` }">
            <screen-icon name="file" :size="13" class="attachment-tree-node__icon" />
            <span class="attachment-tree-node__file-name" :title="file.fileName">{{ file.fileName }}</span>
            <span class="attachment-tree-node__size">
              {{ file.readableSize || formatSize(file.fileSize) }}
            </span>
            <span class="attachment-tree-node__actions">
              <button type="button" class="attachment-tree-node__link" @click.stop="$emit('preview', file)">
                预览
              </button>
              <button type="button" class="attachment-tree-node__link" @click.stop="$emit('download', file)">
                下载
              </button>
              <button
                v-if="editable"
                type="button"
                class="attachment-tree-node__link is-danger"
                @click.stop="$emit('remove', file)"
              >删除</button>
            </span>
          </div>
        </li>

        <!-- 子目录：递归 -->
        <attachment-tree-node
          v-for="child in childDirs"
          :key="child.key"
          :node="child"
          :level="level + 1"
          :expanded-keys="expandedKeys"
          :editable="editable"
          @toggle="$emit('toggle', $event)"
          @preview="$emit('preview', $event)"
          @download="$emit('download', $event)"
          @remove="$emit('remove', $event)"
          @rename-dir="$emit('rename-dir', $event)"
          @delete-dir="$emit('delete-dir', $event)"
        />
      </ul>
    </template>
  </li>
</template>

<script>
import { ScreenIcon } from '@/components/screen'
import { formatSize } from '@/views/screen/data/constants'

export default {
  name: 'AttachmentTreeNode',
  components: { ScreenIcon },
  props: {
    /** 后端返回的目录节点 { key, nodeType, label, dirPath, fileCount, totalSize, empty, children, file } */
    node: { type: Object, required: true },
    level: { type: Number, default: 0 },
    expandedKeys: { type: Array, default: () => [] },
    editable: { type: Boolean, default: true }
  },
  computed: {
    expanded () {
      return this.expandedKeys.indexOf(this.node.key) > -1
    },
    children () {
      return this.node.children || []
    },
    /** 子目录（children 里 nodeType=dir 的） */
    childDirs () {
      return this.children.filter(item => item.nodeType === 'dir')
    },
    /** 挂在本目录下的文件（children 里 nodeType=file 的，取它们的 file 实体） */
    files () {
      return this.children
        .filter(item => item.nodeType === 'file' && item.file)
        .map(item => item.file)
    },
    hasChildren () {
      return this.childDirs.length > 0
    },
    /** 缩进：每层 22px，与目录行左侧箭头宽度对齐 */
    indent () {
      return this.level * 22
    }
  },
  methods: {
    formatSize
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.attachment-tree-node {
  list-style: none;

  &__children {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  &__dir {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    padding-top: 5px;
    padding-bottom: 5px;
    padding-right: var(--screen-space-2);
    border-radius: var(--screen-radius-sm);
    transition: background var(--screen-duration) var(--screen-ease);

    &:hover {
      background: rgba(255, 255, 255, 0.04);

      .attachment-tree-node__ops {
        opacity: 1;
      }
    }
  }

  &__twisty {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    padding: 0;
    color: var(--screen-text-mute);
    background: none;
    border: 0;
    cursor: pointer;
    .screen-focus-ring();

    &.is-placeholder {
      cursor: default;
    }
  }

  &__name {
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

    // /deep/ 是 Vue 2 的深层选择器（本工程统一用 Vue 2，不要用 :deep()）
    /deep/ .screen-icon {
      flex: 0 0 auto;
      color: var(--screen-accent);
    }
  }

  &__text {
    min-width: 0;
    .screen-ellipsis();
  }

  &__badge {
    flex: 0 0 auto;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__hint {
    flex: 0 0 auto;
    padding: 0 6px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    background: rgba(255, 255, 255, 0.06);
    border-radius: var(--screen-radius-pill);
  }

  &__ops {
    flex: 0 0 auto;
    margin-left: auto;
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
    opacity: 0;
    transition: opacity var(--screen-duration) var(--screen-ease);
  }

  &__file-row {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    padding: 4px var(--screen-space-2) 4px 0;
    border-radius: var(--screen-radius-sm);

    &:hover {
      background: rgba(255, 255, 255, 0.03);
    }
  }

  &__icon {
    flex: 0 0 auto;
    color: var(--screen-text-mute);
  }

  &__file-name {
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
