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
- **新旧差异**：`docs/新旧系统差异清单.md` ← 改业务逻辑前必读，里面标了哪些差异是**有意修正**，改回去就是制造 bug

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
`escalation`（提级论证，28 类）、`stat.home`（首页看板：出让地块情况统计 / 待落实配套统计，
见 `docs/首页看板-实现说明.md`）、`stat.query` + `stat.export`（查询统计：经营性用地综合查询 /
用地配套项目查询 / 高级查询 / Excel 导出，见 `docs/查询统计-实现说明.md`）。
**只有 `package-info.java` 的域 = 尚未实现**：`stat` 的其余子域（预警算法）、`analysis`、`map`、`common`、`config`。

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
   本工程有两个后端：中台 `127.0.0.1:4548`（`VUE_APP_API_BASE_URL`）与
   Java `127.0.0.1:9802/api`（`VUE_DATA_JAVA_URL`）。`domianURL` 指向**中台**，
   而 `@/api/manage` 走 `domianURL` → 打到 4548 会 404。
   **判据：只要是 jeecg（Spring Boot）提供的接口，就用 `manageJava`。**
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
- **只有 5 个键没有默认值，缺一个就起不来**：
  `TJ_DB_HOST` `TJ_DB_PORT` `TJ_DB_NAME` `TJ_DB_USER` `TJ_DB_PASSWORD`。
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

当前规模：集成测试 **14 个测试类 / 219 个用例**，全量运行 **0 失败**（2026-10-21 实测）。

> 小提示：直接在源码里数 `@Test` 会得到比实际执行略多的数字（少数方法 JUnit
> 不按顶层测试收集）。**以 `Tests run:` 那一行为准**。

---

## 5. 改代码前的检查单

1. **先读** `docs/新旧系统差异清单.md` 里对应模块的条目。
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
| `docs/新旧系统差异清单.md` | ★ **改业务逻辑前必看**；也用于向中心解释差异 |
| `docs/升级改造工作内容清单.md` | 需求权威原文（239 KB，按章节查） |
| `docs/agent/_db_schema_report.md` | 旧库/新库表结构详解（209 KB） |
| `docs/agent/_old_frontend_analysis.md` | 旧前端页面与交互分析（273 KB） |
| `docs/agent/_plan_text.txt` | 方案的精简文本（10 KB，先看这个） |
| `docs/数据管理-实现说明.md` | 数据管理六大功能的实现与取舍 |
| `docs/经营性用地批量导入-实现说明.md` | 批量导入（模板/校验/回执） |
| `docs/档案管理-实现说明.md` 等 | 各模块实现说明 |
| `code/new/server/环境变量与敏感信息.md` | `.env` 机制、排查手册 |
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
| 2026-10-19 | **补 §2.1 已实现域** | `stat.home` 落地（首页看板两个统计，接口 `/land/stat/home/*`）。原 `data` 域的 `/land/data/dashboard`、`/land/data/facility/dashboard` 已删除，前端 `landData.js` 同步改路径。改首页数字前先读 `docs/首页看板-实现说明.md`：区级子表按 `xzqh2` 分组是**旧系统口径**，不是笔误 |
| 2026-10-20 | **重写 §2.4（括号 → 名称/编号全字符归一）** | 用户确认口径：名称里的半角括号→中文、连字符族→`-`、去掉所有空白；**编号末尾一律补「号」**，`【城更】/【城市更新】` 摘下来记备注。原因是 58 条配套因「带不带号」挂不上宗地、12 对宗地+56 条配套重复录入。写入侧收口到 `DataSupport`（5 个入口，含容易漏的旧接口 `LandServiceImpl`/`FacilityServiceImpl`），历史数据由 `sql/data/12_normalize_codes_and_names.sql` 幂等清洗。注意：**去重必须排在补「号」之前**，否则撞 `uk_land_crzdbh (crzdbh, del_flag)` |
| 2026-10-21 | **补 §2.1 已实现域** | `stat.query` + `stat.export` 落地（查询统计：经营性用地综合查询 / 用地配套项目查询 / 高级查询 / Excel 导出，接口 `/land/stat/query/*`），前端新增 `views/screen/query` 并接入顶栏「查询统计」。改查询口径前先读 `docs/查询统计-实现说明.md`：**两个数据集的指标口径不同**（宗地口径按配套名称去重 + 环节漏斗；配套口径按条计、不漏斗），别拿两页的数字互相校验 |

> 新增陷阱时，请**同时**：① 在本文件 §3 加一条（编号继续，
> 例如 §3.10）；② 在本表登记。陷阱要写**症状**和**怎么排查**，不要只写结论。

| 2026-10-16 | 接入「覆土分析」 | 覆土分析 = CoverageAnalysis 引擎 + CoverageAnalysisPop 结果弹窗（配置键 CoverageAnalysis，已在 PipeManager 摊平表里；总线事件 showCoveragePop 已从解码表证实，弹窗已注册 ✓）。净距分析同款 road 式接入：隐藏引擎 + __host 结果弹窗分支（clearance 分支后）+ toggleToolByKey 分发 toggleCover + resetEngineStates 引擎清单加 cover。互斥/兜底/计数全同净距模式。真机验证点：拾取交互由引擎内部流程决定（拾取管线后出覆土结果），中台需配置 CoverageAnalysis 数据源 |
| 2026-10-16 | 接入「流向分析」 | 流向分析 = FlowDirectionAnalysis 引擎 + FlowDirectionAnalysisPop 结果弹窗（配置键 FlowDirectionAnalysis，已在 PipeManager 摊平表里；总线事件 showFlowDirectionAnalysisPop 已从解码表证实）。净距/覆土同款 road 式接入：隐藏引擎 + __host 结果弹窗分支（cover 分支后）+ toggleToolByKey 分发 toggleFlow + resetEngineStates 引擎清单加 flow。互斥/兜底/计数全同净距模式。真机验证点：拾取交互由引擎内部流程决定（拾取管线后出流向结果），中台需配置 FlowDirectionAnalysis 数据源 |
| 2026-10-16 | 接入「配套估算」（前端复刻旧公式，用户选方案 1） | 插件无此组件（已查证）；旧系统 ptgsManager.js（163 行）的区间算法实为一行公式：拾取地块 → 读「出让金（亿元）」→ ptf = 出让金×10000（万元），区间 = ptf/2 ∓ ptf/2×0.3（即 ptf 的 35%~65%），填三格：宗地编号/地块名称/估算区间。实现：页面自实现——点「配套估算」卡片 → LEFT_CLICK 拾取模式 → scene.pick 读地块属性（字段名宽松匹配：出让宗地编号/CRZDBH、地块名称/DKMC、出让金，全半角与大小写兜底）→ 旧公式计算 → 结果显示在右面板（宗地编号/地块名称/估算区间大字 + 口径说明）。数值无效/无出让金字段/地图未就绪均有 toast 兜底；resetEngineStates 与 beforeDestroy 清拾取句柄。真机验证点：地块属性里出让金字段名与数值单位（亿元）是否与旧数据一致——用地图管理「拾取查询」点地块看属性即可确认 |