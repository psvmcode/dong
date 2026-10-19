package com.dong.upload.support;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.upload.dto.UploadMergeResult;
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
 * <p>四条硬约束：
 * <ul>
 *   <li><b>全程流式，绝不整块读进内存</b>。GB 级文件只要出现一次 readAllBytes 就会 OOM，
 *       这里写片用 Files.copy、合并用 InputStream.transferTo，都是流式拷贝。</li>
 *   <li><b>文件名只取 basename，且排除 . 与 ..</b>。
 *       Paths.get("..").getFileName() 返回的就是 ".."，
 *       只做 basename 而不排除它，resolve 之后仍然能跳到父目录。</li>
 *   <li><b>任何从外部进来的路径都要校验仍在存储根目录内</b>。
 *       deleteTaskFile 的 storagePath 来自数据库，库被污染或被人改过之后，
 *       不校验就会删到目录外面去。</li>
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
     * <p>返回实际写出的字节数而不是 file.getSize()——两者在正常情况相等，
     * 但磁盘写满、流被截断时会不等，用实际值才能发现「记了 5MB 实际只写了 3MB」这类问题。
     *
     * @param uploadId   上传任务号
     * @param chunkIndex 分片下标
     * @param file       分片内容
     * @return 实际写出的字节数，未落盘时返回声明大小
     * @throws IOException 写盘失败
     */
    public long writeChunk(String uploadId, int chunkIndex, MultipartFile file) throws IOException {
        long declared = file.getSize();
        if (!persistData) {
            return declared;
        }
        Path dir = Files.createDirectories(chunkDir(uploadId));
        Path target = dir.resolve(chunkIndex + ".part");
        long written;
        try (InputStream in = file.getInputStream()) {
            written = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        if (written != declared) {
            // 写出的和声明的不一致说明流被截断或磁盘满了，这条分片不能算收成功
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE,
                    "chunk " + chunkIndex + " truncated, wrote " + written + " of " + declared);
        }
        return written;
    }

    /**
     * 按顺序合并所有分片为目标文件。
     *
     * @param uploadId     上传任务号
     * @param fileName     原始文件名，内部只取 basename
     * @param chunkIndexes 已接收的分片下标，调用方保证升序且完整
     * @return 合并结果，含相对路径与实际字节数；未落盘时路径为空串、字节数为 0
     * @throws IOException 合并失败
     */
    public UploadMergeResult merge(String uploadId, String fileName, List<Integer> chunkIndexes) throws IOException {
        if (!persistData) {
            return new UploadMergeResult("", 0L);
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
        return new UploadMergeResult(Paths.get(storageDir).relativize(target).toString(), merged);
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
     * <p>storagePath 来自数据库，必须先校验它解析之后仍在存储根目录内：
     * 库里的数据一旦被改成 ../../xxx，不校验就会删到目录外面。
     *
     * @param storagePath 相对存储路径
     */
    public void deleteTaskFile(String storagePath) {
        if (!persistData || storagePath == null || storagePath.isEmpty()) {
            return;
        }
        Path target = Paths.get(storageDir).resolve(storagePath).normalize();
        if (!isInsideStorage(target)) {
            log.warn("upload refuse to delete outside storage path={}", storagePath);
            return;
        }
        deleteRecursively(target);
    }

    /**
     * 删除某个任务已合并的文件所在的整个任务目录。
     *
     * <p>与 deleteTaskFile 的区别：那个按库里记的相对路径删单个文件，
     * 这个按 uploadId 直接删掉整个任务目录，用于任务被取消时兜底清理。
     *
     * @param uploadId 上传任务号
     */
    public void deleteTaskDir(String uploadId) {
        if (!persistData) {
            return;
        }
        deleteRecursively(taskFile(uploadId, "unnamed").getParent());
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
        return Paths.get(storageDir).resolve("files").resolve(uploadId).resolve(safeName(fileName)).normalize();
    }

    /**
     * 判断路径是否仍在存储根目录内。
     *
     * @param target 待校验的绝对路径
     * @return true 表示没有越出存储根目录
     */
    private boolean isInsideStorage(Path target) {
        // 两边都转绝对路径再比：storageDir 配成 ./uploads 这类相对路径时，
        // 拿相对路径去 startsWith 绝对路径永远为 false，校验就成了摆设
        Path root = Paths.get(storageDir).toAbsolutePath().normalize();
        return target.toAbsolutePath().normalize().startsWith(root);
    }

    /**
     * 只保留文件名部分，挡掉路径穿越。
     *
     * <p>除了去掉分隔符，还要排除 "." 与 ".."：
     * Paths.get("..").getFileName() 返回的仍然是 ".."，
     * 只做 basename 处理的话，resolve 之后照样能跳到父目录。
     *
     * @param fileName 原始文件名
     * @return 安全文件名
     */
    private String safeName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unnamed";
        }
        String name = Paths.get(fileName).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return "unnamed";
        }
        return name;
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
