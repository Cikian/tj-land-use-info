<template>
  <!--
    CategoryPicker 档案类别选择器
    --------------------------------
    在 ScreenTreeSelect 之上包一层数据加载，把「类别树从哪来」这件事收口在这里，
    这样查询表单、卷内文件表、类别编辑弹窗都只需要写一个标签。

    两套语义（这是这个组件存在的核心原因）：
      leafOnly = true（默认，**录入数据**时用）
        只有末级类别可选。后端强制要求卷内文件的 categoryId 必须是叶子类别，
        所以新增/编辑档案时绝不能让用户选到父类别。
      leafOnly = false（**查询**时用）
        父类别也可选。选中父类别时后端按 category_path 前缀把整棵子树都算进来，
        调用方需要同时提交 categoryId 与 categoryPath（用 findNode 取 path）。

    用法：
      <category-picker ref="category" v-model="query.categoryId" :leaf-only="false"
                       placeholder="可选父类别（含子类）" @change="handleCategoryChange" />
  -->
  <screen-tree-select
    ref="inner"
    :value="value"
    :nodes="nodes"
    :placeholder="placeholder"
    :disabled="disabled || loading"
    :clearable="clearable"
    :leaf-only="leafOnly"
    :invalid="invalid"
    :id="id"
    :aria-label="ariaLabel || placeholder"
    @input="$emit('input', $event)"
    @change="handleChange"
    @clear="$emit('clear')"
  />
</template>

<script>
import { ScreenTreeSelect } from '@/components/screen'
import { queryArchiveCategoryTree } from '@/api/land/archiveCategory'
import { toast } from '@/components/screen/toast'

export default {
  name: 'CategoryPicker',
  components: { ScreenTreeSelect },
  props: {
    /** 选中的类别 id（v-model） */
    value: { type: String, default: '' },
    placeholder: { type: String, default: '请选择档案类别' },
    disabled: { type: Boolean, default: false },
    clearable: { type: Boolean, default: true },
    /** 是否只允许选择末级类别（true = 录入数据，false = 查询条件） */
    leafOnly: { type: Boolean, default: true },
    /** 校验失败态；调用方需同时渲染错误文案 */
    invalid: { type: Boolean, default: false },
    id: { type: String, default: '' },
    ariaLabel: { type: String, default: '' },
  },
  data () {
    return {
      loading: false,
      nodes: [],
    }
  },
  created () {
    this.loadTree()
  },
  methods: {
    /** 只取启用中的类别，停用类别不得再挂新文件 */
    loadTree () {
      this.loading = true
      return queryArchiveCategoryTree({ status: 1 })
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '档案类别加载失败')
            return
          }
          this.nodes = res.result || []
        })
        .finally(() => {
          this.loading = false
        })
    },
    /** 重新拉取（类别管理改动后由父组件调用，避免各处缓存不一致） */
    refresh () {
      return this.loadTree()
    },
    /**
     * 按 id 取原始类别节点（含 path / isLeaf / name）。
     * 查询表单用它拿 categoryPath，从而让父类别检索覆盖整棵子树。
     * @param {string} id
     * @returns {object|null}
     */
    findNode (id) {
      const inner = this.$refs.inner
      if (inner && typeof inner.findNode === 'function') {
        return inner.findNode(id)
      }
      return this.searchNodes(this.nodes, id)
    },
    /** 兜底实现：万一 ScreenTreeSelect 没提供 findNode，就在本地递归找 */
    searchNodes (list, id) {
      for (let i = 0; i < (list || []).length; i += 1) {
        const node = list[i]
        if (node.id === id) return node
        const hit = this.searchNodes(node.children, id)
        if (hit) return hit
      }
      return null
    },
    handleChange (value, node) {
      this.$emit('change', value, node)
    },
  },
}
</script>
