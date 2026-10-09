package org.jeecg.modules.land.data.attachment;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.data.attachment.entity.LandAttachment;
import org.jeecg.modules.land.data.attachment.service.ILandAttachmentService;
import org.jeecg.modules.land.data.dto.LandSaveDTO;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.service.ILandAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 附件服务 · 存储路径归一的回归测试。
 *
 * <p><b>★ 为什么单独有这个测试类</b>：
 * 2026-10-09 用户实测「上传附件」100% 失败，报
 * <pre>
 *   文件存储路径必须是相对路径（不能以「/」开头）：
 *   「/facility/attachment/2026/10/1783009999_xxx.png」
 * </pre>
 * 根因是写侧的 {@code normalizeStorePath} 把「以 / 开头」当非法值拒绝，依据是
 * 代码注释里那句<b>「jeecg 3.4.3 的 uploadLocal 不带前导斜杠」——那句话是错的</b>：
 * {@code CommonController.uploadLocal} 返回 {@code bizPath + "/" + fileName}，
 * 而 {@code bizPath} 来自上传请求的 {@code biz} 参数，
 * 前端各模块的 {@code buildBizPath()} 统一返回 {@code /facility/attachment/yyyy/MM}（带斜杠）。
 * 于是「用上传接口返回的路径落库」这条唯一正确的用法被自己的校验挡住了。
 *
 * <p>修法是「服务端归一」而不是「改前端去斜杠」，因为：
 * ① 该 biz 约定被档案 / 收发文 / 提级论证三个模块共用；
 * ② 档案与收发文模块把上传返回值<b>原样入库</b>，库里本来就有带斜杠的历史值。
 *
 * <p>这个测试把结论钉住：<b>带前导斜杠必须能存进去并被归一</b>，
 * 同时<b>路径穿越与盘符绝对路径仍然必须被拒绝</b>（归一不等于放松安全检查）。
 */
public class LandAttachmentPathTest extends LandIntegrationTestBase {

    @Autowired
    private ILandAttachmentService attachmentService;

    @Autowired
    private ILandAdminService landAdminService;

    /** 造一条有效的宗地，作为附件的归属对象（附件要求归属对象真实存在） */
    private String createLand() {
        LandSaveDTO dto = new LandSaveDTO();
        dto.setCrzdbh(TEST_DATA_CRZDBH_PREFIX + "ATT-001");
        dto.setDkmc("附件路径测试用地");
        dto.setXmfl("区级项目");
        return landAdminService.createLand(dto, null);
    }

    private LandAttachment meta(String storePath) {
        LandAttachment meta = new LandAttachment();
        meta.setFileName("测试扫描件.png");
        meta.setFileExt("png");
        meta.setFileSize(2048L);
        meta.setStoreType("local");
        meta.setStorePath(storePath);
        return meta;
    }

    /**
     * ★ 回归断言：上传接口返回的路径**带前导斜杠**，必须能正常落库。
     *
     * <p>这条就是用户报的那个故障。修复前它会抛
     * 「文件存储路径必须是相对路径（不能以「/」开头）」。
     */
    @Test
    public void leadingSlashFromUploadIsAcceptedAndNormalized() {
        String landId = createLand();
        // 复刻上传接口的真实返回形态（biz 原样拼进路径，于是以 / 开头）
        String fromUploadApi = "/facility/attachment/2026/10/1783009999_5a56a8ef2b65.png";

        LandAttachment saved = attachmentService.saveUploaded(
                "land", landId, TEST_DATA_CRZDBH_PREFIX + "ATT-001", "05", meta(fromUploadApi));

        assertNotNull(saved, "带前导斜杠的上传路径必须能落库（这正是用户报的故障）");
        assertNotNull(saved.getId());

        LandAttachment reloaded = attachmentService.queryById(saved.getId());
        assertNotNull(reloaded);
        // ★ 归一：库里存的是不带前导斜杠的相对路径
        assertEquals("facility/attachment/2026/10/1783009999_5a56a8ef2b65.png",
                reloaded.getStorePath(),
                "落库前应去掉前导斜杠，否则前端拼 staticDomainURL 会多一个斜杠导致 404");
        assertFalse(reloaded.getStorePath().startsWith("/"), "库里不应残留前导斜杠");
    }

    /** 已经是不带斜杠的写法（档案 / 收发文模块之外的调用方可能这么传）同样要能用 */
    @Test
    public void relativePathWithoutLeadingSlashStillWorks() {
        String landId = createLand();
        LandAttachment saved = attachmentService.saveUploaded(
                "land", landId, TEST_DATA_CRZDBH_PREFIX + "ATT-001", "05",
                meta("facility/attachment/2026/10/1783009999_abc.png"));

        LandAttachment reloaded = attachmentService.queryById(saved.getId());
        assertEquals("facility/attachment/2026/10/1783009999_abc.png", reloaded.getStorePath());
    }

    /** 多个前导斜杠 / 多余分隔符也要归一（历史数据里出现过 //a//b 这类写法） */
    @Test
    public void repeatedSlashesAreCollapsed() {
        String landId = createLand();
        LandAttachment saved = attachmentService.saveUploaded(
                "land", landId, TEST_DATA_CRZDBH_PREFIX + "ATT-001", "05",
                meta("//facility//attachment//2026/10/a.png"));

        LandAttachment reloaded = attachmentService.queryById(saved.getId());
        assertEquals("facility/attachment/2026/10/a.png", reloaded.getStorePath());
    }

    /**
     * ★ 归一不等于放松安全检查：路径穿越必须继续被拒绝。
     *
     * <p>如果这次改动只是「把 startsWith("/") 那段删掉」，这条会红 ——
     * 它是防止修复方式退化成「什么都放行」的守门断言。
     */
    @Test
    public void pathTraversalIsStillRejected() {
        String landId = createLand();
        JeecgBootException ex = assertThrows(JeecgBootException.class, () ->
                attachmentService.saveUploaded("land", landId, TEST_DATA_CRZDBH_PREFIX + "ATT-001", "05",
                        meta("/facility/../../etc/passwd")));
        assertTrue(ex.getMessage().contains(".."), "应提示路径穿越，实际：" + ex.getMessage());
    }

    /** 盘符绝对路径（Windows 形态，不以 / 开头但同样是绝对路径）也必须拒绝 */
    @Test
    public void driveLetterPathIsStillRejected() {
        String landId = createLand();
        JeecgBootException ex = assertThrows(JeecgBootException.class, () ->
                attachmentService.saveUploaded("land", landId, TEST_DATA_CRZDBH_PREFIX + "ATT-001", "05",
                        meta("C:/upFiles/facility/a.png")));
        assertTrue(ex.getMessage().contains("盘符"), "应提示盘符绝对路径，实际：" + ex.getMessage());
    }

    /** 去掉前导斜杠后为空（例如只传了 "/"）也要给出明确报错，而不是存一条空路径 */
    @Test
    public void slashOnlyPathIsRejected() {
        String landId = createLand();
        assertThrows(JeecgBootException.class, () ->
                attachmentService.saveUploaded("land", landId, TEST_DATA_CRZDBH_PREFIX + "ATT-001", "05",
                        meta("///")));
    }

    /**
     * 归一后的路径要能拼出可用的展示地址。
     *
     * <p>这条把「为什么要去前导斜杠」讲清楚：前端拼的是
     * {@code staticDomainURL + "/" + 相对路径}，相对路径自己再带一个斜杠就是双斜杠 → 404。
     */
    @Test
    public void normalizedPathBuildsUsableUrl() {
        String landId = createLand();
        LandAttachment saved = attachmentService.saveUploaded(
                "land", landId, TEST_DATA_CRZDBH_PREFIX + "ATT-001", "05",
                meta("/facility/attachment/2026/10/a.png"));

        LandAttachment reloaded = attachmentService.queryById(saved.getId());
        String url = reloaded.getUrl();
        assertNotNull(url, "应补出可直接渲染的 url");
        // 只看「协议://」之后的部分，避免把 http:// 里的双斜杠误判
        int afterScheme = url.indexOf("://");
        String afterHost = afterScheme >= 0 ? url.substring(afterScheme + 3) : url;
        assertFalse(afterHost.contains("//"),
                "域名之后不应出现双斜杠（前导斜杠没去掉就会这样）：" + url);
    }

    /** 归属对象不存在时必须继续被拦住（附件不能挂到不存在的业务上） */
    @Test
    public void unknownBizIdIsStillRejected() {
        assertThrows(JeecgBootException.class, () ->
                attachmentService.saveUploaded("land", "not-exist-id", "X", "05",
                        meta("facility/attachment/2026/10/a.png")));
    }

    /** 归属类型非法也要拒绝 */
    @Test
    public void unknownBizTypeIsRejected() {
        String landId = createLand();
        assertThrows(JeecgBootException.class, () ->
                attachmentService.saveUploaded("unknown", landId, "X", "05",
                        meta("facility/attachment/2026/10/a.png")));
    }
}
