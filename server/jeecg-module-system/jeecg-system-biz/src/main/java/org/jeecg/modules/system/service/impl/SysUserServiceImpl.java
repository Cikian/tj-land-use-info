package org.jeecg.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
//update-begin---author:stargis ---date:20260101  for：冻结/解冻用户同步中台（ZK-SERVER）
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
//update-end---author:stargis ---date:20260101  for：冻结/解冻用户同步中台（ZK-SERVER）
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.enums.RoleIndexConfigEnum;
import org.jeecg.common.desensitization.annotation.SensitiveEncode;
//update-begin---author:stargis ---date:20260101  for：单机构单角色校验失败时向前端透出原因（ZK-SERVER）
import org.jeecg.common.exception.JeecgBootException;
//update-end---author:stargis ---date:20260101  for：单机构单角色校验失败时向前端透出原因（ZK-SERVER）
//update-begin---author:stargis ---date:20260101  for：密码强度与中台同一策略（ZK-SERVER）
import com.stargis.zk.sdk.common.ZkPasswordPolicy;
//update-end---author:stargis ---date:20260101  for：密码强度与中台同一策略（ZK-SERVER）
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.system.vo.SysUserCacheInfo;
import org.jeecg.common.util.PasswordUtil;
import org.jeecg.common.util.UUIDGenerator;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.base.service.BaseCommonService;
import org.jeecg.modules.system.entity.*;
import org.jeecg.modules.system.mapper.*;
import org.jeecg.modules.system.model.SysUserSysDepartModel;
import org.jeecg.modules.system.service.ISysUserService;
//update-begin---author:stargis ---date:20260101  for：用户/机构/角色关系同步至中台（ZK-SERVER）
import org.jeecg.modules.system.zk.ZkUserSyncService;
//update-end---author:stargis ---date:20260101  for：用户/机构/角色关系同步至中台（ZK-SERVER）
import org.jeecg.modules.system.vo.SysUserDepVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @Author: scott
 * @Date: 2018-12-20
 */
@Service
@Slf4j
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {
	
	@Autowired
	private SysUserMapper userMapper;
	@Autowired
	private SysPermissionMapper sysPermissionMapper;
	@Autowired
	private SysUserRoleMapper sysUserRoleMapper;
	@Autowired
	private SysUserDepartMapper sysUserDepartMapper;
	@Autowired
	private SysDepartMapper sysDepartMapper;
	@Autowired
	private SysRoleMapper sysRoleMapper;
	@Autowired
	private SysDepartRoleUserMapper departRoleUserMapper;
	@Autowired
	private SysDepartRoleMapper sysDepartRoleMapper;
	@Resource
	private BaseCommonService baseCommonService;
	@Autowired
	private SysThirdAccountMapper sysThirdAccountMapper;
	@Autowired
	ThirdAppWechatEnterpriseServiceImpl wechatEnterpriseService;
	@Autowired
	ThirdAppDingtalkServiceImpl dingtalkService;
	@Autowired
	SysRoleIndexMapper sysRoleIndexMapper;
	//update-begin---author:stargis ---date:20260101  for：用户/机构/角色关系同步至中台（ZK-SERVER）
	@Autowired
	private ZkUserSyncService zkUserSyncService;
	//update-end---author:stargis ---date:20260101  for：用户/机构/角色关系同步至中台（ZK-SERVER）

	//update-begin---author:stargis ---date:20260101  for：用户/机构/角色关系同步至中台（ZK-SERVER）
	/**
	 * 新增用户 + 单机构单角色校验 + 中台同步（同一事务）。
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	@CacheEvict(value = {CacheConstant.SYS_USERS_CACHE}, allEntries = true)
	public void saveUserWithZkSync(SysUser user, String selectedRoles, String selectedDeparts, String plainPassword) {
		// 0. 密码强度校验（与中台同一份策略）：本地先拦，避免"本地建成功、中台 20003 失败"
		checkPasswordPolicy(plainPassword);
		// 1. 口径校验：必须且只能一个机构、一个角色（对应中台的逻辑）
		checkSingleRelation(selectedRoles, selectedDeparts);
		// 2. 本地写入（用户 + 角色关系 + 机构关系）
		this.saveUser(user, selectedRoles, selectedDeparts);
		// 3. 同步 org_code，保证 jeecg 自身的数据权限/登录部门与所选机构一致
		syncOrgCode(user, selectedDeparts);
		// 4. 同步中台（失败在严格模式下抛异常 → 上面所有本地写入一起回滚）
		zkUserSyncService.syncOnCreate(user, plainPassword);
	}

	/**
	 * 编辑用户 + 单机构单角色校验 + 中台同步（同一事务）。
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	@CacheEvict(value = {CacheConstant.SYS_USERS_CACHE}, allEntries = true)
	public void editUserWithZkSync(SysUser user, String roles, String departs) {
		checkSingleRelation(roles, departs);
		this.editUser(user, roles, departs);
		syncOrgCode(user, departs);
		zkUserSyncService.syncOnUpdate(user);
	}

	/**
	 * 冻结/解冻用户 + 中台状态同步（同一事务）。
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	@CacheEvict(value = {CacheConstant.SYS_USERS_CACHE}, allEntries = true)
	public void updateUserStatusWithZkSync(List<String> userIds, Integer status) {
		for (String id : userIds) {
			if (oConvertUtils.isEmpty(id)) {
				continue;
			}
			this.update(new SysUser().setStatus(status),
					new UpdateWrapper<SysUser>().lambda().eq(SysUser::getId, id));
		}
		zkUserSyncService.syncOnStatusChange(userIds);
	}

	/**
	 * 密码强度校验：<b>与中台使用同一份策略</b>（{@link ZkPasswordPolicy}，取自中台
	 * {@code ZKStringUtils.checkPassword} 的原文正则）。
	 *
	 * <p>为什么必须在本地先拦：jeecg 原本对密码强度没有硬约束（前端规则也偏松），
	 * 会出现"本系统改密成功、中台同步失败（code=20003）"，导致两边口令不一致——
	 * 用户以为改了密码，中台侧还是旧口令。
	 *
	 * <p>规则：<b>8-20 位</b>，且同时包含<b>小写字母、大写字母、数字、特殊字符</b>；
	 * 特殊字符是中台的白名单 {@code $ @ # ! % ^ * ? & + -}（下划线 {@code _}、点 {@code .}、
	 * 波浪 {@code ~}、空格、中文都不允许）。
	 */
	private void checkPasswordPolicy(String plainPassword) {
		if (oConvertUtils.isEmpty(plainPassword)) {
			throw new JeecgBootException("密码不能为空");
		}
		String err = ZkPasswordPolicy.validate(plainPassword);
		if (err != null) {
			throw new JeecgBootException(err);
		}
	}

	/**
	 * 单机构单角色校验：本系统按中台口径限制，一个用户只能有一个机构和一个角色。
	 */
	private void checkSingleRelation(String roles, String departs) {
		if (oConvertUtils.isEmpty(departs)) {
			throw new JeecgBootException("必须选择所属机构：本系统与中台一致，一个用户只能有一个机构");
		}
		if (departs.split(",").length > 1) {
			throw new JeecgBootException("只能选择一个所属机构：本系统与中台一致，一个用户只能有一个机构");
		}
		if (oConvertUtils.isEmpty(roles)) {
			throw new JeecgBootException("必须选择角色：本系统与中台一致，一个用户只能有一个角色");
		}
		if (roles.split(",").length > 1) {
			throw new JeecgBootException("只能选择一个角色：本系统与中台一致，一个用户只能有一个角色");
		}
	}

	/**
	 * 把所选机构的 org_code 写到用户上。
	 *
	 * <p>jeecg 的用户-机构关系其实有两处：{@code sys_user_depart}（多对多）与
	 * {@code sys_user.org_code}（登录/数据权限用的当前部门）。既然已限制为单机构，
	 * 这里让两者保持一致，避免出现"关系表是 A 部门、org_code 还是旧部门"的错乱。
	 */
	private void syncOrgCode(SysUser user, String departs) {
		if (oConvertUtils.isEmpty(departs) || user == null || oConvertUtils.isEmpty(user.getId())) {
			return;
		}
		SysDepart depart = sysDepartMapper.selectById(departs.split(",")[0]);
		if (depart == null || oConvertUtils.isEmpty(depart.getOrgCode())) {
			return;
		}
		if (!depart.getOrgCode().equals(user.getOrgCode())) {
			userMapper.update(new SysUser().setOrgCode(depart.getOrgCode()),
					new UpdateWrapper<SysUser>().lambda().eq(SysUser::getId, user.getId()));
			user.setOrgCode(depart.getOrgCode());
		}
	}
	//update-end---author:stargis ---date:20260101  for：用户/机构/角色关系同步至中台（ZK-SERVER）

    @Override
    @CacheEvict(value = {CacheConstant.SYS_USERS_CACHE}, allEntries = true)
    //update-begin---author:stargis ---date:20260101  for：改密与中台同步必须同事务（ZK-SERVER）
    @Transactional(rollbackFor = Exception.class)
    //update-end---author:stargis ---date:20260101  for：改密与中台同步必须同事务（ZK-SERVER）
    public Result<?> resetPassword(String username, String oldpassword, String newpassword, String confirmpassword) {
        SysUser user = userMapper.getUserByName(username);
        String passwordEncode = PasswordUtil.encrypt(username, oldpassword, user.getSalt());
        if (!user.getPassword().equals(passwordEncode)) {
            return Result.error("旧密码输入错误!");
        }
        if (oConvertUtils.isEmpty(newpassword)) {
            return Result.error("新密码不允许为空!");
        }
        if (!newpassword.equals(confirmpassword)) {
            return Result.error("两次输入密码不一致!");
        }
        //update-begin---author:stargis ---date:20260101  for：密码强度与中台同一策略（ZK-SERVER）
        // 本地先按中台策略校验：不通过就直接拒绝，避免"本系统改成功、中台 20003 失败"造成两边口令不一致
        String policyErr = ZkPasswordPolicy.validate(newpassword);
        if (policyErr != null) {
            return Result.error(policyErr);
        }
        //update-end---author:stargis ---date:20260101  for：密码强度与中台同一策略（ZK-SERVER）
        String password = PasswordUtil.encrypt(username, newpassword, user.getSalt());
        this.userMapper.update(new SysUser().setPassword(password), new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, user.getId()));
        //update-begin---author:stargis ---date:20260101  for：改密时同步中台口令（ZK-SERVER）
        // 中台口令必须与本地一致；若该用户还没映射到中台，会借这次明文顺势建号
        zkUserSyncService.syncOnPasswordChange(user.getId(), newpassword);
        //update-end---author:stargis ---date:20260101  for：改密时同步中台口令（ZK-SERVER）
        return Result.ok("密码重置成功!");
    }

    @Override
    @CacheEvict(value = {CacheConstant.SYS_USERS_CACHE}, allEntries = true)
    //update-begin---author:stargis ---date:20260101  for：改密与中台同步必须同事务（ZK-SERVER）
    @Transactional(rollbackFor = Exception.class)
    //update-end---author:stargis ---date:20260101  for：改密与中台同步必须同事务（ZK-SERVER）
    public Result<?> changePassword(SysUser sysUser) {
        String salt = oConvertUtils.randomGen(8);
        sysUser.setSalt(salt);
        String password = sysUser.getPassword();
        //update-begin---author:stargis ---date:20260101  for：密码强度与中台同一策略（ZK-SERVER）
        String policyErr = ZkPasswordPolicy.validate(password);
        if (policyErr != null) {
            return Result.error(policyErr);
        }
        //update-end---author:stargis ---date:20260101  for：密码强度与中台同一策略（ZK-SERVER）
        String passwordEncode = PasswordUtil.encrypt(sysUser.getUsername(), password, salt);
        sysUser.setPassword(passwordEncode);
        this.userMapper.updateById(sysUser);
        //update-begin---author:stargis ---date:20260101  for：改密时同步中台口令（ZK-SERVER）
        zkUserSyncService.syncOnPasswordChange(sysUser.getId(), password);
        //update-end---author:stargis ---date:20260101  for：改密时同步中台口令（ZK-SERVER）
        return Result.ok("密码修改成功!");
    }

    @Override
    @CacheEvict(value={CacheConstant.SYS_USERS_CACHE}, allEntries=true)
	@Transactional(rollbackFor = Exception.class)
	public boolean deleteUser(String userId) {
		//update-begin---author:stargis ---date:20260101  for：删除用户时同步中台（ZK-SERVER）
		// 先同步中台：中台失败（严格模式）则整体回滚，本地用户保留
		zkUserSyncService.syncOnDelete(Collections.singletonList(userId));
		//update-end---author:stargis ---date:20260101  for：删除用户时同步中台（ZK-SERVER）
		//1.删除用户
		this.removeById(userId);
		return false;
	}

	@Override
    @CacheEvict(value={CacheConstant.SYS_USERS_CACHE}, allEntries=true)
	@Transactional(rollbackFor = Exception.class)
	public boolean deleteBatchUsers(String userIds) {
		//update-begin---author:stargis ---date:20260101  for：删除用户时同步中台（ZK-SERVER）
		zkUserSyncService.syncOnDelete(Arrays.asList(userIds.split(",")));
		//update-end---author:stargis ---date:20260101  for：删除用户时同步中台（ZK-SERVER）
		//1.删除用户
		this.removeByIds(Arrays.asList(userIds.split(",")));
		return false;
	}

	@Override
	public SysUser getUserByName(String username) {
		return userMapper.getUserByName(username);
	}
	
	
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void addUserWithRole(SysUser user, String roles) {
		//update-begin---author:stargis ---date:20260101  for：注册建号也按中台策略校验密码（ZK-SERVER）
		// 注意：注册路径不经过中台建号（没有机构/角色），因此这里只做强度校验，保证"全系统一套密码规则"
		if (oConvertUtils.isNotEmpty(user.getPassword()) && !user.getPassword().startsWith("$")) {
			checkPasswordPolicy(user.getPassword());
		}
		//update-end---author:stargis ---date:20260101  for：注册建号也按中台策略校验密码（ZK-SERVER）
		this.save(user);
		if(oConvertUtils.isNotEmpty(roles)) {
			String[] arr = roles.split(",");
			for (String roleId : arr) {
				SysUserRole userRole = new SysUserRole(user.getId(), roleId);
				sysUserRoleMapper.insert(userRole);
			}
		}
	}

	@Override
	@CacheEvict(value= {CacheConstant.SYS_USERS_CACHE}, allEntries=true)
	@Transactional(rollbackFor = Exception.class)
	public void editUserWithRole(SysUser user, String roles) {
		this.updateById(user);
		//先删后加
		sysUserRoleMapper.delete(new QueryWrapper<SysUserRole>().lambda().eq(SysUserRole::getUserId, user.getId()));
		if(oConvertUtils.isNotEmpty(roles)) {
			String[] arr = roles.split(",");
			for (String roleId : arr) {
				SysUserRole userRole = new SysUserRole(user.getId(), roleId);
				sysUserRoleMapper.insert(userRole);
			}
		}
	}


	@Override
	public List<String> getRole(String username) {
		return sysUserRoleMapper.getRoleByUserName(username);
	}

	/**
	 * 获取动态首页路由配置
	 * @param username
	 * @param version
	 * @return
	 */
	@Override
	public SysRoleIndex getDynamicIndexByUserRole(String username,String version) {
		List<String> roles = sysUserRoleMapper.getRoleByUserName(username);
		String componentUrl = RoleIndexConfigEnum.getIndexByRoles(roles);
		SysRoleIndex roleIndex = new SysRoleIndex(componentUrl);
		//只有 X-Version=v3 的时候，才读取sys_role_index表获取角色首页配置
		if (oConvertUtils.isNotEmpty(version) && roles!=null && roles.size()>0) {
			LambdaQueryWrapper<SysRoleIndex> routeIndexQuery = new LambdaQueryWrapper();
			//用户所有角色
			routeIndexQuery.in(SysRoleIndex::getRoleCode, roles);
			//角色首页状态0：未开启  1：开启
			routeIndexQuery.eq(SysRoleIndex::getStatus, CommonConstant.STATUS_1);
			//优先级正序排序
			routeIndexQuery.orderByAsc(SysRoleIndex::getPriority);
			List<SysRoleIndex> list = sysRoleIndexMapper.selectList(routeIndexQuery);
			if (null != list && list.size() > 0) {
				roleIndex = list.get(0);
			}
		}
		
		//如果componentUrl为空，则返回空
		if(oConvertUtils.isEmpty(roleIndex.getComponent())){
			return null;
		}
		return roleIndex;
	}

	/**
	 * 通过用户名获取用户角色集合
	 * @param username 用户名
     * @return 角色集合
	 */
	@Override
	public Set<String> getUserRolesSet(String username) {
		// 查询用户拥有的角色集合
		List<String> roles = sysUserRoleMapper.getRoleByUserName(username);
		log.info("-------通过数据库读取用户拥有的角色Rules------username： " + username + ",Roles size: " + (roles == null ? 0 : roles.size()));
		return new HashSet<>(roles);
	}

	/**
	 * 通过用户名获取用户权限集合
	 *
	 * @param username 用户名
	 * @return 权限集合
	 */
	@Override
	public Set<String> getUserPermissionsSet(String username) {
		Set<String> permissionSet = new HashSet<>();
		List<SysPermission> permissionList = sysPermissionMapper.queryByUser(username);
		for (SysPermission po : permissionList) {
//			// TODO URL规则有问题？
//			if (oConvertUtils.isNotEmpty(po.getUrl())) {
//				permissionSet.add(po.getUrl());
//			}
			if (oConvertUtils.isNotEmpty(po.getPerms())) {
				permissionSet.add(po.getPerms());
			}
		}
		log.info("-------通过数据库读取用户拥有的权限Perms------username： "+ username+",Perms size: "+ (permissionSet==null?0:permissionSet.size()) );
		return permissionSet;
	}

	/**
	 * 升级SpringBoot2.6.6,不允许循环依赖
	 * @author:qinfeng
	 * @update: 2022-04-07
	 * @param username
	 * @return
	 */
	@Override
	public SysUserCacheInfo getCacheUser(String username) {
		SysUserCacheInfo info = new SysUserCacheInfo();
		info.setOneDepart(true);
		if(oConvertUtils.isEmpty(username)) {
			return null;
		}

		//查询用户信息
		SysUser sysUser = userMapper.getUserByName(username);
		if(sysUser!=null) {
			info.setSysUserCode(sysUser.getUsername());
			info.setSysUserName(sysUser.getRealname());
			info.setSysOrgCode(sysUser.getOrgCode());
		}
		
		//多部门支持in查询
		List<SysDepart> list = sysDepartMapper.queryUserDeparts(sysUser.getId());
		List<String> sysMultiOrgCode = new ArrayList<String>();
		if(list==null || list.size()==0) {
			//当前用户无部门
			//sysMultiOrgCode.add("0");
		}else if(list.size()==1) {
			sysMultiOrgCode.add(list.get(0).getOrgCode());
		}else {
			info.setOneDepart(false);
			for (SysDepart dpt : list) {
				sysMultiOrgCode.add(dpt.getOrgCode());
			}
		}
		info.setSysMultiOrgCode(sysMultiOrgCode);
		
		return info;
	}

    /**
     * 根据部门Id查询
     * @param page
     * @param departId 部门id
     * @param username 用户账户名称
     * @return
     */
	@Override
	public IPage<SysUser> getUserByDepId(Page<SysUser> page, String departId,String username) {
		return userMapper.getUserByDepId(page, departId,username);
	}

	@Override
	public IPage<SysUser> getUserByDepIds(Page<SysUser> page, List<String> departIds, String username) {
		return userMapper.getUserByDepIds(page, departIds,username);
	}

	@Override
	public Map<String, String> getDepNamesByUserIds(List<String> userIds) {
		List<SysUserDepVo> list = this.baseMapper.getDepNamesByUserIds(userIds);

		Map<String, String> res = new HashMap(5);
		list.forEach(item -> {
					if (res.get(item.getUserId()) == null) {
						res.put(item.getUserId(), item.getDepartName());
					} else {
						res.put(item.getUserId(), res.get(item.getUserId()) + "," + item.getDepartName());
					}
				}
		);
		return res;
	}

	//update-begin-author:taoyan date:2022-9-13 for: VUEN-2245【漏洞】发现新漏洞待处理20220906 ----sql注入  方法没有使用，注掉
/*	@Override
	public IPage<SysUser> getUserByDepartIdAndQueryWrapper(Page<SysUser> page, String departId, QueryWrapper<SysUser> queryWrapper) {
		LambdaQueryWrapper<SysUser> lambdaQueryWrapper = queryWrapper.lambda();

		lambdaQueryWrapper.eq(SysUser::getDelFlag, CommonConstant.DEL_FLAG_0);
        lambdaQueryWrapper.inSql(SysUser::getId, "SELECT user_id FROM sys_user_depart WHERE dep_id = '" + departId + "'");

        return userMapper.selectPage(page, lambdaQueryWrapper);
	}*/
	//update-end-author:taoyan date:2022-9-13 for: VUEN-2245【漏洞】发现新漏洞待处理20220906 ----sql注入 方法没有使用，注掉

	@Override
	public IPage<SysUserSysDepartModel> queryUserByOrgCode(String orgCode, SysUser userParams, IPage page) {
		List<SysUserSysDepartModel> list = baseMapper.getUserByOrgCode(page, orgCode, userParams);
		Integer total = baseMapper.getUserByOrgCodeTotal(orgCode, userParams);

		IPage<SysUserSysDepartModel> result = new Page<>(page.getCurrent(), page.getSize(), total);
		result.setRecords(list);

		return result;
	}

    /**
     * 根据角色Id查询
     * @param page
     * @param roleId 角色id
     * @param username 用户账户名称
     * @return
     */
	@Override
	public IPage<SysUser> getUserByRoleId(Page<SysUser> page, String roleId, String username) {
		return userMapper.getUserByRoleId(page,roleId,username);
	}


	@Override
	@CacheEvict(value= {CacheConstant.SYS_USERS_CACHE}, key="#username")
	public void updateUserDepart(String username,String orgCode) {
		baseMapper.updateUserDepart(username, orgCode);
	}


	@Override
	public SysUser getUserByPhone(String phone) {
		return userMapper.getUserByPhone(phone);
	}


	@Override
	public SysUser getUserByEmail(String email) {
		return userMapper.getUserByEmail(email);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void addUserWithDepart(SysUser user, String selectedParts) {
//		this.save(user);  //保存角色的时候已经添加过一次了
		if(oConvertUtils.isNotEmpty(selectedParts)) {
			String[] arr = selectedParts.split(",");
			for (String deaprtId : arr) {
				SysUserDepart userDeaprt = new SysUserDepart(user.getId(), deaprtId);
				sysUserDepartMapper.insert(userDeaprt);
			}
		}
	}


	@Override
	@Transactional(rollbackFor = Exception.class)
	@CacheEvict(value={CacheConstant.SYS_USERS_CACHE}, allEntries=true)
	public void editUserWithDepart(SysUser user, String departs) {
        //更新角色的时候已经更新了一次了，可以再跟新一次
		this.updateById(user);
		String[] arr = {};
		if(oConvertUtils.isNotEmpty(departs)){
			arr = departs.split(",");
		}
		//查询已关联部门
		List<SysUserDepart> userDepartList = sysUserDepartMapper.selectList(new QueryWrapper<SysUserDepart>().lambda().eq(SysUserDepart::getUserId, user.getId()));
		if(userDepartList != null && userDepartList.size()>0){
			for(SysUserDepart depart : userDepartList ){
				//修改已关联部门删除部门用户角色关系
				if(!Arrays.asList(arr).contains(depart.getDepId())){
					List<SysDepartRole> sysDepartRoleList = sysDepartRoleMapper.selectList(
							new QueryWrapper<SysDepartRole>().lambda().eq(SysDepartRole::getDepartId,depart.getDepId()));
					List<String> roleIds = sysDepartRoleList.stream().map(SysDepartRole::getId).collect(Collectors.toList());
					if(roleIds != null && roleIds.size()>0){
						departRoleUserMapper.delete(new QueryWrapper<SysDepartRoleUser>().lambda().eq(SysDepartRoleUser::getUserId, user.getId())
								.in(SysDepartRoleUser::getDroleId,roleIds));
					}
				}
			}
		}
		//先删后加
		sysUserDepartMapper.delete(new QueryWrapper<SysUserDepart>().lambda().eq(SysUserDepart::getUserId, user.getId()));
		if(oConvertUtils.isNotEmpty(departs)) {
			for (String departId : arr) {
				SysUserDepart userDepart = new SysUserDepart(user.getId(), departId);
				sysUserDepartMapper.insert(userDepart);
			}
		}
	}


	/**
	   * 校验用户是否有效
	 * @param sysUser
	 * @return
	 */
	@Override
	public Result<?> checkUserIsEffective(SysUser sysUser) {
		Result<?> result = new Result<Object>();
		//情况1：根据用户信息查询，该用户不存在
		if (sysUser == null) {
			result.error500("该用户不存在，请注册");
			baseCommonService.addLog("用户登录失败，用户不存在！", CommonConstant.LOG_TYPE_1, null);
			return result;
		}
		//情况2：根据用户信息查询，该用户已注销
		//update-begin---author:王帅   Date:20200601  for：if条件永远为falsebug------------
		if (CommonConstant.DEL_FLAG_1.equals(sysUser.getDelFlag())) {
		//update-end---author:王帅   Date:20200601  for：if条件永远为falsebug------------
			baseCommonService.addLog("用户登录失败，用户名:" + sysUser.getUsername() + "已注销！", CommonConstant.LOG_TYPE_1, null);
			result.error500("该用户已注销");
			return result;
		}
		//情况3：根据用户信息查询，该用户已冻结
		if (CommonConstant.USER_FREEZE.equals(sysUser.getStatus())) {
			baseCommonService.addLog("用户登录失败，用户名:" + sysUser.getUsername() + "已冻结！", CommonConstant.LOG_TYPE_1, null);
			result.error500("该用户已冻结");
			return result;
		}
		return result;
	}

	@Override
	public List<SysUser> queryLogicDeleted() {
		return this.queryLogicDeleted(null);
	}

	@Override
	public List<SysUser> queryLogicDeleted(LambdaQueryWrapper<SysUser> wrapper) {
		if (wrapper == null) {
			wrapper = new LambdaQueryWrapper<>();
		}
		wrapper.eq(SysUser::getDelFlag, CommonConstant.DEL_FLAG_1);
		return userMapper.selectLogicDeleted(wrapper);
	}

	@Override
	@CacheEvict(value={CacheConstant.SYS_USERS_CACHE}, allEntries=true)
	public boolean revertLogicDeleted(List<String> userIds, SysUser updateEntity) {
		return userMapper.revertLogicDeleted(userIds, updateEntity) > 0;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean removeLogicDeleted(List<String> userIds) {
		// 1. 删除用户
		int line = userMapper.deleteLogicDeleted(userIds);
		// 2. 删除用户部门关系
		line += sysUserDepartMapper.delete(new LambdaQueryWrapper<SysUserDepart>().in(SysUserDepart::getUserId, userIds));
		//3. 删除用户角色关系
		line += sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
		//4.同步删除第三方App的用户
		try {
			dingtalkService.removeThirdAppUser(userIds);
			wechatEnterpriseService.removeThirdAppUser(userIds);
		} catch (Exception e) {
			log.error("同步删除第三方App的用户失败：", e);
		}
		//5. 删除第三方用户表（因为第4步需要用到第三方用户表，所以在他之后删）
		line += sysThirdAccountMapper.delete(new LambdaQueryWrapper<SysThirdAccount>().in(SysThirdAccount::getSysUserId, userIds));

		return line != 0;
	}

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateNullPhoneEmail() {
        userMapper.updateNullByEmptyString("email");
        userMapper.updateNullByEmptyString("phone");
        return true;
    }

	@Override
	public void saveThirdUser(SysUser sysUser) {
		//保存用户
		String userid = UUIDGenerator.generate();
		sysUser.setId(userid);
		baseMapper.insert(sysUser);
		//获取第三方角色
		SysRole sysRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "third_role"));
		//保存用户角色
		SysUserRole userRole = new SysUserRole();
		userRole.setRoleId(sysRole.getId());
		userRole.setUserId(userid);
		sysUserRoleMapper.insert(userRole);
	}

	@Override
	public List<SysUser> queryByDepIds(List<String> departIds, String username) {
		return userMapper.queryByDepIds(departIds,username);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveUser(SysUser user, String selectedRoles, String selectedDeparts) {
		//step.1 保存用户
		this.save(user);
		//step.2 保存角色
		if(oConvertUtils.isNotEmpty(selectedRoles)) {
			String[] arr = selectedRoles.split(",");
			for (String roleId : arr) {
				SysUserRole userRole = new SysUserRole(user.getId(), roleId);
				sysUserRoleMapper.insert(userRole);
			}
		}
		//step.3 保存所属部门
		if(oConvertUtils.isNotEmpty(selectedDeparts)) {
			String[] arr = selectedDeparts.split(",");
			for (String deaprtId : arr) {
				SysUserDepart userDeaprt = new SysUserDepart(user.getId(), deaprtId);
				sysUserDepartMapper.insert(userDeaprt);
			}
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	@CacheEvict(value={CacheConstant.SYS_USERS_CACHE}, allEntries=true)
	public void editUser(SysUser user, String roles, String departs) {
		//step.1 修改用户基础信息
		this.updateById(user);
		//step.2 修改角色
		//处理用户角色 先删后加
		sysUserRoleMapper.delete(new QueryWrapper<SysUserRole>().lambda().eq(SysUserRole::getUserId, user.getId()));
		if(oConvertUtils.isNotEmpty(roles)) {
			String[] arr = roles.split(",");
			for (String roleId : arr) {
				SysUserRole userRole = new SysUserRole(user.getId(), roleId);
				sysUserRoleMapper.insert(userRole);
			}
		}

		//step.3 修改部门
		String[] arr = {};
		if(oConvertUtils.isNotEmpty(departs)){
			arr = departs.split(",");
		}
		//查询已关联部门
		List<SysUserDepart> userDepartList = sysUserDepartMapper.selectList(new QueryWrapper<SysUserDepart>().lambda().eq(SysUserDepart::getUserId, user.getId()));
		if(userDepartList != null && userDepartList.size()>0){
			for(SysUserDepart depart : userDepartList ){
				//修改已关联部门删除部门用户角色关系
				if(!Arrays.asList(arr).contains(depart.getDepId())){
					List<SysDepartRole> sysDepartRoleList = sysDepartRoleMapper.selectList(
							new QueryWrapper<SysDepartRole>().lambda().eq(SysDepartRole::getDepartId,depart.getDepId()));
					List<String> roleIds = sysDepartRoleList.stream().map(SysDepartRole::getId).collect(Collectors.toList());
					if(roleIds != null && roleIds.size()>0){
						departRoleUserMapper.delete(new QueryWrapper<SysDepartRoleUser>().lambda().eq(SysDepartRoleUser::getUserId, user.getId())
								.in(SysDepartRoleUser::getDroleId,roleIds));
					}
				}
			}
		}
		//先删后加
		sysUserDepartMapper.delete(new QueryWrapper<SysUserDepart>().lambda().eq(SysUserDepart::getUserId, user.getId()));
		if(oConvertUtils.isNotEmpty(departs)) {
			for (String departId : arr) {
				SysUserDepart userDepart = new SysUserDepart(user.getId(), departId);
				sysUserDepartMapper.insert(userDepart);
			}
		}
		//step.4 修改手机号和邮箱
		// 更新手机号、邮箱空字符串为 null
		userMapper.updateNullByEmptyString("email");
		userMapper.updateNullByEmptyString("phone");

	}

	@Override
	public List<String> userIdToUsername(Collection<String> userIdList) {
		LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.in(SysUser::getId, userIdList);
		List<SysUser> userList = super.list(queryWrapper);
		return userList.stream().map(SysUser::getUsername).collect(Collectors.toList());
	}

	@Override
	@Cacheable(cacheNames=CacheConstant.SYS_USERS_CACHE, key="#username")
	@SensitiveEncode
	public LoginUser getEncodeUserInfo(String username){
		if(oConvertUtils.isEmpty(username)) {
			return null;
		}
		LoginUser loginUser = new LoginUser();
		SysUser sysUser = userMapper.getUserByName(username);
		if(sysUser==null) {
			return null;
		}
		BeanUtils.copyProperties(sysUser, loginUser);
		return loginUser;
	}
}
