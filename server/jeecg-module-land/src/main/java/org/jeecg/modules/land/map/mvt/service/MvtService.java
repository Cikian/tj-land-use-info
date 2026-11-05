package org.jeecg.modules.land.map.mvt.service;

import lombok.extern.slf4j.Slf4j;
import com.google.common.base.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.jeecg.modules.land.map.mvt.mapper.MvtSourceMapper;
import org.jeecg.modules.land.map.mvt.support.MvtPostgis;
import org.jeecg.modules.land.map.mvt.support.MvtSql;
import org.jeecg.modules.land.map.mvt.support.MvtTileCache;
import org.jeecg.modules.land.map.mvt.vo.MvtSource;
import org.jeecg.modules.land.map.mvt.vo.MvtTable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** 无逐表注册状态：数据库目录就是服务目录，刷新立即重读。 */
@Slf4j
@Service
public class MvtService {
    private static final long SOURCE_TTL = TimeUnit.SECONDS.toNanos(30);
    private static final long CATALOG_TTL = TimeUnit.MINUTES.toNanos(5);
    private static final AtomicLong VERSIONS = new AtomicLong();
    private final MvtSourceMapper sources;
    private final MvtDataSources connections;
    private final MvtPostgis postgis;
    private final Map<String, SourceState> catalogs = new ConcurrentHashMap<>();
    private final Object sourceLock = new Object();
    private final Ticker ticker;
    private final MvtTileCache tiles;
    private volatile SourceSnapshot sourceSnapshot;

    @Autowired
    public MvtService(MvtSourceMapper sources, MvtDataSources connections, MvtPostgis postgis) {
        this(sources, connections, postgis, Ticker.systemTicker());
    }

    MvtService(MvtSourceMapper sources, MvtDataSources connections, MvtPostgis postgis, Ticker ticker) {
        this.sources = sources;
        this.connections = connections;
        this.postgis = postgis;
        this.ticker = ticker;
        this.tiles = new MvtTileCache(ticker);
    }

    public List<MvtSource> sources() { return sourceSnapshot(true).sources; }

    private SourceSnapshot sourceSnapshot(boolean refresh) {
        SourceSnapshot cached = sourceSnapshot;
        if (!refresh && cached != null && ticker.read() - cached.created < SOURCE_TTL) { return cached; }
        synchronized (sourceLock) {
            cached = sourceSnapshot;
            if (!refresh && cached != null && ticker.read() - cached.created < SOURCE_TTL) { return cached; }
            // 探活过滤：并行连接每个数据源（登录超时 5s），连不上的（库已删除/
            // 主机不可达/密码失效）从下拉过滤，避免选中后长时间挂起
            List<MvtSource> list = probeAndFilter(sources.listPostgres());
            Set<String> codes = new HashSet<>();
            list.forEach(source -> codes.add(source.getCode()));
            catalogs.forEach((code, state) -> {
                if (!codes.contains(code) && catalogs.remove(code, state)) {
                    Catalog removed = state.catalog;
                    if (removed != null) { tiles.invalidate(removed.version); }
                }
            });
            connections.retain(codes);
            sourceSnapshot = new SourceSnapshot(list, codes, ticker.read());
            return sourceSnapshot;
        }
    }

    private void requireSource(String code, boolean refresh) {
        if (!sourceSnapshot(refresh).codes.contains(code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PostgreSQL 数据源不存在");
        }
    }

    public List<MvtTable> tables(String code, boolean refresh) {
        // 管理端实时核对源配置；高频公开瓦片请求使用短期白名单，不能逐张查业务库。
        requireSource(code, true);
        return catalog(code, refresh).tables;
    }

    private Catalog catalog(String code, boolean refresh) {
        SourceState state = catalogs.computeIfAbsent(code, ignored -> new SourceState());
        Catalog cached = state.catalog;
        if (!refresh && cached != null && ticker.read() - cached.created < CATALOG_TTL) {
            return cached;
        }
        // 慢库只阻塞自己的目录重建；在途瓦片继续使用自己的目录版本。
        synchronized (state) {
            cached = state.catalog;
            if (!refresh && cached != null && ticker.read() - cached.created < CATALOG_TTL) { return cached; }
            if (cached != null) { tiles.invalidate(cached.version); }
            state.catalog = null;
            try {
                Catalog result = new Catalog(postgis.discover(connections.get(code)), ticker.read());
                state.catalog = result;
                return result;
            } catch (RuntimeException ex) {
                log.warn("MVT 空间目录读取失败，数据源={}，异常类型={}", code, ex.getClass().getSimpleName());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "无法读取空间目录，请检查数据库连接、SELECT 权限，以及是否安装 PostGIS 3 或以上版本");
            }
        }
    }

    private Catalog resolveCatalog(String source) {
        requireSource(source, false);
        return catalog(source, false);
    }

    private static String layerKey(String schema, String name, String geometry) {
        return schema + "\0" + name + "\0" + geometry;
    }

    private MvtTable resolve(Catalog catalog, String key) {
        MvtTable table = catalog.byLayer.get(key);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "空间表或几何列不存在，或当前数据库账号无读取权限");
        }
        return table;
    }

    private void requireAvailable(MvtTable table) {
        if (!table.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, table.getReason());
        }
    }

    public byte[] tile(String source, String schema, String name, String geometry, int z, int x, int y) {
        try { MvtSql.validateTile(z, x, y); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
        Catalog catalog = resolveCatalog(source);
        String layer = layerKey(schema, name, geometry);
        MvtTable table = resolve(catalog, layer);
        requireAvailable(table);
        try {
            String key = catalog.version + "\0" + layer + "\0" + z + "\0" + x + "\0" + y;
            return tiles.get(key, () -> postgis.tile(connections.get(source), table, z, x, y));
        } catch (RuntimeException ex) {
            log.warn("MVT 瓦片生成失败，数据源={}，表={}.{}，异常类型={}", source, schema, name, ex.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "瓦片查询失败或超时，请检查来源几何、坐标系和空间索引");
        }
    }

    public Map<String, Object> tileJson(String source, String schema, String name, String geometry, String baseUrl) {
        Catalog catalog = resolveCatalog(source);
        String key = layerKey(schema, name, geometry);
        MvtTable table = resolve(catalog, key);
        requireAvailable(table);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tilejson", "3.0.0");
        result.put("name", schema + "." + name);
        result.put("scheme", "xyz");
        result.put("format", "pbf");
        result.put("minzoom", 0);
        result.put("maxzoom", 22);
        result.put("tiles", Collections.singletonList(baseUrl + "/{z}/{x}/{y}.pbf"));
        Map<String, Object> layer = new LinkedHashMap<>();
        layer.put("id", name);
        layer.put("fields", table.getFields());
        layer.put("minzoom", 0);
        layer.put("maxzoom", 22);
        result.put("vector_layers", Collections.singletonList(layer));
        // 范围只在请求 TileJSON 时读取；目录刷新不扫描全部要素。
        List<Double> bounds = catalog.bounds.computeIfAbsent(key, ignored -> {
            try { return postgis.bounds(connections.get(source), table); }
            catch (RuntimeException ex) {
                log.warn("MVT 图层范围不可用，数据源={}，表={}.{}", source, schema, name);
                return Collections.emptyList();
            }
        });
        if (!bounds.isEmpty()) {
            result.put("bounds", bounds);
            result.put("center", Arrays.asList((bounds.get(0) + bounds.get(2)) / 2,
                    (bounds.get(1) + bounds.get(3)) / 2, 12));
        }
        return result;
    }

    /**
     * 并行探活：每个数据源尝试直连（不经连接池，避免死池连接挂起），
     * 登录超时 5 秒/个并行执行，总耗时约等于最慢一个。结果随快照缓存 30 秒。
     */
    private List<MvtSource> probeAndFilter(List<MvtSource> list) {
        if (list.size() <= 1) return list;
        DriverManager.setLoginTimeout(5);
        List<MvtSource> alive = list.parallelStream()
                .filter(this::reachable)
                .collect(Collectors.toList());
        if (alive.size() != list.size()) {
            List<String> dead = new ArrayList<>();
            list.forEach(s -> { if (!alive.contains(s)) dead.add(s.getCode()); });
            log.info("[MVT] 数据源探活过滤：{} 个不可达已移除 {}", dead.size(), dead);
        }
        // 敏感字段不下发
        alive.forEach(s -> s.setDbPassword(null));
        return alive;
    }

    private boolean reachable(MvtSource s) {
        // 第一次用存储原值（明文）；失败再试 jiemi 解密值——兼容 jeecg 界面
        // 手动添加时"明文被 add 接口 jiemi 成乱码密文"的记录。
        if (tryConnect(s.getDbUrl(), s.getDbUsername(), s.getDbPassword())) return true;
        try {
            // 与 system 模块 SecurityUtil.jiemi 等价（AES/ECB/PKCS5，key=JEECGBOOT1423670），
            // 该工具类在 system-biz，land 模块依赖不到，故内联同一实现。
            String decrypted = new cn.hutool.crypto.symmetric.SymmetricCrypto(
                    cn.hutool.crypto.symmetric.SymmetricAlgorithm.AES,
                    "JEECGBOOT1423670".getBytes()).decryptStr(s.getDbPassword());
            if (decrypted != null && !decrypted.equals(s.getDbPassword())
                    && tryConnect(s.getDbUrl(), s.getDbUsername(), decrypted)) return true;
        } catch (Exception ignored) { }
        log.info("[MVT] 数据源探活失败：{}", s.getCode());
        return false;
    }

    private boolean tryConnect(String url, String user, String password) {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            return c.isValid(3);
        } catch (Exception e) {
            log.info("[MVT] 连接失败：{} - {}", url, e.getMessage());
            return false;
        }
    }

    private static class SourceSnapshot {
        final List<MvtSource> sources;
        final Set<String> codes;
        final long created;
        SourceSnapshot(List<MvtSource> sources, Set<String> codes, long created) {
            this.sources = sources;
            this.codes = codes;
            this.created = created;
        }
    }

    private static class SourceState {
        volatile Catalog catalog;
    }

    private static class Catalog {
        final long created;
        final long version = VERSIONS.incrementAndGet();
        final List<MvtTable> tables;
        final Map<String, MvtTable> byLayer = new HashMap<>();
        final Map<String, List<Double>> bounds = new ConcurrentHashMap<>();
        Catalog(List<MvtTable> tables, long created) {
            this.created = created;
            this.tables = Collections.unmodifiableList(new ArrayList<>(tables));
            tables.forEach(table -> byLayer.put(layerKey(table.getSchema(), table.getTable(), table.getGeometry()), table));
        }
    }
}
