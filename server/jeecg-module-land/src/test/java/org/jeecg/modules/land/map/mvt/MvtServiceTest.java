package org.jeecg.modules.land.map.mvt;

import org.jeecg.modules.land.map.mvt.mapper.MvtSourceMapper;
import org.jeecg.modules.land.map.mvt.service.*;
import org.jeecg.modules.land.map.mvt.support.*;
import org.jeecg.modules.land.map.mvt.vo.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 重点验证 URL 不能绕过目录白名单及参数边界，不接触业务数据。 */
class MvtServiceTest {
    @Test
    void rejectsInvalidCoordinatesBeforeOpeningDatabase() {
        MvtSourceMapper mapper = mock(MvtSourceMapper.class);
        MvtDataSources connections = mock(MvtDataSources.class);
        MvtService service = new MvtService(mapper, connections, mock(MvtPostgis.class));
        for (int[] tile : new int[][] {{-1,0,0},{23,0,0},{0,1,0},{4,-1,0},{4,0,16}}) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.tile("db", "public", "land", "geom", tile[0], tile[1], tile[2]));
            assertEquals(400, error.getStatus().value());
        }
        verifyNoInteractions(mapper, connections);
        assertDoesNotThrow(() -> MvtSql.validateTile(22,4194303,4194303));
    }

    @Test
    void rejectsUnknownTablesAndSourcesWithoutExecutingTileSql() {
        MvtSourceMapper mapper = mock(MvtSourceMapper.class);
        MvtSource source = new MvtSource(); source.setCode("db");
        when(mapper.listPostgres()).thenReturn(Collections.singletonList(source));
        MvtDataSources connections = mock(MvtDataSources.class);
        MvtPostgis pg = mock(MvtPostgis.class);
        DataSource ds = mock(DataSource.class);
        when(connections.get("db")).thenReturn(ds);
        MvtTable known = new MvtTable(); known.setSchema("public"); known.setTable("land"); known.setGeometry("geom");
        when(pg.discover(ds)).thenReturn(Collections.singletonList(known));
        MvtService service = new MvtService(mapper, connections, pg);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.tile("db", "public", "land\"; DROP TABLE land;--", "geom", 0,0,0));
        assertEquals(404, error.getStatus().value());
        verify(pg, never()).tile(any(), any(), anyInt(), anyInt(), anyInt());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.tables("unconfigured", true)).getStatus().value());
        verify(connections, never()).get("unconfigured");
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> service.tile("db", "public", "land", "geom", 0,0,0)).getStatus().value());
    }

    @Test
    void refreshFindsNewTablesAndDeletionCannotReuseOldSourceCache() {
        MvtSourceMapper mapper = mock(MvtSourceMapper.class);
        MvtSource source = new MvtSource(); source.setCode("db");
        when(mapper.listPostgres()).thenReturn(Collections.singletonList(source));
        MvtDataSources connections = mock(MvtDataSources.class);
        MvtPostgis pg = mock(MvtPostgis.class);
        DataSource ds = mock(DataSource.class); when(connections.get("db")).thenReturn(ds);
        when(pg.discover(ds)).thenReturn(Collections.emptyList(), Collections.singletonList(new MvtTable()));
        MvtService service = new MvtService(mapper, connections, pg);
        assertEquals(0, service.tables("db", false).size());
        assertEquals(0, service.tables("db", false).size());
        assertEquals(1, service.tables("db", true).size());
        verify(pg, times(2)).discover(ds);
        when(mapper.listPostgres()).thenReturn(Collections.emptyList());
        assertThrows(ResponseStatusException.class, () -> service.tables("db", false));
    }

    @Test
    void quotesEachSqlIdentifierIncludingEmbeddedQuotes() {
        assertEquals("\"地块\"\"; DROP TABLE land;--\"", MvtSql.quote("地块\"; DROP TABLE land;--"));
        assertThrows(IllegalArgumentException.class, () -> MvtSql.quote("a\0b"));
    }
}
