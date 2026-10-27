package com.dong.logging.service;

import com.dong.logging.dto.LogImportResponse;

/**
 * 历史日志回填。对应 Filebeat 那一环：把 logback 已经落盘的文件补进索引。
 *
 * <p>实时采集覆盖不到的三类日志都靠它：应用启动之前的、
 * 上一次进程来不及刷出去的、以及 ES 短暂不可用时被丢掉的。
 *
 * <p>位点存在 ES 里而不是本地文件：位点本身是要跨重启、跨实例共享的，
 * 放本地文件等于换台机器就重导一遍。
 */
public interface LogFileImportService {

    /**
     * 从日志目录回填。
     *
     * @param maxLines 本次最多处理多少行
     * @return 回填结果
     */
    LogImportResponse importFiles(int maxLines);

}
