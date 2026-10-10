package org.jeecg.modules.land.data.attachment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @Description: 附件目录（支撑附件树的空目录与目录维护）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-10
 * @Version V1.0
 *
 * <p><b>★ 为什么目录要单独一张表，而不是只靠附件路径推导</b>：
 * 只存路径前缀的话，树只能由「已有哪些文件」推导出来 ——
 * 文件全删了目录就消失，也**建不出空目录**。
 * 而本次需求明确要求能新建 / 重命名 / 删除目录（含空目录），
 * 所以目录必须是可以独立存在的实体。
 *
 * <p><b>★ 为什么附件上还要冗余一个 dirPath</b>：
 * 目录表回答「有哪些目录」，附件表回答「文件在哪个目录」。
 * 附件只存路径字符串（不存目录 id）的好处是：
 * 重命名目录只需改目录表一行，不必逐条更新附件；
 * 代价是「移动文件」这类操作要更新路径前缀 —— 本模块暂不提供移动，
 * 因为附件是「上传即归档」的语义，移动需求很弱。
 *
 * <p><b>★ 路径一律用「相对业务对象的路径」</b>（例如 {@code 招标文件/2024}）：
 * 业务对象（宗地 / 配套 / 环节）本身就是树的第一层，由 bizType + bizId 决定。
 * 根目录用空字符串 ""，不用 null —— 这样分组、拼接、比较都不用额外判空。
 *
 * <p>本类的 {@code children} 是给树形接口用的**内存字段**，不落库。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_land_attachment_dir")
public class LandAttachmentDir implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 根目录路径常量：空字符串，不是 null */
    public static final String ROOT_PATH = "";

    /** 路径分隔符：统一用 /，与 store_path 一致（Windows 反斜杠在入口就被归一） */
    public static final String SEPARATOR = "/";

    /** 单段目录名最大长度（与 dir_path 的 500 一起保证整条路径可控） */
    public static final int MAX_SEGMENT_LENGTH = 100;

    /** 目录总深度上限：防止上传一个超深目录把界面/查询拖垮 */
    public static final int MAX_DEPTH = 8;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务类型：land / facility / process */
    private String bizType;

    /** 业务主键：t_land.id / 配套项目.id / t_facility_process.id */
    private String bizId;

    /** 业务可读键（冗余，列表与检索直接展示） */
    private String bizKey;

    /** 目录相对路径，例如 招标文件/2024；根目录不在本表里 */
    private String dirPath;

    /** 父目录相对路径；第一层的父是空串 */
    private String parentPath;

    /** 目录名（dirPath 的最后一段，冗余便于展示与排序） */
    private String dirName;

    /** 层级：根下第一层为 1 */
    private Integer depth;

    /** 同级排序；为空按名称排 */
    private Integer sortNo;

    private String createBy;

    private String createName;

    private Date createTime;

    /** 删除状态：0 正常 1 已删除 */
    private Integer delFlag;

    // ==================================================================
    // 以下为树形接口的**内存**字段，不落库
    // ★ 必须标 @TableField(exist = false)：否则 MyBatis-Plus 会把 children
    //   当成表字段去拼 INSERT/UPDATE，报
    //   「Type handler was null on parameter mapping for property 'children'」
    // ==================================================================

    /** 子目录（树形接口用） */
    @TableField(exist = false)
    private List<LandAttachmentDir> children = new ArrayList<>();

    /** 该目录下的附件数量（含子目录时要看接口是否累加，见 Service 注释） */
    @TableField(exist = false)
    private Integer fileCount;
}
