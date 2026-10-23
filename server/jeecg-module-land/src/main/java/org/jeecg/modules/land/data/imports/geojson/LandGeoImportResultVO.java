package org.jeecg.modules.land.data.imports.geojson;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 地块图形（GeoJSON）导入 - 结果回执（预览与入库共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.0
 *
 * <p>与 {@link org.jeecg.modules.land.data.imports.LandImportResultVO}（Excel 批量导入）同一设计：
 * 「预览」与「入库」使用<b>同一个结构</b>，前端只渲染一套结果面板：
 * <pre>
 *   预览：updatedRows 恒为 0，其余字段照常给出
 *         （用户先看「共多少个地块、哪些能更新、哪些没匹配上、错在哪」）
 *   入库：updatedRows 是真正写进库的地块数
 * </pre>
 *
 * <p>与 Excel 导入的结果差异（由业务决定，不是字段漏配）：
 * <ul>
 *   <li>没有 insertedRows —— GeoJSON 导入只给<b>已存在</b>的宗地补/换图形，
 *       编号匹配不上的一律按「未匹配」警告处理，不新建宗地；</li>
 *   <li>没有 duplicateStrategy —— 本功能没有「重复怎么办」的分支，
 *       文件内同一编号出现多个 Feature 属于数据问题，直接报错让用户处理文件；</li>
 *   <li>多一个 unchangedRows —— 库里存的图形与导入的图形（归一后）一字不差时
 *       跳过不写，避免把 800 条全量重导一遍、变更履历被无意义的「导入」刷屏。</li>
 * </ul>
 */
@Data
public class LandGeoImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------------- 解析计数 ----------------

    /** 解析到的 Feature 总数 */
    private int totalFeatures;

    /** 校验通过、且匹配到库内宗地的 Feature 数（预览时 = willUpdateRows + unchangedRows） */
    private int validFeatures;

    /** 实际更新图形的条数（预览时为 0） */
    private int updatedRows;

    /** 预览时「将会更新图形」的条数（入库时与 updatedRows 一致） */
    private int willUpdateRows;

    /** 图形与库内现值完全一致（归一后字符串相同）、无需更新的条数 */
    private int unchangedRows;

    /** 未匹配到库内宗地的 Feature 数（记为警告，不影响其它 Feature 导入） */
    private int unmatchedCount;

    // ---------------- 安全阀 ----------------

    /**
     * 是否因为存在错误而整批不入库。
     *
     * <p>与 Excel 导入同一口径：有错默认一行都不写（预览时为 true 表示
     * 「本次不会写入任何数据」），前端据此禁用确认按钮。
     */
    private boolean aborted;

    // ---------------- 回执清单 ----------------

    /** 错误清单（有错误时整批不入库） */
    private List<FeatureError> errors = new ArrayList<>();

    /**
     * 警告清单（不影响入库）。
     *
     * <p>目前只有一类：「编号 … 未匹配到系统中的宗地，已跳过」——
     * 通常是宗地还没录入、或文件里的编号与库内写法不一致。
     */
    private List<String> warnings = new ArrayList<>();

    /** 提示清单（不影响入库，例如「图形与库内一致、跳过 N 条」「重复编号已报错」） */
    private List<String> notices = new ArrayList<>();

    // ==================================================================
    // 便捷方法
    // ==================================================================

    /** 追加一条错误 */
    public void addError(int featureIndex, String crzdbh, String message) {
        errors.add(new FeatureError(featureIndex, crzdbh, message));
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
     * @Description: 一个 Feature 的错误
     */
    @Data
    public static class FeatureError implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Feature 在文件中的序号（1 基，按 features 数组顺序）。
         * GeoJSON 在文本编辑器里没有「行号」可对，用序号 + 编号定位。
         */
        private int featureIndex;

        /** 该 Feature 的出让宗地编号（可能为空 —— CRZDBH 属性缺失时） */
        private String crzdbh;

        /** 原因 */
        private String message;

        public FeatureError() {
        }

        public FeatureError(int featureIndex, String crzdbh, String message) {
            this.featureIndex = featureIndex;
            this.crzdbh = crzdbh;
            this.message = message;
        }
    }
}
