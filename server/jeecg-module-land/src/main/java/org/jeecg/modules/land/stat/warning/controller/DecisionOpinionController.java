package org.jeecg.modules.land.stat.warning.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.stat.warning.service.IDecisionOpinionService;
import org.jeecg.modules.land.stat.warning.vo.DecisionOpinionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Description: 统计预警 · 决策意见维护
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>管理端「统计预警 → 决策意见维护」（契约 §5.7）。三个接口：
 * <pre>
 *   分页 → GET    /land/stat/warning/opinion/list?levelId=&amp;problemType=&amp;pageNo=&amp;pageSize=
 *   保存 → POST   /land/stat/warning/opinion/save    （id 空 = 新增）
 *   删除 → DELETE /land/stat/warning/opinion/delete?id=
 * </pre>
 *
 * <p><b>★★ {@code problemType} 的三种传参语义（最容易踩的地方）</b>：
 * <table border="1">
 *   <tr><th>前端传什么</th><th>语义</th><th>本接口怎么处理</th></tr>
 *   <tr><td>不传（参数缺失）</td><td>全部问题类型</td><td>不加过滤</td></tr>
 *   <tr><td>{@code problemType=}（空串）</td><td>只看「通用（所有问题类型）」</td><td>过滤 {@code problem_type = ''}</td></tr>
 *   <tr><td>{@code problemType=资金问题}</td><td>单个类型</td><td>精确匹配</td></tr>
 * </table>
 * 关键点：**必须按「参数是否为 null」判断，不能用 {@code StringUtils.isNotBlank} 过滤** ——
 * 后者会把空串也当成「没传」，于是「只看通用」退化成「全部」，
 * 页面上表现为「筛了通用却把具体类型也列出来了」。
 * 所以这里把 {@code problemType} <b>原样</b>传进 Service，不做 trim、不转 null。
 *
 * <p><b>★ 分页字段</b>：返回 {@code Result<IPage<DecisionOpinionVO>>}，
 * 前端按 jeecg 标准读 {@code result.records / result.total}，
 * 请求传 {@code pageNo / pageSize}（不自定义 list/count 双字段结构）。
 *
 * <p><b>★ 校验失败一律 {@code Result.error(中文msg)}</b>，与等级维护页同一口径。
 */
@Slf4j
@Api(tags = "统计预警-决策意见维护")
@RestController
@RequestMapping("/land/stat/warning/opinion")
public class DecisionOpinionController {

    @Autowired
    private IDecisionOpinionService decisionOpinionService;

    /**
     * 分页查询决策意见。
     *
     * @param levelId     等级ID（空 = 全部）
     * @param problemType 问题类型；<b>null = 全部，空串 = 只看通用</b>（见类注释的三种语义）
     * @param pageNo      页码，从 1 起
     * @param pageSize    每页条数（上限 200）
     */
    @AutoLog(value = "决策意见-分页查询")
    @ApiOperation(value = "决策意见分页", notes = "problemType 传空串=只看通用意见，不传=全部")
    @GetMapping(value = "/list")
    public Result<IPage<DecisionOpinionVO>> list(
            @RequestParam(name = "levelId", required = false) String levelId,
            @RequestParam(name = "problemType", required = false) String problemType,
            @RequestParam(name = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        try {
            return Result.OK(decisionOpinionService.list(levelId, problemType, pageNo, pageSize));
        } catch (Exception e) {
            return failure("查询决策意见失败", e);
        }
    }

    /**
     * 新增或编辑决策意见（id 为空 = 新增）。
     *
     * <p>校验：{@code levelId} 必须存在且未删；{@code opinion} 非空且 ≤ 1000 字；
     * 同一 {@code (problemType, levelId)} 只允许一条（重复报「已存在，请直接编辑」）。
     * {@code levelName} 由服务端按 levelId 回填，前端传了也会被覆盖。
     */
    @AutoLog(value = "决策意见-保存")
    @ApiOperation(value = "保存决策意见", notes = "id 为空=新增；problemType 空串=通用兜底意见")
    @RequiresPermissions("land:stat:decisionOpinion:edit")
    @PostMapping(value = "/save")
    public Result<String> save(@RequestBody DecisionOpinionVO vo) {
        try {
            return Result.OK("保存成功", decisionOpinionService.save(vo));
        } catch (Exception e) {
            return failure("保存决策意见失败", e);
        }
    }

    /** 删除决策意见（软删；已生成预警里的意见正文是快照，不受影响） */
    @AutoLog(value = "决策意见-删除")
    @ApiOperation(value = "删除决策意见", notes = "软删；历史预警里已填的意见快照不变")
    @RequiresPermissions("land:stat:decisionOpinion:edit")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id") String id) {
        try {
            decisionOpinionService.delete(id);
            return Result.OK("删除成功");
        } catch (Exception e) {
            return failure("删除决策意见失败", e);
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
