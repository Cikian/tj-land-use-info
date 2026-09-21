package org.jeecg.modules.system.zk;

import com.stargis.zk.sdk.codec.ZkCodec;
import com.stargis.zk.sdk.db.ZkDep2UserDao;
import com.stargis.zk.sdk.model.ZkPage;
import com.stargis.zk.sdk.model.ZkUser;
import com.stargis.zk.sdk.userApi.ZkUserApi;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.system.entity.SysDepart;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserDepart;
import org.jeecg.modules.system.entity.SysUserRole;
import org.jeecg.modules.system.mapper.SysDepartMapper;
import org.jeecg.modules.system.mapper.SysRoleMapper;
import org.jeecg.modules.system.mapper.SysUserDepartMapper;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.jeecg.modules.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 用户（{@code sys_user}）→ 中台用户（{@code estar_user_system}）单向同步服务。
 *
 * <h3>同步范围</h3>
 * 用户基础信息 + <b>所属机构</b>与<b>角色</b>两段关联关系（中台分别落在
 * {@code estar_dep2user_system} / {@code estar_role2user_system}）。
 * 本系统已按中台口径约束为「<b>一个用户只有一个机构、一个角色</b>」，因此三段信息可以一次写清。
 *
 * <h3>字段映射</h3>
 * <table border="1">
 *   <tr><th>jeecg {@code sys_user}</th><th>中台 {@code estar_user_system}</th><th>说明</th></tr>
 *   <tr><td>{@code username}</td><td>{@code loginname}</td><td>登录账号（中台登录名）</td></tr>
 *   <tr><td>{@code realname}</td><td>{@code username}</td><td>姓名</td></tr>
 *   <tr><td>{@code password}（明文时）</td><td>{@code password}</td><td>中台落 {@code MD5("WEBGLZKSYSTEM"+明文)}</td></tr>
 *   <tr><td>{@code sex} 1男/2女</td><td>{@code gender} 0男/1女</td><td>取值不同，需转换</td></tr>
 *   <tr><td>{@code phone}</td><td>{@code phonenumber}</td><td>中台 varchar(30)</td></tr>
 *   <tr><td>{@code email}</td><td>{@code email}</td><td>中台 varchar(64)</td></tr>
 *   <tr><td>{@code status} 1正常/2冻结</td><td>{@code status} 1正常/0停用</td><td>取值不同，需转换</td></tr>
 *   <tr><td>{@code sys_user_depart}（1 条）</td><td>{@code departmentid} → {@code estar_dep2user_system}</td><td>用部门的 {@code zk_dept_id}</td></tr>
 *   <tr><td>{@code sys_user_role}（1 条）</td><td>{@code workpostid} → {@code estar_role2user_system}</td><td>用角色的 {@code zk_role_id}</td></tr>
 *   <tr><td>—</td><td>{@code applystatus}</td><td>本服务统一置 {@code 1}（已审批）</td></tr>
 * </table>
 *
 * <h3>⚠️ 中台用户接口的四个实测特性（决定了本服务的实现方式）</h3>
 * <ol>
 *   <li><b>新增会强制"未审批"</b>：{@code addEntity} 忽略传入的 {@code applystatus}，落库恒为 {@code 0}，
 *       而中台登录要求 {@code applystatus=1}。因此本服务在新增后<b>必须再调用一次 {@code updateEntity}
 *       置 {@code applystatus=1}</b>，否则该用户根本登录不了中台。</li>
 *   <li><b>角色关系是"替换"语义</b>：{@code updateEntity} 会按传入的 {@code workpostid} 重设角色关系
 *       （不传则不动）。所以更新时<b>必须带上角色 ID</b>，否则角色关系可能与本地不一致。</li>
 *   <li><b>机构关系只能在建号时写入</b>：{@code updateEntity} 对 {@code departmentid} 只在"该用户还没有机构"
 *       时才新增，已有机构时<b>静默忽略</b>（真机实测）。即<b>通过中台接口无法修改用户所属机构</b>。
 *       这是中台自身的缺陷，而机构调整是业务刚需，因此本服务在<b>配置了
 *       {@code ck.stargis.zk.db-info}</b> 时，会直连中台库把
 *       {@code estar_dep2user_system} 的关系改对（SDK 的 {@code db.ZkDep2UserDao}，只做这一件事）；
 *       未配置直连时退化为<b>硬阻断并提示</b>，绝不让两边静默不一致。</li>
 *   <li><b>删除是物理删除</b>且会级联清掉两张关系表；而中台用户 ID 被图层共享、操作日志等引用，
 *       物理删除会断链。故默认改为<b>停用</b>（{@code user-delete-mode=disable}），
 *       需要物理删除时显式配置 {@code physical}。</li>
 * </ol>
 *
 * <h3>失败语义</h3>
 * 与机构/角色同步一致：{@code user-sync-strict=true} 时中台失败即抛异常、本地事务整体回滚；
 * {@code false} 时只记 {@code zk_sync_status=failed} + 原因，本地照常成功。
 *
 * <h3>明文口令的来源</h3>
 * 中台建号/改密都需要<b>明文</b>口令，而本系统只存哈希。因此只有两个入口能拿到明文：
 * <ul>
 *   <li>新建用户（{@code /sys/user/add} 收到明文后立即交给本服务）；</li>
 *   <li>重置/修改密码（{@code changePassword} / {@code updatePassword}）。</li>
 * </ul>
 * 存量用户（本地还没有 {@code zk_user_id}）在普通"编辑"里无法同步——因为拿不到明文口令，中台无法建号。
 * 这类用户编辑时会照常保存本地，并被标记 {@code not_synced} + 提示；<b>重置一次密码即可完成中台建号</b>
 * （{@code changePassword} / {@code resetPassword} 都能拿到明文）。
 */
@Slf4j
@Service
public class ZkUserSyncService {

    /** 中台 {@code phonenumber} varchar(30)。 */
    private static final int MAX_PHONE_LENGTH = 30;

    /** 中台 {@code email} varchar(64)。 */
    private static final int MAX_EMAIL_LENGTH = 64;

    /** 写入 {@code zk_sync_msg} 的最大长度，与建表脚本 varchar(500) 对齐。 */
    private static final int MAX_MSG_LENGTH = 500;

    /** 删除策略：停用（默认）。 */
    public static final String DELETE_MODE_DISABLE = "disable";

    /** 删除策略：物理删除。 */
    public static final String DELETE_MODE_PHYSICAL = "physical";

    @Value("${ck.stargis.zk.user-sync-enabled:true}")
    private boolean syncEnabled;

    @Value("${ck.stargis.zk.user-sync-strict:true}")
    private boolean strict;

    @Value("${ck.stargis.zk.user-delete-mode:disable}")
    private String deleteMode;

    private final ObjectProvider<ZkUserApi> zkUserApiProvider;
    /**
     * 中台库直连通道（可选）：中台接口改不了用户机构，只能直接改
     * {@code estar_dep2user_system}。未配置 {@code ck.stargis.zk.db-info} 时拿不到 Bean。
     */
    private final ObjectProvider<ZkDep2UserDao> zkDep2UserDaoProvider;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysUserDepartMapper sysUserDepartMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysDepartMapper sysDepartMapper;

    @Autowired
    public ZkUserSyncService(ObjectProvider<ZkUserApi> zkUserApiProvider,
                             ObjectProvider<ZkDep2UserDao> zkDep2UserDaoProvider,
                             SysUserMapper sysUserMapper,
                             SysUserRoleMapper sysUserRoleMapper,
                             SysUserDepartMapper sysUserDepartMapper,
                             SysRoleMapper sysRoleMapper,
                             SysDepartMapper sysDepartMapper) {
        this.zkUserApiProvider = zkUserApiProvider;
        this.zkDep2UserDaoProvider = zkDep2UserDaoProvider;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysUserDepartMapper = sysUserDepartMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysDepartMapper = sysDepartMapper;
    }

    // ==================================================================
    // 对外入口（均由 SysUserServiceImpl 在事务内调用）
    // ==================================================================

    /** 是否真正会执行中台同步（开关打开且 SDK 已装配）。 */
    public boolean isSyncEnabled() {
        return syncEnabled && zkUserApiProvider.getIfAvailable() != null;
    }

    /**
     * 新增用户后同步到中台（建号 + 机构关系 + 角色关系 + 审批通过）。
     *
     * @param user          已落库的用户（含 id、username、realname、status 等）
     * @param plainPassword 明文口令（中台建号必需）
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncOnCreate(SysUser user, String plainPassword) {
        if (user == null || oConvertUtils.isEmpty(user.getId())) {
            return;
        }
        // 口径校验：无论开关与严格模式，数据不合规都应直接报错
        validate(user, plainPassword, true);
        UserRelation relation = resolveRelation(user.getId());
        ZkUserApi api = zkUserApiProvider.getIfAvailable();
        if (!syncEnabled || api == null) {
            markNotSynced(user);
            return;
        }
        try {
            ZkUser remote = createRemote(api, user, relation, plainPassword);
            markSynced(user, remote);
        } catch (Exception e) {
            handleFailure(user, "新增", e);
        }
    }

    /**
     * 修改用户后同步到中台（更新基础信息 + 角色关系；机构不允许改，见类注释）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncOnUpdate(SysUser user) {
        if (user == null || oConvertUtils.isEmpty(user.getId())) {
            return;
        }
        SysUser full = sysUserMapper.selectById(user.getId());
        if (full == null) {
            log.warn("[zk-sync] 用户 {} 在本地已不存在，跳过中台同步", user.getId());
            return;
        }
        validate(full, null, false);
        UserRelation relation = resolveRelation(full.getId());
        ZkUserApi api = zkUserApiProvider.getIfAvailable();
        if (!syncEnabled || api == null) {
            markNotSynced(full);
            copySyncFields(full, user);
            return;
        }
        if (oConvertUtils.isEmpty(full.getZkUserId())) {
            // 没有中台 ID 说明是存量用户：中台建号需要明文口令，而"编辑"拿不到明文。
            // 这里不阻断本地编辑（否则管理员连机构和角色都改不了，形成死锁），只标记待同步并给出可操作提示；
            // 补同步入口是【重置密码】——改密时能拿到明文，会顺手把中台用户建出来。
            log.warn("[zk-sync] 用户【{}】尚未同步到中台（本地无 zk_user_id），本次仅更新本地；"
                            + "请通过【重置密码】完成中台建号", full.getUsername());
            full.setZkSyncStatus(ZkSyncStatus.NOT_SYNCED);
            full.setZkSyncTime(new Date());
            full.setZkSyncMsg("尚未同步到中台：中台建号需要明文口令，请在【用户管理 → 密码】里重置一次该用户密码，"
                    + "系统会在改密时自动完成中台建号");
            persist(full);
            copySyncFields(full, user);
            return;
        }
        // ⚠️ 机构关系必须在中台写接口之前处理好：中台 updateEntity 改不了机构
        // （只在用户还没有机构时才写入），所以这里走 SDK 的直连通道改 estar_dep2user_system；
        // 未配置 ck.stargis.zk.db-info 时退化为"硬阻断并提示"，绝不静默不一致。
        syncDepartmentRelation(api, full, relation);
        try {
            updateRemote(api, full, relation);
            markSynced(full, preserveZkId(full));
        } catch (Exception e) {
            handleFailure(full, "修改", e);
        }
        copySyncFields(full, user);
    }

    /**
     * 改密后同步口令到中台。
     *
     * <p>若该用户还没有 {@code zk_user_id}（存量用户），则借这次拿到的明文口令<b>顺手完成中台建号</b>，
     * 这是存量用户补同步的推荐入口。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncOnPasswordChange(String userId, String plainPassword) {
        if (oConvertUtils.isEmpty(userId) || oConvertUtils.isEmpty(plainPassword)) {
            return;
        }
        SysUser full = sysUserMapper.selectById(userId);
        if (full == null) {
            return;
        }
        ZkUserApi api = zkUserApiProvider.getIfAvailable();
        if (!syncEnabled || api == null) {
            return;
        }
        try {
            if (oConvertUtils.isEmpty(full.getZkUserId())) {
                UserRelation relation = resolveRelation(userId);
                ZkUser remote = createRemote(api, full, relation, plainPassword);
                markSynced(full, remote);
                log.info("[zk-sync] 存量用户【{}】已在改密时补建到中台，zkUserId={}",
                        full.getUsername(), full.getZkUserId());
            } else {
                ZkUser req = new ZkUser();
                req.setId(full.getZkUserId());
                req.setLoginname(full.getUsername());
                req.setUsername(full.getRealname());
                api.update(req, plainPassword);
                markSynced(full, preserveZkId(full));
                log.info("[zk-sync] 用户【{}】的中台口令已同步", full.getUsername());
            }
        } catch (Exception e) {
            handleFailure(full, "改密", e);
        }
    }

    /**
     * 删除用户时同步中台。
     *
     * <p>默认（{@code user-delete-mode=disable}）只把中台用户置为<b>停用</b>：
     * 中台用户 ID 被图层共享授权、操作日志、业务字段引用，物理删除会断链。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncOnDelete(Collection<String> userIds) {
        ZkUserApi api = zkUserApiProvider.getIfAvailable();
        if (!syncEnabled || api == null || userIds == null || userIds.isEmpty()) {
            return;
        }
        List<SysUser> users = new ArrayList<>(sysUserMapper.selectBatchIds(userIds));
        List<String> mapped = new ArrayList<>();
        for (SysUser u : users) {
            if (oConvertUtils.isNotEmpty(u.getZkUserId())) {
                mapped.add(u.getZkUserId());
            }
        }
        if (mapped.isEmpty()) {
            return;
        }
        boolean physical = DELETE_MODE_PHYSICAL.equalsIgnoreCase(deleteMode);
        try {
            if (physical) {
                api.deleteBatch(mapped);
                log.warn("[zk-sync] 已按物理删除策略删除中台用户 {} 个：{}", mapped.size(), mapped);
            } else {
                for (SysUser u : users) {
                    if (oConvertUtils.isEmpty(u.getZkUserId())) {
                        continue;
                    }
                    ZkUser req = new ZkUser();
                    req.setId(u.getZkUserId());
                    req.setLoginname(u.getUsername());
                    req.setUsername(u.getRealname());
                    req.setStatus("0");
                    req.setApplystatus("1");
                    api.update(req, null);
                }
                log.info("[zk-sync] 已按停用策略停用中台用户 {} 个：{}", mapped.size(), mapped);
            }
        } catch (Exception e) {
            log.error("[zk-sync] 删除用户时同步中台失败：{}", e.getMessage(), e);
            if (strict) {
                throw new JeecgBootException("删除用户时同步中台失败，操作已回滚：" + e.getMessage(), e);
            }
        }
    }

    /**
     * 冻结/解冻用户后同步状态到中台（中台 {@code status}: 1 正常 / 0 停用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncOnStatusChange(Collection<String> userIds) {
        ZkUserApi api = zkUserApiProvider.getIfAvailable();
        if (!syncEnabled || api == null || userIds == null || userIds.isEmpty()) {
            return;
        }
        List<SysUser> users = new ArrayList<>(sysUserMapper.selectBatchIds(userIds));
        for (SysUser u : users) {
            if (oConvertUtils.isEmpty(u.getZkUserId())) {
                continue;
            }
            try {
                ZkUser req = new ZkUser();
                req.setId(u.getZkUserId());
                req.setLoginname(u.getUsername());
                req.setUsername(u.getRealname());
                req.setStatus(toZkStatus(u.getStatus()));
                req.setApplystatus("1");
                api.update(req, null);
                markSynced(u, preserveZkId(u));
            } catch (Exception e) {
                log.error("[zk-sync] 同步用户【{}】状态到中台失败：{}", u.getUsername(), e.getMessage(), e);
                if (strict) {
                    throw new JeecgBootException("同步用户【" + u.getRealname() + "】状态到中台失败，操作已回滚："
                            + e.getMessage(), e);
                }
            }
        }
    }

    // ==================================================================
    // 关系解析与校验
    // ==================================================================

    /**
     * 解析用户的"唯一机构 + 唯一角色"，并换算出中台侧的 ID。
     *
     * <p>本系统已约束为单机构单角色，这里既是取值来源，也是<b>最后一道校验</b>：
     * 一旦出现 0 个或 2 个以上关联记录，直接报错而不是猜一个。
     */
    public UserRelation resolveRelation(String userId) {
        List<SysUserDepart> departs = sysUserDepartMapper.selectList(
                new LambdaQueryWrapper<SysUserDepart>().eq(SysUserDepart::getUserId, userId));
        if (departs == null || departs.isEmpty()) {
            throw new JeecgBootException("用户必须且只能选择一个所属机构：当前没有机构，请先在用户编辑页选择机构");
        }
        if (departs.size() > 1) {
            throw new JeecgBootException("用户必须且只能选择一个所属机构：当前有 " + departs.size()
                    + " 个机构，请先在用户编辑页只保留一个（本系统按中台口径限制为单机构）");
        }
        List<SysUserRole> roles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roles == null || roles.isEmpty()) {
            throw new JeecgBootException("用户必须且只能选择一个角色：当前没有角色，请先在用户编辑页选择角色");
        }
        if (roles.size() > 1) {
            throw new JeecgBootException("用户必须且只能选择一个角色：当前有 " + roles.size()
                    + " 个角色，请先在用户编辑页只保留一个（本系统按中台口径限制为单角色）");
        }

        String deptId = departs.get(0).getDepId();
        SysDepart depart = sysDepartMapper.selectById(deptId);
        if (depart == null) {
            throw new JeecgBootException("用户关联的机构已不存在（depId=" + deptId + "），请重新选择机构");
        }
        if (oConvertUtils.isEmpty(depart.getZkDeptId())) {
            throw new JeecgBootException("机构【" + depart.getDepartName() + "】尚未同步到中台，"
                    + "请先到【机构管理】对该机构保存一次，再同步用户");
        }

        String roleId = roles.get(0).getRoleId();
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new JeecgBootException("用户关联的角色已不存在（roleId=" + roleId + "），请重新选择角色");
        }
        if (oConvertUtils.isEmpty(role.getZkRoleId())) {
            throw new JeecgBootException("角色【" + role.getRoleName() + "】尚未同步到中台，"
                    + "请先到【角色管理】对该角色保存一次，再同步用户");
        }

        UserRelation r = new UserRelation();
        r.deptId = deptId;
        r.deptName = depart.getDepartName();
        r.zkDeptId = depart.getZkDeptId();
        r.roleId = roleId;
        r.roleName = role.getRoleName();
        r.zkRoleId = role.getZkRoleId();
        return r;
    }

    private void validate(SysUser user, String plainPassword, boolean requirePassword) {
        if (oConvertUtils.isEmpty(user.getUsername())) {
            throw new JeecgBootException("用户账号不能为空：中台用账号作为登录名(loginname)");
        }
        // 中台登录接口是 SQL 拼接，SDK 侧统一做白名单自保；这里提前给出可读提示
        ZkCodec.checkLoginname(user.getUsername());
        if (oConvertUtils.isEmpty(user.getRealname())) {
            throw new JeecgBootException("用户姓名不能为空：中台要求姓名(username)必填");
        }
        if (requirePassword && oConvertUtils.isEmpty(plainPassword)) {
            throw new JeecgBootException("明文口令不能为空：中台建号必须提供明文口令");
        }
        if (oConvertUtils.isNotEmpty(user.getPhone()) && user.getPhone().length() > MAX_PHONE_LENGTH) {
            throw new JeecgBootException("手机号长度超过中台上限 " + MAX_PHONE_LENGTH + " 个字符：中台 phonenumber varchar(30)");
        }
        if (oConvertUtils.isNotEmpty(user.getEmail()) && user.getEmail().length() > MAX_EMAIL_LENGTH) {
            throw new JeecgBootException("邮箱长度超过中台上限 " + MAX_EMAIL_LENGTH + " 个字符：中台 email varchar(64)");
        }
    }

    // ==================================================================
    // 中台调用
    // ==================================================================

    /** 在中台建号，并补一次 {@code applystatus=1}（中台新增强制未审批，见类注释）。 */
    private ZkUser createRemote(ZkUserApi api, SysUser user, UserRelation relation, String plainPassword) {
        ZkUser request = buildZkUser(user, relation);
        ZkUser created = api.add(request, plainPassword);
        if (created == null || oConvertUtils.isEmpty(created.getId())) {
            created = findByLoginname(api, user.getUsername());
        }
        if (created == null || oConvertUtils.isEmpty(created.getId())) {
            throw new JeecgBootException("中台未返回用户 ID，无法建立映射。账号：" + user.getUsername());
        }
        approveRemote(api, user, relation, created.getId());
        created.setApplystatus("1");
        // 建号时中台接口写的是正确的机构；这里再核一遍（不额外写）：
        // 万一中台实现变化导致机构关系没落上，直连通道会把它补出来（幂等，一致时不产生写操作）
        repairDepartmentQuietly(created.getId(), relation, user);
        return created;
    }

    /** 建号后的机构关系核对：只在中台确实没写对时才直连修正，且失败不致命（建号本身已成功）。 */
    private void repairDepartmentQuietly(String zkUserId, UserRelation relation, SysUser user) {
        ZkDep2UserDao dao = zkDep2UserDaoProvider.getIfAvailable();
        if (dao == null || !dao.isAvailable() || relation == null) {
            return;
        }
        try {
            dao.ensureUserDepartment(zkUserId, relation.zkDeptId);
        } catch (Exception e) {
            log.warn("[zk-sync] 建号后核对中台用户【{}】机构关系失败（不影响建号）：{}",
                    user.getUsername(), e.getMessage());
        }
    }

    /**
     * 新增后置为"已审批 + 正常"，否则该用户无法登录中台。
     *
     * <p>同时带上机构与角色 ID：中台 {@code updateEntity} 的角色关系是"替换"语义，
     * 不传会把角色关系清掉（实测确认传了才可靠）。
     */
    private void approveRemote(ZkUserApi api, SysUser user, UserRelation relation, String zkUserId) {
        ZkUser approve = new ZkUser();
        approve.setId(zkUserId);
        approve.setLoginname(user.getUsername());
        approve.setUsername(user.getRealname());
        approve.setStatus(toZkStatus(user.getStatus()));
        approve.setApplystatus("1");
        approve.setDepartmentid(relation.zkDeptId);
        approve.setWorkpostid(relation.zkRoleId);
        api.update(approve, null);
    }

    /**
     * 把中台侧的"用户-机构"关系对齐到本地所选机构。
     *
     * <p><b>为什么不能走中台接口</b>：中台 {@code userApi/updateEntity} 对 {@code departmentid}
     * 只在"该用户还没有机构"时才写入，已有机构时静默忽略（真机实测），因此接口层面
     * <b>无法修改用户所属机构</b>（中台自身缺陷，接口会回显新值但库里没变）。
     *
     * <p><b>本方法的处理顺序</b>：
     * <ol>
     *   <li>读取中台当前机构（优先 REST 详情，失败则退回直连查询）；</li>
     *   <li>一致 → 什么都不做（不做无谓写操作）；</li>
     *   <li>不一致且<b>已配置 {@code ck.stargis.zk.db-info}</b> → 直连中台库关系表
     *       {@code estar_dep2user_system} 改/补机构关系（这是本方法存在的唯一理由）；</li>
     *   <li>不一致但<b>没有配置</b>直连 → 直接报错阻断本次修改（不受严格模式影响），
     *       提示配置 {@code ck.stargis.zk.db-info}，避免"本地改了机构、中台没改"这种静默不一致。</li>
     * </ol>
     *
     * <p>直连会绕过中台的接口校验与操作日志，因此每次都按 WARN 级别留痕，便于审计与对账。
     */
    private void syncDepartmentRelation(ZkUserApi api, SysUser user, UserRelation relation) {
        String zkUserId = user.getZkUserId();
        if (oConvertUtils.isEmpty(zkUserId) || relation == null) {
            return;
        }
        ZkDep2UserDao dao = zkDep2UserDaoProvider.getIfAvailable();
        boolean dbAvailable = dao != null && dao.isAvailable();

        // 1) 读中台当前机构 ID：先 REST，取不到再退回直连查询
        String currentId = null;
        String currentName = null;
        try {
            ZkUser remote = api.getDataById(zkUserId);
            if (remote != null) {
                currentId = remote.getDepartmentid();
                currentName = remote.getDepartmentname();
            }
        } catch (Exception e) {
            log.warn("[zk-sync] 读取中台用户【{}】详情失败，将改用直连方式核对机构：{}",
                    user.getUsername(), e.getMessage());
        }
        if (oConvertUtils.isEmpty(currentId) && dbAvailable) {
            currentId = dao.findDepartmentId(zkUserId);
            currentName = null;
        }
        // 已经一致 → 不做任何写操作
        if (relation.zkDeptId.equals(currentId)) {
            return;
        }

        // 2) 有直连通道 → 直接改/补机构关系
        if (dbAvailable) {
            try {
                dao.ensureUserDepartment(zkUserId, relation.zkDeptId);
            } catch (Exception e) {
                throw new JeecgBootException("修改中台用户机构关系失败（直连中台库）：" + e.getMessage()
                        + "。为避免两系统机构不一致，本次修改已回滚；请检查 ck.stargis.zk.db-info 配置与中台库连通性", e);
            }
            return;
        }

        // 3) 没有直连通道：只有在"确认中台当前机构与目标不同"时才阻断；
        //    读不到当前机构时无法判断，按原逻辑继续（由后续 REST 调用按严格/非严格语义处理）
        if (oConvertUtils.isNotEmpty(currentId)) {
            String show = oConvertUtils.isNotEmpty(currentName) ? currentName : currentId;
            throw new JeecgBootException("中台接口不支持修改用户所属机构：该用户在中台属于【" + show
                    + "】，本次要改成【" + relation.deptName + "】。"
                    + "请在配置中补上 ck.stargis.zk.db-info（中台数据库连接），"
                    + "系统会在保存用户时直连中台库修正 estar_dep2user_system 关系；"
                    + "否则为避免两系统机构不一致，这里只能阻止本次修改。");
        }
    }

    /** 更新中台用户（不含口令）。 */
    private void updateRemote(ZkUserApi api, SysUser user, UserRelation relation) {
        ZkUser request = buildZkUser(user, relation);
        request.setId(user.getZkUserId());
        request.setApplystatus("1");
        api.update(request, null);
    }

    private ZkUser buildZkUser(SysUser user, UserRelation relation) {
        ZkUser zk = new ZkUser();
        zk.setLoginname(user.getUsername());
        zk.setUsername(user.getRealname());
        zk.setGender(toZkGender(user.getSex()));
        zk.setPhonenumber(user.getPhone());
        zk.setEmail(user.getEmail());
        zk.setStatus(toZkStatus(user.getStatus()));
        zk.setApplystatus("1");
        zk.setDepartmentid(relation.zkDeptId);
        zk.setWorkpostid(relation.zkRoleId);
        return zk;
    }

    /** 中台未回传实体时按登录名反查兜底。 */
    private ZkUser findByLoginname(ZkUserApi api, String loginname) {
        if (oConvertUtils.isEmpty(loginname)) {
            return null;
        }
        try {
            ZkPage<ZkUser> page = api.getDataList(loginname, null, null, null, null, null, 1, 50);
            List<ZkUser> list = page == null ? null : page.getData();
            if (list == null) {
                return null;
            }
            for (ZkUser u : list) {
                if (loginname.equals(u.getLoginname())) {
                    return u;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[zk-sync] 新增后按 loginname={} 反查中台用户失败：{}", loginname, e.getMessage());
            return null;
        }
    }

    // ==================================================================
    // 状态回写
    // ==================================================================

    private ZkUser preserveZkId(SysUser user) {
        ZkUser zk = new ZkUser();
        zk.setId(user.getZkUserId());
        return zk;
    }

    private void markSynced(SysUser user, ZkUser remote) {
        if (remote != null && oConvertUtils.isNotEmpty(remote.getId())) {
            user.setZkUserId(remote.getId());
        }
        user.setZkLoginname(user.getUsername());
        user.setZkSyncStatus(ZkSyncStatus.SYNCED);
        user.setZkSyncTime(new Date());
        // 用空串而不是 null：MyBatis-Plus 默认更新策略会忽略 null，置空串才能真正清掉上次的失败原因
        user.setZkSyncMsg("");
        persist(user);
    }

    private void markNotSynced(SysUser user) {
        user.setZkSyncStatus(ZkSyncStatus.NOT_SYNCED);
        user.setZkSyncMsg("");
        persist(user);
    }

    private void handleFailure(SysUser user, String action, Exception e) {
        user.setZkSyncStatus(ZkSyncStatus.FAILED);
        user.setZkSyncTime(new Date());
        user.setZkSyncMsg(abbreviate(e.getMessage()));
        persist(user);
        log.error("[zk-sync] 用户【{}】(loginname={}) {} 同步到中台失败：{}",
                user.getRealname(), user.getUsername(), action, e.getMessage(), e);
        if (strict) {
            throw new JeecgBootException("用户【" + user.getRealname() + "】同步到中台失败，操作已回滚："
                    + e.getMessage(), e);
        }
    }

    private void persist(SysUser user) {
        try {
            sysUserMapper.updateById(user);
        } catch (Exception e) {
            log.error("[zk-sync] 回写用户 {} 的中台同步状态失败：{}", user.getId(), e.getMessage(), e);
        }
    }

    private void copySyncFields(SysUser from, SysUser to) {
        if (to == null || to == from) {
            return;
        }
        to.setZkUserId(from.getZkUserId());
        to.setZkLoginname(from.getZkLoginname());
        to.setZkSyncStatus(from.getZkSyncStatus());
        to.setZkSyncTime(from.getZkSyncTime());
        to.setZkSyncMsg(from.getZkSyncMsg());
    }

    /** jeecg {@code sex}（1 男 / 2 女）→ 中台 {@code gender}（0 男 / 1 女）。 */
    private String toZkGender(Integer sex) {
        if (sex == null) {
            return null;
        }
        return sex == 2 ? "1" : "0";
    }

    /** jeecg {@code status}（1 正常 / 2 冻结）→ 中台 {@code status}（1 正常 / 0 停用）。 */
    private String toZkStatus(Integer status) {
        return (status != null && status == 1) ? "1" : "0";
    }

    private String abbreviate(String msg) {
        if (msg == null) {
            return "未知错误";
        }
        return msg.length() <= MAX_MSG_LENGTH ? msg : msg.substring(0, MAX_MSG_LENGTH);
    }

    /** 用户的唯一机构 + 唯一角色（本地 ID 与中台 ID）。 */
    public static class UserRelation {
        public String deptId;
        public String deptName;
        public String zkDeptId;
        public String roleId;
        public String roleName;
        public String zkRoleId;
    }
}
