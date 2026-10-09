package org.jeecg.modules.land.archive.ledger.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.ledger.dto.LedgerQueryDTO;
import org.jeecg.modules.land.archive.ledger.dto.LedgerSaveDTO;
import org.jeecg.modules.land.archive.ledger.entity.RoadAcceptanceLedger;
import org.jeecg.modules.land.archive.ledger.enums.LedgerMaterial;
import org.jeecg.modules.land.archive.ledger.service.ILedgerExportService;
import org.jeecg.modules.land.archive.ledger.service.ILedgerImportService;
import org.jeecg.modules.land.archive.ledger.service.IRoadAcceptanceLedgerService;
import org.jeecg.modules.land.archive.ledger.support.LedgerSupport;
import org.jeecg.modules.land.archive.ledger.vo.LedgerImportResultVO;
import org.jeecg.modules.land.archive.ledger.vo.LedgerStatVO;
import org.jeecg.modules.land.archive.ledger.vo.RelatedArchiveVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description: 道路设施验收及移交资料台账（方案 2.3.2 第 7 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>统一前缀 {@code /land/archive/ledger}（与清单 §9.5 的接口约定一致，
 * 归在档案管理域下，因为方案 2.3.2 把本功能列在「档案管理」九项里）。
 *
 * <p><b>权限码</b>（全部登记为 sys_permission 里 menu_type=2 的「按钮权限」，
 * 授权脚本见 {@code sql/ledger/04_ledger_permission_buttons.sql}；
 * 按钮 id 前缀 {@code 1ed9e0a11ed9e0a11ed9e0a11ed9e1**}）：
 * <pre>
 *   land:ledger:list      列表 / 详情 / 统计 / 状态计数 / 13 类资料定义
 *   land:ledger:add       新增 / 生成台账编号 / 编号唯一校验
 *   land:ledger:edit      编辑 / 变更状态
 *   land:ledger:delete    删除 / 批量删除
 *   land:ledger:archive   关联档案 / 取消关联 / 挑档案
 *   land:ledger:export    导出 Excel
 *   land:ledger:import    Excel 批量补录（模板下载 / 预览校验 / 入库）
 * </pre>
 *
 * <p>接口清单（本类 20 个）：
 * <pre>
 *   GET    /land/archive/ledger/list            台账分页（台账页/查询共用）
 *   GET    /land/archive/ledger/queryById       详情（含 13 类资料统计 + 关联档案）
 *   GET    /land/archive/ledger/stat            汇总与 8 组统计
 *   GET    /land/archive/ledger/countByStatus   各状态计数（固定 4 项）
 *   GET    /land/archive/ledger/materials       ★13 类资料定义（前端表头用，与后端同源）
 *   POST   /land/archive/ledger/add             新增
 *   PUT    /land/archive/ledger/edit            编辑
 *   POST   /land/archive/ledger/status          变更状态
 *   DELETE /land/archive/ledger/delete          删除
 *   DELETE /land/archive/ledger/deleteBatch     批量删除
 *   GET    /land/archive/ledger/generateNo      预生成台账编号 YS-{yyyy}-{4位}
 *   GET    /land/archive/ledger/checkNo         台账编号唯一校验
 *   GET    /land/archive/ledger/relatedArchives 该台账的关联档案
 *   GET    /land/archive/ledger/archive/pick    挑档案（关联时用）
 *   POST   /land/archive/ledger/linkArchive     关联档案
 *   POST   /land/archive/ledger/unlinkArchive   取消关联
 *   GET    /land/archive/ledger/import/template ★下载批量补录模板
 *   POST   /land/archive/ledger/import/preview  ★批量补录预览（不写库）
 *   POST   /land/archive/ledger/import/confirm  ★批量补录入库
 *   GET    /land/archive/ledger/exportXls       导出台账 Excel
 * </pre>
 *
 * <p><b>资料原件不在这里上传</b>：按需求方口径，13 类资料的原件统一走档案管理模块
 * （{@code POST /sys/common/upload} + {@code /land/archive/**}），
 * 台账只通过 {@code linkArchive} 记一个指针。
 */
@Slf4j
@Api(tags = "档案管理-道路设施验收及移交资料台账")
@RestController
@RequestMapping("/land/archive/ledger")
public class RoadAcceptanceLedgerController {

    /** 权限码：查询（列表 / 详情 / 统计 / 状态计数 / 资料定义） */
    private static final String PERM_LIST = "land:ledger:list";
    /** 权限码：新增（含编号预览与校验） */
    private static final String PERM_ADD = "land:ledger:add";
    /** 权限码：编辑（含状态变更） */
    private static final String PERM_EDIT = "land:ledger:edit";
    /** 权限码：删除（含批量） */
    private static final String PERM_DELETE = "land:ledger:delete";
    /** 权限码：关联档案（含挑档案与取消关联） */
    private static final String PERM_ARCHIVE = "land:ledger:archive";
    /** 权限码：导出 */
    private static final String PERM_EXPORT = "land:ledger:export";
    /** 权限码：Excel 批量补录（模板下载 / 预览校验 / 入库） */
    private static final String PERM_IMPORT = "land:ledger:import";

    @Autowired
    private IRoadAcceptanceLedgerService ledgerService;

    @Autowired
    private ILedgerExportService exportService;

    @Autowired
    private ILedgerImportService importService;

    // ==================================================================
    // 查询
    // ==================================================================

    @AutoLog(value = "道路验收移交台账-分页列表查询")
    @ApiOperation(value = "道路验收移交台账-分页列表查询",
            notes = "13 列勾选矩阵 + 台账字段；与统计、导出共用同一套查询条件；分页上限 500")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/list")
    public Result<IPage<RoadAcceptanceLedger>> list(LedgerQueryDTO query) {
        return Result.OK(ledgerService.queryPage(query));
    }

    @AutoLog(value = "道路验收移交台账-通过id查询")
    @ApiOperation(value = "道路验收移交台账-通过id查询", notes = "返回台账 + 13 类资料统计 + 关联档案列表")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/queryById")
    public Result<RoadAcceptanceLedger> queryById(@RequestParam(name = "id", required = true) String id) {
        RoadAcceptanceLedger ledger = ledgerService.queryDetail(id);
        if (ledger == null) {
            return Result.error("未找到对应的台账记录");
        }
        return Result.OK(ledger);
    }

    @AutoLog(value = "道路验收移交台账-统计")
    @ApiOperation(value = "道路验收移交台账-统计",
            notes = "汇总指标（总数/迁移数/人工数/宗地待核对/资料齐全/资料为空）+ 按状态/行政区/功能区/验收类型/验收结果/年度/设施类别/13 类资料 共 8 组分类统计")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/stat")
    public Result<LedgerStatVO> stat(LedgerQueryDTO query) {
        return Result.OK(ledgerService.queryStat(query));
    }

    @AutoLog(value = "道路验收移交台账-各状态计数")
    @ApiOperation(value = "道路验收移交台账-各状态计数",
            notes = "固定返回未验收/验收中/已验收/已移交 4 项，数量为 0 也返回，前端 tab 角标直接用")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/countByStatus")
    public Result<List<LedgerStatVO.StatusCount>> countByStatus(LedgerQueryDTO query) {
        return Result.OK(ledgerService.countByStatus(query));
    }

    /**
     * 13 类资料定义。
     *
     * <p><b>为什么要有这个接口</b>：13 类资料在「数据库列 / 后端枚举 / 前端表头」三处出现，
     * 是最容易漂移的一处契约。前端从这里取表头（序号、属性名、中文名、迁移来源），
     * 本地列表只作为接口不可用时的兜底，这样「后端加一类资料」不会再出现
     * 「表头 13 列、数据 14 列」的错位。
     */
    @AutoLog(value = "道路验收移交台账-13类资料定义")
    @ApiOperation(value = "道路验收移交台账-13类资料定义", notes = "台账资料矩阵的表头来源（与后端 LedgerMaterial 同源）")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/materials")
    public Result<List<Map<String, Object>>> materials() {
        List<Map<String, Object>> list = new ArrayList<>(LedgerSupport.MATERIAL_TOTAL);
        for (LedgerMaterial material : LedgerMaterial.ordered()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", material.getSeq());
            item.put("key", material.getProperty());
            item.put("column", material.getColumn());
            item.put("label", material.getLabel());
            item.put("source", material.getSource());
            list.add(item);
        }
        return Result.OK(list);
    }

    // ==================================================================
    // 增删改
    // ==================================================================

    @AutoLog(value = "道路验收移交台账-新增")
    @ApiOperation(value = "道路验收移交台账-新增", notes = "台账编号可留空自动生成；13 类资料按 0/1 传值")
    @RequiresPermissions(PERM_ADD)
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody LedgerSaveDTO dto) {
        try {
            String id = ledgerService.createLedger(dto);
            return Result.OK("新增成功！", id);
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("新增道路验收移交台账失败", e);
            return Result.error("新增失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "道路验收移交台账-编辑")
    @ApiOperation(value = "道路验收移交台账-编辑",
            notes = "台账编号允许手工改写；字段允许清空（显式 SQL 覆盖，不受 MyBatis-Plus NOT_NULL 策略限制）")
    @RequiresPermissions(PERM_EDIT)
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> edit(@RequestBody LedgerSaveDTO dto) {
        try {
            ledgerService.updateLedger(dto);
            return Result.OK("修改成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("编辑道路验收移交台账失败", e);
            return Result.error("修改失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "道路验收移交台账-变更状态")
    @ApiOperation(value = "道路验收移交台账-变更状态", notes = "未验收/验收中/已验收/已移交（无流转顺序校验）")
    @RequiresPermissions(PERM_EDIT)
    @PostMapping(value = "/status")
    public Result<?> status(@RequestParam(name = "id") String id,
                            @RequestParam(name = "status") String status) {
        try {
            ledgerService.changeStatus(id, status);
            return Result.OK("状态变更成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("变更道路验收移交台账状态失败", e);
            return Result.error("状态变更失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "道路验收移交台账-删除")
    @ApiOperation(value = "道路验收移交台账-删除",
            notes = "逻辑删除；不动档案（资料原件归档案模块）、不动配套项目（底账属旧系统业务数据）")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
        try {
            ledgerService.deleteLedger(id);
            return Result.OK("删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除道路验收移交台账失败", e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "道路验收移交台账-批量删除")
    @ApiOperation(value = "道路验收移交台账-批量删除")
    @RequiresPermissions(PERM_DELETE)
    @DeleteMapping(value = "/deleteBatch")
    public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        try {
            List<String> idList = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            ledgerService.deleteLedgers(idList);
            return Result.OK("批量删除成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("批量删除道路验收移交台账失败", e);
            return Result.error("批量删除失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 台账编号
    // ==================================================================

    @AutoLog(value = "道路验收移交台账-生成台账编号")
    @ApiOperation(value = "道路验收移交台账-生成台账编号", notes = "仅预览，不落库；格式 YS-{yyyy}-{4位流水}")
    @RequiresPermissions(PERM_ADD)
    @GetMapping(value = "/generateNo")
    public Result<String> generateNo(@RequestParam(name = "year", required = false) Integer year) {
        return Result.OK(ledgerService.generateLedgerNo(year));
    }

    @AutoLog(value = "道路验收移交台账-台账编号唯一校验")
    @ApiOperation(value = "道路验收移交台账-台账编号唯一校验", notes = "编辑时传 id 排除自身")
    @RequiresPermissions(PERM_ADD)
    @GetMapping(value = "/checkNo")
    public Result<?> checkNo(@RequestParam(name = "ledgerNo", required = true) String ledgerNo,
                             @RequestParam(name = "id", required = false) String id) {
        try {
            ledgerService.checkLedgerNoUnique(ledgerNo, id);
            return Result.OK("台账编号可用");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        }
    }

    // ==================================================================
    // 关联档案
    // ==================================================================

    @AutoLog(value = "道路验收移交台账-关联档案列表")
    @ApiOperation(value = "道路验收移交台账-关联档案列表",
            notes = "按配套项目ID优先、出让宗地编号兜底反查 t_archive（只读，不改档案数据）")
    @RequiresPermissions(PERM_LIST)
    @GetMapping(value = "/relatedArchives")
    public Result<List<RelatedArchiveVO>> relatedArchives(@RequestParam(name = "id") String id,
                                                          @RequestParam(name = "limit", required = false) Integer limit) {
        return Result.OK(ledgerService.queryRelatedArchives(id, limit));
    }

    @AutoLog(value = "道路验收移交台账-挑档案")
    @ApiOperation(value = "道路验收移交台账-挑档案", notes = "按配套项目/宗地过滤，或按关键词全局搜（只读）")
    @RequiresPermissions(PERM_ARCHIVE)
    @GetMapping(value = "/archive/pick")
    public Result<List<RelatedArchiveVO>> pickArchive(@RequestParam(name = "facilityId", required = false) String facilityId,
                                                      @RequestParam(name = "crzdbh", required = false) String crzdbh,
                                                      @RequestParam(name = "keyword", required = false) String keyword,
                                                      @RequestParam(name = "limit", required = false) Integer limit) {
        return Result.OK(ledgerService.queryArchivesForPick(facilityId, crzdbh, keyword, limit));
    }

    @AutoLog(value = "道路验收移交台账-关联档案")
    @ApiOperation(value = "道路验收移交台账-关联档案", notes = "写台账自己的 archive_id 与 archive_count，不修改档案表")
    @RequiresPermissions(PERM_ARCHIVE)
    @PostMapping(value = "/linkArchive")
    public Result<?> linkArchive(@RequestParam(name = "id") String id,
                                 @RequestParam(name = "archiveId") String archiveId) {
        try {
            ledgerService.linkArchive(id, archiveId);
            return Result.OK("关联成功！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("台账关联档案失败", e);
            return Result.error("关联失败：" + e.getMessage());
        }
    }

    @AutoLog(value = "道路验收移交台账-取消关联档案")
    @ApiOperation(value = "道路验收移交台账-取消关联档案", notes = "只清台账的 archive_id 指针，不删除档案本身")
    @RequiresPermissions(PERM_ARCHIVE)
    @PostMapping(value = "/unlinkArchive")
    public Result<?> unlinkArchive(@RequestParam(name = "id") String id) {
        try {
            ledgerService.unlinkArchive(id);
            return Result.OK("已取消关联！");
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("台账取消关联档案失败", e);
            return Result.error("取消关联失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // Excel 批量补录（模块 C）
    // ==================================================================

    /**
     * 下载补录模板。
     *
     * <p>模板第 1 个 sheet 是补录表（表头 + 200 行带边框的空行），
     * 第 2 个 sheet 是「填表说明」（匹配键、空单元格语义、13 类资料的取值与来源、状态取值）。
     */
    @AutoLog(value = "道路验收移交台账-下载批量补录模板")
    @ApiOperation(value = "道路验收移交台账-下载批量补录模板",
            notes = "文件名：道路验收移交资料台账_批量补录模板.xlsx；含「填表说明」sheet")
    @RequiresPermissions(PERM_IMPORT)
    @GetMapping(value = "/import/template")
    public void importTemplate(HttpServletResponse response) {
        Workbook workbook = null;
        try {
            workbook = importService.buildTemplate();
            String fileName = "道路验收移交资料台账_批量补录模板.xlsx";
            String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            response.reset();
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
                out.flush();
            }
        } catch (Exception e) {
            log.error("下载台账补录模板失败", e);
            writeJsonError(response, "下载模板失败：" + e.getMessage());
        } finally {
            closeQuietly(workbook);
        }
    }

    /**
     * 批量补录 - 预览校验（★ 不写库）。
     *
     * <p>返回「会更新多少条 / 命中多少条 / 错在哪几行（Excel 行号 + 台账编号 + 原因）」，
     * 用户确认后再调 {@code /import/confirm}。
     */
    @AutoLog(value = "道路验收移交台账-批量补录预览")
    @ApiOperation(value = "道路验收移交台账-批量补录预览",
            notes = "解析并校验上传的 Excel，不写库；返回更新行数预览与错误/警告清单")
    @RequiresPermissions(PERM_IMPORT)
    @PostMapping(value = "/import/preview")
    public Result<LedgerImportResultVO> importPreview(@RequestParam("file") MultipartFile file) {
        try {
            return Result.OK(importService.preview(file));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("台账批量补录预览失败", e);
            return Result.error("预览失败：" + e.getMessage());
        }
    }

    /**
     * 批量补录 - 确认入库。
     *
     * @param skipErrorRows true = 跳过有错误的行只导入正确的行；
     *                      false（默认）= 只要有一行错误就整批不入库，只回执错误清单
     */
    @AutoLog(value = "道路验收移交台账-批量补录入库")
    @ApiOperation(value = "道路验收移交台账-批量补录入库",
            notes = "匹配键为台账编号；空单元格表示「不修改」；默认有错整批不入库")
    @RequiresPermissions(PERM_IMPORT)
    @PostMapping(value = "/import/confirm")
    public Result<LedgerImportResultVO> importConfirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "skipErrorRows", required = false, defaultValue = "false") boolean skipErrorRows) {
        try {
            return Result.OK(importService.confirm(file, skipErrorRows));
        } catch (JeecgBootException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("台账批量补录入库失败", e);
            return Result.error("导入失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 导出
    // ==================================================================

    /**
     * 导出台账。
     *
     * <p>与台账列表<b>共用同一套查询条件</b>，所以导出的行就是屏幕上的行；
     * 文件里的「序号」列由 Service 侧从 1 连续编号。
     *
     * <p>实现方式照抄档案模块：easypoi 生成 .xlsx 工作簿再写响应流。
     * 必须显式 {@code ExcelType.XSSF}（在 {@code LedgerExportServiceImpl} 里设置），
     * 否则写出的是 .xls 内容配 .xlsx 文件名，Excel 会报「文件格式或扩展名无效」。
     */
    @AutoLog(value = "道路验收移交台账-导出Excel")
    @ApiOperation(value = "道路验收移交台账-导出Excel",
            notes = "文件名：道路设施验收及移交资料台账_{yyyyMMddHHmm}.xlsx；列 = 台账列 + 13 类资料列 + 状态")
    @RequiresPermissions(PERM_EXPORT)
    @GetMapping(value = "/exportXls")
    public void exportXls(LedgerQueryDTO query, HttpServletResponse response) {
        Workbook workbook = null;
        try {
            workbook = exportService.exportLedger(query);
            String fileName = "道路设施验收及移交资料台账_"
                    + new SimpleDateFormat("yyyyMMddHHmm").format(new Date()) + ".xlsx";
            String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");

            response.reset();
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
                out.flush();
            }
        } catch (Exception e) {
            log.error("导出台账失败", e);
            writeJsonError(response, "导出失败：" + e.getMessage());
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (Exception e) {
                    log.warn("关闭导出工作簿失败：{}", e.getMessage());
                }
            }
        }
    }

    /** 关闭工作簿且不让关闭异常盖住真实错误 */
    private void closeQuietly(Workbook workbook) {
        if (workbook != null) {
            try {
                workbook.close();
            } catch (Exception e) {
                log.warn("关闭工作簿失败：{}", e.getMessage());
            }
        }
    }

    /** 已提交响应时不能再写错误信息，避免 IllegalStateException 掩盖真实异常 */
    private void writeJsonError(HttpServletResponse response, String message) {        if (response.isCommitted()) {
            return;
        }
        try {
            response.reset();
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\""
                    + (message == null ? "未知错误" : message.replace("\"", "'")) + "\"}");
        } catch (Exception e) {
            log.warn("回写错误信息失败：{}", e.getMessage());
        }
    }
}
