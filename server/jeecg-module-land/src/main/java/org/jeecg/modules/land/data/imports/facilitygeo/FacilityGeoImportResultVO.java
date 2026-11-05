package org.jeecg.modules.land.data.imports.facilitygeo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 配套地块图形（GeoJSON）导入 - 结果回执（预览与入库共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.0
 *
 * <p>与 {@code LandGeoImportResultVO}（经营性用地 GeoJSON 导入）同一结构，差异点：
 * <ul>
 *   <li>匹配键是 <b>PTXMMC（配套项目名称，归一后）</b>——配套 shp 成果属性里
 *       没有 CRZDBH（实测 配套数据0922.geojson：1664 个 Feature 只有 PTXMMC）；</li>
 *   <li><b>同名跨宗地多条记录是业务常态</b>（一条道路在多个宗地各记一条），
 *       导入时全部更新，{@code updatedRows} 按记录计、
 *       {@code multiRecordNames} 注明涉及几条；</li>
 *   <li>没有 inserted —— 配套图形只更新已存在的记录，名称匹配不上的一律警告跳过。</li>
 * </ul>
 */
@Data
public class FacilityGeoImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------------- 解析计数 ----------------

    /** 解析到的 Feature 总数 */
    private int totalFeatures;

    /** 校验通过且匹配到配套记录的 Feature 数 */
    private int validFeatures;

    /** 实际更新图形的<b>记录</b>条数（预览时为 0） */
    private int updatedRows;

    /** 预览时「将会更新图形」的记录条数（入库时与 updatedRows 一致） */
    private int willUpdateRows;

    /** 图形与库内现值一致、无需更新的记录条数 */
    private int unchangedRows;

    /** 未匹配到配套记录的<b>名称</b>个数（按名称聚合警告） */
    private int unmatchedCount;

    /** 同名匹配到多条记录的名称个数（这些名称每次更新会同时改 N 条记录） */
    private int multiRecordNames;

    // ---------------- 安全阀 ----------------

    /** 是否因为存在错误而整批不入库（与 Excel 导入同一安全阀：有错一行不写） */
    private boolean aborted;

    // ---------------- 回执清单 ----------------

    /** 错误清单（有错误时整批不入库） */
    private List<FeatureError> errors = new ArrayList<>();

    /** 警告清单（不影响入库：名称未匹配等） */
    private List<String> warnings = new ArrayList<>();

    /** 提示清单（不影响入库：图形未变化、同名多记录说明等） */
    private List<String> notices = new ArrayList<>();

    // ==================================================================
    // 便捷方法
    // ==================================================================

    /** 追加一条错误 */
    public void addError(int featureIndex, String ptxmmc, String message) {
        errors.add(new FeatureError(featureIndex, ptxmmc, message));
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

        /** Feature 在文件中的序号（1 基，按 features 数组顺序） */
        private int featureIndex;

        /** 该 Feature 的配套项目名称（可能为空 —— PTXMMC 属性缺失时） */
        private String ptxmmc;

        /** 原因 */
        private String message;

        public FeatureError() {
        }

        public FeatureError(int featureIndex, String ptxmmc, String message) {
            this.featureIndex = featureIndex;
            this.ptxmmc = ptxmmc;
            this.message = message;
        }
    }
}
