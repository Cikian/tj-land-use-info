package org.jeecg.modules.land.data.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @Description: 数据管理 - 公共支撑（当前登录人 / 文本清洗 / 业务类型）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>把「当前登录人」与「文本/路径清洗」收敛到一个组件里，
 * 与档案模块的 {@code CompletionSupport}、台账模块的 {@code LedgerSupport}、
 * 批量导入模块的 {@code LandImportSupport} 保持同一写法：
 * 业务层不直接散落 {@code SecurityUtils} 调用，测试里也好替换。
 */
@Slf4j
@Component
public class DataSupport {

    // ==================================================================
    // 一、业务类型（跨业务的数据管理统一口径）
    // ==================================================================

    /** 业务类型：经营性用地（宗地） */
    public static final String BIZ_LAND = "land";
    /** 业务类型：配套项目 */
    public static final String BIZ_FACILITY = "facility";
    /** 业务类型：环节进度 */
    public static final String BIZ_PROCESS = "process";
    /** 业务类型：附件 */
    public static final String BIZ_ATTACHMENT = "attachment";

    // ==================================================================
    // 二、当前登录人
    // ==================================================================

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

    /** 姓名取不到时给一个明确占位，避免列表/导出里出现空白单元格 */
    public static String nameOrPlaceholder(String name) {
        return StringUtils.isBlank(name) ? "未知" : name;
    }

    /** 编辑人字段的取值：取账号，无上下文时留空（不写「未知」，避免把占位符写进库） */
    public String currentEditor() {
        String username = currentUsername();
        return StringUtils.isBlank(username) ? null : username;
    }

    /** 创建账号字段的取值 */
    public String currentAccount() {
        return currentEditor();
    }

    /**
     * 客户端 IP（变更留痕用）。
     *
     * <p>依次尝试 {@code X-Forwarded-For} / {@code X-Real-IP} / {@code RemoteAddr}：
     * 反向代理后面拿到的 RemoteAddr 是代理的地址，对排查没意义。
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwarded)) {
            // X-Forwarded-For 可能是「客户端, 代理1, 代理2」，第一个才是真实客户端
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            String trimmed = first.trim();
            if (!trimmed.isEmpty() && !"unknown".equalsIgnoreCase(trimmed)) {
                return capLength(trimmed, 64);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.isNotBlank(realIp) && !"unknown".equalsIgnoreCase(realIp.trim())) {
            return capLength(realIp.trim(), 64);
        }
        return capLength(request.getRemoteAddr(), 64);
    }

    // ==================================================================
    // 三、文本清洗
    // ==================================================================

    /**
     * 剔除 ASCII 10~13 控制字符（{@code \n \r \v \f}）。
     *
     * <p>与批量导入模块同一口径（也沿用旧系统 {@code importData} 里
     * {@code for(i=10;i<14;i++)} 那个循环）：从 Word/网页粘贴进表单的内容
     * 经常夹着换行与软回车，不剔掉会污染库内文本，还会让「编号是否重复」这类
     * 比对失效（末尾多个不可见字符，看起来一样的两个值并不相等）。
     */
    public static String stripControlChars(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 10 && c <= 13) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** 清洗 + 去首尾空白 + 空串归一化为 null */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = stripControlChars(value).trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** 截断到指定长度（0 或负数表示不限制） */
    public static String capLength(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /** 清洗并按列宽截断 */
    public static String cleanAndCap(String value, int maxLength) {
        return capLength(clean(value), maxLength);
    }

    /**
     * 把用户录入名称里的**半角括号统一成中文括号**，其余字符不动。
     *
     * <p><b>★ 为什么要归一（不是洁癖，是在防重复记录）</b>：
     * 宗地编号、配套项目名称这些「用户录入的业务标识」里都带括号
     * （{@code 津西青（挂）2024-01号}、{@code 义安路（永顺道-永尚道）}）。
     * 半角 {@code ()} 与中文 {@code （）} 看起来几乎一样，但**是不同的字符**，
     * 于是同一宗地会变成两条记录，判重失效、附件也会挂到不同的 id 上。
     *
     * <p>实测库里就有这种情况：847 条宗地编号里 846 条用中文括号，
     * 2 条用了半角（{@code 津西浯(挂)2025-07}、{@code 津辰青(挂)2025-008号}）。
     *
     * <p><b>★ 只在「写入」侧归一</b>：查询与匹配仍然按归一方后的值比较，
     * 所以历史数据必须一并刷成中文括号（见 sql/data/11_normalize_brackets.sql）。
     * 若只在读侧宽松匹配（两种都认），库里会长期并存两种写法，
     * 每次查询都要做一次「宽松比较」，且导出的表格里两种括号混着很难看。
     *
     * <p><b>★ 旧系统本来就是中文括号</b>：旧存储目录名如
     * {@code 津北辰仓（挂）2018-015}、{@code 义安路（永顺道-永尚道）} 全用中文括号，
     * 所以归一成中文括号是「向旧系统口径对齐」，不是新发明的规则。
     *
     * @param value 原始文本；null 原样返回
     * @return 括号已改为中文括号的文本
     */
    public static String normalizeBrackets(String value) {
        if (value == null) {
            return null;
        }
        if (value.indexOf('(') < 0 && value.indexOf(')') < 0) {
            // 绝大多数值没有半角括号，直接原样返回，省掉一次字符串构造
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                sb.append('（');
            } else if (c == ')') {
                sb.append('）');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 清洗 + 括号归一 + 按列宽截断（用户录入的名称类字段统一走这个） */
    public static String cleanName(String value, int maxLength) {
        return capLength(normalizeBrackets(clean(value)), maxLength);
    }

    // ==================================================================
    // 四、其它小工具
    // ==================================================================

    /** 今天（00:00:00），逾期判断的基准 */
    public static Date today() {
        return new Date();
    }

    /** yyyy-MM-dd 格式化（错误信息与摘要里用） */
    public static String formatDate(Date date) {
        return date == null ? "" : new SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    /** yyyy-MM-dd HH:mm:ss 格式化 */
    public static String formatDateTime(Date date) {
        return date == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    /**
     * 把可能为 null 的值转成「可比较的展示文本」。
     *
     * <p>变更留痕的 before/after 用它：{@code null} 与空串都显示成
     * 「（空）」，让用户在履历里一眼看出「原来是空、现在有值了」，
     * 而不是看到两格空白分不清是「没变」还是「从无到有」。
     */
    public static String display(Object value) {
        if (value == null) {
            return "（空）";
        }
        if (value instanceof Date) {
            return formatDate((Date) value);
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "（空）" : text;
    }

    /**
     * 两个值是否相等（用于决定要不要记一条变更）。
     *
     * <p>数值类型统一按 {@code BigDecimal} 的 compareTo 语义比 ——
     * {@code new BigDecimal("1.20").equals(new BigDecimal("1.2"))} 是 false，
     * 但业务上它们是同一个金额，用 equals 会产生大量「1.20 → 1.2」的假变更。
     */
    public static boolean valueEquals(Object before, Object after) {
        if (before == null && after == null) {
            return true;
        }
        if (before == null || after == null) {
            // 一边为空：另一边也要是「空文本」才算相等
            Object present = before == null ? after : before;
            if (present instanceof String) {
                return ((String) present).trim().isEmpty();
            }
            return false;
        }
        if (before instanceof java.math.BigDecimal && after instanceof java.math.BigDecimal) {
            return ((java.math.BigDecimal) before).compareTo((java.math.BigDecimal) after) == 0;
        }
        if (before instanceof Number && after instanceof Number) {
            return new java.math.BigDecimal(String.valueOf(before))
                    .compareTo(new java.math.BigDecimal(String.valueOf(after))) == 0;
        }
        return String.valueOf(before).trim().equals(String.valueOf(after).trim());
    }
}
