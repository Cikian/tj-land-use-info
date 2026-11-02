/**
 * 分级预警（差异清单 D-603）：预警等级匹配、预警值计算、决策意见匹配与预警记录落库。
 *
 * <p>方案 2.3.1（五）之 5-06；旧系统对应
 * {@code ProcessStatusController.createSpecial} L152-241 与
 * {@code ProcessWarningServiceImpl} 的 {@code getYjzForStage / getLevelForStage /
 * getWarningMsgForWhole}。
 *
 * <p><b>本包的文件落点</b>（按已实现域 {@code stat.home} / {@code stat.query} 的风格，
 * 实体/Mapper/Service 都放在 {@code stat.warning} 的子包里）：
 * <pre>
 *   entity/    ProcessWarning / ProcessWarningLevel / DecisionOpinion
 *   dto/       WarningQueryDTO / WarningRecalcDTO
 *   vo/        WarningVO / WarningSummaryVO / WarningLevelVO / DecisionOpinionVO /
 *              WarningOptionsVO / RecalcResultVO
 *   support/   WarningLevelSupport（纯函数，可单测）/ WarningFields（字面量收口）
 *   mapper/    ProcessWarningMapper（+ xml/ProcessWarningMapper.xml）/
 *              ProcessWarningLevelMapper / DecisionOpinionMapper
 *   service/   IWarningService / IWarningLevelService / IDecisionOpinionService（+ impl）
 *   controller/WarningController（/land/stat/warning）/
 *              WarningLevelController（/land/stat/warning/level）/
 *              DecisionOpinionController（/land/stat/warning/opinion）
 * </pre>
 *
 * <p><b>与 {@code stat/entity}、{@code stat/mapper}、{@code stat/service} 三个占位包的关系</b>：
 * 那三个包保留不动（它们的 javadoc 记录的是最初的分层计划），
 * 实际落点以 {@code docs/分级预警-实现契约.md} §4 为准 —— 与 {@code stat.home}、
 * {@code stat.query} 的做法一致：一个业务域用自己的子包，
 * 免得统计功能越加越多时 {@code stat/entity} 变成几十个类的堆栈。
 *
 * <p>所属工程：jeecg-module-land（JEECG-Boot 3.4.3 / JDK 1.8）
 * <p>参考文档：docs/分级预警-实现契约.md、docs/升级改造工作内容清单.md
 */
package org.jeecg.modules.land.stat.warning;
