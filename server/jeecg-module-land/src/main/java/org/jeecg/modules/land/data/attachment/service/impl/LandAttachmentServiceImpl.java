package org.jeecg.modules.land.data.attachment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.data.attachment.entity.LandAttachment;
import org.jeecg.modules.land.data.attachment.mapper.LandAttachmentMapper;
import org.jeecg.modules.land.data.attachment.service.ILandAttachmentService;
import org.jeecg.modules.land.data.attachment.vo.AttachmentQueryDTO;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.mapper.FacilityMapper;
import org.jeecg.modules.land.data.mapper.LandMapper;
import org.jeecg.modules.land.data.oplog.entity.DataChangeLog;
import org.jeecg.modules.land.data.oplog.support.ChangeLogSupport;
import org.jeecg.modules.land.data.process.entity.FacilityProcess;
import org.jeecg.modules.land.data.process.mapper.FacilityProcessMapper;
import org.jeecg.modules.land.data.support.DataSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 数据管理统一附件 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>三件事按顺序做：<b>① 校验（写侧严格）→ ② 落库 → ③ 展示字段与安全读盘</b>。
 *
 * <p><b>★ 关于「附件类型允许集合为什么写死在 Java 里，而不去读
 * {@code sys_dict.land_attach_type}」</b>（三条理由，缺一条都站不住）：
 * <ol>
 *   <li><b>编译期依赖不允许</b>：读字典要用 jeecg 的 {@code ISysBaseAPI}，
 *       它在 {@code jeecg-system-local-api} 模块里，业务模块
 *       （jeecg-module-land）的编译期依赖里没有它（同一取舍见
 *       {@code SysUserLookupMapper} 的类注释）；</li>
 *   <li><b>可用性</b>：附件表与字典表是两份脚本。若用户先建了附件表、
 *       还没执行 {@code 01_data_dict.sql}，读字典校验的结果就是「任何类型都不合法」——
 *       上传功能整体不可用，且报错信息完全指不到真正的原因；</li>
 *   <li><b>与本模块既有做法一致</b>：环节情况（{@code ProcessStatus} 枚举）
 *       就是「代码里登记一份 + 字典里登记一份 + 脚本末尾用守卫查询比对」，
 *       因为这两处都有各自的消费者：字典供界面下拉，代码供校验与默认值。
 *       附件类型同样是「界面要显示中文名、后端要白名单校验」，因此沿用同一模式。</li>
 * </ol>
 * 代价是「加一类附件要改两处」。为把这个代价降到最低，
 * {@code 01_data_dict.sql} 的守卫查询会比对字典项数，数量对不上就会被发现；
 * 且 {@link #typeText} 在码值不在表内时回退为码值本身 ——
 * 只影响显示（看得见英文/数字），不会让功能坏掉。
 *
 * <p><b>★ 关于软删列 {@code del_flag}</b>：本表用 {@code @TableLogic} 的下划线
 * {@code del_flag} tinyint（与配套表的驼峰 {@code delFlag} varchar 完全不同）。
 * BaseMapper 的方法（{@code selectById / updateById / removeById / selectPage}）
 * 会自动附上 {@code del_flag = 0}；而 {@link LandAttachmentMapper} 里
 * <b>手写</b>的注解 SQL 不受 {@code @TableLogic} 影响，
 * 所以那些语句自己带 {@code del_flag = 0}（见该 Mapper 的类注释）。
 */
@Slf4j
@Service
public class LandAttachmentServiceImpl extends ServiceImpl<LandAttachmentMapper, LandAttachment>
        implements ILandAttachmentService {

    /** 上传根目录（jeecg.path.upload），dev 为 E:/work-space/upFiles/tj-land-use-info */
    @Value("${jeecg.path.upload}")
    private String uploadRoot;

    /** 分页上限：附件列表是「翻着找人」的场景，一次要 1000 条没有意义，反而拖慢接口 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 业务类型白名单（与 {@link LandAttachment} 的常量、{@link DataSupport} 的常量必须一致） */
    private static final Set<String> ALLOWED_BIZ_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(DataSupport.BIZ_LAND, DataSupport.BIZ_FACILITY,
                    DataSupport.BIZ_PROCESS)));

    /**
     * 附件类型白名单（码 → 中文名），逐字对应 {@code sql/data/01_data_dict.sql}
     * 的 {@code land_attach_type} 字典（01~12 + 99，共 13 项）。
     *
     * <p>01~04 沿用旧系统宗地侧的 4 个槽位目录名；05~13 是配套侧/环节侧扩展。
     * 用 LinkedHashMap 保证顺序 = 字典的 {@code sort_order}，前端下拉直接照序渲染。
     */
    private static final Map<String, String> ALLOWED_FILE_TYPES;

    static {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("01", "土地整理计划");
        types.put("02", "配套情况函");
        types.put("03", "配套筹备函");
        types.put("04", "出让宗地图形数据");
        types.put("05", "项建批复");
        types.put("06", "可研批复");
        types.put("07", "初设及概算批复");
        types.put("08", "道路规划");
        types.put("09", "管线综合矢量数据");
        types.put("10", "专业配套方案");
        types.put("11", "施工许可");
        types.put("12", "竣工与移交文件");
        types.put("99", "其他");
        ALLOWED_FILE_TYPES = Collections.unmodifiableMap(types);
    }

    /** 可预览的扩展名 → 预览方式（图片） */
    private static final Set<String> IMAGE_EXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp")));

    /** 可预览的扩展名 → 预览方式（Office 文档；浏览器不能直接渲染，前端交给在线预览组件） */
    private static final Set<String> OFFICE_EXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("doc", "docx", "xls", "xlsx", "ppt", "pptx")));

    /** 可预览的扩展名 → 预览方式（纯文本，浏览器直接渲染） */
    private static final Set<String> TEXT_EXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("txt", "csv", "log", "md")));

    @Autowired
    private LandMapper landMapper;

    @Autowired
    private FacilityMapper facilityMapper;

    @Autowired
    private FacilityProcessMapper facilityProcessMapper;

    @Autowired
    private ChangeLogSupport changeLogSupport;

    @Autowired
    private DataSupport dataSupport;

    // ==================================================================
    // 一、查询
    // ==================================================================

    @Override
    public IPage<LandAttachment> queryPage(AttachmentQueryDTO query) {
        AttachmentQueryDTO condition = query == null ? new AttachmentQueryDTO() : query;
        QueryWrapper<LandAttachment> wrapper = new QueryWrapper<>();
        // ★ 逻辑删除条件由 @TableLogic 自动附加（本实体是 Integer delFlag），
        //   这里不再手写 del_flag = 0，避免出现「同一条件写两遍」的误导
        if (StringUtils.isNotBlank(condition.getBizType())) {
            wrapper.eq("biz_type", condition.getBizType().trim());
        }
        if (StringUtils.isNotBlank(condition.getBizId())) {
            wrapper.eq("biz_id", condition.getBizId().trim());
        }
        if (StringUtils.isNotBlank(condition.getBizKey())) {
            wrapper.like("biz_key", condition.getBizKey().trim());
        }
        if (StringUtils.isNotBlank(condition.getFileType())) {
            wrapper.eq("file_type", condition.getFileType().trim());
        }
        if (StringUtils.isNotBlank(condition.getKeyword())) {
            wrapper.like("file_name", condition.getKeyword().trim());
        }
        if (StringUtils.isNotBlank(condition.getUploadBy())) {
            wrapper.eq("upload_by", condition.getUploadBy().trim());
        }
        // ★ 日期范围按「天」闭区间处理：用户选 2026-10-08 的意思是「这一整天」，
        //   若直接把 00:00:00 当上界，当天上传的文件全都筛不出来（见 DTO 类注释）
        if (StringUtils.isNotBlank(condition.getBeginDate())) {
            wrapper.ge("upload_time", condition.getBeginDate().trim() + " 00:00:00");
        }
        if (StringUtils.isNotBlank(condition.getEndDate())) {
            wrapper.le("upload_time", condition.getEndDate().trim() + " 23:59:59");
        }
        // 排序：先按业务排序号（没填的排最后），再按上传时间倒序（新传的在前面）
        wrapper.last("ORDER BY IFNULL(sort_no, 9999) ASC, upload_time DESC");

        int pageNo = condition.getPageNo() == null || condition.getPageNo() < 1 ? 1 : condition.getPageNo();
        int pageSize = condition.getPageSize() == null || condition.getPageSize() < 1
                ? 10 : Math.min(condition.getPageSize(), MAX_PAGE_SIZE);
        IPage<LandAttachment> page = page(new Page<>(pageNo, pageSize), wrapper);
        enrichAll(page.getRecords());
        return page;
    }

    @Override
    public List<LandAttachment> queryByBiz(String bizType, String bizId) {
        String type = cleanBizType(bizType);
        String id = DataSupport.clean(bizId);
        if (type == null || id == null) {
            return Collections.emptyList();
        }
        return enrichAll(baseMapper.selectByBiz(type, id));
    }

    @Override
    public Map<String, List<LandAttachment>> queryByBizIds(String bizType, List<String> bizIds) {
        Map<String, List<LandAttachment>> grouped = new LinkedHashMap<>();
        String type = cleanBizType(bizType);
        if (type == null || bizIds == null || bizIds.isEmpty()) {
            return grouped;
        }
        List<LandAttachment> list = baseMapper.selectByBizIds(type, bizIds);
        if (list == null) {
            return grouped;
        }
        enrichAll(list);
        for (LandAttachment item : list) {
            List<LandAttachment> bucket = grouped.get(item.getBizId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(item.getBizId(), bucket);
            }
            bucket.add(item);
        }
        return grouped;
    }

    @Override
    public Map<String, Long> countByBiz(String bizType, List<String> bizIds) {
        Map<String, Long> counts = new LinkedHashMap<>();
        String type = cleanBizType(bizType);
        if (type == null || bizIds == null || bizIds.isEmpty()) {
            return counts;
        }
        List<Map<String, Object>> rows = baseMapper.selectCountGroupByBiz(type, bizIds);
        if (rows == null) {
            return counts;
        }
        for (Map<String, Object> row : rows) {
            Object bizId = row.get("bizId");
            if (bizId == null) {
                continue;
            }
            counts.put(String.valueOf(bizId), toLong(row.get("num")));
        }
        return counts;
    }

    @Override
    public List<Map<String, Object>> typeDistribution(String bizType) {
        String type = cleanBizType(bizType);
        List<Map<String, Object>> rows = baseMapper.selectTypeDistribution(type);
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            String code = row.get("fileType") == null ? null : String.valueOf(row.get("fileType"));
            long totalSize = toLong(row.get("totalSize"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileType", code);
            item.put("fileTypeText", typeText(code));
            item.put("num", toLong(row.get("num")));
            item.put("totalSize", totalSize);
            item.put("readableSize", formatSize(totalSize));
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> summary(String bizType) {
        String type = cleanBizType(bizType);
        Map<String, Object> row = baseMapper.selectSummary(type);
        long num = row == null ? 0L : toLong(row.get("num"));
        long totalSize = row == null ? 0L : toLong(row.get("totalSize"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bizType", type);
        result.put("num", num);
        result.put("totalSize", totalSize);
        result.put("readableSize", formatSize(totalSize));
        result.put("typeCount", typeDistribution(type).size());
        return result;
    }

    @Override
    public List<Map<String, String>> allowedFileTypes() {
        List<Map<String, String>> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : ALLOWED_FILE_TYPES.entrySet()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("value", entry.getKey());
            item.put("text", entry.getValue());
            list.add(item);
        }
        return list;
    }

    // ==================================================================
    // 二、保存 / 删除
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LandAttachment saveUploaded(String bizType, String bizId, String bizKey, String fileType,
                                       LandAttachment meta) {
        String type = cleanBizType(bizType);
        if (type == null) {
            throw new JeecgBootException("附件业务类型「" + bizType + "」不合法，只能是 land（宗地）/ "
                    + "facility（配套项目）/ process（环节进度）");
        }
        String id = DataSupport.clean(bizId);
        if (id == null) {
            throw new JeecgBootException("缺少业务主键，无法保存附件（请先保存业务数据再上传附件）");
        }
        // ① 业务对象必须真实存在（软删的不算），并顺带取出它的可读名称
        String displayName = requireBizDisplayName(type, id);
        // ② 附件类型必须在允许集合内
        String code = DataSupport.clean(fileType);
        if (code == null || !ALLOWED_FILE_TYPES.containsKey(code)) {
            throw new JeecgBootException("附件类型「" + fileType + "」不在允许的范围内，可选："
                    + allowedTypeText());
        }
        if (meta == null) {
            throw new JeecgBootException("缺少附件信息，请重新上传");
        }
        // ③ 存储路径必须是安全的相对路径
        String storePath = normalizeStorePath(meta.getStorePath());

        LandAttachment entity = new LandAttachment();
        entity.setBizType(type);
        entity.setBizId(id);
        // ★ biz_key 用前端给的值；前端没给（用户在下拉里现挑对象等场景）则用
        //   上面从业务对象解析出来的名称兜底 —— 否则这一列会是 NULL，
        //   列表里就表现为「这条附件不显示项目信息」（2026-10-10 修）
        String bizKeyValue = DataSupport.cleanAndCap(bizKey, 100);
        if (bizKeyValue == null) {
            bizKeyValue = DataSupport.cleanAndCap(displayName, 100);
        }
        entity.setBizKey(bizKeyValue);
        entity.setFileType(code);
        String fileName = DataSupport.clean(meta.getFileName());
        if (fileName == null) {
            // 前端没传文件名时用路径末段兜底：至少让列表上有个可读的名字，
            // 而不是空单元格（空单元格会让人以为这条记录坏了）
            fileName = lastSegment(storePath);
        }
        entity.setFileName(DataSupport.cleanAndCap(fileName, 255));
        // 扩展名统一小写、不含点：预览方式与 Content-Type 都按它判断，
        // 库里存 "JPG" 与 "jpg" 两种写法会让「按扩展名找可预览附件」的查询漏掉一半
        String ext = DataSupport.clean(meta.getFileExt());
        entity.setFileExt(ext == null
                ? extOf(entity.getFileName()) : ext.toLowerCase(Locale.ROOT));
        entity.setFileSize(meta.getFileSize());
        entity.setFileMd5(DataSupport.cleanAndCap(meta.getFileMd5(), 32));
        entity.setContentType(DataSupport.cleanAndCap(meta.getContentType(), 150));
        entity.setStoreType(StringUtils.isBlank(meta.getStoreType())
                ? "local" : DataSupport.cleanAndCap(meta.getStoreType(), 20));
        entity.setStorePath(storePath);
        entity.setRemark(DataSupport.cleanAndCap(meta.getRemark(), 500));
        // 排序号允许为空（为空时列表按上传时间排在最后，见 Mapper 的 IFNULL(sort_no, 9999)）
        entity.setSortNo(meta.getSortNo());
        entity.setUploadBy(dataSupport.currentUsername());
        entity.setUploadName(dataSupport.currentRealname());
        entity.setUploadTime(new Date());
        entity.setDownloadCount(0);
        entity.setDelFlag(0);
        save(entity);

        // 附件上传留痕（挂在附件自己身上，bizKey 用文件名，方便在「操作履历」里按名字找）
        changeLogSupport.collector(DataSupport.BIZ_ATTACHMENT, entity.getId(), entity.getFileName())
                .add("fileName", "文件名", null, entity.getFileName())
                .add("fileType", "附件类型", null, typeText(code))
                .add("fileSize", "文件大小", null, formatSize(entity.getFileSize()))
                .add("bizKey", "所属业务", null, entity.getBizKey())
                .save(DataChangeLog.ACTION_UPLOAD,
                        "上传附件「" + entity.getFileName() + "」（" + typeText(code) + "）", null);

        log.info("附件保存成功：id={}, bizType={}, bizId={}, fileType={}, fileName={}, size={}, 操作人={}",
                entity.getId(), type, id, code, entity.getFileName(), entity.getFileSize(),
                DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
        return entity;
    }

    @Override
    public LandAttachment queryById(String id) {
        String key = DataSupport.clean(id);
        if (key == null) {
            return null;
        }
        // selectById 由 BaseMapper 提供，@TableLogic 会自动附上 del_flag = 0（软删的查不到）
        return enrich(baseMapper.selectById(key));
    }

    @Override
    public LandAttachment prepareDownload(String id) {
        LandAttachment entity = queryById(id);
        if (entity == null) {
            return null;
        }
        // ★ 计数自增放在「确认这条记录存在且未删」之后：
        //   Mapper 里的 increaseDownloadCount 是手写注解 SQL，
        //   @TableLogic 不会给它加 del_flag = 0（该 Mapper 已定稿，不改），
        //   因此必须靠这一步的前置校验保证它只会命中存活记录。
        try {
            baseMapper.increaseDownloadCount(entity.getId());
        } catch (Exception e) {
            // 计数失败不该让用户下载不到文件：文件本身是用户要的东西，
            // 下载次数只是统计口径
            log.warn("附件下载次数自增失败（不影响下载）：id={}, 原因={}", entity.getId(), e.getMessage());
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAttachment(String id, HttpServletRequest request) {
        LandAttachment entity = queryById(id);
        if (entity == null) {
            throw new JeecgBootException("附件不存在或已被删除");
        }
        // 逻辑删除：removeById 会因 @TableLogic 改成 UPDATE del_flag = 1，不做物理删除
        removeById(entity.getId());

        // ★ 这里必须显式登记一个「变化项」：ChangeLogSupport 只对 DELETE / RESTORE
        //   两个动作做「无字段变化也留痕」的特判，ACTION_ATTACH_DELETE 不在其中。
        //   只写摘要不留项，履历里就什么都不会出现 —— 而「谁把这份扫描件删了」
        //   恰恰是附件管理最需要能回答的问题。
        changeLogSupport.collector(DataSupport.BIZ_ATTACHMENT, entity.getId(), entity.getFileName())
                .add("delFlag", "删除状态", "正常", "已删除")
                .save(DataChangeLog.ACTION_ATTACH_DELETE,
                        "删除附件「" + entity.getFileName() + "」（" + typeText(entity.getFileType())
                                + "，所属业务：" + StringUtils.defaultString(entity.getBizKey(), "—") + "）",
                        request);

        log.info("附件删除成功：id={}, fileName={}, 操作人={}",
                entity.getId(), entity.getFileName(),
                DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
    }

    // ==================================================================
    // 三、路径与展示字段
    // ==================================================================

    @Override
    public String resolveAbsolutePath(LandAttachment entity) {
        if (entity == null || StringUtils.isBlank(entity.getStorePath())) {
            return null;
        }
        String relative = entity.getStorePath().replace('\\', '/').trim();
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        // 路径穿越防护：store_path 只允许「相对路径」，出现 .. 一律拒绝
        if (relative.contains("..")) {
            log.warn("检测到疑似路径穿越的 store_path，已拒绝：attachmentId={}, storePath={}",
                    entity.getId(), entity.getStorePath());
            return null;
        }
        File root = new File(uploadRoot);
        File target = new File(root, relative);
        try {
            String rootPath = root.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            // ★ 归属校验不能省：只判断「字符串里没有 ..」挡不住符号链接、
            //   重复分隔符、以及 Windows 上「C:foo」这类相对盘符路径。
            //   规范化之后再要求必须落在上传根目录之内，才是真正闭合的检查。
            if (!targetPath.startsWith(rootPath + File.separator) && !targetPath.equals(rootPath)) {
                log.warn("文件绝对路径越出上传根目录，已拒绝：attachmentId={}, target={}",
                        entity.getId(), targetPath);
                return null;
            }
            return targetPath;
        } catch (Exception e) {
            log.warn("解析附件绝对路径失败：attachmentId={}, 原因={}", entity.getId(), e.getMessage());
            return null;
        }
    }

    @Override
    public LandAttachment enrich(LandAttachment entity) {
        if (entity == null) {
            return null;
        }
        entity.setUrl(buildUrl(entity));
        entity.setReadableSize(formatSize(entity.getFileSize()));
        entity.setFileTypeText(typeText(entity.getFileType()));
        String ext = entity.getFileExt();
        if (StringUtils.isBlank(ext)) {
            ext = extOf(entity.getFileName());
        }
        entity.setPreviewMode(previewModeOf(ext));
        entity.setPreviewable(previewableOf(ext));
        return entity;
    }

    @Override
    public List<LandAttachment> enrichAll(List<LandAttachment> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        for (LandAttachment item : list) {
            enrich(item);
        }
        return list;
    }

    @Override
    public String previewModeOf(String fileExt) {
        String ext = fileExt == null ? null : fileExt.trim().toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(ext)) {
            return "download";
        }
        if (IMAGE_EXTS.contains(ext)) {
            return "image";
        }
        if ("pdf".equals(ext)) {
            return "pdf";
        }
        if (OFFICE_EXTS.contains(ext)) {
            return "office";
        }
        if (TEXT_EXTS.contains(ext)) {
            return "text";
        }
        // ★ 兜底是 download 而不是「猜一个」：猜错会让前端白屏或弹一堆下载，
        //   用户不知道发生了什么；明确告诉它「这种文件请下载后本地打开」最省事
        return "download";
    }

    @Override
    public boolean previewableOf(String fileExt) {
        String mode = previewModeOf(fileExt);
        return "image".equals(mode) || "pdf".equals(mode)
                || "office".equals(mode) || "text".equals(mode);
    }

    // ==================================================================
    // 四、内部工具
    // ==================================================================

    /**
     * 业务类型清洗与白名单校验（查询入口用）。
     *
     * <p>查询时「类型不合法」直接返回空而不抛异常：列表页传了一个过期的
     * {@code bizType} 只该显示「没有数据」，不该整页报错。
     * 而写入时类型不合法必须抛（见 {@link #saveUploaded}）——
     * 那时候悄悄改成别的类型写进去，就会造出挂在错误业务上的附件。
     */
    private String cleanBizType(String bizType) {
        String type = DataSupport.clean(bizType);
        if (type == null || !ALLOWED_BIZ_TYPES.contains(type)) {
            return null;
        }
        return type;
    }

    /**
     * 业务对象存在性校验 + 取出它的**可读名称**（软删视为不存在）。
     *
     * <p><b>★ 为什么顺手把名称取出来（2026-10-10 修）</b>：
     * {@code biz_key} 是列表与检索直接展示的那一列（「文件名 / 归属」的副行），
     * 以前完全依赖前端在提交时带上 {@code bizKey}。前端一旦漏带（例如用户是
     * 在下拉里现挑的对象，而不是从配套详情跳转过来的），这一列就是 NULL ——
     * 现象正是「列表里这条附件不显示项目信息」，而附件本身是好的。
     *
     * <p>既然校验本来就要把实体查出来，就顺带把名称解析出来：
     * 前端带了就用前端的（它可能带更完整的展示口径），
     * 前端没带则由服务端兜底。这样 {@code biz_key} 不再是一个「看客户端心情」的字段。
     *
     * @return 该业务对象的可读名称；取不到名称时返回 null（不阻断保存）
     */
    private String requireBizDisplayName(String bizType, String bizId) {
        if (DataSupport.BIZ_LAND.equals(bizType)) {
            // Land 上有 @TableLogic，selectById 会自动排除已删宗地
            Land land = landMapper.selectById(bizId);
            if (land == null) {
                throw new JeecgBootException("出让宗地不存在或已被移除，无法上传附件（编号："
                        + bizId + "）");
            }
            return withCode(land.getDkmc(), land.getCrzdbh());
        }
        if (DataSupport.BIZ_FACILITY.equals(bizType)) {
            Facility facility = facilityMapper.selectById(bizId);
            // ★ 本表软删列是驼峰 delFlag 且类型是 varchar('0'/'1')，
            //   实体上没有 @TableLogic，必须自己判断（见 Facility 实体注释）
            if (facility == null || "1".equals(facility.getDelFlag())) {
                throw new JeecgBootException("配套项目不存在或已被移除，无法上传附件（ID："
                        + bizId + "）");
            }
            return withCode(facility.getPtxmmc(), facility.getCrzdbh());
        }
        if (DataSupport.BIZ_PROCESS.equals(bizType)) {
            FacilityProcess process = facilityProcessMapper.selectById(bizId);
            if (process == null) {
                throw new JeecgBootException("环节进度不存在或已被删除，无法上传附件（ID："
                        + bizId + "）");
            }
            // 环节的展示名就是环节名
            return withCode(process.getLcName(), process.getCrzdbh());
        }
        throw new JeecgBootException("附件业务类型「" + bizType + "」不合法");
    }

    /**
     * 统一的「名称（编号）」展示口径。
     *
     * <p>★ 必须与前端下拉选项、以及历史数据修复脚本保持同一个格式，
     * 否则同一列里会出现「编号　名称」与「名称（编号）」两种写法 ——
     * 用户看起来像是两套数据。
     * 这里的口径取自前端配套下拉：{@code 名称（宗地编号）}。
     *
     * <p>任一为空时的退化：只有名称 → 名称；只有编号 → 编号；都空 → null。
     */
    private String withCode(String name, String code) {
        String n = DataSupport.clean(name);
        String c = DataSupport.clean(code);
        if (n == null) {
            return c;
        }
        if (c == null) {
            return n;
        }
        return n + "（" + c + "）";
    }

    /**
     * 存储路径校验与规范化（写侧）。
     *
     * <p><b>★ 前导斜杠：容忍并去掉，不再拒绝</b>（2026-10-09 修正）。
     * 先前这里把「以 {@code /} 开头」当成非法值直接拒绝，依据是本文件注释里那句
     * 「3.4.3 的 uploadLocal 不带前导斜杠」—— <b>那句话是错的</b>：
     * {@code CommonController.uploadLocal} 返回的是 {@code bizPath + "/" + fileName}，
     * 而 {@code bizPath} 来自上传请求的 {@code biz} 参数；前端各模块的
     * {@code buildBizPath()} 统一返回 {@code /facility/attachment/yyyy/MM}（**带**前导斜杠），
     * 所以上传接口回给前端的路径本身就是以 {@code /} 开头的。
     * 于是「用上传接口返回的路径落库」这条正常路径 100% 报错 ——
     * 这个校验把唯一正确的用法给挡了。
     *
     * <p>为什么选择「规范化」而不是「改前端去掉斜杠」：
     * <ol>
     *   <li>同一个 {@code biz_path} 约定被档案 / 收发文 / 提级论证三个模块共用
     *       （{@code archive/constants.js}、{@code escalation/constants.js} 的
     *       {@code buildBizPath} 都带前导斜杠），改前端要动 3 处且遗漏一处就又踩；</li>
     *   <li>档案 / 收发文模块把上传返回的 storePath <b>原样入库</b>，
     *       所以库里本来就可能存在带前导斜杠的值 —— 只改前端不解决历史数据与新数据的差异；</li>
     *   <li>服务端做归一是「不信任调用方写法」的正确位置，对任何调用方都成立。</li>
     * </ol>
     *
     * <p>安全性没有放松：{@code ..}（路径穿越）、{@code :}（盘符绝对路径）
     * 仍然一律拒绝；去掉前导斜杠只是把 {@code /a/b} 与 {@code a/b} 归一成同一种
     * 「相对上传根目录」的语义 —— 两者在读盘时本来就指向同一个文件。
     *
     * <p>★ 与读侧（{@link #resolveAbsolutePath}）的分工：
     * 写侧负责归一（让坏数据进不来、好数据不被误拒），
     * 读侧用 canonicalPath 兜住真正的越界（容忍历史数据里各种写法）。
     */
    private String normalizeStorePath(String storePath) {
        String path = DataSupport.clean(storePath);
        if (path == null) {
            throw new JeecgBootException("缺少文件存储路径，请重新上传文件");
        }
        String normalized = path.replace('\\', '/');
        if (normalized.contains("..")) {
            throw new JeecgBootException("文件存储路径不合法（包含「..」，疑似路径穿越）：「" + path + "」");
        }
        if (normalized.contains(":")) {
            // Windows 的 C:/... 形式：它同样是绝对路径，只是不用斜杠开头
            throw new JeecgBootException("文件存储路径不合法（疑似盘符绝对路径）：「" + path + "」");
        }
        // 去掉前导斜杠：jeecg 上传接口返回的就是带前导斜杠的路径（见方法注释），
        // 统一存成相对路径，前端拼 staticDomainURL 时不会多出一个斜杠导致 404
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.isEmpty()) {
            throw new JeecgBootException("文件存储路径不合法（去掉前导斜杠后为空）：「" + path + "」");
        }
        return DataSupport.capLength(normalized, 500);
    }

    /**
     * 相对路径（供前端拼 staticDomainURL）。
     *
     * <p>与档案模块 {@code ArchiveFileServiceImpl#buildUrl} 同一口径：统一去掉前导斜杠，
     * 因为前端拼的是「域名 + /sys/common/static/ + 相对路径」，多一个斜杠就 404。
     * 这里 {@code store_path} 是不含前导斜杠的，但仍然统一处理一次 ——
     * 迁移进来的历史数据可能有，反复出现的小问题不值得再踩一次。
     */
    private String buildUrl(LandAttachment entity) {
        if (entity == null || StringUtils.isBlank(entity.getStorePath())) {
            return null;
        }
        String path = entity.getStorePath().trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    /** 附件类型码 → 中文名（未登记时回退为码值本身，保证页面上不会出现空白） */
    private static String typeText(String code) {
        if (StringUtils.isBlank(code)) {
            return "未分类";
        }
        String text = ALLOWED_FILE_TYPES.get(code.trim());
        return text == null ? code.trim() : text;
    }

    /** 允许类型的中文提示文案（错误信息里用） */
    private static String allowedTypeText() {
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, String> entry : ALLOWED_FILE_TYPES.entrySet()) {
            items.add(entry.getKey() + " " + entry.getValue());
        }
        return String.join(" / ", items);
    }

    private static String extOf(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 路径末段作为文件名兜底 */
    private static String lastSegment(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    /**
     * 人性化文件大小。
     *
     * <p>★ 刻意不复用档案模块的 {@code ArchiveFileServiceImpl.formatSize}：
     * 那是 {@code archive} 包下的 Service 类，从 {@code data} 包静态调用它，
     * 会让「数据管理」在编译期依赖「档案管理」—— 两个业务域之间多一条只为一个
     * 12 行格式化方法而存在的边。真正的解法是在公共包里放一个工具类，
     * 但那需要改动多个无关文件；本模块自带一份，代价是两个实现可能显示得不完全一致
     * （都是 B/KB/MB/GB/TB、保留两位小数，实际不会分叉）。
     */
    private static String formatSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "—";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double size = bytes;
        int unit = 0;
        while (size >= 1024 && unit < units.length - 1) {
            size /= 1024;
            unit++;
        }
        return unit == 0
                ? ((long) size) + " " + units[unit]
                // 显式指定 Locale.ROOT：否则在把逗号当小数点的区域设置下，
                // 会显示成「2,31 MB」，前端按数值再解析时容易出错
                : String.format(Locale.ROOT, "%.2f %s", size, units[unit]);
    }

    /** Map 里的数值列（MySQL 的 SUM/COUNT 可能回来 BigDecimal / Long / BigInteger）转 long */
    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
