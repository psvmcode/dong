package com.dong.logging.service;

/**
 * 样本日志生成。
 *
 * <p>存在的理由很实际：日志检索页面在没有流量的时候是空的，
 * 空页面上既看不出直方图对不对，也看不出级别筛选有没有生效。
 *
 * <p>生成的日志走完整链路——真的经过 logback，真的进缓冲，真的由刷写任务写进 ES，
 * 而不是直接调 bulk 造数据。否则验证的是写入接口，不是采集管道。
 */
public interface LogSampleService {

    /**
     * 生成样本日志。
     *
     * @param count         条数
     * @param withException 是否带异常堆栈
     * @param withTrace     是否模拟链路，每若干条共用一个追踪 id
     * @return 生成的条数
     */
    int generate(int count, boolean withException, boolean withTrace);

}
