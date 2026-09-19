package org.jeecg.modules.land.archive.document.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 收发文统计（列表页顶部的统计卡片）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>口径沿用旧 tj-sfw 的 {@code /send/homeInfo}（发文总数 / 收文总数 / 办结总数 / 我的待办），
 * 并补齐「已归档」与「已退回」两个对管理更有意义的维度。
 */
@Data
public class DocStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总数 */
    private long total;

    /** 待承办 */
    private long pending;

    /** 承办中 */
    private long handling;

    /** 已退回 */
    private long rejected;

    /** 已办结（含已归档） */
    private long finished;

    /** 已归档 */
    private long archived;

    /** 我的待办（current_handler = 当前登录人且未办结） */
    private long myTodo;
}
