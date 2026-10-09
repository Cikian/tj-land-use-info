package org.jeecg.modules.land.escalation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.escalation.entity.EscalationMaterial;

import java.util.List;

/**
 * @Description: 提级论证材料 Service（上传校验 / 版本与补充标记 / 路径安全解析）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 */
public interface IEscalationMaterialService extends IService<EscalationMaterial> {

    /** 某项目的材料列表（已补齐 url 与可读大小） */
    List<EscalationMaterial> queryByProjectId(String projectId);

    /** 单个材料（下载 / 预览 / 删除前查库用；只认 id） */
    EscalationMaterial queryMaterialById(String id);

    /**
     * 新增材料（扩展名白名单 + 50MB 上限 + 服务端补版本号）。
     *
     * @param projectId 项目ID
     * @param material  材料（storePath 必填，由 jeecg 通用上传接口返回）
     * @return 落库后的材料（含生成的 id 与版本号）
     */
    EscalationMaterial addMaterial(String projectId, EscalationMaterial material);

    /** 编辑材料（只允许改类型 / 补充标记 / 备注性字段，不动文件本身） */
    void updateMaterial(EscalationMaterial material);

    /** 删除材料（逻辑删除），并重算主表 material_count */
    void deleteMaterial(String id);

    /**
     * 项目编辑时的材料三向合并（带 id 的保留、不带 id 的新增、库里多出的删除）。
     *
     * <p>照抄档案模块 {@code ArchiveServiceImpl#syncFiles} 的思路：
     * 以<b>前端提交的列表为准</b>，避免前端漏传材料把已有材料全删掉。
     */
    void syncMaterials(String projectId, List<EscalationMaterial> submitted);

    /** 重算并回写主表 {@code material_count}（材料增删后调用） */
    void refreshMaterialCount(String projectId);

    /**
     * 解析材料的磁盘绝对路径（下载 / 预览用）。
     *
     * <p><b>安全约定</b>：只接受材料实体（其 {@code store_path} 来自数据库），
     * 出现 {@code ..} 或规范化后越出 {@code jeecg.path.upload} 一律返回 null。
     * 绝不接受前端传入的路径（旧系统 {@code /pdf/render?filePath=} 就是这样出的任意文件读取）。
     */
    String resolveAbsolutePath(EscalationMaterial material);

    /** 下载/预览用的相对 URL（store_path 去掉前导斜杠） */
    String buildUrl(EscalationMaterial material);
}
