package org.jeecg.modules.land.archive.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.archive.entity.ArchiveLog;

import java.util.List;

/**
 * @Description: 档案操作记录 Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>方案原文「记录相关操作」的落地点：所有档案写操作都要调用
 * {@link #record(String, String, String, String, String)} 落一条日志。
 * 日志写入采用<b>吞异常</b>策略——记不上日志不应该让业务操作失败。
 */
public interface IArchiveLogService extends IService<ArchiveLog> {

    /**
     * 记录一条档案操作。
     *
     * @param archiveId 档案ID，可空
     * @param fileId    文件ID，可空
     * @param action    动作（见 {@link ArchiveLog} 的常量）
     * @param detail    明细
     * @param bizKey    业务键（档案号 / 宗地编号 / 项目名称）
     */
    void record(String archiveId, String fileId, String action, String detail, String bizKey);

    /** 查某档案的操作记录（时间倒序） */
    List<ArchiveLog> queryByArchiveId(String archiveId);

    /** 查某文件的操作记录（时间倒序） */
    List<ArchiveLog> queryByFileId(String fileId);
}
