package org.jeecg.modules.land.archive;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.modules.land.LandTestConfig;
import org.jeecg.modules.land.archive.dto.ArchiveQueryDTO;
import org.jeecg.modules.land.archive.entity.Archive;
import org.jeecg.modules.land.archive.entity.ArchiveCategory;
import org.jeecg.modules.land.archive.mapper.ArchiveCategoryMapper;
import org.jeecg.modules.land.archive.mapper.ArchiveMapper;
import org.jeecg.modules.land.archive.vo.ArchiveProjectVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-04 的真实 SQL 回归，只执行 SELECT。
 *
 * <p>刻意不继承 LandIntegrationTestBase：该基类在 AfterTransaction 中会执行测试行 DELETE，
 * 而本轮任务禁止任何数据库写入。本类不开启事务，也不调用任何新增、修改或删除方法。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LandTestConfig.class)
class ArchiveMaintenanceReadonlyTest {

    @Autowired
    private ArchiveMapper archiveMapper;

    @Autowired
    private ArchiveCategoryMapper categoryMapper;

    @Autowired
    private javax.sql.DataSource dataSource;

    @Test
    void reportedArchiveHasSameDatabaseStatusInListAndDetail() {
        ArchiveQueryDTO query = new ArchiveQueryDTO();
        query.setArchiveNo("DA-2026-0002");
        IPage<Archive> page = archiveMapper.selectArchivePage(new Page<>(1, 10), query);

        assertEquals(1, page.getTotal(), "报告中的档案必须能按档案号唯一命中");
        Archive listed = page.getRecords().get(0);
        assertEquals("2119620585792770049", listed.getId());
        Archive detail = archiveMapper.selectDetailById(listed.getId());
        assertNotNull(detail);
        assertEquals("未归档", listed.getStatus());
        assertEquals(listed.getStatus(), detail.getStatus(), "列表与详情都必须直接读取 t_archive.status");
    }

    @Test
    void parentCategoryWithPathIncludesAllDescendantArchivesInBothViews() {
        List<ArchiveCategory> categories = categoryMapper.selectList(null);
        Map<String, Long> directCounts = directFileCounts();
        ArchiveCategory parent = null;
        long expected = 0L;
        for (ArchiveCategory candidate : categories) {
            if (!Integer.valueOf(1).equals(candidate.getHasChildren())) {
                continue;
            }
            long count = countArchivesInSubtree(candidate.getPath());
            // 选“自身没有文件、子树有文件”的父类，才能精确防止 path 与 categoryId 被叠加成 AND。
            if (count > 0 && directCounts.getOrDefault(candidate.getId(), 0L) == 0L) {
                parent = candidate;
                expected = count;
                break;
            }
        }
        assertNotNull(parent, "测试库应至少存在一个只有子类引用的父类别");

        ArchiveQueryDTO query = new ArchiveQueryDTO();
        query.setCategoryId(parent.getId());
        query.setCategoryPath(parent.getPath());
        IPage<Archive> page = archiveMapper.selectArchivePage(new Page<>(1, 500), query);
        assertEquals(expected, page.getTotal(), "父类平铺列表必须包含全部子类档案");

        long projectTotal = archiveMapper.countProjectGroups(query);
        List<ArchiveProjectVO> projects = archiveMapper.selectProjectGroups(query, 0, 500);
        assertTrue(projectTotal > 0, "默认项目视图也必须命中父类子树");
        assertFalse(projects.isEmpty());
        assertEquals(projectTotal, projects.size());
    }

    @Test
    void categoryIdWithoutPathKeepsExactLeafMatching() {
        List<ArchiveCategory> categories = categoryMapper.selectList(null);
        Map<String, Long> directCounts = directFileCounts();
        ArchiveCategory leaf = null;
        for (ArchiveCategory candidate : categories) {
            if (directCounts.getOrDefault(candidate.getId(), 0L) > 0) {
                leaf = candidate;
                break;
            }
        }
        assertNotNull(leaf);

        ArchiveQueryDTO query = new ArchiveQueryDTO();
        query.setCategoryId(leaf.getId());
        long expected = countArchivesAtExactCategory(leaf.getId());
        IPage<Archive> page = archiveMapper.selectArchivePage(new Page<>(1, 500), query);
        assertTrue(expected > 0);
        assertEquals(expected, page.getTotal(), "未传 path 时仍按叶子 category_id 精确匹配");
    }

    private Map<String, Long> directFileCounts() {
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : categoryMapper.countArchiveGroupByCategory()) {
            Object id = row.get("categoryId");
            Object count = row.get("cnt");
            if (id != null) {
                result.put(String.valueOf(id), count == null ? 0L : Long.parseLong(String.valueOf(count)));
            }
        }
        return result;
    }

    private long countArchivesInSubtree(String path) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(DISTINCT a.id) FROM t_archive a WHERE a.del_flag = 0 AND EXISTS ("
                        + "SELECT 1 FROM t_archive_file f WHERE f.archive_id = a.id AND f.del_flag = 0 "
                        + "AND (f.category_path = ? OR f.category_path LIKE CONCAT(?, '/%')))",
                Long.class, path, path);
    }

    private long countArchivesAtExactCategory(String categoryId) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(DISTINCT a.id) FROM t_archive a WHERE a.del_flag = 0 AND EXISTS ("
                        + "SELECT 1 FROM t_archive_file f WHERE f.archive_id = a.id AND f.del_flag = 0 "
                        + "AND f.category_id = ?)",
                Long.class, categoryId);
    }
}
