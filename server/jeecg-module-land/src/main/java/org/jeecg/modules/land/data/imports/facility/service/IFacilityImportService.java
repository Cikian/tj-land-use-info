package org.jeecg.modules.land.data.imports.facility.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.modules.land.data.imports.facility.FacilityDuplicateStrategy;
import org.jeecg.modules.land.data.imports.facility.FacilityImportResultVO;
import org.jeecg.modules.land.data.imports.facility.support.FacilityImportSupport;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: 配套信息批量导入（模板 + 校验 + 孤儿清单 + 错误回执 + 入库）核心服务
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（三）「配套信息批量导入管理」，
 * 旧实现为 {@code xjKjkfbSupportingFacilitiesController.importData()} / {@code getExcelDemo()}。
 *
 * <p><b>四个动作</b>：
 * <ol>
 *   <li>{@link #buildTemplate()} —— 下载模板（第 2 个 sheet 是逐字段的填表说明）；</li>
 *   <li>{@link #preview} —— 解析 + 校验，<b>不写库</b>，
 *       返回「会新增多少条 / 会覆盖多少条 / 错在哪几行 / 哪些行找不到宗地」；</li>
 *   <li>{@link #confirm} —— 校验通过（或显式允许跳过错误行）才真正入库；</li>
 *   <li>{@link #buildErrorReport} —— 把错误清单（以及孤儿清单）导成 Excel。</li>
 * </ol>
 *
 * <p><b>★ 预览与入库共用同一条解析路径</b>（{@code parse(file, apply, …)}），
 * 只在最后一步决定「写不写」。这样能保证「预览看到什么，导入就写什么」——
 * 如果两边各写一套校验，必然会出现「预览说没问题、导入报错」这种最伤信任的情况。
 *
 * <p><b>★★ 本模块比宗地导入多一个参数 {@code allowOrphan}，这是本模块最重要的业务取舍</b>：
 * <ul>
 *   <li>{@code allowOrphan = false}（默认）：{@code crzdbh} 在 {@code t_land} 里查不到
 *       → <b>硬错误</b>，整批不入库（除非同时勾「跳过错误行」）。
 *       默认拦住，是因为设计文档实测旧库有 <b>60 行孤儿配套</b>，
 *       而档案、收发文、台账都按宗地关联 —— 挂不上的配套等于白导。</li>
 *   <li>{@code allowOrphan = true}：这些行记入<b>孤儿清单</b>（警告级）后<b>照常入库</b>。
 *       业务上确实存在「配套台账先到、宗地后录」的时序，
 *       一刀切拒绝会让用户只能手工补录；给一个显式开关，把「我知道我在干什么」
 *       交给用户确认，同时用清单保证「导完还有一份待办」。</li>
 * </ul>
 */
public interface IFacilityImportService {

    /** 生成导入模板（含「填表说明」sheet，两行表头：英文字段名 + 中文说明） */
    Workbook buildTemplate();

    /**
     * 预览：解析并校验上传的 Excel，<b>不写库</b>。
     *
     * @param file              上传文件（.xlsx / .xls）
     * @param duplicateStrategy 重复 (宗地编号, 配套项目名称) 的处理策略（null = 默认「重复即报错」）
     * @param allowOrphan       是否允许把 {@code crzdbh} 查不到的行挂到「未登记宗地」
     *                          （true = 记入孤儿清单并照样视为可导入；false = 作为硬错误）
     * @return 解析统计 + 错误/警告/提示/孤儿清单；{@code insertedRows} 与 {@code updatedRows} 恒为 0
     */
    FacilityImportResultVO preview(MultipartFile file, FacilityDuplicateStrategy duplicateStrategy,
                                   boolean allowOrphan);

    /**
     * 入库。
     *
     * @param file              上传文件
     * @param duplicateStrategy 重复处理策略
     * @param skipErrorRows     true = 跳过有错误的行、只导入正确的行；
     *                          false（默认）= 只要有一行错误就整批不入库，只回执错误清单
     * @param allowOrphan       是否允许挂到未登记宗地（true 时孤儿行照常导入并记入清单）
     * @return 入库结果（含实际新增/覆盖行数与孤儿清单）
     */
    FacilityImportResultVO confirm(MultipartFile file, FacilityDuplicateStrategy duplicateStrategy,
                                   boolean skipErrorRows, boolean allowOrphan);

    /**
     * 生成错误回执 Excel（重新解析上传文件）。
     *
     * <p>与模板列一致（用户可以把回执改好后直接重传），末尾追加 3 列诊断信息：
     * {@code __excel行号 / __错误字段 / __错误原因}；有孤儿时另附「孤儿清单」sheet。
     *
     * @param file              上传文件（重新解析以便回填用户原始填写内容）
     * @param duplicateStrategy 与预览时相同的策略（保证回执错误清单与预览一致）
     * @param allowOrphan       与预览时相同的开关（否则孤儿/错误的分流会与刚才那次不一致）
     */
    Workbook buildErrorReport(MultipartFile file, FacilityDuplicateStrategy duplicateStrategy,
                              boolean allowOrphan);

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
    Workbook buildErrorReport(FacilityImportResultVO result, FacilityImportSupport.ErrorReportSource source);

    /**
     * 只做解析（供回执复用原始行内容，不写库、不抛「有错误」的异常）。
     *
     * @return 解析结果 + 原始行内容载体
     */
    ParsedFile parseOnly(MultipartFile file, FacilityDuplicateStrategy duplicateStrategy, boolean allowOrphan);

    /**
     * @Description: 一次解析的完整产物（结果 + 原始行内容）
     *
     * <p>把两者放在一起返回，是为了让「预览 → 用户看错误 → 下载回执」这条链路
     * 只解析一次文件；同时入库之后下载回执也能拿到「当时那一版」的原始行内容。
     */
    final class ParsedFile {

        private final FacilityImportResultVO result;
        private final FacilityImportSupport.ErrorReportSource source;

        public ParsedFile(FacilityImportResultVO result, FacilityImportSupport.ErrorReportSource source) {
            this.result = result;
            this.source = source;
        }

        public FacilityImportResultVO getResult() {
            return result;
        }

        public FacilityImportSupport.ErrorReportSource getSource() {
            return source;
        }
    }
}
