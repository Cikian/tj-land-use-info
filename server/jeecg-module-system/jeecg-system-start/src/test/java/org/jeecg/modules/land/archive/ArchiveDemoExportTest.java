package org.jeecg.modules.land.archive;

import org.jeecg.JeecgSystemApplication;
import org.jeecg.modules.land.archive.dto.ArchiveQueryDTO;
import org.jeecg.modules.land.archive.entity.Archive;
import org.jeecg.modules.land.archive.entity.ArchiveFile;
import org.jeecg.modules.land.archive.service.IArchiveService;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 档案「按项目导出 ZIP」的端到端校验（数据库 → 磁盘文件 → ZIP → 档案清单.xlsx）。
 *
 * <p>这组用例依赖演示数据（{@code 07_t_archive_demo_data.sql}）与配套生成的磁盘文件
 * （{@code gen-archive-demo-files.ps1}）。**没有演示数据时用 {@link Assume} 跳过**，
 * 因此在干净的库上跑也不会红。
 *
 * <p>为什么要单独验这一条：导出是唯一一条「数据库 + 磁盘 + 压缩 + Excel」四条链路串起来的接口，
 * 只断言「生成了非空 ZIP」抓不到「文件全缺失，包里只有一堆 .缺失说明.txt」这种情况。
 * 这里直接读回 ZIP 条目，断言**真实的卷内文件确实被打进包里**，并附带了档案清单.xlsx。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = JeecgSystemApplication.class)
@Transactional
public class ArchiveDemoExportTest {

    /**
     * ZIP 条目名是用 GBK 写的（简体中文 Windows 资源管理器的默认解析方式），
     * 读回来时也必须用 GBK，否则中文文件名会变成乱码，断言会误判。
     */
    private static final Charset ZIP_CHARSET = Charset.forName("GBK");

    @Autowired
    private IArchiveService archiveService;

    @Test
    public void testExportZipContainsRealArchiveFiles() throws Exception {
        // 1) 找一卷有卷内文件的演示档案（档案号里带 -DEMO）
        ArchiveQueryDTO demoQuery = new ArchiveQueryDTO();
        demoQuery.setArchiveNo("-DEMO");
        List<Archive> demoArchives = archiveService.queryPage(demoQuery).getRecords();

        Assume.assumeTrue("库中没有演示档案（未执行 07_t_archive_demo_data.sql），跳过本用例",
                demoArchives != null && !demoArchives.isEmpty());

        Archive target = null;
        List<ArchiveFile> targetFiles = new ArrayList<>();
        for (Archive candidate : demoArchives) {
            List<ArchiveFile> files = archiveService.queryFiles(candidate.getId());
            if (!files.isEmpty()) {
                target = candidate;
                targetFiles = files;
                break;
            }
        }
        Assume.assumeTrue("演示档案下没有卷内文件，跳过本用例", target != null);
        // lambda 只能捕获 effectively final 的变量，target 是在循环里赋值的，这里固化成 final
        final Archive exportTarget = target;

        // 2) 按该档案所属项目导出
        ArchiveQueryDTO query = new ArchiveQueryDTO();
        query.setFacilityId(exportTarget.getFacilityId());

        File zip = archiveService.exportZip(query, "单元测试");
        try {
            Assert.assertNotNull("导出应返回临时文件", zip);
            Assert.assertTrue("导出的 ZIP 不应为空", zip.exists() && zip.length() > 0);

            Set<String> entries = readEntries(zip);

            // 3) ★ 关键断言：演示文件是真实存在的磁盘文件，必须被打进包里，
            //    而不是退化成「.缺失说明.txt」
            ArchiveFile sample = targetFiles.get(0);
            String expectedFileSuffix = sample.getFileName();
            boolean hasRealFile = entries.stream()
                    .anyMatch(name -> name.endsWith(expectedFileSuffix) && !name.endsWith(".缺失说明.txt"));
            Assert.assertTrue("ZIP 里应包含真实卷内文件「" + expectedFileSuffix + "」，实际条目：\n"
                    + String.join("\n", entries), hasRealFile);

            // 4) 目录结构应为 {宗地编号}/{配套项目}/{档案类别}/{档案号_档案名称}/{文件名}
            Assert.assertTrue("ZIP 条目应按「宗地编号/配套项目/档案类别/档案/文件名」分层，实际条目：\n"
                            + String.join("\n", entries),
                    entries.stream().anyMatch(name -> name.startsWith(exportTarget.getCrzdbh() + "/"
                            + exportTarget.getPtxmmc() + "/")));

            // 5) 根目录必须附一份档案清单.xlsx
            Assert.assertTrue("ZIP 根目录应包含「档案清单.xlsx」", entries.contains("档案清单.xlsx"));

            // 6) 不该出现「无卷内文件.txt」这种兜底占位（该项目下有文件的档案）
            Assert.assertFalse("有卷内文件的档案不应生成「无卷内文件.txt」占位",
                    entries.stream().anyMatch(name -> name.endsWith("无卷内文件.txt")));
        } finally {
            if (zip != null && zip.exists() && !zip.delete()) {
                System.err.println("临时 ZIP 清理失败：" + zip.getAbsolutePath());
            }
        }
    }

    @Test
    public void testDemoArchiveStatisticsAreConsistent() {
        ArchiveQueryDTO demoQuery = new ArchiveQueryDTO();
        demoQuery.setArchiveNo("-DEMO");
        long demoCount = archiveService.queryPage(demoQuery).getTotal();
        Assume.assumeTrue("库中没有演示档案，跳过本用例", demoCount > 0);

        ArchiveQueryDTO query = new ArchiveQueryDTO();
        query.setArchiveNo("-DEMO");

        // 主表上的冗余统计（file_count / total_size）应与卷内文件实际汇总一致
        List<Archive> archives = archiveService.queryPage(query).getRecords();
        for (Archive archive : archives) {
            List<ArchiveFile> files = archiveService.queryFiles(archive.getId());
            Assert.assertEquals("档案【" + archive.getArchiveNo() + "】的 file_count 与实际文件数不一致",
                    files.size(), archive.getFileCount() == null ? 0 : archive.getFileCount().intValue());
            long sum = files.stream().mapToLong(f -> f.getFileSize() == null ? 0L : f.getFileSize()).sum();
            Assert.assertEquals("档案【" + archive.getArchiveNo() + "】的 total_size 与实际字节数不一致",
                    sum, archive.getTotalSize() == null ? 0L : archive.getTotalSize().longValue());
        }
    }

    /** 读回 ZIP 的全部条目名（按写入时的 GBK 解码） */
    private Set<String> readEntries(File zip) throws Exception {
        Set<String> entries = new LinkedHashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip), ZIP_CHARSET)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                zis.closeEntry();
            }
        }
        return entries;
    }
}
