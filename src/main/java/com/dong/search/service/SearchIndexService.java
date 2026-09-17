package com.dong.search.service;

import com.dong.search.dto.RebuildResponse;

/**
 * 索引治理。管的是「索引本身」而不是数据，读写索引仍走 SearchService。
 *
 * <p>为什么要有这一层：ES 的字段类型不可改，分词器也不能换（改 settings 要关索引），
 * 所以一旦 mapping 要动（加个字段类型、换分词器、加同义词），正确做法不是原地改，
 * 而是另起一个新索引、把数据搬过去、让别名指过去。应用全程只认别名，
 * 这套动作对它完全透明，这就是「零停机重建」。
 */
public interface SearchIndexService {

    /**
     * 确保别名可用：别名已存在就什么都不做，不存在则建好版本索引并挂上别名。
     *
     * <p>顺带兼容老的部署：如果 ES 里已经存在一个和别名同名的真实索引
     * （也就是还没引入别名之前的索引），会先把数据迁到 v1 再挂别名，老索引删掉。
     */
    void ensureAlias();

    /**
     * 当前别名指向的真实索引名。排查问题时先问它：写进去的数据到底落在哪个索引上。
     *
     * @return 真实索引名
     */
    String currentIndex();

    /**
     * 零停机重建：建下一个版本的索引，搬数据，原子切换别名，删掉旧索引。
     *
     * <p>注意这里有个已知缺口：reindex 期间如果有新写入，会写进旧索引而搬不走，
     * 切换后这部分增量就丢了。数据量小的场景可以忽略，
     * 生产上要么在重建窗口内停写，要么改用双写加时间戳补偿。
     *
     * @return 重建结果
     */
    RebuildResponse rebuild();

}
