/**
 * 高保真模型切图资源索引（蓝色科技风 · 2026-09 版）
 * ------------------------------------------------------------------
 * 来源：UI 同事交付的高保真原型
 *       docs/高保真/市政-天津市经营性用地市政配套设施动态监管工作站/首页.html
 *       （Axure 导出，原始切图位于该目录 images/首页/）
 *
 * 复制到工程时统一改为 ASCII 文件名，避免中文路径在不同构建环境下的编码问题；
 * 本文件是「原始文件名 → 工程文件名」的唯一映射入口，组件里只允许引用 hf.xxx，
 * 不要直接写 require('@/assets/screen-blue/....png')。
 */

export const hf = {
  /* ---------- 全屏氛围 / 蒙版 ---------- */
  vignetteTop: require('./vignette-top.png'),
  vignetteBottom: require('./vignette-bottom.png'),
  vignetteLeft: require('./vignette-left.png'),
  vignetteRight: require('./vignette-right.png'),

  /* ---------- 顶栏 ---------- */
  headerBg: require('./header-bg.png'),
  title: require('./title.png'),
  avatar: require('./avatar.png'),
  caretDown: require('./caret-down.svg'),
  navActive: require('./nav-active.png'),
  navIdle: require('./nav-idle.png'),

  /* ---------- 面板框 / 标题装饰 ---------- */
  panelLeftBg: require('./panel-left-bg.png'),
  panelRightBg: require('./panel-right-bg.png'),
  panelTitleLeft: require('./panel-title-left.png'),
  panelTitleRight: require('./panel-title-right.png'),
  attrBg: require('./attr-bg.png'),
  attrBar: require('./attr-bar.png'),
  modalBg: require('./modal-bg.png'),

  /* ---------- 页签 ---------- */
  tabTrack: require('./tab-track.svg'),
  tabIdle: require('./tab-idle.svg'),
  tabActive: require('./tab-active.svg'),

  /* ---------- 统计卡片 / 环形图 ---------- */
  statCard: require('./stat-card.png'),
  statCubeA: require('./stat-cube-a.png'),
  statCubeB: require('./stat-cube-b.png'),
  splitCard: require('./split-card.png'),
  splitCardActive: require('./split-card-active.png'),
  ringTrack: require('./ring-track.svg'),
  /** 出让情况 · 市级：绿色 28% 弧 */
  ringFillGreen: require('./ring-fill-green.svg'),
  /** 出让情况 · 区级：青色 72% 弧 */
  ringFillTeal: require('./ring-fill-teal.svg'),
  /** 落实配套情况 · 市级：红色 28% 弧（高保真原色） */
  ringFillRed: require('./ring-fill-red.svg'),
  /** 落实配套情况 · 区级：金色 72% 弧（高保真原色） */
  ringFillGold: require('./ring-fill-gold.svg'),

  /* ---------- 表格 ---------- */
  tableHead: require('./table-head.png'),
  tableBody: require('./table-body.png'),
  tableRow: require('./table-row.png'),
  tableRowHover: require('./table-row-hover.png'),
  attrHead: require('./attr-head.png'),
  attrBody: require('./attr-body.png'),
  attrRow: require('./attr-row.png'),
  attrRowHover: require('./attr-row-hover.png'),
  actionView: require('./action-view.svg'),
  actionViewHover: require('./action-view-hover.svg'),

  /* ---------- 折叠 / 展开 ---------- */
  attrCollapse: require('./attr-collapse.svg'),
  attrCollapseHover: require('./attr-collapse-hover.svg'),
  attrExpand: require('./attr-expand.svg'),
  attrExpandHover: require('./attr-expand-hover.svg'),

  /* ---------- 检索框 / 图层树 ---------- */
  fieldSearch: require('./field-search.svg'),
  fieldSearchHover: require('./field-search-hover.svg'),
  fieldSearchIcon: require('./field-search-icon.svg'),
  treeBg: require('./tree-bg.png'),
  treeRow: require('./tree-row.png'),
  /** 8×6 展开三角（收起时旋转 -90° 指向右侧） */
  treeCaret: require('./tree-caret.svg'),
  /** 15×14 文件夹图标，只用于 level 0 分组 */
  treeFolder: require('./tree-folder.svg'),
  /** 14×14 图层类型图标：level 1 / 2 使用；选中行用高亮版 */
  treeLayer: require('./tree-layer.svg'),
  treeLayerActive: require('./tree-layer-active.svg'),
  treeSwitchOn: require('./tree-switch-on.svg'),
  treeSwitchOff: require('./tree-switch-off.svg'),

  /* ---------- 地块预警弹窗 ---------- */
  modalClose: require('./modal-close.svg'),
  modalCloseHover: require('./modal-close-hover.svg'),
  plotTableHead: require('./plot-table-head.png'),
  plotTableBody: require('./plot-table-body.png'),
  plotRow: require('./plot-row.png'),
  plotRowHover: require('./plot-row-hover.png'),

  /* ---------- 分页 ---------- */
  pagerInput: require('./pager-input.svg'),
  pagerSize: require('./pager-size.svg'),
  pagerPage: require('./pager-page.svg'),
  pagerCaret: require('./pager-caret.svg'),
}

export default hf
