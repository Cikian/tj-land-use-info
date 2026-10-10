package org.jeecg.modules.land.data.attachment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.land.data.attachment.entity.LandAttachmentDir;

import java.util.List;

/**
 * @Description: 附件目录 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-10
 * @Version V1.0
 *
 * <p>★ 目录表**没有** @TableLogic（与 t_supporting_facilities 同理）：
 * {@code del_flag} 是自己判断的 tinyint。这里所有手写 SQL 都显式带 {@code del_flag = 0}，
 * 因为 MyBatis-Plus 的 @TableLogic 不会作用于手写 @Select。
 *
 * <p>★ 为什么唯一键 {@code uk_att_dir(biz_type, biz_id, dir_path)} 不含 del_flag：
 * 目录删了再建同名目录是常见操作，若把 del_flag 放进唯一键，
 * 就会允许「同路径同时存在一条正常 + 一条已删」，之后恢复时必然撞键。
 * 所以策略是：**删除即物理删除**（目录只是个名字，没有独立价值），
 * 附件不跟着删 —— 目录下的文件会被移动到父目录（见 Service 的 removeDir）。
 */
@Mapper
public interface LandAttachmentDirMapper extends BaseMapper<LandAttachmentDir> {

    /** 目录全部列（表别名 d） */
    String COLUMNS = " d.id, d.biz_type AS bizType, d.biz_id AS bizId, d.biz_key AS bizKey, "
            + " d.dir_path AS dirPath, d.parent_path AS parentPath, d.dir_name AS dirName, "
            + " d.depth, d.sort_no AS sortNo, d.create_by AS createBy, d.create_name AS createName, "
            + " d.create_time AS createTime, d.del_flag AS delFlag ";

    /** 某业务对象下的全部目录，按路径升序（父一定在子之前，便于一次性建树） */
    @Select("SELECT " + COLUMNS + " FROM t_land_attachment_dir d "
            + "WHERE d.del_flag = 0 AND d.biz_type = #{bizType} AND d.biz_id = #{bizId} "
            + "ORDER BY d.dir_path ASC")
    List<LandAttachmentDir> selectByBiz(@Param("bizType") String bizType, @Param("bizId") String bizId);

    /** 一批业务对象的目录（按项目树展示时批量取，避免 N+1） */
    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM t_land_attachment_dir d "
            + "WHERE d.del_flag = 0 AND d.biz_type = #{bizType} AND d.biz_id IN "
            + "<foreach collection='bizIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " ORDER BY d.biz_id ASC, d.dir_path ASC"
            + "</script>")
    List<LandAttachmentDir> selectByBizIds(@Param("bizType") String bizType,
                                           @Param("bizIds") List<String> bizIds);

    /** 精确取一个目录（含已软删的不算） */
    @Select("SELECT " + COLUMNS + " FROM t_land_attachment_dir d "
            + "WHERE d.del_flag = 0 AND d.biz_type = #{bizType} AND d.biz_id = #{bizId} "
            + "AND d.dir_path = #{dirPath} LIMIT 1")
    LandAttachmentDir selectByPath(@Param("bizType") String bizType,
                                   @Param("bizId") String bizId,
                                   @Param("dirPath") String dirPath);

    /** 某目录的直接子目录（重命名 / 删除时要连带处理，见 Service） */
    @Select("SELECT " + COLUMNS + " FROM t_land_attachment_dir d "
            + "WHERE d.del_flag = 0 AND d.biz_type = #{bizType} AND d.biz_id = #{bizId} "
            + "AND d.parent_path = #{parentPath} ORDER BY d.dir_name ASC")
    List<LandAttachmentDir> selectChildren(@Param("bizType") String bizType,
                                           @Param("bizId") String bizId,
                                           @Param("parentPath") String parentPath);

    /** 某目录之下的全部后代（用路径前缀匹配，避免递归查询） */
    @Select("SELECT " + COLUMNS + " FROM t_land_attachment_dir d "
            + "WHERE d.del_flag = 0 AND d.biz_type = #{bizType} AND d.biz_id = #{bizId} "
            + "AND d.dir_path LIKE CONCAT(#{prefix}, '/%') ORDER BY d.dir_path ASC")
    List<LandAttachmentDir> selectDescendants(@Param("bizType") String bizType,
                                              @Param("bizId") String bizId,
                                              @Param("prefix") String prefix);

    /** 某业务对象下的目录数（概览用） */
    @Select("SELECT COUNT(*) FROM t_land_attachment_dir "
            + "WHERE del_flag = 0 AND biz_type = #{bizType} AND biz_id = #{bizId}")
    int countByBiz(@Param("bizType") String bizType, @Param("bizId") String bizId);
}
