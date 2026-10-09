package org.jeecg.modules.land.archive.handover.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.springframework.stereotype.Component;

/**
 * @Description: 道路交付及养护协议移交事项-当前登录用户支撑
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>与收发文模块的 {@code DocSupport}、台账模块的 {@code LedgerSupport} 同一写法：
 * 把「当前登录人」的获取收敛到一个组件里，不在各处散落 {@code SecurityUtils} 调用；
 * 审计列由服务端显式写（jeecg 的 {@code MybatisInterceptor} 在本工程启动模块里没有注册）。
 */
@Slf4j
@Component
public class HandoverSupport {

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

    /** 当前登录账号 */
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

    /** 姓名取不到时给一个明确占位，避免导出里出现空白单元格 */
    public static String nameOrPlaceholder(String name) {
        return StringUtils.isBlank(name) ? "未知" : name;
    }
}
