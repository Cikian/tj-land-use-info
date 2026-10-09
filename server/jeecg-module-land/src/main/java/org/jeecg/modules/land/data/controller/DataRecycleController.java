package org.jeecg.modules.land.data.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.data.oplog.entity.DataChangeLog;
import org.jeecg.modules.land.data.service.IDataRecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @Description: 数据管理 · 数据更新与移除（软删 / 恢复 / 变更留痕）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（三）第 6 项。统一前缀 {@code /land/data/recycle}。
 *
 * <p><b>接口一览</b>：
 * <pre>
 *   GET  /land/data/recycle/list              回收站列表（宗地 + 配套，可筛类型/关键字）
 *   GET  /land/data/recycle/summary           回收站计数
 *   POST /land/data/recycle/restore           恢复一条
 *   POST /land/data/recycle/restoreBatch      批量恢复
 *   GET  /land/data/recycle/changeLog          变更留痕分页
 *   GET  /land/data/recycle/history            某条数据的完整履历
 *   GET  /land/data/recycle/actionDistribution 动作分布
 *   GET  /land/data/recycle/actionOptions      动作下拉
 * </pre>
 *
 * <p><b>★ 为什么这个方法只用一个权限码 {@code land:data:recycle}</b>：
 * 「移除」是录入页上的动作（那里已用 {@code land:data:land} / {@code land:data:facility} 把关），
 * 而本页是<b>回收站与留痕</b> —— 它既能恢复数据（等于一次写入），
 * 又能看到全部变更履历（含别人的操作）。
 * 这两件事的敏感度都高于普通查看，所以单独一个码，
 * 由管理员决定「谁可以恢复被删的数据、谁可以看操作留痕」。
 */
@Slf4j
@Api(tags = "数据管理-数据更新与移除")
@RestController
@RequestMapping("/land/data/recycle")
public class DataRecycleController {

    /** 本模块权限码（与 sql/data/08 脚本登记的按钮权限一致） */
    private static final String PERM = "land:data:recycle";

    @Autowired
    private IDataRecycleService recycleService;

    // ==================================================================
    // 一、回收站
    // ==================================================================

    @AutoLog(value = "数据更新与移除-回收站列表")
    @ApiOperation(value = "数据更新与移除-回收站列表",
            notes = "两张表软删列名不同（t_land.del_flag / 配套.delFlag），这里统一成同一结构返回")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(name = "bizType", required = false) String bizType,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return Result.OK(recycleService.queryRecycleList(bizType, keyword));
    }

    @AutoLog(value = "数据更新与移除-回收站计数")
    @ApiOperation(value = "数据更新与移除-回收站计数")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/summary")
    public Result<Map<String, Object>> summary() {
        return Result.OK(recycleService.queryRecycleSummary());
    }

    // ==================================================================
    // 二、恢复
    // ==================================================================

    @AutoLog(value = "数据更新与移除-恢复")
    @ApiOperation(value = "数据更新与移除-恢复",
            notes = "恢复前做唯一键冲突检测；宗地冲突会阻断并给出人话提示，配套同名只提示不阻断")
    @RequiresPermissions(PERM)
    @PostMapping(value = "/restore")
    public Result<Map<String, Object>> restore(@RequestParam(name = "bizType") String bizType,
                                               @RequestParam(name = "id") String id,
                                               HttpServletRequest request) {
        try {
            Map<String, Object> result = recycleService.restore(bizType, id, request);
            Object warning = result.get("warning");
            if (warning != null) {
                return Result.OK(String.valueOf(warning), result);
            }
            return Result.OK("已恢复", result);
        } catch (Exception e) {
            log.warn("恢复数据失败：bizType={}, id={}, 原因={}", bizType, id, e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @AutoLog(value = "数据更新与移除-批量恢复")
    @ApiOperation(value = "数据更新与移除-批量恢复", notes = "逐条恢复并返回失败明细")
    @RequiresPermissions(PERM)
    @PostMapping(value = "/restoreBatch")
    public Result<Map<String, Object>> restoreBatch(@RequestParam(name = "bizType") String bizType,
                                                    @RequestParam(name = "ids") String ids,
                                                    HttpServletRequest request) {
        List<String> idList = Arrays.asList(ids.split(","));
        Map<String, Object> result = recycleService.restoreBatch(bizType, idList, request);
        int failCount = result.get("failCount") == null ? 0 : (Integer) result.get("failCount");
        if (failCount > 0) {
            return Result.OK("部分恢复失败（详见返回明细）", result);
        }
        return Result.OK("恢复成功", result);
    }

    // ==================================================================
    // 三、变更留痕
    // ==================================================================

    @AutoLog(value = "数据更新与移除-变更留痕")
    @ApiOperation(value = "数据更新与移除-变更留痕",
            notes = "字段级：改了哪个字段、改前改后是什么、谁在什么时候改的")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/changeLog")
    public Result<Map<String, Object>> changeLog(
            @RequestParam(name = "bizType", required = false) String bizType,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "operator", required = false) String operator,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return Result.OK(recycleService.queryChangeLogPage(bizType, action, operator, keyword, pageNo, pageSize));
    }

    @AutoLog(value = "数据更新与移除-完整履历")
    @ApiOperation(value = "数据更新与移除-完整履历", notes = "某一条宗地/配套/环节的全部变更记录")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/history")
    public Result<List<DataChangeLog>> history(
            @RequestParam(name = "bizType") String bizType,
            @RequestParam(name = "bizId") String bizId,
            @RequestParam(name = "limit", required = false, defaultValue = "50") Integer limit) {
        return Result.OK(recycleService.queryHistory(bizType, bizId, limit));
    }

    @AutoLog(value = "数据更新与移除-动作分布")
    @ApiOperation(value = "数据更新与移除-动作分布")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/actionDistribution")
    public Result<List<Map<String, Object>>> actionDistribution(
            @RequestParam(name = "bizType", required = false) String bizType) {
        return Result.OK(recycleService.queryActionDistribution(bizType));
    }

    @AutoLog(value = "数据更新与移除-动作下拉")
    @ApiOperation(value = "数据更新与移除-动作下拉")
    @RequiresPermissions(PERM)
    @GetMapping(value = "/actionOptions")
    public Result<List<Map<String, String>>> actionOptions() {
        return Result.OK(recycleService.queryActionOptions());
    }
}
