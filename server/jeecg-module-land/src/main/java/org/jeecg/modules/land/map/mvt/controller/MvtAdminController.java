package org.jeecg.modules.land.map.mvt.controller;

import org.apache.shiro.authz.annotation.RequiresRoles;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.land.map.mvt.service.MvtService;
import org.jeecg.modules.land.map.mvt.vo.MvtSource;
import org.jeecg.modules.land.map.mvt.vo.MvtTable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/land/mvt")
@RequiresRoles("admin")
public class MvtAdminController {
    private final MvtService service;
    public MvtAdminController(MvtService service) { this.service = service; }

    @GetMapping("/sources")
    public Result<List<MvtSource>> sources() { return Result.OK(service.sources()); }

    @GetMapping("/tables")
    public Result<?> tables(@RequestParam String source, @RequestParam(defaultValue = "false") boolean refresh) {
        try { return Result.OK(service.tables(source, refresh)); }
        catch (ResponseStatusException ex) { return Result.error(ex.getReason()); }
    }
}
