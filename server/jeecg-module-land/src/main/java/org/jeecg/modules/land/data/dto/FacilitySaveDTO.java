package org.jeecg.modules.land.data.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.modules.land.data.entity.Facility;

import java.io.Serializable;

/**
 * @Description: 配套地块数据录入 - 提交体
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 为什么继承 {@link Facility}</b>：配套表有 53 个业务字段。
 * 逐个抄进 DTO 再逐个抄回实体，是 106 行没有任何业务规则的重复代码，
 * 而新增一个字段就要改三处（实体 / DTO / 映射）。
 * 继承让 53 个字段自动可用，映射交给 {@code BeanUtils.copyProperties}。
 *
 * <p><b>★ 那怎么防越权写入</b>：Service 侧在 copy 之后，
 * <b>从库内旧记录把审计字段覆盖回来</b>（{@code delFlag} / {@code createTime} /
 * {@code createAccount}），并对 {@code id} 做显式校验。
 * 这样客户端在 JSON 里塞 {@code "delFlag":"0"} 想「复活」一条已删除的记录也不生效 ——
 * 因为 copy 之后又被旧值盖回去了。
 *
 * <p><b>★ 服务端强制的两个字段</b>：{@code crzdbh}（必须指向已有宗地）与
 * {@code ptxmmc}（配套项目名称，同一宗地下不可重复）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class FacilitySaveDTO extends Facility implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 移除原因（仅移除操作使用，不入库） */
    private String removeReason;
}
