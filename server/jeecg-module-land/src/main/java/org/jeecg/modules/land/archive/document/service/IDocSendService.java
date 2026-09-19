package org.jeecg.modules.land.archive.document.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.archive.document.dto.DocArchiveDTO;
import org.jeecg.modules.land.archive.document.dto.DocQueryDTO;
import org.jeecg.modules.land.archive.document.entity.DocSend;

import java.util.List;

/**
 * @Description: 发文 Service（方案 2.3.2 第 9 项：发文的信息记录）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>发文是纯台账：登记、编辑、附件、删除、可选归档，<b>没有流转</b>
 * （与旧 tj-sfw 一致，也与方案原文「发文的信息记录」的口径一致）。
 */
public interface IDocSendService extends IService<DocSend> {

    /** 发文分页查询 */
    IPage<DocSend> queryPage(DocQueryDTO query);

    /** 发文详情（含附件） */
    DocSend queryDetail(String id);

    /** 查与某项目 / 宗地相关的发文（档案详情页的「收发文情况」用） */
    List<DocSend> queryRelated(String facilityId, String landId, String crzdbh, Integer limit);

    /** 发文总数 / 已归档数（列表页统计条） */
    long countAll();

    /** 生成发文登记号：FW-{yyyy}-{4位} */
    String generateDocNo(Integer year);

    /** 新增发文 */
    String createDoc(DocSend doc);

    /** 编辑发文（含附件同步） */
    void updateDoc(DocSend doc);

    /** 删除发文（逻辑删除，级联附件） */
    void deleteDoc(String id);

    /** 批量删除 */
    void deleteDocs(List<String> ids);

    /** 归档到档案管理模块（同样必须选择档案类别） */
    String archive(DocArchiveDTO dto);
}
