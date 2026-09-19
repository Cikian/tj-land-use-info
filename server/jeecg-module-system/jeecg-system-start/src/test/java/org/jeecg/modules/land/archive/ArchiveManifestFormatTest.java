package org.jeecg.modules.land.archive;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 专测「导出 ZIP 里的 档案清单.xlsx 到底能不能被 Excel 打开」。
 *
 * <p>为什么单独一个用例：{@link ArchiveDemoExportTest} 只断言了 ZIP 的**条目名**，
 * 于是「档案清单.xlsx」这个名字一直在，但里面的**字节**是错的也没人发现 ——
 * 用户双击就报「Excel 无法打开文件，因为文件格式或文件扩展名无效」。
 * 名字对 ≠ 文件对，这个用例补的就是这一步。
 *
 * <p>两种 Excel 容器的魔数完全不同，认错了 Excel 就直接拒绝打开：
 * <ul>
 *   <li>{@code .xlsx} = OOXML，本质是个 ZIP：{@code 50 4B 03 04}（"PK\x03\x04"）</li>
 *   <li>{@code .xls}  = OLE2 复合文档：{@code D0 CF 11 E0 A1 B1 1A E1}</li>
 * </ul>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = JeecgSystemApplication.class)
@Transactional
public class ArchiveManifestFormatTest {

    private static final Charset ZIP_CHARSET = Charset.forName("GBK");
    private static final String MANIFEST = "档案清单.xlsx";

    @Autowired
    private IArchiveService archiveService;

    @Test
    public void testManifestIsARealXlsx() throws Exception {
        File zip = exportDemoZip();
        try {
            byte[] manifest = extractEntry(zip, MANIFEST);
            Assert.assertNotNull("ZIP 里应有 " + MANIFEST, manifest);
            Assert.assertTrue(MANIFEST + " 不应为空", manifest.length > 0);

            String hex = hex(manifest, 8);
            System.out.println("[诊断] " + MANIFEST + " 大小=" + manifest.length + " 首8字节=" + hex);

            // 1) 魔数必须是 OOXML(ZIP)，因为文件名是 .xlsx
            Assert.assertTrue("文件名是 .xlsx，内容却**不是** OOXML(ZIP) 容器，首字节为 " + hex
                            + "；Excel 会报「文件格式或文件扩展名无效」",
                    manifest[0] == 0x50 && manifest[1] == 0x4B
                            && manifest[2] == 0x03 && manifest[3] == 0x04);

            // 2) 再往下走一步：真的用 POI 当 xlsx 打开一次，确认不是「碰巧以 PK 开头」
            try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(manifest))) {
                Assert.assertTrue("工作簿至少应有一个 sheet", wb.getNumberOfSheets() >= 1);
                System.out.println("[诊断] POI 打开成功，sheet 数=" + wb.getNumberOfSheets()
                        + "，首个 sheet 名=" + wb.getSheetName(0)
                        + "，数据行数=" + wb.getSheetAt(0).getLastRowNum());
            }

            // 3) 可选：把这份 ZIP 拷出来给人「双击验证」。
            //    只在显式传了 -Dexport.dump.dir=... 时才落盘，不污染常规测试。
            String dumpDir = System.getProperty("export.dump.dir");
            if (dumpDir != null && !dumpDir.trim().isEmpty()) {
                File dir = new File(dumpDir);
                Assert.assertTrue("导出目录不存在：" + dir.getAbsolutePath(), dir.isDirectory());
                File dump = new File(dir, "导出验证.zip");
                java.nio.file.Files.copy(zip.toPath(), dump.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[诊断] 已导出可双击验证的 ZIP：" + dump.getAbsolutePath()
                        + "（" + dump.length() + " 字节）");
            }
        } finally {
            if (zip != null && zip.exists()) {
                zip.delete();
            }
        }
    }

    private File exportDemoZip() {
        ArchiveQueryDTO demoQuery = new ArchiveQueryDTO();
        demoQuery.setArchiveNo("-DEMO");
        List<Archive> demoArchives = archiveService.queryPage(demoQuery).getRecords();
        Assume.assumeTrue("库中没有演示档案（未执行 07_t_archive_demo_data.sql），跳过本用例",
                demoArchives != null && !demoArchives.isEmpty());

        Archive target = demoArchives.get(0);
        for (Archive candidate : demoArchives) {
            List<ArchiveFile> files = archiveService.queryFiles(candidate.getId());
            if (!files.isEmpty()) {
                target = candidate;
                break;
            }
        }
        ArchiveQueryDTO query = new ArchiveQueryDTO();
        query.setFacilityId(target.getFacilityId());
        return archiveService.exportZip(query, "单元测试");
    }

    /** 从 ZIP 里取出指定条目的字节 */
    private byte[] extractEntry(File zip, String entryName) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip), ZIP_CHARSET)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) > 0) {
                        bos.write(buf, 0, n);
                    }
                    return bos.toByteArray();
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    private String hex(byte[] data, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(len, data.length); i++) {
            sb.append(String.format("%02X ", data[i]));
        }
        return sb.toString().trim();
    }
}
