package org.jeecg.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 角色表 服务类
 * </p>
 *
 * @Author scott
 * @since 2018-12-19
 */
public interface ISysRoleService extends IService<SysRole> {

    /**
     * 导入 excel ，检查 roleCode 的唯一性
     *
     * @param file
     * @param params
     * @return
     * @throws Exception
     */
    Result importExcelCheckRoleCode(MultipartFile file, ImportParams params) throws Exception;

    /**
     * 删除角色
     * @param roleid
     * @return
     */
    public boolean deleteRole(String roleid);

    /**
     * 批量删除角色
     * @param roleids
     * @return
     */
    public boolean deleteBatchRole(String[] roleids);

    //update-begin---author:stargis ---date:20260101  for：角色同步至中台（ZK-SERVER）
    /**
     * 新增角色，并同步到中台（同事务：中台失败时整体回滚）。
     *
     * @param role 角色（id/角色编码/名称/描述）
     */
    void saveRoleWithZkSync(SysRole role);

    /**
     * 修改角色，并同步到中台（同事务：中台失败时整体回滚）。
     *
     * @param role 角色（至少要有 id）
     * @return 是否更新成功
     */
    boolean updateRoleWithZkSync(SysRole role);
    //update-end---author:stargis ---date:20260101  for：角色同步至中台（ZK-SERVER）

}
