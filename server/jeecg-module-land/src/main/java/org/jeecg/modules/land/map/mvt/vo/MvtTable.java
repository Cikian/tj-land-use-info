package org.jeecg.modules.land.map.mvt.vo;

import lombok.Data;
import java.util.LinkedHashMap;
import java.util.Map;

/** 同一张表有多个几何列时，分别提供地址，避免隐式选错列。 */
@Data
public class MvtTable {
    private String schema;
    private String table;
    private String geometry;
    private String geometryType;
    private String kind;
    private int srid;
    private boolean geography;
    private boolean available;
    private String reason;
    private String comment;
    private Map<String, String> fields = new LinkedHashMap<>();
}
