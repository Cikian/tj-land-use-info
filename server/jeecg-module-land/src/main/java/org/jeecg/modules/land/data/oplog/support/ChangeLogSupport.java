package org.jeecg.modules.land.data.oplog.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.modules.land.data.fieldconfig.DataFieldLabels;
import org.jeecg.modules.land.data.oplog.entity.DataChangeLog;
import org.jeecg.modules.land.data.oplog.mapper.DataChangeLogMapper;
import org.jeecg.modules.land.data.support.DataSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 数据变更留痕 - 字段级差异计算与落库支撑
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 这个类要解决的问题</b>：旧日志表 {@code xj_kjkfb_operationrecord}
 * 只记「添加了 N 条数据」这种一句话，<b>记不下改了哪个字段、改前改后是什么</b>。
 * 于是「这个出让金是谁从 1.2 改成 2.0 的」根本查不出来 —— 而数据管理里
 * 用户最常问的就是这句。
 *
 * <p><b>★ 字段中文名从哪来</b>：Java 反射拿不到「业务中文名」（实体上没有
 * {@code @Schema} 之类的注解，Lombok 生成的 getter 也不带注释），
 * 所以统一登记在 {@link DataFieldLabels}。名字本来就要写在前端表单上，
 * 服务层再写一遍不额外增加维护面，却换来「履历里显示的是人看得懂的名字」。
 * 该字典由 {@code DataChangeLogTest} 兜底核对（实体加了字段忘了登记会被测出来）。
 *
 * <p><b>★ 为什么用反射逐字段比对、而不是每个字段手写一行</b>：
 * 宗地 34 个字段、配套 53 个字段，手写一遍就是 87 行几乎相同的代码，
 * 而且<b>新增字段时必然会忘记补一行</b> —— 忘了的后果是
 * 「这个字段改了但履历里没有」，属于最隐蔽的一类缺失
 * （页面上一切正常，只有追溯时才发现缺）。反射让新增字段自动被覆盖。
 *
 * <p><b>为什么只记「真的变了」的字段</b>：一次编辑往往只改一两个字段，
 * 但如果把整条记录 30 多个字段都记下来，「履历」会变得没法看 ——
 * 用户要的是「这次改了什么」，不是「这条记录长什么样」。
 */
@Slf4j
@Component
public class ChangeLogSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 反射比对时跳过的字段。
     *
     * <p><b>审计与溯源列一律跳过</b>，理由是它们「每次改动都会变」或「表单根本不提交」，
     * 记进履历只会淹没真正的业务变更：
     * <ul>
     *   <li>{@code updateBy} / {@code updateTime}：每次修改都会变，
     *       而操作人与时间本来就单独有列（{@code operator} / {@code create_time}）；</li>
     *   <li>{@code createBy} / {@code createTime} / {@code createAccount}：
     *       创建信息，编辑时不该变（Service 也不会改它）；真正的风险是
     *       <b>编辑用的实体没有回填这些列</b>，于是 diff 会记出
     *       「createTime 2026-10-08 → （空）」这种假变更 ——
     *       用户看到会以为系统把创建时间清空了；</li>
     *   <li>{@code delFlag}：软删与恢复是<b>独立动作</b>（DELETE / RESTORE），
     *       由 Service 显式写一条带动作语义的履历。
     *       如果它参与 diff，那么「移除」这个动作会被记成一次
     *       「修改：delFlag 0 → 1」，动作筛选里就再也找不到「移除」了；</li>
     *   <li>{@code sourceId}：旧库主键，数据迁移时写下的溯源列，
     *       录入与编辑表单不带这个字段（DTO 搬运里也不赋值）；</li>
     *   <li>{@code serialVersionUID}：Lombok 生成的静态常量，本就被 static 判断挡掉，
     *       这里再列一次是为了让「跳过清单」一眼看全。</li>
     * </ul>
     */
    private static final java.util.Set<String> SKIP_FIELDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "updateBy", "updateTime",
            "createBy", "createTime", "createAccount",
            "delFlag", "sourceId", "serialVersionUID"));

    /** 字段缓存（按类缓存，避免每次比对都反射一遍） */
    private static final Map<Class<?>, List<java.lang.reflect.Field>> FIELD_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    private DataChangeLogMapper changeLogMapper;

    @Autowired
    private DataSupport dataSupport;

    // ==================================================================
    // 零、反射工具
    // ==================================================================

    /**
     * 收集一个类的全部实例字段（含父类），带缓存。
     *
     * <p>只取本模块的字段：{@code getDeclaredFields} 会包含 Lombok 生成的
     * 静态 {@code serialVersionUID}，由调用方按 static 过滤。
     */
    private static List<java.lang.reflect.Field> collectFields(Class<?> clazz) {
        List<java.lang.reflect.Field> cached = FIELD_CACHE.get(clazz);
        if (cached != null) {
            return cached;
        }
        List<java.lang.reflect.Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        FIELD_CACHE.put(clazz, fields);
        return fields;
    }

    /**
     * 读字段值。
     *
     * <p>★ 直接用 {@code field.setAccessible(true)} + {@code field.get()}，
     * <b>不走 getter</b>：Lombok 的 getter 都是 public，但
     * {@code @Accessors(chain = true)} 不改 getter 名称，反射调 getter 要拼方法名、
     * 还要处理 {@code isXxx}/{@code getXxx} 两种前缀，比直接读字段脆弱得多。
     * 读字段失败返回 null（不抛），因为一条读不出来的字段不该让整个保存失败。
     */
    private static Object readField(Object target, java.lang.reflect.Field field) {
        if (target == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            log.debug("读取字段失败：{}.{}，{}", target.getClass().getSimpleName(), field.getName(), e.getMessage());
            return null;
        }
    }

    /** 收集变更的字段名（测试用：核对实体字段是否都登记了中文名） */
    public List<String> changedFields(Object before, Object after) {
        Collector collector = collector("_test", "_test", "_test");
        collector.diff(before, after, java.util.Collections.<String, String>emptyMap());
        List<String> names = new ArrayList<>();
        for (ChangeItem item : collector.items) {
            names.add(item.getField());
        }
        return names;
    }

    // ==================================================================
    // 一、收集变更（链式 API，调用方按字段登记 before/after）
    // ==================================================================

    /**
     * 开始收集一次操作的变更。
     *
     * @param bizType 业务类型（land / facility / process / attachment）
     * @param bizId   业务主键
     * @param bizKey  业务可读键（宗地编号 / 配套项目名称）
     */
    public Collector collector(String bizType, String bizId, String bizKey) {
        return new Collector(bizType, bizId, bizKey);
    }

    /**
     * @Description: 变更收集器 —— 登记字段的「改前值 / 改后值」，只保留真正变化的字段
     */
    public class Collector {

        private final String bizType;
        private final String bizId;
        private final String bizKey;
        /** 保持登记顺序，履历里的字段顺序 = 表单上的字段顺序 */
        final List<ChangeItem> items = new ArrayList<>();

        Collector(String bizType, String bizId, String bizKey) {
            this.bizType = bizType;
            this.bizId = bizId;
            this.bizKey = bizKey;
        }

        /**
         * 登记一个字段。值相等（按业务语义比较，见
         * {@link DataSupport#valueEquals}）则自动忽略。
         *
         * @param field   字段名（与前端/导入模板的字段名一致）
         * @param label   中文字段名
         * @param before  改前值（新增时传 null）
         * @param after   改后值
         */
        public Collector add(String field, String label, Object before, Object after) {
            if (DataSupport.valueEquals(before, after)) {
                return this;
            }
            items.add(new ChangeItem(field, label, DataSupport.display(before), DataSupport.display(after)));
            return this;
        }

        /** 已登记的变更条数 */
        public int size() {
            return items.size();
        }

        /** 是否有变更 */
        public boolean hasChange() {
            return !items.isEmpty();
        }

        /**
         * 用反射把两个实体逐字段比一遍，自动登记有差异的字段。
         *
         * <p><b>★ 为什么用反射而不是「每个字段写一行 collector.add(...)」</b>：
         * 宗地 34 个字段、配套 53 个字段，手写一遍就是 87 行几乎相同的代码，
         * 而且<b>新增字段时必然会忘记补一行</b> —— 忘了的后果是「这个字段改了但履历里没有」，
         * 属于最隐蔽的一类缺失（页面上一切正常，只有追溯时才发现缺）。
         * 反射让「新增字段自动被覆盖」，代价是需要一个中文字段名字典
         * （{@link org.jeecg.modules.land.data.fieldconfig.DataFieldLabels}），
         * 而那份字典是靠测试兜底的（见 DataChangeLogTest）。
         *
         * <p>跳过这些字段：{@code serialVersionUID} 静态常量、
         * {@code updateBy}/{@code updateTime}（每次改都会变，记了等于噪音）。
         *
         * @param before 改前实体（可为 null = 新增）
         * @param after  改后实体
         * @param labels 字段中文名字典（{@code DataFieldLabels.landLabels()} 等）
         */
        public Collector diff(Object before, Object after, Map<String, String> labels) {
            if (after == null) {
                return this;
            }
            List<java.lang.reflect.Field> fields = collectFields(after.getClass());
            for (java.lang.reflect.Field field : fields) {
                String name = field.getName();
                if (SKIP_FIELDS.contains(name)
                        || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object beforeValue = readField(before, field);
                Object afterValue = readField(after, field);
                add(name, DataFieldLabels.label(labels, name), beforeValue, afterValue);
            }
            return this;
        }

        /**
         * 落库。
         *
         * @param action  动作（{@link DataChangeLog#ACTION_CREATE} 等）
         * @param summary 一句话摘要；传 null 时按动作与条数自动生成
         * @return 实际写入的记录（无变更时返回 null）
         */
        public DataChangeLog save(String action, String summary) {
            return save(action, summary, null);
        }

        /** 落库（带请求对象，用于记录操作 IP） */
        public DataChangeLog save(String action, String summary, HttpServletRequest request) {
            if (items.isEmpty() && !isAlwaysLogged(action)) {
                return null;
            }
            DataChangeLog record = new DataChangeLog();
            record.setBizType(bizType);
            record.setBizId(bizId);
            record.setBizKey(DataSupport.capLength(bizKey, 200));
            record.setAction(action);
            record.setChangeCount(items.size());
            record.setChangeDetail(toJson(items));
            record.setChangeSummary(DataSupport.capLength(
                    StringUtils.isNotBlank(summary) ? summary : defaultSummary(action), 500));
            record.setOperator(dataSupport.currentUsername());
            record.setOperatorName(dataSupport.currentRealname());
            record.setOperatorIp(DataSupport.clientIp(request));
            record.setCreateTime(new Date());
            try {
                changeLogMapper.insert(record);
            } catch (Exception e) {
                // ★ 留痕失败不该让一次成功的业务操作在用户眼里变成「保存失败」——
                //   旧系统在这一步也是 try/catch 吞掉的。
                log.warn("写数据变更留痕失败（不影响业务结果）：bizType={}, bizId={}, action={}, 原因={}",
                        bizType, bizId, action, e.getMessage());
                return null;
            }
            return record;
        }

        /** 摘要兜底 */
        private String defaultSummary(String action) {
            String what = actionText(action);
            if (items.isEmpty()) {
                return what;
            }
            if (items.size() == 1) {
                ChangeItem item = items.get(0);
                return what + "：" + item.label + " " + item.before + " → " + item.after;
            }
            return what + "：" + items.size() + " 个字段（"
                    + items.get(0).label + " 等）";
        }
    }

    /**
     * 这些动作即使「没有任何字段变化」也要留一条痕。
     *
     * <p>典型场景：软删 / 恢复 —— 它们本身不改业务字段，
     * 但用户需要看到「这条数据在某时刻被移除 / 被恢复」。
     */
    private boolean isAlwaysLogged(String action) {
        return DataChangeLog.ACTION_DELETE.equals(action)
                || DataChangeLog.ACTION_RESTORE.equals(action);
    }

    // ==================================================================
    // 二、解析（读履历用）
    // ==================================================================

    /**
     * 把 {@code change_detail} 的 JSON 解析成结构化列表，供前端直接渲染表格。
     *
     * <p>解析失败不抛异常，返回空列表：履历里一条坏 JSON 不该让整个页面打不开。
     */
    public List<Map<String, Object>> parseDetail(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.warn("变更明细 JSON 解析失败，已按空处理：{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 给一条履历补齐展示字段（明细列表 / 动作中文名 / 动作配色） */
    public DataChangeLog enrich(DataChangeLog record) {
        if (record == null) {
            return null;
        }
        record.setDetails(new ArrayList<Object>(parseDetail(record.getChangeDetail())));
        record.setActionText(actionText(record.getAction()));
        record.setActionColor(actionColor(record.getAction()));
        return record;
    }

    /** 批量补齐 */
    public List<DataChangeLog> enrichAll(List<DataChangeLog> records) {
        if (records == null || records.isEmpty()) {
            return records;
        }
        for (DataChangeLog record : records) {
            enrich(record);
        }
        return records;
    }

    // ==================================================================
    // 三、动作文案与配色（唯一实现，页面与导出共用）
    // ==================================================================

    /** 动作 → 中文名 */
    public static String actionText(String action) {
        if (action == null) {
            return "变更";
        }
        switch (action) {
            case DataChangeLog.ACTION_CREATE:
                return "新增";
            case DataChangeLog.ACTION_UPDATE:
                return "修改";
            case DataChangeLog.ACTION_DELETE:
                return "移除";
            case DataChangeLog.ACTION_RESTORE:
                return "恢复";
            case DataChangeLog.ACTION_IMPORT:
                return "批量导入";
            case DataChangeLog.ACTION_UPLOAD:
                return "附件上传";
            case DataChangeLog.ACTION_ATTACH_DELETE:
                return "附件删除";
            default:
                return action;
        }
    }

    /**
     * 动作 → a-tag 颜色。
     *
     * <p>★ 绝不能返回 {@code "default"}：它不是 antd 预设色，会被当成自定义色，
     * 结果是白字 + 非法背景被丢弃 → 白底白字看不见
     * （档案模块踩过这个坑，见《档案管理-实现说明》7.5）。无强调色就返回 null。
     */
    public static String actionColor(String action) {
        if (action == null) {
            return null;
        }
        switch (action) {
            case DataChangeLog.ACTION_CREATE:
                return "green";
            case DataChangeLog.ACTION_UPDATE:
                return "blue";
            case DataChangeLog.ACTION_DELETE:
                return "red";
            case DataChangeLog.ACTION_RESTORE:
                return "orange";
            case DataChangeLog.ACTION_IMPORT:
                return "cyan";
            case DataChangeLog.ACTION_UPLOAD:
                return "purple";
            default:
                return null;
        }
    }

    /** 全部动作（下拉筛选用） */
    public static List<Map<String, String>> actionOptions() {
        List<Map<String, String>> options = new ArrayList<>();
        for (String action : new String[]{
                DataChangeLog.ACTION_CREATE, DataChangeLog.ACTION_UPDATE,
                DataChangeLog.ACTION_DELETE, DataChangeLog.ACTION_RESTORE,
                DataChangeLog.ACTION_IMPORT, DataChangeLog.ACTION_UPLOAD,
                DataChangeLog.ACTION_ATTACH_DELETE}) {
            Map<String, String> option = new LinkedHashMap<>();
            option.put("value", action);
            option.put("label", actionText(action));
            options.add(option);
        }
        return options;
    }

    // ==================================================================
    // 四、内部
    // ==================================================================

    private String toJson(List<ChangeItem> items) {
        try {
            return JSON.writeValueAsString(items);
        } catch (Exception e) {
            log.warn("变更明细序列化失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * @Description: 一条字段级变更
     *
     * <p>字段名与 {@code change_detail} 的 JSON 键一一对应，前端直接绑。
     */
    public static class ChangeItem {

        /** 字段名 */
        private String field;
        /** 中文字段名 */
        private String label;
        /** 改前值（已格式化为展示文本） */
        private String before;
        /** 改后值 */
        private String after;

        public ChangeItem() {
        }

        public ChangeItem(String field, String label, String before, String after) {
            this.field = field;
            this.label = label;
            this.before = before;
            this.after = after;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getBefore() {
            return before;
        }

        public void setBefore(String before) {
            this.before = before;
        }

        public String getAfter() {
            return after;
        }

        public void setAfter(String after) {
            this.after = after;
        }
    }
}
