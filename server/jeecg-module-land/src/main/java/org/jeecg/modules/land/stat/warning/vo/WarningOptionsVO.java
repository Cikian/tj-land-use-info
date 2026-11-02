package org.jeecg.modules.land.stat.warning.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 分级预警 · 下拉选项（合同 §5.3）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>大屏端筛选条与管理端「决策意见维护」的问题类型下拉共用这一个接口，
 * 省掉两次请求、也保证两个入口的选项永远一致（各查一次字典迟早会漂移）。
 *
 * <p><b>★ {@code problemTypes} 的来源是字典 {@code land_process_issue}，
 * 不是代码常量</b>：中心的分类口径会调整（例如新增「消防问题」），
 * 写死常量就要发版。字典查不到（新环境还没导字典）时回落到 5 项常量，
 * 保证下拉不空 —— 空下拉会让用户以为「不能选问题类型」。
 *
 * <p><b>★ {@code yjTypes} 用常量而不是查库</b>：三个分类是算法的分支定义（不是业务配置），
 * 库里没有分类字典；而 DISTINCT 查库会漏掉「当前一条总体预警都没有」时的分类，
 * 下拉里就少一项。
 */
@Data
public class WarningOptionsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预警等级（按 sortNo 升序，供筛选下拉与维护页） */
    private List<WarningLevelVO> levels = new ArrayList<>();

    /** 预警分类：环节预警 / 阶段预警 / 总体预警（顺序固定） */
    private List<String> yjTypes = new ArrayList<>();

    /** 问题类型（来自字典 land_process_issue，查不到时用内置 5 项） */
    private List<String> problemTypes = new ArrayList<>();
}
