package org.jeecg.modules.land.archive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @Description: 档案类别树（方案 2.3.2 第 1 项：档案类别管理）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>表结构见 docs/升级改造工作内容清单.md 6.2.1，建表脚本
 * jeecg-module-land/src/main/resources/sql/archive/01_t_archive_category.sql。
 *
 * <p>树形约定：
 * <ul>
 *   <li>{@code parentId} 为空表示顶级类别；</li>
 *   <li>{@code path} 是「主键串」，顶级为 {@code /id}，下级为 {@code 父.path + "/" + id}，
 *       用于一次 SQL 检索整棵子树（改名不影响它，因此更名无需级联更新）；</li>
 *   <li>{@code level} 顶级为 1，逐级 +1；</li>
 *   <li>{@code isLeaf=1} 表示没有下级，只有叶子类别允许挂档案。</li>
 * </ul>
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_archive_category")
public class ArchiveCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 启用 */
    public static final int STATUS_ENABLED = 1;
    /** 停用 */
    public static final int STATUS_DISABLED = 0;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 父级ID，顶级为空 */
    private String parentId;

    /** 树路径（主键串），如 /idA/idB */
    private String path;

    /** 类别名称，如 基本建设手续 */
    private String name;

    /** 类别编码（预留对接档案标准） */
    private String code;

    /** 别名/拼音码，便于检索 */
    private String aliasName;

    /** 类别说明 */
    private String note;

    /** 层级，顶级为 1（MySQL 下 level 非保留字，可直接用；其它库如需转义请改 column-format） */
    private Integer level;

    /** 是否有子节点 1有 0无 */
    private Integer hasChildren;

    /** 是否叶子（叶子才能挂档案）1是 0否 */
    private Integer isLeaf;

    /** 同级排序，升序 */
    private Integer sortNo;

    /** 1启用 0停用 */
    private Integer status;

    /** 删除状态 0正常 1已删除 */
    @TableLogic
    private Integer delFlag;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    // ------------------------------------------------------------------
    // 以下为接口返回用的非表字段
    // ------------------------------------------------------------------

    /** 子类别（tree 接口返回的树形结构） */
    @TableField(exist = false)
    private List<ArchiveCategory> children;

    /** 父类别名称（详情接口回显） */
    @TableField(exist = false)
    private String parentName;

    /** 类别全路径名称，如「基本建设手续 / 项目建议书批复」 */
    @TableField(exist = false)
    private String fullPathName;

    /**
     * 该类别（不含子类别）下已归档的档案数量。
     * 档案管理模块的 t_archive 表尚未建立时为 null，前端据此提示「档案校验未启用」。
     */
    @TableField(exist = false)
    private Long archiveCount;
}
