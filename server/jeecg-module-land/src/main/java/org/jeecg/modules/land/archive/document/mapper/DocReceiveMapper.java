package org.jeecg.modules.land.archive.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.archive.document.dto.DocQueryDTO;
import org.jeecg.modules.land.archive.document.entity.DocReceive;

import java.util.Map;

/**
 * @Description: 收文 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface DocReceiveMapper extends BaseMapper<DocReceive> {

    /** 收文分页查询（附带附件数） */
    IPage<DocReceive> selectDocPage(Page<DocReceive> page, @Param("q") DocQueryDTO q);

    /** 某年度已用掉的最大流水号（登记号自动生成） */
    Integer selectMaxSeqOfYear(@Param("prefix") String prefix);

    /** 登记号重复校验（编辑时用 excludeId 排除自身） */
    int countByDocNo(@Param("docNo") String docNo, @Param("excludeId") String excludeId);

    /** 统计各状态数量：{status, cnt} */
    java.util.List<Map<String, Object>> countGroupByStatus();

    /** 我的待办数 */
    int countMyTodo(@Param("username") String username);
}
