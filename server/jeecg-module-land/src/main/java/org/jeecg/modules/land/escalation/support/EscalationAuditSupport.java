package org.jeecg.modules.land.escalation.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.springframework.stereotype.Component;

/**
 * @Description: 提级论证-当前登录用户与审计字段支撑
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>写法与收发文模块的 {@code DocSupport} 一致：把「当前登录人」的获取收敛到一个组件里，
 * 不在各处散落 {@code SecurityUtils} 调用。
 *
 * <p><b>为什么审计字段要显式写</b>：jeecg 的 {@code MybatisInterceptor} 虽然会给
 * {@code createBy} / {@code createTime} 兜底，但它在本工程的启动模块里<b>没有注册</b>
 * （全局只在 {@code MybatisPlusSaasConfig} 里注册了分页与动态表名拦截器），
 * 所以服务端必须自己写审计列 —— 档案模块与数据模块也都是这么做的。
 *
 * <p><b>为什么姓名要冗余落库</b>：{@code recorder_name} / {@code latest_opinion_by}
 * 都是「留痕」字段，用户改名或离职后历史痕迹不该跟着变，因此登记时就把姓名写死。
 */
@Slf4j
@Component
public class EscalationAuditSupport {

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
     * <p>{@code LoginUser} 里只有部门编码（{@code orgCode} / {@code departIds}），
     * 没有部门名称列，本模块也没有引入用户表的联表查询：
     * 因此「记录人部门」优先落部门编码，取不到就留空。
     * 该列只用于展示与留痕，不参与任何统计口径，宁缺勿错比猜一个部门名更好。
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
}
