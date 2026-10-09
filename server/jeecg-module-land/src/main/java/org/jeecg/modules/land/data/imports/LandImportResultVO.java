package org.jeecg.modules.land.data.imports;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 经营性用地批量导入 - 结果回执（预览与入库共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>「预览」与「入库」使用<b>同一个结构</b>，前端只渲染一套结果面板：
 * <pre>
 *   预览：insertedRows / updatedRows 恒为 0，其余字段照常给出
 *         （用户先看「会新增多少条、会覆盖多少条、错在哪几行」）
 *   入库：insertedRows / updatedRows 是真正写进库的行数
 * </pre>
 *
 * <p><b>★ 为什么错误必须精确到「Excel 行号 + 宗地编号 + 字段 + 原因」</b>：
 * 一次可能传几百行，只说「有 17 行有问题」等于没说。
 * 用户要能拿着「第 37 行、crzdbh 重复」直接跳到 Excel 里改完重传 ——
 * 这是批量导入能不能真正被人用起来的分水岭（沿用台账补录模块的同一取舍）。
 */
@Data
public class LandImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------------- 解析计数 ----------------

    /** 解析到的数据行数（不含表头行与空行） */
    private int totalRows;

    /** 校验通过、可以写入的行数 */
    private int validRows;

    /** 实际新增的行数（预览时为 0） */
    private int insertedRows;

    /** 实际覆盖更新的行数（预览时为 0） */
    private int updatedRows;

    /** 因重复宗地编号被跳过的行数（策略 = skip 时） */
    private int skippedDuplicates;

    /** 预览时「按当前策略将会覆盖更新」的行数（入库时与 updatedRows 一致） */
    private int willUpdateRows;

    /** 预览时「按当前策略将会新增」的行数 */
    private int willInsertRows;

    // ---------------- 策略与安全阀 ----------------

    /** 本次使用的重复处理策略 */
    private String duplicateStrategy;

    /** 重复处理策略的中文说明 */
    private String duplicateStrategyLabel;

    /**
     * 是否会因为存在错误而整批不入库。
     *
     * <p>{@code skipErrorRows=false} 且有错误时为 {@code true}——
     * 前端据此提示「本次不会写入任何数据」。
     */
    private boolean aborted;

    // ---------------- 回执清单 ----------------

    /** 错误清单（有错误时整批不入库，除非显式跳过错误行） */
    private List<RowError> errors = new ArrayList<>();

    /** 警告清单（不影响入库，例如「联系电话按文本处理」「取值已自动归一化」） */
    private List<String> warnings = new ArrayList<>();

    /** 提示清单（不影响入库，例如「重复行已跳过」「空单元格未覆盖」） */
    private List<String> notices = new ArrayList<>();

    // ==================================================================
    // 便捷方法
    // ==================================================================

    /** 追加一条错误 */
    public void addError(int rowNum, String crzdbh, String column, String columnLabel, String message) {
        errors.add(new RowError(rowNum, crzdbh, column, columnLabel, message));
    }

    /** 追加一条警告 */
    public void addWarning(String message) {
        warnings.add(message);
    }

    /** 追加一条提示 */
    public void addNotice(String message) {
        notices.add(message);
    }

    /** 错误条数 */
    public int getErrorCount() {
        return errors.size();
    }

    /**
     * @Description: 一行错误
     */
    @Data
    public static class RowError implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Excel 行号（1 基，与用户在 Excel 左侧看到的行号一致） */
        private int rowNum;

        /** 该行的出让宗地编号（可能为空 —— 编号本身就没填时） */
        private String crzdbh;

        /** 出错的列名（英文字段名），整体性错误为 null */
        private String column;

        /** 出错的列中文名 */
        private String columnLabel;

        /** 原因 */
        private String message;

        public RowError() {
        }

        public RowError(int rowNum, String crzdbh, String column, String columnLabel, String message) {
            this.rowNum = rowNum;
            this.crzdbh = crzdbh;
            this.column = column;
            this.columnLabel = columnLabel;
            this.message = message;
        }
    }
}
