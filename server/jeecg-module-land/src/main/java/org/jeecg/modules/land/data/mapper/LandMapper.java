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
     * 按出让宗地编号取宗地。
     *
     * @param crzdbh 出让宗地编号
     * @return 宗地，可能为 null
     */
    Land selectByCrzdbh(@Param("crzdbh") String crzdbh);
}
