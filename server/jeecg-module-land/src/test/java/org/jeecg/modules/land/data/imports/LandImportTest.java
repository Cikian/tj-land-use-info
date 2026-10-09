package org.jeecg.modules.land.data.imports;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.imports.service.ILandImportService;
import org.jeecg.modules.land.data.imports.service.ILandImportService.ParsedFile;
import org.jeecg.modules.land.data.service.ILandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Description: 经营性用地批量导入 - 集成测试（模板 / 校验 / 错误回执 / 入库）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 为什么测试文件由「模板自己」生成</b>：
 * 上传的 Excel 不是手搓的二进制，而是先调 {@link ILandImportService#buildTemplate()}
 * 拿模板、再往里填格子。这样「模板能不能被自己的解析器认出来」这件事被测到了 ——
 * 模板与解析器一旦脱节（改了列名忘了改解析、或反过来），这些测试立刻变红。
 *
 * <p><b>★ 为什么用 {@code crzdbh} 前缀 {@value #TEST_PREFIX} 造数据</b>：
 * 测试方法跑在一个事务里、结束自动回滚；但万一回滚没生效，
 * {@link LandIntegrationTestBase#cleanTestRows()} 里按这个前缀做的兜底物理删除
 * 能保证不把测试数据留在库里。前缀用的是业务上不可能出现的写法。
 */
public class LandImportTest extends LandIntegrationTestBase {

    /** 测试数据专用的出让宗地编号前缀（业务上不会出现，兜底清理按它识别） */
    private static final String TEST_PREFIX = "TJ-TEST-IMP-";

    @Autowired
    private ILandImportService importService;

    @Autowired
    private ILandService landService;

    // ==================================================================
    // 一、模板
    // ==================================================================

    @Test
    public void templateHasTwoHeaderRowsAndInstructionSheet() throws Exception {
        Workbook workbook = importService.buildTemplate();
        try {
            assertEquals(2, workbook.getNumberOfSheets(),
                    "模板应有「经营性用地信息表模板」与「填表说明」两个 sheet");
            Sheet sheet = workbook.getSheetAt(0);
            Row columnRow = sheet.getRow(0);
            Row labelRow = sheet.getRow(1);

            List<LandImportField.FieldSpec> fields = LandImportField.templated();
            assertEquals(fields.size(), columnRow.getLastCellNum(),
                    "第 1 行表头列数应等于字段字典里的模板字段数");
            assertEquals(fields.size(), labelRow.getLastCellNum(),
                    "第 2 行（中文说明）列数应与第 1 行一致");

            // 第 1 行必须是英文字段名，且与字段字典逐列对应（顺序也一致）
            for (int i = 0; i < fields.size(); i++) {
                assertEquals(fields.get(i).getColumn(), columnRow.getCell(i).getStringCellValue(),
                        "第 " + (i + 1) + " 列的英文字段名应与字段字典一致");
            }
            // 第 2 行是中文说明，必填列带（必填）后缀
            assertEquals("出让宗地编号（必填）", labelRow.getCell(0).getStringCellValue());
            assertTrue(labelRow.getCell(1).getStringCellValue().startsWith("项目分类"));

            // 模板里绝对不能出现这几个系统列
            List<String> columns = new ArrayList<>();
            for (int i = 0; i < columnRow.getLastCellNum(); i++) {
                columns.add(columnRow.getCell(i).getStringCellValue());
            }
            for (String forbidden : Arrays.asList("id", "wcd", "delFlag", "del_flag",
                    "createAccount", "createTime", "create_by", "create_time", "source_id")) {
                assertFalse(columns.contains(forbidden),
                        "模板里不该出现系统列：" + forbidden);
            }
            // 旧系统模板排除 id/createAccount/createTime/delFlag/wcd，
            // 本实现额外排除 source_id；这里再确认一次旧系统那 5 个确实排除了
            assertTrue(columns.contains("lrdw") && columns.contains("lrr") && columns.contains("lxdh"),
                    "录入单位/录入人/联系电话必须在模板里（旧系统要求批量导入时必填）");

            // 填表说明 sheet 必须把每个字段都解释到，并说明「重复编号怎么办」
            Sheet instruction = workbook.getSheetAt(1);
            StringBuilder all = new StringBuilder();
            for (int r = 0; r <= instruction.getLastRowNum(); r++) {
                Row row = instruction.getRow(r);
                if (row == null) {
                    continue;
                }
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    if (row.getCell(c) != null) {
                        all.append(row.getCell(c).toString()).append('|');
                    }
                }
            }
            String text = all.toString();
            for (LandImportField.FieldSpec spec : fields) {
                assertTrue(text.contains(spec.getColumn()),
                        "填表说明里应解释字段：" + spec.getColumn());
            }
            assertTrue(text.contains("先预览再导入"), "填表说明里应说明「先预览再导入」");
            assertTrue(text.contains("错误回执"), "填表说明里应说明错误回执怎么用");
            assertTrue(text.contains("重复宗地编号"), "填表说明里应说明重复编号的处理");
        } finally {
            workbook.close();
        }
    }

    // ==================================================================
    // 二、预览（不写库）
    // ==================================================================

    @Test
    public void previewNeverWritesAndReportsValidRows() throws Exception {
        String crzdbh = uniqueNo("ok");
        MockMultipartFile file = buildTemplateFile(sheet -> {
            fillValidRow(sheet, 2, crzdbh, "测试地块一");
        });

        LandImportResultVO preview = importService.preview(file, LandDuplicateStrategy.REJECT);
        assertEquals(1, preview.getTotalRows(), "应解析到 1 个数据行");
        assertEquals(1, preview.getValidRows(), "应 1 行校验通过");
        assertEquals(0, preview.getInsertedRows(), "预览绝不写库");
        assertEquals(0, preview.getUpdatedRows(), "预览绝不写库");
        assertEquals(0, preview.getWillUpdateRows(), "库里没有这条，不该算「将覆盖」");
        assertEquals(1, preview.getWillInsertRows(), "库里没有这条，应算「将新增」");
        assertTrue(preview.getErrors().isEmpty(), "不该有错误：" + describe(preview));
        assertFalse(preview.isAborted(), "没有错误时不该标记为「不会写入」");

        // ★ 关键：预览之后库里不能有这条记录
        assertNull(landService.queryByCrzdbh(crzdbh), "预览绝不能写库");
    }

    @Test
    public void previewReportsRowLevelErrorsWithExcelRowNumbers() throws Exception {
        // ★ 注意填写顺序：fillValidRow 会写一整套「合法值」，
        //   所以要把「故意写错的列」放在它**之后**再覆盖，
        //   否则会被合法值盖回去，测试就变成了「什么都没测」。
        MockMultipartFile file = buildTemplateFile(sheet -> {
            // 第 3 行：crzdbh 为空（必填）
            fillValidRow(sheet, 2, uniqueNo("blank"), "测试地块一");
            setCell(sheet, 2, "crzdbh", "");
            // 第 4 行：手机号格式错
            fillValidRow(sheet, 3, uniqueNo("phone"), "测试地块二");
            setCell(sheet, 3, "lxdh", "12345");
            // 第 5 行：项目分类非法
            fillValidRow(sheet, 4, uniqueNo("xmfl"), "测试地块三");
            setCell(sheet, 4, "xmfl", "国家级项目");
            // 第 6 行：日期非法
            fillValidRow(sheet, 5, uniqueNo("date"), "测试地块四");
            setCell(sheet, 5, "crsj", "去年六月");
            // 第 7 行：数字列填了带单位的文本
            fillValidRow(sheet, 6, uniqueNo("num"), "测试地块五");
            setCell(sheet, 6, "crj", "十二亿元");
            // 第 8 行：行政区划不在 16 区里
            fillValidRow(sheet, 7, uniqueNo("xzqh"), "测试地块六");
            setCell(sheet, 7, "xzqh", "市内六区");
            // 第 9 行：完全合法
            fillValidRow(sheet, 8, uniqueNo("good"), "测试地块七");
        });

        LandImportResultVO preview = importService.preview(file, LandDuplicateStrategy.REJECT);
        assertEquals(7, preview.getTotalRows(), "应解析到 7 个数据行");
        assertEquals(1, preview.getValidRows(), "只有最后一行合法：" + describe(preview));
        assertTrue(preview.isAborted(), "有错误时预览应标记为「不会写入」");
        assertEquals(0, preview.getInsertedRows());

        // 错误必须精确定位到 Excel 1 基行号
        java.util.Map<Integer, String> errorByRow = new java.util.HashMap<>();
        for (LandImportResultVO.RowError error : preview.getErrors()) {
            errorByRow.merge(error.getRowNum(), error.getMessage(), (a, b) -> a + " ;; " + b);
        }
        assertTrue(errorByRow.containsKey(3), "第 3 行（crzdbh 为空）应报错：" + describe(preview));
        assertTrue(errorByRow.get(3).contains("不能为空"), "必填错误提示：" + errorByRow.get(3));
        assertTrue(errorByRow.containsKey(4), "第 4 行（手机号）应报错：" + describe(preview));
        assertTrue(errorByRow.get(4).contains("手机号"), "手机号错误提示：" + errorByRow.get(4));
        assertTrue(errorByRow.containsKey(5), "第 5 行（项目分类）应报错：" + describe(preview));
        assertTrue(errorByRow.get(5).contains("市级项目"), "应提示允许的两个取值：" + errorByRow.get(5));
        assertTrue(errorByRow.containsKey(6), "第 6 行（日期）应报错：" + describe(preview));
        assertTrue(errorByRow.get(6).contains("日期"), "日期错误提示：" + errorByRow.get(6));
        assertTrue(errorByRow.containsKey(7), "第 7 行（数字）应报错：" + describe(preview));
        assertTrue(errorByRow.get(7).contains("数字"), "数字错误提示：" + errorByRow.get(7));
        assertTrue(errorByRow.containsKey(8), "第 8 行（行政区划）应报错：" + describe(preview));
        assertTrue(errorByRow.get(8).contains("行政区划"), "行政区划错误提示：" + errorByRow.get(8));
        assertFalse(errorByRow.containsKey(9), "第 9 行是合法的，不该报错：" + describe(preview));
    }

    @Test
    public void previewDetectsDuplicateInsideFile() throws Exception {
        String crzdbh = uniqueNo("dup-in-file");
        MockMultipartFile file = buildTemplateFile(sheet -> {
            fillValidRow(sheet, 2, crzdbh, "同编号地块甲");
            fillValidRow(sheet, 3, crzdbh, "同编号地块乙");
        });
        LandImportResultVO preview = importService.preview(file, LandDuplicateStrategy.REJECT);
        assertEquals(2, preview.getTotalRows());
        assertEquals(1, preview.getValidRows(), "同一编号只允许出现一次");
        assertEquals(1, preview.getErrors().size(), "第二次出现应报错：" + describe(preview));
        assertTrue(preview.getErrors().get(0).getMessage().contains("重复"),
                "错误提示应说明文件内重复：" + preview.getErrors().get(0).getMessage());
        assertTrue(preview.getErrors().get(0).getMessage().contains("第 3 行"),
                "应能指出首次出现的行号：" + preview.getErrors().get(0).getMessage());
    }

    @Test
    public void previewRejectsFileWithoutRequiredHeaders() throws Exception {
        // 随便造一个只有一列、且列名不是本系统字段的文件
        Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        Sheet sheet = workbook.createSheet("随便");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("道路名称");
        Row data = sheet.createRow(1);
        data.createCell(0).setCellValue("某条路");
        MockMultipartFile file = toFile(workbook, "随便.xlsx");

        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> importService.preview(file, LandDuplicateStrategy.REJECT));
        assertTrue(error.getMessage().contains("模板不匹配"),
                "错误提示应指明模板不匹配：" + error.getMessage());
    }

    @Test
    public void previewRejectsMissingRequiredColumn() throws Exception {
        // 表头里有 crzdbh，但缺 xmfl / lrdw / lrr / lxdh
        Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        Sheet sheet = workbook.createSheet("缺列");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("crzdbh");
        header.createCell(1).setCellValue("dkmc");
        Row data = sheet.createRow(1);
        data.createCell(0).setCellValue(uniqueNo("missing-col"));
        data.createCell(1).setCellValue("某地块");
        MockMultipartFile file = toFile(workbook, "缺列.xlsx");

        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> importService.preview(file, LandDuplicateStrategy.REJECT));
        assertTrue(error.getMessage().contains("缺少必填列"), "错误提示：" + error.getMessage());
        assertTrue(error.getMessage().contains("xmfl"), "应指出缺了 xmfl：" + error.getMessage());
    }

    @Test
    public void previewAcceptsLegacyTwoRowHeaderExactly() throws Exception {
        // ★ 兼容旧系统模板：第 1 行英文字段名 + 第 2 行中文说明，数据从第 3 行开始。
        //   这里显式用「旧系统那套中文说明文字」造文件，验证说明行不会被当成数据。
        String crzdbh = uniqueNo("legacy");
        Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        Sheet sheet = workbook.createSheet("经营性用地信息信息表模板");
        String[] columns = {"crzdbh", "xmfl", "dkmc", "xzqh", "lrdw", "lrr", "lxdh", "crj", "crsj", "ghydxz"};
        String[] labels = {"出让宗地编号", "项目分类", "地块名称", "行政区划", "录入单位", "录入人",
                "联系电话", "出让金（亿元）", "出让时间", "规划用地性质"};
        Row columnRow = sheet.createRow(0);
        Row labelRow = sheet.createRow(1);
        for (int i = 0; i < columns.length; i++) {
            columnRow.createCell(i).setCellValue(columns[i]);
            labelRow.createCell(i).setCellValue(labels[i]);
        }
        Row data = sheet.createRow(2);
        data.createCell(0).setCellValue(crzdbh);
        data.createCell(1).setCellValue("区级项目");
        data.createCell(2).setCellValue("旧模板地块");
        data.createCell(3).setCellValue("西青区");
        data.createCell(4).setCellValue("测试录入单位");
        data.createCell(5).setCellValue("测试录入人");
        data.createCell(6).setCellValue("13800000002");
        data.createCell(7).setCellValue("3.14");
        data.createCell(8).setCellValue("2024-06-18");
        data.createCell(9).setCellValue("城镇住宅、商服");
        MockMultipartFile file = toFile(workbook, "旧模板.xlsx");

        LandImportResultVO preview = importService.preview(file, LandDuplicateStrategy.REJECT);
        assertEquals(1, preview.getTotalRows(),
                "旧模板的中文说明行不能被当成数据行：" + describe(preview));
        assertTrue(preview.getErrors().isEmpty(), "旧模板应能正常解析：" + describe(preview));
        assertEquals(1, preview.getValidRows());
    }

    // ==================================================================
    // 三、入库
    // ==================================================================

    @Test
    public void confirmInsertsAllConvertedValues() throws Exception {
        String crzdbh = uniqueNo("insert");
        MockMultipartFile file = buildTemplateFile(sheet -> {
            fillValidRow(sheet, 2, crzdbh, "完整字段地块");
            setCell(sheet, 2, "xzqh", "武清区");
            setCell(sheet, 2, "ghydxz", "城镇住宅、商服");
            setCell(sheet, 2, "crj", "12.35");
            setCell(sheet, 2, "crsj", "2024-06-18");
            setCell(sheet, 2, "kjsydmj", "45231.5");
            setCell(sheet, 2, "zydmj", "51008");
            setCell(sheet, 2, "jsmj", "90463");
            setCell(sheet, 2, "nrcbdptf", "1580");
            setCell(sheet, 2, "srr", "测试受让人");
            setCell(sheet, 2, "htydjfsj", "2026-12-31");
            setCell(sheet, 2, "lpmc", "测试楼盘");
            setCell(sheet, 2, "lpjfsj", "2027/6/30");
            setCell(sheet, 2, "dz", "规划路一");
            setCell(sheet, 2, "xz", "现状住宅");
            setCell(sheet, 2, "nz", "规划绿地");
            setCell(sheet, 2, "bz", "某某路");
            setCell(sheet, 2, "tdzldw", "测试整理单位");
            setCell(sheet, 2, "tdzljhxdwjh", "津国土房整〔2024〕15号");
            setCell(sheet, 2, "tdzljh", "是");
            setCell(sheet, 2, "ptsfqq", "否");
            setCell(sheet, 2, "ptqkh", "√");
            setCell(sheet, 2, "ptcbh", "是");
            setCell(sheet, 2, "crzdtxsj", "否");
            setCell(sheet, 2, "ptjsnr", "地块北侧规划路及雨污水管线");
            setCell(sheet, 2, "zlqsnrsm", "缺少出让合同扫描件");
            setCell(sheet, 2, "beizhu", "备注内容");
        });

        LandImportResultVO result = importService.confirm(file, LandDuplicateStrategy.REJECT, false);
        assertTrue(result.getErrors().isEmpty(), "不该有错误：" + describe(result));
        assertEquals(1, result.getInsertedRows(), "应新增 1 条");
        assertEquals(0, result.getUpdatedRows());

        Land saved = landService.queryByCrzdbh(crzdbh);
        assertNotNull(saved, "库里应能查到新记录");
        assertEquals("完整字段地块", saved.getDkmc());
        assertEquals("武清区", saved.getXzqh());
        assertEquals("区级项目", saved.getXmfl());
        assertEquals(0, new BigDecimal("12.35").compareTo(saved.getCrj()), "出让金应正确落库");
        assertEquals(0, new BigDecimal("45231.50").compareTo(saved.getKjsydmj()));
        assertEquals("2024-06-18", new SimpleDateFormat("yyyy-MM-dd").format(saved.getCrsj()));
        assertEquals("2027-06-30", new SimpleDateFormat("yyyy-MM-dd").format(saved.getLpjfsj()),
                "2027/6/30 这种写法也要能解析");
        // ★ 是/否 归一化：√ 应写成「是」
        assertEquals("是", saved.getPtqkh(), "√ 应归一化为「是」");
        assertEquals("否", saved.getPtsfqq());
        assertEquals(0, Integer.valueOf(0).compareTo(saved.getDelFlag()), "新导入记录 del_flag 必须是 0");
        assertNotNull(saved.getCreateTime(), "创建时间应自动写入");
        // 模板外的系统列不该被写脏
        assertNull(saved.getSourceId(), "source_id 是迁移溯源列，导入不该写它");
    }

    @Test
    public void confirmRefusesWholeBatchWhenErrorsExist() throws Exception {
        String crzdbh = uniqueNo("strict");
        MockMultipartFile file = buildTemplateFile(sheet -> {
            fillValidRow(sheet, 2, crzdbh, "合法行");
            // 第 4 行：手机号错 —— 旧系统会「中断并留下半成品」，本实现应一行都不写
            fillValidRow(sheet, 3, uniqueNo("strict-bad"), "非法行");
            setCell(sheet, 3, "lxdh", "abc");
        });

        LandImportResultVO result = importService.confirm(file, LandDuplicateStrategy.REJECT, false);
        assertEquals(0, result.getInsertedRows(), "有错误行时默认整批不入库");
        assertTrue(result.isAborted());
        assertEquals(1, result.getErrorCount(), "错误清单仍要回执给用户");
        assertNull(landService.queryByCrzdbh(crzdbh),
                "★ 整批不入库时，连合法行也不该被写入（旧系统在这里会留下半成品）");
    }

    @Test
    public void confirmWithSkipErrorRowsImportsValidRowsOnly() throws Exception {
        String crzdbh = uniqueNo("relaxed");
        MockMultipartFile file = buildTemplateFile(sheet -> {
            fillValidRow(sheet, 2, crzdbh, "合法行");
            fillValidRow(sheet, 3, uniqueNo("relaxed-bad"), "非法行");
            setCell(sheet, 3, "lxdh", "abc");
        });

        LandImportResultVO strict = importService.confirm(file, LandDuplicateStrategy.REJECT, false);
        assertEquals(0, strict.getInsertedRows());

        LandImportResultVO relaxed = importService.confirm(file, LandDuplicateStrategy.REJECT, true);
        assertEquals(1, relaxed.getInsertedRows(), "勾选跳过错误行时应只导入合法行");
        assertEquals(1, relaxed.getErrorCount(), "错误清单仍然要回执");
        assertNotNull(landService.queryByCrzdbh(crzdbh), "合法行应已落库");
    }

    @Test
    public void duplicateStrategyControlsBehaviour() throws Exception {
        String crzdbh = uniqueNo("dup-strategy");
        // 先入库一条
        LandImportResultVO first = importService.confirm(
                buildTemplateFile(sheet -> {
                    fillValidRow(sheet, 2, crzdbh, "原始地块名");
                    setCell(sheet, 2, "crj", "1.00");
                    setCell(sheet, 2, "srr", "原始受让人");
                }), LandDuplicateStrategy.REJECT, false);
        assertEquals(1, first.getInsertedRows(), "首次导入应新增：" + describe(first));

        // ① REJECT：报错、不写库
        MockMultipartFile second = buildTemplateFile(sheet -> {
            fillValidRow(sheet, 2, crzdbh, "改名后地块");
            setCell(sheet, 2, "crj", "2.00");
        });
        LandImportResultVO rejected = importService.preview(second, LandDuplicateStrategy.REJECT);
        assertEquals(1, rejected.getErrorCount(), "REJECT 策略下重复编号应报错");
        assertTrue(rejected.getErrors().get(0).getMessage().contains("已存在"),
                "错误提示应说明已存在：" + rejected.getErrors().get(0).getMessage());
        assertEquals("原始地块名", landService.queryByCrzdbh(crzdbh).getDkmc(), "不应被改动");

        // ② SKIP：跳过重复、不写库
        LandImportResultVO skipped = importService.confirm(second, LandDuplicateStrategy.SKIP, false);
        assertEquals(0, skipped.getInsertedRows());
        assertEquals(0, skipped.getUpdatedRows());
        assertEquals(1, skipped.getSkippedDuplicates(), "应统计到 1 行被跳过");
        assertEquals("原始地块名", landService.queryByCrzdbh(crzdbh).getDkmc(), "不应被改动");

        // ③ UPDATE：覆盖更新，且**空单元格不清空原值**
        LandImportResultVO updated = importService.confirm(second, LandDuplicateStrategy.UPDATE, false);
        assertEquals(0, updated.getInsertedRows());
        assertEquals(1, updated.getUpdatedRows(), "应覆盖更新 1 条：" + describe(updated));
        Land after = landService.queryByCrzdbh(crzdbh);
        assertEquals("改名后地块", after.getDkmc(), "文件里有值的地块名称应被覆盖");
        assertEquals(0, new BigDecimal("2.00").compareTo(after.getCrj()), "出让金应被覆盖");
        // ★★ 文件里没填的列必须保持原值
        assertEquals("原始受让人", after.getSrr(),
                "★ 文件里没有「受让人」这一列的值，不能被清空");
    }

    @Test
    public void previewWithUpdateStrategyDoesNotWrite() throws Exception {
        String crzdbh = uniqueNo("preview-update");
        importService.confirm(buildTemplateFile(sheet -> {
            fillValidRow(sheet, 2, crzdbh, "原始名");
            setCell(sheet, 2, "srr", "原始受让人");
        }), LandDuplicateStrategy.REJECT, false);

        LandImportResultVO preview = importService.preview(
                buildTemplateFile(sheet -> fillValidRow(sheet, 2, crzdbh, "预览改名")),
                LandDuplicateStrategy.UPDATE);
        assertEquals(1, preview.getWillUpdateRows(), "应预告会覆盖 1 条");
        assertEquals(0, preview.getUpdatedRows(), "★ 预览绝不写库");
        assertEquals("原始名", landService.queryByCrzdbh(crzdbh).getDkmc(), "预览后库内应原样");
    }

    @Test
    public void confirmCanReimportAfterLogicalDelete() throws Exception {
        String crzdbh = uniqueNo("reimport");
        importService.confirm(buildTemplateFile(sheet -> fillValidRow(sheet, 2, crzdbh, "首次导入")),
                LandDuplicateStrategy.REJECT, false);
        Land first = landService.queryByCrzdbh(crzdbh);
        assertNotNull(first);

        // 逻辑删除（del_flag = 1）。t_land 的唯一键是 (crzdbh, del_flag)，
        // 因此「删除后重新录入同一编号」必须能成功 —— 否则用户会撞一个
        // 「提示编号已存在，但列表里又查不到」的死结。
        landService.removeById(first.getId());
        assertNull(landService.queryByCrzdbh(crzdbh), "逻辑删除后按编号应查不到");

        LandImportResultVO again = importService.confirm(
                buildTemplateFile(sheet -> fillValidRow(sheet, 2, crzdbh, "删除后重录")),
                LandDuplicateStrategy.REJECT, false);
        assertEquals(1, again.getInsertedRows(), "删除后应能重新录入同一编号：" + describe(again));
        Land second = landService.queryByCrzdbh(crzdbh);
        assertNotNull(second, "重新录入后应能查到");
        assertEquals("删除后重录", second.getDkmc());
    }

    // ==================================================================
    // 四、错误回执
    // ==================================================================

    @Test
    public void errorReportKeepsOriginalContentAndAddsReasonColumns() throws Exception {
        String badNo = uniqueNo("report");
        MockMultipartFile file = buildTemplateFile(sheet -> {
            fillValidRow(sheet, 2, badNo, "回执地块");
            setCell(sheet, 2, "lxdh", "1390000000");  // 10 位，格式错
            fillValidRow(sheet, 3, uniqueNo("report-ok"), "正常地块");
        });

        ParsedFile parsed = importService.parseOnly(file, LandDuplicateStrategy.REJECT);
        assertEquals(1, parsed.getResult().getErrorCount(), "应有 1 条错误：" + describe(parsed.getResult()));

        Workbook report = importService.buildErrorReport(parsed.getResult(), parsed.getSource());
        try {
            Sheet sheet = report.getSheetAt(0);
            Row header = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < header.getLastCellNum(); c++) {
                headers.add(header.getCell(c).getStringCellValue());
            }
            // 诊断三列必须在末尾
            assertEquals("__excel行号", headers.get(headers.size() - 3));
            assertEquals("__错误字段", headers.get(headers.size() - 2));
            assertEquals("__错误原因", headers.get(headers.size() - 1));
            // 前 N 列必须与模板一致（用户改完能直接重传）
            List<LandImportField.FieldSpec> fields = LandImportField.templated();
            for (int i = 0; i < fields.size(); i++) {
                assertEquals(fields.get(i).getColumn(), headers.get(i),
                        "回执第 " + (i + 1) + " 列应与模板一致");
            }

            Row errorRow = sheet.getRow(1);
            assertNotNull(errorRow, "回执数据区应有 1 行");
            // 原始内容要保留（用户填的东西）
            int crzdbhIndex = indexOf(headers, "crzdbh");
            int dkmcIndex = indexOf(headers, "dkmc");
            int lxdhIndex = indexOf(headers, "lxdh");
            assertEquals(badNo, errorRow.getCell(crzdbhIndex).getStringCellValue(),
                    "回执里必须保留用户填的宗地编号");
            assertEquals("回执地块", errorRow.getCell(dkmcIndex).getStringCellValue());
            assertEquals("1390000000", errorRow.getCell(lxdhIndex).getStringCellValue(),
                    "回执里必须保留用户填的原始手机号（好让他直接改）");
            // 诊断信息
            assertEquals(3, (int) errorRow.getCell(headers.size() - 3).getNumericCellValue(),
                    "错误行的 Excel 行号应为 3（1 基）");
            assertEquals("联系电话", errorRow.getCell(headers.size() - 2).getStringCellValue());
            assertTrue(errorRow.getCell(headers.size() - 1).getStringCellValue().contains("手机号"),
                    "错误原因应写明手机号格式问题");
            // 说明 sheet
            assertEquals(2, report.getNumberOfSheets(), "回执应有「错误回执」与「回执说明」两个 sheet");
        } finally {
            report.close();
        }
    }

    @Test
    public void rejectEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.xlsx",
                "application/octet-stream", new byte[0]);
        JeecgBootException error = assertThrows(JeecgBootException.class,
                () -> importService.preview(empty, LandDuplicateStrategy.REJECT));
        assertTrue(error.getMessage().contains("请选择"), "空文件提示：" + error.getMessage());
    }

    @Test
    public void rejectIllegalDuplicateStrategy() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> LandDuplicateStrategy.of("whatever"));
        assertTrue(error.getMessage().contains("不支持的重复处理策略"));
        assertEquals(LandDuplicateStrategy.REJECT, LandDuplicateStrategy.of(null),
                "不传策略时应默认「重复即报错」");
        assertEquals(LandDuplicateStrategy.UPDATE, LandDuplicateStrategy.of("UPDATE"));
        assertEquals(LandDuplicateStrategy.SKIP, LandDuplicateStrategy.of("skip"));
    }

    // ==================================================================
    // 五、字段字典自检
    // ==================================================================

    @Test
    public void fieldDictionaryIsConsistent() {
        // 必填列必须是模板列（必填但不在模板里 = 用户永远填不上，必然整批失败）
        for (LandImportField.FieldSpec spec : LandImportField.required()) {
            assertTrue(spec.isTemplated(),
                    "必填字段必须出现在模板里：" + spec.getColumn());
        }
        // 旧系统那 5 个必填项一个都不能少
        List<String> requiredColumns = new ArrayList<>();
        for (LandImportField.FieldSpec spec : LandImportField.required()) {
            requiredColumns.add(spec.getColumn());
        }
        assertEquals(Arrays.asList("crzdbh", "xmfl", "lrdw", "lrr", "lxdh"), requiredColumns,
                "必填列应与旧系统的 5 项一致（顺序即模板列顺序）");
        // 受控取值必须有别名表或非空选项，否则用户填什么都过不了
        for (LandImportField.FieldSpec spec : LandImportField.all()) {
            if (spec.getType() == LandImportField.FieldType.ENUM
                    || spec.getType() == LandImportField.FieldType.YES_NO) {
                assertFalse(spec.getOptions().isEmpty(),
                        spec.getColumn() + " 是受控取值列，必须有 options");
            }
        }
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /** 用「本系统模板」造一个上传文件 */
    private MockMultipartFile buildTemplateFile(Consumer<Sheet> filler) throws Exception {
        Workbook workbook = importService.buildTemplate();
        try {
            Sheet sheet = workbook.getSheetAt(0);
            filler.accept(sheet);
            return toFile(workbook, "批量导入.xlsx");
        } finally {
            workbook.close();
        }
    }

    private MockMultipartFile toFile(Workbook workbook, String fileName) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return new MockMultipartFile("file", fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    /** 按表头名写单元格（模板的第 2 行是中文说明，所以 rowIndex 从 2 开始填数据） */
    private void setCell(Sheet sheet, int rowIndex, String header, String value) {
        Integer column = headerIndex(sheet, header);
        assertNotNull(column, "模板里找不到列：" + header);
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        if (row.getCell(column) == null) {
            row.createCell(column);
        }
        row.getCell(column).setCellValue(value == null ? "" : value);
    }

    private Integer headerIndex(Sheet sheet, String header) {
        Row row = sheet.getRow(0);
        for (int c = 0; c < row.getLastCellNum(); c++) {
            if (header.equals(row.getCell(c).getStringCellValue())) {
                return c;
            }
        }
        return null;
    }

    private int indexOf(List<String> list, String value) {
        for (int i = 0; i < list.size(); i++) {
            if (value.equals(list.get(i))) {
                return i;
            }
        }
        throw new AssertionError("回执里找不到列：" + value);
    }

    /** 填一行「除 crzdbh/dkmc 外全部满足必填与格式」的数据（rowIndex 从 2 开始） */
    private void fillValidRow(Sheet sheet, int rowIndex, String crzdbh, String dkmc) {
        setCell(sheet, rowIndex, "crzdbh", crzdbh);
        setCell(sheet, rowIndex, "xmfl", "区级项目");
        setCell(sheet, rowIndex, "dkmc", dkmc);
        setCell(sheet, rowIndex, "lrdw", "测试录入单位");
        setCell(sheet, rowIndex, "lrr", "测试录入人");
        setCell(sheet, rowIndex, "lxdh", "13800000000");
    }

    /** 生成一个测试专用的、不与库内冲突的出让宗地编号 */
    private String uniqueNo(String tag) {
        return TEST_PREFIX + tag + "-" + System.nanoTime();
    }

    /** 把错误清单拼成一句可读文本，断言失败时能直接看出问题 */
    private String describe(LandImportResultVO result) {
        StringBuilder sb = new StringBuilder();
        sb.append("总行").append(result.getTotalRows())
                .append(" 有效").append(result.getValidRows())
                .append(" 新增").append(result.getInsertedRows())
                .append(" 覆盖").append(result.getUpdatedRows())
                .append(" 跳过重复").append(result.getSkippedDuplicates())
                .append(" 错误").append(result.getErrorCount());
        for (LandImportResultVO.RowError error : result.getErrors()) {
            sb.append(" [行").append(error.getRowNum()).append(' ')
                    .append(error.getColumnLabel()).append(' ').append(error.getMessage()).append(']');
        }
        for (String warning : result.getWarnings()) {
            sb.append(" [警告 ").append(warning).append(']');
        }
        for (String notice : result.getNotices()) {
            sb.append(" [提示 ").append(notice).append(']');
        }
        return sb.toString();
    }
}
