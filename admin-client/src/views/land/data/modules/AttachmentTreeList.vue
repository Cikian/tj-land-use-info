<template>
  <!--
    AttachmentTreeList 附件目录树（按材料类型分组）
    ------------------------------------------------
    结构：**业务对象 → 材料类型 → 文件**（多项目模式下最外层再加项目）。

      ▾ 项建批复文件                        3 个 / 12.4 MB
          立项批复.pdf              4.1 MB     预览 下载 删除
          批复附件.docx             8.3 MB     预览 下载 删除
      ▸ 道路规划                            1 个 / 3.1 MB

    ★ 目录名就是材料类型名：类型码本来就存在附件的 file_type 里，
      所以整棵树是「实际有附件的材料类型」这一个**派生结果**，
      没有需要单独维护的目录数据，也就不可能出现「目录与附件不一致」。
    ★ 空类型不进树（后端只返回有文件的类型）。
    ★ 用 a-collapse 而不是 a-tree：这里每层还要挂「上传到该类型」按钮与
      行内操作，a-tree 的渲染插槽做这件事会很别扭；折叠面板天然适合。

    事件：
      upload (group)           上传到某个材料类型
      upload-project (project) 上传到某个项目
      preview / download / remove (file)
  -->
  <div class="attach-tree">
    <a-spin :spinning="loading">
      <!-- ============ 多项目模式（附件管理页） ============ -->
      <template v-if="projects.length">
        <!-- ★ key 带上 bizType：不限归属类型时 land 与 facility 会同时出现，
                 同一个 bizId 理论上可能撞（两者来自不同的表） -->
        <div v-for="project in projects" :key="`${project.bizType}:${project.bizId}`" class="attach-tree__project">
          <div class="attach-tree__project-head">
            <!-- 归属类型标签：不限类型时同一棵树下会混着宗地与配套，不标出来分不清 -->
            <a-tag v-if="project.bizType" :color="bizTypeColor(project.bizType)" class="attach-tree__type">
              {{ bizTypeText(project.bizType) }}
            </a-tag>
            <span class="attach-tree__project-name" :title="project.bizKey">
              {{ project.bizKey || '未命名' }}
            </span>
            <span class="attach-tree__meta">
              {{ project.totalFiles }} 个 / {{ formatSize(project.totalSize) }} / {{ project.typeCount }} 类
            </span>
            <a
              v-if="editable"
              class="attach-tree__op"
              @click="$emit('upload-project', project)">上传到该项目</a>
          </div>

          <type-groups
            :groups="groupListOf(project)"
            :editable="editable"
            @upload="$emit('upload', $event)"
            @preview="$emit('preview', $event)"
            @download="$emit('download', $event)"
            @remove="$emit('remove', $event)"
          />
        </div>
      </template>

      <!-- ============ 单业务对象模式（详情弹窗内） ============ -->
      <template v-else>
        <type-groups
          :groups="groups"
          :editable="editable"
          @upload="$emit('upload', $event)"
          @preview="$emit('preview', $event)"
          @download="$emit('download', $event)"
          @remove="$emit('remove', $event)"
        />
        <a-empty v-if="!groups.length" :description="emptyText" />
      </template>
    </a-spin>
  </div>
</template>

<script>
// ★ formatSize 与上传/查询接口同属 @/api/land/attachment（admin-client 里没有公共的 util 版本）
import { formatSize, bizTypeText } from '@/api/land/attachment'
import { bizTypeColor } from '@/api/land/dataRecycle'
import TypeGroups from './AttachmentTypeGroups.vue'

export default {
  name: 'AttachmentTreeList',
  components: { TypeGroups },
  props: {
    /**
     * 单业务对象的树：{ groups:[{key,fileType,fileTypeName,files,fileCount,totalSize}],
     *                   totalFiles, totalSize, typeCount }
     */
    tree: { type: Object, default: () => ({ groups: [] }) },
    /**
     * 多项目的树：[{ bizId, bizKey, totalFiles, totalSize, typeCount, tree:{...} }, ...]
     * ★ 与 tree 二选一：传了 projects 就按项目分组（附件管理页是跨项目的），
     *   否则直接渲染单个 tree（详情弹窗里已明确是哪个项目）。
     */
    projects: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    /** 是否显示上传 / 删除。详情页只读时传 false */
    editable: { type: Boolean, default: true },
    emptyText: { type: String, default: '还没有附件' }
  },
  computed: {
    groups () {
      return (this.tree && this.tree.groups) || []
    }
  },
  methods: {
    formatSize,
    bizTypeText,
    bizTypeColor,

    groupListOf (project) {
      return (project && project.tree && project.tree.groups) || []
    }
  }
}
</script>

<style scoped lang="less">
.attach-tree {
  /*
   * ★ 这里**故意不做**自己的滚动。
   *
   *   之前给树加了 max-height + overflow-y: auto，结果是两个滚动条打架：
   *   树被自己的 max-height 截断（内容看着"被切了"），
   *   而页面级滚动条同时存在 —— 一个页面两条滚动条，观感很怪。
   *
   *   现在滚动统一交给页面内容区（PageLayout 的 .page-layout-root），
   *   整页只保留一条滚动条。
   */
  &__project {
    margin-bottom: 16px;
    border: 1px solid #e8e8e8;
    border-radius: 4px;
    overflow: hidden;
  }

  &__project-head {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 12px;
    background: #fafafa;
    border-bottom: 1px solid #e8e8e8;
  }

  &__project-name {
    flex: 1 1 auto;
    min-width: 0;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__type {
    flex: 0 0 auto;
    margin: 0;
  }

  &__meta {
    flex: 0 0 auto;
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  &__op {
    flex: 0 0 auto;
    font-size: 12px;
  }
}
</style>
