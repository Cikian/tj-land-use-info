package org.jeecg.modules.land.data.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.modules.land.data.dto.LandAdminQueryDTO;
import org.jeecg.modules.land.data.dto.LandGeoOverviewVO;
import org.jeecg.modules.land.data.dto.LandSaveDTO;
import org.jeecg.modules.land.data.entity.Land;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * @Description: 数据管理 · 经营性用地信息录入（逐条）Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（三）第 1 项「经营性用地信息录入」。
 * 旧实现：{@code xjKjkfbCommercialLandController.createSpecial / updateItem /
 * getUniqueItem / commerialLandQuery}，前端 {@code addXjCommercialLand.html}。
 *
 * <p><b>★ 与 {@link ILandService} 的分工</b>：
 * {@code ILandService} 是「基础只读」（下拉、详情、首页看板），
 * 被档案/收发文等模块复用；本接口是「数据管理的写入面」，
 * 多了完整 34 字段落库、唯一性校验、变更留痕、软删/恢复。
 * 拆开是为了让「只读依赖」的模块不必感知写操作与留痕逻辑。
 */
public interface ILandAdminService {

    // ==================================================================
    // 查询
    // ==================================================================

    /** 分页查询（数据管理页的列表） */
    IPage<Land> queryPage(LandAdminQueryDTO query);

    /**
     * 铺图概览数据（<b>版本化增量</b>）。
     *
     * <p>前端带上次拿到的 {@code clientVersion}；后端比对当前版本（全表
     * MAX(update_time)，任何导入/编辑/移除/恢复都会推高它）：
     * <ul>
     *   <li>一致 → {@code unchanged=true}，不传宗地数据（几十字节）——
     *       进页面的加载量与「总共有多少宗」解耦，图形没变就永远是快的；</li>
     *   <li>不一致（首次 / 有变更）→ 全量概览列表（概览列预计算，总体 &lt;1MB）
     *       + 新版本号，前端替换本地缓存后渲染。</li>
     * </ul>
     * 精确边界由 {@link #queryGeoDetail(String)} 按需单宗拉取。
     *
     * @param clientVersion 前端持有的版本（首次为 null）
     */
    LandGeoOverviewVO queryGeoOverview(String clientVersion);

    /**
     * 写操作后的强制刷新：绕过客户端版本比对与服务端内存缓存，直接按当前数据库重建概览。
     *
     * @param clientVersion 前端持有的版本
     * @param forceRefresh 是否强制重建
     */
    LandGeoOverviewVO queryGeoOverview(String clientVersion, boolean forceRefresh);

    /**
     * 单宗地的地块图形（定位档：每环 ≤800 点，视觉与精确边界无差别）。
     *
     * <p>配合 {@link #queryGeoOverview(String)} 的两级加载：首屏铺概览档（40 点）秒开，
     * 双击定位某宗地时前端再按 id 拉这一宗的图形替换渲染。
     *
     * @param id 宗地主键
     * @return 仅 id / crzdbh / dkmc / dkGeojson 四列有值；宗地不存在或没有图形时返回 null
     */
    Land queryGeoDetail(String id);

    /** 详情（含配套项目数量、附件数量、变更次数等展示字段） */
    Land queryDetail(String id);

    /** 变更履历（倒序，默认 50 条） */
    List<Map<String, Object>> queryHistory(String id, Integer limit);

    // ==================================================================
    // 写入
    // ==================================================================

    /**
     * 新增。
     *
     * <p>沿用旧系统规则：{@code crzdbh}（出让宗地编号）必填且不可重复
     * （旧代码 581 行的唯一性校验）。旧系统还把这个校验做成了「不带 delFlag」，
     * 导致删除后不能重录 —— 本实现改为只查有效记录，允许删除后重录。
     *
     * @return 新记录主键
     */
    String createLand(LandSaveDTO dto, HttpServletRequest request);

    /** 编辑（字段级留痕：只记真正变化的字段） */
    void updateLand(LandSaveDTO dto, HttpServletRequest request);

    /**
     * 移除（软删）。
     *
     * <p>★ 不做物理删除：{@code t_land} 被配套项目、档案、收发文多处按
     * {@code crzdbh} 引用，物理删除会让这些引用变成孤儿。软删之后可在
     * 「数据更新与移除」页恢复。
     */
    void removeLand(String id, String reason, HttpServletRequest request);

    /** 批量移除（软删） */
    Map<String, Object> removeLandBatch(List<String> ids, String reason, HttpServletRequest request);

    // ==================================================================
    // 校验
    // ==================================================================

    /**
     * 出让宗地编号唯一性校验（表单实时校验用）。
     *
     * @param crzdbh 待校验编号
     * @param excludeId 编辑时排除自身的主键，可空
     * @return 含 {@code available} 与 {@code message} 的结果
     */
    Map<String, Object> checkCrzdbh(String crzdbh, String excludeId);

    /** 取全部有效宗地的编号 + 名称（表单下拉，支持关键字过滤） */
    List<Map<String, Object>> queryCodeOptions(String keyword, Integer limit);
}
