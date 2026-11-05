package org.jeecg.modules.land.map.mvt.support;

import com.google.common.base.Ticker;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class MvtTileCacheTest {
    private final AtomicLong now = new AtomicLong();
    private final Ticker ticker = new Ticker() { @Override public long read() { return now.get(); } };

    @Test
    void emptyTilesAlsoHaveAnEntryLimitAndExpiredEntriesReload() {
        MvtTileCache cache = new MvtTileCache(ticker, 32 * 1024);
        AtomicInteger loads = new AtomicInteger();
        cache.get("1", () -> { loads.incrementAndGet(); return null; });
        cache.get("2", () -> null); cache.get("3", () -> null);
        cache.get("1", () -> { loads.incrementAndGet(); return null; });
        assertEquals(2, loads.get(), "空瓦片也必须淘汰");
        cache.get("1", () -> { loads.incrementAndGet(); return null; });
        assertEquals(2, loads.get());
        now.addAndGet(TimeUnit.SECONDS.toNanos(30));
        cache.get("1", () -> { loads.incrementAndGet(); return null; });
        assertEquals(3, loads.get());
    }

    @Test
    void oversizedTileIsReturnedButNotRetainedAndCatalogInvalidationIsIsolated() {
        MvtTileCache cache = new MvtTileCache(ticker, 32 * 1024);
        AtomicInteger loads = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            assertEquals(40 * 1024, cache.get("large", () -> { loads.incrementAndGet(); return new byte[40 * 1024]; }).length);
        }
        assertEquals(2, loads.get());
        cache.get("1\0land", () -> new byte[] {1}); cache.get("2\0land", () -> new byte[] {2});
        cache.invalidate(1);
        assertArrayEquals(new byte[] {3}, cache.get("1\0land", () -> new byte[] {3}));
        assertArrayEquals(new byte[] {2}, cache.get("2\0land", () -> new byte[] {4}));
    }
}
