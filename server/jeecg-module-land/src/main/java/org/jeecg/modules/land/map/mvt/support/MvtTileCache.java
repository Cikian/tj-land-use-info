package org.jeecg.modules.land.map.mvt.support;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** 同瓦片并发只生成一次；为海量空瓦片也计入权重，避免只有字节上限却无限增长。 */
public final class MvtTileCache {
    private static final byte[] EMPTY = new byte[0];
    private final Cache<String, byte[]> tiles;

    public MvtTileCache(Ticker ticker) {
        this(ticker, 64L * 1024 * 1024);
    }

    MvtTileCache(Ticker ticker, long capacity) {
        tiles = CacheBuilder.newBuilder().concurrencyLevel(1)
                .maximumWeight(capacity)
                .weigher((String key, byte[] value) -> Math.max(16 * 1024, value.length))
                .expireAfterWrite(30, TimeUnit.SECONDS).ticker(ticker).build();
    }

    public byte[] get(String key, Callable<byte[]> loader) {
        try {
            return tiles.get(key, () -> {
                byte[] bytes = loader.call();
                return bytes == null ? EMPTY : bytes;
            });
        } catch (ExecutionException | UncheckedExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) { throw (RuntimeException) cause; }
            throw new IllegalStateException("瓦片生成失败", cause);
        }
    }

    public void invalidate(long catalogVersion) {
        String prefix = catalogVersion + "\0";
        tiles.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }
}
