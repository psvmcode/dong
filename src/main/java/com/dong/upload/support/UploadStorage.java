package com.dong.upload.support;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 上传文件落盘组件。
 *
 * <p>默认只记元信息不落盘（persist-data=false），这样实验本身不占服务器磁盘，
 * 断点续传、分片并发、秒传这些机制照样能验证——它们依赖的是分片记录，不是文件内容。
 * 把开关打开之后才会真正写分片、合并成完整文件。
 *
 * <p>三条硬约束：
 * <ul>
 *   <li><b>全程流式，绝不整块读进内存</b>。GB 级文件只要出现一次 readAllBytes 就会 OOM，
 *       这里写片用 Files.copy、合并用 InputStream.transferTo，都是流式拷贝。</li>
 *   <li><b>文件名必须取 basename</b>。直接用前端传来的文件名拼路径，
 *       一个 "../../etc/passwd" 就能写到目录外面去。</li>
 *   <li><b>合并按分片下标顺序</b>。分片是并发到达的，落盘顺序不可控，
 *       合并时必须按下标排序再拼接，否则文件内容是乱的。</li>
 * </ul>
 */
@Slf4j
@Component
public class UploadStorage {

    /**
     * 是否真正落盘，关闭时只记元信息。
     */
    @Value("${dong.upload.persist-data:false}")
    private boolean persistData;

    /**
     * 存储根目录。
     */
    @Value("${dong.upload.storage-dir:./uploads}")
    private String storageDir;

    /**
     * 写入一个分片。
     *
     * <p>同一分片重复上传直接覆盖：断点续传时前端可能重发上一轮没有收到响应的分片，
     * 覆盖既保证幂等，也不会让分片目录越滚越大。
     *
     * @param uploadId   上传任务号
     * @param chunkIndex 分片下标
     * @param file       分片内容
     * @return 分片字节数
     * @throws IOException 写盘失败
     */
    public long writeChunk(String uploadId, int chunkIndex, MultipartFile file) throws IOException {
        long size = file.getSize();
        if (!persistData) {
            return size;
        }
        Path dir = Files.createDirectories(chunkDir(uploadId));
        Path target = dir.resolve(chunkIndex + ".part");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return size;
    }

    /**
     * 按顺序合并所有分片为目标文件。
     *
     * @param uploadId   上传任务号
     * @param fileName   原始文件名，内部只取 basename
     * @param chunkIndexes 已接收的分片下标，调用方保证升序且完整
     * @return 相对存储路径，未落盘返回空串
     * @throws IOException 合并失败
     */
    public String merge(String uploadId, String fileName, List<Integer> chunkIndexes) throws IOException {
        if (!persistData) {
            return "";
        }
        Path target = taskFile(uploadId, fileName);
        Files.createDirectories(target.getParent());
        long merged = 0;
        try (OutputStream out = Files.newOutputStream(target)) {
            for (Integer index : chunkIndexes) {
                Path part = chunkDir(uploadId).resolve(index + ".part");
                if (!Files.exists(part)) {
                    throw new BusinessException(Constants.CODE_PARAM_INVALID, "chunk " + index + " missing");
                }
                try (InputStream in = Files.newInputStream(part)) {
                    merged += in.transferTo(out);
                }
            }
        }
        log.info("upload merged uploadId={} bytes={} path={}", uploadId, merged, target);
        return Paths.get(storageDir).relativize(target).toString();
    }

    /**
     * 删除某个任务的分片目录。
     *
     * @param uploadId 上传任务号
     */
    public void deleteChunks(String uploadId) {
        if (!persistData) {
            return;
        }
        deleteRecursively(chunkDir(uploadId));
    }

    /**
     * 删除已合并的目标文件。
     *
     * @param storagePath 相对存储路径
     */
    public void deleteTaskFile(String storagePath) {
        if (!persistData || storagePath == null || storagePath.isEmpty()) {
            return;
        }
        deleteRecursively(Paths.get(storageDir).resolve(storagePath).normalize());
    }

    /**
     * 分片目录。
     *
     * @param uploadId 上传任务号
     * @return 分片目录路径
     */
    private Path chunkDir(String uploadId) {
        return Paths.get(storageDir).resolve("chunks").resolve(uploadId).normalize();
    }

    /**
     * 合并后的目标文件路径。
     *
     * @param uploadId  上传任务号
     * @param fileName  原始文件名
     * @return 目标文件路径
     */
    private Path taskFile(String uploadId, String fileName) {
        String safe = safeName(fileName);
        return Paths.get(storageDir).resolve("files").resolve(uploadId).resolve(safe).normalize();
    }

    /**
     * 只保留文件名部分，挡掉路径穿越。
     *
     * @param fileName 原始文件名
     * @return 安全文件名
     */
    private String safeName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unnamed";
        }
        String name = Paths.get(fileName).getFileName().toString();
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 递归删除目录，失败只记日志不抛出——清理是旁路动作，不该影响主流程。
     *
     * @param path 待删除路径
     */
    private void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ex) {
                    log.warn("upload cleanup delete failed path={} reason={}", item, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.warn("upload cleanup walk failed path={} reason={}", path, ex.getMessage());
        }
    }

}
