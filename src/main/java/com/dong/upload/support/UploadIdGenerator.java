package com.dong.upload.support;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 上传任务号生成器。
 *
 * <p>任务号由「内容指纹 + 文件大小 + 文件名 + 分片大小」确定性推导，而不是随机生成。
 * 这样前端刷新页面、换标签页、甚至换浏览器之后重新 init，
 * 都能天然命中同一个任务，把已收的分片问出来——不需要前端把 upload_id 存到 localStorage。
 *
 * <p>把分片大小也纳入推导是有意为之：分片大小变了，总分片数与每片边界都变了，
 * 之前收的那些分片在新口径下没有任何意义。与其复用一个口径不一致的任务，
 * 不如开一个新任务从头传。
 */
@Component
public class UploadIdGenerator {

    /**
     * 任务号前缀。
     */
    private static final String PREFIX = "UP";

    /**
     * 任务号长度，含前缀。
     */
    private static final int LENGTH = 32;

    /**
     * 推导上传任务号。
     *
     * @param fileHash  文件指纹
     * @param fileSize  文件大小
     * @param fileName  文件名
     * @param chunkSize 分片大小
     * @return 上传任务号
     */
    public String resolve(String fileHash, long fileSize, String fileName, int chunkSize) {
        String raw = fileHash + "|" + fileSize + "|" + fileName + "|" + chunkSize;
        return PREFIX + md5(raw).substring(0, LENGTH - PREFIX.length());
    }

    /**
     * 计算 MD5 十六进制串。
     *
     * @param input 原始字符串
     * @return 32 位小写十六进制
     */
    private String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(Character.forDigit((item >> 4) & 0xf, 16));
                builder.append(Character.forDigit(item & 0xf, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            // MD5 是 JDK 必须实现的算法，走不到这里；兜底退回 hashCode 保证不中断
            return String.format("%032x", input.hashCode());
        }
    }

}
