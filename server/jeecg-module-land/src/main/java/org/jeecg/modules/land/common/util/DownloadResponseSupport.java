package org.jeecg.modules.land.common.util;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件下载响应的收口工具：**重置响应时必须把跨域头一起带回来**。
 *
 * <p><b>它解决的是真机踩到的故障（2026-11-24）</b>：
 * 两个前端的「下载错误回执」都走 {@code fetch + blob}（因为要 POST 文件内容，
 * {@code window.open} 发不了 POST），点击后浏览器报
 * <pre>
 * Access to fetch at 'http://127.0.0.1:9802/api/land/data/import/errorReport?token=...'
 * from origin 'http://localhost:3000' has been blocked by CORS policy:
 * No 'Access-Control-Allow-Origin' header is present on the requested resource.
 * </pre>
 * 前端只剩一句「Failed to fetch」，用户看不到任何可操作的原因。
 *
 * <p><b>根因不是跨域没配</b>（{@code WebMvcConfiguration#corsFilter}、
 * {@code JwtFilter#preHandle} 都配了，预览接口也一直是好的），而是下载方法里的
 * {@code response.reset()}：按 Servlet 规范，{@code reset()} 会
 * <b>把缓冲区、状态码和<b>全部响应头</b>一起清空</b>。而
 * {@code Access-Control-Allow-Origin} / {@code -Credentials} 是过滤器在
 * <b>进入 Controller 之前</b>加上的 —— 于是「先加头、后 reset」= 头白加了。
 * 同一条链上的 {@code Access-Control-Expose-Headers} 之所以看起来没问题，
 * 只是因为它由 Controller 在 reset <b>之后</b>才设置。
 *
 * <p><b>为什么只在回执上下载暴露</b>：模板、变更明细走 {@code window.open} 原生下载，
 * 导航不参与 CORS 校验；只有 {@code fetch} 才会被浏览器按跨域规则拦。也就是说
 * 别的下载接口同样埋着这颗雷，只是暂时没人用 XHR 去取。
 *
 * <p><b>用法</b>：凡是「先完整生成、再一次性写响应」的下载方法，
 * 把 {@code response.reset()} 换成 {@link #resetKeepingCors(HttpServletResponse)}；
 * 只按需清理正文的场合不要调 reset（改成 {@code response.resetBuffer()} 即可保住响应头）。
 *
 * <p>所属工程：jeecg-module-land（JEECG-Boot 3.4.3 / JDK 1.8）
 */
public final class DownloadResponseSupport {

    /**
     * reset 之后必须原样补回的响应头。
     *
     * <p>前两个是浏览器放行跨域响应的必要条件；{@code Expose-Headers} 决定前端
     * {@code response.headers.get('Content-Disposition')} 能不能读到（读不到就拿不到后端起的文件名）；
     * {@code Vary} 是缓存正确性（同一个 URL 对不同 Origin 不能共用缓存）；
     * 其余是预检响应上的头，一并保住，避免「预检过了、真请求又丢头」这种半残状态。
     */
    private static final String[] CORS_HEADERS = {
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials",
            "Access-Control-Allow-Methods",
            "Access-Control-Allow-Headers",
            "Access-Control-Expose-Headers",
            "Access-Control-Max-Age",
            "Vary"
    };

    private DownloadResponseSupport() {
    }

    /**
     * 等价于 {@code response.reset()}，但保留跨域相关响应头（其余照常清空）。
     *
     * <p>为什么是「先抄下来再补回去」而不是自己拼一份值：跨域头的值由容器的
     * CorsFilter 决定（{@code allowCredentials=true} 时必须原样回显 Origin，
     * 手写 {@code *} 会被浏览器拒绝），照抄现场值才能与实际配置保持一致。
     *
     * @param response 当前响应
     * @throws IllegalStateException 响应已提交时（与 {@code reset()} 行为一致，由调用方兜底）
     */
    public static void resetKeepingCors(HttpServletResponse response) {
        Map<String, List<String>> preserved = new LinkedHashMap<>();
        for (String name : CORS_HEADERS) {
            Collection<String> values = response.getHeaders(name);
            if (values != null && !values.isEmpty()) {
                preserved.put(name, new ArrayList<>(values));
            }
        }
        response.reset();
        for (Map.Entry<String, List<String>> entry : preserved.entrySet()) {
            for (String value : entry.getValue()) {
                response.addHeader(entry.getKey(), value);
            }
        }
    }
}
