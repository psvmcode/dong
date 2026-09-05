create database if not exists dong_lab default character set utf8mb4 collate utf8mb4_general_ci;

use dong_lab;

create table if not exists product
(
    id          bigint unsigned not null auto_increment                  comment '主键',
    name        varchar(128)    not null default ''                      comment '商品名称',
    category    varchar(64)     not null default ''                      comment '商品类目，缓存实验按类目过滤',
    price       decimal(12, 2)  not null default 0                       comment '商品单价',
    stock       int             not null default 0                       comment '库存数量',
    status      tinyint         not null default 1                       comment '状态：1 在售 2 已下架',
    create_time datetime        not null default current_timestamp       comment '创建时间',
    update_time datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    key idx_category (category)                                          comment '按类目查询商品'
) engine = innodb
  default charset = utf8mb4
  comment = '商品。多级缓存实验的载体，用于验证穿透、击穿、雪崩与双写一致性';

create table if not exists short_url
(
    id         bigint unsigned not null auto_increment                  comment '主键',
    code       varchar(16)     not null                                 comment '短码，Base62 编码后的唯一标识',
    origin_url varchar(2048)   not null                                 comment '原始长链接',
    hit_count  bigint          not null default 0                       comment '访问次数，每次跳转累加',
    create_time datetime       not null default current_timestamp       comment '创建时间',
    primary key (id),
    unique key uk_code (code)                                            comment '短码唯一，防止同一长链生成重复短码'
) engine = innodb
  default charset = utf8mb4
  comment = '短链接。发号器与 Base62 编码的落地表';

create table if not exists seckill_activity
(
    id              bigint unsigned not null auto_increment                  comment '主键',
    product_id      bigint unsigned not null                                 comment '参与秒杀的商品 id',
    title           varchar(128)    not null default ''                      comment '活动标题',
    total_stock     int             not null default 0                       comment '活动总库存，预热时写入 Redis',
    available_stock int             not null default 0                       comment '剩余库存，Redis 扣减完成后回写',
    unit_price      decimal(12, 2)  not null default 0                       comment '秒杀单价',
    start_time      datetime        not null                                 comment '活动开始时间',
    end_time        datetime        not null                                 comment '活动结束时间',
    status          tinyint         not null default 0                       comment '状态：0 草稿 1 已预热 2 上线中 3 已结束',
    version         int             not null default 0                       comment '乐观锁版本号，库存回写时递增',
    create_time     datetime        not null default current_timestamp       comment '创建时间',
    update_time     datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    key idx_product (product_id)                                             comment '按商品查活动'
) engine = innodb
  default charset = utf8mb4
  comment = '秒杀活动。库存先在 Redis 预扣，异步落库后回写剩余库存';

create table if not exists seckill_order
(
    id          bigint unsigned not null auto_increment                  comment '主键',
    order_no    varchar(32)     not null                                 comment '订单号',
    activity_id bigint unsigned not null                                 comment '所属秒杀活动 id',
    product_id  bigint unsigned not null                                 comment '商品 id',
    user_id     bigint unsigned not null                                 comment '下单用户 id',
    quantity    int             not null default 1                       comment '购买数量',
    amount      decimal(12, 2)  not null default 0                       comment '订单金额',
    status      tinyint         not null default 0                       comment '状态：0 待支付 1 已支付 2 已取消',
    create_time datetime        not null default current_timestamp       comment '创建时间',
    update_time datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_order_no (order_no)                                        comment '订单号唯一',
    unique key uk_activity_user (activity_id, user_id)                       comment '同一活动每人限购一次，防重复抢购',
    key idx_status_time (status, create_time)                                comment '按状态与时间扫描超时未支付订单'
) engine = innodb
  default charset = utf8mb4
  comment = '秒杀订单。唯一索引兜底防重复购买，超时未支付由定时任务回收';

create table if not exists red_packet
(
    id            bigint unsigned not null auto_increment                  comment '主键',
    packet_no     varchar(32)     not null                                 comment '红包编号',
    sponsor_id    bigint unsigned not null                                 comment '发红包的用户 id',
    total_amount  bigint          not null default 0                       comment '总金额，单位分',
    total_count   int             not null default 0                       comment '红包总份数',
    remain_amount bigint          not null default 0                       comment '剩余金额，单位分',
    remain_count  int             not null default 0                       comment '剩余份数',
    packet_type   tinyint         not null default 2                       comment '类型：1 固定金额 2 随机金额',
    status        tinyint         not null default 0                       comment '状态：0 已创建 1 发放中 2 已领完 3 已过期',
    create_time   datetime        not null default current_timestamp       comment '创建时间',
    update_time   datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_packet_no (packet_no)                                      comment '红包编号唯一'
) engine = innodb
  default charset = utf8mb4
  comment = '红包。金额在发送时预分配并推入 Redis，抢红包只是一次原子弹出';

create table if not exists red_packet_record
(
    id          bigint unsigned not null auto_increment                  comment '主键',
    packet_no   varchar(32)     not null                                 comment '所属红包编号',
    user_id     bigint unsigned not null                                 comment '领取用户 id',
    amount      bigint          not null default 0                       comment '领取金额，单位分',
    create_time datetime        not null default current_timestamp       comment '领取时间',
    primary key (id),
    unique key uk_packet_user (packet_no, user_id)                           comment '同一红包每人限领一次',
    key idx_packet (packet_no)                                               comment '按红包查领取记录'
) engine = innodb
  default charset = utf8mb4
  comment = '抢红包记录。可用于核对发放金额是否精确守恒';

create table if not exists social_relation
(
    id          bigint unsigned not null auto_increment                  comment '主键',
    follower_id bigint unsigned not null                                 comment '关注者 id',
    followee_id bigint unsigned not null                                 comment '被关注者 id',
    create_time datetime        not null default current_timestamp       comment '关注时间',
    primary key (id),
    unique key uk_relation (follower_id, followee_id)                         comment '关注关系唯一，重复关注被拦截',
    key idx_followee (followee_id)                                           comment '按被关注者查粉丝列表'
) engine = innodb
  default charset = utf8mb4
  comment = '关注关系。Redis Set 之外的关系落库，用于推拉两种时间线实验';

create table if not exists social_feed
(
    id         bigint unsigned not null auto_increment                  comment '主键',
    feed_id    bigint unsigned not null                                 comment '动态 id，雪花算法生成',
    author_id  bigint unsigned not null                                 comment '发布者 id',
    content    varchar(512)    not null default ''                      comment '动态正文',
    like_count bigint          not null default 0                       comment '点赞数',
    create_time datetime       not null default current_timestamp       comment '发布时间',
    primary key (id),
    unique key uk_feed (feed_id)                                            comment '动态 id 唯一',
    key idx_author (author_id, create_time)                                 comment '按作者与时间倒序拉取个人动态'
) engine = innodb
  default charset = utf8mb4
  comment = '用户动态。推模式写入粉丝时间线，拉模式读取时聚合关注关系';

create table if not exists tcc_transaction
(
    id          bigint unsigned not null auto_increment                  comment '主键',
    xid         varchar(64)     not null                                 comment '全局事务 id',
    status      tinyint         not null default 0                       comment '状态：1 尝试中 2 确认中 3 已确认 4 取消中 5 已取消，0 为插入前占位',
    expire_time datetime        not null                                 comment '事务过期时间，超时由恢复任务处理',
    retry_count int             not null default 0                       comment '恢复重试次数',
    create_time datetime        not null default current_timestamp       comment '创建时间',
    update_time datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_xid (xid)                                                  comment '全局事务 id 唯一',
    key idx_status (status, create_time)                                     comment '按状态扫描待恢复的事务'
) engine = innodb
  default charset = utf8mb4
  comment = 'TCC 全局事务。记录三阶段提交的推进状态，恢复任务据此补偿';

create table if not exists tcc_branch
(
    id              bigint unsigned not null auto_increment                  comment '主键',
    xid             varchar(64)     not null                                 comment '所属全局事务 id',
    branch_id       varchar(64)     not null                                 comment '分支事务 id',
    status          tinyint         not null default 0                       comment '状态：1 已尝试 2 已确认 3 已取消，0 为插入前占位',
    payload         varchar(2048)   not null default ''                      comment '分支参数快照，Confirm 与 Cancel 阶段回放用',
    error_message   varchar(512)    not null default ''                      comment '失败原因，非空约束故失败时写空字符串兜底',
    next_retry_time datetime        not null default current_timestamp       comment '下次重试时间',
    retry_count     int             not null default 0                       comment '已重试次数',
    create_time     datetime        not null default current_timestamp       comment '创建时间',
    update_time     datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_xid_branch (xid, branch_id)                                    comment '同一全局事务内分支唯一',
    key idx_retry (status, next_retry_time)                                      comment '按状态与重试时间捞取待处理分支'
) engine = innodb
  default charset = utf8mb4
  comment = 'TCC 分支事务。每个参与者一条，记录预留、确认与取消的结果';

create table if not exists tcc_inventory
(
    id          bigint unsigned not null auto_increment                  comment '主键',
    product_id  bigint unsigned not null                                 comment '商品 id',
    available   int             not null default 0                       comment '可用库存，Try 阶段扣减',
    frozen      int             not null default 0                       comment '冻结库存，Try 阶段累加，Confirm 时清零',
    create_time datetime        not null default current_timestamp       comment '创建时间',
    update_time datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_product (product_id)                                       comment '每个商品一条库存记录'
) engine = innodb
  default charset = utf8mb4
  comment = 'TCC 库存。可用与冻结分离，回滚后冻结必须归零';

create table if not exists tcc_account
(
    id          bigint unsigned not null auto_increment                  comment '主键',
    user_id     bigint unsigned not null                                 comment '用户 id',
    balance     bigint          not null default 0                       comment '可用余额，单位分',
    frozen      bigint          not null default 0                       comment '冻结金额，单位分，Confirm 时扣除、Cancel 时释放',
    create_time datetime        not null default current_timestamp       comment '创建时间',
    update_time datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_user (user_id)                                             comment '每个用户一条账户记录'
) engine = innodb
  default charset = utf8mb4
  comment = 'TCC 账户。与库存同为分支参与者，验证资金与库存的一致性';

create table if not exists tcc_order
(
    id         bigint unsigned not null auto_increment                  comment '主键',
    order_no   varchar(32)     not null                                 comment '订单号',
    xid        varchar(64)     not null                                 comment '所属全局事务 id',
    user_id    bigint unsigned not null                                 comment '下单用户 id',
    product_id bigint unsigned not null                                 comment '商品 id',
    quantity   int             not null default 0                       comment '购买数量',
    amount     bigint          not null default 0                       comment '订单金额，单位分',
    status     tinyint         not null default 0                       comment '状态：1 待确认 2 已确认 3 已取消，0 为插入前占位',
    create_time datetime      not null default current_timestamp       comment '创建时间',
    update_time datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_order_no (order_no)                                        comment '订单号唯一',
    unique key uk_xid (xid)                                                  comment '一个全局事务对应一张订单'
) engine = innodb
  default charset = utf8mb4
  comment = 'TCC 订单。反映分布式事务在业务侧的最终状态';

create table if not exists mq_message_log
(
    id          bigint unsigned not null auto_increment                  comment '主键',
    msg_id      varchar(64)     not null                                 comment '消息唯一 id，消费端据此去重',
    topic       varchar(128)    not null default ''                      comment '消息主题',
    payload     text                                                     comment '消息内容',
    status      tinyint         not null default 0                       comment '状态：0 已正常消费 1 已进入死信队列',
    retry_count int             not null default 0                       comment '消费重试次数，超过阈值转死信',
    create_time datetime        not null default current_timestamp       comment '首次投递时间',
    update_time datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_msg (msg_id)                                               comment '消息 id 唯一，重复投递被唯一索引拦截'
) engine = innodb
  default charset = utf8mb4
  comment = '消息投递日志。用于验证顺序、延迟与幂等消费';

create table if not exists cross_border_account
(
    id             bigint unsigned not null auto_increment                  comment '主键',
    account_no     varchar(32)     not null default ''                      comment '账号，对外唯一标识',
    owner_name     varchar(64)     not null default ''                      comment '户主姓名，制裁名单据此匹配',
    country        varchar(8)      not null default ''                      comment '所属国家或地区代码',
    currency       varchar(8)      not null default ''                      comment '账户币种，跨境汇款要求两端币种不同',
    balance        decimal(18, 2)  not null default 0                       comment '可用余额',
    frozen_balance decimal(18, 2)  not null default 0                       comment '冻结余额，司法或反洗钱调查时冻结',
    kyc_level      tinyint         not null default 0                       comment 'KYC 等级：0 级限 1000、1 级限 10000、2 级限 100000',
    daily_limit    decimal(18, 2)  not null default 0                       comment '日累计汇出限额，未指定时默认 100000',
    single_limit   decimal(18, 2)  not null default 0                       comment '单笔汇出限额，未指定时默认 50000',
    status         tinyint         not null default 1                       comment '状态：1 激活 2 冻结',
    create_time    datetime        not null default current_timestamp       comment '开户时间',
    update_time    datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_account_no (account_no)                                       comment '账号唯一',
    key idx_currency (currency)                                                 comment '按币种筛选账户'
) engine = innodb
  default charset = utf8mb4
  comment = '跨境账户。KYC 等级与限额决定可汇额度，状态决定能否参与汇款';

-- 账户事件只增不改：事件一旦允许修改就失去审计价值，因此不提供更新接口
create table if not exists cross_border_account_event
(
    id          bigint unsigned not null auto_increment                  comment '主键',
    account_no  varchar(32)     not null default ''                      comment '事件所属账号',
    event_type  tinyint         not null default 0                       comment '事件类型：1 冻结 2 解冻',
    reason      varchar(255)    not null default ''                      comment '操作原因，监管检查与审计追溯必填',
    operator    varchar(64)     not null default ''                      comment '操作人，状态变更必须能追溯到责任人',
    create_time datetime        not null default current_timestamp       comment '事件发生时间',
    update_time datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    key idx_account_no (account_no)                                          comment '按账号查事件历史',
    key idx_create_time (create_time)                                        comment '按时间倒序查近期事件'
) engine = innodb
  default charset = utf8mb4
  comment = '账户事件。状态回答现在能不能用，事件回答怎么变成这样的';

create table if not exists cross_border_fx_quote
(
    id            bigint unsigned not null auto_increment                  comment '主键',
    quote_no      varchar(32)     not null default ''                      comment '报价编号',
    currency_pair varchar(16)     not null default ''                      comment '货币对，形如 CNY/USD',
    bid_rate      decimal(18, 8)  not null default 0                       comment '买入价，银行向客户买入的汇率',
    ask_rate      decimal(18, 8)  not null default 0                       comment '卖出价，客户换汇按此价成交',
    locked_rate   decimal(18, 8)  not null default 0                       comment '锁定汇率，锁汇后写入并不再变动',
    status        tinyint         not null default 0                       comment '状态：1 可用 2 已锁定 3 已使用 4 已过期',
    expire_time   datetime        not null                                 comment '报价失效时间，过期后不能锁定',
    remittance_no varchar(32)     not null default ''                      comment '占用该报价的汇款单号',
    create_time   datetime        not null default current_timestamp       comment '报价生成时间',
    update_time   datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_quote_no (quote_no)                                           comment '报价编号唯一',
    key idx_pair_status (currency_pair, status)                                 comment '按货币对与状态找可用报价',
    key idx_expire_time (expire_time)                                           comment '定时任务按失效时间批量清理'
) engine = innodb
  default charset = utf8mb4
  comment = '汇率报价。买卖价差即银行利润，锁汇后汇率固定不受市场波动影响';

create table if not exists cross_border_remittance
(
    id                bigint unsigned not null auto_increment                  comment '主键',
    remittance_no     varchar(32)     not null default ''                      comment '汇款单号',
    idempotent_key    varchar(64)     not null default ''                      comment '幂等键，客户端重试时唯一索引兜底防重复汇款',
    payer_account_id  bigint unsigned not null default 0                       comment '付款账户 id',
    payee_account_id  bigint unsigned not null default 0                       comment '收款账户 id',
    source_currency   varchar(8)      not null default ''                      comment '汇出币种',
    target_currency   varchar(8)      not null default ''                      comment '到账币种',
    source_amount     decimal(18, 2)  not null default 0                       comment '汇出金额',
    exchange_rate     decimal(18, 8)  not null default 0                       comment '成交汇率，锁汇后写入',
    target_amount     decimal(18, 2)  not null default 0                       comment '到账金额，汇出金额乘以成交汇率',
    fee_amount        decimal(18, 2)  not null default 0                       comment '手续费，按渠道的固定费加比例费计算',
    channel           tinyint         not null default 1                       comment '清算渠道：1 SWIFT 2 CIPS 3 本地清算',
    status            tinyint         not null default 1                       comment '状态：1 已创建 2 合规拒绝 3 已锁汇 4 已扣款 5 清算中 6 已结算 7 失败 8 已退款 9 待人工审核',
    compliance_status tinyint         not null default 0                       comment '合规结论：1 通过 2 拒绝 3 人工审核',
    quote_no          varchar(32)     not null default ''                      comment '锁定的报价编号',
    batch_no          varchar(32)     not null default ''                      comment '所属清算批次，对账时按批次拉取',
    fail_reason       varchar(255)    not null default ''                      comment '失败或驳回原因',
    version           int             not null default 0                       comment '乐观锁版本号，状态推进的唯一凭据',
    create_time       datetime        not null default current_timestamp       comment '创建时间',
    update_time       datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_remittance_no (remittance_no)                                    comment '汇款单号唯一',
    unique key uk_idempotent_key (idempotent_key)                                  comment '幂等键唯一，是防重复汇款的最后一道防线',
    key idx_status (status)                                                         comment '按状态扫描待补偿与待清算的单子',
    key idx_batch_no (batch_no)                                                     comment '按批次查汇款单',
    key idx_create_time (create_time)                                               comment '按创建时间判断是否进入补偿静默期'
) engine = innodb
  default charset = utf8mb4
  comment = '跨境汇款单。状态只能由状态机推进，失败一律走退款不回退';

create table if not exists cross_border_compliance_record
(
    id            bigint unsigned not null auto_increment                  comment '主键',
    remittance_no varchar(32)     not null default ''                      comment '所属汇款单号',
    check_type    tinyint         not null default 0                       comment '检查项：1 制裁名单 2 KYC 等级 3 反洗钱 4 限额 5 人工复核',
    result        tinyint         not null default 0                       comment '结论：1 通过 2 拒绝 3 人工审核',
    hit_detail    varchar(512)    not null default ''                      comment '命中详情，说明被拒或需人工审核的具体原因',
    create_time   datetime        not null default current_timestamp       comment '检查时间',
    update_time   datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    key idx_remittance_no (remittance_no)                                       comment '按汇款单查全部检查记录'
) engine = innodb
  default charset = utf8mb4
  comment = '合规检查记录。只增不改，是监管检查时该笔交易已审核的直接证据';

create table if not exists cross_border_account_ledger
(
    id            bigint unsigned not null auto_increment                  comment '主键',
    ledger_no     varchar(32)     not null default ''                      comment '流水号',
    remittance_no varchar(32)     not null default ''                      comment '所属汇款单号',
    account_id    bigint unsigned not null default 0                       comment '账户 id',
    direction     tinyint         not null default 0                       comment '方向：1 借方（扣款）2 贷方（入账）',
    currency      varchar(8)      not null default ''                      comment '币种',
    amount        decimal(18, 2)  not null default 0                       comment '发生金额',
    balance_after decimal(18, 2)  not null default 0                       comment '变动后余额，用于还原当时的账务快照',
    create_time   datetime        not null default current_timestamp       comment '记账时间',
    update_time   datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_ledger_no (ledger_no)                                          comment '流水号唯一',
    unique key uk_remittance_account_direction (remittance_no, account_id, direction) comment '同一汇款同一账户同方向只能有一条，防重复入账',
    key idx_account_id (account_id)                                              comment '按账户查流水',
    key idx_create_time (create_time)                                            comment '按时间排序流水'
) engine = innodb
  default charset = utf8mb4
  comment = '资金流水。与余额变动同事务写入，唯一索引是防重复入账的最后一道防线';

create table if not exists cross_border_settlement_batch
(
    id           bigint unsigned not null auto_increment                  comment '主键',
    batch_no     varchar(32)     not null default ''                      comment '批次号',
    channel      tinyint         not null default 1                       comment '清算渠道：1 SWIFT 2 CIPS 3 本地清算',
    currency     varchar(8)      not null default ''                      comment '清算币种',
    total_count  int             not null default 0                       comment '批次内汇款笔数',
    total_amount decimal(18, 2)  not null default 0                       comment '批次内清算总金额',
    status       tinyint         not null default 0                       comment '状态：1 开启收集 2 已关闭 3 已结算',
    cutoff_time  datetime        not null                                 comment '清算截止时间，到点后不再接收新汇款',
    create_time  datetime        not null default current_timestamp       comment '批次创建时间',
    update_time  datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_batch_no (batch_no)                                           comment '批次号唯一',
    key idx_status (status)                                                     comment '按状态找开启中的批次与关闭到期批次'
) engine = innodb
  default charset = utf8mb4
  comment = '清算批次。批次开启时拒绝对账，因为还在收集期间结果会一直变';

create table if not exists cross_border_recon_diff
(
    id             bigint unsigned not null auto_increment                  comment '主键',
    batch_no       varchar(32)     not null default ''                      comment '所属对账批次号',
    remittance_no  varchar(32)     not null default ''                      comment '涉及的单号，渠道多出的记录用渠道侧单号',
    diff_type      tinyint         not null default 0                       comment '差异类型：3 金额不符 4 渠道漏单 5 渠道多单',
    local_amount   decimal(18, 2)  not null default 0                       comment '本地金额',
    channel_amount decimal(18, 2)  not null default 0                       comment '渠道回单金额',
    handle_status  tinyint         not null default 0                       comment '处理状态：0 未处理 1 已处理',
    create_time    datetime        not null default current_timestamp       comment '发现时间',
    update_time    datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    key idx_batch_no (batch_no)                                                 comment '按批次查差异',
    key idx_remittance_no (remittance_no)                                       comment '按单号查差异'
) engine = innodb
  default charset = utf8mb4
  comment = '对账差异。对账前先清旧差异，保证重复对账不会越跑差异越多';

create database if not exists dong_lab_replica default character set utf8mb4 collate utf8mb4_general_ci;

use dong_lab_replica;

create table if not exists user_account
(
    id          bigint unsigned not null auto_increment                  comment '主键',
    user_id     bigint unsigned not null                                 comment '用户 id',
    username    varchar(64)     not null default ''                      comment '用户名',
    balance     bigint          not null default 0                       comment '余额，单位分',
    create_time datetime        not null default current_timestamp       comment '创建时间',
    update_time datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_user (user_id)                                             comment '用户唯一'
) engine = innodb
  default charset = utf8mb4
  comment = '第二数据源账户。位于 MariaDB，用于验证多数据源与独立事务管理器';

use dong_lab;

-- 状态机只回答能不能走这一步，真正的并发安全落在 version 上，两次校验缺一不可
create table if not exists trade_order
(
    id             bigint unsigned not null auto_increment                  comment '主键',
    order_no       varchar(32)     not null                                 comment '订单号',
    user_id        bigint unsigned not null default 0                       comment '下单用户 id',
    product_name   varchar(128)    not null default ''                      comment '商品名称',
    quantity       int             not null default 1                       comment '购买数量',
    pay_amount     decimal(12, 2)  not null default 0                       comment '应付金额',
    refund_amount  decimal(12, 2)  not null default 0                       comment '已退金额，退款成功时累加',
    status         tinyint         not null default 1                       comment '状态：1 待支付 2 待发货 3 待收货 4 已完成 5 已取消 6 退款中 7 已退款',
    refund_from    tinyint         not null default 0                       comment '发起退款前的状态，退款失败据此退回，0 表示未曾退款',
    tracking_no    varchar(64)     not null default ''                      comment '物流单号，发货时写入，也是发货守卫的入参',
    pay_no         varchar(64)     not null default ''                      comment '支付流水号，支付时写入，也是支付守卫的入参',
    urge_count     int             not null default 0                       comment '催单次数，内部迁移累加，不改变订单状态',
    version        int             not null default 0                       comment '乐观锁版本号，状态推进的唯一凭据',
    create_time    datetime        not null default current_timestamp       comment '下单时间',
    update_time    datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_order_no (order_no)                                           comment '订单号唯一',
    key idx_status (status)                                                     comment '按状态筛选订单'
) engine = innodb
  default charset = utf8mb4
  comment = '订单履约主表。状态由状态机驱动，接口层不提供直接改状态的入口';

-- 被拒绝的流转也要记，否则无法量化验证并发实验中被拦下了多少次
create table if not exists trade_order_transition_log
(
    id           bigint unsigned not null auto_increment                  comment '主键',
    order_no     varchar(32)     not null                                 comment '所属订单号',
    from_status  tinyint         not null default 0                       comment '迁移前状态编码',
    to_status    tinyint         not null default 0                       comment '迁移后状态编码，被拦下时与迁移前相同',
    event        varchar(32)     not null default ''                      comment '触发的事件名',
    result       tinyint         not null default 0                       comment '结果：1 推进成功 0 被拒绝',
    reason       varchar(255)    not null default ''                      comment '拒绝原因，成功时为空',
    operator     varchar(64)     not null default ''                      comment '操作人或来源标识',
    create_time  datetime        not null default current_timestamp       comment '发生时间',
    primary key (id),
    key idx_order_no (order_no)                                               comment '按订单查流转历史',
    key idx_create_time (create_time)                                         comment '按时间排序'
) engine = innodb
  default charset = utf8mb4
  comment = '状态流转日志。成功与失败都记，是并发实验可量化验证的依据';
