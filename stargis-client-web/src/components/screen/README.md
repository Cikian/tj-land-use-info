# 大屏基础组件库 · Screen UI

> 设计来源：UI 同事交付的高保真模型
> `docs/高保真/市政-天津市经营性用地市政配套设施动态监管工作站/首页.html`
> （Axure 导出；配色取自 `files/首页/styles.css` 的文字色 + 切图实测底色，
> 切图已复制到 `src/assets/screen-blue/`，见该目录 `index.js` 的映射表）
> 主题：**深海蓝科技风**（固化主题，不随项目 `blue/green` 皮肤切换）
>
> 上一版为「青绿暗色」（源自 `saved-images/20260911-150021-gis-ui-*.png`），
> 2026-09 按新的高保真模型整体换肤：**只改了 `screen-tokens.less` 与少量硬编码
> 高亮色**，组件结构未变，因此所有既有页面自动跟随。

本目录是地图大屏页面的基础组件层。页面只负责「摆位置 + 喂数据」，
所有视觉规范、交互细节、无障碍处理都收敛在组件内部。

高保真首页（`views/screen/home/`）另有一套**自适应舞台**实现：设计稿是 1920×1080，
面板边框/标题装饰/表格底纹都是定尺切图，因此该页用 `ScreenStage` 按高度缩放、
宽度自适应伸展（铺满且不留黑边），坐标一律使用设计稿原值。详见第 4 节。

---

## 1. 设计令牌

| 文件 | 作用 | 引入方式 |
| --- | --- | --- |
| `styles/screen-tokens.less` | CSS 自定义属性（颜色 / 间距 / 字号 / 圆角 / 动效）+ 全局工具类 | 已在 `src/main.js` **全局引入一次** |
| `styles/screen-mixins.less` | Less 混入（玻璃面板、条形渐变、省略、焦点环） | 组件内按相对路径 `@import` |

**约定：组件样式一律使用 `var(--screen-*)`，禁止写死颜色。**
需要换肤时只改 `screen-tokens.less` 一个文件即可。

核心令牌（节选，均为高保真实测取色）：

```less
--screen-text: #ffffff;          // 主文本（高保真标题与表格主要文字为纯白）
--screen-text-sub: #66afd4;      // 次级文本（高保真大量使用）
--screen-text-mute: #4a7396;     // 弱化文本
--screen-number: #ffe597;        // 大号统计数字（高保真指定暖黄 #FFE597）
--screen-accent: #82c6ff;        // 主强调（激活态 / 描边 / 图标）
--screen-accent-soft: #66afd4;   // 次强调（单位 / 标签）
--screen-panel-bg: rgba(8, 22, 42, 0.92);
--screen-border: rgba(55, 132, 215, 0.32);

/* 数据可视化语义色 —— 高保真里颜色是有含义的，不要随意替换 */
--screen-viz-green: #43e972;     // 出让情况 · 市级环形图
--screen-viz-cyan: #3fd8d0;      // 出让情况 · 区级环形图
--screen-viz-amber: #f5a524;     // 预警橙：资金 / 未开工 / 未竣工 / 未移交
```

> 落实配套情况的环形图在高保真里用的是红 `#e95b43` / 金 `#c99e54`（见
> `assets/screen-blue/ring-fill-red.svg` / `ring-fill-gold.svg`），表示「未落实」，
> 与出让情况的绿 / 青不是同一套语义，实现时按页签区分引用，不要统一。

全局工具类：`.stage-hit` —— `ScreenStage` 画布整体 `pointer-events: none`
（否则全屏蒙版会挡住地图），画布内需要交互的元素必须挂这个类。

对比度：正文 `--screen-text` 对面板底色 ≥ 4.5:1，弱化文本仅用于单位 / 序号等非关键信息。

---

## 2. 组件清单

### 2.1 容器 / 布局

| 组件 | 用途 | 关键 Props |
| --- | --- | --- |
| `ScreenStage` | 自适应舞台（定尺设计画布 + 真实像素地图层 + 铺满不留黑边） | `width` `height` `minDesignWidth` `maxScale` `minScale`；插槽 `map` / 默认；事件 `resize` |
| `ScreenPanel` | 面板外框（标题栏 / 内容 / 底栏 / 折叠 / 四角装饰） | `title` `subTitle` `bar` `collapsible` `scrollable` `decorated` `variant` `flat` |
| `ScreenHeader` | 顶栏（高保真切图 + 7 项导航 + 实时时钟 + 用户区下拉） | `title` `menus` `activeMenu` `showClock` `showWeekday` `userName` `logoutText` `online`；事件 `menu-change` `logout` |
| `ScreenMapStage` | 地图舞台（地图插槽 + 兜底底图 + 取景框 + 暗角） | `enabled` `showGrid` `showVignette` `showFrame` `status` |
| `ScreenModal` | 弹窗（挂 body + 焦点陷阱 + 滚动锁；可选非模态：不压暗背景 + 可拖动） | `visible`(`.sync`) `title` `width` `size` `fullscreen` `maskClosable` `escClosable` `showFooter` `confirmLoading` `dimmed`(默认 `true`) `draggable`(默认 `false`)；事件 `drag-end({x,y})` |
| `ScreenPickStage` | 「选取地块」模式（**不再隐藏页面**，只提供取消选择/选择完成两个按钮 + 提示） | `mapReady` `source` `title`；事件 `cancel` `done(geometry)` |
| `ScreenPopover` | 浮层容器（挂 body，供下拉/气泡复用） | `open` `anchor` `placement` `width` `maxHeight` `role` |
| `ScreenPopconfirm` | 危险操作二次确认 | `title` `description` `tone` `okText` `placement` `confirmLoading` |
| `ScreenResizeBar` | 横向拖动条：用鼠标/键盘改「上一个兄弟容器」的高度 | `value` `min` `max` `step` `disabled` `ariaLabel`；事件 `input`(实时) `change`(松手/键盘) |


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
| `ScreenDescriptions` | 标签-值描述栅格（替代 a-descriptions） | `items` `columns` `variant` `bordered` `size` `labelWidth`(封顶宽) |
| `ScreenTabs` | 标签页（胶囊 / 下划线） | `tabs` `value`(`v-model`) `type` `align` |
| `ScreenTag` | 状态标签（胶囊） | `tone` `size` `outline` |
| `ScreenEmpty` | 空状态 | `text` `description` `size` `bordered` |
| `ScreenLoading` | 加载态（遮罩 / 内联） | `loading` `text` `overlay` `size` |

#### `ScreenDescriptions` 的两个必读约定（详情页排版全靠它）

1. **`labelWidth` 是「上限」不是「固定宽」**
   标签列实际宽 = `min(该组最长标签的内容宽, labelWidth)`。
   短标签组不会留一片空白，长标签组也不会把值列挤到折行。
   `variant="flat"` 时单行行高恒为 29px（值默认单行 + 省略号，溢出自动补 `title`）。
2. **长文本要通读就用 `item.stack: true`**
   该条目独占整行、标签在上值在下，允许折行 —— 这是唯一会变高的条目。
   不标 `stack` 的长值会被省略号截断（悬停可看全文）。

详细版面契约与四条禁止回退的老路，见
`perf/detail-redesign/README.md` 的「版面契约」一节。

### 2.3 表单控件（档案管理模块新增）

| 组件 | 用途 | 关键 Props |
| --- | --- | --- |
| `ScreenField` | 字段容器（标签 + 控件 + 提示/错误） | `label` `required` `tip` `error` `labelWidth` `htmlFor` `size` `stacked` |
| `ScreenInput` | 输入框（单行 / 多行 / 数字） | `value` `type` `placeholder` `maxlength` `rows` `clearable` `invalid` `icon` |
| `ScreenSelect` | 下拉选择 | `value` `options` `searchable` `filterLocal` `clearable` `ariaLabel` `placement` |
| `ScreenTreeSelect` | 树形下拉（分类选择） | `value` `nodes` `leafOnly` `disabledStatus` `clearable` `ariaLabel` |
| `ScreenDateInput` | 日期选择（单日 / 区间） | `value` `mode` `inputType` `startPlaceholder` `endPlaceholder` `clearable` `stacked`(窄容器里区间改上下两行) |
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

## 4. 自适应舞台（ScreenStage）与地图共存

高保真模型是 **1920×1080 的定尺设计稿**，面板边框、标题装饰、表格底纹全部是
定尺切图。早期实现是「固定 1920×1080 画布 + 等比缩放 + 居中」，
在非 16:9 的窗口上必然出现黑边（实测 1912×962 左右各留约 100px），因此改成**按高度缩放、宽度自适应伸展**：

```vue
<screen-stage>
  <template #map><s3dm-viewer /></template>   <!-- 真实像素层 -->
  ...按设计稿坐标绝对定位的界面...              <!-- 缩放层 -->
</screen-stage>
```

```js
k  = clamp(min(视口高 / 1080, 视口宽 / minDesignWidth), minScale, maxScale)
设计画布 = (视口宽 / k) × (视口高 / k)，transform: scale(k)
```

`画布宽 × k ≡ 视口宽`，所以**任何比例都铺满、不留黑边**；`视口高 / k` 在设计画布
宽度 ≥ `minDesignWidth` 时恒等于 1080，纵向仍严格等于设计稿。

`minDesignWidth` 默认 **1920**（设计稿原宽），因为左/右面板 400 + 属性表 1000 + 间距 30
刚好铺满 1920，再窄属性表的 10 列表头就会互相挤压，顶栏标题切图也会和导航重叠。
于是：

| 屏幕比例 | 设计画布 | 多出来的空间去哪 |
| --- | --- | --- |
| 正好 16:9 | 1920×1080 | 无（与设计稿逐像素一致） |
| 比 16:9 更宽 | 宽 > 1920，高 = 1080 | 中间的**地图与属性表横向变宽**，两侧面板保持 400 不变 |
| 比 16:9 更窄 | 宽 = 1920，高 > 1080 | 中间的**地图纵向变高**，各面板与属性表仍按定尺高度摆放 |

所以画布内组件的写法约定：

- 靠左用 `left`、靠右用 `right`、靠上用 `top`、靠下用 `bottom`；
  **禁止把右/下位置写成 `1920 - x` / `1080 - y` 的固定值**；
- 需要横向拉伸的定尺切图用 `width: 100%` / `background-size: 100% 100%`；
  纵向不拉伸（多出来的高度给地图，避免切图变形）；
- 需要知道设计画布尺寸时读 CSS 变量 `--stage-w` / `--stage-h`（无单位数字）。

**为什么地图单独一层、不放进缩放画布：**
Cesium 的拾取与相机控制依赖 `canvas.clientWidth`（布局尺寸，不受 CSS `transform` 影响）
与事件 `clientX`（视觉尺寸，受 `transform` 影响），两者在 `transform: scale` 下不一致，
点击/拖拽会整体偏移。因此地图层直接**铺满舞台**（真实像素、不施加 `transform`），
UI 画布缩放后与它完全重合，地图交互坐标也保持正确。

地图大屏的关键约束：**浮层不能吃掉地图的鼠标事件**。

```less
/* ScreenStage 画布整体穿透 */
.screen-stage__canvas { pointer-events: none; }
/* 画布内需要交互的元素挂全局类（定义在 screen-tokens.less） */
.stage-hit { pointer-events: auto; }
```

`ScreenHeader` 内部同样只在可点击元素上开启 `pointer-events: auto`，
因此顶部栏的空白处依旧可以拖动地图。顶栏的导航用「弹性收缩的 flex 行」实现
（`left`/`right` 定边界 + 各项 `flex: 0 1 140px`），设计画布比 1920 窄时等比收窄，
不会压到标题切图上。

右上角用户区（头像 + 用户名 + 下拉箭头）是**点击展开**的账户菜单，
菜单项由 `@logout` 抛给页面处理：

```vue
<screen-header :user-name="user.name" @logout="handleLogout" />
```

```js
import { logoutAndGoToLogin } from '@/utils/session'

handleLogout () {
  // 清中台 + jeecg 两套登录态并回到登录页；与「登录态失效」是同一条出口
  logoutAndGoToLogin()
}
```

> 登录态失效（中台或 jeecg 任一令牌过期 / 无效）也走 `logoutAndGoToLogin`，
> 由 `src/utils/request.js` 统一触发，不会只弹提示而把人留在页面上。

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

5. **面板会裁剪浮层**：`ScreenPanel` 有 `overflow: hidden`
   （它原先还有 `backdrop-filter`，那会让面板成为 `position: fixed` 的包含块，
   已因性能原因移除，见 `styles/screen-mixins.less` 里 `.screen-glass` 的实测数据）。
   所以任何「贴着控件弹出的下拉 / 气泡」都必须走 `ScreenPopover`
   （它会把自身节点移动到 `document.body`），直接在面板里写 `position: absolute`
   的下拉一定会被裁掉或错位。
6. **浮层的 z-index 必须高于弹窗**：`ScreenPopover` 默认 `1260`、
   `ScreenModal` 是 `1200`、`ScreenToast` 是 `1300`、
   `ScreenPickStage`（「选取地块」模式的两个按钮）是 `1250`。
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
    登录时会用同一套账号口令同时登录中台与业务后端（jeecg），
    但前端按钮权限清单仍由中台下发，业务后端的角色权限对不上，
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

## 6.1 「选取地块」模式（2026-10 新增，2026-10-16 改口径）

数据管理的两张表单（`LandFormModal` / `FacilityFormModal`）里都有「选取地块」按钮。
点它之后**页面不再隐藏**，视口上方中间（顶栏之下）浮出两个按钮
（取消选择 / 选择完成），点任一个都回到表单 —— 表单**不关**，填到一半的内容都在。

> ★ **为什么不再隐藏页面（2026-10-16 用户实测后改的口径）**
> 上一版是「所有页面全部隐藏、只留下方地图」。这一版把表单弹窗改成了
> **可拖动 + 背景不压暗**（`ScreenModal` 的 `draggable` / `:dimmed="false"`），
> 用户把窗口往旁边一拖，地图本来就随时可点可拖。
> 再整屏藏一遍页面，反而把「填到一半的表单」一起藏了 —— 用户想核对刚填的字段
> 都得先退出选取模式。于是本组件的职责从「盖住一切的地图模式」收缩成
> 「两个按钮 + 一句提示」，两个按钮也从视口最上沿（原 `top: 24px`）下移到
> `top: 150px`：页面露出来之后，贴在 24px 会正好压在顶栏的导航菜单上。

组件拆分与分工（改之前先看清谁负责什么）：

| 文件 | 职责 |
| --- | --- |
| `views/screen/pickSession.js` | 会话状态单例（Vue.observable）：谁发起的、当前是否在选取模式 |
| `views/screen/index.vue` | 唯一渲染 `ScreenPickStage` 的地方；`mapPaused`（选取时**不能**暂停地图渲染） |
| `components/screen/ScreenPickStage.vue` | 两个按钮 + 提示文案 + Esc 监听；`drawTools` 是绘制的**占位实现** |
| `components/screen/ScreenModal.vue` | 表单弹窗本体：`draggable` 拖动 + `:dimmed="false"` 不压暗、不吃鼠标 |

必须记住的点（每一条都对应一个实际踩中的坑）：

1. **地图不重新创建**。本层刻意**无背景**，下面就是 `ScreenStage` 里那个常驻的
   `<s3dm-viewer>`。再 `new Cesium.Viewer` 一份会白烧 GPU 与内存，
   而且两个 canvas 抢同一个 `#home` 容器 id 会直接把地图搞坏。
2. **★ 本层根节点必须 `pointer-events: none`**，只有两个按钮显式 `auto`。
   铺满视口的透明层若写成 `auto` 会把地图彻底挡死，与提示文案自相矛盾。
   **透明 ≠ 不接事件**：命中测试只看盒模型，不看有没有背景色。
   同一个坑在 `ScreenModal` 的遮罩上又出现过一次（只把底色改透明、
   忘了 `pointer-events`，表现是「看得见地图却点不到」）—— 见 §2.4 的 `is-clear`。
3. **★ 会话必须带 token**。同页可能同时开着「新增宗地」与「新增配套」两个常驻弹窗，
   没有 token 校验就会出现「另一个表单的结果被本表单吃掉」，
   以及「后一个的取消把前一个的选取模式关掉」。
4. **Esc 用捕获阶段监听并 `preventDefault`**。弹窗**仍然可见**，焦点很可能就在
   某个输入框上，而 `ScreenModal` 的 Escape 处理挂在它的 dialog 上、
   会先于 window 的冒泡监听触发 —— 不拦的话「按 Esc 取消选择」会连整张表单一起关掉。
5. **不要用 `watch` 去同步 `pickState`**。它是模块 import，没挂在组件实例上，
   `watch: { pickState }` 会对 `vm.pickState` 求值得到 undefined、**不建立依赖**，
   handler 永不触发（踩过：本层永远不出现）。正确做法是用 computed 直接读单例字段。
6. **不要再用「给 body 打标记 + 全局 CSS 藏浮层」那套**（2026-10-16 已删除）。
   它当初的职责是藏掉 `.screen-modal-root`（含发起选取的那张表单）与 `.screen-popover`；
   现在弹窗**必须留着可见**，而下拉浮层是表单自己的控件，藏了用户就没法用下拉。
   历史实现是 `body.is-picking-map`，留在 git 历史里即可，不要再捡回来。

**绘制功能尚未接入**（需求明确「先不做，把方法预留」）。
落点在 `ScreenPickStage.vue` 的 `createDrawTools()`：现在每个方法只提示不干活；
接入时把绘制引擎挂进覆盖层、在 `start()` 激活
（**记得置 `active = true`**，否则 `stop()` 第一行就 return、插件监听永远停不掉）。
★ 抛图形的落点要注意：`drawTools` 是 `createDrawTools(vm)` 返回的**普通对象**，
`this` 指向它自己，**没有 `$emit`** —— 在 `stop()` 里写 `this.$emit('done', geometry)`
会直接抛 `this.$emit is not a function`。正确做法是用闭包里的实例 `vm`：
按钮触发完成时走 `getGeometry()`（`handleDone` 已接好）；插件异步画完时由插件回调
调 `vm.$emit('done', geometry)`，并保证与 `handleDone` 不重复 emit。

**本期 `done` 的 payload 恒为 null，表单收到 null 时保留字段原值**，
所以现在点「选择完成」等价于「关掉选取模式回到表单」，不会有半个坐标被写进模型。

本层的 z-index 是 **1250**（高于 `ScreenModal` 1200、低于 `ScreenPopover` 1260）。
`ScreenToast` 1300 的提示仍在最上面 —— 这是有意的，toast 是本模式唯一的反馈通道
（「尚未绘制地块，本次没有写回任何字段」就靠它），任何「隐藏浮层」的规则里
**绝不能把 `.screen-toast` 也藏掉**，否则用户看到的是静默失败。

> ★ **覆盖层必须 `appendChild` 到 `document.body`**（`ScreenPickStage.vue` 的 `mounted`，
> 做法同 `ScreenModal` / `ScreenPopover`）。它在 `index.vue` 里是写进
> `<screen-stage>` 的**默认插槽**的，真实父节点是 `.screen-stage__canvas` ——
> 那个元素带 `position: absolute; z-index: 1`，**自己就是层叠上下文**。
> 留在里面写 `z-index: 1250` 出不了这个上下文：body 级的 `.screen-modal`(1200)
> 反而画在它上面，而且它还会被画布的 `transform: scale(k)` 一起缩放
> （实测 1920 视口下两个按钮从 168×54 缩成 154×49）。
> 卸载时记得 `removeChild`：`$el` 已不在 Vue 管的父节点下，Vue 不会替你移除。

> **验证脚本必须用真实输入域点击**：`perf/verify-pick-stage.js` 用
> CDP `Input.dispatchMouseEvent` 点两个按钮，并在点之前用 `elementFromPoint`
> 确认按钮中心**没有被遮挡**。用元素的 `b.click()` 会绕过命中测试 ——
> 按钮被盖住时它照样"成功"，断言就变成假绿。

> **改完必须跑浏览器验证**：`node perf/verify-pick-stage.js http://localhost:3001`
> （35 条断言：覆盖层出现、页面全隐藏、按钮居中可点且未被遮挡、
> **屏幕正中命中 canvas**、取消/完成回到原页面）与
> `node perf/verify-pick-esc.js http://localhost:3001`（6 条：Esc 只退选取、不关表单）。
> 上面 3/4/5 三个坑以及"覆盖层没挂 body"**模板编译、less、eslint、verify-sfc
> 一个都查不出来** —— 只在真实浏览器里数元素、做命中测试才发现。
> 看效果图用 `node perf/shot-pick-stage.js`。
>
> ★ **端口别搞错**：本机两个前端同时在跑，**3000 是管理端 admin-client，
> 3001 才是大屏端 stargis-client-web**。指错端口时页面照样打得开，
> 但登录后是另一个应用（标题都不一样），断言会成片失败且原因很难看出来。

> 想要「只预览界面、不加载地图」时用全站已有的 `?map=0`：
> 该模式下「选取地块」按钮是**禁用**的，并给出原因，
> 而不是让用户点进去看到一张空白。

---

## 6.2 数据管理「右侧检索列 + 可拖高列表」（2026-10 新增）

需求：**列表默认只露 3~4 行**（列表是定位工具，主角是地图）、
**检索容器竖排在屏幕右侧并与左侧二级导航左右对称**、
**列表高度可以用鼠标拖动改变**。经营性用地与配套项目两个页签同步。

### 版式（设计画布坐标，纵向值）

```
顶栏下沿 104 ────────────────────────────────────────────
             │              │                    │
   左导航     │   列表(贴底)  │      检索列        │
   left:30    │  left:250    │   right:30        │
   width:200  │  right:250   │   width:200       │
             │  height 可变  │   top:104         │
             │              │   bottom:40       │
底部留白 40 ────────────────────────────────────────────
```

- **对称是字面意义上的对称**：检索列 `right:30; width:200`，
  左导航 `left:30; width:200`，两者 `top/bottom` 也相同 —— 改一边就要改另一边。
- 列表 `left:250; right:250`，与检索列之间留 20px（和左导航侧一致）。
- 列表**贴底**、靠内联 `height` 向上长（`ScreenResizeBar` 在上面）。

### 谁负责什么

| 文件 | 职责 |
| --- | --- |
| `ScreenResizeBar.vue` | 拖动条本体：指针事件 + 键盘 + `input`/`change`，只管「报数值」 |
| `LandEntryPanel.vue` / `FacilityEntryPanel.vue` | 持有 `listHeight`，把高度写进列表容器的内联 `height` |
| `LandSearchForm.vue` / `FacilitySearchForm.vue` | `vertical` prop：窄列里改单列竖排 + 字段 stacked |
| `ScreenDateInput.vue` | 新增 `stacked` prop：窄列里**区间日期改上下两行**（否则每段只剩 ~60px，`2026-10-16` 会被截断） |
| `views/screen/data/index.vue` | `is-split` 类：这两个页签的内容区铺满画布（`pointer-events: none`），其余四个页签保持旧版式 |

### ★ 拖动换算：必须除以画布缩放 k

`ScreenStage` 把整个界面 `transform: scale(k)`（实测 1894×987 视口下 k≈0.9139），
于是**鼠标的 `clientY` 是真实像素，而面板高度是画布像素**：

```js
const delta = (this.startY - event.clientY) / scale   // ← 除以 k，不是直接用
```

不除的后果是列表「跟不上鼠标」：拖 100px 只长 100px（实际应长 100/0.9139≈109px），
偏差在拖动过程中累积，越拖越不准。
`ScreenResizeBar` 用 `parentElement.getBoundingClientRect().height / parentElement.offsetHeight`
现算 k（取父节点是为了量得更准，父节点比 12px 的拖动条高得多），并兜底为 1。

### 其它几个必须知道的点

- **画布宽度是自适应的，不是 1920**：`ScreenStage` 是「高度贴合 1080、
  宽度 = 视口宽 / k」并且铺满不留黑边，所以**横向坐标上限随窗口宽高比变化**
  （1894×987 视口 → 设计画布 **2072.46×1080**，`--stage-w` / `--stage-h` 上写着）。
  写「靠右/居中」时用 `right` 或 `--stage-w`，**不要写死 1920**。
- **模板拿不到模块级常量**：`:min="LIST_MIN_HEIGHT"` 会取到 `undefined`
  （模板只解析**实例上**的属性），组件静默退回自己的默认值 —— 不报错但设置失效。
  要用 `computed` 暴露（两个面板里的 `listMinHeight` / `listMaxHeight` 就是这个原因）。
- **标签列宽是内联样式**：`ScreenField` 用 `rootStyle` 注入 `grid-template-columns`，
  外部 CSS 盖不住（要 `!important`）。窄列里正确的做法是用它的 `stacked` prop，
  所以两个搜索表单是 `:stacked="vertical"` 而不是写 `/deep/` 覆盖。
- **不能用媒体查询做这个降级**：CSS 媒体查询看的是**视口**宽度（大屏是 1920），
  感知不到「容器只有 200px」。所以版式由父组件用 `vertical` prop 显式声明。
- **窄列里区间日期必须竖排**：166px 里两段并排各剩 ~60px，原生日期输入不换行，
  `2026-10-16` 只会被截断（截断在真实像素下看不出来，要换算成设计像素量）。
  所以 `ScreenDateInput` 也加了 `stacked`（上下两行，各拿到整行宽度）。
  这条是**浏览器实测发现的**：几何断言全绿时它仍可能坏，得专门量控件宽度。
- **`pointer-events` 纪律**：铺满画布的容器（`data-screen__body.is-split`、
  `.land-entry` / `.facility-entry`）必须 `none`，只让 `__search` / `__list`
  （和拖动条）为 `auto`。在铺满屏幕的节点上开 `auto` 正是当年「地图点不动」的成因。
- **拖动不发请求、不落存储**：可见行数由高度决定、数据没变，每拖一下都请求后端
  会让拖动过程中疯狂打接口；高度也不写 localStorage（切页签由 `keep-alive` 保住）。

### 验证

```powershell
node perf/verify-data-layout.js http://localhost:3001
```
42 条断言，断言的是**需求本身**而不是「没报错」：列表贴底且只露 3~4 行、
检索列在右侧且与左导航逐项对称、条件是单列竖排、标签在控件上方、
**屏幕正中命中 canvas**（列表变矮的意义所在）、
拖动 100px 后高度 = 100/k（**换算正确**：实测 360→469，未换算会停在 460）、
上下限夹紧（200 / 936）、键盘也能调；另外 4 个页签回归旧版式。
效果图：`node perf/shot-data-layout.js http://localhost:3001`。
（2026-10-16 复跑为 **46 条**，多出的是日期竖排与溢出相关的断言。）

---

## 6.3 新增/编辑弹窗：2 列 + 高度减半 + 可拖动 + 背景不压暗（2026-10-16 新增）

数据管理的两张表单弹窗（`LandFormModal` / `FacilityFormModal`）按用户实测反馈改了形态。
两个表单**必须保持一致** —— 同一套交互，用户在两者之间来回切换不该有两套手感：

| 项 | 改造前 | 现在 | 落在哪 |
| --- | --- | --- | --- |
| 表单列数 | 3 列 | **2 列** | `&__grid` 的 `grid-template-columns` |
| 弹窗宽度 | 1440 / 1280 | **960** | `:width="960"`（缩到 960 后 3 列每列太窄，2 列才是正常比例） |
| 弹窗高度 | `100vh - 100`（实测 892） | **一半**（实测 448.5） | `:body-max-height="'calc(50vh - 150px)'"` |
| 背景 | 压暗 `rgba(1,14,17,.72)` | **不压暗，且不吃鼠标** | `ScreenModal` 的 `:dimmed="false"` |
| 位置 | 居中固定 | **可拖动**（鼠标 + 方向键） | `ScreenModal` 的 `draggable` |

高度怎么来的：改造前 `body-max-height` 是 `calc(100vh - 200px)`，头 46 + 底栏 54 ≈ 100，
所以改造前总高 ≈ `100vh - 100`；取 `calc(50vh - 150px)` 后总高正好是一半。
**正文因此变成滚动**，这是有意的：弹窗矮下去才看得到下面的地图。

几条硬约束（踩过才写下来）：

- **★ 「不压暗」必须同时 `pointer-events: none`**。遮罩是整屏一层，只把 `background`
  改透明的话它照样参与命中测试 —— 用户看得见下面的地图，却拖不动、点不到，
  而且**肉眼完全看不出有东西挡着**。所以这两件事写在同一个类 `&__mask.is-clear` 里，别拆开。
- **★ 三层 `pointer-events` 要分开声明**：`.screen-modal`（铺满视口）是 `none`，
  `.screen-modal__mask` 默认 `auto`（模态弹窗仍要挡住背景点击）、
  `.screen-modal__dialog` 是 `auto`。父级 `none` + 子级 `auto` 是合法的，
  子元素能重新打开自己的命中测试。
- **★ 拖动位移用 `left/top`，不要用 `transform`**：`.screen-modal__dialog` 的进出场动画
  就是 `transform: translateY(10px)`，行内 `transform` 会把它整个盖掉（弹窗不再淡入上浮）。
- **★ 拖动位移是真实像素，不要除以画布缩放 `k`**：弹窗挂在 `document.body` 上，
  不在 `ScreenStage` 的缩放画布内 —— 与 `ScreenResizeBar` 正好相反（那边**必须**除 `k`）。
  照搬那套换算会让弹窗跟不上鼠标。
- **★ 拖动必须夹紧**：横向左右各留 80px、纵向让标题栏（唯一的把手）完整留在视口内。
  不夹的话「随意拖动」在触摸板上轻轻一滑就变成「弹窗消失、找不回来」。
  夹紧要按**拖动起点记录的可视矩形**算，不能假设弹窗居中（它可能已经被拖到别处）。
- **监听挂 `window` 而不是标题栏**：指针划出标题栏时元素上的 `pointermove` 会断，
  表现是「拖快了弹窗跟不上」。配合 `setPointerCapture` 双保险，
  并在 `beforeDestroy` 里收尾（否则 `window` 上留监听、`body` 上留 `is-dragging-modal`）。
- **标题栏可聚焦 + 方向键移动**（`Shift` 走大步）：拖动不能只有鼠标。
  但要把它从「打开时自动聚焦」里**排除**（`firstFocusable` 过滤），
  否则进表单时光标落在标题栏上，用户得先多按一次 Tab 才能开始填。
- **`dimmed` / `draggable` 默认关闭**：既有弹窗（档案、收发文…）的模态行为与居中定位
  保持不变，只有这两个表单显式打开。

### 验证

```powershell
node perf/verify-modal.js http://localhost:3001
```
58 条断言，两个表单各跑一遍：2 列、高度 = 基准的一半、正文确实在滚动、宽度 960、
遮罩底色全透明**且 `pointer-events: none`**、**打开前后背景那块画面的 PNG 逐字节相同**
（真的没压暗）、弹窗外的点命中 `canvas`（透明层没吃事件）、拖标题栏位移与鼠标一致、
方向键移动 48px、往屏幕外拖会被夹住、**选取模式下 `.is-pick-hidden` 数量为 0**
且发起选取的弹窗仍 `visibility: visible`、两个按钮在顶栏之下、Esc 只退选取不关表单、
「选择完成」回到表单、正文无横向溢出、只读标志位格子没被裁掉。
效果图：`node perf/shot-modal.js http://localhost:3001`。

---

## 7. 目录结构

```
src/components/screen/
├─ index.js                    # 统一出口 + Vue 插件（新增组件务必在这里登记，否则导入到 undefined）
├─ utils.js                    # 纯函数工具（格式化 / 动效 / 唯一 id）
├─ toast.js                    # 命令式消息提示服务（$screenToast / toast）
│
├─ ScreenStage.vue             # 自适应舞台（定尺设计画布 + 真实像素地图层 + 铺满不留黑边）
├─ ScreenPanel.vue             # 面板容器
├─ ScreenHeader.vue            # 顶栏（高保真切图 + 7 项导航 + 时钟）
├─ ScreenMapStage.vue          # 地图舞台
├─ ScreenModal.vue             # 弹窗（挂 body + 焦点陷阱 + 滚动锁；可选不压暗 + 可拖动）
├─ ScreenPickStage.vue         # 「选取地块」模式（不再隐藏页面 + 取消选择/选择完成）
├─ ScreenPopover.vue           # 浮层容器（挂 body，下拉 / 气泡的公共基座）
├─ ScreenPopconfirm.vue        # 危险操作二次确认
├─ ScreenResizeBar.vue         # 横向拖动条（拖动/键盘改上一个兄弟容器的高度，自动换算画布缩放）
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
| 地图大屏首页 | `/`、`/screen` | `src/views/screen/index.vue` |
| 首页定尺面板 | — | `src/views/screen/home/`：`HomeLeftPanel`（出让地块情况统计）、`HomeLayerTree`（地图管理图层面板）、`HomeAttrPanel`（属性表）、`HomePlotModal`（地块预警信息弹窗） |
| 档案管理（大屏子页面） | 顶栏「档案管理」，深链 `/screen/archive` | `src/views/screen/archive/index.vue` |
| 道路交付及养护协议移交事项（方案 2.3.2 第 6 项） | 档案模块内的页签，不占一级导航 | 深链 `/screen/archive?tab=handover`；`src/views/screen/archive/modules/handover/` |
| 道路设施验收及移交资料台账（方案 2.3.2 第 7 项） | 同上 | 深链 `/screen/archive?tab=ledger`；`src/views/screen/archive/modules/ledger/` |
| 竣工验收项目历史工程资料数字化档案（方案 2.3.2 第 8 项） | 同上 | 深链 `/screen/archive?tab=completion`；`src/views/screen/archive/modules/completion/` |
| 收发文 | 不占一级导航，是档案模块内的页签 | 深链 `/screen/archive?tab=doc` |

> 档案管理下的页签顺序：档案维护 → 档案查询 → 档案统计 → 道路交付养护移交 → 道路验收移交台账
> → 竣工验收历史档案 → 收发文管理 → 档案类别管理。第 6/7/8 项插在统计与收发文之间，
> 与方案 2.3.2 的条目序号一致。页签 key 的**白名单在 `views/screen/index.vue` 的
> `ARCHIVE_TABS`**、页签与组件的映射在 `views/screen/archive/index.vue` 的 `tabs` / `PANELS`，
> 两处必须同步改（漏改白名单的表现是「深链进不去、静默落回默认页签」）。
>
> 这三个模块的接口层在 `src/api/land/{ledger,handover,completion}.js`，
> 与 `admin-client` 的同名文件一一对应（同一套 Java 后端，改接口必须两边同改）。
> 它们只做展示与增删改查，**没有打印功能**（大屏既有模块都没有）；补录走 Excel 导入。

**首页数据来源（`views/screen/` 三个文件的分工，改动时别搞混）：**

| 文件 | 职责 |
| --- | --- |
| `config.js` | 静态常量：7 项顶栏导航、系统标题、属性表页签、预警字段契约 |
| `mock.js` | **兜底演示数据**（高保真设计稿数字）。只在接口不可用/返回空时生效 |
| `index.vue` | `loadDashboard()` 并发拉取 `LandDashboardVO` + `SupportingFacilitiesDashboardVO`，成功后整段覆盖兜底数据并下发给 `home/` 的面板组件 |

接口来自 **Java 业务后端**（`@/api/land/landData`，走 `window._CONFIG.VUE_DATA_JAVA_URL`），
不是中台。已知缺口（前端已按「宁可留白也不串口径」处理，等后端补齐）：

1. 宗地 `ranks` 只有**行政区划维度**，对应设计稿的市级子表；
   街镇维度的区级子表后端暂无 → 该子表显示「暂无数据」。
2. 配套 `ranks` 只有区县汇总，同样没有街镇维度。
3. 「地块预警信息」弹窗的明细后端暂未提供，沿用兜底数据。

高保真切图资源：`src/assets/screen-blue/`（来源为本仓 `docs/高保真/.../images/首页/`，
复制时统一改为 ASCII 文件名；`index.js` 是唯一映射入口，组件里只引用 `hf.xxx`）。

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
  javaAccessToken()              当前 jeecg 令牌（JEECG_ACCESS_TOKEN）
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
2. **令牌要靠请求头传，而且必须是 jeecg 自己的令牌**。本项目的 `request.js` 请求拦截器是被注释掉的
   （不像 admin-client 那样自动加 `X-Access-Token`），所以 `manageJava.js`
   显式设置 `X-Access-Token`；同时按 admin-client 的做法带上
   `X-Sign` / `X-TIMESTAMP`（`X-TIMESTAMP` 用毫秒，与后端 `SignAuthInterceptor` 兼容）。
   原生上传（XHR）与原生下载（`window.open`）绕过 axios，因此分别手动带
   `X-Access-Token` 头和 `?token=` 查询参数。

   > **两套令牌**：中台令牌存在 `ACCESS_TOKEN`（`Access-Token`，走 query/body 的
   > `access_token`），jeecg 令牌存在 `JEECG_ACCESS_TOKEN`（`Jeecg-Access-Token`，
   > 走请求头 `X-Access-Token`），由登录时 `store/modules/user.js` 的 `JeecgLogin`
   > 动作写入。**两者绝不能互相覆盖**：`manageJava.js`、档案上传/预览一律取
   > `JEECG_ACCESS_TOKEN`，`@/api/manage`、`utils/request.js` 一律取 `ACCESS_TOKEN`。
   >
   > jeecg **没有** `/sys/refreshToken` 之类的刷新接口，它的“无感刷新”是后端滑动续期
   > （`ShiroRealm.jwtTokenRefresh`：登录写 Redis、有效期 2 小时，JWT 自身 1 小时过期后
   > 只要 Redis 里还有 key 就重新签发并重置 2 小时），前端只要每次请求都带同一个
   > `X-Access-Token` 即可。令牌最终失效时后端返回 HTTP 401 + “Token失效”，
   > `utils/request.js` 会按“业务后端登录已过期”提示，并**同时清掉中台与 jeecg 两套
   > 登录态、跳回登录页**（详见 `src/utils/session.js`）——中台侧令牌失效同理，
   > 任一套不可用都不再只提示而把人留在页面上。

### 业务接口不再做前端鉴权

业务后端（Java 端）的接口目前**已放开**，前端也不做按钮级鉴权：
档案模块的所有操作按钮都直接渲染，没有 `v-if` 权限判断，也没有 `v-has`。

原来是「中台一套用户/角色、业务后端另一套」导致登录身份对不上；
现在登录时会用同一套账号口令再登录一次业务后端（jeecg），
`X-Access-Token` 是业务后端真实有效的令牌，但**授权**仍按下一条处理：

原因是本项目有**两套互相独立的角色体系**（中台一套、业务后端一套），
虽然用户/机构/角色已经同步（业务后端侧能看到与中台一致的用户和部门），
但前端按钮级权限清单仍由中台下发，业务后端的角色权限对不上时
`v-has` 会把元素从 DOM 删掉，只会导致「功能写了但按钮不出现」。详见第 6 节第 13 条。

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

