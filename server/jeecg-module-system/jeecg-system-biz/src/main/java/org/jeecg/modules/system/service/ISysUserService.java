package org.jeecg.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.system.vo.SysUserCacheInfo;
import org.jeecg.modules.system.entity.SysRoleIndex;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.model.SysUserSysDepartModel;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @Author scott
 * @since 2018-12-20
 */
public interface ISysUserService extends IService<SysUser> {

	/**
	 * 重置密码
	 *
	 * @param username
	 * @param oldpassword
	 * @param newpassword
	 * @param confirmpassword
	 * @return
	 */
	public Result<?> resetPassword(String username, String oldpassword, String newpassword, String confirmpassword);

	/**
	 * 修改密码
	 *
	 * @param sysUser
	 * @return
	 */
	public Result<?> changePassword(SysUser sysUser);

	/**
	 * 删除用户
	 * @param userId
	 * @return
	 */
	public boolean deleteUser(String userId);

	/**
	 * 批量删除用户
	 * @param userIds
	 * @return
	 */
	public boolean deleteBatchUsers(String userIds);

    /**
     * 根据用户名查询
     * @param username 用户名
     * @return SysUser
     */
	public SysUser getUserByName(String username);
	
	/**
	 * 添加用户和用户角色关系
	 * @param user
	 * @param roles
	 */
	public void addUserWithRole(SysUser user,String roles);
	
	
	/**
	 * 修改用户和用户角色关系
	 * @param user
	 * @param roles
	 */
	public void editUserWithRole(SysUser user,String roles);

	/**
	 * 获取用户的授权角色
	 * @param username
	 * @return
	 */
	public List<String> getRole(String username);

	/**
	 * 获取根据登录用户的角色获取动态首页
	 *
	 * @param username
	 * @param version 前端UI版本
	 * @return
	 */
	public SysRoleIndex getDynamicIndexByUserRole(String username,String version);
	
	/**
	  * 查询用户信息包括 部门信息
	 * @param username
	 * @return
	 */
	@Deprecated
	public SysUserCacheInfo getCacheUser(String username);

	/**
	 * 根据部门Id查询
	 * @param page
     * @param departId 部门id
     * @param username 用户账户名称
	 * @return
	 */
	public IPage<SysUser> getUserByDepId(Page<SysUser> page, String departId, String username);

	/**
	 * 根据部门Ids查询
	 * @param page
     * @param departIds  部门id集合
     * @param username 用户账户名称
	 * @return
	 */
	public IPage<SysUser> getUserByDepIds(Page<SysUser> page, List<String> departIds, String username);

	/**
	 * 根据 userIds查询，查询用户所属部门的名称（多个部门名逗号隔开）
	 * @param userIds
	 * @return
	 */
	public Map<String,String> getDepNamesByUserIds(List<String> userIds);

    /**
     * 根据部门 Id 和 QueryWrapper 查询
     *
     * @param page
     * @param departId
     * @param queryWrapper
     * @return
     */
    //update-begin-author:taoyan date:2022-9-13 for: VUEN-2245【漏洞】发现新漏洞待处理20220906 ----sql注入 方法没有使用，注掉
    // public IPage<SysUser> getUserByDepartIdAndQueryWrapper(Page<SysUser> page, String departId, QueryWrapper<SysUser> queryWrapper);
	//update-end-author:taoyan date:2022-9-13 for: VUEN-2245【漏洞】发现新漏洞待处理20220906 ----sql注入 方法没有使用，注掉

	/**
	 * 根据 orgCode 查询用户，包括子部门下的用户
	 *
	 * @param orgCode
	 * @param userParams 用户查询条件，可为空
	 * @param page 分页参数
	 * @return
	 */
	IPage<SysUserSysDepartModel> queryUserByOrgCode(String orgCode, SysUser userParams, IPage page);

	/**
	 * 根据角色Id查询
	 * @param page
     * @param roleId 角色id
     * @param username 用户账户名称
	 * @return
	 */
	public IPage<SysUser> getUserByRoleId(Page<SysUser> page,String roleId, String username);

	/**
	 * 通过用户名获取用户角色集合
	 *
	 * @param username 用户名
	 * @return 角色集合
	 */
	Set<String> getUserRolesSet(String username);

	/**
	 * 通过用户名获取用户权限集合
	 *
	 * @param username 用户名
	 * @return 权限集合
	 */
	Set<String> getUserPermissionsSet(String username);
	
	/**
	 * 根据用户名设置部门ID
	 * @param username
	 * @param orgCode
	 */
	void updateUserDepart(String username,String orgCode);
	
	/**
	 * 根据手机号获取用户名和密码
     * @param phone 手机号
     * @return SysUser
	 */
	public SysUser getUserByPhone(String phone);


	/**
	 * 根据邮箱获取用户
     * @param email 邮箱
     * @return SysUser
     */
	public SysUser getUserByEmail(String email);


	/**
	 * 添加用户和用户部门关系
	 * @param user
	 * @param selectedParts
	 */
	void addUserWithDepart(SysUser user, String selectedParts);

	/**
	 * 编辑用户和用户部门关系
	 * @param user
	 * @param departs
	 */
	void editUserWithDepart(SysUser user, String departs);
	
	/**
	   * 校验用户是否有效
	 * @param sysUser
	 * @return
	 */
	Result checkUserIsEffective(SysUser sysUser);

	/**
	 * 查询被逻辑删除的用户
     * @return List<SysUser>
	 */
	List<SysUser> queryLogicDeleted();

	/**
	 * 查询被逻辑删除的用户（可拼装查询条件）
     * @param wrapper
     * @return List<SysUser>
	 */
	List<SysUser> queryLogicDeleted(LambdaQueryWrapper<SysUser> wrapper);

	/**
	 * 还原被逻辑删除的用户
     * @param userIds  存放用户id集合
     * @param updateEntity
     * @return boolean
	 */
	boolean revertLogicDeleted(List<String> userIds, SysUser updateEntity);

	/**
	 * 彻底删除被逻辑删除的用户
     * @param userIds 存放用户id集合
     * @return boolean
	 */
	boolean removeLogicDeleted(List<String> userIds);

    /**
     * 更新手机号、邮箱空字符串为 null
     * @return boolean
     */
    @Transactional(rollbackFor = Exception.class)
    boolean updateNullPhoneEmail();

	/**
	 * 保存第三方用户信息
	 * @param sysUser
	 */
	void saveThirdUser(SysUser sysUser);

	/**
	 * 根据部门Ids查询
	 * @param departIds 部门id集合
     * @param username 用户账户名称
	 * @return
	 */
	List<SysUser> queryByDepIds(List<String> departIds, String username);

	/**
	 * 保存用户
	 * @param user 用户
	 * @param selectedRoles 选择的角色id，多个以逗号隔开
	 * @param selectedDeparts 选择的部门id，多个以逗号隔开
	 */
	void saveUser(SysUser user, String selectedRoles, String selectedDeparts);

	/**
	 * 编辑用户
	 * @param user 用户
	 * @param roles 选择的角色id，多个以逗号隔开
	 * @param departs 选择的部门id，多个以逗号隔开
	 */
	void editUser(SysUser user, String roles, String departs);

	//update-begin---author:stargis ---date:20260101  for：用户/机构/角色关系同步至中台（ZK-SERVER）
	/**
	 * 新增用户，并同步到中台。
	 *
	 * <p>本系统按中台口径约束为「一个用户只能有一个机构、一个角色」：
	 * {@code selectedRoles} 与 {@code selectedDeparts} <b>必填且只能各有一个</b>，否则直接报错。
	 *
	 * <p>事务内先写本地（用户 + 角色关系 + 机构关系 + org_code），再调中台建号并置为已审批；
	 * 中台失败时（严格模式）整体回滚。
	 *
	 * @param user            用户
	 * @param selectedRoles   角色 id（必须且只能一个）
	 * @param selectedDeparts 机构 id（必须且只能一个）
	 * @param plainPassword   明文口令（中台建号必需，本地落库仍为哈希）
	 */
	void saveUserWithZkSync(SysUser user, String selectedRoles, String selectedDeparts, String plainPassword);

	/**
	 * 编辑用户，并同步到中台。
	 *
	 * <p>同样要求 {@code roles} 与 {@code departs} 必填且各只有一个。
	 * 注意：中台接口不支持修改用户所属机构，若本次改动涉及机构且该用户已映射到中台，会直接报错阻止。
	 */
	void editUserWithZkSync(SysUser user, String roles, String departs);

	/**
	 * 冻结/解冻用户（status 1 正常 / 2 冻结），并把状态同步到中台。
	 *
	 * @param userIds 用户 id 集合
	 * @param status  1 正常 / 2 冻结
	 */
	void updateUserStatusWithZkSync(List<String> userIds, Integer status);
	//update-end---author:stargis ---date:20260101  for：用户/机构/角色关系同步至中台（ZK-SERVER）

	/**
     * userId转为username
     * @param userIdList
     * @return List<String>
     */
	List<String> userIdToUsername(Collection<String> userIdList);


	/**
	 * 获取用户信息 字段信息是加密后的 【加密用户信息】
	 * @param username
	 * @return
	 */
	LoginUser getEncodeUserInfo(String username);

}
