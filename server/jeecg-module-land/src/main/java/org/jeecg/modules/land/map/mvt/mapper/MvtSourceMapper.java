package org.jeecg.modules.land.map.mvt.mapper;

import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.land.map.mvt.vo.MvtSource;
import java.util.List;

/**
 * ★ 只读本系统添加的 GIS 库清单（land_mvt_source）——中控是独立部署的
 * 第三方软件，其内部数据不做任何读取或写入，两套体系完全解耦。
 * 添加入口：spatial 域 SHP 上传入库（自动写入本表）。
 */
public interface MvtSourceMapper {

    @Select("SELECT id, code, COALESCE(NULLIF(name, code), code) AS name, dbName, dbUrl, "
            + "dbUsername, dbPassword, remark, createTime FROM ("
            + "SELECT id, code, COALESCE(NULLIF(name, code), code) AS name, db_name AS dbName, db_url AS dbUrl, "
            + "db_username AS dbUsername, db_password AS dbPassword, remark, "
            + "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime, create_time AS ord "
            + "FROM land_mvt_source "
            + "UNION ALL "
            + "SELECT DatasourceID_c AS id, Database_c AS code, Name_c AS name, Database_c AS dbName, "
            + "CONCAT('jdbc:postgresql://', Server_c, ':', Port_c, '/', Database_c) AS dbUrl, "
            + "User_c AS dbUsername, Pwd_c AS dbPassword, '中控数据源' AS remark, "
            + "DATE_FORMAT(creation_Time, '%Y-%m-%d %H:%i') AS createTime, creation_Time AS ord "
            + "FROM `tj-jyxyd-zk`.stargis_metadata "
            + "WHERE DatasourceType_c = 'PostgreSQL' "
            + "AND NOT EXISTS (SELECT 1 FROM land_mvt_source l WHERE l.code = Database_c) "
            + ") t ORDER BY ord DESC")
    List<MvtSource> listPostgres();
}
