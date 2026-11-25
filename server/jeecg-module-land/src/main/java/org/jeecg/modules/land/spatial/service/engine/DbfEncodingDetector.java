package org.jeecg.modules.land.spatial.service.engine;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;

/**
 * DBF 编码自动探测（shp2pgsql / GeoTools 两个导入引擎共用）。
 *
 * <p>背景：2026-11-17「经营性用地信息」678 行编号损坏（显示 �，不可逆）——
 * 源 SHP 的 DBF 实际是 GBK，但被按 UTF-8 解码（.cpg 声明了 UTF-8 / 导入编码
 * 选了 UTF-8），中文变成 U+FFFD 写进切片库，定位/图形同步按编号找不到要素。
 *
 * <p>探测口径：
 * <ol>
 *   <li>样本：DBF 前 2MB（表头 + 记录区，属性文本足够代表整表）；</li>
 *   <li>严格 UTF-8（malformed=REPORT）解码零错 → 判 UTF-8（纯 ASCII / 真 UTF-8）；</li>
 *   <li>否则 GBK 解码并校验产出含中文（CJK ≥ 3 字）→ 判 GBK（中文 SHP 的
 *       非 UTF-8 场景几乎都是 GBK）；</li>
 *   <li>都判不出 → 返回 fallbackCharset（沿用配置口径，如 land.spatial.dbf-charset）。</li>
 * </ol>
 *
 * <p>DBF 头部第 29 字节（language driver id，0x57=GBK）只做日志佐证，不参与
 * 判定——不少工具写出 UTF-8 内容却仍保留 0x57，字节行为比声明可信。
 */
@Slf4j
public final class DbfEncodingDetector {

    private DbfEncodingDetector() {
    }

    /**
     * 探测 DBF 实际编码。探测失败（文件不存在 / 读不了）返回 fallbackCharset。
     *
     * @param dbf             .dbf 文件
     * @param fallbackCharset 探测不出来时的回退编码（如配置的 land.spatial.dbf-charset）
     * @return "UTF-8" / "GBK" / fallbackCharset
     */
    public static String detect(File dbf, String fallbackCharset) throws java.io.IOException {
        if (dbf == null || !dbf.isFile()) return fallbackCharset;
        int cap = 2 * 1024 * 1024;
        byte[] sample = new byte[cap];
        int read;
        try (FileInputStream in = new FileInputStream(dbf)) {
            read = in.read(sample);
        }
        if (read <= 0) return fallbackCharset;
        if (read < cap) sample = java.util.Arrays.copyOf(sample, read);

        // DBF 头部第 29 字节（语言 ID）——仅日志佐证
        if (sample.length >= 30) {
            log.info("[DBF编码探测] 头部语言 ID（偏移 29）= 0x{}",
                    String.format("%02X", sample[29] & 0xFF));
        }

        CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        try {
            utf8.decode(ByteBuffer.wrap(sample));
            log.info("[DBF编码探测] 严格 UTF-8 试解码零错 → UTF-8");
            return "UTF-8";
        } catch (CharacterCodingException ignored) {
            // 不是合法 UTF-8（GBK 中文几乎必炸）→ 走 GBK 验证
        }

        CharsetDecoder gbk = java.nio.charset.Charset.forName("GBK").newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        String decoded;
        try {
            decoded = gbk.decode(ByteBuffer.wrap(sample)).toString();
        } catch (CharacterCodingException ignored) {
            log.warn("[DBF编码探测] 既非 UTF-8 也非 GBK，回退配置编码 {}", fallbackCharset);
            return fallbackCharset;
        }
        int cjk = 0;
        for (int i = 0; i < decoded.length(); i++) {
            char ch = decoded.charAt(i);
            if ((ch >= 0x4E00 && ch <= 0x9FFF) || (ch >= 0x3000 && ch <= 0x303F)) cjk++;
        }
        if (cjk >= 3) {
            log.info("[DBF编码探测] 非 UTF-8 且 GBK 解码出 {} 个中文字符 → GBK", cjk);
            return "GBK";
        }
        log.warn("[DBF编码探测] 非 UTF-8、GBK 无中文佐证，回退配置编码 {}", fallbackCharset);
        return fallbackCharset;
    }
}
