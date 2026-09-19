package org.jeecg.modules.land.archive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.archive.entity.ArchiveFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @Description: 档案文件 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface ArchiveFileMapper extends BaseMapper<ArchiveFile> {

    /** 某档案下的全部卷内文件（按序号升序） */
    List<ArchiveFile> selectByArchiveId(@Param("archiveId") String archiveId);

    /** 批量取多个档案的文件（导出打包用，避免 N+1） */
    List<ArchiveFile> selectByArchiveIds(@Param("archiveIds") Collection<String> archiveIds);

    /** 单个文件（下载 / 预览 / 删除前校验用） */
    ArchiveFile selectFileById(@Param("id") String id);

    /** 某类别（含子类，按 path 前缀）下的文件数——类别移除前置校验用 */
    int countByCategoryPath(@Param("categoryPath") String categoryPath);

    /** 某档案的文件数统计：{fileCount, totalSize} */
    Map<String, Object> sumByArchiveId(@Param("archiveId") String archiveId);
}
