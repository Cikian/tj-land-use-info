package org.jeecg.modules.land.archive.completion.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 竣工历史档案批量关联入参：多条历史档案共同指向一个扫描件档案。
 *
 * <p>archive_id 仍是每条历史档案自己的单值指针；批量只减少重复操作，
 * 不把多个扫描件 ID 塞进同一列，也不改变项目归属字段。
 */
@Data
public class CompletionBatchLinkDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待关联的竣工历史档案 ID */
    private List<String> ids;

    /** 共同关联的扫描件档案 ID */
    private String archiveId;
}
