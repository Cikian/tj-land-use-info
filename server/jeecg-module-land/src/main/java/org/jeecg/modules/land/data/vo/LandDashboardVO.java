package org.jeecg.modules.land.data.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 宗地首页统计。 */
@Data
public class LandDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;
    private long cityCount;
    private long districtCount;
    private long roadCount;
    private List<RankItem> ranks = new ArrayList<>();

    @Data
    public static class RankItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private long value;
        private long roadCount;
    }
}
