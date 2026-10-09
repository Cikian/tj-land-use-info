package org.jeecg.modules.land.archive.ledger.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 道路设施验收及移交资料台账 - Excel 批量补录结果（预览与入库共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>「预览」与「入库」两个接口返回<b>同一个结构</b>，这样前端只要渲染一套结果面板：
 * <pre>
 *   预览：updatedRows = 0，其余字段照常给出（用户先看会改多少条、错在哪几行）
 *   入库：updatedRows = 真正写进库的行数
 * </pre>
 *
 * <p><b>★ 为什么错误要精确到「行号 + 台账编号 + 原因」</b>：
 * 需求方要在 Excel 里补录 713 条空台账、8 类缺失资料，一次可能传几百行。
 * 只说「有 17 行有问题」等于没说 —— 必须能直接定位到 Excel 的第几行、是哪个台账、错在哪个字段，
 * 用户改完重传即可。这是批量导入能不能真正用起来的分水岭。
 */
@Data
public class LedgerImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 解析到的数据行数（不含表头与空行） */
    private int totalRows;

    /** 其中命中已有台账的行数（按台账编号匹配） */
    private int matchedRows;

    /** 实际更新的行数（预览时为 0） */
    private int updatedRows;

    /** 被跳过的行数（整行没有可写内容，例如用户只填了模板的说明行） */
    private int skippedRows;

    /** 本次因勾选而新归集的资料份数（信息项，让用户感知「补了多少资料」） */
    private int materialFilled;

    /** 是否会因为存在错误而整批不入库（skipErrorRows=false 且有错误时为 true） */
    private boolean aborted;

    /** 错误清单（有错误时整批不入库，除非显式指定 skipErrorRows） */
    private List<RowError> errors = new ArrayList<>();

    /** 警告清单（不影响入库，例如「道路名称与库内不一致」「把功能区写进了行政区划列」） */
    private List<String> warnings = new ArrayList<>();

    /** 追加一条错误 */
    public void addError(int rowNum, String ledgerNo, String message) {
        errors.add(new RowError(rowNum, ledgerNo, message));
    }

    /** 追加一条警告 */
    public void addWarning(String message) {
        warnings.add(message);
    }

    /**
     * @Description: 一行错误（行号按 Excel 的行号，从 1 开始，方便用户直接跳过去）
     */
    @Data
    public static class RowError implements Serializable {
        private static final long serialVersionUID = 1L;

        /** Excel 行号（1 基，与用户在 Excel 左侧看到的行号一致） */
        private int rowNum;

        /** 该行的台账编号（可能为空 —— 编号本身就没填时） */
        private String ledgerNo;

        /** 原因 */
        private String message;

        public RowError() {
        }

        public RowError(int rowNum, String ledgerNo, String message) {
            this.rowNum = rowNum;
            this.ledgerNo = ledgerNo;
            this.message = message;
        }
    }
}
