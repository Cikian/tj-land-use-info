package org.jeecg.modules.land.archive;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.land.archive.controller.ArchiveController;
import org.jeecg.modules.land.archive.entity.Archive;
import org.jeecg.modules.land.archive.service.IArchiveService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 档案新增接口契约纯单测，不连接数据库。 */
class ArchiveControllerTest {

    @Test
    void addReturnsCreatedArchiveIdForListRefresh() {
        IArchiveService service = mock(IArchiveService.class);
        when(service.createArchive(any(), any())).thenReturn("new-archive-id");
        ArchiveController controller = new ArchiveController();
        ReflectionTestUtils.setField(controller, "archiveService", service);
        Archive body = new Archive();
        body.setArchiveName("新增档案");

        Result<String> result = controller.add(body);

        assertTrue(result.isSuccess());
        assertEquals("new-archive-id", result.getResult(), "新增 ID 必须返回给前端，供保存后刷新识别新增场景");
    }
}
