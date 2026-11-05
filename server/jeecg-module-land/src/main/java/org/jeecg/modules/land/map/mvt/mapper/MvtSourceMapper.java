package org.jeecg.modules.land.map.mvt.mapper;

import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.land.map.mvt.vo.MvtSource;
import java.util.List;

/** 复用系统的多数据源配置，凭据仍由原连接池管理。 */
public interface MvtSourceMapper {
    /**
     * ★ 只读本系统添加的 GIS 库清单（land_mvt_source），不再全量扫描 sys_data_source。
     * 添加入口：spatial 域 SHP 上传入库（自动写入本表）。
     */
    /**
     * ★ 只读本系统清单（land_mvt_source）——中控是独立部署的第三方软件，
     * 其内部数据不做任何读取或写入，两套体系完全解耦。
     */
    List<MvtSource> listPostgres();
}
