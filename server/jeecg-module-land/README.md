# jeecg-module-land — 天津市经营性用地市政基础设施配套动态监管工作站

业务模块。基于 **JEECG-Boot 3.4.3 / SpringBoot 2.6.6 / MyBatis-Plus 3.5.1 / Shiro 1.10.0 / JDK 1.8**。

依赖文档：`docs/升级改造工作内容清单.md`（下称「清单」）。

## 一、为什么是这个结构

| 约束 | 来源 | 后果 |
|---|---|---|
| 组件扫描根为 `org.jeecg` | `jeecg-system-start` 的 `org.jeecg.JeecgSystemApplication` | 业务包**必须**置于 `org.jeecg.modules.*` 之下，否则 `@Controller/@Service/@Component` 不被扫描 |
| `@MapperScan("org.jeecg.modules.**.mapper*")` | `jeecg-boot-base-core` 的 `MybatisPlusSaasConfig` | Mapper 接口**必须**放在 `...mapper` 或 `...mapper.*` 包下 |
| `mapper-locations: classpath*:org/jeecg/modules/**/xml/*Mapper.xml` | 各 `application-*.yml` | Mapper XML**必须**放在名为 `xml` 的子目录、且文件名以 `Mapper.xml` 结尾 |

> ⚠ 旧收发文工程把 Mapper XML 放在 `mapper/` 而非 `mapper/xml/`，因内容为空才没暴露问题。本模块一律使用 `mapper/xml/`。

## 二、模块接入方式

```
server/pom.xml                                     <modules> 增加 jeecg-module-land
server/jeecg-module-system/jeecg-system-start/pom.xml   <dependencies> 增加 jeecg-module-land
server/jeecg-module-land/pom.xml                    依赖 jeecg-boot-base-core（传递带入 web / MP / Shiro / knife4j / autopoi）
```

依赖 `jeecg-boot-base-core` 后，模块内可直接使用，无需重复声明：

- `org.jeecg.common.api.vo.Result` — 统一返回体
- `org.jeecg.common.aspect.annotation.AutoLog` — 操作日志
- `org.jeecg.common.system.base.controller.JeecgController` — CRUD 基类
- `org.jeecg.common.util.QueryGenerator` — 条件构造
- `com.baomidou.mybatisplus.*` — MyBatis-Plus
- `org.apache.shiro.authz.annotation.*` — 权限注解
- `org.jeecgframework.poi.excel.*` — Excel 导入导出（AutoPoi）

## 三、包结构与职责

```
org.jeecg.modules.land
├── config/                     业务级 Spring 配置（跨库数据源、文件存储、任务注册）
│   └── properties/             业务自定义配置项
├── common/                     业务公共层
│   ├── constant/               常量：字典码、流程节点码、档案状态码、附件业务类型
│   ├── enums/                  枚举：项目分类、配套设施类别、预警等级、档案状态、论证结果
│   ├── exception/              业务异常与统一异常处理
│   └── util/                   工具：SQL 参数化辅助、几何/坐标、工作日计算
│
├── data/                       数据管理域 —— 清单 5.3 / 方案 2.3.1（三）
│   ├── land/                   经营性用地（宗地）录入、编辑、软删、crzdbh 唯一校验
│   ├── facility/               配套项目（1 宗地 : N 配套，按 crzdbh 关联）
│   ├── attachment/             附件（元数据入库 + 文件落盘，★ 只传 id 不传路径）
│   ├── fieldconfig/            字段展示配置（替代旧 information_schema 反射）
│   ├── process/                配套流程环节填报
│   ├── oplog/                  业务操作日志
│   └── imports/                Excel 批量导入（双行表头、预校验、参数化写入）
│
├── map/                        地图管理域 —— 清单 5.1 / 5.2 / 方案 2.3.1（一）（二）
│   ├── layer/                  图层管理（树、显隐、透明度、符号、标注）
│   ├── bookmark/               视图书签
│   ├── tour/                   动画导航
│   ├── query/                  空间/属性/综合查询与综合统计
│   ├── locate/                 地名定位、道路定位
│   ├── export/                 高清出图
│   └── engine/                 ★ 三维引擎适配层（RenderControl Shim）
│       ├── shim/               按旧插件签名定义的引擎接口（约 242 个方法）
│       └── impl/               Cesium/Three.js 实现；无法实现者显式 NotImplemented
│
├── analysis/                   配套分析管理域 —— 清单 5.4 / 方案 2.3.1（四）
│   ├── label/                  标注管理（管线/管点/埋深/坐标/清除）
│   ├── pipeline/               管线统计
│   ├── section/                横断面、纵断面
│   ├── clearance/              净距分析
│   ├── cover/                  覆土分析
│   ├── flow/                   流向分析
│   ├── burst/                  爆管分析
│   ├── excavation/             开挖分析
│   ├── maturity/               成熟度分析
│   ├── estimate/               ★ 配套估算（区间算法，见清单 5.4.1）
│   └── spec/                   管线规范域数据（编码域/埋深/净距）
│
├── archive/                    ★ 档案管理域 —— 清单 第 6 章 / 方案 2.3.2（九项，全新）
│   ├── category/               档案类别管理（★ 删除前校验无档案无子类）
│   ├── archive/                档案维护
│   ├── file/                   档案文件（卷内文件、MD5 去重、预览）
│   ├── document/               ★ 收发文管理（重写实现，旧 tj-sfw 不可直接复用）
│   ├── handover/               道路交付及养护协议移交事项
│   ├── ledger/                 道路设施验收及移交资料台账
│   ├── completion/             竣工验收项目历史工程资料数字化档案
│   ├── log/                    档案操作记录
│   └── enums/                  档案状态、密级、保管期限、操作类型
│
├── escalation/                 ★ 提级论证管理域 —— 清单 第 7 章 / 方案 2.3.3（四项，全新）
│   ├── project/                项目录入（对象是未出让地块）
│   ├── material/               论证材料（多文件、版本、补充材料）
│   ├── approval/               ★ 5 节点审批 + 1000 字批注
│   ├── ledger/                 资料及台账管理
│   └── enums/                  审批状态、5 个节点、论证结果
│
└── stat/                       查询统计与首页域 —— 清单 5.5 / 方案 2.3.1（五）
    ├── home/                   ★ 首页看板（阈值：市级 5 档、区级 3 档）
    ├── query/                  综合查询、进展情况查询
    ├── warning/                预警计算（环节/阶段/总体三种口径）
    ├── export/                 导出（Excel/Word）
    ├── process/                流程配置基础数据维护
    └── job/                    定时任务（非工作日日历、预警重算）
```

每个域下均含标准分层：`entity/` `mapper/` `mapper/xml/` `service/` `service/impl/` `controller/` `vo/` `dto/`。

## 四、开发约定（清单 4.4）

新建一个业务功能的标准文件清单：

```
<域>/entity/Xxx.java                 @TableName + @Data + @Excel + @Dict + @TableLogic
<域>/mapper/XxxMapper.java           extends BaseMapper<Xxx>
<域>/mapper/xml/XxxMapper.xml        ★ 必须在 xml 子目录
<域>/service/IXxxService.java        extends IService<Xxx>
<域>/service/impl/XxxServiceImpl.java extends ServiceImpl<XxxMapper, Xxx>
<域>/controller/XxxController.java   extends JeecgController，★ 每个接口加 @RequiresPermissions
```

上线检查清单：

- [ ] 表已建，字段注释齐全，有主键与必要索引
- [ ] 实体 `@TableLogic` 逻辑删除生效
- [ ] **每个接口都有 `@RequiresPermissions`**（旧收发文系统权限全被注释的教训，清单 4.3-4）
- [ ] 前端按钮有 `v-has` 控制
- [ ] 菜单已 INSERT 到 `sys_permission` 并授权角色
- [ ] 分页/条件查询/导入导出均已验证
- [ ] 写操作有 `@AutoLog` 并写业务日志表
- [ ] **附件类接口只传 id，不传路径**（旧系统路径穿越漏洞，清单 附录B.4）
- [ ] 字典字段用 `@Dict` + `JDictSelectTag`，不硬编码枚举

## 五、当前状态

本模块目前**只有目录结构与包说明（109 个 `package-info.java`）**，尚无业务代码，因此编译产物不含 class 文件（JDK 8 下纯 Javadoc 的 `package-info` 不生成 `.class`）。

已验证：

```
mvn -pl jeecg-module-land install     → BUILD SUCCESS
```

下一步建议按清单 **P1（基座搭建）→ P2（数据管理）** 顺序填充，P5d（纯表单/查询类面板）因后端接口已就绪，可作为第一个可演示里程碑。
