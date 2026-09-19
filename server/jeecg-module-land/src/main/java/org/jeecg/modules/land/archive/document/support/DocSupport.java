package org.jeecg.modules.land.archive.document.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.archive.document.mapper.SysUserLookupMapper;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.service.IFacilityService;
import org.jeecg.modules.land.data.service.ILandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @Description: 收发文模块的当前用户 / 关联项目支撑
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>旧 tj-sfw 的 {@code UserCommonApiImpl} 是自己写 SQL 查 sys_user，
 * 新系统沿用同样的轻量做法（见 {@link SysUserLookupMapper} 的说明），
 * 但收敛到一个组件里，不在各处散落 SQL。
 *
 * <p>姓名一律<b>冗余落库</b>到 {@code flow.handler_name} /
 * {@code doc.current_handler_name}——这样流转时间轴不需要回表查用户，
 * 也不会因为用户改名或离职导致历史痕迹丢失。
 */
@Slf4j
@Component
public class DocSupport {

    @Autowired
    private SysUserLookupMapper sysUserLookupMapper;

    @Autowired
    private IFacilityService facilityService;

    @Autowired
    private ILandService landService;

    // ==================================================================
    // 当前登录用户
    // ==================================================================

    /** 当前登录用户；无登录上下文时返回 null（定时任务/测试场景） */
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

    // ==================================================================
    // 用户姓名
    // ==================================================================

    /**
     * 按账号查姓名。
     *
     * @param username 账号
     * @return 姓名；查不到时回退返回账号本身（保证时间轴不会显示空白）
     */
    public String realnameOf(String username) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        String account = username.trim();
        try {
            Map<String, Object> row = sysUserLookupMapper.selectByUsername(account);
            if (row != null) {
                Object realname = row.get("realname");
                if (realname != null && StringUtils.isNotBlank(String.valueOf(realname))) {
                    return String.valueOf(realname);
                }
            }
        } catch (Exception e) {
            log.warn("按账号查询用户姓名失败：username={}, 原因={}", account, e.getMessage());
        }
        return account;
    }

    /**
     * 校验账号存在且可用，并返回姓名（流转指派时使用）。
     *
     * @throws JeecgBootException 账号不存在或已停用
     */
    public String requireRealname(String username) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        String account = username.trim();
        Map<String, Object> row;
        try {
            row = sysUserLookupMapper.selectByUsername(account);
        } catch (Exception e) {
            log.warn("校验处理人失败：username={}, 原因={}", account, e.getMessage());
            // 查询异常不阻断业务，退化为直接用账号
            return account;
        }
        if (row == null) {
            throw new JeecgBootException("指定的处理人「" + account + "」不存在或已停用");
        }
        Object realname = row.get("realname");
        return realname == null || StringUtils.isBlank(String.valueOf(realname))
                ? account : String.valueOf(realname);
    }

    // ==================================================================
    // 业务关联填充（先选宗地 → 再联动选配套项目）
    // ==================================================================

    /** 取配套项目，不存在时抛业务异常 */
    public Facility requireFacility(String facilityId) {
        if (StringUtils.isBlank(facilityId)) {
            throw new JeecgBootException("请选择关联的配套项目");
        }
        Facility facility = facilityService.queryById(facilityId.trim());
        if (facility == null) {
            throw new JeecgBootException("关联的配套项目不存在，请重新选择");
        }
        return facility;
    }

    /**
     * 按出让宗地编号取宗地。
     *
     * @return 宗地；取不到返回 null（历史数据里存在「配套先录、宗地后录」的孤儿项目）
     */
    public Land queryLandByCrzdbh(String crzdbh) {
        if (StringUtils.isBlank(crzdbh)) {
            return null;
        }
        return landService.queryByCrzdbh(crzdbh.trim());
    }
}
