package org.jeecg.modules.land.archive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.archive.entity.ArchiveLog;
import org.jeecg.modules.land.archive.mapper.ArchiveLogMapper;
import org.jeecg.modules.land.archive.service.IArchiveLogService;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @Description: 档案操作记录 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
@Slf4j
@Service
public class ArchiveLogServiceImpl extends ServiceImpl<ArchiveLogMapper, ArchiveLog> implements IArchiveLogService {

    /** detail 字段是 varchar(2000)，超长直接截断，避免插入失败把业务操作带崩 */
    private static final int DETAIL_MAX_LENGTH = 2000;

    @Override
    public void record(String archiveId, String fileId, String action, String detail, String bizKey) {
        try {
            ArchiveLog entity = new ArchiveLog()
                    .setArchiveId(archiveId)
                    .setFileId(fileId)
                    .setAction(action)
                    .setDetail(truncate(detail))
                    .setBizKey(truncate(bizKey, 200))
                    .setOperateTime(new Date());
            fillOperator(entity);
            save(entity);
        } catch (Exception e) {
            // 记日志失败不能影响业务：打 warn 继续
            log.warn("写档案操作记录失败：archiveId={}, fileId={}, action={}, 原因={}",
                    archiveId, fileId, action, e.getMessage());
        }
    }

    @Override
    public List<ArchiveLog> queryByArchiveId(String archiveId) {
        if (StringUtils.isBlank(archiveId)) {
            return Collections.emptyList();
        }
        return list(new QueryWrapper<ArchiveLog>()
                .eq("archive_id", archiveId)
                .orderByDesc("operate_time"));
    }

    @Override
    public List<ArchiveLog> queryByFileId(String fileId) {
        if (StringUtils.isBlank(fileId)) {
            return Collections.emptyList();
        }
        return list(new QueryWrapper<ArchiveLog>()
                .eq("file_id", fileId)
                .orderByDesc("operate_time"));
    }

    /** 从 shiro 上下文与当前请求里补齐操作人 / IP */
    private void fillOperator(ArchiveLog entity) {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            if (principal instanceof LoginUser) {
                LoginUser user = (LoginUser) principal;
                entity.setOperateBy(user.getUsername());
                entity.setOperateName(user.getRealname());
            }
        } catch (Exception e) {
            log.debug("获取当前登录用户失败：{}", e.getMessage());
        }
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                entity.setIp(getClientIp(request));
            }
        } catch (Exception e) {
            log.debug("获取请求上下文失败：{}", e.getMessage());
        }
    }

    /**
     * 取客户端 IP。
     * 注意：旧 tj-sfw 的 /pdf/render 就是因为直接使用前端传来的路径而存在任意文件读取漏洞，
     * 这里对 X-Forwarded-For 也只取第一段并做长度截断，只用于日志展示，不参与任何鉴权判断。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            ip = comma > 0 ? ip.substring(0, comma) : ip;
        } else {
            ip = request.getRemoteAddr();
        }
        return truncate(ip, 64);
    }

    private static String truncate(String value) {
        return truncate(value, DETAIL_MAX_LENGTH);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
