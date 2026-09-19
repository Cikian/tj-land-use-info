package org.jeecg.modules.land.archive.document.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 收文流转操作入参（转办 / 退回 / 办结共用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version V1.0
 */
@Data
public class DocHandleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 收文ID */
    private String docId;

    /** 转办 / 分办时的接收人账号（退回、办结时不需要） */
    private String toUsername;

    /** 处理意见 / 批注 */
    private String opinion;
}
