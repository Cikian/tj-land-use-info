package org.jeecg.modules.land.archive.ledger.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.modules.land.archive.ledger.vo.LedgerImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: 道路设施验收及移交资料台账 - Excel 批量补录（模块 C）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p><b>★ 为什么需要它</b>：迁移完成后台账里还有 713 条「一类资料都没勾」、
 * 8 类资料全为 0（旧库压根没有对应标志位）。这 1000+ 个格子靠页面一条条点开勾选不现实，
 * 而中心的原始资料本来就是 Excel 台账 —— 所以批量补录必须支持 Excel。
 *
 * <p><b>三个动作</b>：
 * <ol>
 *   <li>{@link #buildTemplate()} —— 下载模板（第 2 个 sheet 写填表说明）；</li>
 *   <li>{@link #preview} —— 解析 + 校验，<b>不写库</b>，返回「会改多少条 / 错在哪几行」；</li>
 *   <li>{@link #confirm} —— 校验通过（或显式允许跳过错误行）才真正入库。</li>
 * </ol>
 *
 * <p><b>★ 匹配键是台账编号</b>（单列唯一键）。道路名称只做核对并给警告、不写回 ——
 * 避免一次导入把有人工修正过的名称批量改掉。
 */
public interface ILedgerImportService {

    /** 生成补录模板（含「填表说明」sheet） */
    Workbook buildTemplate();

    /**
     * 预览：解析并校验上传的 Excel。
     *
     * @param file 上传文件（.xlsx）
     * @return 解析统计 + 错误/警告清单；{@code updatedRows} 恒为 0
     */
    LedgerImportResultVO preview(MultipartFile file);

    /**
     * 入库。
     *
     * @param file          上传文件（.xlsx）
     * @param skipErrorRows true = 跳过有错误的行、只导入正确的行；
     *                      false（默认）= 只要有一行错误就整批不入库，只回执错误清单
     * @return 入库结果（含实际更新行数）
     */
    LedgerImportResultVO confirm(MultipartFile file, boolean skipErrorRows);
}
