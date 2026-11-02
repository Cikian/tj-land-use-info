package org.jeecg.modules.land.stat.warning.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.stat.warning.service.IWarningLevelService;
import org.jeecg.modules.land.stat.warning.vo.WarningLevelVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Description: 统计预警 · 预警等级维护
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>管理端「统计预警 → 预警等级维护」（契约 §5.6）。三个接口：
 * <pre>
 *   列表 → GET    /land/stat/warning/level/list
 *   保存 → POST   /land/stat/warning/level/save     （id 空 = 新增）
 *   删除 → DELETE /land/stat/warning/level/delete?id=
 * </pre>
 *
 * <p><b>★ 权限只加在写接口上</b>：{@code save / delete} 挂
 * {@code land:stat:warningLevel:edit}；{@code list} 不加 ——
 * 查询类接口不加权限是本工程的一致口径（见 {@code WarningController} 类注释），
 * 而且管理端「决策意见维护」页也要读这份等级列表当下拉，
 * 挂上按钮权限会让只有意见维护权的角色看到空下拉。
 *
 * <p><b>★ 校验失败一律 {@code Result.error(中文msg)}，绝不抛 500</b>：
 * 管理端表单只认 {@code success === false} 并原样弹 {@code message}。
 * 抛异常时用户看到的是「系统异常」，而真正该看到的是
 * 「区间与等级「蓝色预警」的区间 [1, 10] 重叠」这种能直接改的提示。
 * 所有校验都在 Service 里（前端校验拦不住直接调接口），本控制器只负责把异常转成 Result。
 */
@Slf4j
@Api(tags = "统计预警-预警等级维护")
@RestController
@RequestMapping("/land/stat/warning/level")
public class WarningLevelController {

    @Autowired
    private IWarningLevelService warningLevelService;

    /**
     * 等级列表（按 sortNo 升序）。
     *
     * <p>{@code sort_no = 0}（前端留空时按 0 传）被视为「排最后」，
     * 见 {@code ProcessWarningLevelMapper#ORDER_BY_SORT}。
     */
    @AutoLog(value = "预警等级-列表")
    @ApiOperation(value = "预警等级列表", notes = "按 sortNo 升序；sortNo=0 视为排最后")
    @GetMapping(value = "/list")
    public Result<List<WarningLevelVO>> list() {
        try {
            return Result.OK(warningLevelService.list());
        } catch (Exception e) {
            return failure("查询预警等级失败", e);
        }
    }

    /**
     * 新增或编辑等级（id 为空 = 新增）。
     *
     * <p>校验：名称/别名非空、别名唯一（大小写不敏感）且只允许 {@code [a-z0-9_-]{2,20}}、
     * {@code 0 <= minDays <= maxDays}、编辑时 id 必须存在、
     * <b>区间不得与其它等级重叠</b>（重叠时给出冲突等级名）。
     */
    @AutoLog(value = "预警等级-保存")
    @ApiOperation(value = "保存预警等级", notes = "id 为空=新增；区间重叠/别名重复一律返回 Result.error")
    @RequiresPermissions("land:stat:warningLevel:edit")
    @PostMapping(value = "/save")
    public Result<String> save(@RequestBody WarningLevelVO vo) {
        try {
            return Result.OK("保存成功", warningLevelService.save(vo));
        } catch (Exception e) {
            return failure("保存预警等级失败", e);
        }
    }

    /** 删除等级（软删：等级 id 被预警行与决策意见引用着，物理删会产生孤儿数据） */
    @AutoLog(value = "预警等级-删除")
    @ApiOperation(value = "删除预警等级", notes = "软删；已被引用的历史数据保留")
    @RequiresPermissions("land:stat:warningLevel:edit")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id") String id) {
        try {
            warningLevelService.delete(id);
            return Result.OK("删除成功");
        } catch (Exception e) {
            return failure("删除预警等级失败", e);
        }
    }

    /** 统一错误出口（与 WarningController 同一写法） */
    private <T> Result<T> failure(String action, Exception e) {
        if (e instanceof JeecgBootException) {
            log.warn("{}：{}", action, e.getMessage());
            return Result.error(e.getMessage());
        }
        log.error(action, e);
        return Result.error(action + "：" + (e.getMessage() == null ? "内部错误" : e.getMessage()));
    }
}
