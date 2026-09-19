package org.jeecg.modules.land.archive.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.archive.document.entity.DocReceiveFlow;

import java.util.List;

/**
 * @Description: 收文流转记录 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface DocReceiveFlowMapper extends BaseMapper<DocReceiveFlow> {

    /** 某收文的全部流转记录（按顺序正序，供时间轴展示） */
    List<DocReceiveFlow> selectByDocId(@Param("docId") String docId);

    /** 取当前待办记录（handleTime 为空且 seqNo 最大的那条），没有则返回 null */
    DocReceiveFlow selectCurrentOpenFlow(@Param("docId") String docId);
}
