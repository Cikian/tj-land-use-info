package org.jeecg.modules.land.data.attachment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.modules.land.data.attachment.entity.LandAttachment;
import org.jeecg.modules.land.data.attachment.vo.AttachmentQueryDTO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * @Description: 数据管理统一附件（宗地 / 配套项目 / 环节进度）Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（三）「配套附件管理（上传、查询、预览、下载）」。
 * 旧实现：{@code xjKjkfbCommercialLandController.file() / getFilesCommon() / viewLocalFile()}、
 * {@code xjKjkfbSupportingFacilitiesController.file() / getFilesCommon()}，
 * 前端 {@code attachmentList.html / attachmentView.html / attachmentSee.html}。
 *
 * <p><b>★★ 本 Service 的职责边界（决定了它比旧实现简单得多）</b>：
 * <b>文件本体不经过这里</b>。上传分两步：
 * <ol>
 *   <li>前端先调 jeecg 通用上传接口 {@code POST /sys/common/upload} 把字节落盘，
 *       拿到相对路径（本模块不重复实现上传，避免再出现一套「自己的目录规则」）；</li>
 *   <li>再调 {@link #saveUploaded} 把元数据落库，<b>此时才做全部校验</b>。</li>
 * </ol>
 * 之所以要第二步，是因为通用上传接口只认「目录名」，不认业务 ——
 * 它不知道这个文件是挂在哪个宗地上的，也无从校验路径安全性。
 *
 * <p><b>★ 与旧实现的四条差异（每条都对应旧系统的一个硬伤）</b>：
 * <ol>
 *   <li><b>查询靠查库，不扫目录</b>。旧 {@code getFilesCommon()} 递归遍历磁盘目录拼列表，
 *       目录被清理后页面就空了，而数据库里本来有记录。</li>
 *   <li><b>预览直读 store_path，不经临时目录</b>。旧预览先把文件复制到 temp，
 *       再 {@code Files.deleteDir(tempPath)} <b>清空整个 temp 目录</b> ——
 *       两个人同时预览时互相删文件。</li>
 *   <li><b>类型走允许集合，不写死槽位</b>。旧系统是 {@code file01~file04} 四个固定入参，
 *       加一类就要改 Controller 签名与前端表单。</li>
 *   <li><b>路径只存相对路径 + 读盘二次校验</b>。见 {@link #saveUploaded} 与
 *       {@link #resolveAbsolutePath}：写侧严格拒绝，读侧再用 canonicalPath 兜一层。</li>
 * </ol>
 */
public interface ILandAttachmentService {

    // ==================================================================
    // 一、查询
    // ==================================================================

    /** 分页查询（业务类型 / 业务主键 / 附件类型 / 文件名 / 上传人 / 时间范围） */
    IPage<LandAttachment> queryPage(AttachmentQueryDTO dto);

    /**
     * 某个业务对象的附件列表（按排序号 + 上传时间）。
     *
     * <p>宗地详情、配套详情、环节进度详情都调它 —— 三处展示逻辑完全一样，
     * 差别只有 {@code bizType} 的取值。
     */
    List<LandAttachment> queryByBiz(String bizType, String bizId);

    /** 一批业务对象的附件，按业务主键分桶（列表页批量展示，避免 N+1） */
    Map<String, List<LandAttachment>> queryByBizIds(String bizType, List<String> bizIds);

    /**
     * 一批业务对象的附件数量。
     *
     * <p>列表页每行显示「附件 3」用；只返回计数而不返回实体，
     * 是因为列表页一屏可能几十行、附件可能上百个，全部取回来只为了数个数不值得。
     */
    Map<String, Long> countByBiz(String bizType, List<String> bizIds);

    /**
     * 附件类型分布（数据管理页顶部的「附件概览」用）。
     *
     * @param bizType 业务类型（null = 全部业务）
     * @return 每项含 {@code fileType / fileTypeText / num / totalSize / readableSize}
     */
    List<Map<String, Object>> typeDistribution(String bizType);

    /**
     * 全局附件概览（总数 / 总大小 / 类型数）。
     *
     * @param bizType 业务类型（null = 全部业务）
     */
    Map<String, Object> summary(String bizType);

    /**
     * 可用的附件类型（材料类型）列表，**按业务类型取对应那套**。
     *
     * <p>★ 必须区分业务类型：宗地与配套是两套不同的材料清单
     * （宗地 5 类 / 配套 13 类，名称均逐字取自旧系统存储目录的类型目录名）。
     * 不带 bizType 的话前端只能拿到一半，或者看到与自己无关的选项。
     *
     * @param bizType land / facility / process；为空按宗地那一套返回
     * @return [{ value: 码, text: 中文名 }]，顺序即字典排序
     */
    List<Map<String, String>> allowedFileTypes(String bizType);

    /**
     * 附件目录树：按**材料类型**分组（只有一层）。
     *
     * <p>★ 树是派生结果，不落地：目录名就是材料类型名，类型码存在附件的 file_type 里。
     * 空类型（没有附件的材料类型）不进结果。
     *
     * <p>★ 用哪套材料清单由 bizType 决定（land/process → 宗地 5 类；facility → 配套 13 类）。
     *
     * @param bizType land / facility / process
     * @param bizId   业务对象 id
     */
    org.jeecg.modules.land.data.attachment.vo.AttachmentTreeVO tree(String bizType, String bizId);

    // ==================================================================
    // 二、保存 / 删除
    // ==================================================================

    /**
     * 保存一条已落盘的附件元数据。
     *
     * <p>调用前提：前端已经用 jeecg 的 {@code /sys/common/upload} 把文件<b>写入磁盘</b>，
     * 并把返回的相对路径放进了 {@code meta.storePath}。
     *
     * <p><b>本方法做全部校验</b>（任何一条不满足都抛 {@code JeecgBootException}，
     * 消息里说明「该怎么改」）：
     * <ol>
     *   <li>{@code bizType} 必须是 land / facility / process；</li>
     *   <li>{@code bizId} 对应的业务对象必须<b>真实存在</b>
     *       （软删的不算）—— 否则会造出「挂在不存在业务上的附件」，
     *       页面上永远显示不出来，磁盘上却占着空间；</li>
     *   <li>{@code storePath} 由 Service **归一成相对路径**：jeecg 上传接口返回的路径
     *       带前导斜杠，会被去掉；但含 {@code ..}（路径穿越）或 {@code :}（盘符绝对路径）
     *       仍然一律拒绝。见 {@code LandAttachmentServiceImpl#normalizeStorePath}。</li>
     *   <li>{@code fileType} 必须在**该业务类型对应的**材料清单内
     *       （见 {@link #allowedFileTypes(String)}）。</li>
     * </ol>
     *
     * <p>★ 为什么校验放在「入库」这一步而不是「上传」那一步：
     * 字节已经落盘了，此时拒绝不会留下坏数据（磁盘上多一个没被引用的文件，
     * 由运维清理即可）；而如果放行，坏数据会长期留在库里，且它指向的
     * 路径不可信 —— 读盘时再拦一次，意味着「上传成功」与「能下载」中间隔着一个坑。
     *
     * @param bizType  业务类型：land / facility / process
     * @param bizId    业务主键
     * @param bizKey   业务可读键（宗地编号 / 配套项目名称；可空，仅为列表展示方便）
     * @param fileType 附件类型码（01~13、99）
     * @param meta     由 jeecg 上传接口返回的信息（文件名 / 大小 / 路径 / md5 等）
     * @return 落库后的实体（含服务端生成的 id）
     */
    LandAttachment saveUploaded(String bizType, String bizId, String bizKey, String fileType,
                                LandAttachment meta);

    /** 按主键取一条有效附件（软删的返回 null） */
    LandAttachment queryById(String id);

    /**
     * 下载前的准备：取实体并把 {@code download_count} +1。
     *
     * <p>★ 计数用 SQL 自增而不是「读出来 +1 再写回」：后者在并发下载时会丢计数
     * （两个请求都读到 5，各写回 6，实际应为 7）。
     *
     * @return 附件实体（已补 url / 可读大小 / 预览方式）；不存在或已删返回 null
     */
    LandAttachment prepareDownload(String id);

    /**
     * 逻辑删除 + 变更留痕。
     *
     * <p>★ <b>不删磁盘文件</b>：同一个文件（同 md5）挂在多条业务上是正常业务
     * （比如同一份规划条件函同时支撑多个地块，这也是「秒传」提示存在的前提），
     * 删盘会把别人的附件一起删掉。磁盘清理交给运维按「无引用文件」扫描处理。
     */
    void deleteAttachment(String id, HttpServletRequest request);

    // ==================================================================
    // 三、路径与展示字段
    // ==================================================================

    /**
     * 把相对存储路径解析成磁盘绝对路径（下载 / 预览用）。
     *
     * <p><b>★ 安全约定（照抄档案模块 {@code ArchiveFileServiceImpl.resolveAbsolutePath}）</b>：
     * <ol>
     *   <li>去掉前导斜杠、把 {@code \} 统一成 {@code /}（兼容早期数据里的 Windows 分隔符）；</li>
     *   <li>出现 {@code ..} 一律拒绝并打 warn 日志；</li>
     *   <li>用 {@code getCanonicalPath()} 规范化后，要求结果必须落在
     *       {@code jeecg.path.upload} 之内（前缀匹配 + 等于上传根目录本身也允许）——
     *       只靠「字符串里没有 ..」是不够的，符号链接、多余分隔符、
     *       相对路径拼出来的特殊情况都要靠规范化后的归属校验兜住。</li>
     * </ol>
     *
     * @return 磁盘绝对路径；校验不通过（或实体/路径为空）返回 null
     */
    String resolveAbsolutePath(LandAttachment entity);

    /**
     * 补齐展示字段：{@code url / readableSize / fileTypeText / previewable / previewMode}。
     *
     * <p>这些字段不入库（{@code @TableField(exist = false)}），因为它们是
     * 「怎么展示」而不是「业务是什么」—— 入库会引入「库里的值可能与当前规则不符」
     * 的问题（将来多支持一种预览格式，历史数据里的 previewMode 就过期了）。
     */
    LandAttachment enrich(LandAttachment entity);

    /** 批量补齐展示字段 */
    List<LandAttachment> enrichAll(List<LandAttachment> list);

    /**
     * 预览方式（前端据此选打开方式）。
     *
     * @return {@code image} / {@code pdf} / {@code office} / {@code text} / {@code download}
     */
    String previewModeOf(String fileExt);

    /**
     * 是否可以在浏览器里直接预览。
     *
     * <p>★ 与 {@link #previewModeOf} 分开而不是「previewMode != download」：
     * 将来若新增一种「只能用本机 Office 打开」的 {@code office} 之外的模式，
     * 两个判断会分化；现在就写成显式判断，避免那天来改这个隐式等式。
     */
    boolean previewableOf(String fileExt);
}
