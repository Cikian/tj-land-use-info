# AGENTS.md —— 开工前必读

> **所有 agent 在改任何代码之前，先读完这一页。**
> 这里只放「不知道就会踩坑、且代价很高」的信息；细节指向 `docs/` 下的专项文档。
>
> 最近更新：2026-10-09　维护人：见 git log　**改错了会连带影响别人**，请连同「变更记录」一起更新。

---

## 0. 30 秒认识这个项目

**天津市经营性用地市政基础设施配套动态监管平台**——把旧系统（Nutz）的「经营性用地 + 配套项目 + 29 环节进度」
迁到 JEECG-Boot，并新增档案、收发文、提级论证、台账、移交、统计预警等模块。

- 旧系统（**只读基线，不要改**）：`code/old/3DPlanWeb/wk-app/wk-web`（Nutz + NutzDao）
- 新系统：`code/new/`（请看下一节「目录结构」——**仓库根不是工作区根**）
- 两个前端：`admin-client`（管理端，浅色）+ `stargis-client-web`（三维大屏端，暗色）
- 业务需求权威文档：`docs/升级改造工作内容清单.md`（239 KB，方案原文）
- **新旧差异**：`docs/参考资料/新旧系统差异清单.md` ← 改业务逻辑前必读，里面标了哪些差异是**有意修正**，改回去就是制造 bug

---

## 1. ⚠️ 先搞清楚「哪个目录是什么」

**这是最容易浪费半小时的地方：仓库根不是工作区根。**

```
E:\Project\tj-land-use-info\                  ← 工作区根（不是 git 仓库！）
├── docs/                                     ← ★ 文档都在这，但它在 git 仓库之外，push 不上去
├── code/
│   ├── old/                                  ← 旧系统源码（只读参考）
│   └── new/                                  ← ★ git 仓库根（.git 在这里）
│       ├── AGENTS.md                          ← 本文件
│       ├── server/                            ← ★ 后端源码根（"后端根目录"就是这里）
│       │   ├── .env / .env.example            ← 环境变量（.env 被 gitignore，需自行创建）
│       │   ├── scripts/run-with-env.ps1
│       │   ├── pom.xml                        ← 父 POM
│       │   ├── jeecg-boot-base-core/          ← 公共内核（含 .env 加载器）
│       │   ├── jeecg-module-land/             ← ★ 本项目的业务模块（主要工作量在这）
│       │   ├── jeecg-module-system/           ← 系统模块 + 启动模块
│       │   └── jeecg-server-cloud/            ← jeecg 云版模板（本项目未使用，别改）
│       ├── admin-client/                      ← 管理端前端
│       └── stargis-client-web/                ← 三维大屏前端
└── prototype/ / 局-经营用地项目-给UI设计/ …     ← 设计稿等
```

**记住两件事**：
1. `git` 命令要在 `code/new/` 下执行（或 `git -C code/new …`）。
2. **`docs/` 不在 git 仓库内**——在那里写的文档不会被提交，同事拉不到。
   如果某个文档需要随代码走，放进 `code/new/docs/`。

---

## 2. 目录与文件约定（新增文件请照做）

### 2.1 后端

| 位置 | 放什么 |
|---|---|
| `jeecg-module-land/src/main/java/org/jeecg/modules/land/<域>/` | 业务代码，按域分包 |
| `<域>/controller` `<域>/service` `<域>/service/impl` `<域>/entity` `<域>/mapper` `<域>/dto` `<域>/vo` `<域>/support` `<域>/enums` | 标准分层 |
| `…/mapper/xml/*.xml` | MyBatis XML（**必须放 `xml` 子目录**，父 POM 的 `mapper-locations` 只扫这个模式） |
| `src/main/resources/sql/<域>/NN_xxx.sql` | 建表/字典/菜单/权限脚本，**按域分子目录**，`NN_` 前缀表顺序 |
| `src/test/java/org/jeecg/modules/land/` | 集成测试（见 §4） |

已实现的业务域：`data`（数据管理，61 类）、`archive`（档案+收发文+台账+移交+竣工档案，87 类）、
`escalation`（提级论证，28 类）、`stat.home`（首页看板：出让地块情况统计 / 待落实配套统计 /
市区两级项目预警 + 地块预警信息 + 预警下钻，见 `docs/模块实现说明/首页看板-实现说明.md`）、
`stat.query` + `stat.export`（查询统计：经营性用地综合查询 /
用地配套项目查询 / 高级查询 / Excel 导出，见 `docs/模块实现说明/查询统计-实现说明.md`）、
`stat.warning`（分级预警：逾期天数 → 蓝/黄/橙/红等级 + 预警值 + 决策意见 + 预警记录，
环节/阶段/总体三种口径，接口 `/land/stat/warning/*`，入口 = 大屏端「查询统计 → 预警信息查询」
（只加页签、不占菜单）+ 管理端「统计预警 → 预警等级维护 / 决策意见维护」，
见 `docs/模块实现说明/分级预警-实现说明.md`）。
**只有 `package-info.java` 的域 = 尚未实现**：`stat` 的其余子域、`analysis`、`map`、`common`、`config`。

### 2.2 前端

| 位置 | 放什么 |
|---|---|
| `src/api/land/*.js` | 一个业务域一个文件；顶部写注释说明对应后端 Controller |
| `src/views/land/data/`（管理端）/ `src/views/screen/data/`（大屏端） | 页面 |
| `…/modules/` | 页面拆出的子组件（表格/表单弹窗/搜索栏） |

### 2.3 注释与文档语言

**全部用中文**。注释要写**为什么**，不要复述代码。
本仓库的注释密度偏高是**有意的**——大量注释在记录「旧系统是什么行为、为什么这里不照抄」。

### 2.4 名称/编号归一：中文括号 + 连字符 + 去空白 + 编号补「号」

**口径**（2026-10-10 定括号，2026-10-20 扩到全部字符）：凡是**用户看到或录入的名称类内容**
都按同一套规则归一，别让「看起来一样、其实是不同字符」的值各建一条记录。

| # | 规则 | 例 |
|---|---|---|
| 1 | 半角 `(` `)` → 中文 `（` `）` | `津西浯(挂)2025-07` → `津西浯（挂）2025-07` |
| 2 | 连字符族 `_ — – ― ‐ ‑ ‒ − － ﹣ ～ 〜` → 半角 `-` | `义安路（永顺道—永尚道）` → `义安路（永顺道-永尚道）` |
| 3 | 去掉**所有**空白与不可见字符（空格/制表/换行/全角空格/NBSP/零宽） | `东润路\n道路工程` → `东润路道路工程` |
| 4 | 编号末尾的 `【城更】`/`【城市更新】` 等 `【…】` 标记**摘下来写进备注** | `津南（挂）2025-08【城市更新】` → 编号 `津南（挂）2025-08号`，备注 `【城市更新】` |
| 5 | 编号**一律以「号」结尾** | `津北辰（挂）2018-015` → `津北辰（挂）2018-015号` |

适用：宗地编号、地块名称、受让人、楼盘名称、配套项目名称、建设单位、
材料类型名、字典项、界面文案。

**为什么这不是洁癖**：半角 `()` 与中文 `（）`、`-` 与 `—` 是**不同的字符**，
看起来却几乎一样。不统一就会出现「同一宗地两条记录」：

- 判重失效 → 同一宗地/配套被录入两次
- 批量导入的「宗地必须已存在」校验误报「宗地不存在」
- 附件与环节挂在两个 id 上，统计翻倍
- **编号是 `t_supporting_facilities.crzdbh = t_land.crzdbh` 的关联键**，
  带不带末尾「号」直接决定配套挂不挂得上宗地（首页「待落实配套」因此少算）

**实测（2026-10-20 清洗前）**：847 条宗地编号里 819 条缺末尾「号」、5 条带 `【…】` 标记；
1433 条配套里 1366 条缺「号」、4 条带标记；**58 条配套挂不上宗地**
（47 条只差一个「号」、11 条宗地确实不存在）；12 对宗地、56 条配套是同一项目录了两遍。
已由 `sql/data/12_normalize_codes_and_names.sql` 一次性刷完（幂等，带核对查询）。

**怎么做**：
- 写入侧统一走 `DataSupport`：`normalizeText`（字符归一）、`normalizeName(v, max)`
  （归一 + 截断）、`normalizeCode(v)`（归一 + 摘标记 + 补「号」，返回 `CodeInfo{code, markers}`）、
  `appendRemark(remark, markers)`（把标记并进备注，幂等）。
- **顺序不能反**：`normalizeCode` → 判重 → 落库。先判重再归一 = 两种写法各建一条。
- 覆盖**全部五个**写入口（少一个就会漏）：
  1. 单条录入 `LandAdminServiceImpl` / `FacilityAdminServiceImpl`
  2. 批量导入 `LandImportServiceImpl` / `FacilityImportServiceImpl` 的 `applyValue`
  3. 旧接口 `/land/data/land/add|edit`、`/land/data/facility/add|edit`
     （`LandServiceImpl` / `FacilityServiceImpl`，**容易漏**，漏了从这里进来的编号仍缺「号」）
  4. 历史数据：上面那个 SQL 脚本
  5. 前端不重复做归一——**后端是唯一收口**，前端只做提示（见下）
- **补「号」与去重的先后顺序是被唯一索引逼出来的**：`t_land` 上有
  `uk_land_crzdbh (crzdbh, del_flag)`。若先补「号」，`X` 与已存在的 `X号` 会撞索引；
  所以脚本里必须**先去重（软删不带号的那条）再补号**。

**旧系统本来就是中文括号**：旧存储目录名如 `津北辰仓（挂）2018-015`、
`义安路（永顺道-永尚道）` 全用中文括号，所以这是**向旧口径对齐**，不是新规则。

**怎么「限制」用户乱输**（用户问过）：不要在前端做校验拦截（拦不住导入、拦不住旧接口），
而是在后端每个写入口做**归一而不是报错**——用户照自己的习惯输，系统存成规范值，
体验上无感，数据上干净。前端只在表单里加一行提示文案即可。

---

## 3. 🔴 高危陷阱（每条都真踩过）

### 3.1 ⚠️ 只构建单个子模块时，上游模块会用 `.m2` 里的旧 jar（不是源码）

**这不是配置错误，是 Maven 的基本工作方式**，但它是本项目最容易浪费半小时的坑。

**为什么**：`-pl jeecg-module-land` 只把**一个**模块放进本次 reactor；
`jeecg-boot-base-core` 于是被当成**外部依赖**，从本地仓库 `.m2` 取 jar。
你可以自己看 reactor 里有什么：

```powershell
cd code/new/server
# 只有 1 个模块（base-core 不在里面 → 用 .m2 的 jar）
mvn -o -pl jeecg-module-land validate
# 有 3 个模块：jeecg-boot-parent → jeecg-boot-base-core → jeecg-module-land（用源码构建）
mvn -o -pl jeecg-module-land -am validate
```

雪上加霜的是版本号是 **`3.4.3`（release，不是 `-SNAPSHOT`）**：
Maven 把 release 版本当**不可变发布件**，一旦 `.m2` 里有就永远不检查更新，
也不会「过期后自动重取」。

**正确做法（二选一，推荐 ①）**：

```powershell
# ① 加 -am（also make）：把上游模块一起放进 reactor，用工作区源码构建
mvn -o -pl jeecg-module-land -am test -Pit

# ② 或先把改动 install 到 .m2，之后再单独构建子模块
mvn -o -pl jeecg-boot-base-core install -DskipTests
```

> ★ 只从**根目录**跑 `mvn test` / `mvn package`（不带 `-pl`）时，
> 所有模块天然都在 reactor 里，**不会**有这个坑 —— 所以它只在
> 「用 `-pl` 单独构建某个子模块」时才出现。

**症状**（改了 `jeecg-boot-base-core` 但没让源码进 reactor 时）：
`ClassNotFoundException`，或「`.env` 明明放对了却报
`Could not resolve placeholder 'TJ_DB_HOST'`」，或「改的代码像没生效」。
本项目的 `.env` 加载器就在 base-core 里，所以这个坑特别容易被撞上。

**怎么确认自己踩了**：看构建日志里有没有 `Building jeecg-boot-base-core`。
没有就是用了 `.m2` 的 jar。

### 3.2 两个 MySQL 实例，`application-dev.yml` 指向**远程**那个

| 实例 | 库 | 说明 |
|---|---|---|
| **远程** `49.232.252.56:3306` | `tj-jyxyd`、`tj-jyxyd-zk` | ★ **应用和集成测试都用这个** |
| 本机 `localhost:3306` | `tj-jyxyd` | 旧副本，**缺已改名的表**（见下） |

**建表/改表必须打远程库**，否则应用报「表不存在」。

⚠️ 已知环境差异：`t_supporting_facilities` 这个表在**远程库已存在**，**本机库没有**
（配套数据还在旧表名 `xj_kjkfb_supporting_facilities`）。见差异清单 D-008。

**数据管理相关表**：`t_land`、`t_supporting_facilities`、`t_facility_process`、
`t_process_configuration`、`t_land_attachment`、`t_data_change_log`、`t_non_working_day`、
`t_land_import_log`、`t_facility_import_log`。

### 3.3 软删列名两张表**不一样**（写错就是「查不到」或「删不掉」）

| 表 | 软删列 | 类型 |
|---|---|---|
| `t_land` | `del_flag` | tinyint，有 `@TableLogic` |
| `t_supporting_facilities` | **`delFlag`** | varchar `'0'`/`'1'`，**没有** `@TableLogic` |

同理配套表的审计列是**驼峰**（`createTime` / `createAccount`），与其它表的下划线风格不同——
因为列结构沿用旧系统配套表。**不要「顺手统一」列名**：改列名要动实体、Mapper、导入模板，
以及中心手里在用的 Excel 表头。

### 3.4 `@TableLogic` 不作用于手写 SQL

回收站要查 `del_flag = 1` 的行，而 `@TableLogic` 会让所有实体查询自动加 `del_flag = 0`
→ **永远查不到**。所以 `DataRecycleMapper` 全部是手写注解 SQL。
写手写 SQL 时**必须自己带软删条件**。

### 3.5 集成测试必须带 `-Pit`（否则「假绿」）

父 POM 在 surefire 里硬编码了 `<skipTests>true</skipTests>`，
**`-DskipTests=false` 盖不住**（插件配置优先）。只有 `-Pit` profile 会打开：

```powershell
cd code/new/server
mvn -pl jeecg-module-land -am test -Pit              # 全量
mvn -pl jeecg-module-land -am test -Pit -Dtest=数据管理类名   # 单类（-Dtest 支持子串）
```

不带 `-Pit` 会看到 `Tests run: 0 ... BUILD SUCCESS` —— **那不是通过，是没跑**。
`-am` 的作用见 §3.1（让上游模块用源码构建）。

### 3.6 大屏端（stargis-client-web）的四条硬规定

1. **不要加 `v-has`**。大屏的 `GetPermissionList` 从不下发按钮权限清单，
   指令会在清单为空时把元素**从 DOM 删掉** → 表现是「按钮全部消失」。
   真正的边界是「菜单能不能打开」。见 `src/components/screen/README.md`。
2. **必须走 `@/api/manageJava`，不要用 `@/api/manage`**。
   本工程有三个后端地址（见 `public/static/config.js`）：中台 `127.0.0.1:4548`（`VUE_APP_API_BASE_URL`）、
   Java 业务后端 `127.0.0.1:9802/api`（`VUE_BUSINESS_JAVA_URL`）、
   JNI 分析服务 `127.0.0.1:38190`（`VUE_DATA_JAVA_URL`，混淆插件 `stargis-function` 写死读它）。
   `domianURL` 指向**中台**，而 `@/api/manage` 走 `domianURL` → 打到 4548 会 404。
   **判据：只要是 jeecg（Spring Boot）提供的接口，就用 `manageJava`。**
   ⚠ **2026-10 起 `VUE_DATA_JAVA_URL` 不再指业务后端**（改指 38190 JNI 服务）：
   业务请求一律走 `javaBaseUrl()`（`VUE_BUSINESS_JAVA_URL`，旧配置才兜底 `VUE_DATA_JAVA_URL`），
   **不要**在业务代码里显式传 `window._CONFIG.VUE_DATA_JAVA_URL` 当 baseURL。
   同理附件静态地址要用 `getJavaFileAccessHttpUrl()`，不能用 `getFileAccessHttpUrl()`。
3. **Vue 2**：用 `/deep/`，**不要用 `:deep()`**；不要用 `v-bind()` in CSS。
4. **`pointer-events` 两个方向都要管**：
   需要交互的元素在三维舞台上要有 `pointer-events: auto`（全局类 `.stage-hit`）；
   **铺满屏幕的容器层（遮罩、覆盖层、铺满画布的页面容器）必须是 `none`**。
   **透明 ≠ 不接事件**——命中测试只看盒模型，不看有没有背景色。
   在铺满屏幕的节点上开 `auto` 就是「看得见地图却点不到、拖不动」的成因
   （本项目已经踩过三次：选取覆盖层、数据管理铺满画布的容器、弹窗遮罩改透明时）。
   子元素可以用显式 `auto` 把自己的命中测试重新打开，所以「父 `none` + 子 `auto`」是标准写法。

### 3.7 前端 lint 必须加 `--no-ignore`

两个前端的 `.eslintignore` 内容都是 `/src`，不加参数等于**什么都不检查**：

```powershell
cd code/new/admin-client           # 或 stargis-client-web
npx --no-install eslint --no-ignore --ext .js,.vue <文件或目录>
```

另外两个前端的自检脚本（**优先用它**，它同时校验模板/less 编译）：
```powershell
node scripts/verify-sfc.js
```

### 3.8 `.env` 机制（口令不进仓库）

- yml 里全是 `${TJ_XXX}` 占位符，真实值在 **`code/new/server/.env`**（已 gitignore）。
- 新环境：`Copy-Item .env.example .env` 再填值。
- **这些键没有默认值，缺一个就起不来**：
  `TJ_DB_HOST` `TJ_DB_PORT` `TJ_DB_NAME` `TJ_DB_USER` `TJ_DB_PASSWORD`
  （2026-11 起 `TJ_REDIS_DB`、`TJ_PG_PASSWORD` 也没有默认值，同样必须填）。
- 加载方式：`DotEnvEnvironmentPostProcessor`（在 `jeecg-boot-base-core`）
  从 JVM 工作目录**向上找 4 级**找 `.env`。IDE / `java -jar` / `mvn` 三种方式都能用。
  所以**放仓库根或 `server/` 都能被找到**，但 `server/` 是约定位置。
- 排查：启动日志里应有 `[jeecg] 已加载环境变量文件：<路径>（N 项）`。
  **没有这行** → 基本是 §3.1：base-core 的源码没进 reactor（或没 install 到 `.m2`）。
- 详见 `code/new/server/环境变量与敏感信息.md`。

### 3.9 数据库/文档改动很容易被「顺手统一」毁掉

- **不要改列名**（见 §3.3）。
- **不要改 `sys_permission.id` 的长度**：`varchar(32)`，本项目模块 id 是 28 字符
  （前缀 `7a1f0d2c4e5b4d8a9c3f6b1e`）。曾经用 34 字符导致 `ERROR 1406 Data too long`。
- SQL 脚本要**幂等**（可重复执行），并带核对查询。

### 3.10 合并远端后必须先跑两个 `verify-sfc`：它会抓到「文件被截断」

**症状**：合并零冲突、`git status` 干净，但某个文件在尾部被截断——不是合并造成的，
是**提交时文件就没写完**（文件结尾是半个标识符）。2026-11-05 合并 `f6c3b6e` 一次撞到 5 个：

| 文件 | 被截断成 | 后果 |
|---|---|---|
| `admin-client/scripts/verify-sfc.js` | `failed++` 之后直接结束 | 脚本本身语法错误，自检根本跑不起来 |
| `admin-client/…/data/FacilityEntry.vue` | `&__met` | `<style>` 尾部规则与 `</style>` 丢失 |
| `admin-client/src/api/land/facilityAdmin.js` | `deleteProcess` + `}` | 默认导出少 3 个名字，GeoJSON 导入两个函数整节丢失 → 弹窗一用就 `TypeError` |
| `stargis-client-web/…/screen/ScreenPopover.vue` | `<` | `</style>` 被截成半个标签 |
| `stargis-client-web/…/maps/S3dmViewer.vue` | `.cesium` | `<style>` 尾部两条规则与 `</style>` 丢失 |

**为什么合并会「原样保留」坏文件**：这类截断落在「只有远端改过」的行上，本地改动在别处，
git 直接取远端那一侧 → 冲突为零，坏内容进主干。**冲突为零 ≠ 合并正确。**

**怎么排查（三分钟）**：
1. 两个前端各跑一次 `node scripts/verify-sfc.js`（模板 / less 编译不过会当场报）；
2. `git diff <合并基点> <远端分支> | Select-String 'No newline at end of file'`，
   逐个看这几个文件**结尾是不是半个标识符**（`.cesium` / `<` / `&__met` 一眼可辨）；
3. `.js` 尾部被吃掉时看**孤儿线索**：新加的 import 从未被使用、新加的 url 常量无人消费、
   别处 import 的具名导出在目标文件里 0 处定义——三者出现任意一个，基本就是截断吞了尾部函数。

**怎么修**：按「远端新增内容 + 共同祖先里被截断掉的那段」补齐（`git show <基点>:<文件>` 取尾），
**不要**整文件回退到祖先（那会抹掉同事的功能），也**不要**只补冲突标记里看得见的部分。
补完把新文件登记进 `scripts/verify-sfc.js` 的硬编码清单——不登记就等于「自检绿灯但其实没检查过」。

### 3.11 后端启动即 `ArrayIndexOutOfBoundsException: -1`：snakeyaml 1.29 的 CRLF 解析缺陷

**症状**：`Application run failed`，栈顶是

```
java.lang.ArrayIndexOutOfBoundsException: Index -1 out of bounds for length N
    at org.yaml.snakeyaml.reader.StreamReader.peek(StreamReader.java:136)
    at org.yaml.snakeyaml.scanner.ScannerImpl.scanToNextToken(ScannerImpl.java:1222)
    at ... ParserImpl$ParseBlockMappingKey.produce ...
    at ... CommentEventsCollector$1.peek ...
```

**先排除两条误判**：① 不是 YAML 写错——那会报 `ScannerException` / `ParserException`
并指出**行列号**；② 不是编码/BOM——`application-dev.yml` 是合法 UTF-8、无 BOM、无控制字符
（严查一遍：`[Text.UTF8Encoding($false,$true)` 严格解码 + 扫 NUL/控制字符，全是干净的）。

**根因**：Spring Boot 2.6.6 默认带 **snakeyaml 1.29**，该版本的 `StreamReader.peek`
在读缓冲边界上碰到 **CRLF** 会取到负下标。**与内容位置强相关**，不是内容错：
同一份 `application-dev.yml` 在最前面加一个空行就解析通过，删几行注释又可能重新踩中。
实测同一文件：snakeyaml **1.25 通过 / 1.29 报错 / 1.30、2.4、2.6 通过**，
用 Spring Boot 自己的 `YamlPropertySourceLoader` 也能复现。
本项目工作副本是 CRLF（`core.autocrlf=true`），所以「加/删几行注释把内容长度顶到边界」
就可能突然起不来——2026-11-12 就是这么踩的（改了 `TJ_*` 占位符注释之后启动失败）。

**修法**：已在 `server/pom.xml` 的 `<properties>` 覆盖
`<snakeyaml.version>1.30</snakeyaml.version>`（1.30 是 Spring Boot 2.7 的默认版本，API 兼容）。
改完**必须在 IDE 里 Reload Maven Project**（或重新 `mvn` 编译），否则仍是旧的 1.29。
核对一条命令：

```powershell
cd code/new/server
mvn -pl jeecg-module-system/jeecg-system-start dependency:tree "-Dincludes=org.yaml:snakeyaml"
# 期望：org.yaml:snakeyaml:jar:1.30:compile
```

**不要**用「加个空行 / 删几条注释」把位置挪开当修法：那只是把地雷推到下一次改 YAML，
且与内容本身无关，排查时极易误导。

**三分钟定性办法**（比反复重启应用快）：写 5 行 Java main，`new Yaml().load(file)`
逐个试 `application*.yml`，1.29 与 1.30 各跑一次——1.29 挂、1.30 过就是本条；
顺带说明：`jeecg-server-cloud/**` 里 5 个 `application.yml` 本来就解析不通
（`@config.server-addr@` 是 Maven 过滤占位符，只在打包后展开），属**另一个原因**，与本条无关。

### 3.12 大屏三块面板「拖宽 / 拖高 + 折叠」的八个硬点

首页 / 地图管理 / 配套分析管理的左右面板可拖动改宽、下方面板可拖动改高、三块都能折叠；
数据管理的左二级导航 / 右检索列也可拖宽、可折叠（底部列表沿用原有拖高），
左右与下方的**氛围蒙版也跟着面板一起伸缩 / 折叠**。
共用件在 `stargis-client-web/src/components/screen/`（`panelLayout.js` / `ScreenResizeBar` /
`ScreenSideRail` / `ScreenBottomDock`，契约见该目录 README §6.4）。改这块前先看这八条，
每一条都对应一次实测返工：

1. **拖动位移必须除以画布缩放 k，而且按轴向量**：大屏是 `transform: scale(k)` 的定尺画布，
   `clientX/Y` 是真实像素、面板尺寸是画布像素。纵向改宽要量**宽度**的比值、横向改高量**高度**的
   （`ScreenResizeBar.measureScale` 已按 `orientation` 切换）。少除或不换轴，表现是
   「拖 100px 只长 91px」且误差全程累积。
2. **铺满画布的包装层必须 `pointer-events: none`**，拖动条 / 面板各自 `.stage-hit`。
   在 `inset: 0` 的节点上开 `auto` 就是「看得见地图却点不到、拖不动」（§3.6 第 4 条），
   而折叠包装层正好是这种节点。
3. **拖动期间必须关掉高度过渡**：`ScreenBottomDock` / `HomeAttrPanel` 平时有 320ms 的
   height/left/right 过渡（折叠与侧栏联动要用），拖动时靠 `body.is-resizing-panel`
   （`ScreenResizeBar` 挂/摘）把 transition 置 none。不关就是「手指已经移开，面板还在追」。
4. **宽度用 CSS 变量下发**（`--panel-left-w` / `--panel-right-w`），不要逐层传 prop：
   宽度要同时驱动面板底图、标题装饰、拖动条位置、工具卡片换行与插件宿主；
   CSS 自定义属性会自然继承进子组件，scoped 也拦不住。
5. **折叠位移 = 面板宽 + 30，不能写死 430**：面板被拖宽后写死的距离会滑不干净（露出半块）。
   `panelSlideDistance()` 统一算，位移写在根节点的内联 style 里。
6. **`transform` 只在折叠时下发**：常驻 transform 会让铺满画布的包装层变成 `position: fixed`
   后代的包含块，插件浮层（`/pipeline/*` 结果面板等）定位会跟着偏。
   另外状态持久化在 `localStorage['screen-panel-layout-v1']`（结构变更就换 key），
   读取时逐项夹到 `[min, max]`，别信手改过的值。
7. **氛围蒙版必须跟面板同一份状态**：左右蒙版宽 = 面板宽 + 160（设计稿 400→560）、
   下方蒙版高 = 下方面板高 − 35（321→286），折叠 → 0；由 `views/screen/index.vue`
   按 `vignetteLayout`（四页的 panelLayout 单例）内联下发，拖动期间关过渡。
   另写一套阈值 = 必然出现「面板收起了、黑色遮罩还留在原处」（2026-11-13 用户反馈）。
8. **数据管理页三块在页面内部**：左导航 / 右检索列 / 列表用 `--data-nav-w` /
   `--data-search-w` / `--data-nav-offset` 三个变量联动（`offset = 折叠 ? 30 : 导航宽 + 50`），
   entry 面板的检索列宽 / 折叠 / 列表高度读同一份 `usePanelLayout('data')`；
   右检索列只在两个分栏页签存在（`v-if="isSplitPanel"`），下方面板只拖高、不折叠。
   另注意分栏页签的 `.data-screen__body` 是 `inset: 0`，量「内容区左偏移」只能
   在非分栏页签上量，分栏页签看列表 `left`。

**验证**：`node perf/verify-panel-layout.js http://localhost:3001`（65 条，真实拖拽 +
蒙版偏移逐像素核对 + 数据管理左右拖宽折叠 + 刷新持久化 + 0 条未捕获异常）；
横向可拖高的回归仍看 `perf/verify-data-layout.js`。

---

## 4. 常用命令速查

```powershell
# ===== 后端（★ -am 是关键：见 §3.1）=====
cd code/new/server

# 集成测试（-Pit 打开测试；-am 让 base-core 用源码而不是 .m2 的 jar）
mvn -pl jeecg-module-land -am test -Pit
# 单类
mvn -pl jeecg-module-land -am test -Pit -Dtest=DataManagementTest
# 只改了 base-core 时，把它装进 .m2（之后单独构建子模块才拿得到）
mvn -o -pl jeecg-boot-base-core install -DskipTests
# 启动（脚本注入 .env；也可直接用 IDE 跑主类）
powershell -ExecutionPolicy Bypass -File scripts/run-with-env.ps1

# ===== 前端 =====
cd code/new/admin-client          # 或 stargis-client-web
npx --no-install eslint --no-ignore --ext .js,.vue <路径>
node scripts/verify-sfc.js        # 模板编译 + less 编译 + 命名/组件/权限约定自检

# ===== git（仓库根是 code/new）=====
cd code/new
git status --porcelain
```

**测试连的是远程真实库**（`49.232.252.56`），口令取自 `.env`；
每个测试方法跑在事务里、结束回滚，并兜底物理清理 `*-TEST-*` 前缀的行。
所以**默认可以安全地在真实库上跑测试**——但**不要**在测试里关掉事务或提交。

当前规模：集成测试 **17 个测试类 / 270 个用例**（2026-11-02 实测）。
其中 **2 条是「业务补录后的基线快照」用例，当前是失败状态**，属**数据漂移、不是代码缺陷**：
`RoadHandoverDataTest#migrationBaselineSnapshot`（断言「迁移后 84 条全部已移交」，
但远程库 `YJ-2010-0001` 已于 2026-10-22 被改成「待移交」）与
`RoadHandoverApiTest#listSupportsAllDocumentedConditions`（同一数字，接口侧断言 84 实得 83）。
按该测试自己的注释「业务补录数据后这个方法会失败，这是设计如此」，
要恢复绿灯得由 handover 负责人确认后同步改期望值（并更新《线上同步记录》）。

> 小提示：直接在源码里数 `@Test` 会得到比实际执行略多的数字（少数方法 JUnit
> 不按顶层测试收集）。**以 `Tests run:` 那一行为准**。

---

## 5. 改代码前的检查单

1. **先读** `docs/参考资料/新旧系统差异清单.md` 里对应模块的条目。
   想让某处「跟旧系统一致」之前，先确认它是不是 🔧**有意修正**——是的话改回去就是制造 bug。
2. 确认改动落在**正确的模块与目录**（§2）。
3. 后端改动：加/改 SQL 脚本 → 执行到**远程库** → 补测试 → `mvn -pl jeecg-module-land -am test -Pit`
   （**`-am` 别漏**：漏了就可能用 `.m2` 里的旧 base-core，见 §3.1）。
4. 前端改动：`eslint --no-ignore` + `node scripts/verify-sfc.js` 都要过。
5. 动了 `jeecg-boot-base-core` → 要么构建时带 `-am`，要么先
   `mvn -o -pl jeecg-boot-base-core install -DskipTests`（见 §3.1）。
6. 发现新的新旧差异 → **补进差异清单**（照它的条目模板，带行号证据），并写变更记录。
7. 提交信息用中文，说明**为什么**改；`.env`、口令、token **绝不提交**。

---

## 6. 不要做的事

| ❌ 不要 | 原因 |
|---|---|
| 改 `code/old/**` | 旧系统是**只读基线**，改了差异清单的行号证据全部失效 |
| 提交 `.env`（后端）或任何真实口令 | 已 gitignore；仓库是公开的 |
| 在 `code/new` 之外执行 git 命令并以为能用 | 仓库根是 `code/new` |
| 以为 `docs/` 的改动会被提交 | `docs/` 在仓库外 |
| 改 `jeecg-server-cloud/**` | 云版模板，本项目未使用 |
| 把 `sys_*` / `jimu_*` / `onl_*` / `qrtz_*` 平台表当成业务表改 | jeecg 框架自带 |
| 给大屏端加 `v-has` | 见 §3.6 |
| 不带 `-Pit` 就说「测试通过」 | 那是没跑，见 §3.5 |
| 顺手统一列名 / id 长度 / 表名 | 见 §3.9 |

---

## 7. 文档地图

| 文档 | 什么时候看 |
|---|---|
| `docs/参考资料/新旧系统差异清单.md` | ★ **改业务逻辑前必看**；也用于向中心解释差异 |
| `docs/升级改造工作内容清单.md` | 需求权威原文（239 KB，按章节查） |
| `docs/参考资料/旧系统逆向分析/_db_schema_report.md` | 旧库/新库表结构详解（209 KB） |
| `docs/参考资料/旧系统逆向分析/_old_frontend_analysis.md` | 旧前端页面与交互分析（273 KB） |
| `docs/参考资料/旧系统逆向分析/_plan_text.txt` | 方案的精简文本（10 KB，先看这个） |
| `docs/模块实现说明/数据管理-实现说明.md` | 数据管理六大功能的实现与取舍 |
| `docs/模块实现说明/经营性用地批量导入-实现说明.md` | 批量导入（模板/校验/回执） |
| `docs/模块实现说明/档案管理-实现说明.md` 等 | 各模块实现说明 |
| `code/new/server/环境变量与敏感信息.md` | `.env` 机制、排查手册 |
| `code/new/docs/文件预览-kkFileView接入说明.md` | 改文件预览 / 部署 kkFileView 前必读 |
| `code/new/stargis-client-web/src/components/screen/README.md` | 大屏组件库契约 + 已知坑（**写大屏必读**） |

---

## 8. 变更记录

| 日期 | 变更 | 说明 |
|---|---|---|
| 2026-10-09 | 建立本文件 | 汇总目录结构、高危陷阱、命令速查、检查单 |
| 2026-10-09 | **改写 §3.1** | 原来只写「必须 install」，容易被理解成配置有问题。实际是 Maven 的 reactor 机制：`-pl` 只把选中模块放进 reactor，其余模块从 `.m2` 取。改为解释原因 + 给出 `-am` 这个更省事的做法（已实测 `-pl … -am` 会构建 3 个模块），并把 §4/§5 的命令同步加上 `-am` |
| 2026-10-13 | 大屏新增「配套分析管理」页面骨架 | stargis-client-web：新增 `views/screen/analysis/`（左工具箱 11 项 + 右面板壳 + 底部属性条），接线 `screen/index.vue`（模板分支 / import / components / IMPLEMENTED_MENUS / show-clock）；11 个功能图标取自旧库 stargis_menu 的功能映射（成熟分析旧系统即复用 gxtj.png），白模按现行工具图标配色重上色后落为 `assets/screen-blue/analysis-*.png`。**功能面板本体待接入**：接入点集中在 `analysis/index.vue` 的 `__host` 容器与 `toggleToolByKey`，写法参照 map/index.vue 的 PLUGIN_TOOLS 模式 |
| 2026-10-13 | 配套分析页接入「管线统计」 | 首个接功能的工具：`<pipe-statistics>` 引擎页面级挂载（查询表单在引擎组件内部，用 `__engine-host` 包裹——闲置 display:none 只藏按钮壳、激活后显示，让表单落进右面板框，**不能像 road 引擎那样整体 v-show="false"**，否则表单不可见）；配置按老工作台 HomePipeFooter 口径从 `sysConfig['PipeManager']` 摊平取 PipeStatistics；五个统计结果弹窗（property / diameter / material / diameterSect / depthSect）在右面板 `__host` 内挂载——弹窗自含（内部监听以配置键命名的总线事件 xzanalyse / gjanalyse / czanalyse / gjfdanalyse / msfdanalyse 显隐，已从 UMD 字符表证实），宿主只喂 configMin，挂法照抄 HomePipeBody。老式 .pop 壳（300px 宽、青辉光、旧标题栏）已按 map/index.vue 的 __road-host 写法 /deep/ 压平面板框（对 .pop / .pop-header / .pop-title / .pop-close / .pop-body 统一覆盖，对五个弹窗与引擎自画面板同时生效）。开关顺序遵循先挂弹窗宿主再 btnClick；未配数据源 / 地图未就绪均有 toast 兜底。**遗留真机验证点**：弹窗内容在 400px 面板内的实际观感、引擎闲置态是否有多余按钮壳、弹窗自带关闭钮与卡片状态的同步（事件名在混淆包里未证实），需中台配好数据源后跑一次 |
| 2026-10-14 | 管线统计真机问题修复（首轮回） | 真机反馈两处：① 五个统计类型入口按钮（.analyse-pipe：管径分段 / 埋深分段 / 材质分类 / 管径分类 / 点性质分类）浮在页头导航上——**插件把它们手动挂到 body 层，scoped /deep/ 够不着**（673 条 CSS 规则全是 absolute 却出现在视口坐标，即已脱离组件树挂载），改在 analysis/index.vue 末尾用**非 scoped 全局样式块**隐藏（旧工作台页面未注册路由，全局规则无副作用）；② 引擎面板自带「管线统计」标题栏+关闭钮，画布层标题重复——pipeline 分支不再渲染画布层标题/装饰（其它工具仍渲染）。**备选方案已写进全局样式块注释**：若真机发现引擎面板内选不到五种统计类型（即那五枚按钮是唯一入口），把 display:none 换成按视口坐标收进面板。另一个已知待观察点：弹窗自带关闭钮与卡片选中态的同步 |
| 2026-10-14 | 管线统计真机问题修复（第二轮回） | 上一轮的全局 `display:none` 一刀切**误伤引擎面板**：引擎对话框的内容包裹层同样带 .analyse-pipe 类（UMD 有 .analyse-pipe .analyse-pipe 嵌套规则），面板被藏得只剩标题条（真机已复现）。收敛为两条精确规则：① 全局 `.analyse-pipe:not(.analysis-screen .analyse-pipe)`——只藏 body 层浮层入口，页面子树内的引擎内容豁免；② scoped `&__host /deep/ .analyse-pipe:not(.pop)`——只藏五个弹窗自己的收起态按钮壳（弹窗在面板子树内，若壳用 fixed 行内定位会顶到页头），打开后的面板根带 .pop 走压平规则正常显示，引擎内容在 `__engine-host` 不受影响。已知边界：:not() 内复杂选择器需 Chrome 88+，不支持时规则整条失效退回浮层可见；若弹窗面板根不带 .pop 则打开后不可见（真机跑一次统计即可验证） |
| 2026-10-14 | 五枚统计类型入口收进面板顶部 | 用户确认那五枚 .analyse-pipe 按钮就是统计类型入口，要求从页头浮层挪进面板顶部按两到三行排布。实现：`__type-buttons` 容器（面板标题栏下方，flex wrap 两枚一行）+ `collectTypeButtons()` 用 appendChild 把插件渲染的按钮**原节点搬移**进容器（点击监听随节点保留，行为不变），按钮的旧工作台定位（fixed + 行内 top/left）用 !important 清成 static 交给容器排布。两个关键坑：**容器必须 v-show 不能 v-if**——按钮搬进来就属于容器，v-if 切走会连插件监听一起销毁；引擎重渲染会重新生成按钮，用 MutationObserver 监听 `__engine-host` 触发重搬（搬移前清容器防重复）。搬移时机：mounted 先试一次 + watch pipeActive 开轮询（300ms×20）兜底异步渲染。按钮宽度：插件写死 155px 一行一枚，强制 `width: calc(50% - 4px)` 两枚一行占满 |
| 2026-10-14 | 类型入口点击无反应修复（改总线直发） | 自绘按钮排版 OK 但点击无反应：程序化 click 命中的是 .analyse-pipe 的**外层包裹节点**（该类有嵌套结构，按文本匹配会先命中包含全部按钮文案的容器），点它没有绑定处理器。改为**直接发总线事件**：五个统计弹窗各自 $on 以配置键命名的事件（gjfdanalyse / msfdanalyse / czanalyse / gjanalyse / xzanalyse，payload true 显示 / false 隐藏，UMD 已证实），自绘按钮点击 = `$bus.$emit(event, true)`，与插件浮层按钮的最终行为同一条契约，绕开对插件节点结构的依赖。数据结构改为 `pipelineTypeLabels: [{label, event}]`，方法名 triggerPluginButton → openTypePop。collectTypeButtons / 停靠区保留（职责改为纯粹的「收浮层入口防页头污染」） |
| 2026-10-14 | 统计弹窗互斥（防叠加） | 直发事件绕过了引擎 checkedALL 的互斥，五个弹窗又都被压平成面板全幅，连点几枚就整叠在一起（真机已复现）。补页面级互斥：`activePopEvent` 记当前打开的弹窗事件名，openTypePop 先对上一枚发 false 再开当前枚，再点同一枚 = 收起；closeOtherTools 里关卡片 / 切工具时也发 false 复位（弹窗宿主虽随 v-if 卸载，内部开态还在，不复位下次进入会直接铺开）。已知边界：弹窗自带关闭钮若只改内部状态不发总线，activePopEvent 会失真——下次点按钮多发一次 false 属无害幂等 |
| 2026-10-14 | 配套分析页接入「标注管理」 | 四功能 → 插件组件映射（package.html + HomePipeFooter 老挂法）：管线标注 = PipeMark + PipeMarkPop（10-14）、埋深标注 = DepthLabel、坐标标注 = CoordinateLabel + CoordinateLabelPop（Common 家族 1-4，配置在 sysConfig['Common']）、清除标注 = DeleteLabel（10-9，无弹窗、地图点选删除）。实现：三枚 road 式引擎隐藏挂载（v-show="false"，表单都在独立弹窗里，与管线统计引擎不同），配置摊平扩展为 PipeManager + Common 两模块并集；面板四枚自绘按钮，点击 resetEngineStates(key) 收其它引擎 → engine.btnClick() 进 / 退标注模式 → activeLabelTool 记选中态，再点同一枚 = 退出；PipeMarkPop / CoordinateLabelPop 挂 __host 由引擎经总线拉起。resetEngineStates(keepKey) 统一收口全部引擎复位 + activePopEvent 复位，cancelAll（引擎激活契约）与 closeOtherTools 都走它 |
| 2026-10-14 | 埋深标注组件缺失的定性 + 自适应接入 | 用户质疑「怎么可能没有埋深标注」，深查后定性（证据链）：① 插件三份构建（common / umd / umd.min，代码项目与 git 仓库的各一份）DepthLabel 均只有 6 处图标资源路径；② 用 Node 解出混淆字符串表（javascript-obfuscator 标准模式：字符串表 + 移位校验，18643 条全量解码）——DeleteLabel / LabelTool / PipeMark / CoordinateLabel / TextLabel 全部在表里，**DepthLabel 不存在**；③ 注册逻辑 `Vue.component(com.name, com)`，com.name 明文可查。结论：这版插件确实没打包 DepthLabel 组件（图标做了、代码没进构建，源码已丢失无从考证）。处理改为**乐观挂载 + 运行时能力检测**：`<depth-label v-if="DepthLabel 配置">` 乐观挂载（组件缺失时 Vue 渲染空自定义元素并告警，无害），按钮点击时 `typeof engine.btnClick !== 'function'` 则 toast「当前 stargis-function 包未包含埋深标注组件」——插件升级带上组件后零改动直接可用。查插件组件是否存在的正确姿势：混淆包要解字符串表，单靠 grep 明文不可靠 |
| 2026-10-14 | 标注子模式与类型按钮的遮挡修复（第二版） | 第一版「子模式隐藏自绘按钮」方向不对——用户要的是**四枚按钮常驻顶部、插件工具条出现在按钮下方**。改为布局让位：容器不再隐藏（v-show 去掉 !activeLabelTool 条件），`__host /deep/ .pop-body` 顶部 padding 让出按钮条高度（top:198 + 管线三行 ≈ 底缘面板内 173px，标注两行略有余量），插件工具条（管线/管点/管井/确定/重置/清扫）与统计表单都从按钮条下方开始排，超高走 .pop-body 纵向滚动；`__engine-host` 的管线统计工作台对话框不加让位。同时删掉上一版的 checked 轮询（startLabelModeWatch，不再需要）。子模式退出 = 再点同一枚功能按钮（btnClick 切换） |
| 2026-10-14 | 埋深标注页面自实现（DepthLabel 插件缺失的替代） | 参照旧系统 soilLabel.js 的逻辑用 Cesium 重写（旧实现 100% 依赖旧专有引擎 DataEngineManager / VRStarGeometry / QueryFilter，无法移植）：点「埋深标注」→ LEFT_CLICK 拾取模式（照抄 map/index.vue 的图层名探测模式）→ scene.pick 点管线 → 读属性 QSMS（起点埋深）/ ZZMS（终点埋深），字段名做大小写 + 中文名兜底，PropertyBag 与 3D Tiles getProperty 两种拾取结果都兼容 → 起止点坐标**取折线首末顶点**（旧系统要拿点号查管点层，新系统管线本身就是折线，省一步；取不到退化到拾取点）→ viewer.entities 画「起点埋深 / 终点埋深」两个文字标注（禁深度测试等价旧实现的 Z+4 抬高），实体 id 记入非响应式 `_depthLabels`。「清除标注」一键清扫：先清这批自绘标注再触发插件 DeleteLabel 引擎。生命周期：beforeDestroy 清 handler + 实体（地图跨页共享）。**真机待验证**：① 管线属性里有没有 QSMS/ZZMS 字段（新系统代码无引用，静态查不到数据侧）——用地图管理的「拾取查询」点一条管线看属性即可确认；② 折线顶点可不可读（取决于管线图层渲染方式） |
| 2026-10-14 | 「清除标注」语义修正 | 之前错接成「插件的删除模式」。查旧系统 deleteObject.js + labelManager.js：清除标注 = **一键清空全部标注**（`labelManager.clearLabel()` → 删标注树所有实体 + 取消高亮），不是删除模式。新插件里 DeleteLabel 引擎的原生行为也是清扫全部——解码字符串表证实存在 `clearLabels` 方法名与「确定清扫全部？」确认文案。现行实现已正确：清自绘埋深标注 + 触发 DeleteLabel 引擎（插件弹自己的「确定清扫全部？」确认框清扫它自绘的管线 / 坐标标注），本轮仅修正注释与提示语口径 |
| 2026-10-14 | 清除标注解除配置门槛 | 真机：点清除标注 toast「未配置数据源」——中台 PipeManager 配置树里没有 DeleteLabel 项，引擎 v-if 不挂载导致被配置守卫拦截。修正：清除标注的**主职责（清自绘埋深标注）不依赖任何配置**，改为无条件先清，插件 DeleteLabel 引擎「存在且有 btnClick」时才追加触发其清扫确认框；都没得清时提示「当前没有可清除的标注」。若希望插件侧（管线 / 坐标标注）也能一键清扫，需在中台 GIS 功能配置「管线管理」里补 DeleteLabel 配置项 |
| 2026-10-14 | 清除标注覆盖 HTML 浮层标注（坐标标注） | 真机：坐标标注画出的标注是带「经度/纬度/高度」+ X 关闭钮的 **HTML 浮层**（不在 viewer.entities 里），实体清扫碰不到它。五轮迭代：①文案特征写死全角冒号，浮层实际半角，匹配 0（§2.4 同类坑）；②放宽两词 + 按 class 猜关闭钮——空计数、气泡还在（猜的钮没绑事件）；③数值特征 + 取「最外层匹配」直接删——**白屏**（textContent 沿祖先链上冒，#app 也含这段文字，最外层匹配 = 整个应用）；④最内层匹配 + 向上爬 + 直接删——**能清但清完再也标注不上**：爬到了插件放标注的**共享容器**并删掉，之后新标注全部 append 进脱离文档的容器（护栏只防 canvas/>300/自有类名，防不住小共享容器），且实体清扫计数并入导致**报数虚高**；⑤（现行）**绝不删节点**：每浮层按视觉定位找右上角 60×60 区域内的小元素（取面积最小 = X 图标，点击冒泡触发任意层级的处理器）点掉，插件自己清理状态与 DOM；实体清扫降级为静默卫生清理不计数；labelPopsReset 重建机制撤掉（扰动源）。报数 = 埋深标注 + 浮层点击数 |
| 2026-10-14 | 断面 startDraw 直发的定位修正 | 真机：卡片点击 toast「包未包含断面分析组件」——startDraw **不在引擎根实例上**，在引擎的子组件（内部面板）上，`engine.startDraw` 直呼必然 undefined。修法：保留 btnClick 激活（面板渲染出来），`$nextTick` 后 BFS 遍历引擎 `$children` 子树找到持有 startDraw 的实例再直发模式（findSectionalVm）；退出时同路径调 cleanProfile + cancelChecked。经验：混淆插件引擎的公开方法不一定在根实例，ref 拿到实例后要在组件子树里找方法持有者 |
| 2026-10-16 | 断面模式面板全局隐藏 | 用户确认横/纵断面都不需要右侧面板——纯地图交互。面板根类从插件 CSS 反查（.pipecard 同 data-v 哈希组件：police-iot-pop / iotcontainer），全局规则 `.police-iot-pop:has(.pipecard), .iotcontainer:has(.pipecard) { display:none }` 精准隐藏（:has 限定只藏装着断面模式按钮的层，Chrome 105+，不支持则退回可见）；隐藏 DOM 里程序化 click 照常派发，模式按钮的自动点中不受影响。已知取舍：统计/出图结果若渲染在该面板内会一并隐藏，真机出图后如需查看再改「出结果时显示」 |
| 2026-10-16 | 纵断面面板隐藏修正（一行） | 真机：纵断面激活时面板仍显示——面板在引擎子树内，而 engine-host 的 is-idle 条件里有 activeSectionMode !== 'vertical' 的例外（纵断面时故意显示宿主——设计错了，用户要的就是面板不出现）。修正：is-idle 回归 `!pipeActive`，横/纵断面时宿主一律隐藏；拾取交互在地图画布上照常工作（隐藏 DOM 的程序化点击照常派发）。已知取舍：纵断面出图若渲染在隐藏宿主内则不可见，真机拾取管线后如需查看再改「出结果时显示」 |
| 2026-10-16 | 接入「净距分析」 | 净距分析 = DistanceAnalysis 引擎 + DistanceAnalysisPop 结果弹窗（配置键 DistanceAnalysis，已在 PipeManager 摊平表里；总线事件 showDistancePop 已从解码表证实）。标准 road 式接入：隐藏引擎 + __host 结果弹窗分支 + toggleToolByKey 分发 toggleClearance（配置守卫 / 组件缺失 toast / 地图未就绪兜底 / resetEngineStates 互斥，全部同既有模式）。净距的语义：拾取两根管线量它们之间的净距，结果弹窗展示——具体拾取交互由引擎内部流程决定，真机验证 |
| 2026-10-16 | 接入「爆管分析」 | 爆管分析 = BurstAnalysis 引擎 + BurstAnalysisPop 结果弹窗（配置键 BurstAnalysis，已在 PipeManager 摊平表里；总线事件 showBurstPop 已从解码表证实，弹窗已注册 ✓）。净距/覆土/流向同款 road 式接入：隐藏引擎 + __host 结果弹窗分支（flow 分支后）+ toggleToolByKey 分发 toggleBurst + resetEngineStates 引擎清单加 burst。互斥/兜底/计数全同净距模式。真机验证点：拾取交互由引擎内部流程决定（拾取爆管点后出关阀方案与影响分析），中台需配置 BurstAnalysis 数据源 |
| 2026-10-16 | 接入「开挖分析」 | 开挖分析 = ExcavationAnalysis 引擎 + ExcavationAnalysisPop 结果弹窗（配置键 ExcavationAnalysis，已在 PipeManager 摊平表里）。净距/覆土/流向/爆管同款 road 式接入：隐藏引擎 + __host 结果弹窗分支（burst 分支后）+ toggleToolByKey 分发 toggleExcavation + resetEngineStates 引擎清单加 excavation。⚠ 事件名双拼写并存：showExcavatePop 与 showExcatatePop 都在解码表里（插件历史拼写变体，弹窗文件名就是拼错的 ExcatatePop），开合双发保命中。互斥/兜底/计数全同净距模式。真机验证点：拾取交互由引擎内部流程决定，中台需配置 ExcavationAnalysis 数据源 |
| 2026-10-16 | 「成熟度分析」预留面板（配置点 + 空态） | 查证：插件三份构建 + 解码字符串表（18643 条）中均无成熟度/Maturity/csd 组件；旧系统的成熟分析也非分析功能——showImage → showQueryData('11') + csdManager 的 btns2~15 = 按成熟度等级**互斥切换宗地专题图层**的显隐开关 + 成熟度数据表格。处理：页面自实现预留面板——MATURITY_LAYERS 配置常量（export default 前，当前为空，含接入说明与旧系统图层 code 清单）+ __host 成熟度分支（开关列表 / 空态提示）+ toggleMaturityLayer（互斥翻状态，图层显隐 API 留 TODO 接入点）。图层服务接入后：填 MATURITY_LAYERS 数组（label + code）+ 在 TODO 处接图层显隐 API，两步即用 |
| 2026-10-16 | 净距弹窗顶部空白修复 | 净距分析没有类型按钮条，但 __host 的 .pop-body 顶部让位（174px）是无条件生效的，导致净距弹窗顶部一大块空白（真机截图）。改为条件生效：__host 加 has-typestrip 类（仅管线统计/标注管理两种有按钮条的模式），.pop-body 的 padding-top:174px 只在 has-typestrip 下生效，净距等无按钮条的模式内容顶格 |
| 2026-10-16 | 断面模式面板与统计浮层的 body 层隐藏（按标题定位） | 上一轮的 :has() CSS 规则没生效——断面面板根类猜错（police-iot-pop/iotcontainer 是从 pipecard 同哈希反查的，实际面板 DOM 不含它们）。改**按标题定位**的通用方法 hidePluginFloat(label)：body 直接子级里含目标标题、且不含 .analysis-screen（排除本页卡片文案）的元素置 display:none。三个调用点：① 断面激活后 hidePluginFloat('断面分析')（横/纵两处轮询成功分支各一次）；② resetEngineStates 里 hidePluginFloat('管线统计')（统计浮层此前也一直漏收——真机截图里「管线统计」标题就是它）。已知取舍：出图结果若渲染在被隐藏的浮层里则不可见，真机出图后如需查看再改「出结果时显示」 |
| 2026-10-15 | 横 / 纵断面拆成两个不同组件（对齐旧系统交互） | 用户澄清两枚卡片在旧系统里是不同交互：横断面 = 右侧无面板、直接画切线；纵断面 = 带「绘制 / 清除」按钮的操作弹窗（老系统 sectionZ.html 里有清除分析按钮）。映射修正：横断面 → SectionalAnalysis（引擎宿主保持 is-idle 隐藏、面板里的模式按钮在隐藏 DOM 里程序化点中——display:none 的 click 照常派发）；纵断面 → **ProfileAnalysis + ProfileAnalysisPop**（SpatialAnalysis 家族 4-11，drawProfile 绘制 / cleanProfile 清除，showProfilePop 总线事件拉起，配置在 sysConfig['SpatialAnalysis'] → ProfileAnalysis）——之前把两枚卡片都接到 SectionalAnalysis 的两种模式上是理解错了。配置摊平加入第三个模块 SpatialAnalysis。修配置查找太苛刻的坑：面板模式按钮带图标（内部有 img/span 子元素），「纯文本叶子」条件匹配不上，改「文案精确匹配取最内层」（与清除标注浮层同套路），找不到明确 toast 反馈不静默 |
| 2026-10-15 | 纵断面改回 SectionalAnalysis 纵断面模式（修正上一轮误接） | 查老系统 dmAnalysis.js 实锤两种断面的交互：横断面 enableCrossSectionAnalysis = 几何编辑**画切线**；纵断面 enablePipeSectionAnalysis = OnMouseClickSelect **拾取管线** → sectionZ 对话框「开始分析/清除分析」→ 出管线沿走向的埋深剖面。上一轮把纵断面指到 ProfileAnalysis（剖面分析，画线看地形起伏，语义不同）是接错了——改回 SectionalAnalysis 纵断面模式：点卡片 → 引擎激活 + 自动点面板「纵断面分析」模式按钮进入拾取；纵断面激活时引擎宿主可见（面板 = 拾取后的操作 UI），横断面激活时宿主保持隐藏（右侧无面板、直接画线）。ProfileAnalysis 引擎与配置摊平保留（SpatialAnalysis 模块已并入，配置未配则不挂载，留作后续「剖面分析」独立功能用） |
| 2026-10-14 | 断面分析改左面板直控（按地图管理规则） | 用户要求：点横/纵断面卡片**直接在地图上起绘制**，不弹插件的模式面板（btnClick 会弹「断面分析 + 纵/横断面按钮」面板）。解码 startDraw 源码证实它自带模式设置与地图交互挂接、可独立调用 → toggleSectional 改为**直发 startDraw(mode)、不调 btnClick**；再点同一枚卡片 = cleanProfile（清已画断面）+ cancelChecked 复位；切其它工具时 resetEngineStates 也清已画剖面。经验：插件引擎的公开方法（startDraw/cleanProfile 等方法名在混淆包里是明文）可以绕过其内部面板直呼，按地图管理「点按钮直接画」的规则集成 |
| 2026-10-14 | 接入「横断面分析 / 纵断面分析」 | 同一枚 **SectionalAnalysis 引擎**（PipeManager 家族 10-17，配置键 SectionalAnalysis，清单无独立弹窗——对话框在引擎内部）的两种模式：解码字符串表发现引擎有公开方法 **`startDraw('horizontal' \| 'vertical')`**（设模式 + 起绘制，downloadImg 按模式出图 titled 横断面分析/纵断面分析/断面分析）。两枚断面卡片（sectionH/sectionV）→ toggleSectional(mode)：resetEngineStates('sectional') 收其它引擎 → 引擎未 checked 则 btnClick() → startDraw(mode)；横↔纵切换直接 startDraw 不重进；再点同一枚卡片 = cancelChecked 退出。引擎宿主 generalize：v-if 放宽（pipeConfig 或 sectionConfig）、is-idle 条件加 activeSectionMode、断面激活时加 is-front（z-index 提到空壳面板之上，否则断面对话框被空壳盖住）。已知待验证：断面对话框是否 .pop 壳（决定 /deep/ 压平是否生效）、startDraw 是否需要 btnClick 的前置状态 |
| 2026-10-14 | 断面分析三处修正（字符串表全量破解后定性） | 破解本构建混淆字符串表（**数组在原文即明文**，访问器只做 `idx-0x6e` 偏移查表；难点是移位 IIFE——按其校验和表达式 + 种子 0xbe3a8 暴力试出旋转量 352 即可全量按索引解码，此前「解码脚本跑通但搜不到已知串」就是旋转量没对上）。据此把上一条的三个待验证项全部定死并修正：① **startDraw 不在引擎本体上**——在内嵌 `PopSectionList` 弹层组件（name 0x536，props isShow/configMin，数据 checkedALL 键就是 horizontal/vertical），引擎只有 btnClick/onCheckedBtn/cancelChecked；原 `typeof engine.startDraw === 'function'` 守卫**静默跳过**、画线永远不开始。改为经 `engine.$children` 找到弹层直发 startDraw(mode)（卡片即模式入口，清单仍开着可横↔纵切换；isShow→false 的 watch 连锁 controlChecked('')→clearAll 销毁 window.handlerDraw，退出清理链完整）。② **出图浮层带 .pop 类**（'pop bottom-table info-section'）会被 `__engine-host /deep/ .pop` 压平规则塞进 400px 面板（1024×720 出图必炸）——四条压平选择器加 `:not(.bottom-table)` 豁免，另给专属规则保持原生尺寸、锚宿主右下 right:20/bottom:50（补齐宿主距视口的 30/50 偏移 = 旧工作台视口右下 bottom:100/right:50 同位）。③ **模式清单 .list-section 插件锚 top:-80px**（锚老工具栏，本页宿主顶 149−80=69 顶进页头，与统计类型入口同类坑）——改锚面板内 top:60/right:8。顺带核实全链路：清单项 componentname 就是 horizontal/vertical，点项 → startDraw → controlChecked 单选高亮 → horizontalAnalyse 逐选中图层 getAction `/app/basicinfo/metaJsonByTreeId` 取数据源元数据 → POST `VUE_DATA_JAVA_URL + '/pipeline/drawHSection'`（{points, tableNames, title:'横断面出图'}）→ 结果图 `VUE_DATA_JAVA_URL + '/api/file/' + data`；**应用未注入 window._CONFIG.VUE_DATA_JAVA_URL**（config/index.js 只设 domianURL 等），插件会拼出 undefined/…——待真机确认后决定注入方式（环境变量 or 中台 domianURL），本次未改。/pipeline/* 服务族（爆管 squibAnaylse、覆土 buryAnaylse、净距 getVHCollisionDisAnalyse 等同族）的部署连通性同样是真机验证项 |
| 2026-10-16 | **补全 §3.6 第 4 条** | 原来只写「交互元素要有 `pointer-events: auto`」，只覆盖了一个方向，照它做反而可能给铺满屏幕的容器层加 `auto`。补上反方向（铺满屏幕的容器必须 `none`）+「透明 ≠ 不接事件」+「父 `none` + 子 `auto`」。触发原因：弹窗遮罩改透明时又踩了一次（表现是「看得见地图却点不到」，且肉眼完全看不出有东西挡着） |
| 2026-10-19 | **补 §2.1 已实现域** | `stat.home` 落地（首页看板两个统计，接口 `/land/stat/home/*`）。原 `data` 域的 `/land/data/dashboard`、`/land/data/facility/dashboard` 已删除，前端 `landData.js` 同步改路径。改首页数字前先读 `docs/模块实现说明/首页看板-实现说明.md`：区级子表按 `xzqh2` 分组是**旧系统口径**，不是笔误 |
| 2026-10-20 | **重写 §2.4（括号 → 名称/编号全字符归一）** | 用户确认口径：名称里的半角括号→中文、连字符族→`-`、去掉所有空白；**编号末尾一律补「号」**，`【城更】/【城市更新】` 摘下来记备注。原因是 58 条配套因「带不带号」挂不上宗地、12 对宗地+56 条配套重复录入。写入侧收口到 `DataSupport`（5 个入口，含容易漏的旧接口 `LandServiceImpl`/`FacilityServiceImpl`），历史数据由 `sql/data/12_normalize_codes_and_names.sql` 幂等清洗。注意：**去重必须排在补「号」之前**，否则撞 `uk_land_crzdbh (crzdbh, del_flag)` |
| 2026-10-21 | **补 §2.1 已实现域** | `stat.query` + `stat.export` 落地（查询统计：经营性用地综合查询 / 用地配套项目查询 / 高级查询 / Excel 导出，接口 `/land/stat/query/*`），前端新增 `views/screen/query` 并接入顶栏「查询统计」。改查询口径前先读 `docs/模块实现说明/查询统计-实现说明.md`：**两个数据集的指标口径不同**（宗地口径按配套名称去重 + 环节漏斗；配套口径按条计、不漏斗），别拿两页的数字互相校验 |
| 2026-10-22 | **大屏「档案查询」页签并入「档案维护」** | 两者本来就共用 `ArchiveSearchForm`（13 个检索条件）、`ArchiveTable`、`/land/archive/list`，差别只有「只读 + 条件默认全展开 + 每页 20 条」，在大屏上属同一功能的两种用法 → 合并，侧栏 8 项→7 项，方案 2.3.2 第 3 项由「档案维护」承载。改动：`ArchiveMaintain.vue` 接管 `applyDrill` / `openDetailById`（原 `ArchiveQuery.vue`，已删除），并新增 `searchInitialQuery` 计算属性把深链参数 `archiveId` 挡在检索条件之外（原来会被「查询/导出」原样带给后端）；`archive/index.vue` 删 `query` 页签、`DRILLABLE_PANELS` 改 `['maintain','ledger','handover','completion']`、`handleDrill` 落点改 `maintain`；`views/screen/index.vue` 的 `ARCHIVE_TABS` 同步删 `query`（历史深链 `?tab=query` 静默落回维护、条件照常生效）；统计页/台账/移交的下钻落点随之改到维护页。**`admin-client` 的「档案查询」菜单保留不动** —— 那里才是「只查不改」的权限边界（`land:archive:list` + `export`），且方案条目由它满足 |
| 2026-10-22 | **首页「项目预警」补齐（市级 / 区级 / 地块）** | 方案 2.3.1（五）。数据早就在 `pendingFacility.warnings` 里，但前端只把百分比原样印出来，等于没做。本轮：① `views/screen/config.js` 新增 `warningTiers` / `warningTone()` —— **阈值唯一口径**，市级 5 档（0-20/20-40/40-60/60-80/≥80）、区级 3 档（0-33.33/33.33-66.66/≥66.66），照搬旧 `homePage.js` 的 `GetColumn717` / `GetColumn0805`；颜色取 `screen-tokens.less` 的 `--screen-level-*`。**两处都要改**，只改一处会让底部面板与弹窗颜色不一致。② 「一条配套都没上报」的区改成跨七列一句「未填报配套项目信息」。③ 第三个页签「地块预警信息」补回 `warningTabs` 并接上 `plotVisible` —— 此前 `HomeAttrPanel` emit 了 `open-plot-warning` 但**没有任何监听者**，点了没反应。④ 新增 `GET /land/stat/home/warningDetail`（按区下钻、一行一宗地）；下钻按**页签 key** 传 `projectType`，不要从行 id 的中文字符串猜（旧 `changeShowInfo()` 的老毛病）。⑤ 删掉旧 `/land/data/facility/warning/detail`：它按 `l.xzqh` 过滤，而区级预警的分组列是 `xzqh2` → **区级点「查看」永远空表**；口径收口到 `stat/home`。⑥ 预警分子分母补软删过滤（D-613）、`0/0` 不再出 `NaN%`（D-614）、阈值按真实数值比较（D-615）。改预警口径前先读 `docs/模块实现说明/首页看板-实现说明.md` 第 6 节：**7 项未完成率按配套条数算、不按 `ptxmmc` 去重**，与同页「配套总数 / 已完成」的去重口径故意不同 |

> 新增陷阱时，请**同时**：① 在本文件 §3 加一条（编号继续，
> 例如 §3.10）；② 在本表登记。陷阱要写**症状**和**怎么排查**，不要只写结论。

| 2026-10-16 | 接入「覆土分析」 | 覆土分析 = CoverageAnalysis 引擎 + CoverageAnalysisPop 结果弹窗（配置键 CoverageAnalysis，已在 PipeManager 摊平表里；总线事件 showCoveragePop 已从解码表证实，弹窗已注册 ✓）。净距分析同款 road 式接入：隐藏引擎 + __host 结果弹窗分支（clearance 分支后）+ toggleToolByKey 分发 toggleCover + resetEngineStates 引擎清单加 cover。互斥/兜底/计数全同净距模式。真机验证点：拾取交互由引擎内部流程决定（拾取管线后出覆土结果），中台需配置 CoverageAnalysis 数据源 |
| 2026-10-16 | 接入「流向分析」 | 流向分析 = FlowDirectionAnalysis 引擎 + FlowDirectionAnalysisPop 结果弹窗（配置键 FlowDirectionAnalysis，已在 PipeManager 摊平表里；总线事件 showFlowDirectionAnalysisPop 已从解码表证实）。净距/覆土同款 road 式接入：隐藏引擎 + __host 结果弹窗分支（cover 分支后）+ toggleToolByKey 分发 toggleFlow + resetEngineStates 引擎清单加 flow。互斥/兜底/计数全同净距模式。真机验证点：拾取交互由引擎内部流程决定（拾取管线后出流向结果），中台需配置 FlowDirectionAnalysis 数据源 |
| 2026-10-16 | 接入「配套估算」（前端复刻旧公式，用户选方案 1） | 插件无此组件（已查证）；旧系统 ptgsManager.js（163 行）的区间算法实为一行公式：拾取地块 → 读「出让金（亿元）」→ ptf = 出让金×10000（万元），区间 = ptf/2 ∓ ptf/2×0.3（即 ptf 的 35%~65%），填三格：宗地编号/地块名称/估算区间。实现：页面自实现——点「配套估算」卡片 → LEFT_CLICK 拾取模式 → scene.pick 读地块属性（字段名宽松匹配：出让宗地编号/CRZDBH、地块名称/DKMC、出让金，全半角与大小写兜底）→ 旧公式计算 → 结果显示在右面板（宗地编号/地块名称/估算区间大字 + 口径说明）。数值无效/无出让金字段/地图未就绪均有 toast 兜底；resetEngineStates 与 beforeDestroy 清拾取句柄。真机验证点：地块属性里出让金字段名与数值单位（亿元）是否与旧数据一致——用地图管理「拾取查询」点地块看属性即可确认 |
| 2026-10-23 | **合并远端 `e122f25`（导入）后修掉 `VUE_DATA_JAVA_URL` 语义漂移** | 远端把 `public/static/config.js` 的后端地址拆成三个：`VUE_APP_API_BASE_URL`（中台 4548）、**新增** `VUE_BUSINESS_JAVA_URL`（jeecg 9802/api）、`VUE_DATA_JAVA_URL`（改为 JNI 分析服务 38190，混淆插件写死读它），`manageJava.javaBaseUrl()` 改为优先取新变量。合并时发现本分支 `landData.js` 的 `queryWarningDetail` 仍显式传 `window._CONFIG.VUE_DATA_JAVA_URL` 当 baseURL → 首页预警下钻会打到 38190 上（404/连不上）。已改为走默认 `javaBaseUrl()`，并同步 §3.6 第 2 条口径。**判据：业务（jeecg）接口一律不显式传 baseURL；`VUE_DATA_JAVA_URL` 只属于插件 / `/pipeline/*` 分析服务族** |
| 2026-10-27 | **首页日期时间挪进顶栏导航行** | 用户要求：把时钟（设计稿贴在 (477,115) 的地图留白区，56px 大时间 + 星期/日期两行小字）挪到导航栏上、紧跟最后一项「查询统计」右侧。改法：`ScreenHeader` 右侧原本是「导航」「用户区」两个 absolute 块，改成**一整行 flex**（`screen-header__bar`：left 660 / right 30 / top 6 / 高 60 / `gap` 16，右对齐），顺序为 导航 → 时钟（左侧带竖向分隔线）→ 用户区；时钟由 absolute 改 flex 项，字号 56 → 30、星期/日期 17 → 15px，竖向与导航项、用户区居中（三者中心都在 y=36）。**布局代价**：导航项宽度 150 → 128（7 项共 896），1920 画布下合计约 1208 < 「660→1890」的 1230 可用宽；画布更窄时只收窄导航项（`flex: 0 1 128px`），时钟与用户区 `flex: 0 0 auto` 不收缩，用户名超长走省略号。`HEADER_LAYOUT` 字段随之改名（`navLeft/navRightMargin/userRightMargin/navTop/navItemHeight` → `barLeft/barRightMargin/barTop/barHeight/barGap`，`navStyle()` → `barStyle()`）。时钟只在首页 / 地图管理 / 配套分析管理显示的口径不变 |
| 2026-10-27 | **首页统计拆左右 + 侧栏可折叠 + 右面板页签化** | 用户三条要求：① 左侧只放「出让地块情况统计」、把「落实配套情况统计」挪到右侧；② 右面板默认统计，屏幕右边缘放竖排小页签可切到「图层树」；③ 左右面板与底部属性表都能折叠，**折叠时底部面板宽度要带过渡**。实现：`home/HomeLeftPanel.vue` → **`home/HomeStatPanel.vue`**（一个面板一份统计，`side` 决定镜像位置 / 标题装饰 / 底图；`collapsed` 时整体 `translateX(±430)` 滑出屏幕 —— 430 = 面板宽 400 + 屏边 30），「出让情况 / 落实配套情况」页签整体删除；新增 **`home/HomeSideRail.vue`**（屏边 30px 轨道：折叠把手 + 右面板「统计 / 图层树」竖排小页签）。★ 把手**必须挂在轨道上、不能挂进面板**，否则面板滑走后把手一起滑走，折叠就再也点不回来。`home/HomeLayerTree.vue` 改为右面板第二个页签内容，父组件 `v-if="treeMounted"` 懒挂载（首次切过去才拉起插件引擎 + 发图层树请求）、`v-show` 保活；`home/HomeAttrPanel.vue` 新增 `leftInset` / `rightInset` 入参（侧栏折叠 460 → 30，`transition` 320ms），并把收起按钮从 `left:963px` 改成 `right:23px`（面板变宽后写死 left 会把按钮留在中间）。两个面板根节点都改成 `position:absolute; inset:0` 再整体位移 —— 它们的子元素全是按画布坐标写死的 absolute，根节点一旦不是画布大小，位置全飞。**补**：两侧那两张氛围蒙版（`.land-screen__vignette.is-left/is-right`）本身就是面板的暗底，折叠时若留在原处，屏幕两侧会剩两条与面板等量的暗带（真机截图确认过），现在按 `isHomeLayout && 该侧折叠` 一起淡出（同 320ms），其它页面不受影响 |
| 2026-10-27 | **窗口边缘白缝兜底** | 真机截图里大屏四边有一条 4~6px 的纯白边（大屏底色是 `#04091a`，白只能是「页面默认白底露出来」）。两处兜底：① `ScreenStage` 给舞台加 `box-shadow: 0 0 0 32px var(--screen-bg-deep)`——同色阴影向外铺一圈，**自身 overflow 不会裁掉自己的阴影**，越界那一圈也是深的（⚠ 不要用 `inset:-Npx` 放大，那会连 clientWidth/Height 一起变、缩放比与画布原点都偏）；② 首页 `mounted` 给 `<body>` 挂 `is-screen-dark`、`beforeDestroy` 摘掉，配合文件末尾**非 scoped** 的 `body.is-screen-dark { background: var(--screen-bg-deep) }`（body 背景会铺满整个文档画布，登录页等浅色页面不受影响）。若刷新后仍有白边，说明它在页面之外（浏览器窗口边框 / iframe 或预览容器留边），CSS 无法消除 |
| 2026-10-30 | **地图管理 / 配套分析管理左侧图标统一为政务风线性 SVG** | 两页共 29 个入口不再使用彩色填充、立体 PNG，改为复用 `ScreenIcon` 并补齐 `map-*` / `analysis-*` 业务几何；统一 44×44、1.5px 圆头描边、`--screen-accent` 普通态与 `--screen-text` 选中态。只替换图标及槽位尺寸，卡片布局、工具 key、点击分发与插件交互均不变；旧 PNG 资源与 `hf` 映射暂保留，避免影响其它引用 |
| 2026-10-30 | **大屏顶栏时钟改为全页面显示** | `views/screen/index.vue` 不再按首页 / 地图管理 / 配套分析管理限制 `show-clock`，所有带顶栏导航的页面统一显示时间日期；时钟位置、样式与实时刷新逻辑不变 |
| 2026-11-02 | **补 §2.1 已实现域 + 登记 D-603 收口** | `stat.warning` 落地（分级预警：逾期天数 → 蓝/黄/橙/红等级 + 预警值 + 决策意见 + 预警记录；环节/阶段/总体三种口径；接口 `/land/stat/warning/*`）。入口两处：大屏端「查询统计 → 预警信息查询」（**只加页签、不占菜单**）、管理端「统计预警 → 预警等级维护 / 决策意见维护」（菜单与 4 个按钮权限由 `sql/stat/04_stat_warning_menu.sql` 建）。改这块前先读 `docs/模块实现说明/分级预警-实现说明.md`：**阶段/总体预警没有决策意见**（没有「问题类型」可套）、**`byLevel` 必须四档齐全并含 0**（缺一档前端四色卡跳色）、**`yjPercent` 已由后端乘过 100**（前端再乘会显示 11000%）。另有 6 处**刻意不照抄旧系统**的实现（工作日递归、并行判定、过期预警删除、决策意见兜底、问题类型存文本、唯一键去重），已登记为差异清单 D-616~D-621 —— 改回去就是制造 bug。测试：`mvn -pl jeecg-module-land -am test -Pit -Dtest="Warning*"` |
| 2026-11-05 | **合并远端 `f6c3b6e`（MVT 服务 / 空间导入 / 配套图形导入）＋ 补回 5 个被截断的文件** | 远端新增 MVT 瓦片服务（`map/mvt/*`）、SHP 空间导入（`spatial/*`，依赖 GeoTools 28.2 + osgeo 仓库）、地块/配套图形 GeoJSON 导入（`data/imports/facilitygeo/*`）；本地新增 `stat.warning` 分级预警，并把界面「变更履历 / 六阶段」统一为「操作记录 / 配套流程」（仅文案，不动逻辑）。**文本冲突 0 处**：两侧都改过的 7 个文件（`admin-client/scripts/verify-sfc.js`、`stargis-client-web/scripts/verify-sfc.js`、`FacilityAdminServiceImpl.java`、`FacilityEntry.vue`、`FacilityEntryPanel.vue`、`LandEntryPanel.vue`、`LandTable.vue`）都是自动合并，逐个人工核对过两侧改动都在。**但远端有 5 个文件被截断**（提交时没写完，尾部是半个标识符）：`admin-client/scripts/verify-sfc.js`（丢收尾 `Promise.all`，脚本本身语法错误）、`admin-client/…/FacilityEntry.vue` 与 `stargis-client-web/…/S3dmViewer.vue`（丢 `<style>` 尾部与 `</style>`）、`ScreenPopover.vue`（`</style>` 变成 `<`）、`admin-client/src/api/land/facilityAdmin.js`（默认导出少 3 个名字 + 配套图形导入两个函数整节丢失 → 管理端「导入地块图形」一点就 `TypeError`）。按「远端新增 + 共同祖先补齐」修复（`f265d82` → `0ab9605` → `75dd6a4`），并把本次新增的 10 个文件登记进两个 `verify-sfc.js` 清单。判据与三分钟排查法见 §3.10 |
| 2026-11-09 | **prod 配置全量占位符化 + 合并远端 `5fbad9f`（MVT 空间库参数）** | 本轮两件事。① 打包部署口径：`application-prod.yml` 里端口/上下文路径、库地址与库名、上传目录、中台 `ck.*`、`land.*` 全改成 `${TJ_XXX}` 占位符，真实值留 `server/.env`；业务行为对齐 dev（local 上传、200MB 上限、Druid maxActive 50、ES 不检查、knife4j Basic 关闭）；**补上模板整节缺失的 `ck.stargis.zk`** —— 不写这一节时 SDK 的 `matchIfMissing = true` 会让同步开关照样为 true 却去调空地址，表现是「保存用户直接失败」且日志看不出是配置问题。新增 `docs/deploy/`（nginx admin.web / gis.web 反代样例、static/config.js 样例），放仓库内才 push 得上去。② 合并冲突只有一个文件：`application-prod.yml` 的**顶层 `land:`** —— 两边都新增了同名顶层键，直接拼会变成重复键（YAML 后键覆盖前键，`land.escalation` 会静默消失）。按「同事的键全留 + 口令走占位符」收口：`pg-host/pg-port/pg-user/pg-password/temp-dir` 全部保留，`pg-password` 改成 `${TJ_PG_PASSWORD}`（原值明文已随 `5fbad9f` 进了公开仓库，**建议换掉口令**），`TJ_PG_*` 四个键同步补进 `.env.example` 与本文件键清单，否则新环境照模板复制会缺键、启动即占位符解析失败。合并后已用 js-yaml 校验（13 个顶层键、`land`/`ck` 各只出现一次），并按 §3.10 跑两个前端 `verify-sfc` |
| 2026-11-13 | **MVT 空间库管理行为明确化（移除可删库 / 重复导入显式选择）+ 修复登记端点不存在的静默 bug** | 用户反馈两个静默行为有坑，改动落在 `spatial` 域 + 两个前端文件。① **重复导入不再静默**：新增 `POST /land/spatial/precheck`（不上传文件，返回库是否存在/凭据可用/是否本系统导入/库内表清单），前端 ShpImportModal 提交先预检，库已存在时让用户在「连接导入（仅覆盖同名表）/ 覆盖重建（整库删除重建，仅限 `remark='SHP 上传入库'`）」间选择；服务端 `requireOwnImported` 二次校验，绕过前端直调接口也删不掉手工登记的生产库。**登记从 `INSERT IGNORE` 改为替换式（先删后插）**——旧行为下改密码重导会被静默忽略导致出图连不上；且不依赖唯一键防重（`land_mvt_source` 的 DDL 此前不在仓库、唯一键无法确认，现已补 `sql/map/02_mvt_source_table.sql`，幂等含条件 ALTER）。② **移除分两档**：`DELETE /land/spatial/source/{id}?dropDatabase=`，删库档先 `pg_terminate_backend` 断连再 `DROP DATABASE`（带退避重试，否则 Druid 池占住库报 "database is being accessed by other users"），**删库成功才清登记**防孤儿库；前端 MvtServiceList 换三重闸确认弹窗（选档 + 知情勾选 + 输入「删除」），中控数据源卡片不显示勾选框（本就删不掉）。③ **修复潜在 bug**：前端原调的 `/land/spatial/source/register` 在后端**从未存在**（被 `.catch` 静默吞掉，`sys_data_source` 从未登记成功过），登记收口到导入时服务端完成。④ 新增 `PgSupport.databaseExists/dropDatabase/listTables` 与预检探活超时变体（connectTimeout 8s，管理操作不能像导入那样容忍慢库）。测试：`PgSupportDatabaseTest`（真实 PG 生命周期，`SPATIAL_TEST_PG_*` 环境变量门控）+ `ShpImportServiceImplGuardTest`（防线纯单测，`jdbc()` 改 protected 供桩替换）。**部署**：执行 02 号 SQL + 重启 Java |
| 2026-11-13 | **MVT 对外服务基座 `MVT_PUBLIC_BASE_URL`** | 「MVT 空间表服务」页复制 TileJSON/XYZ 地址的基座从 `domianURL` 改为**优先取运行时配置 `window._CONFIG.MVT_PUBLIC_BASE_URL`**（`admin-client/public/static/config.js` 留空默认、`docs/deploy/static-config-admin.web.js` 样例填真实值），留空回退 `domianURL`，本地开发行为不变。原因：部署走 nginx 统一入口时 `VUE_APP_API_BASE_URL` 是 `location.origin + '/api'`——操作者用 hosts 别名（如 admin.web）打开页面，复制的瓦片链接外机解析不了。部署后该键必须填外机可达地址，复制出的链接才能被中台/地图客户端直接访问 |
| 2026-11-13 | **修复「全部」模式下 MVT 服务地址不可用** | 服务地址的连接编码此前取下拉框值 `this.source`——数据库下拉选「全部」时它是前端占位值 `__all__`，拼进 TileJSON/XYZ 地址后端按真实编码找库必然 404（只有选定具体库后复制的地址可用）。改为取行数据自带的 `sourceCode`（两种模式下每行都带），详情抽屉「数据库」字段同步改显 `selectedTable.sourceName`（原来「全部」模式下显示"全部"而非实际库名），并删除不再使用的 `sourceName` computed |
| 2026-11-13 | **排查记录：大屏定位报「未配置定位范围，请补充 bounds」≠ 范围问题** | 用户在大屏定位 MVT 图层报此错。经 JDBC 直查（临时工具 `工具/jdbc-diag/`，读 `.env` 连 MySQL/PG）实证：登记在、数据在（1140 行）、bounds 计算正常、线上 TileJSON 含 bounds——**根因是中台资源里配的旧地址已失效**（重新导入后连接编码由 `netzwk_jxyd_dksj` 变为 `netzwk_jyxyd_dksj`，逐字符之差），TileJSON 返回错误 JSON（HTTP 200），Cesium 解析不到 bounds 退回全球矩形才报这个错。排查口径写进《MVT空间表服务目录-使用说明.md》末节：先浏览器打开资源地址看是不是错误 JSON，再逐字符核对连接编码。**已知改进点（未实施，待用户确认）**：stargis-client-web 的 `createMvtImageryProvider` 在 TileJSON 拉取失败时应抛真实原因，而不是静默退回全球矩形让定位报误导性的 bounds 错 |
| 2026-11-13 | **导入自动创建空间索引（大表兼容性缺口）** | 用户问"以后导入更多数据都兼容吗"，排查发现两个导入引擎都**没建空间索引**（shp2pgsql 命令无 `-I`、geotools 无 CREATE INDEX）——小表无感，大表低层级瓦片全表扫描会撞 10 秒查询超时。修复：Shp2pgsqlEngine 命令加 `-I`（其生成的建索引 SQL 与建表/插数一起被引擎逐句执行、同事务提交）；GeotoolsEngine 在提交前 `CREATE INDEX IF NOT EXISTS {表}_geom_idx USING GIST ("SHAPE")` + `ANALYZE`。**存量表已补救**：临时工具读 land_mvt_source 登记连接，给 `地块0922`/`配套数据0922` 补建 `_geom_idx`（幂等 IF NOT EXISTS），工具用完即删。后端已编译验证（BUILD SUCCESS），生效需重启 Java |
| 2026-11-12 | **首页「排行」按市级 / 区级分开统计** | 用户反馈：大屏首页「出让地块情况」「落实配套情况」的排行把市级与区级混在一张榜单里。根因是后端 `ranks`（`/land/stat/home/landOverview`、`pendingFacility`）把两级放在同一结果集、`ORDER BY value DESC, name` 全局倒序（实测市级 10 行、区级 25 行交替），前端原样整表渲染 —— 榜首永远是区级项目，条形在拿两个量级不同的集合互相比较。改为**只动前端**：`index.vue` 新增 `rankGroups()` / `sortRankRows()`，`transferRank` / `supportingRank` 由 `Array` 变 `{ city, district }`（声明处用 `emptyRankGroups()` 兜底）；`HomeStatPanel.vue` 的排行视图跟随「市级 / 区级」双卡（双卡同时仍是明细表切换器），序号与条形按组内归一，表头补「（市级）/（区级）」，某级无数据显示「暂无数据」而**不**借用另一级榜单。接口、SQL、数据库均未改动（分组列仍是市级 `xzqh` / 区级 `xzqh2`，见 `docs/模块实现说明/首页看板-实现说明.md` §5/§7） |
| 2026-11-12 | **修后端启动失败：把 snakeyaml 覆盖到 1.30（§3.11）** | 用户报「后端启动报错 `ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 830` @ `snakeyaml StreamReader.peek`」。定性过程：`application-dev.yml` 是合法 UTF-8（无 BOM、无控制字符、严格解码通过），逐块拆开解析每个顶层块都正常；用 5 行 Java main 逐个试 yml 并对比版本，发现**同一份文件** snakeyaml 1.25 通过 / **1.29 报错** / 1.30、2.4、2.6 通过，Spring Boot 自己的 `YamlPropertySourceLoader`（2.6.6 + 1.29）同样复现 —— 是 **1.29 在 CRLF + 读缓冲边界上的解析缺陷**，与内容位置强相关（文件最前面加一个空行就能解析）。触发经过：14:27 给 `application-dev.yml` 的 `TJ_*` 占位符补注释、行数变了，正好把 CRLF 顶到边界。修法：`server/pom.xml` `<properties>` 加 `<snakeyaml.version>1.30</snakeyaml.version>`（1.30 = Spring Boot 2.7 默认版本，API 兼容），已用 `mvn -pl jeecg-module-system/jeecg-system-start dependency:tree "-Dincludes=org.yaml:snakeyaml"` 核对解析到 `1.30:compile`。**不要**用「加空行/删注释」绕：那只是把地雷推到下一次改 YAML。陷阱与三分钟定性办法见新增的 §3.11 |
| 2026-11-12 | **大屏三块面板：左右可拖宽 / 下方可拖高 + 三块都能折叠（§3.12）** | 用户要求：大屏所有「左/右/下」面板都能像数据管理列表面板那样拖动改尺寸，并像首页左右面板那样折叠。范围按用户确认收敛为**有三块面板的三个页面**：首页、地图管理、配套分析管理（数据管理已有下方面板拖高，档案/提级论证/查询统计只有左导航，不动）。新增共用件 `components/screen/panelLayout.js`（三页共用的尺寸/折叠状态，localStorage `screen-panel-layout-v1` 持久化 + `[min,max]` 夹紧 + 去抖落盘）、`ScreenSideRail`（由 `home/HomeSideRail` 泛化而来，原文件删除）、`ScreenBottomDock`（地图管理/配套分析管理底部「属性表」：默认 47px 收起条，点开是空态面板，可上沿拖高）；`ScreenResizeBar` 增加 `orientation="vertical"`（改宽，量**宽度**比值）与 `reverse`（右面板往左拖变宽），横向改高行为不变。首页 `HomeStatPanel` 增加 `width` 入参 + 内侧拖动条，内部列位/双卡/统计卡由固定 px 改百分比（面板变宽后列按比例摊开）；`HomeAttrPanel` 增加 `height` / `collapsed` 入参与上沿拖动条，折叠状态改由页面持有（原来在组件 data 里，切页即丢）；`HomeLayerTree` 与右面板统计共用同一份宽度/折叠状态。地图管理 / 配套分析管理两页的左右面板用「铺满画布的定位根 + 折叠时 translateX(面板宽+30)」整体滑出，宽度经 CSS 变量 `--panel-left-w` / `--panel-right-w` 下发（底图、标题装饰、拖动条、插件宿主、工具卡片一起跟随）；两个工具箱的卡片从 JS 绝对定位改 flex 换行（400 宽仍是设计稿三列，行距 11 = 设计稿步进 139 − 128），地图管理的卡片在 400 宽下默认零滚动。拖动期间 `body.is-resizing-panel` 关掉 320ms 高度过渡（否则面板追不上指针），折叠位移只在 collapsed 时下发 transform（避免给插件 fixed 浮层引入新包含块）。坑与排查见新增 §3.12。验证：`node perf/verify-panel-layout.js http://localhost:3001` **26/26**（真实拖拽 + 刷新持久化 + 0 条未捕获异常），截图 `perf/shots/panel-layout-{home,map,analysis}.png`；`perf/verify-data-layout.js` 复跑确认横向拖高未回归（同时修了该脚本两处过期断言：大屏标题「星际GIS」守卫、数据管理页签旧文案） |
| 2026-11-13 | **补两处用户反馈：蒙版随面板伸缩/折叠 + 数据管理左右面板可拖宽可折叠** | ① 用户截图反馈「面板收起了、左右和下面的黑色遮罩还留在原处」：三张 `.land-screen__vignette` 原来宽度/高度是设计稿定尺（560 / 286），只有首页左右两张在折叠时淡出。改为父组件 `views/screen/index.vue` 按当前页的 `vignetteLayout`（home / map / analysis / data 四份 panelLayout 单例，父组件取到的与子页面是同一个响应式对象）内联下发尺寸：左右宽 = 面板宽 + 160（设计稿 400→560）、下方高 = 下方面板高 − 35（321→286）、折叠 → 0，过渡 320ms，拖动期间用 `body.is-resizing-panel` 关掉；没有面板的档案 / 提级论证 / 查询统计不传 style，保持设计稿定尺。② 数据管理的左二级导航 / 右检索列补拖宽 + 折叠：`panelLayout` 新增 `data` 默认值（左/右 200、下限 160、上限 340；bottom 360 = 原列表默认高度，200~936），`data/index.vue` 输出 `--data-nav-w` / `--data-search-w` / `--data-nav-offset` 三个 CSS 变量并挂左/右拖动条与屏边把手；`LandEntryPanel` / `FacilityEntryPanel` 改读同一份 `usePanelLayout('data')`（检索列宽 / 折叠、列表高度取代各自持有的 `listHeight`），列表左右偏移跟着导航 / 检索列变，检索列只在两个分栏页签存在；数据管理下方面板仍只拖高、不折叠（没有设计收起条）。验证脚本扩到 **65/65**（新增蒙版逐像素核对、数据管理左右拖宽折叠、非分栏页签内容区左偏移、列表拖高与下蒙版联动），截图新增 `panel-layout-data.png`；`node scripts/verify-sfc.js` 109 文件与改动文件 eslint 均通过 |
| 2026-11-13 | **大屏数据管理两面板移除「进菜单自动铺 GeoJSON 图层」（用户要求）** | 经营性用地信息 / 配套项目信息进入时不再自动把全部已画图地块铺到地图上（地图保持干净）。改动：`LandEntryPanel.vue` / `FacilityEntryPanel.vue` 删除 `loadGeoLayer` 与概览缓存（geoOverviewCache / facilityGeoCache）、activated 钩子与全部写后重铺调用、`locateTargetId` 连点保护；`handleLocate` 改为**按需渲染**——双击才按 id 拉 `/geoDetail` 精确图形，`renderAll([单条], true)` 先清后画 + 高亮 + flyTo（单条实体量小，无需概览/精确两档切换），回不出 dkGeojson 时 toast 提示。「导入地块图形」入口保留（只落库不铺图）；写后只刷新列表。图层模块 `landGeoLayer.js` 本身不动（renderAll 支持任意列表，两档渲染能力保留，将来要恢复可从 git 历史找回）。概览接口 `/geoList` 后端保留但大屏已不调用。验证：eslint 无告警、verify-sfc 109 文件全过 |
| 2026-11-14 | **数据管理两面板改为「联动图层树」渲染切片图层（替代 GeoJSON 自动铺图）** | 用户在中台数据字典的切片类型加了「地块数据」「配套设施」两个数据（对应已发布的 MVT 服务），要求进这两个模块时**联动图层树**：没勾就自动勾选、图形走图层树渲染管线。实现：`data/index.vue` 隐藏挂载插件全局组件 `layer-tree-pop` + `mvt-layer-tree` 指令（fixed 甩出屏幕 + transform 形成包含块裁掉插件 fixed 弹层 + visibility 继承 + pointer-events none），`watch activeTab(immediate)` + `activated` 触发 `ensureSliceLayer(tab)`——在隐藏实例的 `treeData` 里找名为「地块数据 / 配套设施」的 MVT 节点（精确名优先、包含兜底，树数据插件异步拉取：500ms×24 重试），把节点 id 合并进 `checkedKeys`（**必须赋新数组引用**，已勾过也要触发指令 watch 重 sync），经 `manager.sync` 渲染到共享 viewer；切走不取消勾选（用户确认保持）。**两个关键坑**：① 指令的 `manager.sync` 是全量替换语义——联动前必须并上 `renderedMvtIds(viewer)`（新增导出）里当前已渲染的层，否则地图管理侧勾的层会被联动整批撤下；② 隐藏实例与地图管理弹层实例的 checkedKeys 各自独立，靠指令自身的 hydrate（弹层重挂载时从 manager.selectedIds 恢复）保持视觉一致。图层模块 `landGeoLayer.js` 与按需定位（上一轮改的 handleLocate）保留不动。验证：eslint 无告警、verify-sfc 109 文件全过。**已自动化端到端实测打通（2026-11-14）**：中台 `/app/oauth/tokenapply`（三重 Base64 multipart：userName/passWord 各 Base64 三次）+ jeecg `/sys/login`（captcha 固定 `cikian`，注释称后端不强制校验——实测**必须带**，不带报「验证码错误」412）拿到双平台 token，经 permission.js 的 query 注入 URL（`#/?access_token=..&sceneid=..&username=admin&refresh_token=..`，**hash 路由下 query 必须在 # 内**）注入登录态，进 `/screen/data?tab=land` 实测——隐藏树挂载 ✓、树数据加载 ✓（5 节点，「地块数据」「配套设施」精确匹配 ✓）、勾选后 MVT imagery layer 加入共享 viewer（`_mvtResourceId` = 地块数据节点 oldid）✓。**过程中发现并修/记三点**：① ensure 重试窗口 500ms×24=12s 太短（插件首拉树数据要 10~30 秒，窗口过了就永远不勾），改为 1s×90；② **中台里两个图层的 URL 是交叉的**——「地块数据」节点配的是配套数据的 TileJSON、「配套数据」节点配的是地块的 XYZ（用户中台配置时交错，前端按图层名匹配不受影响，需用户在中台对调）；③ ZCode 内置测试浏览器到局域网 IP 192.168.20.118 的连接挂起（同机 127.0.0.1 秒通、shell curl 局域网 IP 也通——浏览器进程特有），测试用 fetch 垫片（仅测试会话改写 127.0.0.1）完成验证，用户浏览器实测 9802 直连无此问题；token 临时文件已删除 |
| 2026-11-14 | **管理端不再提供 XYZ 形态地址（只发 TileJSON）** | 用户在图层树定位「配套数据」又报「未配置定位范围」——根因是中台里该节点配的是 XYZ 形态地址（且与地块的地址交叉）：XYZ 模板无 bounds，渲染能出图但「定位」必报错。管理端 MVT 服务页**详情抽屉撤掉「XYZ 瓦片地址」块、删除 `tileUrl`**，只发 TileJSON（自带 bounds，定位/起始层级优化自动生效）；需要 XYZ 时在 TileJSON 后拼 `/{z}/{x}/{y}.pbf`（代码注释与使用说明都写明）。同时把「中台资源配置贴 TileJSON 不贴 XYZ」写进使用说明的地址格式节。注意区分中台两处配置：**数据字典的切片类型**只是类型清单，图层树节点的**服务地址**在场景/图层树配置里——用户第一次改字典没改到节点地址，树数据（进页面时拉取）实测仍是旧 URL，改完需刷新大屏页面。**首进不渲染的两个真 bug（2026-11-14 端到端实测定位并修复，两模块联动渲染全通）**：① `ScreenHiddenLayerTree.vue` 的 `registerLayerTreePop` 误从**指令模块**导入（该函数在 `layerTreeBridge.js`）→ 命名导出不存在 → undefined → 注册静默失败（setInterval 里抛错被吞）→ 桥的实例永远 null——eslint/编译都查不出这种「错误模块的同名导入」；② `watch activeTab` 的 `immediate` 处理器在 `created()` **之前**执行，`_sliceLayerTimers`（created 里才赋值）当时还是 undefined → 首次联动调用 TypeError → 重试循环从未启动。修复：导入改指 bridge + `ensureSliceLayer` 入口兜底初始化 + 循环判定改为「viewer 上真出现该资源 id 的影像层」（每秒断言，插件重置勾选就自动再勾，120s 预算，自愈）。实测：land 首进即渲染地块数据层、切 facility 配套数据层加入且已有层保留。**联动匹配口径改为「关键词」而非图层名**（用户要求零配置零写死）：`SLICE_LAYER_TARGETS` 每模块给关键词列表（land=['地块数据',连接编码 netzwk_jyxyd_dksj]、facility=['配套数据','配套设施',连接编码 netzwk_jyxyd_ptsj]），匹配字段=图层名+url+proxyparams+customproperty 拼接串——**中台切片类型新加的图层只要发布自对应库（url 含连接编码）就自动联动，图层名随便起**；图层名关键词保留为兜底。注意：中台 URL 交叉未修正时（节点 url 含对侧编码）每个节点会命中两个模块——属过渡态，中台对调后即干净。**【联动口径终版】改为「切片类型 tiletype」**（用户明确：中台资源配置的切片类型就是联动依据，库名/图层名以后都会变，连接编码/图层名方案全部废弃）：树节点自带 `tiletype` 字段（切片类型字典值，实测地块数据=4、配套数据=5），`checkSliceLayer` 按 `tiletype ∈ 类型映射` 圈定目标节点；映射 = localStorage（`screen-slice-type-map`）持久化 + **名字兜底自学习**（`SLICE_TYPE_BOOTSTRAP` 名字命中的节点，其 tiletype 自动记进映射并持久化——字典重建换值/新类型都会被重新学会）+ 开箱默认值（4/5）。实测：刷新后 land/facility 两页签的图层均自动勾选渲染 ✓。**【定位改造】列表双击定位改为「相机飞行到 MVT 切图层上的该宗地」**（摆脱 GeoJSON 覆盖层渲染）：`handleLocate` 只按 id 拉该条 dkGeojson 计算范围（`polygonBounds`，geoPolygons 新增导出，含经纬度合理区间校验）+ `camera.flyTo(Rectangle)`，不创建任何实体；无图形的行 toast 提示不变。两面板的 createGeoLayer/landGeoLayer 依赖全部移除（模块文件保留备查）。⚠ 踩坑教训：`polygonBounds` 曾「计划添加但未真正写入源码」→ 导入 undefined → TypeError 被 handleLocate 的空 catch 吞掉 → 定位静默无效——**新增导出必须落地后立刻构建验证，不能只写在计划里**。实测：handleLocate 后相机从 400km 高空精确飞至该宗地低空（567m）✓。**【二次改造】定位数据源彻底摆脱 dk_geojson**（用户确认字段将删除、全面走 MVT）：列表双击 → 面板 emit `locate-record` → DataScreen `handleLocateRecord` 按页签取唯一值（地块=crzdbh、配套=ptxxmc，实测切片源表属性：地块0922 带 crzdbh、配套数据0922 只有 ptxxmmc 无编号字段）→ 新后端接口 `GET /land/spatial/sliceFeatureBounds`（land_mvt_source 取连接 → ST_Extent(ST_Transform(geom,4326)) WHERE 唯一值字段=值，值 PreparedStatement 参数化防注入，精确未命中回退包含）→ camera.flyTo。切片源信息（连接编码/表名）从壳层隐藏图层树的树数据动态解析（url 含 /land/mvt/public/{code}/{schema}/{table}/{geom}），中台改库名/表名零配置跟随。面板 emit 后 createGeoLayer/landGeoLayer 依赖全部移除（模块文件保留）。**踩坑两个**：① FQN 混用漏 import（ResultSet 用了裸名）→ 编译期暴露——新增代码里的 java.sql 类型必须与文件既有风格一致（FQN 或 import 二选一别混）；② psql 客户端编码错配（控制台 GBK vs PGCLIENTENCODING=UTF8）导致中文表名查询连环假失败（字段不存在/无效编码字节序）——SQL 落 UTF-8 文件用 `-f` 执行才可靠。**待办**：用户重启后端（IDE）后 E2E 复核定位；dk_geojson 字段删除由用户执行（DDL：ALTER TABLE t_land/t_supporting_facilities DROP COLUMN dk_geojson；删前须同步撤大屏「添加地块」绘制与两个 GeoJSON 导入入口、并调整 Land/Facility 保存链路的 dk_geojson 写入） |
| 2026-11-14 | **联动图层树两模块实测全通 + 修两个首进不渲染的坑** | 浏览器自动化端到端：land 页签 → 「地块数据」MVT 层渲染进共享 viewer（_mvtResourceId=320a7be3）；facility 页签 → 「配套数据」MVT 层渲染（5cc12193），且已有层不被撤下（合并语义 ✓）。修的两个坑：① **节点名以中台实际配置为准**——图层树节点叫「配套数据」，与字典名「配套设施」不一致，按字典名匹配永远勾不上 → `SLICE_LAYER_TARGETS` 改为候选名列表（facility=['配套数据','配套设施']）逐个匹配；② **插件在树数据异步加载完成后会重置勾选**——只盯「赋值成功」会出现「第二次进菜单才渲染」（第一次勾上的 id 被插件清掉），循环判定改为「viewer 上真出现该资源 id 的影像层」：每秒断言，被重置就再勾，直到渲染出来（自愈），重试预算 120s。**口径注意**：插件每次加载树会重新生成节点 id（checkedKeys 存 node.id、资源按 oldid 键控），所以按**图层名**匹配节点、按 **oldid** 判渲染才是稳定口径，跨会话比对 id 全是坑 |
| 2026-11-15 | **大屏图形编辑上线：新增/编辑/删除记录时同步切片源表（几何权威从 dk_geojson 迁到 PostGIS）** | 用户定稿交互（沿用现有弹窗，不另起炉灶）：表单「添加地块」升级为「管理地块图形」入口 → 新组件 `FeatureGroupModal`（Land/Facility 共用）——回显该记录名下**全部小块**（切片源表按唯一值精确匹配：地块 crzdbh / 配套 ptxxmmc），每块可**定位**（前端按该块几何 flyTo，比整组合并范围准）/ **移除**（弹窗内暂存可反悔）/ 继续绘制（复用 pickSession 令牌会话与 drawPolygon 画笔）；**弹窗无独立保存**，改动暂存、随表单「保存」一并落库。**一对多是数据常态**（实测地块0922：1140 行 / 833 唯一 / 145 组重复 max11；配套数据0922：1640 行 / 338 组 max18——一宗多块、一项目多点位），故保存语义=**整组替换**（事务内先删该唯一值全部旧行再插入当前组，空组=显式清空）→ **不需要行主键、源表零改动**，这是弹窗暂存模式最大的架构收益。后端三接口 `GET/POST/DELETE /land/spatial/featureGroup`（land_mvt_source 取连接 → geometry_columns 动态取几何列+SRID → ST_Transform(ST_GeomFromGeoJSON,4326→srid)；回显只走精确匹配，与定位接口的 LIKE 回退刻意区分——写路径的读不能把别家图形拉进编辑弹窗）；precheck 新增 `writable`（has_database_privilege CREATE 探测，只读账号录入前暴露）。配套改造：`data/sliceInfo.js` 收敛切片源解析/类型映射/唯一值字段（index.vue 瘦身引用，中台换库换表一处跟随全模块）+ `reloadModuleLayers`（撤下→500ms 勾回，绕开瓦片缓存）；`purgeFeatureGroups` 移除记录联动清图形（fire-and-forget 不阻断软删）；编辑改编号/名称先 DELETE 旧值组再 POST 新值组（防孤儿图形）；dk_geojson 保存恒传 null（跳过列），过渡期「切片库没有→回退旧字段回显（标 legacy，保存即迁移）」双保险。**自测发现并修掉两个真 bug**：① FeatureGroupModal 的 `$bus.$on(this.pickFinishedEvent, …)` 把**方法引用**当事件名传（漏调用括号）→ 事件永远收不到；② 防清库闸门 `geoPartsLoaded`：切片源未就绪/回显失败时空组不得当「主动清空」提交（否则一次保存把切片库已有图形整组删光），只有管理弹窗「完成」交回的组才视为真实意图。验证：mvn compile + 单测 7+1 全过；eslint 全过；verify-sfc 109 文件全过。**待办**：后端未重启（22:04 旧进程缺 sliceFeatureBounds/featureGroup 全部新端点，探针 404）——重启后冒烟验证 + 删 tokens 临时文件；LandGeoImportModal 等 GeoJSON 导入入口的去留待用户拍板 |
| 2026-11-16 | **拾取回查业务库：配套列名拼写修复 + 折叠匹配** | 拾取配套设施后回查业务库一直失败回退切片属性的病根：前端取唯一键的属性名与后端 like 列名是「ptxxmc」拼法，而 MySQL t_supporting_facilities 与 PG 切片表的真实列名是「ptxmmc」（hex 7074786d6d63，information_schema + 实连 SELECT 双重机器终审）——错误拼写在两库都不存在，SQL 报 Unknown column；且 uniqueKey 取到空时会退化成无条件分页查询、显示随机第一条业务记录（即「显示内容不一致」）。修复：map/index.vue 拾取 uniqueKey 属性名、FacilityAdminServiceImpl 的 srr like 与 foldExpr 列名全部改为真实拼写（node 按 hex 机器替换，肉眼零参与，全仓字节扫描清零）；同时给拾取回查加 srrMode=fold 标点折叠匹配（—/–/－/～/全角空格/全角括号折叠成统一字符，SQL 侧 REPLACE+UPPER 与 Java 侧同一套规则，管理端列表不传该参数行为不变），配套双库可匹配率 88.8%→93.9%（delFlag=0 口径，JDBC 实测捞回 52 条），地块 crzdbh like 包含语义实测 96.6% 无需改动。教训：相似列名肉眼无法区分，一律按字节扫描比对 |
| 2026-11-16 | **拾取面板四连修：去闪烁、全字段含空值、隐藏系统列、配套名称标签修复** | ① 拾中要素不再先用切片属性占位（用户不要「闪一下 shp 属性」）：面板先空等业务库回查，未命中/查询失败才回退切片属性，回退内容同样按标签映射+排除清单渲染（buildSliceAttrRows）；② 业务记录全字段展示，去掉「非空才显示」过滤——空值也显示字段名，每条记录字段数一致（解决「有的属性多有的属性少」）；③ 排除清单补 sourceId/createBy/createAccount；④ 配套项目名称标签映射键是第三种拼法（7 字母变体，比真值多一个 x），实体属性名（=JSON 键）以 Facility.java 为准（hex 7074786d6d63），标签键改真值后「配套项目名称」正确显示；标签查找加归一化兜底（小写去下划线，覆盖 shapeLe↔shape_le 驼峰差异）。全仓扫描：7 字母变体 10 处（2 处代码 8 处注释）全部机器替换清零。ESLint --no-ignore 解析 0 错误 |
| 2026-11-16 | **拾取面板空态规则：去掉设计稿演示数据与切片属性回退** | 「闪一下 shp 属性」「默认值」的真身：MapPickPanel 里写死的高保真演示数据（OID/FID/CRZDBH/图层001 一组假 shp 属性），面板只要没有真实结果就显示它。按用户规则改为：默认/未拾中/业务库未命中/查询失败一律完全空白（连当前图层名也清掉），只有拾到业务记录才显示内容；顺带唯一键改不分大小写匹配（shp 追加导入列名是大写 CRZDBH，之前小写取不到键），字段名统一取 sliceInfo.SLICE_UNIQUE_FIELDS（map/index.vue 不再手抄拼写） |
| 2026-11-16 | **拾取面板：标签字典对齐数据管理 + 图形大列驼峰拦截 + 悬浮全文** ① LAND/FACILITY_FIELD_LABELS 两份手写映射整表替换为机器生成版——内容提取自后端 fieldconfig/DataFieldLabels.java（数据管理页共用的中文字典，land 34 + facility 58 条，附加 shape_le/shape_ar 几何统计列），键从后端文件字节直取（含配套项目名称条目），拼音字段不再裸奔；② 排除清单改归一化比对（小写去下划线）：业务 JSON 键是实体驼峰属性名（dkGeojson/dkGeojsonOverview），与清单列名拼写（dk_geojson）不同体系，精确匹配拦不住——归一化后两个图形大列正确隐藏；③ 字段名/字段值/当前图层三处省略号截断内容加原生 title 悬浮显示全文 |
| 2026-11-16 | **拾取查询混合模式：MVT 优先 + 原有拾取兜底** 此前纯前端化后插件 QueryPick 引擎不再激活，非 MVT 图层（S3M/实体标绘/矢量要素）的拾取断了。现改为混合模式：点击先走 MVT pickFeatures（含层级重试），未拾中再回退 scene.pick 原有拾取——Entity.properties(PropertyBag.getValue)/要素 getProperty/裸 attributes 三路取属性；带宗地编号/项目名称唯一键的与 MVT 同口径回查业务库（类型按命中键判定），无唯一键展示要素自身属性（排除清单归一化 + 标签自动匹配地块/配套字典）；拾取高亮图形本身排除，防止高亮范围内的点击拾中高亮自己；拾到但无可展示属性（如倾斜摄影模型）按未拾取处理 |
| 2026-11-16 | **拾取查询架构纠正：恢复插件引擎，MVT 改为叠加而非替代** 用户指出拾取查询是通用功能（建筑/管线/一切可拾对象），MVT 只是补充。此前纯前端化 （不再激活 QueryPick 引擎 + 手写 scene.pick 兜底）导致原有拾取全断——手写三路取属性 对本引擎 S3M 模型不适用（拾中建筑取不到属性按未拾中处理）。现恢复原有插件引擎激活 （togglePick 调 engine.btnClick，结果经总线 showPickQueryPop 进右面板，原有功能原样），probe LEFT_CLICK 里加 3D 命中闸门：拾中 3D 对象（S3M/实体/管线）→ 插件结果为准只补图层名；未拾中（纯影像区域）→ 才走 MVT pickFeatures + 业务库回查；拾取高亮图形排除防自拾；删除手写 pickLegacyFeature 兜底 |
| 2026-11-16 | **混合拾取两个时序 bug 修复：面板复位挪到闸门后 + 闸门改为插件结果优先** 用户实测管线拾取仍走 MVT 提示。根因有二：① probe 处理器在闸门**之前**执行 pickRows/pickLayer 复位，把插件同一击刚经总线发进面板的结果抹掉；② 闸门只看 scene.pick 判 3D 命中，而插件实际拾中与否应以它自己经总线抛的判定为准。现闸门改为「插件 payload 非空 rows 或 scene.pick 拾中 3D 对象 → 保留插件结果只补图层名」，面板复位挪到闸门之后（仅纯影像区域才清空并走 MVT）；未拾中提示改中性文案（该位置没有可拾取的要素） |
| 2026-11-16 | **拾取不到不再弹提示** | 用户反馈没拾到时弹 warning 像报错。去掉拾取流程里两处「没拾到」弹窗（纯影像区域未拾中 / 点击天空视境外），一律静默保持空面板；真正的服务异常 error 提示保留 |
| 2026-11-16 | **空间查询「获取所属行政区配置信息失败」提示静默** 提示源自 stargis-function 插件的空间查询引擎 loadCountyData：激活时 getAction 读 GIS 功能配置的「所属行政区」数据源，返回空即弹 warning。大屏未配置该数据源属正常形态，按用户要求在 main.js 给 antd message.warning 挂精确文本过滤器（只拦这一条，其余提示照常）；根因治理：管理端 GIS 功能配置配好空间查询的行政区数据源后功能可用、提示自然消失 |
| 2026-11-16 | **三个图层树实例联动（会话勾选意图）** 首页页签「地图管理」/ 地图管理「图层管理」/ 壳层隐藏实例是同一个插件组件 layer-tree-pop 的三个独立实例：地图场景共享（显隐即时生效），但勾选框各实例自己拉树自己持有，服务端 getSceneAndTree 返回配置态不含运行时勾改 → 在首页关掉的图层进地图管理又显示勾上。layerTreeDirective 增加 sessionChecked（nodeId→最后用户意图）：每次 sync 把 checkedKeys 增量写进会话表，新实例水合时按会话意图纠正勾选框（会话意图优先、已渲染 MVT 恢复其次仅加不减），三树联动。不触碰插件内部，非 MVT 图层勾选框与场景实际显隐保持一致 |
| 2026-11-16 | **行政区配置提示静默补丁二次修正：包 Vue 原型 /** 第一版只包了 import 进来的 antd message 对象没拦住——插件经组件实例 this. 调用、走 Vue 原型链，且安装顺序可能覆盖原型指向。改为在所有 Vue.use 之后直接包 Vue.prototype. / （函数本体 + success/error/info/warning/warn/open/loading 全部展示方法按精确文本过滤），其余提示不受影响 |
| 2026-11-16 | **插件结果表停靠底部属性表抽屉** 空间/属性/综合查询的属性表浮窗（插件 .pop.bottom-table.info-table，自带标题/导出/关闭）原本弹在右上，用户要求进底部「属性表」抽屉。实现：map/index.vue 轮询 300ms 探测浮窗显隐（插件 v-show 切 display，无内部事件可拿），出现 → 抽屉自动展开（记住之前折叠态）+ 页面根加 is-attr-grid-open，消失 → 恢复折叠态；文件尾非 scoped 样式把浮窗 fixed 钉进抽屉体区域（居中、宽度 min(920px, 100vw-120px)、高度 clamp 到抽屉高-52px，CSS 变量 --attr-grid-dock-h 跟随抽屉拖高）；浮窗打开期间隐藏抽屉自身的标题与空态占位（浮窗自带标题） |
| 2026-11-16 | **结果表停靠翻车修复：fixed 钉位改 DOM 收养** 第一版用 position:fixed + 100vw 钉位——大屏舞台带 transform 缩放，fixed 的包含块被劫持到舞台/面板，叠加插件内联 transform 后表格飞到顶部横贯全屏。改为把浮窗 DOM （.pop.bottom-table.info-table）appendChild 收进抽屉根节点（只搬一次、保留事件监听与 Vue 引用，插件 v-show 显隐/导出/关闭/翻页继续生效），CSS 让它 absolute 铺满抽屉内容区、抽屉折叠时随抽屉隐藏；停靠判定改看插件 v-show 内联 display（排除抽屉折叠的干扰） |
| 2026-11-16 | **结果表去插件头部，直接嵌入属性面板** 用户要求去掉插件自带的「>> 属性表 / 导出 / 关闭」头部，直接嵌入现有底部属性面板。CSS 隐藏停靠表格的 .bottom-table-title 头部，表格从抽屉原生标题栏下方铺满内容区（top: 36px），关闭 = 抽屉折叠钮；抽屉空态占位继续隐藏，原生标题栏保留 |
| 2026-11-16 | **结果表停靠 v4：内部布局约束（flex 列 + 裁剪）** 第三版表格进抽屉后分页条溢出到抽屉框外——根因：插件内容（表格+分页）原尺寸超出抽屉盒且无 overflow。停靠节点加 display:flex 纵列 + overflow:hidden；标题（已隐藏）外的第 2 个子节点（表体）flex:1 + min-height:0 + 内部滚动，第 3+ 子节点（分页条）固定贴底 |

| 2026-11-16 | **结果表停靠 v5：height 归零 + 抽屉级裁剪兜底** 第四版仍溢出——插件常给浮窗设内联固定高度，绝对定位同时有 top/bottom/height 时 height 赢、bottom 被忽略，盒子按插件高度撑出抽屉框。停靠节点补 height:auto/min-height:0；另加抽屉根 overflow:hidden 兜底（任何子内容不允许画到抽屉框外） |

| 2026-11-16 | **结果表停靠 v6（终版）：文档流嵌入抽屉滚动区** v4/v5 与插件内部结构搏斗（flex/height/裁剪）仍不稳。改为零结构假设：浮窗以文档流 （position:static）嵌入抽屉滚动内容区 .screen-bottom-dock__body（自带 overflow-y:auto），表格+分页条按插件原样渲染、多高都靠抽屉滚动，分页条永远在表格下方可达；抽屉折叠时内容区被 v-if 摘掉，浮窗暂寄抽屉根并用 CSS 隐藏（直接子代选择器），展开后下一轮询周期搬回内容区；插件头部继续隐藏，原生标题栏承载 |

| 2026-11-16 | **结果表停靠 v7（终版）：读插件原始 CSS 后按它的定位语义覆盖** 从 node_modules/stargis-function/stargis-function.css 拿到全量规则：.system-theme1 .bottom-table { position:absolute; bottom:-80px; min-height:340px; min-width:calc(100%-400px) }——插件本来就靠绝对定位+故意下探，v6 改 static 拆了定位上下文导致内层碎裂。终版：保留 absolute 定位模式（内部布局不散），只覆盖坐标到抽屉内容区并把 min-width/min-height/height 归零、内容整体 overflow:auto 滚动；轮询改 querySelectorAll 搬运所有匹配节点（防插件重建新节点残留原位） |

| 2026-11-16 | **结果表停靠 v8：分页贴底 + 主题配色对齐大屏** v7 布局压住了溢出但分页被表体挤出可视区（antd 固定表头结构里表体吃满高度）。按 antd v1 真实结构（ant-table > ant-table-content > ant-table-scroll > ant-table-body）逐层 flex：表体内部滚动、分页条 flex:0 贴底（弹层 overflow:auto 兜底防分页非直接子级）；同时按大屏主题重刷表格配色——表头深蓝 rgba(9,36,60,.92)、字 #e6f4ff、青色分割线、正文 #9fd4f5、斑马行、分页青色 #82c6ff/选中 #00e5c9，替代插件老蓝绿 #09636e/#002422 |

| 2026-11-16 | **结果表停靠 v9：espree 解剖插件渲染函数，分页真身找到** 用项目内 espree 语法级提取 + 字符串表解码器求值成功，反混淆插件渲染函数：结果表是 a-table 带 scroll:{y:220,x:...} + **内置 pagination**（.ant-pagination 渲染在 .ant-table 内底部），列多时走固定列双解剖 .ant-table-body-outer > .ant-table-body-inner——v8 弹性链漏了这层导致分页被挤出。CSS 按 antd v1 全解剖逐层 flex（content/scroll/header/body/body-outer/body-inner），内联 max-height:220 归零，分页 ant-table-pagination 贴底。解码还证实行政区提示走 this.$message.warn（已在前置补丁覆盖） |

| 2026-11-16 | **结果表停靠 v10：a-table 根是 ant-table-wrapper，弹性链从根接通** v9 分页出来了但表格塌没——antd-vue 的 a-table 根元素是 .ant-table-wrapper（弹层直接子级），.ant-table 在 ant-spin-nested-loading/ant-spin-container 两级包裹之下；v9 的 > .ant-table 没接上根，后代 flex 链无参照全塌成 0 高。补 wrapper→spin 两级弹性透传，.ant-spin-container 作为 flex 列：.ant-table(flex:1 表体内部滚动) + .ant-table-pagination(flex:0 贴底)——数据行与分页同时可见 |

| 2026-11-16 | **配置提示静默升级为家族匹配（街道变体）** 用户实测又弹「获取所属街道配置信息失败」——插件按层级（行政区/街道/…）各有一套同类提示，逐条精确匹配追不完。静默口径升级为按共同后缀「配置信息失败」家族匹配 （AntdMessage.warning/warn 双入口 + Vue.prototype.$message/$notification 原型包装同步），新增层级自动覆盖；其余 warning 照常 |

| 2026-11-16 | **回归修复：warn 包装缺存在性防御 + 属性查询恢复插件面板** ① main.js 的 AntdMessage.warn 包装没做存在性防御——部分 antd 版本 message 上无 warn，包装后插件 $message.warn 调用变 TypeError，打断空间查询选中绘制、属性查询配置加载等整条流程。修复：先补 warn 别名（warning 兜底）再包装，_wrapWarn/_wrapApi 均做存在性防御 + 原型链取值（Object.keys 漏继承方法）；② toggleAttr 曾改为直接进拾取模式，按用户要求恢复原始插件属性查询面板（配置校验 + 引擎激活 + 右侧功能区），确认模板接线完好 |

| 2026-11-16 | **四个查询工具卡片高亮修复（存量 key 错位）** 空间/属性/综合查询/综合统计的卡片 key（querySpatial/queryAttr/queryCombined/statistics）与父容器 activeTool（spatial/attr/comp/stat）不一致——面板能开但卡片永不高亮（HEAD 时代就存在的存量 bug）。MapToolPanel 增加 ACTIVE_KEY_OF 映射，isCardActive 按映射比对，不改父容器 |

| 2026-11-16 | **批量导入：批次号留痕 + 「导出本批变更」 + 录入单位/录入人选填** | 一条线：导入入库后要能回答「这一批到底改了哪些字段的什么值」。① **批次号**：`LandImportResultVO` / `FacilityImportResultVO` 新增 `batchId`（确认导入时生成），`t_land_import_log.id` / `t_facility_import_log.id` 改用它 —— 业务写入、`t_data_change_log`、导入日志、导出四处共用同一个号，前端 `hasBatchChanges` 也靠它决定出不出导出按钮。② **导入留痕**：`write()` 里 INSERT 与 UPDATE 都收日志（UPDATE 先用 `BeanUtils.copyProperties` 复制快照再就地改，避免「改完再回查库」的 N+1），`ChangeLogSupport.collector(...)` 抽出 **`buildRecord`（只构建、不落库）**，`save()` 改为调它 + `insert`（单条写入行为不变），末尾 `flushImportChangeLogs` 每 200 条 `batchInsert` —— **留痕与业务同一事务**，批量写异常一起回滚，不留「数据已改但无法追溯」的半批。摘要固定为「批次号：xxx，Excel 第 N 行，操作人：…」，导出靠它取行号。③ **`GET /land/data/import/changeExport`、`/land/data/facilityImport/changeExport`**：新增 `DataChangeLogExportServiceImpl.buildBatchExport(batchId)`，数据源**只有 `t_data_change_log`**（`selectByBatch` 按 `change_summary LIKE '%批次号%'` 取、按时间排），逐字段展开 `change_detail`；**不拿业务表最终值拼导出** —— 否则「本批变更」会变成「当前值」，与日志对不上（`LandImportTest` 拿同一条 diff 双验日志与导出）。④ **`lrdw` / `lrr` 改选填**（`lxdh` 仍必填并校验手机号，模板第 2 页填表说明同步改口径，管理端提示文案同步）：旧系统这 5 列全必填，现按测试口径只留编号 / 项目分类 / 联系电话；**审计列 `create_by` / `update_by` 仍取当前登录账号，绝不用登录信息覆盖原始填报字段**（`blankEntryUnitAndRecorderAllowedAndAuditUsesLoginUser` 锁这条，断言原始 `lrdw` / `lrr` 为 null 而 `create_by` 是登录账号）。测试：`LandImportTest` 补批次留痕 / 导出断言（空单元格不清原值，也不得写成字段 diff），新增 `FacilityImportTest`（配套侧同口径） |
| 2026-11-16 | **配套新增「功能区」独立列 + 建设性质/道路等级补值 + 三处交互修复** | ① **功能区 `gnq`**：`t_supporting_facilities` 加 `gnq VARCHAR(50)`（`sql/data/14_facility_gnq_and_dict.sql`，幂等加列 + 核对查询），实体 / `DataFieldLabels` / 导入字段与 `applyValue` / 两端表单与详情 / 大屏 `constants.js` 的 `FUNCTION_AREAS` 全部接线。**口径**：`xzqh` 只留 16 个行政区，生态城 / 土地发展中心 / 经开区 / 高新区 / 保税区是**功能区、不是第 17 个区**；脚本只迁移这 5 类已确认写法（`gnq = xzqh`、`xzqh = '滨海新区'`），未知值原样保留、**不归「其他」**（归了就丢业务事实）。② 建设性质补「现状」、道路等级补「快速路」—— 原来「快速路」是被刻意排除的（`DLDJ_OPTIONS` 上那段「不硬归位」的注释），业务确认要选，注释与选项同步改。③ **回收站 keep-alive 不刷新**：管理端 `DataRecycle.vue` 与数据管理面板 `DataRecyclePanel.vue` 的首次加载从 `mounted` / `created` 移到 `activated`（首次进入同样触发），避免「移除后再回来看到旧快照」。④ **大屏双层 fixed 弹窗互相截获点击**：`LandDetailModal` / `FacilityDetailModal` 补 `close()`，两个 entry panel 在「编辑 / 打开流程管理 / 按编号查配套下钻」前先 `detailModal.close()`，`FacilityDetailModal.handleOpenProcess` 自己也先 `handleClose()` —— 两层整屏遮罩叠着时下层按钮点不动。⑤ **履历编号兜底**：`LandAdminServiceImpl.queryHistory` 的 `bizKey` 在旧日志漏存时取当前宗地编号，表格不再整列空白（`DataManagementTest` 有断言，含「先把日志 biz_key 置空再查」的模拟）。验证：`node scripts/verify-sfc.js` 管理端 59 / 大屏 109 文件通过，改动文件 eslint（`--no-ignore`）通过；后端 `mvn -o -pl jeecg-module-land -am -DskipTests compile` 通过 |
| 2026-11-16 | **补完 P1-05 的查询侧功能区 + 附件/预警/拾取/文案四处清单项** | 承接上一条「功能区」只做了录入与历史迁移：① **查询统计侧补 `gnq`**——`QueryFieldRegistry` 登记（可选列 / 导出列 / 高级查询条件同一来源，默认列仍 7 列）、`selectFacilityRows` / `selectFacilitiesByLand` / `selectFacilityDetailById` 带出 `gnq`、`facilityWhere` 支持 `gnq IN (...)`、`/stat/query/options` 返回库里实际出现的功能区值、大屏「用地配套项目查询」加「功能区」下拉。**不补这一半，202 条功能区记录就是「录得进、查不到、导不出」**（`StatQueryTest#gnqIsQueryableAndSeparateFromXzqh` 锁死：按 `gnq` 命中 1 条、按 `xzqh=生态城` 命中 0 条）。② **回收站刷新不再只靠 `activated`**——两端改 `mounted + activated` 共用 `ensureLoaded()`（同 tick 去重）；只写 `activated` 的话，某环境菜单一旦关掉 `keep_alive`（`sys_permission` 上那个开关），页面会整页空白且不报错。③ **附件查询**——树接口只收 `bizType`，把「树表达不了的条件」收成 `FLAT_ONLY_FIELDS` 清单，点查询 / 下钻 / 深链首帧时自动切「平铺列表」+ 一次 toast 说明；删除后改刷**当前视图**（树重拉即同步项目计数 / 文件数 / 大小），不再固定刷平铺列表；管理端同构查询缺陷同修（其删除路径原本就对）。④ **地块预警每页条数**——原生 `select` 三档（10 / 20 / 50）+ 切档回第 1 页 + `pageCount` 变化时夹回末页，行区域改 484px 滚动视口、画布按行数撑高；**顺带修正行起点 `ROW_TOP` 118→46**（旧值用的是弹窗坐标系，导致第 9/10 行被 `overflow:hidden` 长期裁掉、首行下方还空 82px），并把 `HomePlotModal.vue` 登记进 `verify-sfc.js`（它原来在自检清单外）。⑤ **拾取查询去掉演示数据回退**——原来未操作就显示 `OID 153 / 津武（挂）2023-014` 这类假属性，展厅里容易被当成真实地块数据；改空态「请在地图上拾取要素」+ 图层名占位「—」。⑥ **配套绘制文案**按 `ptsslb` 显示「配套道路 / 配套设施范围」（按钮 / ARIA / 状态行 / 绘制标题 / 成功与空态 toast 六处），`dkGeojson` 与接口参数未动。验证：`StatQueryTest` **19/19**；全量 `-Pit` **313 用例 / 8 失败**（全是既有数据基线快照与 MVT 性能断言，与本次无关）；`verify-sfc` 大屏 **110**（新增登记）/ 管理端 59；改动文件 eslint（`--no-ignore`）通过，`git diff --check` 干净 |
| 2026-11-16 | **测试清单二次整改（23 条）+ 三项数据迁移** | 按《系统测试问题分级整改清单》把剩余 23 条一次做完（P0-04 管理端、P1-02/04/05/06/08~15、P2-01/02/03/05/07/08/09/10/11/12）。**三条必须先知道的约定**：① **材料类型已改成「宗地 4 类 / 配套 13 类」**（`LandAttachmentServiceImpl` + `land_attach_type` / `land_facility_attach_type` 字典 + 两端常量），宗地侧**不再有「配套方案」**，历史那 23 条进了 `99 其他（历史）`（只读回显、不进上传下拉）；改附件类型前先读 `sql/data/15_attachment_type_remap.sql` 头部的映射表。② **`t_data_migration_marker` 是本项目第一张迁移台账表**（`code varchar(64)` 主键 + `applied_time` + `remark`），新增数据迁移脚本一律「UPDATE 带 `WHERE NOT EXISTS(marker)` + 同事务写 marker」，否则脚本重跑会把已迁数据再搬一遍（旧→新是**置换**，逐条 UPDATE 会串味，必须单条 `CASE`）。③ **配套重复策略默认已从 `REJECT` 改为 `UPSERT`**（后端 `of()` / Service 兜底 / 两端前端 fallback 四处必须同改）；`ptxmmc` 现在**先归一再判重**。其余要点：档案加「项目→案卷→类别→文件」树形聚合（`/land/archive/projectPage`，分页以项目为单位）+ 归档复用一个项目一卷（只追加不删历史卷）；收发文已归档前后端都拒绝编辑、归档返回三段文件计数；查询导出新增 `allColumns=true` 全量模式（宗地 36 / 配套 59 列）；普通查询页新增自定义条件构造器（`QueryConditionBuilder.vue`，已登记 `verify-sfc`）；各域统一 `resetFilters()` 契约（`activated`/页签切换且路由无下钻参数时清条件，下钻参数消费后从路由摘掉）；`ScreenDataTable` 新增**可选**左右固定列（不写 `fixed` 时渲染与旧版等价）。验证：后端编译通过、定向回归 150 用例仅 3 条既有基线漂移失败、迁移后 103 用例全绿；两端 `verify-sfc` 111/59 通过、改动文件 eslint 0 error；清单新增「十一、二次整改交付记录」含迁移前后数字与人工核对清单 |
| 2026-11-16 | **按独立复核补齐清单剩余 11 条缺口 + 4 个清单外问题** | 对清单 34 条做了一次不采信交付记录的源码复核，结论 22 条闭合、11 条仍有缺口（P0-05、P1-01/04/05/09/10/11、P2-01/03/07/10）。本批要点：① **P1-01 修的是上批引入的回归** —— 详情里的「配套流程管理」原来先关详情再挂面板，面板随 `ScreenModal` 的 `v-if` 销毁；改为 `open-process` 事件 + 面板常驻 `FacilityEntryPanel`，详情「流程」页签只留只读摘要。② **P2-03 清空不落库**：`updateById` 的 `NOT_NULL` 让「清空备注/问题类型」变成假成功，后端对 6 个文本列改显式 `UpdateWrapper.set`（缺键=不改、显式空=清空），大屏平铺视图补内联编辑、管理端 `czwtlx` 去掉 `undefined`。③ **导入不再物理删回收站记录**：改为「同编号软删行恢复并复用」（`LandMapper` 两段手写 SQL，留痕记 RESTORE，新增用例）。④ **P2-01 两端一起做**：查库确认 21 个 land 菜单 `keep_alive=1`，管理端同样残留条件 —— 新增 `views/land/mixins/resetOnActivate.js`（首次跳过、下钻只重查不清），16 个可检索页接入；大屏补 5 个面板 `resetFilters` + 档案域首屏消费深链。⑤ **P1-11 落库行数取真实值**（归档前后 count 差值）；**P2-07 管理端项目聚合树**；**P2-10 映射确认表脚本**（不做自动映射）；**P3-01 上传上限 10MB→200MB**。验证：后端编译 SUCCESS、定向 82 用例 0 失败、大屏 verify-sfc 111 / 管理端 64、两端改动文件 eslint 0 error。仍为业务阻塞：中心业务档案表字段映射；材料类型 01 名称待确认。**工作区仍未提交（181 项），而五项迁移已在远程库执行。** |
| 2026-11-17 | **拉取合并排障：三文件被远端版本挤掉功能，工作树终版完好** 用户拉取合并（8178e31+2f2d74a，远端为另一 AI 的 P1/P2 清单修复）后三个文件出问题——比对发现合并结果丢了 11-15 的图形管理集成：FacilityFormModal 与 HEAD 差 297 行（FeatureGroupModal「管理地块图形」入口整批在 HEAD 里被退回旧「添加地块」），MapPickPanel 图层名 span 远端+本地两行叠放（已去重：保留带 tooltip 版、不放占位符），MapToolPanel 高亮映射在工作树完好。体检结论：工作树 = 用户最终版且 12 项关键改动全部完好（家族静默/混合拾取闸门/停靠 v10/三树联动/四卡映射/srrMode/折叠匹配…），无冲突标记残留；AGENTS.md 89 行无重复。待用户决定是否提交工作树版本 |

| 2026-11-17 | **合并残渣修复收尾：FacilityFormModal 编译错误 + FacilityEntryPanel 漏导入** ① FacilityFormModal 模板/脚本里的拼接残渣全清：按钮双 aria-label/双 @click、未闭合 span、computed 交错（远端 drawnVertexCount/pickTargetText/pickDrawTargetText/mapPick* 系列删除，partSummary 注释与闭合恢复）、方法区重建（handleOpenGroup 去重、handlePickEvent/handlePickFinished/pickToken/startPick 旧绘制流整批移除、syncFeatureGroupToSlice 拼接复原），eslint 全绿；② FacilityEntryPanel 2f2d74a 删了 queryFacilityGeoDetail 的 import 却留下 519 行调用（no-undef 运行时炸点），补回导入 |

| 2026-11-17 | **标注管理哑火修复：标注三件套配置兜底** 配套分析管理的标注管理三个子工具（管线标注/坐标标注/清除标注）不挂载、不发请求——引擎/弹窗挂载被 pipePopConfig.PipeMark/CoordinateLabel/DeleteLabel 门禁卡住，而 GIS 功能配置树（sceneCreateApi/dataList，ZK-SERVER 从 tj-jyxyd-zk 场景树下发）的顶层组（常用功能/地下空间/…）在 stargis_function 里无对应记录、componentname 为空，loadPipeConfig 只递归 PipeManager/Common/SpatialAnalysis 三个键——对不上时三键全空。且库里三节点的 ServerURL={}/DataURL=null 本就不带 URL（插件实际走 window._CONFIG.VUE_DATA_JAVA_URL=38190 backend-service）。修复：loadPipeConfig 末尾为标注三键补最小配置兜底 + deletelable→DeleteLabel 别名（库里组件名是小写拼法）。前置条件：backend-service(38190) 已于本日修复启动 |

| 2026-11-18 | **覆土分析属性表改为底部停靠**：仅将 CoverageAnalysisPop 的 `.bottom-table.info-table` 原节点接入 ScreenBottomDock，保留右侧统计操作和单条详情；新增 keepContent 可选属性，收起不销毁插件节点；结果显示时自动展开，切换工具/离页归还并清理节点。断面居中/拖动限定在断面模式，避免移动覆土属性表。验证：eslint、110 个 SFC 编译通过；浏览器模拟 20 行结果验证停靠、导出回调、折叠保留、拖高滚动、关闭/重开和切换清理。 |

| 2026-11-18 | **净距分析结果停靠底部属性表**：将插件原结果标题、导出按钮和表格节点移到底部，保留右侧拾取与分析操作及原事件；结果产生时展开，切换工具时归还节点并清理监听。 |

| 2026-11-18 | **管线统计结果停靠底部**：五类统计的原结果区、柱状图和导出入口接入底部属性表，图层及统计操作留在右侧；切换工具归还节点并清理监听。模拟验证五类结果展示和切换，eslint 与 110 个 SFC 编译通过。 |
| 2026-11-18 | **修复管径分段统计结果不展开**：插件直接向 `diameterSectPanel` 写入 div/a-row/a-col，既不更新 `diametersectdata` 也不生成 table，原停靠逻辑漏判结果。改为识别实际结果区文本及图表，监听内容变化；同步清空再写入相同结果也能重新展开。验证：浏览器拦截统计接口返回模拟数据，调用插件原分析方法复现并验证修复；覆盖收起保留、重复统计、导出事件、切换恢复、节点归还及空结果。eslint 与 110 个 SFC 编译通过；未验证真实 GIS 数据端到端链路。 |
| 2026-11-18 | **修复管线统计底部布局错乱**：结果行列按底部宽度均分，移除重复标题和空栅格，柱状图/导出按钮靠右；五类统计的原柱状图节点也停靠底部，关闭返回表格，按原画布比例适配宽度，高度不足时内部滚动，释放时归还节点并清理监听。验证：五类原图表入口、关闭/折叠/切换，管径分段原分析方法配模拟响应的九项回归及导出，真实鼠标拖高与截图检查；eslint、110 个 SFC、diff 检查通过。真实 GIS 分析请求未做端到端验证。 |
| 2026-11-18 | **恢复纵断面原接线并收起断面右侧旧面板**：纵断面恢复与横断面共用 `SectionalAnalysis`，自动选择插件内「纵断面分析」模式；移除误接的 `ProfileAnalysis` / `ProfileAnalysisPop`（剖面分析）。断面激活时隐藏引擎旧操作弹窗与模式清单，只保留 `.pop.bottom-table` 出图结果，居中拖动逻辑保留；横/纵模式互切会先复位旧模式。验证：eslint、110 个 SFC 模板/less 检查与 diff 检查通过。 |
| 2026-11-18 | **配套估算支持 MVT 拾取并从业务表兜底出让金**：三维要素优先，缺少地块属性时拾取当前可见 MVT 图层；切片无有效出让金时按归一后的完整宗地编号查询 Java 业务接口 `/land/data/landAdmin/list`，读取 `t_land.crj`（亿元），并校验精确编号，避免模糊查询误用相似地块。保留旧系统 35%～65% 估算公式。验证：配套估算回归 12 项、110 个 SFC 模板/样式检查、改动文件 ESLint、`git diff --check` 通过；未做真实 GIS 服务端到端验证。 |
| 2026-11-17 | **反向合并：本机 181 项整改落地 + 合并远端 b3e1783「地图管理」** 本轮把工作区（后端 + 两端前端 + SQL）拆成后端 / 管理端 / 大屏端 3 个提交，再 `pull --no-rebase` 合远端，冲突 4 个文件，逐个人工核对两侧改动都在：① `AGENTS.md` —— 两侧都只是追加变更记录，按「本机三条在前、同事三条在后」全留，表结构无重叠；② `LandEntryPanel.vue` —— 保留本机 P1-02 的 `ensureListLoaded`（`created` / `activated` 共用同 tick 去重），**移除本机原有的 `geoLayer` 生命周期**（created 建实例、activated 重铺、deactivated / beforeDestroy 清）：同事 2026-11-14 起进菜单不自动铺图、定位走切片源，两边都保留会在同一地图上画两遍面；③ `FacilityEntryPanel.vue` —— 以本机版本为底（P2-05 快速定位需要两个图层实例 + 概览缓存），叠加同事两项新增：移除成功后 `purgeFeatureGroups` 联动清切片图形、双击行改 `$emit('locate-record')` 交给父页定位；同时把本机 `loadGeoLayer` 收成「**只在定位态**铺本宗地配套面」（无定位态直接空转），顺带修掉同事版本里 `this.geoLayer` 已不再创建却仍在 `handleLocate` 里调用 `has/highlight/flyTo` 的运行时炸点；④ `map/index.vue` —— 本机 P2-02 的 `resetPickResult`（清结果 + 图层名）与同事 MVT 叠加拾取的 `clearPickHighlight` / `_lastPluginPickPayload` 全部保留：`beforeDestroy` / `deactivated` / 退出拾取三处两个都调，`handlePickResult` 先记 payload 再归一 rows。判据：`git grep '^<<<<<<<'` 为 0，两端 `verify-sfc` 与后端编译在合并后复跑（见下一条）。 |
| 2026-11-17 | **SHP 导入 DBF 编码自动探测（678 行乱码根因加固）** 「经营性用地信息」678 行编号损坏的根因：源 SHP 的 DBF 实际是 GBK，但被按 UTF-8 解码（.cpg 声明错误/手填编码选错）——不可逆。Shp2pgsqlEngine 增加 detectDbfEncoding：① 严格 UTF-8 试解码零错 → UTF-8；② 否则 GBK 试解码 + 中文佐证（CJK≥3）→ GBK；③ 探测失败回退 .cpg/配置编码。探测结果显式传 -W（覆盖 .cpg 错误声明，UTF-8 时不传跟随已钉死的 client encoding）；DBF 头部语言 ID 仅日志佐证。样本取前 2MB。javac（lombok 1.18.30 + slf4j 1.7.36 + target/classes）编译通过。生效需重启 jeecg 后端 |

| 2026-11-17 | **DBF 编码探测抽成共享工具，GeoTools 引擎同步加固** 探测逻辑从 Shp2pgsqlEngine 抽出为共享类 DbfEncodingDetector（严格 UTF-8 试解码 + GBK 中文佐证 + 回退配置），shp2pgsql 与 geotools 两个引擎共用；GeotoolsEngine 原来直接 setCharset(配置默认 GBK)——遇到 UTF-8 的 DBF 反向乱码，现改为探测结果驱动。javac（lombok 1.18.30 + slf4j + gt-shapefile/gt-opengis/gt-main/gt-metadata 28.2）四文件编译全绿 |
| 2026-11-18 | **合并远端 `ee738ed` / `68be66b`（配套分析优化）+ 删除 system-start 8 个跑不起来的测试** | ① 本机唯一改动是 8 个集成测试被删除（`jeecg-system-start/src/test/java` 下 `ArchiveAndDocumentServiceTest` / `ArchiveCategoryServiceTest` / `ArchiveDemoExportTest` / `ArchiveManifestFormatTest` / `LandPermissionTreeTest` / `SysUserTest` / `SampleTest` / `TestMain`）：该模块的 surefire 继承根 pom 的 `skipTests=true`，而把 skipTests 改回 false 的 `it` profile **只定义在 `jeecg-module-land`**（§3.5），所以这些类从来没进过任何一次构建；它们还假设「本机 MySQL 127.0.0.1」，与 §3.2「一律打远程库」口径相反。**已知代价**：仓库内没有同名副本，`jeecg-module-land` 现有 33 个测试类也没有覆盖档案类别 / 权限树 / 系统用户 —— 以后补测试请写在 `jeecg-module-land`，不要再往 `system-start` 放。② 远端两个提交（配套估算改 MVT 拾取 + 业务表兜底出让金、管线统计底部布局与断面接线回归）**零冲突**合并；按 §3.10 复跑两端 `verify-sfc`（大屏 113 / 管理端 64）与改动文件 `eslint --no-ignore` 全绿，并核对 `analysis/index.vue` 尾部 `</style>` 完整、`estimate.js` 的具名导出确被 `analysis/index.vue` 引用（无孤儿线索）。 |
| 2026-11-23 | **1118 业务侧整改落地（提交 / 推送 / 合并）** | 本轮按《业务相关问题整改清单1118》把工作区拆成后端 / 管理端 / 大屏端 / 文档四个提交推到 `origin/main`，再拉取合并（两侧功能都保留）。要点：① 竣工档案新增 `POST /land/archive/completion/linkArchiveBatch`（批量关联扫描件，整批一个事务、上限 100 条、任一失败全批回滚，每条按自身项目重算 `archive_count`）；② 档案类别 `archiveCount` 改为按 `path` 汇总「自身 + 全部祖先」（原来父类别恒为 0、详情也漏算子树），`t_archive_file` 不可用时「移除」直接拒绝，不再跳过校验后误删；③ 档案查询的类别条件改 `choose`（`categoryPath` 优先、否则 `categoryId`），两条件同时 AND 会查不到；④ 道路移交 / 验收台账：同一道路已有「移交中 / 已移交」（台账「验收中 / 已移交」）时禁止再次发起或推进到活跃态，编辑排除自身；详情关联档案 `limit=0` = 不限量；⑤ `limit=0` 成为「显式全量」契约（mapper 不拼 LIMIT，缺省仍 50 条上限），移交 / 台账 / 配套下拉共用；⑥ 单条配套与宗地解耦（P0-05）：宗地已移除 / 未登记不再阻断保存，`landExists` 只作提示，批量导入的 `allowOrphan` 闸门不变；配套查询补接 `ptsslb`（前端一直传、后端漏接 = 条件静默失效）；⑦ 附件：新增 `GET /land/data/attachment/objectOptions`（归属候选收进附件权限域，避免只有 `land:data:attachment` 的角色被档案 / 配套 Admin 权限拦成空候选），预览前先校验文件真实存在（否则把必然 404 交给 kkFileView，只表现为空白），压缩包只下载不预览；⑧ 切片：以 `pg_attribute` 真实列名解析字段（SHP 导入保留 DBF 大小写 `CRZDBH`，前端契约是小写），修掉 `column crzdbh does not exist` 导致图形回显 / 整组保存 / 定位一起失败；匹配 0 行时探测 U+FFFD 损坏并提示「按原始 DBF 编码（一般 GBK）重新导入」，对应 1118「双击定位报错 / 移除后图形残留」；⑨ 提级论证：行政区划 / 项目类型必填（编辑按「原值 + 本次值」合并校验），已办结必须有论证结论；详情补可打印区（原来 `window.print()` 打出来是空白页），查询页详情「编辑」改只读提示、图表下钻带页面当前条件；⑩ 查询统计新增 `crzdbh` 多值条件并按写入口径归一（补末尾「号」）；⑪ 大屏趋势图 x 轴标签抽稀（≤8 个、首末必留）。文档路径统一指向重组后的 `docs/模块实现说明/`、`docs/参考资料/`、`docs/原型与设计/`。验证：两端 `verify-sfc`（管理端 65 / 大屏 114）通过、改动文件 `eslint --no-ignore` 通过（`ScreenHeader.vue` / `ScreenStage.vue` 的 comma-dangle 是 HEAD 既有问题，本次只改注释行）；`ShpImportServiceImplGuardTest` 是随实现包一起移到 `spatial/service/impl`，不是删测试。 |

