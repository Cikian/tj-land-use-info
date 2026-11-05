package org.jeecg.modules.land.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.service.ILandService;
import org.jeecg.modules.land.data.mapper.FacilityMapper;
import org.jeecg.modules.land.data.service.IFacilityService;
import org.jeecg.modules.land.data.support.DataSupport;
import org.jeecg.modules.land.data.vo.FacilityOptionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Collections;
import java.util.List;

/**
 * @Description: 市政配套项目 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
@Slf4j
@Service
public class FacilityServiceImpl extends ServiceImpl<FacilityMapper, Facility> implements IFacilityService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    @Autowired
    private ILandService landService;

    @Override
    public List<FacilityOptionVO> queryOptions(String crzdbh, String keyword, Integer limit) {
        if (StringUtils.isBlank(crzdbh)) {
            // 宗地没选就不该列配套项目；返回空列表而不是全表，避免前端误用
            return Collections.emptyList();
        }
        int size = normalizeLimit(limit);
        return baseMapper.selectOptions(crzdbh.trim(), trimToNull(keyword), size);
    }

    @Override
    public List<FacilityOptionVO> searchOptions(String keyword, Integer limit) {
        return baseMapper.searchOptions(trimToNull(keyword), normalizeLimit(limit));
    }

    @Override
    public Facility queryById(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        return getOne(new QueryWrapper<Facility>().eq("id", id).eq("delFlag", "0"), false);
    }

    @Override
    public Facility queryByPtxmmc(String ptxmmc) {
        if (StringUtils.isBlank(ptxmmc)) {
            return null;
        }
        return baseMapper.selectByPtxmmc(ptxmmc.trim());
    }

    @Override
    public long countByCrzdbh(String crzdbh) {
        if (StringUtils.isBlank(crzdbh)) {
            return 0L;
        }
        return baseMapper.countByCrzdbh(crzdbh.trim());
    }

    @Override
    public void createFacility(Facility facility) {
        prepareFacility(facility, true);
        if (queryByPtxmmc(facility.getPtxmmc()) != null) {
            throw new JeecgBootException("创建失败！当前项目名称已创建!");
        }
        facility.setId(null);
        facility.setDelFlag("0");
        facility.setCreateAccount(currentUsername());
        facility.setCreateTime(new Date());
        save(facility);
    }

    @Override
    public void updateFacility(Facility facility) {
        prepareFacility(facility, false);
        if (queryById(facility.getId()) == null) {
            throw new JeecgBootException("未找到对应的配套项目");
        }
        Facility duplicated = queryByPtxmmc(facility.getPtxmmc());
        if (duplicated != null && !duplicated.getId().equals(facility.getId())) {
            throw new JeecgBootException("配套项目名称不能重复");
        }
        updateById(facility);
    }

    // 说明：queryWarningDetails 已删除 —— 首页预警「查看」明细迁到
    // stat/home（IHomeStatService#queryWarningLands），与预警行共用同一套口径。

    private void prepareFacility(Facility facility, boolean creating) {
        if (facility == null) {
            throw new JeecgBootException(creating ? "配套项目不能为空" : "配套项目主键不能为空");
        }
        if (!creating && StringUtils.isBlank(facility.getId())) {
            throw new JeecgBootException("配套项目主键不能为空");
        }
        if (StringUtils.isBlank(facility.getCrzdbh())) {
            throw new JeecgBootException("出让宗地编号不能为空");
        }
        if (StringUtils.isBlank(facility.getPtxmmc())) {
            throw new JeecgBootException("配套项目名称不能为空");
        }
        // 与 FacilityAdminServiceImpl 同一口径：编号补「号」、摘【城更】标记进备注、名称归一。
        // 这条是旧接口 /land/data/facility/add|edit 的写入路径，不归一的话
        // 从旧接口进来的编号仍会缺「号」，queryByCrzdbh 就会误报「未找到对应的出让宗地」。
        DataSupport.CodeInfo code = DataSupport.normalizeCode(facility.getCrzdbh());
        if (StringUtils.isBlank(code.getCode())) {
            throw new JeecgBootException("出让宗地编号不能为空");
        }
        facility.setCrzdbh(code.getCode());
        facility.setBz(DataSupport.appendRemark(facility.getBz(), code.getMarkers()));
        facility.setPtxmmc(DataSupport.normalizeName(facility.getPtxmmc(), 100));
        facility.setDkmc(DataSupport.normalizeName(facility.getDkmc(), 255));
        facility.setJsdw(DataSupport.normalizeName(facility.getJsdw(), 100));
        Land land = landService.queryByCrzdbh(facility.getCrzdbh());
        if (land == null) {
            throw new JeecgBootException("未找到对应的出让宗地");
        }
        if (StringUtils.isBlank(facility.getDkmc())) {
            facility.setDkmc(land.getDkmc());
        }
        if (StringUtils.isBlank(facility.getXzqh())) {
            facility.setXzqh(land.getXzqh());
        }
        if (StringUtils.isBlank(facility.getXmfl())) {
            facility.setXmfl(land.getXmfl());
        }
    }

    private static String currentUsername() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            if (principal instanceof LoginUser) {
                return ((LoginUser) principal).getUsername();
            }
        } catch (Exception ignored) {
            // 未登录场景不阻断写入，审计字段留空
        }
        return null;
    }

    private static int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
