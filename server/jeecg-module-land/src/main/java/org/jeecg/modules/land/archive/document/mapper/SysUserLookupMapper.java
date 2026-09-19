package org.jeecg.modules.land.archive.document.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * @Description: 系统用户只读查询（按账号取姓名）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p><b>为什么不注入 jeecg 的 {@code ISysBaseAPI}</b>：
 * 该接口在 {@code jeecg-system-local-api} 模块里，业务模块（jeecg-module-land）
 * 的编译期依赖里没有它；为了一个「按账号查姓名」去给业务模块加系统 API 依赖，
 * 收益远小于耦合成本。
 *
 * <p>因此这里用一个只读的轻量 Mapper 直接查 {@code sys_user}
 * （框架表，一定存在），语义与 {@code SysBaseApiImpl.queryUserByNames} 完全一致
 * （{@code status = 1 AND del_flag = 0}）。
 *
 * <p>注意：本 Mapper 位于 {@code org.jeecg.modules.**.mapper*} 之下，
 * 会被 {@code MybatisPlusSaasConfig} 的 {@code @MapperScan} 自动扫描到。
 */
public interface SysUserLookupMapper {

    /**
     * 按账号取用户姓名。
     *
     * @param username 账号
     * @return {username, realname}；账号不存在或已停用时返回 null
     */
    @Select("SELECT u.username AS username, u.realname AS realname "
            + "FROM sys_user u WHERE u.username = #{username} AND u.del_flag = 0 AND u.status = 1 LIMIT 1")
    Map<String, Object> selectByUsername(@Param("username") String username);
}
