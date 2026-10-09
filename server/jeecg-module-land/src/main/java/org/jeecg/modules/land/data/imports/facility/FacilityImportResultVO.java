package org.jeecg.modules.land.data.imports.facility;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 配套信息批量导入 - 结果回执（预览与入库共用，含孤儿清单）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>「预览」与「入库」使用<b>同一个结构</b>，前端只渲染一套结果面板：
 * <pre>
 *   预览：insertedRows / updatedRows 恒为 0，其余字段照常给出
 *         （用户先看「会新增多少条、会覆盖多少条、错在哪几行、哪些是孤儿」）
 *   入库：insertedRows / updatedRows 是真正写进库的行数
 * </pre>
 *
 * <p><b>★★ 本 VO 相对经营性用地导入结果的关键新增：孤儿清单</b>
 * （{@link #orphans} / {@link #orphanRows} / {@link #matchedLandRows} / {@link #allowOrphan}）。
 *
 * <p><b>什么是「孤儿」</b>：一行的 {@code crzdbh}（出让宗地编号）在 {@code t_land} 里
 * <b>查不到</b>，也就是说这条配套项目在系统里<b>没有归属的宗地</b>。
 *
 * <p><b>为什么孤儿要单独成一个清单、而不是当成普通错误</b>：
 * <ol>
 *   <li><b>它不是「填错」</b>。设计文档实测旧库有 <b>60 行孤儿配套</b>
 *       （配套表里有、宗地表里查不到），另外还有 60 行是「编号书写不一致」
 *       （{@code 津南长（挂）G2024-01} 少了末位的「号」、{@code 津西青楚(挂)} 用了半角括号）
 *       —— 这两种情况的处理方式完全不同：前者要补录宗地，后者只要改一下写法。
 *       混在错误清单里，用户分不清「我这行到底哪儿错了」。</li>
 *   <li><b>它可能是用户有意为之</b>。宗地信息由另一个岗位维护，配套台账先到、
 *       宗地后录是真实存在的时序。用户明确勾了「允许挂到未登记宗地」时，
 *       这些行是<b>应该导进去的</b>，此时把它报成错误会自相矛盾。</li>
 *   <li><b>用户需要看到「完整的一份清单」</b>。散在几百条错误里，用户没法
 *       一眼看出「这批数据里有 17 条找不到宗地」，也就无法去找对应岗位催录宗地。
 *       单独一个清单可以直接当成一张待办表用。</li>
 * </ol>
 *
 * <p><b>孤儿行的两种命运（由 {@link #allowOrphan} 决定）</b>：
 * <pre>
 *   allowOrphan = false（默认）：进 {@link #errors}，整批不入库（除非勾「跳过错误行」）。
 *                                错误文案里给出「该怎么改」的两个选项；
 *   allowOrphan = true：        进 {@link #orphans}（警告级），这一行<b>照常入库</b>，
 *                                同时在 {@link #warnings} 里汇总提示有多少条孤儿。
 * </pre>
 * 无论走哪条路，{@link #orphans} 里都<b>不会</b>凭空出现没有被用户确认过的数据：
 * {@code allowOrphan=false} 时清单只用于回执展示（{@link #orphanRows} 仍会统计），
 * 只是这些行同时也在 errors 里。
 *
 * <p><b>★ 为什么错误必须精确到「Excel 行号 + 字段 + 原因」</b>：
 * 一次可能传几百行，只说「有 17 行有问题」等于没说。用户要能拿着
 * 「第 37 行、配套项目名称 重复」直接跳到 Excel 里改完重传 ——
 * 这是批量导入能不能真正被人用起来的分水岭（与宗地导入、台账补录同一取舍）。
 */
@Data
public class FacilityImportResultVO implements Serializable {

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

    /** 因 (宗地编号, 配套项目名称) 与库内重复被跳过的行数（策略 = skip 时） */
    private int skippedDuplicates;

    /** 预览时「按当前策略将会覆盖更新」的行数（入库时与 updatedRows 一致） */
    private int willUpdateRows;

    /** 预览时「按当前策略将会新增」的行数 */
    private int willInsertRows;

    // ---------------- 宗地存在性（孤儿）----------------

    /**
     * 本次是否允许「挂到未登记宗地」。
     *
     * <p>为 {@code true} 时，{@code crzdbh} 在 {@code t_land} 里查不到的行
     * 不再当作错误拦截，而是记入 {@link #orphans} 后照常导入。
     * 回执里带上这个标记，是为了让前端能明确告诉用户「这批数据里确实包含了孤儿」，
     * 而不是让用户事后在库里发现一堆挂不上的配套。
     */
    private boolean allowOrphan;

    /**
     * 孤儿行数（{@code crzdbh} 在 t_land 中不存在的行数，无论是否允许导入都会统计）。
     *
     * <p>★ 统计口径：只统计<b>通过了必填与文件内重复校验</b>的行
     * （这类行才进入宗地存在性校验）。举例：一行连 {@code crzdbh} 都没填，
     * 它会被记成「出让宗地编号不能为空」，而<b>不会</b>再被算成一个孤儿 ——
     * 否则同一行会以两种理由各出现一次，用户改完必填项后还得再看一遍孤儿清单。
     */
    private int orphanRows;

    /**
     * 成功按 {@code crzdbh} 匹配到 {@code t_land} 的行数（口径同 {@link #orphanRows}）。
     *
     * <p>它与 {@link #orphanRows} 一起覆盖了所有「走到宗地校验这一步」的行，
     * 两个数字相加就是「本文件里被认为格式合法、需要判断归属的行数」——
     * 用户据此一眼看出「这批数据里有多少是能直接落位的」。
     */
    private int matchedLandRows;

    /**
     * 孤儿清单：{@code crzdbh} 在 {@code t_land} 里查不到的行。
     *
     * <p>作用见类注释：既是「允许导入」时的告知清单，也是「不允许导入」时的待办清单。
     * 一条行只出现一次（同一行不会因为多处校验重复登记）。
     */
    private List<OrphanRow> orphans = new ArrayList<>();

    // ---------------- 策略与安全阀 ----------------

    /** 本次使用的重复处理策略 */
    private String duplicateStrategy;

    /** 重复处理策略的中文说明 */
    private String duplicateStrategyLabel;

    /**
     * 是否会因为存在错误而整批不入库。
     *
     * <p>{@code skipErrorRows=false} 且有错误时为 {@code true} ——
     * 前端据此提示「本次不会写入任何数据」。
     *
     * <p>注意：{@code allowOrphan=true} 时孤儿<b>不算</b>错误，
     * 因此「只有孤儿、没有其它错误」不会让 {@code aborted} 变成 true。
     */
    private boolean aborted;

    // ---------------- 回执清单 ----------------

    /** 错误清单（有错误时整批不入库，除非显式跳过错误行） */
    private List<RowError> errors = new ArrayList<>();

    /** 警告清单（不影响入库，例如「编号书写不一致，疑似应为 xxx」「取值已自动归一化」） */
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

    /**
     * 登记一条孤儿行。
     *
     * <p>同时把 {@link #orphanRows} +1：两个字段虽然冗余，但前端要分别用 ——
     * 列表顶部显示「孤儿 N 条」只要一个数字，抽屉里展开才需要明细。
     */
    public void addOrphan(int rowNum, String crzdbh, String ptxmmc, String reason) {
        orphans.add(new OrphanRow(rowNum, crzdbh, ptxmmc, reason));
        orphanRows++;
    }

    /** 错误条数 */
    public int getErrorCount() {
        return errors.size();
    }

    /** 有没有任何需要用户交代的异常（错误 或 孤儿） */
    public boolean hasProblem() {
        return !errors.isEmpty() || orphanRows > 0;
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

    /**
     * @Description: 一条孤儿行（{@code crzdbh} 在 t_land 中不存在）
     *
     * <p>字段刻意只保留「用户能拿去干活」的四项：行号（回 Excel 定位）、
     * 宗地编号（去找对应岗位催录宗地）、配套项目名称（确认是哪条配套）、
     * 原因（说明为什么算孤儿，含「疑似应为 xxx」这种提示）。
     */
    @Data
    public static class OrphanRow implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Excel 行号（1 基） */
        private int rowNum;

        /** 该行填写的出让宗地编号 */
        private String crzdbh;

        /** 该行的配套项目名称 */
        private String ptxmmc;

        /** 判定为孤儿的说明（含「与库内 xxx 疑似同一宗地」这类提示） */
        private String reason;

        public OrphanRow() {
        }

        public OrphanRow(int rowNum, String crzdbh, String ptxmmc, String reason) {
            this.rowNum = rowNum;
            this.crzdbh = crzdbh;
            this.ptxmmc = ptxmmc;
            this.reason = reason;
        }
    }
}
