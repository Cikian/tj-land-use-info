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
`escalation`（提级论证，28 类）。
**只有 `package-info.java` 的域 = 尚未实现**：`stat`（统计预警）、`analysis`、`map`、`common`、`config`。

### 2.2 前端

| 位置 | 放什么 |
|---|---|
| `src/api/land/*.js` | 一个业务域一个文件；顶部写注释说明对应后端 Controller |
| `src/views/land/data/`（管理端）/ `src/views/screen/data/`（大屏端） | 页面 |
| `…/modules/` | 页面拆出的子组件（表格/表单弹窗/搜索栏） |

### 2.3 注释与文档语言

**全部用中文**。注释要写**为什么**，不要复述代码。
本仓库的注释密度偏高是**有意的**——大量注释在记录「旧系统是什么行为、为什么这里不照抄」。

### 2.4 括号一律用中文括号「（）」（用户可见与录入的名称类内容）

**口径**（2026-10-10 确认）：**不要求全库半角**，但凡是**用户看到或录入的名称类内容**
一律用中文括号「（」「）」，不要用半角的「(」「)」。

适用：宗地编号、地块名称、受让人、楼盘名称、配套项目名称、建设单位、
材料类型名、字典项、界面文案。

**为什么这条不是洁癖**：半角 `()` 与中文 `（）` 是**不同的字符**，
看起来却几乎一样。不统一就会出现「同一宗地两条记录」：

- 判重失效 → 同一宗地/配套被录入两次
- 批量导入的「宗地必须已存在」校验误报「宗地不存在」
- 附件与环节挂在两个 id 上，统计翻倍

**实测**：847 条宗地编号里曾有 2 条用半角（`津西浯(挂)2025-07`、
`津辰青(挂)2025-008号`），配套名称 13 条、配套上的宗地编号 7 条。
已由 `sql/data/11_normalize_brackets.sql` 一次性刷成中文括号。

**怎么做**：
- 写入侧统一走 `DataSupport.normalizeBrackets(...)` / `cleanName(...)`
  —— 且必须发生在**判重之前**（否则两种写法仍会各建一条）。
- 覆盖三个入口：单条录入（`LandAdminServiceImpl` / `FacilityAdminServiceImpl`）、
  批量导入（两个 `*ImportServiceImpl` 的 `applyValue`）、历史数据（上面那个脚本）。
- **只动括号**，其余字符（含中间空格、大小写、末尾的「号」字）一律不碰 ——
  编号的书写习惯归用户，只统一「看起来一样却是不同字符」的那一处。

**旧系统本来就是中文括号**：旧存储目录名如 `津北辰仓（挂）2018-015`、
`义安路（永顺道-永尚道）` 全用中文括号，所以这是**向旧口径对齐**，不是新规则。

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
4. 交互元素在三维舞台上要有 `pointer-events: auto`（全局类 `.stage-hit`）。

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

当前规模：集成测试 **13 个测试类 / 184 个用例**，全量运行 **0 失败**（2026-10-10 实测）。

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

> 新增陷阱时，请**同时**：① 在本文件 §3 加一条（编号继续，
> 例如 §3.10）；② 在本表登记。陷阱要写**症状**和**怎么排查**，不要只写结论。
