package com.dong.upload.dto;

/**
 * 分片合并结果。
 *
 * <p>path 与 bytes 一起返回，是为了让调用方能校验「合并出来的大小」与「声明的文件大小」是否一致。
 * 只回路径的话，调用方要么再读一次文件系统（越过分层），要么干脆不校验——
 * 而不校验的后果是：分片记录被污染时，一个大小对不上的文件会被标记为上传成功。
 *
 * @param path  相对存储路径，未落盘时为空串
 * @param bytes 合并后实际写出的字节数
 */
public record UploadMergeResult(String path, long bytes) {

}
