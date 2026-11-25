package org.jeecg.modules.land.archive.document;

import org.jeecg.modules.land.archive.document.entity.DocReceive;
import org.jeecg.modules.land.archive.document.mapper.DocReceiveMapper;
import org.jeecg.modules.land.archive.document.support.DocSupport;
import org.jeecg.modules.land.archive.document.vo.DocStatVO;
import org.jeecg.modules.land.archive.document.service.impl.DocReceiveServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 收文统计口径单元测试：不连数据库，只锁定状态分组到统计卡的映射。 */
public class DocReceiveStatTest {

    @Test
    public void finishedAndArchivedAreSeparate() {
        DocReceiveServiceImpl service = new DocReceiveServiceImpl();
        DocReceiveMapper mapper = mock(DocReceiveMapper.class);
        DocSupport support = mock(DocSupport.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "docSupport", support);

        when(mapper.countGroupByStatus()).thenReturn(Arrays.asList(
                row(DocReceive.STATUS_FINISHED, 1L),
                row(DocReceive.STATUS_ARCHIVED, 2L),
                row(DocReceive.STATUS_PENDING, 3L)));
        when(support.currentUsername()).thenReturn(null);

        DocStatVO stat = service.queryStat();
        assertEquals(6L, stat.getTotal());
        assertEquals(1L, stat.getFinished(), "已办结只计 status=已办结");
        assertEquals(2L, stat.getArchived(), "已归档独立计数");
        assertEquals(3L, stat.getPending());
        assertEquals(0L, stat.getMyTodo());
    }

    private static Map<String, Object> row(String status, long count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", status);
        row.put("cnt", count);
        return row;
    }
}
