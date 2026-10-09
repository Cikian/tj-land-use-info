package org.jeecg.modules.land.data.imports.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.modules.land.data.imports.LandDuplicateStrategy;
import org.jeecg.modules.land.data.imports.LandImportResultVO;
import org.jeecg.modules.land.data.imports.support.LandImportSupport;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: 经营性用地批量导入（模板 + 校验 + 错误回执 + 入库）核心服务
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（三）「经营性用地批量导入管理」，
 * 旧实现为 {@code xjKjkfbCommercialLandController.importData()} / {@code getExcelDemo()}。
 *
 * <p><b>四个动作</b>：
 * <ol>
 *   <li>{@link #buildTemplate()} —— 下载模板（第 2 个 sheet 是逐字段的填表说明）；</li>
 *   <li>{@link #preview} —— 解析 + 校验，<b>不写库</b>，返回「会新增多少条 / 会覆盖多少条 / 错在哪几行」；</li>
 *   <li>{@link #confirm} —— 校验通过（或显式允许跳过错误行）才真正入库；</li>
 *   <li>{@link #buildErrorReport} —— 把错误清单导成 Excel，行内容原样保留 + 追加「错误原因」列。</li>
 * </ol>
 *
 * <p><b>★ 预览与入库共用同一条解析路径</b>（{@code parse(file, apply, …)}），
 * 只在最后一步决定「写不写」。这样能保证「预览看到什么，导入就写什么」——
 * 如果两边各写一套校验，必然会出现「预览说没问题、导入报错」这种最伤信任的情况。
 */
public interface ILandImportService {

    /** 生成导入模板（含「填表说明」sheet，两行表头：英文字段名 + 中文说明） */
    Workbook buildTemplate();

    /**
     * 预览：解析并校验上传的 Excel，<b>不写库</b>。
     *
     * @param file              上传文件（.xlsx / .xls）
     * @param duplicateStrategy 重复宗地编号的处理策略（null = 默认「重复即报错」）
     * @return 解析统计 + 错误/警告/提示清单；{@code insertedRows} 与 {@code updatedRows} 恒为 0
     */
    LandImportResultVO preview(MultipartFile file, LandDuplicateStrategy duplicateStrategy);

    /**
     * 入库。
     *
     * @param file              上传文件
     * @param duplicateStrategy 重复宗地编号的处理策略
     * @param skipErrorRows     true = 跳过有错误的行、只导入正确的行；
     *                          false（默认）= 只要有一行错误就整批不入库，只回执错误清单
     * @return 入库结果（含实际新增/覆盖行数）
     */
    LandImportResultVO confirm(MultipartFile file, LandDuplicateStrategy duplicateStrategy, boolean skipErrorRows);

    /**
     * 生成错误回执 Excel。
     *
     * <p>与模板列一致（用户可以把回执改好后直接重传），末尾追加 3 列诊断信息：
     * {@code __excel行号 / __错误字段 / __错误原因}。
     *
     * @param file              上传文件（重新解析以便回填用户原始填写内容）
     * @param duplicateStrategy 与预览时相同的策略（保证回执错误清单与预览一致）
     * @return 回执工作簿；若没有错误，则回执只有表头 + 说明 sheet
     */
    Workbook buildErrorReport(MultipartFile file, LandDuplicateStrategy duplicateStrategy);

    /**
     * 生成错误回执 Excel（直接用已有结果，不重新解析文件）。
     *
     * <p>适用场景：入库（{@code confirm}）之后，前端手里拿的是入库返回的结果，
     * 此时再重新解析一遍文件既慢又可能与刚才那次不一致（库已被本次导入改过），
     * 所以优先用这个重载，把「当次的结果 + 当次的原始行内容」直接回执出去。
     *
     * @param result 当次预览/入库的结果
     * @param source 当次解析出来的原始行内容（{@link ParsedFile#getSource()}）
     */
    Workbook buildErrorReport(LandImportResultVO result, LandImportSupport.ErrorReportSource source);

    /**
     * 只做解析（供回执复用原始行内容，不写库、不抛「有错误」的异常）。
     *
     * @return 解析结果 + 原始行内容载体
     */
    ParsedFile parseOnly(MultipartFile file, LandDuplicateStrategy duplicateStrategy);

    /**
     * @Description: 一次解析的完整产物（结果 + 原始行内容）
     *
     * <p>把两者放在一起返回，是为了让「预览 → 用户看错误 → 下载回执」这条链路
     * 只解析一次文件；同时入库之后下载回执也能拿到「当时那一版」的原始行内容。
     */
    final class ParsedFile {

        private final LandImportResultVO result;
        private final LandImportSupport.ErrorReportSource source;

        public ParsedFile(LandImportResultVO result, LandImportSupport.ErrorReportSource source) {
            this.result = result;
            this.source = source;
        }

        public LandImportResultVO getResult() {
            return result;
        }

        public LandImportSupport.ErrorReportSource getSource() {
            return source;
        }
    }
}
