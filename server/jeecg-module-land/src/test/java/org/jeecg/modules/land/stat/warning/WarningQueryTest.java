package org.jeecg.modules.land.stat.warning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.common.exception.JeecgBootExceptionHandler;
import org.jeecg.modules.land.stat.warning.controller.DecisionOpinionController;
import org.jeecg.modules.land.stat.warning.controller.WarningController;
import org.jeecg.modules.land.stat.warning.controller.WarningLevelController;
import org.jeecg.modules.land.stat.warning.dto.WarningQueryDTO;
import org.jeecg.modules.land.stat.warning.service.IDecisionOpinionService;
import org.jeecg.modules.land.stat.warning.service.IWarningService;
import org.jeecg.modules.land.stat.warning.vo.DecisionOpinionVO;
import org.jeecg.modules.land.stat.warning.vo.WarningOptionsVO;
import org.jeecg.modules.land.stat.warning.vo.WarningVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Description: 分级预警 · 查询 / 汇总 / 下拉 / 导出 / 维护接口 集成测试（HTTP 层）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>守的是实现契约 §5.1~§5.7 与验收清单 §8 的第 5、6 项：
 * 列表按分类/等级过滤、{@code byLevel} 四档齐全且升序、
 * {@code options} 的裸字符串数组、决策意见「空串 = 只看通用」、
 * 等级保存的区间重叠 / 别名重复必须返回 {@code Result.error} 而不是 500。
 *
 * <p><b>★ 为什么用 MockMvc 而不是只看 Service 返回值</b>：
 * 这里要验的恰好是「HTTP 契约」——参数怎么绑（GET query / POST body）、
 * 返回体是不是 {@code Result{success,message,result}}、中文错误信息传不传得出去、
 * 以及「校验失败是 200 + success=false 还是 500」。只看 Service 层这些都验不到。
 *
 * <p><b>★ MockMvc 覆盖不到什么，要说清</b>：
 * {@code @RequiresPermissions} 由 Shiro 的 AOP 织入，{@code standaloneSetup} 不装 Shiro，
 * 所以这里不做「无权限应 403」的断言（也没有登录上下文）。
 * 权限的正确性由 SQL 脚本侧保证（{@code sql/stat/04_stat_warning_menu.sql} 的 4 个按钮权限 + 授权 admin）。
 * 同理，JWT 过滤器链、跨域也不在覆盖范围内 —— 那些是 jeecg 框架既有能力。
 *
 * <p><b>★ 断言为什么用 ObjectMapper 读 JSON 而不是 jsonPath 比对中文</b>：
 * {@code jsonPath(...).value("中文")} 对响应字符集很敏感（MockHttpServletResponse 默认
 * ISO-8859-1），一旦哪天响应头少了 charset 就会变成「乱码 ≠ 期望值」这种极难定位的失败。
 * 显式按 UTF-8 解码再比字符串，失败信息也直接可读。
 */
public class WarningQueryTest extends WarningTestSupport {

    private static final String BASE = "/land/stat/warning";

    @Autowired
    private WarningController warningController;

    @Autowired
    private WarningLevelController warningLevelController;

    @Autowired
    private DecisionOpinionController decisionOpinionController;

    @Autowired
    private IWarningService warningService;

    @Autowired
    private IDecisionOpinionService decisionOpinionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    public void setUpMockMvc() {
        // ★ 必须把 jeecg 的全局异常处理器装上：真实应用里它把 JeecgBootException 转成
        //   {"success":false,"message":"..."}；standaloneSetup 默认不注册任何 @RestControllerAdvice，
        //   会让「业务校验失败」表现成 500 NestedServletException。
        //   （本域的三个 Controller 自己 try/catch 了，装上是为了与真实环境一致、也能兜住意料外的异常。）
        mockMvc = MockMvcBuilders
                .standaloneSetup(warningController, warningLevelController, decisionOpinionController)
                .setControllerAdvice(new JeecgBootExceptionHandler())
                .build();
    }

    // ==================================================================
    // 一、列表筛选（契约 §5.1）
    // ==================================================================

    /**
     * {@code /list} 按 {@code yjType} / {@code levelCode} / {@code crzdbh} 过滤。
     *
     * <p>造数：同一配套下两条环节，逾期 3 天（蓝色）与 15 天（黄色）各一条；
     * 串行阶段合计 65 &gt; 60，于是还有一条阶段预警（蓝色，分类不同）。
     * 用 {@code crzdbh} 把范围收窄到本用例自己的数据，所以在真实库上也只会命中这几条。
     */
    @Test
    public void listFiltersByTypeAndLevelCode() throws Exception {
        Fixture fixture = newFixture();
        insertProcess(fixture, ITEM_PROJECT_1, "进行中",
                dateBeforeWorkdays(25), dateBeforeWorkdays(3), null, null, null);
        insertProcess(fixture, ITEM_PROJECT_2, "进行中",
                dateBeforeWorkdays(40), dateBeforeWorkdays(15), null, null, null);
        warningService.recalc(fixture.ptId);

        // 前置：两类预警都在（不然后面的断言会「因为没有数据而假成立」）
        assertNotNull(findWarning(warningRows(fixture.ptId), "环节预警", config(ITEM_PROJECT_1).id));
        assertNotNull(findWarning(warningRows(fixture.ptId), "阶段预警", config(STAGE_PROJECT).id));

        assertEquals(1, total(getJson(BASE + "/list",
                        "crzdbh", fixture.crzdbh, "yjType", "环节预警", "levelCode", "blue")),
                "环节预警 + 蓝色 → 只应有逾期 3 天那一条");
        assertEquals(1, total(getJson(BASE + "/list",
                        "crzdbh", fixture.crzdbh, "yjType", "环节预警", "levelCode", "yellow")),
                "环节预警 + 黄色 → 只应有逾期 15 天那一条");
        assertEquals(1, total(getJson(BASE + "/list", "crzdbh", fixture.crzdbh, "yjType", "阶段预警")),
                "阶段预警 → 串行 65 > 60 那一条");
        assertEquals(0, total(getJson(BASE + "/list", "crzdbh", fixture.crzdbh, "yjType", "总体预警")),
                "总体 65 < 155 → 没有总体预警");

        // 字段口径：yjPercent 必须已经乘过 100（前端不再自己乘，否则会显示 12000%）
        WarningQueryDTO query = new WarningQueryDTO();
        query.setCrzdbh(fixture.crzdbh);
        query.setYjType("环节预警");
        query.setLevelCode("blue");
        query.setPageSize(10);
        WarningVO vo = firstRecord(warningService.list(query));
        assertNotNull(vo, "服务层应能查到同一条预警");
        assertEquals("blue", vo.getLevelCode());
        assertEquals(3, vo.getOverdueDays().intValue());
        assertEquals(15, vo.getStdDays().intValue());
        assertEquals(0, new BigDecimal("1.2000").compareTo(vo.getYjValue()),
                "预警值（比率）1.2000，实际 " + vo.getYjValue());
        assertEquals(0, new BigDecimal("120.0").compareTo(vo.getYjPercent()),
                "★ yjPercent 必须是已 ×100 的 120.0（1 位小数），前端直接拼 %");
        assertNotNull(vo.getCalcTime(), "calcTime 是「这批数是几时算的」，列表要显示");
    }

    // ==================================================================
    // 二、汇总四色卡（契约 §5.2）
    // ==================================================================

    /**
     * {@code /summary} 的 {@code byLevel} 必须<b>四档齐全、按 sort_no 升序、没数据给 0</b>。
     *
     * <p>★ 为什么这条是验收重点：大屏端四张卡片是直接按 {@code byLevel} 顺序画的，
     * 缺一档就会「跳色」（蓝色卡片下面接着橙色），而 {@code count} 给 null 会让前端
     * 参与计算时出现 {@code NaN}。所以「补齐 0」与「顺序」都是硬契约。
     *
     * <p>本用例只造 1 条蓝色环节预警（阶段合计 25 &lt; 60、总体 25 &lt; 155），
     * 于是四档计数必须是 [1,0,0,0] —— 差一档都会被抓到。
     */
    @Test
    public void summaryKeepsFourLevelsInOrderWithZeroFill() throws Exception {
        Fixture fixture = newFixture();
        insertProcess(fixture, ITEM_PROJECT_1, "进行中",
                dateBeforeWorkdays(25), dateBeforeWorkdays(3), null, null, null);
        warningService.recalc(fixture.ptId);

        JsonNode body = getJson(BASE + "/summary", "crzdbh", fixture.crzdbh);
        assertTrue(body.path("success").asBoolean(), "汇总接口应成功");

        JsonNode result = body.path("result");
        assertEquals(1, result.path("total").asInt(), "该配套只有 1 条预警");
        JsonNode byLevel = result.path("byLevel");
        assertEquals(4, byLevel.size(), "★ byLevel 必须四档齐全（含 count=0 的等级），否则四色卡片跳色");

        String[] expectedCodes = {"blue", "yellow", "orange", "red"};
        long[] expectedCounts = {1L, 0L, 0L, 0L};
        for (int i = 0; i < expectedCodes.length; i++) {
            JsonNode item = byLevel.get(i);
            assertEquals(expectedCodes[i], item.path("levelCode").asText(),
                    "★ byLevel 第 " + (i + 1) + " 档必须按 sort_no 升序（由轻到重）");
            assertEquals(expectedCounts[i], item.path("count").asLong(),
                    "等级 " + expectedCodes[i] + " 的计数不对");
            assertFalse(item.path("levelName").asText().isEmpty(), "四色卡片要显示等级中文名");
            assertFalse(item.path("levelId").asText().isEmpty(), "四色卡片要能按 levelId 下钻筛选");
        }

        // byType 与 byLevel 相反：只返回有数据的分类（前端分类下拉不该凭空多出 0 值项）
        JsonNode byType = result.path("byType");
        assertEquals(1, byType.size(), "只有环节预警这一种分类有数据");
        assertEquals("环节预警", byType.get(0).path("yjType").asText());
        assertEquals(1L, byType.get(0).path("count").asLong());
        assertFalse(result.path("calcTime").asText().isEmpty(), "calcTime 是「数据截止时刻」，不能为空");
    }

    // ==================================================================
    // 三、下拉选项（契约 §5.3）
    // ==================================================================

    /**
     * {@code /options}：等级升序 + {@code yjTypes} / {@code problemTypes} 都是<b>裸字符串数组</b>。
     *
     * <p>★ 「裸字符串」是前后端的硬约定：前端下拉直接把它们当 {@code label} 用，
     * 包成 {@code [{text,value}]} 就要多一层 map，而且两个入口（大屏筛选条、管理端意见维护）
     * 得各写一遍。这里断言 {@code yjTypes[0]} 的文本本身等于「环节预警」，
     * 若哪天改成对象，这条会立刻失败。
     *
     * <p>{@code problemTypes} 在测试上下文里走<b>内置 5 项兜底</b>：
     * 字典接口 {@code CommonAPI} 实现在 system 模块，本工程集成测试是白名单装配，
     * 容器里没有它（Service 里是 {@code @Autowired(required = false)}）——
     * 这正是「新环境还没导字典时下拉不能空」那条兜底的现场验证。
     */
    @Test
    public void optionsReturnBareStringArrays() throws Exception {
        JsonNode result = getJson(BASE + "/options").path("result");

        JsonNode levels = result.path("levels");
        assertEquals(4, levels.size(), "等级下拉应有 4 档（种子 4 行）");
        assertEquals("blue", levels.get(0).path("levelCode").asText(), "等级按 sortNo 升序，第一档是蓝色");
        assertEquals("red", levels.get(3).path("levelCode").asText());
        assertEquals(1, levels.get(0).path("minDays").asInt(), "下拉要带区间，维护页展示用");
        assertEquals(10, levels.get(0).path("maxDays").asInt());

        JsonNode yjTypes = result.path("yjTypes");
        assertEquals(3, yjTypes.size(), "预警分类固定三项");
        assertEquals("环节预警", yjTypes.get(0).asText(), "★ yjTypes 必须是裸字符串数组，不是对象数组");
        assertEquals("阶段预警", yjTypes.get(1).asText());
        assertEquals("总体预警", yjTypes.get(2).asText());

        JsonNode problemTypes = result.path("problemTypes");
        assertTrue(problemTypes.isArray(), "problemTypes 必须是数组");
        assertEquals(5, problemTypes.size(),
                "测试上下文没有字典 Bean → 回落到内置 5 项（新环境没导字典时下拉不能空）");
        assertEquals("审批问题", problemTypes.get(0).asText(), "★ problemTypes 同样是裸字符串数组");
        assertEquals("地质问题", problemTypes.get(4).asText());

        // 与服务层对象交叉验证：VO 里就是 List<String>，不是 List<某种对象>
        WarningOptionsVO vo = warningService.options();
        assertEquals("环节预警", vo.getYjTypes().get(0));
        assertNotNull(vo.getProblemTypes().get(0), "问题类型下拉的每一项就是字符串本身");
    }

    // ==================================================================
    // 四、决策意见「空串 = 只看通用」（契约 §5.7，前后端最容易错的口径）
    // ==================================================================

    /**
     * ⚠️⭐ {@code /opinion/list?problemType=}（空串）只返回<b>通用</b>意见。
     *
     * <p><b>★ 为什么这条必须单测</b>：Spring 把「参数没传」与「参数传了空串」都表现为
     * 一个 {@code String}，而这两种语义完全不同 ——
     * <ul>
     *   <li>没传 = 全部问题类型；</li>
     *   <li>传空串 = 只看「通用（所有问题类型）」那几条（{@code problem_type = ''}）。</li>
     * </ul>
     * 只要有人把判断写成 {@code StringUtils.isNotBlank(problemType)}，空串就会被当成「没传」，
     * 于是「筛了通用却把具体类型也列出来」—— 管理端看起来像筛选失效，实际是参数语义丢了。
     *
     * <p>造数：确保「资金问题 × 蓝色预警」这条<b>具体类型</b>的意见存在
     * （没有就用服务层补一条，正文带测试前缀便于清理），
     * 这样「只看通用」与「全部」的差别是真实存在的。
     */
    @Test
    public void opinionListTreatsEmptyProblemTypeAsGenericOnly() throws Exception {
        String blueLevelId = levelIdByCode("blue");
        assertNotNull(blueLevelId, "前置条件：等级表里应有 blue 这一档");
        String exactText = TEST_PREFIX + "精确意见-" + uniqueTag();
        if (exactOpinion(blueLevelId, "资金问题") == null) {
            DecisionOpinionVO vo = new DecisionOpinionVO();
            vo.setLevelId(blueLevelId);
            vo.setProblemType("资金问题");
            vo.setOpinion(exactText);
            decisionOpinionService.save(vo);
        }

        // ① 传空串 → 只返回通用意见
        JsonNode genericOnly = getJson(BASE + "/opinion/list", "problemType", "").path("result");
        assertTrue(genericOnly.path("total").asInt() > 0,
                "★ 传 problemType= 空串时，通用意见（problem_type=''）必须查得出来（种子里有 4 条）");
        int genericCount = genericOnly.path("records").size();
        assertTrue(genericCount > 0, "records 不能为空");
        for (JsonNode record : genericOnly.path("records")) {
            assertEquals("", record.path("problemType").asText(),
                    "★ problemType=空串 时必须只返回通用意见，不能把具体类型也带出来");
        }

        // ② 传具体类型 → 只返回该类型
        JsonNode exactOnly = getJson(BASE + "/opinion/list", "problemType", "资金问题").path("result");
        assertTrue(exactOnly.path("total").asInt() > 0, "应有「资金问题」这一类型的意见");
        for (JsonNode record : exactOnly.path("records")) {
            assertEquals("资金问题", record.path("problemType").asText());
        }

        // ③ 不传 → 全部（条数不少于「只看通用」的条数）
        JsonNode all = getJson(BASE + "/opinion/list").path("result");
        assertTrue(all.path("total").asInt() >= genericOnly.path("total").asInt(),
                "★ 不传 problemType 时是「全部」，条数不能比只看通用还少");
        boolean hasExact = false;
        for (JsonNode record : all.path("records")) {
            if ("资金问题".equals(record.path("problemType").asText())) {
                hasExact = true;
            }
        }
        assertTrue(hasExact, "不传 problemType 时应同时包含具体类型的意见");
    }

    // ==================================================================
    // 五、等级维护校验（契约 §5.6）
    // ==================================================================

    /**
     * 等级保存的三类校验：区间重叠 / 别名重复 / 下界大于上界，一律 {@code Result.error}。
     *
     * <p>★ 「不是 500」本身就是契约：管理端表单只认 {@code success === false} 并原样弹
     * {@code message}。抛异常时用户看到的是「系统异常」，而真正该看到的是
     * 「区间与等级「蓝色预警」的区间 [1, 10] 重叠」这种能直接改的提示。
     *
     * <p>校验顺序也是设计的一部分：别名唯一性在区间重叠之前判 ——
     * 所以下界 101-200（不与任何档重叠）但别名写 {@code blue} 时，报的必须是「别名已被占用」。
     */
    @Test
    public void levelSaveRejectsOverlapAndDuplicateCode() throws Exception {
        String uniqueCode = "tj" + (System.nanoTime() % 1000000000L);

        // ① 区间与蓝色 [1,10] 重叠
        JsonNode overlap = postJson(BASE + "/level/save",
                "{\"levelName\":\"" + TEST_PREFIX + "测试等级\",\"levelCode\":\"" + uniqueCode
                        + "\",\"minDays\":5,\"maxDays\":12}");
        assertFalse(overlap.path("success").asBoolean(), "★ 区间重叠必须被拒绝");
        assertTrue(overlap.path("message").asText().contains("重叠"),
                "★ 错误信息要指出是「重叠」，并带冲突的等级名，用户才知道怎么改。实际："
                        + overlap.path("message").asText());

        // ② 别名重复：别名同时是前端着色令牌，只允许 [a-z0-9_-]{2,20}，
        //    所以「重复」只能用一个**合法但已存在**的别名来测（blue）。
        //    区间给 101-200：不与任何档重叠，保证这条只会因「别名重复」被拒。
        JsonNode duplicate = postJson(BASE + "/level/save",
                "{\"levelName\":\"" + TEST_PREFIX + "测试等级乙\",\"levelCode\":\"blue\""
                        + ",\"minDays\":101,\"maxDays\":200}");
        assertFalse(duplicate.path("success").asBoolean(), "★ 别名重复必须被拒绝");
        assertTrue(duplicate.path("message").asText().contains("已被等级"),
                "★ 错误信息要指出是别名被占用。实际：" + duplicate.path("message").asText());

        // ③ 别名格式：大写不合法（它要拿去匹配 --screen-level-* 样式令牌，
        //    存了 BLUE 就会「等级显示出来了但没有颜色」，所以保存时就该拦住）
        JsonNode badCase = postJson(BASE + "/level/save",
                "{\"levelName\":\"" + TEST_PREFIX + "测试等级丙\",\"levelCode\":\"BLUE\""
                        + ",\"minDays\":101,\"maxDays\":200}");
        assertFalse(badCase.path("success").asBoolean(), "★ 别名只允许小写（前端着色令牌口径）");
        assertTrue(badCase.path("message").asText().contains("不合法"),
                "★ 错误信息要说明别名不合法。实际：" + badCase.path("message").asText());

        // ④ 下界大于上界
        JsonNode inverted = postJson(BASE + "/level/save",
                "{\"levelName\":\"" + TEST_PREFIX + "测试等级丁\",\"levelCode\":\"" + uniqueCode
                        + "\",\"minDays\":200,\"maxDays\":100}");
        assertFalse(inverted.path("success").asBoolean(), "下界大于上界必须被拒绝");
        assertTrue(inverted.path("message").asText().contains("不能大于"),
                "错误信息要说清下界与上界的关系。实际：" + inverted.path("message").asText());

        // ⑤ 正常保存（100 天之后的溢出区间）→ 成功、出现在列表里、能删掉
        JsonNode saved = postJson(BASE + "/level/save",
                "{\"levelName\":\"" + TEST_PREFIX + "测试等级\",\"levelCode\":\"" + uniqueCode
                        + "\",\"minDays\":101,\"maxDays\":200}");
        assertTrue(saved.path("success").asBoolean(),
                "不重叠的区间应能保存成功。实际：" + saved.path("message").asText());
        String newLevelId = saved.path("result").asText();
        assertFalse(newLevelId.isEmpty(), "保存成功应回传新等级 id");

        assertEquals(5, getJson(BASE + "/level/list").path("result").size(),
                "新增后等级列表应有 5 档");
        JsonNode deleted = deleteJson(BASE + "/level/delete?id=" + newLevelId);
        assertTrue(deleted.path("success").asBoolean(), "软删应成功");
        assertEquals(4, getJson(BASE + "/level/list").path("result").size(),
                "删除后列表应回到 4 档（软删：del_flag=1 不再出现在列表里）");
    }

    // ==================================================================
    // 六、手动重算与导出（契约 §5.4 / §5.5）
    // ==================================================================

    /**
     * {@code POST /recalc}：body 里带 {@code ptId} 只算这一个配套，回执字段齐全。
     *
     * <p>★ 回执不是「锦上添花」：用户点完「重新计算」一定会问「算了多少、为什么数字没变」，
     * {@code created/updated/removed/skipped*} 就是回答这个问题的唯一依据。
     */
    @Test
    public void recalcEndpointReturnsReceipt() throws Exception {
        Fixture fixture = newFixture();
        insertProcess(fixture, ITEM_PROJECT_1, "进行中",
                dateBeforeWorkdays(25), dateBeforeWorkdays(3), null, null, null);

        JsonNode body = postJson(BASE + "/recalc", "{\"ptId\":\"" + fixture.ptId + "\"}");
        assertTrue(body.path("success").asBoolean(), "重算接口应成功");
        JsonNode result = body.path("result");
        assertEquals(1, result.path("scannedFacilities").asInt(), "只扫了指定的这一个配套");
        assertEquals(1, result.path("scannedProcesses").asInt(), "该配套 1 条环节进度");
        assertEquals(1, result.path("created").asInt(), "首轮应新增 1 条预警");
        assertEquals(0, result.path("removed").asInt());
        assertFalse(result.path("levelMissing").asBoolean(), "等级表有 4 档，不该报缺失");
        assertFalse(result.path("calcTime").asText().isEmpty(), "回执要带计算时刻");
        assertTrue(result.path("elapsedMs").asLong() >= 0L, "回执要带耗时");

        // 再来一次：幂等（回执是用户唯一能看到的证据）
        JsonNode again = postJson(BASE + "/recalc", "{\"ptId\":\"" + fixture.ptId + "\"}").path("result");
        assertEquals(0, again.path("created").asInt(), "★ 第二次重算不应再新增");
        assertEquals(0, again.path("removed").asInt(), "★ 第二次重算不应删除");
        assertEquals(1, again.path("updated").asInt(), "第二次重算应把这一行更新一次");
    }

    /**
     * {@code /export} 生成的工作簿：一个 sheet、表头与列表列一致、预警值带 % 且已 ×100。
     *
     * <p>★ 导出是「后端直接写文件」，中间没有任何人能再乘一次 100 ——
     * 所以百分比必须在导出数据里就定好形态（旧系统也是 {@code %.1f + "%"}）。
     */
    @Test
    public void exportWorkbookHasWarningSheet() throws Exception {
        Fixture fixture = newFixture();
        insertProcess(fixture, ITEM_PROJECT_1, "进行中",
                dateBeforeWorkdays(25), dateBeforeWorkdays(3), null, "审批问题", null);
        warningService.recalc(fixture.ptId);

        WarningQueryDTO query = new WarningQueryDTO();
        query.setCrzdbh(fixture.crzdbh);
        Workbook workbook = warningService.export(query);
        try {
            assertEquals(1, workbook.getNumberOfSheets(), "预警导出只应有一个 sheet");
            Sheet sheet = workbook.getSheetAt(0);
            // ExportParams 默认带一行大标题：标题(0) + 表头(1) + 数据(2)
            assertEquals(2, sheet.getLastRowNum(), "1 条预警 = 标题 1 行 + 表头 1 行 + 数据 1 行");
            assertEquals("预警分类", sheet.getRow(1).getCell(0).getStringCellValue(), "第一列是预警分类");
            assertEquals("预警等级", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("预警值", sheet.getRow(1).getCell(8).getStringCellValue(),
                    "第 9 列是预警值（与列表列顺序一致）");
            assertEquals("环节预警", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("120.0%", sheet.getRow(2).getCell(8).getStringCellValue(),
                    "★ 导出里的预警值必须是「已 ×100 + 1 位小数 + %」的字符串，不能再让使用者自己换算");
        } finally {
            try {
                workbook.close();
            } catch (Exception ignore) {
                // 关闭失败不影响断言
            }
        }
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private JsonNode getJson(String url, String... params) throws Exception {
        MockHttpServletRequestBuilder builder = get(url);
        for (int i = 0; i + 1 < params.length; i += 2) {
            builder.param(params[i], params[i + 1]);
        }
        return readJson(mockMvc.perform(builder).andExpect(status().isOk()).andReturn());
    }

    private JsonNode postJson(String url, String body) throws Exception {
        return readJson(mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode deleteJson(String url) throws Exception {
        return readJson(mockMvc.perform(delete(url)).andExpect(status().isOk()).andReturn());
    }

    /** 显式按 UTF-8 解码再解析（见类注释：避免响应字符集影响中文断言） */
    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static int total(JsonNode body) {
        assertTrue(body.path("success").asBoolean(), "接口应成功，实际 message=" + body.path("message").asText());
        return body.path("result").path("total").asInt();
    }

    private static WarningVO firstRecord(com.baomidou.mybatisplus.core.metadata.IPage<WarningVO> page) {
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return null;
        }
        return page.getRecords().get(0);
    }
}
