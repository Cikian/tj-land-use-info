# 大屏基础组件库 · Screen UI

> 设计来源：UI 设计稿《天津市经营性用地市政基础设施配套动态监管工作站》
> （`saved-images/20260911-150021-gis-ui-*.png`，取色与尺寸均按设计稿实测）
> 主题：**青绿暗色**（固化主题，不随项目 `blue/green` 皮肤切换）

本目录是地图大屏页面的基础组件层。页面只负责「摆位置 + 喂数据」，
所有视觉规范、交互细节、无障碍处理都收敛在组件内部。

---

## 1. 设计令牌

| 文件 | 作用 | 引入方式 |
| --- | --- | --- |
| `styles/screen-tokens.less` | CSS 自定义属性（颜色 / 间距 / 字号 / 圆角 / 动效） | 已在 `src/main.js` **全局引入一次** |
| `styles/screen-mixins.less` | Less 混入（玻璃面板、条形渐变、省略、焦点环） | 组件内按相对路径 `@import` |

**约定：组件样式一律使用 `var(--screen-*)`，禁止写死颜色。**
需要换肤时只改 `screen-tokens.less` 一个文件即可。

核心令牌（节选，均为设计稿实测取色）：

```less
--screen-text: #dcedee;          // 主文本
--screen-text-sub: #8fb6b8;      // 次级文本
--screen-text-mute: #5e8285;     // 弱化文本
--screen-number: #62f0c4;        // 大号统计数字（设计稿 #60F0C0）
--screen-accent: #2fe3c0;        // 主强调（激活态 / 描边 / 按钮）
--screen-panel-bg: rgba(3, 27, 32, 0.72);
--screen-border: rgba(20, 168, 158, 0.26);

/* 数据可视化语义色 —— 设计稿里颜色是有含义的，不要随意替换 */
--screen-viz-green: #22c795;     // 市级占比条 / 表格常规项
--screen-viz-cyan: #0fc3ce;      // 区级占比条 / 排行条
--screen-viz-mint: #14d2a2;      // 环形图 市级 / 表盘填充
--screen-viz-blue: #1fa3e8;      // 环形图 区级
--screen-viz-amber: #f0a83c;     // 预警橙：资金 / 未开工 / 未竣工 / 未移交
```

对比度：正文 `--screen-text` 对面板底色 ≥ 4.5:1，弱化文本仅用于单位 / 序号等非关键信息。

---

## 2. 组件清单

### 2.1 容器 / 布局

| 组件 | 用途 | 关键 Props |
| --- | --- | --- |
| `ScreenPanel` | 面板外框（标题栏 / 内容 / 底栏 / 折叠 / 四角装饰） | `title` `subTitle` `bar` `collapsible` `scrollable` `decorated` `variant` `flat` |
| `ScreenHeader` | 顶栏（标题 / 导航 / 实时时钟 / 用户 / 在线态） | `title` `menus` `activeMenu` `showClock` `userName` `online` |
| `ScreenMapStage` | 地图舞台（地图插槽 + 兜底底图 + 取景框 + 暗角） | `enabled` `showGrid` `showVignette` `showFrame` `status` |
| `ScreenModal` | 弹窗（挂 body + 焦点陷阱 + 滚动锁） | `visible`(`.sync`) `title` `width` `size` `fullscreen` `maskClosable` `escClosable` `showFooter` `confirmLoading` |
| `ScreenPopover` | 浮层容器（挂 body，供下拉/气泡复用） | `open` `anchor` `placement` `width` `maxHeight` `role` |
| `ScreenPopconfirm` | 危险操作二次确认 | `title` `description` `tone` `okText` `placement` `confirmLoading` |

### 2.2 展示 / 数据可视化

| 组件 | 用途 | 关键 Props |
| --- | --- | --- |
| `ScreenStatValue` | 大号统计数值（含数字滚动） | `value` `unit` `label` `hint` `size` `tone` `precision` |
| `ScreenCountUp` | 数字滚动原子组件 | `value` `duration` `precision` `animated` |
| `ScreenSplitStat` | 分级占比条（市级 / 区级，多列网格） | `items` `columns` `unit` `precision` `animated` |
| `ScreenDonut` | 环形占比图 | `data` `size` `thickness` `gap` `centerValue` `centerLabel` `legend` `colors` |
| `ScreenGauge` | 仪表盘进度（底部开口圆弧） | `percent` `value` `label` `size` `thickness` `startAngle` `sweep` |
| `ScreenRankList` | 排行条形列表（单行紧凑式） | `items` `max` `unit` `showIndex` `sortDesc` `clickable` |
| `ScreenBarList` | 标签条形列表（两行堆叠式，适合分类统计） | `items` `max` `unit` `showPercent` `sortDesc` `clickable` |
| `ScreenLineChart` | 趋势折线 / 面积图（手写 SVG，无图表库） | `data` `label` `height` `unit` `tone` `showArea` `showPoints` `showAxis` |
| `ScreenDataTable` | 数据表格 | `columns` `data` `rowKey` `stripe` `minWidth` `maxHeight` `selectable` `selectedKeys` `rowClickable` `loading` |
| `ScreenDescriptions` | 标签-值描述栅格（替代 a-descriptions） | `items` `columns` `bordered` `size` `labelWidth` |
| `ScreenTabs` | 标签页（胶囊 / 下划线） | `tabs` `value`(`v-model`) `type` `align` |
| `ScreenTag` | 状态标签（胶囊） | `tone` `size` `outline` |
| `ScreenEmpty` | 空状态 | `text` `description` `size` `bordered` |
| `ScreenLoading` | 加载态（遮罩 / 内联） | `loading` `text` `overlay` `size` |

### 2.3 表单控件（档案管理模块新增）

| 组件 | 用途 | 关键 Props |
| --- | --- | --- |
| `ScreenField` | 字段容器（标签 + 控件 + 提示/错误） | `label` `required` `tip` `error` `labelWidth` `htmlFor` `size` `stacked` |
| `ScreenInput` | 输入框（单行 / 多行 / 数字） | `value` `type` `placeholder` `maxlength` `rows` `clearable` `invalid` `icon` |
| `ScreenSelect` | 下拉选择 | `value` `options` `searchable` `filterLocal` `clearable` `ariaLabel` `placement` |
| `ScreenTreeSelect` | 树形下拉（分类选择） | `value` `nodes` `leafOnly` `disabledStatus` `clearable` `ariaLabel` |
| `ScreenDateInput` | 日期选择（单日 / 区间） | `value` `mode` `inputType` `startPlaceholder` `endPlaceholder` `clearable` |
| `ScreenRadioGroup` | 分段单选 | `value` `options` `disabled` `ariaLabel` `size` |
| `ScreenButton` | 按钮 | `type` `size` `icon` `loading` `disabled` `block` `square` `ariaLabel` |
| `ScreenPagination` | 分页器 | `current` `pageSize` `total` `pageSizeOptions` `showSizeChanger` `showQuickJumper` |
| `ScreenTree` | 树（展开 / 选中 / 拖拽排序与改挂） | `nodes` `selectedKeys` `expandedKeys`(`.sync`) `draggable` `draggableGuard` `highlightKeyword` |
| `ScreenUpload` | 文件上传（XHR + 进度 + 校验） | `action` `headers` `data` `multiple` `allowedExt` `maxSizeMb` `buttonText` |

### 2.4 原子

| 组件 | 用途 | 关键 Props |
| --- | --- | --- |
| `ScreenIcon` | 线性图标集（**禁止用 emoji 当结构图标**） | `name` `size` `strokeWidth` `spin` `title` |
| `ScreenToast` | 消息提示容器（一般通过 `toast.js` 命令式调用） | 由服务层调用 `push()` |

### 2.5 消息提示（命令式）

大屏是固化暗色主题，antd 的 `$message` 是浅色气泡，直接使用会破坏整体观感，
所以提供同族的命令式提示：

```js
// 组件内（已在 main.js 注册插件）
this.$screenToast.success('保存成功')
this.$screenToast.error(res.message)

// 非组件代码
import { toast } from '@/components/screen/toast'
toast.warning('已开始导出，请稍候…')
```

`toast.js` 还导出了 `assertSuccess(res, fallback)`，用于「接口失败就弹后端 message」
这一在各页面重复出现的收敛逻辑。

### 注册方式

```js
// 按需（推荐）
import { ScreenPanel, ScreenDonut } from '@/components/screen'
export default { components: { ScreenPanel, ScreenDonut } }

// 或者全局
import ScreenUI from '@/components/screen'
Vue.use(ScreenUI)
```

---

## 3. 典型用法

```vue
<screen-panel title="出让地块情况统计">
  <screen-stat-value :value="817" unit="宗" />
  <screen-split-stat :items="[
    { label: '市级', value: 215, percent: 26, tone: 'green' },
    { label: '区级', value: 602, percent: 74, tone: 'cyan' },
  ]" />
  <screen-donut :data="[{ name: '市级', value: 215 }, { name: '区级', value: 602 }]"
                center-label="市级" :size="136" />
  <template #footer>合计 215 宗 · 388 条道路</template>
</screen-panel>
```

`ScreenSplitStat.tone`：`green`（默认，对应设计稿「市级」）/ `cyan`（「区级」）/ `amber` / `muted`。
`ScreenDataTable` 百分比列的 `tone`：`accent`（绿）/ `warning`（橙）/ `danger` / `muted`。

表格列 `type` 支持：`text` / `number` / `percent` / `action` / `index` / `tag` / `slot`。

```js
const columns = [
  { key: 'action', title: '操作', width: 86, type: 'action', align: 'center' },
  { key: 'district', title: '行政区划', width: 120, type: 'text' },
  { key: 'fund', title: '资金未落实', width: 130, type: 'percent', tone: 'warning' },
  // formatter(value, row, col) 可覆盖默认渲染文本
]
```

`type: 'slot'` 时用 `#<col.key>` 插槽自定义单元格：

```vue
<screen-data-table :columns="cols" :data="rows">
  <template #district="{ row }">
    <a @click="detail(row)">{{ row.district }}</a>
  </template>
</screen-data-table>
```

---

## 4. 布局与地图共存

地图大屏的关键约束：**浮层不能吃掉地图的鼠标事件**。

页面采用如下约定（见 `src/views/screen/index.vue`）：

```less
.land-screen__ui  { pointer-events: none; }  /* 浮层整体穿透 */
.land-screen__panel { pointer-events: auto; } /* 面板自身可交互 */
```

`ScreenHeader` 内部同样只在可点击元素上开启 `pointer-events: auto`，
因此顶部栏的空白处依旧可以拖动地图。

---

## 5. 无障碍与体验约定

- **不依赖颜色单独传达信息**：环形图 / 等级条 / 表格百分比均同时给出数值文本；
  图形元素带 `role="img"` + `aria-label`；图例是真实文本节点。
- **键盘可达**：`ScreenTabs` 遵循 WAI-ARIA tabs 模式，支持 `←` `→` `Home` `End`；
  所有可点击元素都有 `:focus-visible` 焦点环，且不使用会改变布局尺寸的位移。
- **尊重 `prefers-reduced-motion`**：`ScreenCountUp` / 入场条形动画 / 过渡时长
  在系统开启「减少动态效果」时全部降级为直接呈现终值。
- **后台不空转**：`ScreenHeader` 的时钟在 `document.hidden` 时停止定时器。
- **表格语义**：`ScreenDataTable` 使用原生 `<table>` + `<th scope="col">`，
  横向溢出时局部滚动，纵向溢出时表头 `sticky` 吸顶。

---

## 6. 注意事项（踩坑记录）

1. **Less 会把 `grid-column: 1 / -1` 当成除法算成 `-1`**，导致网格项跑位。
   跨列请写成两个长属性：
   ```less
   grid-column-start: 1;
   grid-column-end: -1;
   ```
   同理，任何 CSS 简写里出现 `数字 / 数字`（如 `font: 12px/1.5`）都要用 `~"..."` 转义。
2. 组件样式通过 `@import './styles/screen-mixins.less'` 引入混入；**设计令牌
   （`screen-tokens.less`）已全局引入，组件内不要再重复导入**，避免 CSS 体积翻倍。
   注意：**在 `views/` 下引用时路径必须带 `~`**（`@import '~@/components/screen/styles/screen-mixins.less'`），
   否则 less-loader 不会走 webpack 别名解析，构建直接报 "wasn't found"。
3. 页面里 `.land-screen__panel` 必须显式 `pointer-events: auto`，
   否则面板会被浮层的 `pointer-events: none` 一起穿透，按钮点不动。
   同理，整页模块（`.land-screen__module`）也要显式开启。
4. `#home`（Cesium 容器）的宽高由 `S3dmViewer.vue` 的非 scoped 样式提供，
   因此新页面必须 `import S3dmViewer`，否则地图容器高度为 0。

### 交互层新增的踩坑（2026 年补，都已在代码里修掉）

5. **面板会裁剪浮层**：`ScreenPanel` 有 `overflow: hidden` + `backdrop-filter`，
   后者还会让面板成为 `position: fixed` 的包含块。所以任何「贴着控件弹出的
   下拉 / 气泡」都必须走 `ScreenPopover`（它会把自身节点移动到 `document.body`），
   直接在面板里写 `position: absolute` 的下拉一定会被裁掉或错位。
6. **浮层的 z-index 必须高于弹窗**：`ScreenPopover` 默认 `1260`、
   `ScreenModal` 是 `1200`、`ScreenToast` 是 `1300`。
   低于弹窗会让「弹窗里的下拉」被盖住，看不见也点不到。
7. **`v-slot` 不能包在 `v-if` 里**：Vue 2.6 会报
   "v-slot can only appear at the root level inside the receiving component"。
   要按条件显示某个具名插槽，请把 `v-if` 下沉到插槽内容里：
   ```vue
   <template #extra>
     <template v-if="selected">...</template>
   </template>
   ```
8. **模板注释里不要再写 `<!-- -->` 示例**：内层 `-->` 会提前结束注释块，
   导致模板出现多个根节点，报 "exactly one root element"。
   文档里要举标签例子就用纯文字（例如【screen-icon】）。
9. **`ScreenLoading` 的根节点不是弹性子项**：它是普通块级元素，
   放在 flex 列里不会拉伸，里面的面板 / 表格会因为拿不到确定高度而塌陷。
   用法是给它加个类并显式 `flex: 1 1 auto; min-height: 0`（见 `ArchiveDetailModal`）。
10. **不要在 computed 里给响应式属性赋值**：`ScreenUpload` 早期版本在 `inputId`
   这个 computed 内部生成并写回 `localId`，会让 computed 自我失效
   （eslint `vue/no-side-effects-in-computed-properties`）。唯一 id 请在 `data()` 里生成。
11. **下拉的鼠标选择会和 blur 打架**：点击 `<li>` 选项时焦点会离开触发器，
    若在 blur 里立刻关闭浮层，`click` 事件就再也不会触发，鼠标选择彻底失效。
    解决办法是选项上加 `@mousedown.prevent`（保住焦点），且 blur 处理里对
    `relatedTarget === null` 不做立即关闭。改 `ScreenSelect` 时务必保留这两点。
12. **Vue 2 不支持 CSS 里的 `v-bind()`**（那是 Vue 3 SFC 的特性）。
    需要动态列宽这类值时用内联 `:style` 传，不要写
    `grid-template-columns: v-bind(x)`。
13. **业务模块不做前端按钮鉴权，不要再加回 `v-has`**。
    原因是本项目有**两套互相独立的角色体系**：一套在中台，一套在业务（Java）后端。
    登录走的是中台用户体系，业务后端的角色数据与当前登录用户对不上，
    因此业务后端下发的按钮权限清单没有意义。

    而 `v-has`（`utils/hasPermission.js`）的行为是
    「**授权清单为空就把元素从 DOM 里删掉**」，admin-client 之所以正常，
    是因为它在 `GetPermissionList` 里调 `/sys/permission/getUserPermissionByToken`
    把清单写进了 sessionStorage；stargis-client-web 的同名 action 被改成了
    「拉中台场景数据 + 鼠标超时退出」，**从不下发这份清单** ——
    于是全站所有带 `v-has` 的按钮都会被删掉，表现为
    「功能明明写了，按钮却不出现」。

    所以档案（业务）模块的做法是：**按钮一律直接渲染，不做 `v-if` 权限判断**。
    业务后端已放开这些接口；若将来要恢复鉴权，必须先把两套角色体系统一
    （或让业务后端能按中台用户解析角色），否则只会再次把所有按钮藏起来。

    > 排查历史问题时的经验：`window.__buttonAuthDebug()` / `utils/buttonAuth.js`
    > 是早期为解决该问题临时加的机制，已随本次「移除业务鉴权」一并删除。

---

## 7. 目录结构

```
src/components/screen/
├─ index.js                    # 统一出口 + Vue 插件（新增组件务必在这里登记，否则导入到 undefined）
├─ utils.js                    # 纯函数工具（格式化 / 动效 / 唯一 id）
├─ toast.js                    # 命令式消息提示服务（$screenToast / toast）
│
├─ ScreenPanel.vue             # 面板容器
├─ ScreenHeader.vue            # 顶栏
├─ ScreenMapStage.vue          # 地图舞台
├─ ScreenModal.vue             # 弹窗（挂 body + 焦点陷阱 + 滚动锁）
├─ ScreenPopover.vue           # 浮层容器（挂 body，下拉 / 气泡的公共基座）
├─ ScreenPopconfirm.vue        # 危险操作二次确认
├─ ScreenStatValue.vue         # 大号统计数值
├─ ScreenCountUp.vue           # 数字滚动
├─ ScreenSplitStat.vue         # 分级占比条
├─ ScreenDonut.vue             # 环形占比图
├─ ScreenGauge.vue             # 仪表盘进度
├─ ScreenRankList.vue          # 排行条形列表（单行紧凑式）
├─ ScreenBarList.vue           # 标签条形列表（两行堆叠式）
├─ ScreenLineChart.vue         # 趋势折线 / 面积图
├─ ScreenDataTable.vue         # 数据表格（含行选择 / 链接 / 多操作 / 加载态）
├─ ScreenDescriptions.vue      # 标签-值描述栅格
├─ ScreenTabs.vue              # 标签页
├─ ScreenTag.vue               # 状态标签
├─ ScreenEmpty.vue             # 空状态
├─ ScreenLoading.vue           # 加载态
├─ ScreenField.vue             # 表单字段容器
├─ ScreenInput.vue             # 输入框
├─ ScreenSelect.vue            # 下拉选择
├─ ScreenTreeSelect.vue        # 树形下拉
├─ ScreenDateInput.vue         # 日期选择（单日 / 区间）
├─ ScreenRadioGroup.vue        # 分段单选
├─ ScreenButton.vue            # 按钮
├─ ScreenPagination.vue        # 分页器
├─ ScreenTree.vue              # 树（拖拽排序 / 改挂）
├─ ScreenUpload.vue            # 文件上传
├─ ScreenIcon.vue              # 线性图标集（禁止用 emoji 当结构图标）
├─ ScreenToast.vue             # 消息提示容器
└─ styles/
   ├─ screen-tokens.less       # 设计令牌（全局引入）
   └─ screen-mixins.less       # Less 混入（含控件 / 浮层 / 链接操作混入）
```

### 页面

| 页面 | 路由 | 文件 |
| --- | --- | --- |
| 地图大屏首页 | `/`、`/screen` | `src/views/screen/index.vue`，演示数据 `src/views/screen/mock.js` |
| 档案管理（大屏子页面） | 顶栏「档案管理」，深链 `/screen/archive` | `src/views/screen/archive/index.vue` |

### 档案模块

「档案管理」二级菜单的页签顺序固定为：
**档案维护 → 档案查询 → 档案统计 → 收发文管理 → 档案类别管理**
（见 `views/screen/archive/index.vue` 的 `tabs` 与 `PANELS`）。

```
src/views/screen/archive/
├─ index.vue                   # 页签壳（上面五个页签）
├─ constants.js                # 共享枚举与展示辅助（唯一实现，页面不许再抄一份）
└─ modules/
   ├─ ArchiveMaintain.vue      # 档案维护（列表 + 增删改 + 批量删除 + 导出）
   ├─ ArchiveQuery.vue         # 档案查询（只读列表 + 导出 + 支持按项目下钻）
   ├─ ArchiveStatistics.vue    # 档案统计（KPI + 3 张图 + 按项目明细）
   ├─ ArchiveCategory.vue      # 档案类别管理（树 + 详情 + 拖拽排序/改挂）
   ├─ ArchiveSearchForm.vue    # 共享检索条件（4 个常驻 + 10 个折叠）
   ├─ ArchiveTable.vue         # 共享列表表格（维护 / 查询复用，纯展示）
   ├─ ArchiveFormModal.vue     # 档案新增 / 编辑（三段式表单）
   ├─ ArchiveDetailModal.vue   # 档案详情（全屏 + 四页签）
   ├─ ArchiveFileTable.vue     # 卷内文件表 + 上传
   ├─ CategoryFormModal.vue    # 类别新增 / 编辑
   ├─ CategoryPicker.vue       # 类别选择器（leafOnly 两套语义；收发文归档也复用它）
   ├─ ProjectPicker.vue        # 出让宗地 → 配套项目 两级联动（收发文表单也复用它）
   └─ doc/                     # 收发文管理（方案 2.3.2 第 9 项）
      ├─ DocManager.vue        # 二级页签壳（收文管理 / 发文管理）
      ├─ DocReceivePanel.vue   # 收文管理（统计卡快捷筛选 + 检索 + 列表 + 流转）
      ├─ DocSendPanel.vue      # 发文管理（纯台账，无流转）
      ├─ DocReceiveFormModal.vue  # 收文 登记 / 编辑
      ├─ DocSendFormModal.vue     # 发文 登记 / 编辑
      ├─ DocFlowModal.vue      # 收文流转（转办 / 退回 / 办结 + 流转时间轴）
      ├─ DocArchiveModal.vue   # 收发文归档（档案类别必填；收文发文共用）
      ├─ DocDetailModal.vue    # 收发文详情（收文发文共用，按 docType 切字段）
      ├─ DocAttachmentTable.vue   # 公文附件表 + 上传（详情页复用其只读模式）
      └─ DocUserSelect.vue     # 人员选择器（v-model 绑定 username 而非 id）
```

**收文的流转模型**（`DocFlowModal`）：
```
登记 ─► 待承办 ─转办/分办─► 承办中 ─办结─► 已办结 ─归档─► 已归档
                  ▲              │
                  └─── 退回 ─────┘
```
`transfer` / `reject` / `finish` 是三个语义化接口，校验分别挂在各自动作上
（转办必须选人且不能转给自己、退回必须写原因、办结意见可空）。
**归档只允许从「已办结」进入**，并且档案类别必填 —— 这条规则前后端一致，
前端把入口条件写成 `row.status === '已办结' && !row.archiveId`。
**发文没有流转**（后端表里没有 status / current_handler 列），
归档条件是 `!row.archiveId`。

几个移植时容易踩的点（都已在代码注释里写明）：
- `archived` 筛选：后端要真布尔，但 `ScreenSelect` 的 value 只接受 String/Number，
  所以界面用 `'true'/'false'`，在 `normalizeQuery()` 里转回布尔（否则「已归档」会筛出「未归档」且不报错）；
- 附件上传的存储路径在响应的 **`message`** 里（`storePath = res.message || res.result`），
  且 biz 目录分收文 / 发文（`/receive/yyyy/MM`、`/send/yyyy/MM`），不要与档案的 `/archive/...` 混用；
- `DocAttachment` 后端**没有** `url` / `readableSize` 字段，所以下载要走
  `getJavaFileAccessHttpUrl(storePath)`，大小要前端 `formatSize()` 兜底；
- 详情页数字不要用 `value || '—'`：`0 页 / 0 份` 是合法值，会被误显示成「—」。


### 两个后端：业务请求必须走 `VUE_DATA_JAVA_URL`

这是本项目最容易踩的坑，改档案/业务接口前务必先读这一段。

`public/static/config.js` 里定义了两个后端地址，且 `window._CONFIG` 的值会**覆盖** `.env`：

| 配置项 | 含义 | 示例 |
| --- | --- | --- |
| `VUE_APP_API_BASE_URL` | **中台**（网关） | `http://127.0.0.1:4548` |
| `VUE_DATA_JAVA_URL` | **Java 业务后端** | `http://127.0.0.1:9802/api` |

而 `src/config/index.js` 里 `domianURL = VUE_APP_API_BASE_URL`，所以
`@/api/manage`（走 `domianURL`）发出的请求全部落在**中台**上——
登录、单点登录、场景/图层（`/app/**`）这些确实应该走中台。

但档案管理这类业务接口（`/land/**`、`/sys/common/**`）是 **Java 后端**的，
必须换通道，否则会打到中台上 404。因此新增了：

```
src/api/manageJava.js     # Java 业务后端请求层
  javaBaseUrl()                  业务后端基址
  javaStaticBaseUrl()            /sys/common/static 基址
  getJavaFileAccessHttpUrl()     临时文件预览地址
  javaGetAction / javaPostAction / javaPutAction / javaDeleteAction / javaHttpAction
  buildJavaDownloadUrl()         原生下载地址（token 拼在 query 上）
  javaUploadUrl()                /sys/common/upload
```

`src/api/land/{archive,archiveCategory,landData}.js` 全部基于它，
**不要**改回 `@/api/manage`。

两点实现上的注意：

1. **不能只覆盖 `baseURL` 而继续用 `@/api/manage`**。因为 `@/api/manage` 会给每个请求塞
   `parameter.access_token`，而 `src/utils/request.js` 的请求拦截器一旦看到
   `params.access_token`，就会额外往**中台**发一次 `/app/sceneCreateApi/dataList` 做刷新判断；
   失败时会清 token 并整页 reload。业务请求不需要这套机制。
   `manageJava.js` 改为只带 `X-Access-Token` 请求头，从而跳过那次多余的中台往返。
2. **令牌要靠请求头传**。本项目的 `request.js` 请求拦截器是被注释掉的
   （不像 admin-client 那样自动加 `X-Access-Token`），所以 `manageJava.js`
   显式设置 `X-Access-Token`；同时按 admin-client 的做法带上
   `X-Sign` / `X-TIMESTAMP`（`X-TIMESTAMP` 用毫秒，与后端 `SignAuthInterceptor` 兼容）。
   原生上传（XHR）与原生下载（`window.open`）绕过 axios，因此分别手动带
   `X-Access-Token` 头和 `?token=` 查询参数。

### 业务接口不再做前端鉴权

业务后端（Java 端）的接口目前**已放开**，前端也不做按钮级鉴权：
档案模块的所有操作按钮都直接渲染，没有 `v-if` 权限判断，也没有 `v-has`。

原因是本项目有**两套互相独立的角色体系**（中台一套、业务后端一套），
登录走的是中台用户体系，业务后端的角色与登录用户对不上，
下发的按钮权限清单没有参考价值 —— 而 `v-has` 在清单为空时会把元素从 DOM 删掉，
只会导致「功能写了但按钮不出现」。详见第 6 节第 13 条。

`manageJava.js` 仍然带着 `X-Access-Token`：鉴权（这个请求是谁发的）与授权
（这个用户能不能调这个接口）是两件事，token 留着既不影响放开后的接口，
也能在将来恢复鉴权时直接生效。

判断某个接口属于哪个后端的方法：看 **admin-client** 能不能调通它。
admin-client 的 `public/static/config.js` 把 `VUE_APP_API_BASE_URL` 留空，
于是它的 `domianURL` 退化成 `.env` 里的 `http://127.0.0.1:9802/api`，
正好就是 Java 后端——所以「admin-client 调的接口」=「Java 后端接口」。

接口层：`src/api/land/{archive,archiveCategory,landData}.js`，与 `admin-client`
的同名文件一一对应。**两端共用同一套后端，改动接口时必须同步两个仓库**，
否则会出现「一边能存、另一边筛不出来」的契约漂移。

调试技巧：
- `?map=0` 关闭真实 Cesium 地图，只看界面（无地图服务时很有用）；
- `/screen/archive?tab=statistics&map=0` 直接落到档案统计页签。

