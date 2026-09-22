package org.jeecg.modules.system.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.poi.ss.formula.functions.T;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.util.ImportExcelUtil;
import org.jeecg.common.util.PmsUtil;
import org.jeecg.modules.quartz.service.IQuartzJobService;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.mapper.SysRoleMapper;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.jeecg.modules.system.service.ISysRoleService;
//update-begin---author:stargis ---date:20260101  for：角色同步至中台（ZK-SERVER）
import org.jeecg.modules.system.zk.ZkRoleSyncService;
//update-end---author:stargis ---date:20260101  for：角色同步至中台（ZK-SERVER）
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 角色表 服务实现类
 * </p>
 *
 * @Author scott
 * @since 2018-12-19
 */
@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements ISysRoleService {
    @Autowired
    SysRoleMapper sysRoleMapper;
    @Autowired
    SysUserMapper sysUserMapper;
    //update-begin---author:stargis ---date:20260101  for：角色同步至中台（ZK-SERVER）
    @Autowired
    private ZkRoleSyncService zkRoleSyncService;
    //update-end---author:stargis ---date:20260101  for：角色同步至中台（ZK-SERVER）

    //update-begin---author:stargis ---date:20260101  for：角色同步至中台（ZK-SERVER）
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRoleWithZkSync(SysRole role) {
        this.save(role);
        // 中台失败时（严格模式）抛异常，本方法事务回滚，保证两边一致
        zkRoleSyncService.syncOnCreate(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRoleWithZkSync(SysRole role) {
        boolean ok = this.updateById(role);
        // 同步内部会按 id 重新加载完整记录，避免用前端只带部分字段的对象覆盖中台角色
        zkRoleSyncService.syncOnUpdate(role);
        return ok;
    }
    //update-end---author:stargis ---date:20260101  for：角色同步至中台（ZK-SERVER）

    @Override
    public Result importExcelCheckRoleCode(MultipartFile file, ImportParams params) throws Exception {
        List<Object> listSysRoles = ExcelImportUtil.importExcel(file.getInputStream(), SysRole.class, params);
        int totalCount = listSysRoles.size();
        List<String> errorStrs = new ArrayList<>();

        // 去除 listSysRoles 中重复的数据
        for (int i = 0; i < listSysRoles.size(); i++) {
            String roleCodeI =((SysRole)listSysRoles.get(i)).getRoleCode();
            for (int j = i + 1; j < listSysRoles.size(); j++) {
                String roleCodeJ =((SysRole)listSysRoles.get(j)).getRoleCode();
                // 发现重复数据
                if (roleCodeI.equals(roleCodeJ)) {
                    errorStrs.add("第 " + (j + 1) + " 行的 roleCode 值：" + roleCodeI + " 已存在，忽略导入");
                    listSysRoles.remove(j);
                    break;
                }
            }
        }
        // 去掉 sql 中的重复数据
        Integer errorLines=0;
        Integer successLines=0;
        List<String> list = ImportExcelUtil.importDateSave(listSysRoles, ISysRoleService.class, errorStrs, CommonConstant.SQL_INDEX_UNIQ_SYS_ROLE_CODE);
         errorLines+=list.size();
         successLines+=(listSysRoles.size()-errorLines);
        return ImportExcelUtil.imporReturnRes(errorLines,successLines,list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRole(String roleid) {
        //update-begin---author:stargis ---date:20260101  for：删除角色前先删除中台角色（ZK-SERVER）
        // 先删中台：中台失败（严格模式）则整体回滚，本地角色保留；若中台成功而本地失败，
        // 下次编辑该角色会按 role_code 重新匹配并复用/重建，可自愈
        zkRoleSyncService.syncOnDelete(Collections.singletonList(roleid));
        //update-end---author:stargis ---date:20260101  for：删除角色前先删除中台角色（ZK-SERVER）
        //1.删除角色和用户关系
        sysRoleMapper.deleteRoleUserRelation(roleid);
        //2.删除角色和权限关系
        sysRoleMapper.deleteRolePermissionRelation(roleid);
        //3.删除角色
        this.removeById(roleid);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatchRole(String[] roleIds) {
        //update-begin---author:stargis ---date:20260101  for：删除角色前先删除中台角色（ZK-SERVER）
        zkRoleSyncService.syncOnDelete(Arrays.asList(roleIds));
        //update-end---author:stargis ---date:20260101  for：删除角色前先删除中台角色（ZK-SERVER）
        //1.删除角色和用户关系
        sysUserMapper.deleteBathRoleUserRelation(roleIds);
        //2.删除角色和权限关系
        sysUserMapper.deleteBathRolePermissionRelation(roleIds);
        //3.删除角色
        this.removeByIds(Arrays.asList(roleIds));
        return true;
    }
}
