package org.jeecg.modules.system.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 角色表
 * </p>
 *
 * @Author scott
 * @since 2018-12-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SysRole implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 角色名称
     */
    @Excel(name="角色名",width=15)
    private String roleName;
    
    /**
     * 角色编码
     */
    @Excel(name="角色编码",width=15)
    private String roleCode;
    
    /**
          * 描述
     */
    @Excel(name="描述",width=60)
    private String description;

    //update-begin---author:stargis ---date:20260101  for：角色同步至中台（ZK-SERVER），镜像字段
    /**中台角色ID(estar_workpost_system.id)*/
    @Excel(name="中台角色ID",width=20)
    private String zkRoleId;
    /**中台同步状态：not_synced未同步 / synced已同步 / failed同步失败*/
    private String zkSyncStatus;
    /**中台同步时间*/
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date zkSyncTime;
    /**中台同步失败原因*/
    private String zkSyncMsg;
    //update-end---author:stargis ---date:20260101  for：角色同步至中台（ZK-SERVER）

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date updateTime;


}
