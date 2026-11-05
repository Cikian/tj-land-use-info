package org.jeecg.modules.land.map.mvt.service;

import com.google.common.base.Ticker;
import org.jeecg.modules.land.map.mvt.mapper.MvtSourceMapper;
import org.jeecg.modules.land.map.mvt.support.MvtPostgis;
import org.jeecg.modules.land.map.mvt.vo.MvtSource;
import org.jeecg.modules.land.map.mvt.vo.MvtTable;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证可观察的查询次数与刷新一致性，不启动服务，也不连接业务数据库。 */
class MvtServicePerformanceTest {
    private final AtomicLong now = new AtomicLong();
    private final Ticker ticker = new Ticker() { @Override public long read() { return now.get(); } };
    private final MvtSourceMapper mapper = mock(MvtSourceMapper.class);
    private final MvtDataSources connections = mock(MvtDataSources.class);
    private final MvtPostgis pg = mock(MvtPostgis.class);
    private final DataSource ds = mock(DataSource.class);
    private final MvtTable table = table("land");

    private MvtService service() {
        MvtSource source = new MvtSource(); source.setCode("db");
        when(mapper.listPostgres()).thenReturn(Collections.singletonList(source));
        when(connections.get("db")).thenReturn(ds);
        when(pg.discover(ds)).thenReturn(Collections.singletonList(table));
        when(pg.tile(eq(ds), any(), anyInt(), anyInt(), anyInt())).thenReturn(new byte[] {1});
        return new MvtService(mapper, connections, pg, ticker);
    }

    private static MvtTable table(String name) {
        MvtTable table = new MvtTable(); table.setSchema("public"); table.setTable(name);
        table.setGeometry("geom"); table.setAvailable(true); table.setSrid(4326);
        return table;
    }

    private byte[] tile(MvtService service, int x) { return service.tile("db", "public", "land", "geom", 8, x, 97); }

    @Test
    void repeatedAndDistinctTilesAvoidRepeatedSourceLookup() {
        MvtService service = service();
        for (int i = 0; i < 20; i++) { assertArrayEquals(new byte[] {1}, tile(service, 211)); }
        tile(service, 212);
        verify(mapper, times(1)).listPostgres();
        verify(pg, times(1)).discover(ds);
        verify(pg, times(1)).tile(ds, table, 8, 211, 97);
        verify(pg, times(1)).tile(ds, table, 8, 212, 97);
        // 目录一次、两张不同瓦片各一次；命中缓存不再取连接。
        verify(connections, times(3)).get("db");
    }

    @Test
    void sourceAndTileExpireWithoutRecomputingBoundsOrCatalogEveryThirtySeconds() {
        MvtService service = service();
        when(pg.bounds(ds, table)).thenReturn(Arrays.asList(116.0, 38.0, 118.0, 40.0));
        tile(service, 211);
        service.tileJson("db", "public", "land", "geom", "http://one/layer");
        now.addAndGet(TimeUnit.SECONDS.toNanos(31));
        tile(service, 211);
        Map<String, Object> metadata = service.tileJson("db", "public", "land", "geom", "http://two/layer");
        assertEquals(Collections.singletonList("http://two/layer/{z}/{x}/{y}.pbf"), metadata.get("tiles"));
        verify(mapper, times(2)).listPostgres();
        verify(pg, times(2)).tile(ds, table, 8, 211, 97);
        verify(pg, times(1)).discover(ds);
        verify(pg, times(1)).bounds(ds, table);
        now.addAndGet(TimeUnit.MINUTES.toNanos(5));
        tile(service, 211);
        verify(pg, times(2)).discover(ds);
    }

    @Test
    void deletedSourceCannotContinueReturningCachedTilesAfterWhitelistExpiry() {
        MvtService service = service();
        tile(service, 211);
        when(mapper.listPostgres()).thenReturn(Collections.emptyList());
        now.addAndGet(TimeUnit.SECONDS.toNanos(30));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> tile(service, 211)).getStatus().value());
        verify(pg, times(1)).tile(ds, table, 8, 211, 97);
    }

    @Test
    void administratorRefreshReplacesCachedTilesAndRemovedLayersAreRejected() {
        MvtService service = service();
        tile(service, 211);
        when(pg.tile(ds, table, 8, 211, 97)).thenReturn(new byte[] {2});
        service.tables("db", true);
        assertArrayEquals(new byte[] {2}, tile(service, 211));
        when(pg.discover(ds)).thenReturn(Collections.emptyList());
        service.tables("db", true);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> tile(service, 211)).getStatus().value());
    }

    @Test
    void failuresAreRetriedAndEmptyTilesAreCached() {
        MvtService service = service();
        when(pg.tile(ds, table, 8, 211, 97)).thenThrow(new IllegalStateException("temporary")).thenReturn(null);
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> tile(service, 211)).getStatus().value());
        assertEquals(0, tile(service, 211).length);
        assertEquals(0, tile(service, 211).length);
        verify(pg, times(2)).tile(ds, table, 8, 211, 97);
    }

    @Test
    void concurrentRequestsGenerateTheSameTileOnce() throws Exception {
        MvtService service = service();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), ready = new CountDownLatch(8);
        when(pg.tile(ds, table, 8, 211, 97)).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return new byte[] {3};
        });
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Future<byte[]>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(workers.submit(() -> { ready.countDown(); assertTrue(ready.await(5, TimeUnit.SECONDS)); return tile(service, 211); }));
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); release.countDown();
            for (Future<byte[]> result : results) { assertArrayEquals(new byte[] {3}, result.get(5, TimeUnit.SECONDS)); }
            verify(pg, times(1)).tile(ds, table, 8, 211, 97);
            verify(mapper, times(1)).listPostgres();
            verify(pg, times(1)).discover(ds);
        } finally { release.countDown(); workers.shutdownNow(); }
    }

    @Test
    void oldInFlightTileCannotOverwriteRefreshedCatalog() throws Exception {
        MvtService service = service();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(pg.tile(ds, table, 8, 211, 97)).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return new byte[] {1};
        }).thenReturn(new byte[] {2});
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<byte[]> old = worker.submit(() -> tile(service, 211));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            service.tables("db", true);
            assertArrayEquals(new byte[] {2}, tile(service, 211));
            release.countDown();
            assertArrayEquals(new byte[] {1}, old.get(5, TimeUnit.SECONDS));
            assertArrayEquals(new byte[] {2}, tile(service, 211));
            verify(pg, times(2)).tile(ds, table, 8, 211, 97);
        } finally { release.countDown(); worker.shutdownNow(); }
    }

    @Test
    void slowCatalogOfOneDatabaseDoesNotBlockAnotherDatabase() throws Exception {
        MvtService service = service();
        MvtSource first = new MvtSource(); first.setCode("db");
        MvtSource second = new MvtSource(); second.setCode("db2");
        when(mapper.listPostgres()).thenReturn(Arrays.asList(first, second));
        DataSource ds2 = mock(DataSource.class); when(connections.get("db2")).thenReturn(ds2);
        when(pg.discover(ds2)).thenReturn(Collections.singletonList(table));
        when(pg.tile(ds2, table, 8, 211, 97)).thenReturn(new byte[] {2});
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(pg.discover(ds)).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return Collections.singletonList(table);
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<byte[]> slow = workers.submit(() -> tile(service, 211));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<byte[]> fast = workers.submit(() -> service.tile("db2", "public", "land", "geom", 8, 211, 97));
            assertArrayEquals(new byte[] {2}, fast.get(2, TimeUnit.SECONDS));
            release.countDown(); slow.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); workers.shutdownNow(); }
    }
}
