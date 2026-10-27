package com.dong.logging.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志索引与别名的命名。
 *
 * <p>三套名字各管一件事：
 * <ul>
 *   <li>{@code dong_lab_log}：读别名，指向全部按天索引。检索、聚合、统计只认它，
 *       索引换了几份、今天是不是新起了一份，调用方都不用知道。</li>
 *   <li>{@code dong_lab_log_2026_10_27}：按天的真实索引。写入目标由日志时间直接算出。</li>
 *   <li>{@code dong_lab_log_meta}：内部状态索引，存历史日志回填的位点，不参与检索。</li>
 * </ul>
 *
 * <p>为什么要按天切索引而不是一个大的：过期日志的清理方式是「整块删索引」，
 * 一天一个索引才能把「保留 N 天」变成删几个索引；放在一个大索引里就只能
 * delete_by_query，ES 要逐条标记删除再等段合并，代价高一个数量级，
 * 而且删完之后磁盘也不会立刻还回来。
 *
 * <p>这里没有写别名：写入目标由日期算出来，是确定的。写别名只在多个索引共享
 * 一个写入口时才是必需的（按大小 rollover 那套），按天切不需要。
 *
 * <p>前缀沿用 dong.elasticsearch.index-prefix，与 search 模块共用一套命名空间。
 */
@Component
public class LogIndexNaming {

    /**
     * 日志的逻辑名，拼进索引名与别名。
     */
    public static final String LOGICAL_NAME = "log";

    /**
     * 按天索引的日期后缀格式。用下划线而不用短横线：ES 索引名只允许小写，
     * 且短横线在按前缀批量匹配时容易和逻辑名本身混淆。
     */
    private static final DateTimeFormatter DATE_SUFFIX = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    /**
     * 从索引名里反解析日期。
     */
    private static final Pattern DATE_PATTERN = Pattern.compile("_(\\d{4})_(\\d{2})_(\\d{2})$");

    /**
     * 索引名前缀。
     */
    private final String prefix;

    /**
     * 按天切分用的时区。与 logback 落盘文件用的是同一个时区，
     * 否则「文件里的最后一行」和「ES 里的最后一天」会对不齐。
     */
    private final ZoneId zone;

    public LogIndexNaming(@Value("${dong.elasticsearch.index-prefix:dong_lab}") String prefix) {
        this.prefix = prefix;
        this.zone = ZoneId.systemDefault();
    }

    /**
     * 读别名。检索与聚合一律打在它上面。
     *
     * @return 读别名，如 dong_lab_log
     */
    public String readAlias() {
        return prefix + "_" + LOGICAL_NAME;
    }

    /**
     * 内部状态索引，存回填位点。不挂别名：它不是检索对象，
     * 也不存在换 mapping 重建的需求，多一层别名只会增加理解成本。
     *
     * @return 状态索引名，如 dong_lab_log_meta
     */
    public String metaIndex() {
        return prefix + "_" + LOGICAL_NAME + "_meta";
    }

    /**
     * 某一天的索引名。
     *
     * @param date 日期
     * @return 索引名，如 dong_lab_log_2026_10_27
     */
    public String dailyIndex(LocalDate date) {
        return prefix + "_" + LOGICAL_NAME + "_" + DATE_SUFFIX.format(date);
    }

    /**
     * 今天。按天切分与保留策略都以它为基准，与 todayIndex 必须同源。
     *
     * @return 今天
     */
    public LocalDate today() {
        return LocalDate.now(zone);
    }

    /**
     * 今天的索引名。实时日志都写这里。
     *
     * @return 今天的索引名
     */
    public String todayIndex() {
        return dailyIndex(LocalDate.now(zone));
    }

    /**
     * 按日志时间算出它该落在哪个索引。回填历史日志时按各自的时间归档，
     * 不能一股脑写进今天——否则「保留 N 天」会把整段历史一次性判死。
     *
     * @param instant 日志时间
     * @return 索引名
     */
    public String indexOf(Instant instant) {
        return dailyIndex(LocalDate.ofInstant(instant, zone));
    }

    /**
     * 反解析索引名里的日期。解析不出来返回 null，说明这名字不是本模块的按天索引
     * （例如读别名或状态索引），清理时应当直接跳过。
     *
     * @param index 索引名
     * @return 日期，非按天索引返回 null
     */
    public LocalDate dateOf(String index) {
        Matcher matcher = DATE_PATTERN.matcher(index);
        if (!matcher.find()) {
            return null;
        }
        return LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
    }

}
