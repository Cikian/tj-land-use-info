package org.jeecg.modules.system.zk;

/**
 * 中台（ZK-SERVER）同步状态常量。
 *
 * <p>对应 {@code sys_depart.zk_sync_status}、{@code sys_user.zk_sync_status}。
 */
public final class ZkSyncStatus {

    /** 未同步：本地存在、中台还没有（存量数据初始化值，或同步开关关闭时的落库值）。 */
    public static final String NOT_SYNCED = "not_synced";

    /** 已同步：本地记录与中台记录一一对应。 */
    public static final String SYNCED = "synced";

    /** 同步失败：非严格模式下中台调用失败，本地保留并记录原因，等待对账/重试。 */
    public static final String FAILED = "failed";

    private ZkSyncStatus() {
    }
}
