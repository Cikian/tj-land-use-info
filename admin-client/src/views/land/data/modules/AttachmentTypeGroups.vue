<template>
  <!--
    AttachmentTypeGroups 材料类型分组（若干段折叠面板）
    ---------------------------------------------------
    单独拆一个组件，是因为它在两种模式里都要用：
      · 多项目模式：每个项目下渲染一组
      · 单业务对象模式：直接渲染一组
    写在父组件里会让「项目循环 + 分组循环」两层揉在一起。

    ★ 默认全部展开：目录结构一眼可见，不用逐个点开找文件。
  -->
  <div class="type-groups">
    <div v-for="group in groups" :key="group.key" class="type-groups__group">
      <div class="type-groups__head">
        <a
          class="type-groups__toggle"
          @click="toggle(group.key)"
        >
          <a-icon :type="isExpanded(group.key) ? 'down' : 'right'" />
          <a-icon :type="isExpanded(group.key) ? 'folder-open' : 'folder'" />
          <span class="type-groups__name">{{ group.fileTypeName }}</span>
        </a>
        <span class="type-groups__meta">
          {{ group.fileCount }} 个 / {{ formatSize(group.totalSize) }}
        </span>
        <a
          v-if="editable"
          class="type-groups__op"
          @click="$emit('upload', group)">上传到该类型</a>
      </div>

      <ul v-if="isExpanded(group.key)" class="type-groups__files">
        <li v-for="file in group.files" :key="file.id" class="type-groups__file">
          <a-icon type="file" class="type-groups__icon" />
          <span class="type-groups__file-name" :title="file.fileName">{{ file.fileName }}</span>
          <span class="type-groups__size">{{ file.readableSize || formatSize(file.fileSize) }}</span>
          <span class="type-groups__actions">
            <a @click="$emit('preview', file)">预览</a>
            <a-divider type="vertical" />
            <a @click="$emit('download', file)">下载</a>
            <template v-if="editable">
              <a-divider type="vertical" />
              <a class="type-groups__danger" @click="$emit('remove', file)">删除</a>
            </template>
          </span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script>
// ★ formatSize 与上传/查询接口同属 @/api/land/attachment（admin-client 里没有公共的 util 版本）
import { formatSize } from '@/api/land/attachment'

export default {
  name: 'AttachmentTypeGroups',
  props: {
    /** [{ key, fileType, fileTypeName, files, fileCount, totalSize }] */
    groups: { type: Array, default: () => [] },
    editable: { type: Boolean, default: true }
  },
  data () {
    return {
      /** 收起的类型 key（默认全展开） */
      collapsed: []
    }
  },
  watch: {
    /**
     * 分组集合变了就恢复全展开。
     * ★ 只在集合真的变化时重置，避免用户收起某个类型后一次刷新被强行展开。
     */
    groups: {
      handler (next) {
        const keys = (next || []).map(g => g.key)
        if (!this.collapsed.every(k => keys.indexOf(k) > -1)) {
          this.collapsed = []
        }
      }
    }
  },
  methods: {
    formatSize,

    isExpanded (key) {
      return this.collapsed.indexOf(key) < 0
    },

    toggle (key) {
      const index = this.collapsed.indexOf(key)
      if (index > -1) {
        this.collapsed.splice(index, 1)
      } else {
        this.collapsed.push(key)
      }
    }
  }
}
</script>

<style scoped lang="less">
.type-groups {
  &__group {
    border-bottom: 1px solid #f0f0f0;

    &:last-child {
      border-bottom: 0;
    }
  }

  &__head {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 12px;
    transition: background 0.3s;

    &:hover {
      background: #fafafa;
    }
  }

  &__toggle {
    flex: 0 1 auto;
    min-width: 0;
    color: rgba(0, 0, 0, 0.85);
    font-weight: 500;

    > .anticon + .anticon {
      margin-left: 6px;
    }
  }

  &__name {
    margin-left: 4px;
  }

  &__meta {
    flex: 0 0 auto;
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  &__op {
    flex: 0 0 auto;
    margin-left: auto;
    font-size: 12px;
  }

  &__files {
    margin: 0;
    padding: 0 0 8px;
    list-style: none;
  }

  &__file {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    padding: 4px 12px 4px 34px;

    &:hover {
      background: #fafafa;
    }
  }

  &__icon {
    flex: 0 0 auto;
    color: rgba(0, 0, 0, 0.45);
  }

  &__file-name {
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__size {
    flex: 0 0 auto;
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  &__actions {
    flex: 0 0 auto;
    font-size: 12px;
  }

  &__danger {
    color: #ff4d4f;
  }
}
</style>
