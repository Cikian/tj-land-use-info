package org.jeecg.modules.land.archive.completion.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 批量关联结果。接口采用整批成功或整批回滚，不返回含糊的“部分成功”。
 */
@Data
@Accessors(chain = true)
public class CompletionBatchLinkResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次去重后的历史档案数 */
    private int requestedCount;

    /** 成功写入数；接口成功时恒等于 requestedCount */
    private int successCount;

    /** 共同关联的扫描件档案 ID */
    private String archiveId;
}
