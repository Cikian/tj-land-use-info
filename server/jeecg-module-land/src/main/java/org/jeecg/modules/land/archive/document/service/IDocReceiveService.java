package org.jeecg.modules.land.archive.document.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.archive.document.dto.DocArchiveDTO;
import org.jeecg.modules.land.archive.document.dto.DocHandleDTO;
import org.jeecg.modules.land.archive.document.dto.DocQueryDTO;
import org.jeecg.modules.land.archive.document.entity.DocReceive;
import org.jeecg.modules.land.archive.document.vo.DocArchiveResultVO;
import org.jeecg.modules.land.archive.document.vo.DocStatVO;

import java.util.List;

/**
 * @Description: 收文 Service（方案 2.3.2 第 9 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface IDocReceiveService extends IService<DocReceive> {

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 收文分页查询 */
    IPage<DocReceive> queryPage(DocQueryDTO query);

    /** 收文详情（含附件与流转记录） */
    DocReceive queryDetail(String id);

    /**
     * 查与某项目 / 宗地相关的收文（档案详情页的「收发文情况」用）。
     *
     * @param facilityId 配套项目ID，优先
     * @param landId     宗地ID
     * @param crzdbh     出让宗地编号
     * @param limit      条数上限
     */
    List<DocReceive> queryRelated(String facilityId, String landId, String crzdbh, Integer limit);

    /**
     * 查直接归档到某份档案的收文（P1-11 第 3 条：按 archiveId 加载「收发文情况」）。
     *
     * <p>与 {@link #queryRelated} 的区别：这条是<b>精确匹配</b>——
     * 「这份档案是由哪几件收文归档来的」，而不是「这个项目下所有收文」。
     * 收发文归档复用同项目案卷后，一份档案会对应多份公文，这里的结果才与档案文件页签同源。
     */
    List<DocReceive> queryByArchiveId(String archiveId, Integer limit);

    /** 收文统计 */
    DocStatVO queryStat();

    // ------------------------------------------------------------------
    // 维护
    // ------------------------------------------------------------------

    /** 生成收文登记号（不落库，仅预览）：SW-{yyyy}-{4位} */
    String generateDocNo(Integer year);

    /** 新增收文（登记）：写主表 + 附件 + 第一条流转记录 */
    String createDoc(DocReceive doc);

    /** 编辑收文（含附件同步；不推进流转） */
    void updateDoc(DocReceive doc);

    /** 删除收文（逻辑删除，级联附件与流转记录） */
    void deleteDoc(String id);

    /** 批量删除 */
    void deleteDocs(List<String> ids);

    // ------------------------------------------------------------------
    // 流转（收文中心内的流转）
    // ------------------------------------------------------------------

    /** 转办 / 分办：把待办交给指定人 */
    void transfer(DocHandleDTO dto);

    /** 退回：退回到上一位处理人（没有上一位则退回到登记人） */
    void reject(DocHandleDTO dto);

    /** 办结：结束办理，清空当前处理人 */
    void finish(DocHandleDTO dto);

    // ------------------------------------------------------------------
    // 归档
    // ------------------------------------------------------------------

    /**
     * 归档到档案管理模块。
     *
     * <p>需求约定：办结时前端先弹「是否归档」，用户选择归档后<b>必须选择档案类别</b>；
     * 公文的所有附件会成为该档案的卷内文件，统一挂在这个类别下。
     *
     * <p>「一项目一案卷」：同项目已有案卷时<b>追加文件到该卷</b>，不再新建案卷；
     * 只追加，不删除、不合并任何历史案卷。
     *
     * @return 归档结果（档案ID + 原附件数 / 转换数 / 落库行数，供页面与日志核对）
     */
    DocArchiveResultVO archive(DocArchiveDTO dto);
}
