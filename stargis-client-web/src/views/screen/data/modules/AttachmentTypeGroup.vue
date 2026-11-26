<template>
  <!--
    AttachmentTypeGroup 材料类型分组（附件树的一段）
    ------------------------------------------------
    结构：材料类型 → 该类型下的文件

      ▾ 项建批复文件                    3 个 · 12.4 MB   上传到该类型
          立项批复.pdf                          4.1 MB   预览 下载 删除
          批复附件.docx                         8.3 MB   预览 下载 删除

    ★ 目录名就是材料类型名：材料类型码本来就存在附件的 file_type 里，
      所以整棵树是「该业务对象下实际有附件的材料类型」这一个**派生结果**，
      没有任何需要单独维护的目录数据，也就不可能出现「目录与附件不一致」。
    ★ 空类型不进树（后端 /attachment/tree 只返回有文件的类型），
      所以这里不需要处理空分组。

    ★ 上传入口就在分组头上：「上传到该类型」→ 材料类型自动定死，
      用户不需要在下拉里选类型（这是本次需求的核心便利点）。

    ★ 展开/收起是**受控**的（expanded prop + toggle 事件），不在这里自己存：
      删除一个附件后，附件管理页会重拉整棵树（loadTree 期间 loading=true，
      模板切到「加载中…」），本组件实例会被销毁重建 —— 状态放在组件内部的话，
      用户刚展开的目录会随着每次删除自己收起来（实测反馈的就是这个现象）。
      状态交给父级 AttachmentTreeView（按 group.key 记），实例重建后展开态照旧。

    事件：
      toggle ()        切换展开/收起（状态由父级持有）
      upload (group)   上传到该材料类型
      preview (file)   预览附件
      download (file)  下载附件
      remove (file)    删除附件
  -->
  <div class="type-group">
    <div class="type-group__head">
      <button
        type="button"
        class="type-group__toggle"
        :aria-expanded="expanded ? 'true' : 'false'"
        :title="group.fileTypeName"
        @click="$emit('toggle')"
      >
        <screen-icon :name="expanded ? 'chevron-down' : 'chevron-right'" :size="12" />
        <screen-icon :name="expanded ? 'folder-open' : 'folder'" :size="13" class="type-group__folder" />
        <span class="type-group__name">{{ group.fileTypeName }}</span>
      </button>

      <span class="type-group__badge">
        {{ group.fileCount }} 个 · {{ formatSize(group.totalSize) }}
      </span>

      <span v-if="editable" class="type-group__ops">
        <button
          type="button"
          class="type-group__link"
          :title="`上传到「${group.fileTypeName}」（在弹窗里选择文件或文件夹）`"
          @click.stop="$emit('upload', group)"
        >
          上传到该类型
        </button>
      </span>
    </div>

    <ul v-if="expanded" class="type-group__files">
      <li v-for="file in group.files" :key="file.id" class="type-group__file">
        <screen-icon name="file-text" :size="13" class="type-group__icon" />
        <span class="type-group__file-name" :title="file.fileName">{{ file.fileName }}</span>
        <span class="type-group__size">
          {{ file.readableSize || formatSize(file.fileSize) }}
        </span>
        <span class="type-group__actions">
          <button type="button" class="type-group__link" @click.stop="$emit('preview', file)">
            预览
          </button>
          <button type="button" class="type-group__link" @click.stop="$emit('download', file)">
            下载
          </button>
          <button
            v-if="editable"
            type="button"
            class="type-group__link is-danger"
            @click.stop="$emit('remove', file)"
          >删除</button>
        </span>
      </li>
    </ul>
  </div>
</template>

<script>
import { ScreenIcon } from '@/components/screen'
import { formatSize } from '@/views/screen/data/constants'

export default {
  name: 'AttachmentTypeGroup',
  components: { ScreenIcon },
  props: {
    /** { key, fileType, fileTypeName, files:[附件], fileCount, totalSize } */
    group: { type: Object, required: true },
    editable: { type: Boolean, default: true },
    /**
     * 是否展开（受控）。
     * ★ 状态不放在本组件：整棵树每次重拉都会重建本组件，
     *   内部状态会被重置 —— 见模板顶部注释（删除附件后目录自己收起来）。
     */
    expanded: { type: Boolean, default: false }
  },
  methods: {
    formatSize
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.type-group {
  border-bottom: 1px solid var(--screen-border-soft);

  &:last-child {
    border-bottom: 0;
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

      .type-group__ops {
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
