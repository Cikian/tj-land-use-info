<template>
  <!--
    AttachmentTreeView 附件目录树（按材料类型分组）
    --------------------------------
    结构只有两层：**业务对象 → 材料类型 → 文件**。

      ▸ 项建批复文件                    3 个 · 12.4 MB
          立项批复.pdf                            4.1 MB   预览 下载 删除
          批复附件.docx                           8.3 MB   预览 下载 删除
      ▸ 道路规划                        1 个 · 3.1 MB
          道路规划图.dwg                          3.1 MB   预览 下载 删除

    ★ 目录名就是材料类型名：材料类型码本来就存在附件的 file_type 里，
      所以树是「该业务对象下实际有附件的材料类型」这一个**派生结果**，
      没有任何需要单独维护的目录数据，也就不可能出现「目录与附件不一致」。
    ★ 空类型不进树（后端 /attachment/tree 只返回有文件的类型），
      所以这里不需要处理空节点。
    ★ 因此不需要递归组件：层级固定两层。

    ★ 上传入口就在每个类型分组上：「上传到该类型」→ 自动把材料类型定死，
      用户不需要在下拉里选类型（这是本次需求的核心便利点）。

    事件：
      upload (group)   上传到某个材料类型
      preview (file)   预览附件
      download (file)  下载附件
      remove (file)    删除附件
  -->
  <div class="attachment-tree">
    <div v-if="loading" class="attachment-tree__loading">加载中…</div>

    <template v-else>
      <div v-for="group in groups" :key="group.key" class="attachment-tree__group">
        <!-- 材料类型分组头 -->
        <div class="attachment-tree__head">
          <button
            type="button"
            class="attachment-tree__toggle"
            :aria-expanded="isExpanded(group.key) ? 'true' : 'false'"
            :title="group.fileTypeName"
            @click="toggle(group.key)"
          >
            <screen-icon :name="isExpanded(group.key) ? 'chevron-down' : 'chevron-right'" :size="12" />
            <screen-icon :name="isExpanded(group.key) ? 'folder-open' : 'folder'" :size="13" class="attachment-tree__folder" />
            <span class="attachment-tree__name">{{ group.fileTypeName }}</span>
          </button>

          <span class="attachment-tree__badge">
            {{ group.fileCount }} 个 · {{ formatSize(group.totalSize) }}
          </span>

          <span v-if="editable" class="attachment-tree__ops">
            <button type="button" class="attachment-tree__link" @click.stop="$emit('upload', group)">
              上传到该类型
            </button>
          </span>
        </div>

        <!-- 该类型下的文件 -->
        <ul v-if="isExpanded(group.key)" class="attachment-tree__files">
          <li v-for="file in group.files" :key="file.id" class="attachment-tree__file">
            <screen-icon name="file" :size="13" class="attachment-tree__icon" />
            <span class="attachment-tree__file-name" :title="file.fileName">{{ file.fileName }}</span>
            <span class="attachment-tree__size">
              {{ file.readableSize || formatSize(file.fileSize) }}
            </span>
            <span class="attachment-tree__actions">
              <button type="button" class="attachment-tree__link" @click.stop="$emit('preview', file)">
                预览
              </button>
              <button type="button" class="attachment-tree__link" @click.stop="$emit('download', file)">
                下载
              </button>
              <button
                v-if="editable"
                type="button"
                class="attachment-tree__link is-danger"
                @click.stop="$emit('remove', file)"
              >删除</button>
            </span>
          </li>
        </ul>
      </div>

      <p v-if="!groups.length" class="attachment-tree__empty">
        {{ emptyText }}
      </p>
    </template>
  </div>
</template>

<script>
import { ScreenIcon } from '@/components/screen'
import { formatSize } from '@/views/screen/data/constants'

export default {
  name: 'AttachmentTreeView',
  components: { ScreenIcon },
  props: {
    /**
     * 后端 /land/data/attachment/tree 的 result：
     * { bizType, bizKey, groups:[{key,fileType,fileTypeName,files,fileCount,totalSize}],
     *   totalFiles, totalSize, typeCount }
     */
    tree: { type: Object, default: () => ({ groups: [] }) },
    loading: { type: Boolean, default: false },
    /** 是否显示上传 / 删除按钮。详情页只读时传 false */
    editable: { type: Boolean, default: true },
    emptyText: { type: String, default: '还没有附件' }
  },
  data () {
    return {
      /** 收起的类型 key 集合（默认全展开，让目录结构一眼可见） */
      collapsed: []
    }
  },
  computed: {
    groups () {
      return (this.tree && this.tree.groups) || []
    }
  },
  watch: {
    /**
     * 数据换了（切换业务对象 / 刷新）就恢复全展开。
     * ★ 只在**分组集合变化**时重置，避免用户收起某个类型后一次刷新被强行展开。
     */
    groups: {
      handler (next) {
        const keys = (next || []).map(g => g.key)
        const staleOnly = this.collapsed.every(k => keys.indexOf(k) > -1)
        if (!staleOnly) {
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

  &__group {
    border-bottom: 1px solid var(--screen-border-soft);

    &:last-child {
      border-bottom: 0;
    }
  }

  &__head {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    padding: 7px var(--screen-space-2);
    border-radius: var(--screen-radius-sm);
    transition: background var(--screen-duration) var(--screen-ease);

    &:hover {
      background: rgba(255, 255, 255, 0.04);

      .attachment-tree__ops {
        opacity: 1;
      }
    }
  }

  &__toggle {
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

  &__folder {
    flex: 0 0 auto;
    color: var(--screen-accent);
  }

  &__name {
    min-width: 0;
    .screen-ellipsis();
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

  &__files {
    margin: 0;
    padding: 0 0 4px;
    list-style: none;
  }

  &__file {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    // 缩进一级，体现「文件属于上面那个材料类型」
    padding: 4px var(--screen-space-2) 4px 30px;
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
