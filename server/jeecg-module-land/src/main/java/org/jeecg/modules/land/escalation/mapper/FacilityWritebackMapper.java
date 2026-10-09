package org.jeecg.modules.land.escalation.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * @Description: ★ 配套项目表回写窄接口（设计文档 2.4 的 L1 / L2 联动）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p><b>为什么单独建一个 Mapper 而不复用 data 模块的 FacilityMapper</b>：
 * <ol>
 *   <li>本模块对旧表<b>只有两个字段的写权限</b>
 *       （{@code sfzsjtjlz} / {@code tjlzsftg}），不该拿到整张表的通用 CRUD 入口；
 *       窄接口本身就把「能改什么」写死在代码里；</li>
 *   <li>顺带避免模块间 Service 依赖：本模块不注入 {@code IFacilityService}，
 *       回写失败也与 data 模块的加载顺序无关。</li>
 * </ol>
 *
 * <p>表名是 {@code xj_kjkfb_supporting_facilities}（<b>不是</b> {@code t_facility}）——
 * 档案模块落地时已确认配套表复用旧表名，清单第 7 章写的 {@code t_facility} 照抄会查不到表
 * （设计文档 3.2 第 1 条）。
 *
 * <p>回写受配置开关 {@code land.escalation.writeback-enabled} 控制（默认开），
 * 且<b>失败只记 warn 日志</b>，绝不影响主业务流程。
 */
public interface FacilityWritebackMapper {

    /**
     * 读当前值，用于「相同则不写」的判断。
     *
     * <p>★ 返回的 Map 里除 {@code sfzsjtjlz} / {@code tjlzsftg} 外**一定含 {@code id}**：
     * 这两列都为 NULL 时（首次回写的必然场景），MyBatis 对 resultType=Map 不会放入任何
     * 空值列，若只选这两列，整行会退化成 null，被误判成「项目不存在」。
     * 调用方用 {@code state == null} 判断存在性，因此 SQL 里必须带一个永不为 NULL 的列。
     *
     * @param facilityId 配套项目ID
     * @return 含 {@code id} / {@code sfzsjtjlz} / {@code tjlzsftg} 的行；项目不存在时返回 null
     */
    Map<String, Object> selectWritebackState(@Param("facilityId") String facilityId);

    /**
     * 只 UPDATE 两列。
     *
     * <p>两列都允许传 null：XML 里用 {@code <if test="xxx != null">} 拼 SQL，
     * 所以「只回写 L1」「只回写 L2」「两列一起回写」三种场景共用这一条语句。
     *
     * @param facilityId 配套项目ID
     * @param sfzsjtjlz  是否展示提级论证论证（字典 yn，中文值「是」）
     * @param tjlzsftg   提级论证是否通过（字典 yn，中文值「是」）
     * @return 受影响行数；没有任何列需要更新时返回 0
     */
    int updateWritebackFields(@Param("facilityId") String facilityId,
                             @Param("sfzsjtjlz") String sfzsjtjlz,
                             @Param("tjlzsftg") String tjlzsftg);
}
