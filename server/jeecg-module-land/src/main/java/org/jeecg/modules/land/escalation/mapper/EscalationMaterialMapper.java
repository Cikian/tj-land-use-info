package org.jeecg.modules.land.escalation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.escalation.entity.EscalationMaterial;

import java.util.List;
import java.util.Map;

/**
 * @Description: 提级论证材料 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>方法与档案模块的 {@code ArchiveFileMapper} 同构（{@code selectByXxx} /
 * {@code selectXxxById} / {@code sumByXxx}），保持两个模块写法一致。
 */
public interface EscalationMaterialMapper extends BaseMapper<EscalationMaterial> {

    /** 某项目的材料列表（按类型、版本、上传时间排序） */
    List<EscalationMaterial> selectByProjectId(@Param("projectId") String projectId);

    /** 批量按项目取材料（导出/详情批量组装用；空集合由调用方拦截） */
    List<EscalationMaterial> selectByProjectIds(@Param("projectIds") List<String> projectIds);

    /** 单个材料（下载/预览/删除前查库用；只认 id，磁盘路径由服务端解析） */
    EscalationMaterial selectMaterialById(@Param("id") String id);

    /**
     * 统计某项目的材料数与总字节。
     *
     * @return fileCount / totalSize 两个键；无材料时均为 0
     */
    Map<String, Object> sumByProjectId(@Param("projectId") String projectId);
}
