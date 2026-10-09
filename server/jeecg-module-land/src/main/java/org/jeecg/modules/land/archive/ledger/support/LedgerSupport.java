package org.jeecg.modules.land.archive.ledger.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.archive.ledger.entity.RoadAcceptanceLedger;
import org.jeecg.modules.land.archive.ledger.enums.LedgerMaterial;
import org.springframework.stereotype.Component;

/**
 * @Description: 道路设施验收及移交资料台账-当前登录用户与资料统计支撑
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>写法与收发文模块的 {@code DocSupport}、提级论证模块的 {@code EscalationAuditSupport} 一致：
 * 把「当前登录人」的获取收敛到一个组件里，不在各处散落 {@code SecurityUtils} 调用。
 *
 * <p><b>为什么审计字段要显式写</b>：jeecg 的 {@code MybatisInterceptor} 虽然会给
 * {@code create_by} / {@code create_time} 兜底，但它在本工程的启动模块里<b>没有注册</b>
 * （全局只在 {@code MybatisPlusSaasConfig} 里注册了分页与动态表名拦截器），
 * 所以服务端必须自己写审计列 —— 档案、收发文、提级论证模块也都是这么做的。
 *
 * <p>资料填报数（{@code material_count}）与展示用的 {@code materialTotal} /
 * {@code materialMissing} 都只在这里算，三处口径不可能不一致。
 */
@Slf4j
@Component
public class LedgerSupport {

    /** 13 类资料的总数（台账页「资料 3/13」的分母） */
    public static final int MATERIAL_TOTAL = LedgerMaterial.values().length;

    /** 当前登录用户；无登录上下文时返回 null（定时任务 / 单元测试场景） */
    public LoginUser currentUser() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            if (principal instanceof LoginUser) {
                return (LoginUser) principal;
            }
        } catch (Exception e) {
            log.debug("获取当前登录用户失败：{}", e.getMessage());
        }
        return null;
    }

    /** 当前登录账号，无上下文时返回 null */
    public String currentUsername() {
        LoginUser user = currentUser();
        return user == null ? null : user.getUsername();
    }

    /** 当前登录人姓名，取不到时回退为账号 */
    public String currentRealname() {
        LoginUser user = currentUser();
        if (user == null) {
            return null;
        }
        return StringUtils.isNotBlank(user.getRealname()) ? user.getRealname() : user.getUsername();
    }

    /**
     * 当前登录人部门。
     *
     * <p>{@code LoginUser} 里只有部门编码（{@code orgCode} / {@code departIds}），没有部门名称，
     * 本模块也没有引入用户表联查：因此优先落部门编码，取不到就留空。
     * 该列只用于展示，不参与任何统计口径，宁缺勿错。
     */
    public String currentDept() {
        LoginUser user = currentUser();
        if (user == null) {
            return null;
        }
        if (StringUtils.isNotBlank(user.getOrgCode())) {
            return user.getOrgCode();
        }
        return StringUtils.isNotBlank(user.getDepartIds()) ? user.getDepartIds() : null;
    }

    // ==================================================================
    // 资料统计（唯一实现，Service / 导出 / 页面共用同一口径）
    // ==================================================================

    /** 勾选值归一化：null 与其它任何非 1 的值都算未勾选 */
    public static int flag(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    /**
     * 把一条台账的 13 类资料统计补齐并回写三个字段。
     *
     * <ul>
     *   <li>{@code materialCount} —— 落库的冗余列（13 列勾选之和）；</li>
     *   <li>{@code materialTotal} —— 非表字段，恒为 13（前端「资料 3/13」的分母）；</li>
     *   <li>{@code materialMissing} —— 非表字段，13 − 已归集（台账页「待补资料」列）。</li>
     * </ul>
     *
     * <p>同时把 13 个勾选列的 null 归一成 0，避免前端拿到 null 时勾选框状态不确定。
     */
    public static void fillMaterialStats(RoadAcceptanceLedger ledger) {
        if (ledger == null) {
            return;
        }
        int count = LedgerMaterial.countMaterials(ledger);
        ledger.setMaterialCount(count);
        ledger.setMaterialTotal(MATERIAL_TOTAL);
        ledger.setMaterialMissing(MATERIAL_TOTAL - count);
    }

    /** 姓名取不到时给一个明确占位，避免导出里出现空白单元格 */
    public static String nameOrPlaceholder(String name) {
        return StringUtils.isBlank(name) ? "未知" : name;
    }
}
