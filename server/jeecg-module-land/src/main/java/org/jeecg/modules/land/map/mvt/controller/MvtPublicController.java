package org.jeecg.modules.land.map.mvt.controller;

import org.jeecg.modules.land.map.mvt.service.MvtService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;

/** 地图客户端无需管理端 Token；管理目录和连接配置仍要求登录及管理员角色。 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/land/mvt/public")
public class MvtPublicController {
    private final MvtService service;
    public MvtPublicController(MvtService service) { this.service = service; }

    @GetMapping("/{source}/{schema}/{table}/{geometry}")
    public ResponseEntity<Map<String, Object>> tileJson(@PathVariable String source, @PathVariable String schema,
            @PathVariable String table, @PathVariable String geometry, HttpServletRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "public, max-age=30")
                .body(service.tileJson(source, schema, table, geometry, request.getRequestURL().toString()));
    }

    @GetMapping("/{source}/{schema}/{table}/{geometry}/{z}/{x}/{y}.pbf")
    public ResponseEntity<byte[]> tile(@PathVariable String source, @PathVariable String schema,
            @PathVariable String table, @PathVariable String geometry, @PathVariable int z, @PathVariable int x, @PathVariable int y) {
        byte[] bytes = service.tile(source, schema, table, geometry, z, x, y);
        return ResponseEntity.status(bytes == null || bytes.length == 0 ? HttpStatus.NO_CONTENT : HttpStatus.OK)
                .contentType(MediaType.parseMediaType("application/vnd.mapbox-vector-tile"))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=30").body(bytes);
    }

    // 局部处理状态码，避免全局异常处理器把不存在/参数错误全部包装为 500。
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> failure(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatus()).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Collections.singletonMap("message", error.getReason()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> badCoordinate(MethodArgumentTypeMismatchException error) {
        return ResponseEntity.badRequest().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Collections.singletonMap("message", "瓦片层级和坐标必须为有效整数"));
    }
}
