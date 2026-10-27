package com.dong.logging.support;

import java.net.InetAddress;

/**
 * 进程主机名。
 *
 * <p>两个来源都要用：appender 打实时日志，回填任务补历史日志。
 * 各解析一份迟早会出「同一台机器在索引里出现两个名字」，
 * 那时按主机过滤就只剩一半数据，而且没有任何报错。
 *
 * <p>解析一次就缓存：它是进程级常量，每条日志都去查 DNS 是白付代价。
 * 取不到就写 unknown——主机名只是检索维度，不值得为它让采集失败。
 */
public final class LogHost {

    /**
     * 主机名。
     */
    private static final String NAME = resolve();

    private LogHost() {
    }

    /**
     * 主机名。
     *
     * @return 主机名，取不到返回 unknown
     */
    public static String name() {
        return NAME;
    }

    /**
     * 解析主机名。
     *
     * @return 主机名，取不到返回 unknown
     */
    private static String resolve() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown";
        }
    }

}
