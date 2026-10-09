package org.jeecg.modules.land.data.process.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.land.data.process.entity.ProcessConfig;

import java.util.List;

/**
 * @Description: 审批流程（环节）配置 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>用注解 SQL 而不是 XML：本 Mapper 只有几句简单查询、无动态条件，
 * 注解写法少一个 XML 文件，也不会和别处的 mapper-locations 通配打架。
 *
 * <p><b>★ 一律用 {@code parent_id IS NULL} 判阶段</b>，不写
 * {@code parent_id IS NULL OR parent_id = ''}：种子脚本已把旧库的空串
 * 归一化为 NULL（并有兜底 UPDATE），保持单一判据才能走 {@code idx_pc_parent} 索引。
 */
@Mapper
public interface ProcessConfigMapper extends BaseMapper<ProcessConfig> {

    String COLUMNS = " id, name, alias_name AS aliasName, note, zgbm, location, parent_id AS parentId, "
            + " has_children AS hasChildren, process_time AS processTime, path, disabled, "
            + " is_pipeline AS isPipeline, is_parallel AS isParallel, "
            + " create_by AS createBy, create_time AS createTime, "
            + " update_by AS updateBy, update_time AS updateTime, del_flag AS delFlag ";

    /** 六大阶段（parent_id IS NULL），按业务顺序 */
    @Select("SELECT " + COLUMNS + " FROM t_process_configuration "
            + "WHERE del_flag = 0 AND parent_id IS NULL ORDER BY location ASC")
    List<ProcessConfig> selectStages();

    /** 全部事项（parent_id 非空），按业务顺序 */
    @Select("SELECT " + COLUMNS + " FROM t_process_configuration "
            + "WHERE del_flag = 0 AND parent_id IS NOT NULL ORDER BY location ASC")
    List<ProcessConfig> selectItems();

    /** 某个阶段下的事项 */
    @Select("SELECT " + COLUMNS + " FROM t_process_configuration "
            + "WHERE del_flag = 0 AND parent_id = #{stageId} ORDER BY location ASC")
    List<ProcessConfig> selectItemsByStage(@Param("stageId") String stageId);

    /** 全部启用中的环节（阶段 + 事项），按业务顺序 —— 「29 环节」录入界面的数据源 */
    @Select("SELECT " + COLUMNS + " FROM t_process_configuration "
            + "WHERE del_flag = 0 AND disabled = 0 ORDER BY location ASC")
    List<ProcessConfig> selectEnabledAll();
}
