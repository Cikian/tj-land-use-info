package org.jeecg.modules.system.zk;

import com.stargis.zk.sdk.deptApi.ZkDeptApi;
import com.stargis.zk.sdk.model.ZkDept;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.system.entity.SysDepart;
import org.jeecg.modules.system.mapper.SysDepartMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 机构（{@code sys_depart}）→ 中台机构（{@code estar_department_system}）单向同步服务。
 *
 * <h3>权威源约定</h3>
 * 机构、用户、角色以 <b>jeecg 为权威源</b>：本系统的增删改是"主"，中台是被同步的"投影"。
 * 中台侧机构只有 {@code departmentname / parentid / dporder} 三个真正生效的字段（{@code codekey} 列存在但接口不认），
 * 因此只能承接本系统的一小部分信息（机构名称、上下级、排序、编码映射）。
 *
 * <h3>映射规则</h3>
 * <ul>
 *   <li>机构名称：{@code sys_depart.depart_name} → {@code departmentname}</li>
 *   <li>上级机构：{@code sys_depart.parent_id} → 父机构记录里保存的 {@code zk_dept_id}；
 *       顶级机构（{@code parent_id} 为空或 {@code "0"}）→ 空串（中台用空串表示顶级）</li>
 *   <li>排序：{@code sys_depart.depart_order} → {@code dporder}</li>
 *   <li>映射锚点：<b>只有</b> {@code sys_depart.zk_dept_id}（中台 {@code addEntity} 返回的 ID）</li>
 * </ul>
 *
 * <h3>⚠️ 为什么不用中台的 codekey 做映射键（2026-09 真机实测）</h3>
 * 中台 {@code estar_department_system} 表里确实有 {@code codekey} 列，但通过 REST 接口<b>完全不可用</b>：
 * <ul>
 *   <li>{@code addEntity} 传了 {@code codekey} 也不会写入（实测传 {@code ZZ-CK-INSERT-TEST}，库里仍为 NULL）；</li>
 *   <li>{@code getAllData} 返回的对象里根本没有 {@code codekey}（{@code dporder} 也不返回），
 *       即使直接用 SQL 写入 codekey 也读不出来。</li>
 * </ul>
 * 因此中台侧<b>不存在任何本系统标识</b>：映射只能单向锚定在 {@code zk_dept_id} 上，
 * 对账匹配只能用"机构名称 + 父级"（见 {@link #findByNameAndParent}）。
 *
 * <h3>同步语义</h3>
 * <ul>
 *   <li><b>严格模式</b>（{@code ck.stargis.zk.depart-sync-strict=true}，默认）：
 *       中台调用失败即抛 {@link JeecgBootException}，由调用方的事务整体回滚，
 *       保证"要么两边都成功，要么本地也不落库"，不会出现半同步状态。</li>
 *   <li><b>非严格模式</b>（{@code false}）：中台失败只记录日志并把
 *       {@code zk_sync_status=failed} + {@code zk_sync_msg} 落库，本地操作照常成功，等待后续对账补同步。
 *       适用于中台临时不可用的过渡期。</li>
 *   <li>开关关闭（{@code ck.stargis.zk.depart-sync-enabled=false}，或 SDK 未装配）：
 *       不做任何中台调用，仅把记录标记为 {@code not_synced}。</li>
 * </ul>
 *
 * <h3>幂等</h3>
 * 新增前先在中台全量机构树里按"机构名称 + 父级"反查（{@code codekey} 不可用，见上），
 * 命中则改为<b>更新</b>而不是重复创建，因此对存量数据"补同步"不会造成中台出现重复机构。
 * 该反查只在本地还没有 {@code zk_dept_id} 时发生（正常新增路径不使用它）。
 *
 * <p>注意：本服务只注入 {@link SysDepartMapper}，不注入 {@code ISysDepartService}，
 * 以避免与 {@code SysDepartServiceImpl} 形成循环依赖。
 */
@Slf4j
@Service
public class ZkDepartSyncService {

    /** 机构层级保护：中台/本系统机构链异常（如成环）时及时失败，避免无限递归。 */
    private static final int MAX_DEPTH = 32;

    /** 写入 {@code zk_sync_msg} 的最大长度，与建表脚本 varchar(500) 对齐。 */
    private static final int MAX_MSG_LENGTH = 500;

    /** 中台机构树的顶级父节点标识：空串。 */
    private static final String TOP_PARENT_ID = "";

    @Value("${ck.stargis.zk.depart-sync-enabled:true}")
    private boolean syncEnabled;

    @Value("${ck.stargis.zk.depart-sync-strict:true}")
    private boolean strict;

    /**
     * 用 ObjectProvider 延迟获取：SDK 未启用（{@code ck.stargis.zk.enabled=false}）时
     * 本服务仍然可以装配，只是不做任何同步。
     */
    private final ObjectProvider<ZkDeptApi> zkDeptApiProvider;

    private final SysDepartMapper sysDepartMapper;

    @Autowired
    public ZkDepartSyncService(ObjectProvider<ZkDeptApi> zkDeptApiProvider, SysDepartMapper sysDepartMapper) {
        this.zkDeptApiProvider = zkDeptApiProvider;
        this.sysDepartMapper = sysDepartMapper;
    }

    // ==================================================================
    // 对外入口：由 SysDepartServiceImpl 在事务内调用
    // ==================================================================

    /** 是否真正会执行中台同步（开关打开且 SDK 已装配）。 */
    public boolean isSyncEnabled() {
        return syncEnabled && zkDeptApiProvider.getIfAvailable() != null;
    }

    /**
     * 新增机构后同步到中台。
     *
     * <p>调用时机：{@code SysDepartServiceImpl#saveDepartData} 中 {@code this.save(sysDepart)} <b>之后</b>，
     * 此时机构 ID 与 {@code org_code} 均已生成。
     *
     * <p>若父机构尚未同步过，会自动<b>向上递归补建父级链</b>，保证中台里子机构不会挂到顶级。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncOnCreate(SysDepart depart) {
        if (depart == null || oConvertUtils.isEmpty(depart.getId())) {
            return;
        }
        ZkDeptApi api = zkDeptApiProvider.getIfAvailable();
        if (!syncEnabled || api == null) {
            markNotSynced(depart);
            return;
        }
        try {
            ZkDept remote = ensureRemote(api, depart, new HashSet<>(), 0);
            markSynced(depart, remote);
        } catch (Exception e) {
            handleFailure(depart, "新增", e);
        }
    }

    /**
     * 修改机构后同步到中台。
     *
     * <p>调用时机：{@code SysDepartServiceImpl#updateDepartDataById} 中 {@code this.updateById(sysDepart)} 之后。
     *
     * <p>⚠️ 必须从库里<b>重新加载完整记录</b>再同步：前端 {@code /sys/sysDepart/edit} 提交的
     * {@code SysDepart} 只带页面改过的字段，若直接把它的 {@code parentId}（null）发给中台，
     * 会把机构静默移动成顶级机构。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncOnUpdate(SysDepart depart) {
        if (depart == null || oConvertUtils.isEmpty(depart.getId())) {
            return;
        }
        ZkDeptApi api = zkDeptApiProvider.getIfAvailable();
        if (!syncEnabled || api == null) {
            markNotSynced(depart);
            return;
        }
        // 重新加载：拿到 name/parentId/order/orgCode 的真实值，避免 null 覆盖中台数据
        SysDepart full = sysDepartMapper.selectById(depart.getId());
        if (full == null) {
            log.warn("[zk-sync] 机构 {} 在本地已不存在，跳过中台同步", depart.getId());
            return;
        }
        try {
            ZkDept remote = ensureRemote(api, full, new HashSet<>(), 0);
            markSynced(full, remote);
            copySyncFields(full, depart);
        } catch (Exception e) {
            handleFailure(full, "修改", e);
            copySyncFields(full, depart);
        }
    }

    /**
     * 删除机构前，先在中台删除对应机构（含被级联删除的子机构）。
     *
     * <p>调用时机：{@code SysDepartServiceImpl#delete} / {@code deleteBatchWithChildren}
     * 中 {@code checkChildrenExists} 收集完 ID、{@code removeByIds} <b>之前</b>。
     *
     * <p>中台是<b>物理删除</b>且不校验有无子机构/用户，因此这里按"先子后父"的顺序逐个删除，
     * 避免父机构先被删掉导致子机构在中台树上变成孤立节点。
     *
     * <p>删除阶段不再区分严格模式：严格模式下失败直接抛出（本地不删，两边保持一致）；
     * 非严格模式下只记录日志并按原逻辑删除本地数据，中台残留由后续对账处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncOnDelete(List<String> departIds) {
        ZkDeptApi api = zkDeptApiProvider.getIfAvailable();
        if (!syncEnabled || api == null || departIds == null || departIds.isEmpty()) {
            return;
        }
        List<SysDepart> departs = new ArrayList<>(sysDepartMapper.selectBatchIds(departIds));
        // 逆序：checkChildrenExists 是前序遍历，逆序后近似"先子后父"
        Collections.reverse(departs);
        for (SysDepart depart : departs) {
            if (oConvertUtils.isEmpty(depart.getZkDeptId())) {
                continue;
            }
            try {
                api.deleteById(depart.getZkDeptId());
                log.info("[zk-sync] 已删除中台机构【{}】zkDeptId={}", depart.getDepartName(), depart.getZkDeptId());
            } catch (Exception e) {
                log.error("[zk-sync] 删除中台机构【{}】zkDeptId={} 失败：{}",
                        depart.getDepartName(), depart.getZkDeptId(), e.getMessage(), e);
                if (strict) {
                    throw new JeecgBootException("删除中台机构【" + depart.getDepartName() + "】失败，操作已回滚："
                            + e.getMessage(), e);
                }
            }
        }
    }

    // ==================================================================
    // 内部实现
    // ==================================================================

    /**
     * 确保某机构在中台存在且内容与本地一致，返回中台侧的机构对象（含中台 ID）。
     *
     * <p>三种情形：
     * <ol>
     *   <li>本地已有 {@code zk_dept_id} → 直接更新中台该机构；</li>
     *   <li>本地无 {@code zk_dept_id}，但中台已有<b>同名同父级</b>的机构（存量补同步）→ 复用并更新，不重复创建；</li>
     *   <li>中台确实没有 → 新增，并用返回的 ID 建立映射。</li>
     * </ol>
     */
    private ZkDept ensureRemote(ZkDeptApi api, SysDepart depart, Set<String> visited, int depth) {
        if (depth > MAX_DEPTH || !visited.add(depart.getId())) {
            throw new JeecgBootException("机构层级异常（超过 " + MAX_DEPTH + " 层或存在循环引用），已终止中台同步："
                    + depart.getDepartName());
        }
        String parentZkId = resolveParentZkId(api, depart, visited, depth);

        // 1) 找中台已有的对应机构
        ZkDept remote;
        if (oConvertUtils.isNotEmpty(depart.getZkDeptId())) {
            remote = new ZkDept();
            remote.setId(depart.getZkDeptId());
        } else {
            remote = findByNameAndParent(api, depart.getDepartName(), parentZkId);
            if (remote != null) {
                log.warn("[zk-sync] 机构【{}】本地无 zk_dept_id，按“名称+父级”在中台匹配到已有机构 id={}，"
                                + "将复用（请确认是否为同一机构，中台侧没有可用的编码字段）",
                        depart.getDepartName(), remote.getId());
            }
        }

        // 2) 更新
        if (remote != null && oConvertUtils.isNotEmpty(remote.getId())) {
            remote.setDepartmentname(depart.getDepartName());
            remote.setParentid(parentZkId);
            remote.setDporder(depart.getDepartOrder());
            ZkDept updated = api.update(remote);
            // 中台 updateEntity 若不回传实体，用请求对象兜底（ID 已经知道）
            return updated != null && oConvertUtils.isNotEmpty(updated.getId()) ? updated : remote;
        }

        // 3) 新增
        ZkDept request = new ZkDept();
        request.setDepartmentname(depart.getDepartName());
        request.setParentid(parentZkId);
        request.setDporder(depart.getDepartOrder());
        ZkDept created = api.add(request);
        if (created == null || oConvertUtils.isEmpty(created.getId())) {
            // 中台新增接口未回传实体时，按“名称+父级”反查一次，避免"创建成功但本地存不上 ID"
            created = findByNameAndParent(api, depart.getDepartName(), parentZkId);
        }
        if (created == null || oConvertUtils.isEmpty(created.getId())) {
            throw new JeecgBootException("中台未返回机构 ID，无法建立映射。机构：" + depart.getDepartName());
        }
        return created;
    }

    /** 解析中台父机构 ID；父机构未同步时先递归补建，保证层级完整。 */
    private String resolveParentZkId(ZkDeptApi api, SysDepart depart, Set<String> visited, int depth) {
        String parentId = depart.getParentId();
        if (oConvertUtils.isEmpty(parentId) || "0".equals(parentId.trim())) {
            return TOP_PARENT_ID;
        }
        SysDepart parent = sysDepartMapper.selectById(parentId);
        if (parent == null) {
            log.warn("[zk-sync] 机构【{}】的父机构 {} 在本地不存在，按顶级机构同步", depart.getDepartName(), parentId);
            return TOP_PARENT_ID;
        }
        if (oConvertUtils.isNotEmpty(parent.getZkDeptId())) {
            return parent.getZkDeptId();
        }
        log.info("[zk-sync] 父机构【{}】尚未同步，先补建父级链", parent.getDepartName());
        ZkDept parentRemote = ensureRemote(api, parent, visited, depth + 1);
        markSynced(parent, parentRemote);
        return parentRemote.getId();
    }

    /**
     * 按"机构名称 + 父级"在中台<b>全量</b>机构树里反查机构（{@code getData} 只有当前账号视角，不能用）。
     *
     * <p>中台侧没有可用的映射编码（{@code codekey} 不生效、也不返回），因此这是唯一的兜底匹配方式，
     * <b>只在本地还没有 {@code zk_dept_id} 时使用</b>；命中时会打 WARN 日志供人工核对。
     *
     * <p>失败不致命：反查只是为了幂等，异常时按新建处理。
     */
    private ZkDept findByNameAndParent(ZkDeptApi api, String departName, String parentZkId) {
        if (oConvertUtils.isEmpty(departName)) {
            return null;
        }
        try {
            String parent = parentZkId == null ? TOP_PARENT_ID : parentZkId;
            return flatten(api.getAllData()).stream()
                    .filter(d -> departName.equals(d.getDepartmentname()))
                    .filter(d -> parent.equals(d.getParentid() == null ? TOP_PARENT_ID : d.getParentid()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[zk-sync] 在中台反查机构【{}】失败，将按新增处理：{}", departName, e.getMessage());
            return null;
        }
    }

    private List<ZkDept> flatten(List<ZkDept> tree) {
        List<ZkDept> all = new ArrayList<>();
        if (tree == null) {
            return all;
        }
        for (ZkDept node : tree) {
            all.add(node);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                all.addAll(flatten(node.getChildren()));
            }
        }
        return all;
    }

    private void markSynced(SysDepart depart, ZkDept remote) {
        depart.setZkDeptId(remote.getId());
        depart.setZkSyncStatus(ZkSyncStatus.SYNCED);
        depart.setZkSyncTime(new Date());
        // 用空串而不是 null：MyBatis-Plus 默认更新策略会忽略 null，置空串才能真正清掉上次的失败原因
        depart.setZkSyncMsg("");
        persist(depart);
    }

    private void markNotSynced(SysDepart depart) {
        depart.setZkSyncStatus(ZkSyncStatus.NOT_SYNCED);
        depart.setZkSyncMsg("");
        persist(depart);
    }

    /** 中台调用失败的统一处理：记录状态，严格模式下抛出以触发整体回滚。 */
    private void handleFailure(SysDepart depart, String action, Exception e) {
        String msg = abbreviate(e.getMessage());
        depart.setZkSyncStatus(ZkSyncStatus.FAILED);
        depart.setZkSyncTime(new Date());
        depart.setZkSyncMsg(msg);
        persist(depart);
        log.error("[zk-sync] 机构【{}】(orgCode={}) {} 同步到中台失败：{}",
                depart.getDepartName(), depart.getOrgCode(), action, e.getMessage(), e);
        if (strict) {
            throw new JeecgBootException("机构【" + depart.getDepartName() + "】同步到中台失败，操作已回滚："
                    + e.getMessage(), e);
        }
    }

    /** 只更新同步相关的列，避免整行覆盖（MyBatis-Plus 默认忽略 null 字段）。 */
    private void persist(SysDepart depart) {
        try {
            sysDepartMapper.updateById(depart);
        } catch (Exception e) {
            log.error("[zk-sync] 回写机构 {} 的中台同步状态失败：{}", depart.getId(), e.getMessage(), e);
        }
    }

    private void copySyncFields(SysDepart from, SysDepart to) {
        if (to == null || to == from) {
            return;
        }
        to.setZkDeptId(from.getZkDeptId());
        to.setZkSyncStatus(from.getZkSyncStatus());
        to.setZkSyncTime(from.getZkSyncTime());
        to.setZkSyncMsg(from.getZkSyncMsg());
    }

    private String abbreviate(String msg) {
        if (msg == null) {
            return "未知错误";
        }
        return msg.length() <= MAX_MSG_LENGTH ? msg : msg.substring(0, MAX_MSG_LENGTH);
    }
}
