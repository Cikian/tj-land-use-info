package org.jeecg.modules.land.archive.completion.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.archive.completion.entity.CompletionArchive;
import org.jeecg.modules.land.archive.completion.enums.DigitizeStatus;
import org.springframework.stereotype.Component;

/**
 * @Description: 竣工验收项目历史工程资料数字化档案-当前登录用户与数字化进度支撑
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>写法与第 7 项台账模块的 {@code LedgerSupport}、提级论证模块的
 * {@code EscalationAuditSupport} 一致：把「当前登录人」的获取收敛到一个组件里，
 * 不在各处散落 {@code SecurityUtils} 调用。
 *
 * <p><b>为什么审计字段要显式写</b>：jeecg 的 {@code MybatisInterceptor} 虽然会给
 * {@code create_by} / {@code create_time} 兜底，但它在本工程的启动模块里<b>没有注册</b>
 * （全局只在 {@code MybatisPlusSaasConfig} 里注册了分页与动态表名拦截器），
 * 所以服务端必须自己写审计列 —— 档案、收发文、提级论证、台账模块也都是这么做的。
 *
 * <p>数字化进度（{@code digitizePercent}）只在这里算一次，
 * 列表页的进度条与统计卡读的是同一个口径。
 */
@Slf4j
@Component
public class CompletionSupport {

    /** 数字化进度：未数字化 */
    private static final int PERCENT_NOT_STARTED = 0;
    /** 数字化进度：数字化中（按「做了一半」估算，只用来说明进度条位置，不参与任何统计口径） */
    private static final int PERCENT_PROCESSING = 50;
    /** 数字化进度：已数字化 */
    private static final int PERCENT_FINISHED = 100;

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
    // 数字化进度（唯一实现，Service / 导出 / 页面共用同一口径）
    // ==================================================================

    /**
     * 数字化状态 → 进度百分比。
     *
     * <p>它只用于前端进度条的视觉表达，<b>不参与任何统计口径</b>
     * （统计一律按 {@code digitize_status} 三值分组，见 Mapper 的 selectCountGroupByStatus）。
     * 「数字化中 = 50%」是一个约定俗成的可视化折中，不代表真实完成度 ——
     * 真实完成度要看 {@code page_count} 与已扫描页数，而后者由档案模块的文件决定。
     */
    public static int digitizePercent(String status) {
        if (DigitizeStatus.FINISHED.getValue().equals(status)) {
            return PERCENT_FINISHED;
        }
        if (DigitizeStatus.PROCESSING.getValue().equals(status)) {
            return PERCENT_PROCESSING;
        }
        return PERCENT_NOT_STARTED;
    }

    /**
     * 把一条档案的展示型统计补齐（非表字段）。
     *
     * <p>同时把 {@code digitize_status} 归一化：库里理论上不会为空（DDL 是 NOT NULL
     * 且默认「未数字化」），但手工 SQL 改库可能造出空串，这里统一兜到默认值，
     * 避免前端标签出现空白。
     */
    public static void fillDigitizeStats(CompletionArchive archive) {
        if (archive == null) {
            return;
        }
        if (StringUtils.isBlank(archive.getDigitizeStatus())) {
            archive.setDigitizeStatus(DigitizeStatus.defaultValue());
        }
        archive.setDigitizePercent(digitizePercent(archive.getDigitizeStatus()));
    }

    /** 姓名取不到时给一个明确占位，避免导出里出现空白单元格 */
    public static String nameOrPlaceholder(String name) {
        return StringUtils.isBlank(name) ? "未知" : name;
    }
}
