package org.jeecg.modules.land.archive.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.archive.document.dto.DocQueryDTO;
import org.jeecg.modules.land.archive.document.entity.DocSend;

/**
 * @Description: 发文 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface DocSendMapper extends BaseMapper<DocSend> {

    /** 发文分页查询（附带附件数） */
    IPage<DocSend> selectDocPage(Page<DocSend> page, @Param("q") DocQueryDTO q);

    /** 某年度已用掉的最大流水号 */
    Integer selectMaxSeqOfYear(@Param("prefix") String prefix);

    /** 登记号重复校验 */
    int countByDocNo(@Param("docNo") String docNo, @Param("excludeId") String excludeId);
}
