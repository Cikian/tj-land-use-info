package org.jeecg.modules.land.archive;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.controller.ArchiveCategoryController;
import org.jeecg.modules.land.archive.entity.ArchiveCategory;
import org.jeecg.modules.land.archive.mapper.ArchiveCategoryMapper;
import org.jeecg.modules.land.archive.service.IArchiveCategoryService;
import org.jeecg.modules.land.archive.service.impl.ArchiveCategoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 档案类别树与删除防线纯单测：全部依赖均为 Mock，不连接数据库。 */
class ArchiveCategoryServiceImplTest {

    /**
     * 纯单测没有 Spring/MyBatis 启动过程，ServiceImpl#removeById 需要 TableInfo
     * 才会走到「逻辑删除」分支；不初始化会 NPE（不是业务缺陷）。
     */
    static {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArchiveCategory.class);
    }

    @Test
    void parentArchiveCountIsSumOfWholeSubtree() {
        ArchiveCategory root = category("root", null, "/root");
        ArchiveCategory child = category("child", "root", "/root/child");
        ArchiveCategory leaf = category("leaf", "child", "/root/child/leaf");
        ArchiveCategory other = category("other", null, "/other");
        ArchiveCategoryMapper mapper = mock(ArchiveCategoryMapper.class);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(root, child, leaf, other));
        when(mapper.countTable("t_archive_file")).thenReturn(1);
        when(mapper.countArchiveGroupByCategory()).thenReturn(Arrays.asList(
                countRow("child", 2), countRow("leaf", 3), countRow("other", 7)));

        ArchiveCategoryServiceImpl service = service(mapper);
        List<ArchiveCategory> tree = service.queryTree(null, null, null);

        assertEquals(5L, tree.get(0).getArchiveCount(), "父类必须包含直接与间接子类");
        assertEquals(5L, tree.get(0).getChildren().get(0).getArchiveCount());
        assertEquals(3L, tree.get(0).getChildren().get(0).getChildren().get(0).getArchiveCount());
        assertEquals(7L, tree.get(1).getArchiveCount());
    }

    @Test
    void deleteRejectsChildrenBeforeCheckingFiles() {
        ArchiveCategoryMapper mapper = mock(ArchiveCategoryMapper.class);
        when(mapper.selectById("root")).thenReturn(category("root", null, "/root"));
        when(mapper.selectCount(any())).thenReturn(2L);
        ArchiveCategoryServiceImpl service = service(mapper);

        JeecgBootException error = assertThrows(JeecgBootException.class, () -> service.deleteCategory("root"));

        assertEquals("该类别下存在 2 个子类别，请先移除子类别", error.getMessage());
        verify(mapper, never()).deleteById("root");
        verify(mapper, never()).countArchiveByCategoryPath(any());
    }

    @Test
    void deleteRejectsFileReferencesWithActualCount() {
        ArchiveCategoryMapper mapper = mock(ArchiveCategoryMapper.class);
        when(mapper.selectById("leaf")).thenReturn(category("leaf", null, "/leaf"));
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.countTable("t_archive_file")).thenReturn(1);
        when(mapper.countArchiveByCategoryPath("/leaf")).thenReturn(3);
        ArchiveCategoryServiceImpl service = service(mapper);

        JeecgBootException error = assertThrows(JeecgBootException.class, () -> service.deleteCategory("leaf"));

        assertEquals("该类别（含子类别）下存在 3 个档案文件，无法移除", error.getMessage());
        verify(mapper, never()).deleteById("leaf");
    }

    @Test
    void deleteSoftDeletesLeafWithoutChildrenOrFileReferences() {
        ArchiveCategoryMapper mapper = mock(ArchiveCategoryMapper.class);
        when(mapper.selectById("leaf")).thenReturn(category("leaf", null, "/leaf"));
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.countTable("t_archive_file")).thenReturn(1);
        when(mapper.countArchiveByCategoryPath("/leaf")).thenReturn(0);
        when(mapper.deleteById("leaf")).thenReturn(1);
        ArchiveCategoryServiceImpl service = service(mapper);

        service.deleteCategory("leaf");

        // @TableLogic 使 BaseMapper.deleteById 在实际 MyBatis 中生成 UPDATE del_flag=1。
        verify(mapper).deleteById("leaf");
    }

    @Test
    void deleteFailsClosedWhenReferenceCheckIsUnavailable() {
        ArchiveCategoryMapper mapper = mock(ArchiveCategoryMapper.class);
        when(mapper.selectById("leaf")).thenReturn(category("leaf", null, "/leaf"));
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.countTable("t_archive_file")).thenReturn(0);
        ArchiveCategoryServiceImpl service = service(mapper);

        JeecgBootException error = assertThrows(JeecgBootException.class, () -> service.deleteCategory("leaf"));

        assertEquals("暂时无法校验该类别下的档案文件引用，请稍后重试", error.getMessage());
        verify(mapper, never()).deleteById("leaf");
    }

    @Test
    void deleteDoesNotReportSuccessWhenSoftDeleteAffectsNoRow() {
        ArchiveCategoryMapper mapper = mock(ArchiveCategoryMapper.class);
        when(mapper.selectById("leaf")).thenReturn(category("leaf", null, "/leaf"));
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.countTable("t_archive_file")).thenReturn(1);
        when(mapper.countArchiveByCategoryPath("/leaf")).thenReturn(0);
        when(mapper.deleteById("leaf")).thenReturn(0);
        ArchiveCategoryServiceImpl service = service(mapper);

        JeecgBootException error = assertThrows(JeecgBootException.class, () -> service.deleteCategory("leaf"));

        assertEquals("类别移除失败，请刷新后重试", error.getMessage());
    }

    @Test
    void controllerReturnsActualReferenceFailureMessage() {
        IArchiveCategoryService service = mock(IArchiveCategoryService.class);
        doThrow(new JeecgBootException("该类别（含子类别）下存在 3 个档案文件，无法移除"))
                .when(service).deleteCategory("leaf");
        ArchiveCategoryController controller = new ArchiveCategoryController();
        ReflectionTestUtils.setField(controller, "archiveCategoryService", service);

        Result<?> result = controller.delete("leaf");

        assertFalse(result.isSuccess());
        assertEquals("该类别（含子类别）下存在 3 个档案文件，无法移除", result.getMessage());
    }

    private static ArchiveCategoryServiceImpl service(ArchiveCategoryMapper mapper) {
        ArchiveCategoryServiceImpl service = new ArchiveCategoryServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    private static ArchiveCategory category(String id, String parentId, String path) {
        return new ArchiveCategory()
                .setId(id)
                .setParentId(parentId)
                .setPath(path)
                .setName(id)
                .setStatus(ArchiveCategory.STATUS_ENABLED)
                .setDelFlag(0);
    }

    private static Map<String, Object> countRow(String categoryId, long count) {
        Map<String, Object> row = new HashMap<>();
        row.put("categoryId", categoryId);
        row.put("cnt", count);
        return row;
    }
}
