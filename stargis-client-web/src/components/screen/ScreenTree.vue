<template>
  <!--
    ScreenTree 档案类别树
    --------------------------------
    「档案类别管理」左侧的类别树，同时用作档案类别选择器（ScreenTreeSelect）的下拉内容。
    数据形态：{ id, name, children, isLeaf, status, archiveCount, disabled }
    （字段名可通过 keyKey / labelKey / childrenKey 改成后端真正返回的名字）。

    能力：
      1. 单选。单击行 = 选中并展开/收起（有子节点时），单击行首箭头 = 只展开/收起，不改变选中；
      2. 键盘导航严格遵循 WAI-ARIA tree 模式（方向键 / Home / End / Enter / Space），
         roving tabindex 保证整棵树在 Tab 序列里只有一个停靠点；
      3. 原生 HTML5 拖拽排序 / 换父，落点分 before / inside / after 三种并各有指示；
      4. 搜索关键字高亮：<mark> 用强调色 + 强调色半透明底，不使用浏览器默认的黄色。

    用法：
      <screen-tree
        :nodes="categories"
        :selected-keys="selectedKeys"
        :expanded-keys.sync="expandedKeys"
        draggable
        :draggable-guard="canDrop"
        highlight-keyword="规划"
        aria-label="档案类别"
        @select="handleSelect"
        @drop="handleDrop"
      >
        <template #node="{ node, entry }">{{ node.name }}({{ entry.level }})</template>
      </screen-tree>

    拖拽守卫：
      draggableGuard(dragNode, dropNode, position) 返回 false 即拒绝放置。
      即使不传守卫，组件也会内置拒绝「把节点拖到它自己或它的子孙节点上」，
      否则重排后会出现环，后端保存必然失败。

    无障碍说明：
      选中态通过 aria-selected + 左侧 2px 竖条（形变）共同表达，
      禁用态通过 aria-disabled + not-allowed 光标 + 降透明度共同表达，
      都不依赖色相单独传达。
  -->
  <div class="screen-tree" :class="{ 'is-dragging': hasDragSource }">
    <ul
      ref="list"
      class="screen-tree__list"
      role="tree"
      :aria-label="ariaLabel || undefined"
      @keydown="handleKeydown"
    >
      <li
        v-for="entry in visibleNodes"
        :key="entry.key"
        class="screen-tree__node"
        :class="nodeClass(entry)"
        role="treeitem"
        :data-tree-key="entry.key"
        :aria-level="entry.level"
        :aria-selected="isSelected(entry.key) ? 'true' : 'false'"
        :aria-expanded="entry.hasChildren ? String(entry.expanded) : undefined"
        :aria-disabled="isNodeDisabled(entry.node) ? 'true' : undefined"
        :tabindex="isRoving(entry.key) ? 0 : -1"
        :draggable="isDraggable(entry) ? 'true' : 'false'"
        :style="{ paddingLeft: `${indentOf(entry.level)}px` }"
        @click="handleNodeClick(entry)"
        @focus="focusKey = entry.key"
        @dragstart="handleDragStart(entry, $event)"
        @dragover="handleDragOver(entry, $event)"
        @dragleave="handleDragLeave(entry, $event)"
        @drop="handleDrop(entry, $event)"
        @dragend="handleDragEnd"
      >
        <!-- 展开箭头：独立按钮，只负责展开/收起，不改变选中，也不参与 Tab 序列 -->
        <button
          v-if="entry.hasChildren"
          type="button"
          class="screen-tree__twisty"
          draggable="false"
          tabindex="-1"
          :aria-label="entry.expanded ? '收起' : '展开'"
          @click.stop="toggleExpand(entry)"
        >
          <screen-icon :name="entry.expanded ? 'chevron-down' : 'chevron-right'" :size="12" />
        </button>
        <span v-else class="screen-tree__twisty is-placeholder" aria-hidden="true" />

        <slot name="node" :node="entry.node" :level="entry.level" :entry="entry">
          <span v-if="showIcon" class="screen-tree__icon" aria-hidden="true">
            <screen-icon :name="iconOf(entry)" :size="14" />
          </span>

          <span class="screen-tree__label">
            <template v-for="(segment, index) in segmentsOf(entry.node)">
              <mark v-if="segment.match" :key="`mark-${index}`" class="screen-tree__mark">{{ segment.text }}</mark>
              <template v-else>{{ segment.text }}</template>
            </template>
          </span>

          <em v-if="isStopped(entry.node)" class="screen-tree__tag">停用</em>
          <em v-if="hasArchiveCount(entry.node)" class="screen-tree__count">{{ formatArchiveCount(entry.node) }}</em>
        </slot>
      </li>

      <!-- 空数据：仍保留 treeitem 语义，避免 role="tree" 出现非法子节点 -->
      <li
        v-if="!visibleNodes.length"
        class="screen-tree__empty"
        role="treeitem"
        aria-level="1"
        aria-selected="false"
        aria-disabled="true"
        tabindex="-1"
      >{{ emptyText }}</li>
    </ul>
  </div>
</template>

<script>
import ScreenIcon from './ScreenIcon'
import { formatNumber } from './utils'

export default {
  name: 'ScreenTree',
  components: { ScreenIcon },
  props: {
    /** 树数据：[{ id, name, children, isLeaf, status, archiveCount, disabled }] */
    nodes: { type: Array, default: () => [] },
    /** 选中节点的 key 数组（本组件为单选，只取第一项参与比较） */
    selectedKeys: { type: Array, default: () => [] },
    /** 展开节点的 key 数组，支持 .sync（同步派发 update:expandedKeys） */
    expandedKeys: { type: Array, default: () => [] },
    /** 是否开启原生 HTML5 拖拽排序 / 换父 */
    draggable: { type: Boolean, default: false },
    /**
     * 拖拽守卫：(dragNode, dropNode, position) => boolean。
     * 返回 false 表示拒绝该次放置；position 为 'before' | 'inside' | 'after'。
     */
    draggableGuard: { type: Function, default: null },
    /** 是否在默认行内容里显示文件夹 / 文件图标 */
    showIcon: { type: Boolean, default: true },
    /** 空数据文案 */
    emptyText: { type: String, default: '暂无数据' },
    /** 搜索高亮关键字，大小写不敏感；为空则不处理 */
    highlightKeyword: { type: String, default: '' },
    /** 节点主键字段名 */
    keyKey: { type: String, default: 'id' },
    /** 节点名称字段名 */
    labelKey: { type: String, default: 'name' },
    /** 子节点字段名 */
    childrenKey: { type: String, default: 'children' },
    /** 树的可见名称（读屏用）；不传则不加 aria-label，由调用方自行关联 */
    ariaLabel: { type: String, default: '' },
  },
  data () {
    return {
      /** 内部展开态：父组件不做 .sync 时也能独立工作 */
      innerExpandedKeys: this.expandedKeys.slice(),
      /** 内部选中态：父组件不监听 select 时也能看到选中效果 */
      innerSelectedKeys: this.selectedKeys.slice(),
      /** 当前焦点（roving tabindex 的落点）所在节点 key */
      focusKey: null,
      /** 正在被拖拽的节点 key */
      dragKey: null,
      /** 当前落点：{ key, position } */
      dropTarget: null,
    }
  },
  computed: {
    /** 展开 key 的字符串化集合，避免后端返回数字 id、v-model 传字符串时比较失败 */
    expandedTokens () {
      return this.innerExpandedKeys.map((key) => this.keyToken(key))
    },
    selectedTokens () {
      return this.innerSelectedKeys.map((key) => this.keyToken(key))
    },
    /**
     * 拍平后的可见节点列表（已按 expandedKeys 过滤掉折叠子树）。
     * 每项：{ node, key, level, hasChildren, expanded, leaf, parent }
     * parent 指向父级 entry，供 ArrowLeft 回到父节点使用。
     */
    visibleNodes () {
      const list = []
      const expandedTokens = this.expandedTokens
      const walk = (children, level, parent) => {
        if (!Array.isArray(children)) return
        children.forEach((node) => {
          if (!node) return
          const key = node[this.keyKey]
          const kids = this.childrenOf(node)
          const hasChildren = kids.length > 0
          const expanded = hasChildren && expandedTokens.indexOf(this.keyToken(key)) > -1
          const entry = {
            node,
            key,
            level,
            hasChildren,
            expanded,
            leaf: node.isLeaf === true || !hasChildren,
            parent,
          }
          list.push(entry)
          if (expanded) walk(kids, level + 1, entry)
        })
      }
      walk(this.nodes, 1, null)
      return list
    },
    /** roving tabindex 的落点：焦点节点不在可见列表里时退回第一个可见节点 */
    rovingKey () {
      const list = this.visibleNodes
      if (!list.length) return null
      const token = this.keyToken(this.focusKey)
      const found = list.filter((entry) => this.keyToken(entry.key) === token)[0]
      return found ? found.key : list[0].key
    },
    hasDragSource () {
      return this.dragKey !== null && this.dragKey !== undefined
    },
  },
  watch: {
    /** 父组件改 expandedKeys（含 .sync 回写）后同步内部状态 */
    expandedKeys (val) {
      this.innerExpandedKeys = (val || []).slice()
    },
    selectedKeys (val) {
      this.innerSelectedKeys = (val || []).slice()
    },
  },
  methods: {
    /** key 的字符串化：兼容后端数字 id 与表单字符串值混用 */
    keyToken (key) {
      return key === null || key === undefined ? '' : String(key)
    },
    /** 读取子节点数组，容错非数组数据 */
    childrenOf (node) {
      const children = node ? node[this.childrenKey] : null
      return Array.isArray(children) ? children : []
    },
    /** 节点是否禁用 */
    isNodeDisabled (node) {
      return Boolean(node && node.disabled)
    },
    /** 节点是否已停用（status === 0），用于默认行内容里的「停用」标记 */
    isStopped (node) {
      if (!node) return false
      const status = node.status
      return status !== undefined && status !== null && Number(status) === 0
    },
    /** 节点是否带档案数量（0 也要显示） */
    hasArchiveCount (node) {
      return Boolean(node) && node.archiveCount !== null && node.archiveCount !== undefined
    },
    formatArchiveCount (node) {
      return formatNumber(node.archiveCount === undefined ? 0 : node.archiveCount, 0)
    },
    /** 层级缩进：基础 8px，每层 +16px */
    indentOf (level) {
      return 8 + (Math.max(1, level) - 1) * 16
    },
    /** 默认行内容的图标：叶子用文件，展开的目录用打开态文件夹 */
    iconOf (entry) {
      if (entry.leaf) return 'file-text'
      return entry.expanded ? 'folder-open' : 'folder'
    },
    isSelected (key) {
      return this.selectedTokens.indexOf(this.keyToken(key)) > -1
    },
    isRoving (key) {
      return this.keyToken(key) === this.keyToken(this.rovingKey)
    },
    isDraggable (entry) {
      return this.draggable && !this.isNodeDisabled(entry.node)
    },
    /** 行样式集合（选中 / 禁用 / 拖拽源 / 三种落点） */
    nodeClass (entry) {
      const token = this.keyToken(entry.key)
      const target = this.dropTarget
      const onTarget = Boolean(target) && this.keyToken(target.key) === token
      const position = onTarget ? target.position : ''
      return {
        'is-selected': this.isSelected(entry.key),
        'is-disabled': this.isNodeDisabled(entry.node),
        'is-expanded': entry.expanded,
        'is-leaf': entry.leaf,
        'is-dragging': this.hasDragSource && this.keyToken(this.dragKey) === token,
        'is-drop-before': position === 'before',
        'is-drop-after': position === 'after',
        'is-drop-inside': position === 'inside',
      }
    },
    /** 关键字高亮分段：大小写不敏感，返回 [{ text, match }] */
    segmentsOf (node) {
      const raw = node ? node[this.labelKey] : ''
      const text = raw === null || raw === undefined ? '' : String(raw)
      const keyword = (this.highlightKeyword || '').trim()
      if (!keyword) return [{ text, match: false }]

      const escaped = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      const regExp = new RegExp(escaped, 'gi')
      const segments = []
      let lastIndex = 0
      let matched = regExp.exec(text)
      while (matched !== null) {
        // 关键字被转义后不会匹配空串，这里只做防御
        if (matched[0] === '') {
          regExp.lastIndex += 1
        } else {
          if (matched.index > lastIndex) {
            segments.push({ text: text.slice(lastIndex, matched.index), match: false })
          }
          segments.push({ text: matched[0], match: true })
          lastIndex = matched.index + matched[0].length
        }
        matched = regExp.exec(text)
      }
      if (!segments.length) return [{ text, match: false }]
      if (lastIndex < text.length) segments.push({ text: text.slice(lastIndex), match: false })
      return segments
    },
    /** 按 key 找到可见 entry */
    entryOf (key) {
      const token = this.keyToken(key)
      return this.visibleNodes.filter((entry) => this.keyToken(entry.key) === token)[0] || null
    },
    /** 按 key 找到 DOM 行元素 */
    nodeEl (key) {
      const list = this.$refs.list
      if (!list || !list.querySelectorAll) return null
      const token = this.keyToken(key)
      const nodes = list.querySelectorAll('[data-tree-key]')
      for (let index = 0; index < nodes.length; index += 1) {
        if (nodes[index].getAttribute('data-tree-key') === token) return nodes[index]
      }
      return null
    },
    /** 把 roving 焦点移到指定节点并真正 focus() 到 DOM 上 */
    setFocus (key) {
      this.focusKey = key
      this.$nextTick(() => {
        const el = this.nodeEl(key)
        if (el && el.focus) el.focus()
      })
    },
    /** 按可见列表下标移动焦点（越界即停，不循环） */
    moveFocus (index) {
      const list = this.visibleNodes
      if (index < 0 || index >= list.length) return
      this.setFocus(list[index].key)
    },
    /** 可见列表中某 key 的下标，找不到返回 -1 */
    indexOfVisible (key) {
      const token = this.keyToken(key)
      return this.visibleNodes.findIndex((entry) => this.keyToken(entry.key) === token)
    },
    /** 展开 / 收起单个节点，派发 update:expandedKeys（.sync）与 expand */
    setExpanded (entry, expanded) {
      if (!entry || !entry.hasChildren) return
      const token = this.keyToken(entry.key)
      // 收起前先判断焦点是否落在待移除的子树里（收起后这些节点就不在 visibleNodes 里了）
      const focusInside = !expanded && this.isDescendantOf(this.entryOf(this.focusKey), entry.key)
      const next = this.innerExpandedKeys.filter((key) => this.keyToken(key) !== token)
      if (expanded) next.push(entry.key)
      this.innerExpandedKeys = next
      this.$emit('update:expandedKeys', next)
      this.$emit('expand', next)
      if (focusInside) this.setFocus(entry.key)
    },
    /** 箭头按钮：只切换展开态，不改变选中 */
    toggleExpand (entry) {
      this.setExpanded(entry, !entry.expanded)
    },
    /** 选中节点：更新内部选中态并派发 select */
    selectEntry (entry) {
      if (!entry || this.isNodeDisabled(entry.node)) return
      const next = [entry.key]
      this.innerSelectedKeys = next
      this.$emit('update:selectedKeys', next)
      this.$emit('select', entry.key, entry.node)
    },
    /** 行点击：选中 + 有子节点时同时切换展开态（桌面树的常见行为） */
    handleNodeClick (entry) {
      if (this.isNodeDisabled(entry.node)) return
      this.setFocus(entry.key)
      if (entry.hasChildren) this.setExpanded(entry, !entry.expanded)
      this.selectEntry(entry)
    },
    /**
     * 键盘导航（WAI-ARIA tree 模式）：
     * 上下方向键在可见节点间移动，右方向键展开或进入第一个子节点，
     * 左方向键收起或回到父节点，Home / End 跳到首 / 末个可见节点，Enter / Space 选中。
     */
    handleKeydown (event) {
      const handled = [
        'ArrowDown',
        'ArrowUp',
        'ArrowRight',
        'ArrowLeft',
        'Home',
        'End',
        'Enter',
        ' ',
        'Spacebar',
      ]
      if (handled.indexOf(event.key) === -1) return

      const list = this.visibleNodes
      if (!list.length) return
      event.preventDefault()

      const index = this.indexOfVisible(this.rovingKey)
      const entry = list[index < 0 ? 0 : index]

      switch (event.key) {
        case 'ArrowDown':
          this.moveFocus(Math.min(index + 1, list.length - 1))
          break
        case 'ArrowUp':
          this.moveFocus(Math.max(index - 1, 0))
          break
        case 'Home':
          this.moveFocus(0)
          break
        case 'End':
          this.moveFocus(list.length - 1)
          break
        case 'ArrowRight':
          if (entry && entry.hasChildren && !entry.expanded) {
            this.setExpanded(entry, true)
          } else if (entry && entry.hasChildren) {
            // 已展开：进入第一个子节点
            this.moveFocus(index + 1)
          }
          break
        case 'ArrowLeft':
          if (entry && entry.hasChildren && entry.expanded) {
            this.setExpanded(entry, false)
          } else if (entry && entry.parent) {
            this.moveFocus(this.indexOfVisible(entry.parent.key))
          }
          break
        default:
          if (entry) this.selectEntry(entry)
          break
      }
    },
    /** 落点判定：上 25% before / 中 50% inside / 下 25% after */
    resolveDropPosition (event) {
      const rect = event.currentTarget.getBoundingClientRect()
      const height = rect.height || 1
      const ratio = (event.clientY - rect.top) / height
      if (ratio < 0.25) return 'before'
      if (ratio > 0.75) return 'after'
      return 'inside'
    },
    /** dropEntry 是否是 ancestorKey 的子孙（用于拒绝拖到自己的子树里） */
    isDescendantOf (entry, ancestorKey) {
      const token = this.keyToken(ancestorKey)
      let cursor = entry ? entry.parent : null
      while (cursor) {
        if (this.keyToken(cursor.key) === token) return true
        cursor = cursor.parent
      }
      return false
    },
    /** 内置校验 + 外部守卫共同决定是否允许放置 */
    canDrop (source, target, position) {
      if (!source || !target) return false
      if (this.keyToken(source.key) === this.keyToken(target.key)) return false
      if (this.isDescendantOf(target, source.key)) return false
      if (typeof this.draggableGuard === 'function') {
        return this.draggableGuard(source.node, target.node, position) !== false
      }
      return true
    },
    handleDragStart (entry, event) {
      if (!this.isDraggable(entry)) {
        if (event.preventDefault) event.preventDefault()
        return
      }
      this.dragKey = entry.key
      this.dropTarget = null
      if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move'
        try {
          // Firefox 必须写入数据，否则不会触发后续的 dragover / drop
          event.dataTransfer.setData('text/plain', this.keyToken(entry.key))
        } catch (err) {
          // 只读模式下 setData 会抛错，忽略即可
        }
      }
    },
    handleDragOver (entry, event) {
      if (!this.draggable || !this.hasDragSource) return
      const source = this.entryOf(this.dragKey)
      const position = this.resolveDropPosition(event)
      if (!this.canDrop(source, entry, position)) {
        this.dropTarget = null
        if (event.dataTransfer) event.dataTransfer.dropEffect = 'none'
        return
      }
      // 只有 preventDefault 之后浏览器才允许 drop
      event.preventDefault()
      if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
      this.dropTarget = { key: entry.key, position }
    },
    handleDragLeave (entry, event) {
      if (!this.dropTarget) return
      if (this.keyToken(this.dropTarget.key) !== this.keyToken(entry.key)) return
      // 在行内部子元素之间移动时也会触发 dragleave，此时不清除指示
      const related = event.relatedTarget
      if (related && event.currentTarget.contains(related)) return
      this.dropTarget = null
    },
    handleDrop (entry, event) {
      if (event.preventDefault) event.preventDefault()
      const dragKey = this.dragKey
      const target = this.dropTarget
      this.clearDragState()
      if (!this.hasDragKey(dragKey) || !target) return
      if (this.keyToken(target.key) !== this.keyToken(entry.key)) return

      const source = this.entryOf(dragKey)
      if (!this.canDrop(source, entry, target.position)) return
      this.$emit('drop', { dragKey, dropKey: entry.key, position: target.position })
    },
    handleDragEnd () {
      this.clearDragState()
    },
    hasDragKey (key) {
      return key !== null && key !== undefined
    },
    clearDragState () {
      this.dragKey = null
      this.dropTarget = null
    },
    /**
     * 聚焦树（供 ScreenTreeSelect 打开下拉时调用）：
     * 优先落在已选中节点上，其次保留当前 roving 节点，最后落在第一个可见节点。
     */
    focus () {
      const list = this.visibleNodes
      if (!list.length) return
      const selected = list.filter((entry) => this.isSelected(entry.key))[0]
      const index = this.indexOfVisible(this.rovingKey)
      const target = selected || list[index < 0 ? 0 : index]
      this.setFocus(target.key)
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-tree {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  max-height: 100%;
  font-size: var(--screen-font-sm);
  color: var(--screen-text);

  &__list {
    flex: 1 1 auto;
    min-height: 0;
    margin: 0;
    padding: 4px 0;
    overflow-x: hidden;
    overflow-y: auto;
    list-style: none;
    .screen-scrollbar();
  }

  &__node {
    position: relative;
    display: flex;
    align-items: center;
    gap: 6px;
    min-height: 28px;
    // 左侧内边距由 :style 按层级覆盖（padding-left），保证缩进随层级变化
    padding: 4px 10px 4px 8px;
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    user-select: none;
    transition: background-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      background: var(--screen-elevate);
    }

    // 选中：强调色浅底 + 左侧 2px 竖条（形状提示，不只靠颜色）
    &.is-selected {
      color: var(--screen-accent);
      background: rgba(47, 227, 192, 0.12);
      font-weight: 600;

      &::before {
        content: '';
        position: absolute;
        top: 3px;
        bottom: 3px;
        left: 0;
        width: 2px;
        border-radius: var(--screen-radius-pill);
        background: var(--screen-accent);
        box-shadow: 0 0 8px var(--screen-accent-glow);
      }
    }

    // 拖拽落点：before / after 用一条 2px 指示线，inside 用描边 + 强调色浅底
    &.is-drop-before::after,
    &.is-drop-after::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      height: 2px;
      background: var(--screen-accent);
      border-radius: var(--screen-radius-pill);
      box-shadow: 0 0 8px var(--screen-accent-glow);
      pointer-events: none;
    }

    &.is-drop-before::after {
      top: 0;
    }

    &.is-drop-after::after {
      bottom: 0;
    }

    &.is-drop-inside {
      background: rgba(47, 227, 192, 0.18);
      box-shadow: inset 0 0 0 1px var(--screen-accent);
    }

    // 正在被拖拽的源节点
    &.is-dragging {
      opacity: 0.45;
    }

    &.is-disabled {
      color: var(--screen-text-mute);
      cursor: not-allowed;
      opacity: 0.7;

      &:hover {
        background: transparent;
      }
    }
  }

  &__twisty {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    padding: 0;
    color: var(--screen-text-sub);
    background: transparent;
    border: 0;
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent);
      background: rgba(47, 227, 192, 0.12);
    }

    // 叶子节点的占位：与箭头等宽，保证同层文字左对齐
    &.is-placeholder {
      cursor: inherit;
    }
  }

  &__icon {
    flex: 0 0 auto;
    display: inline-flex;
    color: var(--screen-text-sub);
  }

  &__label {
    flex: 1 1 auto;
    min-width: 0;
    line-height: 1.5;
    .screen-ellipsis();
  }

  // 搜索命中：强调色文字 + 强调色半透明底，替换浏览器默认的黄色 <mark>
  &__mark {
    padding: 0 1px;
    color: var(--screen-accent-bright);
    background: rgba(47, 227, 192, 0.18);
    border-radius: 2px;
  }

  &__tag {
    flex: 0 0 auto;
    padding: 0 5px;
    font-style: normal;
    font-size: var(--screen-font-xs);
    line-height: 16px;
    color: var(--screen-text-mute);
    background: rgba(2, 24, 28, 0.72);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
  }

  &__count {
    flex: 0 0 auto;
    padding: 0 6px;
    font-style: normal;
    font-size: var(--screen-font-xs);
    line-height: 16px;
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-sub);
    background: rgba(47, 227, 192, 0.08);
    border-radius: var(--screen-radius-pill);
  }

  &__empty {
    padding: 16px 10px;
    font-size: var(--screen-font-sm);
    text-align: center;
    cursor: default;
    .screen-placeholder();
  }

  // 选中行内的图标与数量胶囊一并提亮，避免与强调色文字割裂
  &__node.is-selected &__icon,
  &__node.is-selected &__count {
    color: var(--screen-accent);
  }
}

// 尊重「减少动态效果」：悬停底色与箭头过渡全部取消
@media (prefers-reduced-motion: reduce) {
  .screen-tree__node,
  .screen-tree__twisty {
    transition: none;
  }
}
</style>
