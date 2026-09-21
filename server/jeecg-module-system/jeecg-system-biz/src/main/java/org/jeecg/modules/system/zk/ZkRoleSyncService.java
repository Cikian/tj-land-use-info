package org.jeecg.modules.system.zk;

import com.stargis.zk.sdk.model.ZkPage;
import com.stargis.zk.sdk.model.ZkWorkpost;
import com.stargis.zk.sdk.workpostApi.ZkWorkpostApi;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.mapper.SysRoleMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色（{@code sys_role}）→ 中台角色（{@code estar_workpost_system}）单向同步服务。
 *
 * <p>中台把“角色”叫“岗位”（workpost），{@code postkey} 同时充当权限字符，
 * 中台登录响应里的 {@code roleKey} 就是它。
 *
 * <h3>权威源与同步范围</h3>
 * 角色以 <b>jeecg 为权威源</b>：本系统的新增/修改/删除单向同步到中台。
 * <b>只同步角色本身的三个字段</b>（名称 / 权限字符 / 备注），
 * <b>不涉及菜单权限</b>——即不向中台下发 {@code menuid}，也不读取中台的菜单授权。
 * 这一点有实测支撑：中台 {@code updateEntity} 在 {@code menuid} 为空时
 * <b>不会清空</b>该角色已有的菜单授权，因此本同步不会破坏中台侧原有的菜单配置。
 *
 * <h3>字段映射</h3>
 * <table border="1">
 *   <tr><th>jeecg {@code sys_role}</th><th>中台 {@code estar_workpost_system}</th><th>说明</th></tr>
 *   <tr><td>{@code role_name}</td><td>{@code postname}</td><td>角色名称，中台 varchar(100)</td></tr>
 *   <tr><td>{@code role_code}</td><td>{@code postkey}</td><td>权限字符，中台应用层唯一，varchar(50)</td></tr>
 *   <tr><td>{@code description}</td><td>{@code postdesc}</td><td>备注，中台是 text</td></tr>
 *   <tr><td>—</td><td>{@code id}</td><td>中台生成，回写 {@code zk_role_id}</td></tr>
 *   <tr><td>—</td><td>{@code menuid} / {@code menuList}</td><td><b>不同步</b>（菜单权限不在本次范围）</td></tr>
 * </table>
 *
 * <h3>校验（在调用中台之前完成，且不受严格模式影响）</h3>
 * 中台侧字段比本系统短，超长/为空会导致中台侧永远建不出来，因此这类**数据问题一律直接报错**，
 * 由使用者在页面改正，而不是悄悄落一个“永远同步不了”的角色：
 * <ul>
 *   <li>{@code role_code} 不能为空（中台 {@code postkey} 必填）；</li>
 *   <li>{@code role_code} 长度 ≤ 50（中台 {@code postkey} varchar(50)）；</li>
 *   <li>{@code role_name} 长度 ≤ 100（中台 {@code postname} varchar(100)）。</li>
 * </ul>
 *
 * <h3>失败语义</h3>
 * 与机构同步保持一致：
 * <ul>
 *   <li>{@code ck.stargis.zk.role-sync-strict=true}（默认）：中台调用失败即抛
 *       {@link JeecgBootException}，本地事务整体回滚，两边强一致；</li>
 *   <li>{@code false}：只记日志并把 {@code zk_sync_status=failed} + {@code zk_sync_msg} 落库，
 *       本地操作照常成功；</li>
 *   <li>开关关闭（或 SDK 未装配）：不做中台调用，仅标记 {@code not_synced}。</li>
 * </ul>
 *
 * <h3>幂等与冲突</h3>
 * 新增/修改前先按 {@code postkey}（= 本系统 {@code role_code}）在中台<b>预查</b>：
 * <ul>
 *   <li>精确命中且就是本角色 → 正常更新；</li>
 *   <li>精确命中但属于<b>中台另一个角色</b> → 直接报冲突并阻断：中台 {@code postkey} 没有唯一索引，
 *       继续同步会在中台造出两个相同权限字符的角色，导致中台按角色查菜单错乱；</li>
 *   <li>只命中“大小写不同”的角色 → 直接报冲突并阻断。中台 {@code postkey} 列排序规则是
 *       {@code utf8_general_ci}，其判重忽略大小写（实测 {@code Normal} 与 {@code normal} 视为重复），
 *       若按“复用”处理就会把中台已有角色（可能是中台内置角色）改名，属于静默篡改中台数据；</li>
 *   <li>未命中且本地无 {@code zk_role_id} → 复用/新增（存量补同步不会造重复）。</li>
 * </ul>
 *
 * <p>本服务只注入 {@link SysRoleMapper}，避免与 {@code SysRoleServiceImpl} 形成循环依赖。
 */
@Slf4j
@Service
public class ZkRoleSyncService {

    /** 中台 {@code postkey} varchar(50)。 */
    private static final int MAX_POSTKEY_LENGTH = 50;

    /** 中台 {@code postname} varchar(100)。 */
    private static final int MAX_POSTNAME_LENGTH = 100;

    /** 写入 {@code zk_sync_msg} 的最大长度，与建表脚本 varchar(500) 对齐。 */
    private static final int MAX_MSG_LENGTH = 500;

    @Value("${ck.stargis.zk.role-sync-enabled:true}")
    private boolean syncEnabled;

    @Value("${ck.stargis.zk.role-sync-strict:true}")
    private boolean strict;

    /**
     * 删除时受保护的中台角色权限字符（逗号分隔，忽略大小写）。
     *
     * <p>中台自带几个内置角色（{@code superAdmin/admin/normal/developer}），
     * 其中 {@code admin} 正是中台专用对接账号所持有的角色——一旦被本系统的删除操作带下去，
     * 中台侧登录/鉴权会立刻出问题。因此这些角色在中台侧<b>不会被删除</b>，只记 WARN 日志。
     */
    @Value("${ck.stargis.zk.role-delete-protected-keys:superAdmin,admin,normal,developer}")
    private String protectedKeys;

    /**
     * 用 ObjectProvider 延迟获取：SDK 未启用（{@code ck.stargis.zk.enabled=false}）时
     * 本服务仍可装配，只是不做任何同步。
     */
    private final ObjectProvider<ZkWorkpostApi> zkWorkpostApiProvider;

    private final SysRoleMapper sysRoleMapper;

    @Autowired
    public ZkRoleSyncService(ObjectProvider<ZkWorkpostApi> zkWorkpostApiProvider, SysRoleMapper sysRoleMapper) {
        this.zkWorkpostApiProvider = zkWorkpostApiProvider;
        this.sysRoleMapper = sysRoleMapper;
    }

    // ==================================================================
    // 对外入口：由 SysRoleServiceImpl 在事务内调用
    // ==================================================================

    /** 是否真正会执行中台同步（开关打开且 SDK 已装配）。 */
    public boolean isSyncEnabled() {
        return syncEnabled && zkWorkpostApiProvider.getIfAvailable() != null;
    }

    /**
     * 新增角色后同步到中台。
     *
     * <p>调用时机：{@code SysRoleServiceImpl#saveRoleWithZkSync} 中 {@code this.save(role)} 之后。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncOnCreate(SysRole role) {
        if (role == null || oConvertUtils.isEmpty(role.getId())) {
            return;
        }
        // 数据校验放在开关判断之前：角色编码/名称不符合中台字段约束时，任何模式下都应直接报错
        validate(role);
        ZkWorkpostApi api = zkWorkpostApiProvider.getIfAvailable();
        if (!syncEnabled || api == null) {
            markNotSynced(role);
            return;
        }
        // 预查中台同 postkey 的角色：既用于存量角色复用，也用于拦截“权限字符被别的中台角色占用”
        ZkWorkpost matched = resolveRemoteMatch(api, role);
        ZkWorkpost existing = oConvertUtils.isEmpty(role.getZkRoleId()) ? matched : null;
        try {
            ZkWorkpost remote = ensureRemote(api, role, existing);
            markSynced(role, remote);
        } catch (Exception e) {
            handleFailure(role, "新增", e);
        }
    }

    /**
     * 修改角色后同步到中台。
     *
     * <p>调用时机：{@code SysRoleServiceImpl#updateRoleWithZkSync} 中 {@code this.updateById(role)} 之后。
     *
     * <p>会先从库里重新加载完整记录：前端提交的 {@link SysRole} 可能只带改过的字段，
     * 而中台的 {@code updateEntity} 是整体覆盖，用残缺对象同步会把中台角色名/权限字符清空。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncOnUpdate(SysRole role) {
        if (role == null || oConvertUtils.isEmpty(role.getId())) {
            return;
        }
        SysRole full = sysRoleMapper.selectById(role.getId());
        if (full == null) {
            log.warn("[zk-sync] 角色 {} 在本地已不存在，跳过中台同步", role.getId());
            return;
        }
        validate(full);
        ZkWorkpostApi api = zkWorkpostApiProvider.getIfAvailable();
        if (!syncEnabled || api == null) {
            markNotSynced(full);
            copySyncFields(full, role);
            return;
        }
        ZkWorkpost matched = resolveRemoteMatch(api, full);
        ZkWorkpost existing = oConvertUtils.isEmpty(full.getZkRoleId()) ? matched : null;
        try {
            ZkWorkpost remote = ensureRemote(api, full, existing);
            markSynced(full, remote);
        } catch (Exception e) {
            handleFailure(full, "修改", e);
        }
        copySyncFields(full, role);
    }

    /**
     * 删除角色前，先在中台删除对应角色（批量）。
     *
     * <p>调用时机：{@code SysRoleServiceImpl#deleteRole} / {@code deleteBatchRole}
     * 中 {@code removeById} <b>之前</b>。
     *
     * <p>中台 {@code deleteBetch} 会级联清理该角色的菜单授权与用户关联，且为物理删除。
     * 处于 {@code role-delete-protected-keys} 名单里的中台内置角色<b>不会被删除</b>（只记 WARN）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncOnDelete(List<String> roleIds) {
        ZkWorkpostApi api = zkWorkpostApiProvider.getIfAvailable();
        if (!syncEnabled || api == null || roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<SysRole> roles = new ArrayList<>(sysRoleMapper.selectBatchIds(roleIds));
        Set<String> protectedSet = protectedKeySet();
        List<String> zkRoleIds = new ArrayList<>();
        for (SysRole role : roles) {
            if (oConvertUtils.isEmpty(role.getZkRoleId())) {
                continue;
            }
            if (protectedSet.contains(lower(role.getRoleCode()))) {
                log.warn("[zk-sync] 角色【{}】(roleCode={}) 对应中台内置角色（受保护名单），"
                                + "已跳过删除中台角色 zkRoleId={}，请勿在中台删除该角色",
                        role.getRoleName(), role.getRoleCode(), role.getZkRoleId());
                continue;
            }
            zkRoleIds.add(role.getZkRoleId());
        }
        if (zkRoleIds.isEmpty()) {
            return;
        }
        try {
            api.deleteBatch(zkRoleIds);
            log.info("[zk-sync] 已删除中台角色 {} 个：{}", zkRoleIds.size(), zkRoleIds);
        } catch (Exception e) {
            log.error("[zk-sync] 删除中台角色失败：zkRoleIds={}，原因：{}", zkRoleIds, e.getMessage(), e);
            if (strict) {
                throw new JeecgBootException("删除中台角色失败，操作已回滚：" + e.getMessage(), e);
            }
        }
    }

    // ==================================================================
    // 内部实现
    // ==================================================================

    /**
     * 校验本系统角色能否映射到中台角色。
     *
     * <p>这类失败属于数据问题（中台字段更短/必填），无论严格模式与否都应直接阻断，
     * 避免在本地留下“永远同步不了”的角色。
     */
    private void validate(SysRole role) {
        if (oConvertUtils.isEmpty(role.getRoleCode())) {
            throw new JeecgBootException("角色编码不能为空：中台用角色编码作为权限字符(postkey)，且必填");
        }
        if (role.getRoleCode().length() > MAX_POSTKEY_LENGTH) {
            throw new JeecgBootException("角色编码长度 " + role.getRoleCode().length()
                    + " 超过中台上限 " + MAX_POSTKEY_LENGTH + " 个字符，请改短后再保存：中台 postkey varchar(50)");
        }
        if (oConvertUtils.isNotEmpty(role.getRoleName()) && role.getRoleName().length() > MAX_POSTNAME_LENGTH) {
            throw new JeecgBootException("角色名称长度 " + role.getRoleName().length()
                    + " 超过中台上限 " + MAX_POSTNAME_LENGTH + " 个字符，请改短后再保存：中台 postname varchar(100)");
        }
    }

    /**
     * 确保该角色在中台存在且内容与本地一致，返回中台侧角色对象（含中台 ID）。
     *
     * <ol>
     *   <li>本地已有 {@code zk_role_id} → 直接更新中台该角色；</li>
     *   <li>本地无 {@code zk_role_id}，但预查在中台找到了同 {@code postkey} 的角色（存量补同步）→ 复用并更新；</li>
     *   <li>中台确实没有 → 新增。</li>
     * </ol>
     *
     * <p>全程<b>不传 {@code menuid}</b>：既不改动中台已有菜单授权，也不会给新角色授权菜单。
     */
    private ZkWorkpost ensureRemote(ZkWorkpostApi api, SysRole role, ZkWorkpost existing) {
        ZkWorkpost remote;
        if (oConvertUtils.isNotEmpty(role.getZkRoleId())) {
            remote = new ZkWorkpost();
            remote.setId(role.getZkRoleId());
        } else {
            remote = existing;
            if (remote != null) {
                log.warn("[zk-sync] 角色【{}】本地无 zk_role_id，按权限字符 postkey={} 在中台匹配到已有角色 id={}，"
                                + "将复用并更新其名称/备注（中台菜单授权不受影响；请确认是否为同一角色）",
                        role.getRoleName(), role.getRoleCode(), remote.getId());
            }
        }

        ZkWorkpost request = new ZkWorkpost();
        request.setPostname(role.getRoleName());
        request.setPostkey(role.getRoleCode());
        request.setPostdesc(role.getDescription());

        if (remote != null && oConvertUtils.isNotEmpty(remote.getId())) {
            request.setId(remote.getId());
            ZkWorkpost updated = api.update(request);
            return updated != null && oConvertUtils.isNotEmpty(updated.getId()) ? updated : request;
        }

        ZkWorkpost created = api.add(request);
        if (created == null || oConvertUtils.isEmpty(created.getId())) {
            // 中台未回传实体时，按 postkey 反查一次，避免“建成功但本地存不上 ID”
            created = reloadFromZk(api, role.getRoleCode());
        }
        if (created == null || oConvertUtils.isEmpty(created.getId())) {
            throw new JeecgBootException("中台未返回角色 ID，无法建立映射。角色：" + role.getRoleName()
                    + "，postkey=" + role.getRoleCode());
        }
        return created;
    }

    /**
     * 按权限字符在中台<b>预查</b>角色，既用于存量补同步的幂等复用，也用于拦截冲突。
     *
     * <p>结果与判定：
     * <ul>
     *   <li><b>精确命中且就是本角色</b>（中台 id 等于 {@code zk_role_id}）→ 返回该角色，正常更新；</li>
     *   <li><b>精确命中但中台 id 不是本角色</b> → 抛 {@link JeecgBootException}：
     *       该权限字符已被中台另一个角色占用，继续同步会在中台造出两个相同 postkey 的角色
     *       （中台没有唯一索引，不会报错，但中台自身按角色查菜单会错乱）；</li>
     *   <li><b>只有大小写不同</b> → 抛 {@link JeecgBootException}：中台 {@code postkey} 列排序规则是
     *       {@code utf8_general_ci}，判重<b>忽略大小写</b>（实测 {@code Normal} 与已有的 {@code normal}
     *       被判为重复，返回 {@code code=20001}）。此时若按“复用”处理，就会把中台已有角色
     *       （很可能是中台内置角色）改名，属于静默篡改中台数据；</li>
     *   <li><b>未命中</b> → 返回 null。本地无 {@code zk_role_id} 时调用方走新增，有则按 id 更新。</li>
     * </ul>
     *
     * <p>注意：只有“预查本身成功”才会判定冲突。若中台不可达等导致预查失败，这里只记 WARN 并返回 null，
     * 让后续新增/更新按正常的严格/非严格语义处理，避免破坏“非严格模式下本地照常成功”的约定。
     */
    private ZkWorkpost resolveRemoteMatch(ZkWorkpostApi api, SysRole role) {
        String postkey = role.getRoleCode();
        if (oConvertUtils.isEmpty(postkey)) {
            return null;
        }
        List<ZkWorkpost> list;
        try {
            ZkPage<ZkWorkpost> page = api.getDataList(null, postkey, null, null, 1, 50);
            list = page == null ? null : page.getData();
        } catch (Exception e) {
            log.warn("[zk-sync] 在中台预查角色 postkey={} 失败，按“中台不存在该角色”继续：{}", postkey, e.getMessage());
            return null;
        }
        if (list == null || list.isEmpty()) {
            return null;
        }
        ZkWorkpost exact = null;
        ZkWorkpost caseDiff = null;
        for (ZkWorkpost w : list) {
            String zkPostkey = w.getPostkey();
            if (zkPostkey == null) {
                continue;
            }
            if (zkPostkey.equals(postkey)) {
                exact = w;
            } else if (zkPostkey.equalsIgnoreCase(postkey)) {
                caseDiff = w;
            }
        }
        if (exact != null) {
            if (oConvertUtils.isNotEmpty(role.getZkRoleId()) && !exact.getId().equals(role.getZkRoleId())) {
                throw new JeecgBootException("中台已存在权限字符为【" + postkey + "】的其它角色"
                        + "（中台 id=" + exact.getId() + "，本角色 zk_role_id=" + role.getZkRoleId() + "），"
                        + "继续同步会在中台产生重复权限字符，请改用其它角色编码");
            }
            return exact;
        }
        if (caseDiff != null) {
            throw new JeecgBootException("中台已存在权限字符为【" + caseDiff.getPostkey() + "】的角色"
                    + "（与本系统角色编码【" + postkey + "】仅大小写不同），中台判重忽略大小写，"
                    + "继续同步会覆盖中台已有角色，请改用其它角色编码");
        }
        return null;
    }

    /** 新增后中台未回传实体时，按 postkey 精确反查一次。 */
    private ZkWorkpost reloadFromZk(ZkWorkpostApi api, String postkey) {
        if (oConvertUtils.isEmpty(postkey)) {
            return null;
        }
        try {
            ZkPage<ZkWorkpost> page = api.getDataList(null, postkey, null, null, 1, 50);
            List<ZkWorkpost> list = page == null ? null : page.getData();
            if (list == null) {
                return null;
            }
            return list.stream()
                    .filter(w -> postkey.equals(w.getPostkey()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[zk-sync] 新增后按 postkey={} 反查中台角色失败：{}", postkey, e.getMessage());
            return null;
        }
    }

    private void markSynced(SysRole role, ZkWorkpost remote) {
        role.setZkRoleId(remote.getId());
        role.setZkSyncStatus(ZkSyncStatus.SYNCED);
        role.setZkSyncTime(new Date());
        // 用空串而不是 null：MyBatis-Plus 默认更新策略会忽略 null，置空串才能真正清掉上次的失败原因
        role.setZkSyncMsg("");
        persist(role);
    }

    private void markNotSynced(SysRole role) {
        role.setZkSyncStatus(ZkSyncStatus.NOT_SYNCED);
        role.setZkSyncMsg("");
        persist(role);
    }

    /** 中台调用失败的统一处理：记录状态，严格模式下抛出以触发整体回滚。 */
    private void handleFailure(SysRole role, String action, Exception e) {
        role.setZkSyncStatus(ZkSyncStatus.FAILED);
        role.setZkSyncTime(new Date());
        role.setZkSyncMsg(abbreviate(e.getMessage()));
        persist(role);
        log.error("[zk-sync] 角色【{}】(roleCode={}) {} 同步到中台失败：{}",
                role.getRoleName(), role.getRoleCode(), action, e.getMessage(), e);
        if (strict) {
            throw new JeecgBootException("角色【" + role.getRoleName() + "】同步到中台失败，操作已回滚："
                    + e.getMessage(), e);
        }
    }

    /** 只更新同步相关的列，避免整行覆盖（MyBatis-Plus 默认忽略 null 字段）。 */
    private void persist(SysRole role) {
        try {
            sysRoleMapper.updateById(role);
        } catch (Exception e) {
            log.error("[zk-sync] 回写角色 {} 的中台同步状态失败：{}", role.getId(), e.getMessage(), e);
        }
    }

    private void copySyncFields(SysRole from, SysRole to) {
        if (to == null || to == from) {
            return;
        }
        to.setZkRoleId(from.getZkRoleId());
        to.setZkSyncStatus(from.getZkSyncStatus());
        to.setZkSyncTime(from.getZkSyncTime());
        to.setZkSyncMsg(from.getZkSyncMsg());
    }

    private Set<String> protectedKeySet() {
        if (oConvertUtils.isEmpty(protectedKeys)) {
            return Collections.emptySet();
        }
        return Arrays.stream(protectedKeys.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::lower)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    private String abbreviate(String msg) {
        if (msg == null) {
            return "未知错误";
        }
        return msg.length() <= MAX_MSG_LENGTH ? msg : msg.substring(0, MAX_MSG_LENGTH);
    }
}
