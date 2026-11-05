package org.jeecg.modules.land.map.mvt.service;

import com.alibaba.druid.pool.DruidDataSource;
import org.jeecg.common.util.dynamic.db.DynamicDBUtil;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/** 首次建池串行，后续连接由系统的多数据源池复用。 */
@Component
public class MvtDataSources {
    private final ConcurrentMap<String, DruidDataSource> pools = new ConcurrentHashMap<>();
    private final Function<String, DruidDataSource> loader;

    public MvtDataSources() { this(DynamicDBUtil::getDbSourceByDbKey); }

    MvtDataSources(Function<String, DruidDataSource> loader) { this.loader = loader; }

    public DataSource get(String code) {
        DruidDataSource cached = pools.get(code);
        if (usable(cached)) { return cached; }
        // 框架建池不是线程安全的；只在首次建池/原池被配置管理关闭时串行，正常请求不访问 Redis。
        synchronized (this) {
            cached = pools.get(code);
            if (!usable(cached)) {
                cached = loader.apply(code);
                pools.put(code, cached);
            }
            return cached;
        }
    }

    public void retain(Set<String> codes) {
        // 连接池归框架所有，目录失效只丢引用，不关闭其他业务可能正在使用的池。
        pools.keySet().retainAll(codes);
    }

    private boolean usable(DruidDataSource pool) {
        return pool != null && !pool.isClosed() && pool.isEnable();
    }
}
