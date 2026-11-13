package org.jeecg.modules.land.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.vo.LandOptionVO;

import java.util.List;

/**
 * @Description: 经营性用地（宗地）Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface LandMapper extends BaseMapper<Land> {

    /**
     * 宗地下拉选项（远程搜索）。
     *
     * @param keyword 出让宗地编号 / 地块名称 模糊关键字，可空
     * @param xzqh    行政区划精确过滤，可空
     * @param limit   最大返回条数
     * @return 选项列表，附带该宗地下的配套项目数量
     */
    List<LandOptionVO> selectOptions(@Param("keyword") String keyword,
                                     @Param("xzqh") String xzqh,
                                     @Param("limit") int limit);

    /** 当前库中 t_land 的行数（用于启动自检与测试） */
    long countAll();

    /**
     * 数据版本：全表 MAX(update_time)。
     *
     * <p>任何写路径（导入 / 编辑 / 移除 / 恢复）都会刷新该行的 update_time，
     * 所以它单调不减、任何变更都会推高 —— 铺图概览接口拿它当「版本号」用：
     * 前端版本与它一致 = 图形没变，直接返回 304 式的「无变化」，不再传 0.7MB 数据。
     * t_land 只有几百~几千行，单列 MAX 扫描毫秒级。
     */
    java.util.Date selectMaxUpdateTime();

    /**
     * 按出让宗地编号取宗地。
     *
     * @param crzdbh 出让宗地编号
     * @return 宗地，可能为 null
     */
    Land selectByCrzdbh(@Param("crzdbh") String crzdbh);

    /** 显式软删并同步图形版本；@TableLogic 的 updateById 不会把 delFlag 当普通更新字段写出。 */
    @org.apache.ibatis.annotations.Update("UPDATE `t_land` "
            + "SET `del_flag` = 1, `update_by` = #{updateBy}, `update_time` = #{updateTime} "
            + "WHERE `id` = #{id} AND `del_flag` = 0")
    int softDeleteById(@Param("id") String id,
                       @Param("updateBy") String updateBy,
                       @Param("updateTime") java.util.Date updateTime);

    /**
     * 说明：首页宗地统计的 SQL 已迁到
     * {@code org.jeecg.modules.land.stat.home.mapper.HomeStatMapper}，
     * data 域不再保留同一口径的第二份实现。
     */

    /**
     * 批量更新地块图形（GeoJSON 导入专用）：一次往返写一批（调用方分批，每批 ≤50 条，
     * 多语句以分号分隔、依赖 JDBC URL 的 allowMultiQueries=true）。
     * 逐条 updateById 在远程库上是几千次往返，会把确认导入拖到超时。
     */
    void batchUpdateGeo(@Param("list") java.util.List<org.jeecg.modules.land.data.entity.Land> list);
}
