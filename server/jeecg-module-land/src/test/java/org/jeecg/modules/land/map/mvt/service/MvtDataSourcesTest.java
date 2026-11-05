package org.jeecg.modules.land.map.mvt.service;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MvtDataSourcesTest {
    @Test
    void reusesPoolWithoutLoadingConfigurationAndRecoversWhenFrameworkClosesPool() {
        DruidDataSource first = mock(DruidDataSource.class), second = mock(DruidDataSource.class);
        when(first.isEnable()).thenReturn(true); when(second.isEnable()).thenReturn(true);
        AtomicInteger loads = new AtomicInteger();
        MvtDataSources sources = new MvtDataSources(code -> loads.incrementAndGet() == 1 ? first : second);
        for (int i = 0; i < 20; i++) { assertSame(first, sources.get("db")); }
        assertEquals(1, loads.get());
        when(first.isClosed()).thenReturn(true);
        assertSame(second, sources.get("db")); assertEquals(2, loads.get());
        sources.retain(Collections.emptySet());
        assertSame(second, sources.get("db")); assertEquals(3, loads.get());
        verify(first, never()).close(); verify(second, never()).close();
    }
}
