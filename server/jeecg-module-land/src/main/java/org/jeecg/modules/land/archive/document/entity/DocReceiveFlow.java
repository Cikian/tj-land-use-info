package org.jeecg.modules.land.archive.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: 收文流转记录（「收文中心内的流转」的痕迹）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>表 {@code t_doc_receive_flow}。方案原文要求「实现收文中心内的流转」，
 * 而「流转」的验收要点就是<b>痕迹</b>：谁、何时、什么意见。
 *
 * <p><b>「当前待办」的判定规则</b>：同一 {@code docId} 下 {@code handleTime}
 * 为空且 {@code seqNo} 最大的那一条，就是当前待办；其 {@code handler}
 * 即 {@code t_doc_receive.current_handler}。
 *
 * <p>与旧表 {@code xj_filemanage_flow} 的差异：旧表只有
 * sender/recipient/remark，没有节点概念，也没有「处理时间」，
 * 因此无法表达「待办」状态（这也是旧系统流转链断掉的表象之一）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_doc_receive_flow")
public class DocReceiveFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 动作：登记 */
    public static final String ACTION_REGISTER = "登记";
    /** 动作：转办（含分办） */
    public static final String ACTION_TRANSFER = "转办";
    /** 动作：退回 */
    public static final String ACTION_REJECT = "退回";
    /** 动作：办结 */
    public static final String ACTION_FINISH = "办结";

    /** 节点：登记 */
    public static final String NODE_REGISTER = "登记";
    /** 节点：承办 */
    public static final String NODE_HANDLE = "承办";
    /** 节点：办结 */
    public static final String NODE_FINISH = "办结";

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 收文ID */
    private String docId;

    /** 流转节点 */
    private String nodeName;

    /** 动作：登记/转办/退回/办结 */
    private String action;

    /** 处理人账号（该条流转的待办人） */
    private String handler;

    /** 处理人姓名 */
    private String handlerName;

    /** 处理部门 */
    private String deptName;

    /** 处理意见 / 批注 */
    private String opinion;

    /** 接收（送达）时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date receiveTime;

    /** 处理时间；为空表示尚未处理（即当前待办） */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date handleTime;

    /** 流转顺序 */
    private Integer seqNo;

    /** 删除状态 0正常 1已删除 */
    @TableLogic
    private Integer delFlag;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
