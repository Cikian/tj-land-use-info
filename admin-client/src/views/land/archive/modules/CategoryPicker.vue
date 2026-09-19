<template>
  <a-tree-select
    :value="value"
    :treeData="treeData"
    :loading="loading"
    :dropdownStyle="{ maxHeight: '320px', overflow: 'auto' }"
    :placeholder="placeholder"
    :allowClear="allowClear"
    :disabled="disabled"
    :treeDefaultExpandAll="true"
    :treeNodeFilterProp="'title'"
    showSearch
    @change="handleChange" />
</template>

<script>
  import { queryArchiveCategoryTree } from '@/api/land/archiveCategory'

  /**
   * 档案类别选择器
   *
   * 两种用途：
   *  · {@code leafOnly = true}（默认）——录入时选类别，只有<b>叶子</b>类别可挂档案，
   *    非叶子节点渲染为 disabled；
   *  · {@code leafOnly = false}——查询时按类别过滤，父类别也可选
   *    （服务端会用类别的 path 做前缀匹配，把整棵子树都算进来）。
   *
   * 用 {@link #findNode} 可以把选中的 id 反查成 {id, name, path}，
   * 查询条件需要同时提交 categoryId 与 categoryPath。
   */
  export default {
    name: 'CategoryPicker',
    props: {
      value: {
        type: [String, Number],
        default: undefined
      },
      placeholder: {
        type: String,
        default: '请选择档案类别'
      },
      allowClear: {
        type: Boolean,
        default: true
      },
      disabled: {
        type: Boolean,
        default: false
      },
      /** true 时只有叶子类别可选（录入场景） */
      leafOnly: {
        type: Boolean,
        default: true
      }
    },
    data () {
      return {
        loading: false,
        treeData: [],
        /** id -> 原始类别节点（含 path / name / isLeaf / status） */
        nodeIndex: {}
      }
    },
    created () {
      this.loadTree()
    },
    methods: {
      loadTree () {
        this.loading = true
        return queryArchiveCategoryTree({ status: 1 }).then(res => {
          if (res.success) {
            this.nodeIndex = {}
            this.treeData = this.buildTree(res.result || [])
          } else {
            this.$message.warning(res.message || '档案类别加载失败')
          }
        }).finally(() => {
          this.loading = false
        })
      },
      buildTree (nodes) {
        return (nodes || []).map(node => {
          this.nodeIndex[node.id] = node
          const children = (node.children && node.children.length)
            ? this.buildTree(node.children)
            : undefined
          const isLeaf = !children
          return {
            key: node.id,
            value: node.id,
            title: node.name,
            // 停用的一律不可选；录入场景下非叶子也不可选
            disabled: node.status !== 1 || (this.leafOnly && !isLeaf),
            children: children
          }
        })
      },
      handleChange (value) {
        this.$emit('change', value)
        this.$emit('input', value)
      },
      /**
       * 按 id 反查类别节点。
       * @returns {object|null} {id, name, path, isLeaf, status, parentId, ...}
       */
      findNode (id) {
        if (!id) {
          return null
        }
        return this.nodeIndex[id] || null
      },
      /** 供父组件在需要时强制刷新（例如刚在类别管理里加了新类别） */
      refresh () {
        return this.loadTree()
      }
    }
  }
</script>
