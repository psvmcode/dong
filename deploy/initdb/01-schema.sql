create table if not exists product
(
    id          bigint unsigned not null auto_increment,
    name        varchar(128)    not null default '',
    category    varchar(64)     not null default '',
    price       decimal(12, 2)  not null default 0,
    stock       int             not null default 0,
    status      tinyint         not null default 1,
    create_time datetime        not null default current_timestamp,
    update_time datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    key idx_category (category)
) engine = innodb
  default charset = utf8mb4;

create table if not exists short_url
(
    id         bigint unsigned not null auto_increment,
    code       varchar(16)     not null,
    origin_url varchar(2048)   not null,
    hit_count  bigint          not null default 0,
    create_time datetime       not null default current_timestamp,
    primary key (id),
    unique key uk_code (code)
) engine = innodb
  default charset = utf8mb4;

create table if not exists seckill_activity
(
    id              bigint unsigned not null auto_increment,
    product_id      bigint unsigned not null,
    title           varchar(128)    not null default '',
    total_stock     int             not null default 0,
    available_stock int             not null default 0,
    unit_price      decimal(12, 2)  not null default 0,
    start_time      datetime        not null,
    end_time        datetime        not null,
    status          tinyint         not null default 0,
    version         int             not null default 0,
    create_time     datetime        not null default current_timestamp,
    update_time     datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    key idx_product (product_id)
) engine = innodb
  default charset = utf8mb4;

create table if not exists seckill_order
(
    id          bigint unsigned not null auto_increment,
    order_no    varchar(32)     not null,
    activity_id bigint unsigned not null,
    product_id  bigint unsigned not null,
    user_id     bigint unsigned not null,
    quantity    int             not null default 1,
    amount      decimal(12, 2)  not null default 0,
    status      tinyint         not null default 0,
    create_time datetime        not null default current_timestamp,
    update_time datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_order_no (order_no),
    unique key uk_activity_user (activity_id, user_id),
    key idx_status_time (status, create_time)
) engine = innodb
  default charset = utf8mb4;

create table if not exists red_packet
(
    id            bigint unsigned not null auto_increment,
    packet_no     varchar(32)     not null,
    sponsor_id    bigint unsigned not null,
    total_amount  bigint          not null default 0,
    total_count   int             not null default 0,
    remain_amount bigint          not null default 0,
    remain_count  int             not null default 0,
    packet_type   tinyint         not null default 2,
    status        tinyint         not null default 0,
    create_time   datetime        not null default current_timestamp,
    update_time   datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_packet_no (packet_no)
) engine = innodb
  default charset = utf8mb4;

create table if not exists red_packet_record
(
    id          bigint unsigned not null auto_increment,
    packet_no   varchar(32)     not null,
    user_id     bigint unsigned not null,
    amount      bigint          not null default 0,
    create_time datetime        not null default current_timestamp,
    primary key (id),
    unique key uk_packet_user (packet_no, user_id),
    key idx_packet (packet_no)
) engine = innodb
  default charset = utf8mb4;

create table if not exists social_relation
(
    id          bigint unsigned not null auto_increment,
    follower_id bigint unsigned not null,
    followee_id bigint unsigned not null,
    create_time datetime        not null default current_timestamp,
    primary key (id),
    unique key uk_relation (follower_id, followee_id),
    key idx_followee (followee_id)
) engine = innodb
  default charset = utf8mb4;

create table if not exists social_feed
(
    id         bigint unsigned not null auto_increment,
    feed_id    bigint unsigned not null,
    author_id  bigint unsigned not null,
    content    varchar(512)    not null default '',
    like_count bigint          not null default 0,
    create_time datetime       not null default current_timestamp,
    primary key (id),
    unique key uk_feed (feed_id),
    key idx_author (author_id, create_time)
) engine = innodb
  default charset = utf8mb4;

create table if not exists tcc_transaction
(
    id          bigint unsigned not null auto_increment,
    xid         varchar(64)     not null,
    status      tinyint         not null default 0,
    expire_time datetime        not null,
    retry_count int             not null default 0,
    create_time datetime        not null default current_timestamp,
    update_time datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_xid (xid),
    key idx_status (status, create_time)
) engine = innodb
  default charset = utf8mb4;

create table if not exists tcc_branch
(
    id              bigint unsigned not null auto_increment,
    xid             varchar(64)     not null,
    branch_id       varchar(64)     not null,
    status          tinyint         not null default 0,
    payload         varchar(2048)   not null default '',
    error_message   varchar(512)    not null default '',
    next_retry_time datetime        not null default current_timestamp,
    retry_count     int             not null default 0,
    create_time     datetime        not null default current_timestamp,
    update_time     datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_xid_branch (xid, branch_id),
    key idx_retry (status, next_retry_time)
) engine = innodb
  default charset = utf8mb4;

create table if not exists tcc_inventory
(
    id          bigint unsigned not null auto_increment,
    product_id  bigint unsigned not null,
    available   int             not null default 0,
    frozen      int             not null default 0,
    create_time datetime        not null default current_timestamp,
    update_time datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_product (product_id)
) engine = innodb
  default charset = utf8mb4;

create table if not exists tcc_account
(
    id          bigint unsigned not null auto_increment,
    user_id     bigint unsigned not null,
    balance     bigint          not null default 0,
    frozen      bigint          not null default 0,
    create_time datetime        not null default current_timestamp,
    update_time datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_user (user_id)
) engine = innodb
  default charset = utf8mb4;

create table if not exists tcc_order
(
    id         bigint unsigned not null auto_increment,
    order_no   varchar(32)     not null,
    xid        varchar(64)     not null,
    user_id    bigint unsigned not null,
    product_id bigint unsigned not null,
    quantity   int             not null default 0,
    amount     bigint          not null default 0,
    status     tinyint         not null default 0,
    create_time datetime      not null default current_timestamp,
    update_time datetime      not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_order_no (order_no),
    unique key uk_xid (xid)
) engine = innodb
  default charset = utf8mb4;

create table if not exists mq_message_log
(
    id          bigint unsigned not null auto_increment,
    msg_id      varchar(64)     not null,
    topic       varchar(128)    not null default '',
    payload     text,
    status      tinyint         not null default 0,
    retry_count int             not null default 0,
    create_time datetime        not null default current_timestamp,
    update_time datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_msg (msg_id)
) engine = innodb
  default charset = utf8mb4;

create table if not exists cross_border_account
(
    id             bigint unsigned not null auto_increment,
    account_no     varchar(32)     not null default '',
    owner_name     varchar(64)     not null default '',
    country        varchar(8)      not null default '',
    currency       varchar(8)      not null default '',
    balance        decimal(18, 2)  not null default 0,
    frozen_balance decimal(18, 2)  not null default 0,
    kyc_level      tinyint         not null default 0,
    daily_limit    decimal(18, 2)  not null default 0,
    single_limit   decimal(18, 2)  not null default 0,
    status         tinyint         not null default 1,
    create_time    datetime        not null default current_timestamp,
    update_time    datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_account_no (account_no),
    key idx_currency (currency)
) engine = innodb
  default charset = utf8mb4;

create table if not exists cross_border_fx_quote
(
    id            bigint unsigned not null auto_increment,
    quote_no      varchar(32)     not null default '',
    currency_pair varchar(16)     not null default '',
    bid_rate      decimal(18, 8)  not null default 0,
    ask_rate      decimal(18, 8)  not null default 0,
    locked_rate   decimal(18, 8)  not null default 0,
    status        tinyint         not null default 0,
    expire_time   datetime        not null,
    remittance_no varchar(32)     not null default '',
    create_time   datetime        not null default current_timestamp,
    update_time   datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_quote_no (quote_no),
    key idx_pair_status (currency_pair, status),
    key idx_expire_time (expire_time)
) engine = innodb
  default charset = utf8mb4;

create table if not exists cross_border_remittance
(
    id                bigint unsigned not null auto_increment,
    remittance_no     varchar(32)     not null default '',
    idempotent_key    varchar(64)     not null default '',
    payer_account_id  bigint unsigned not null default 0,
    payee_account_id  bigint unsigned not null default 0,
    source_currency   varchar(8)      not null default '',
    target_currency   varchar(8)      not null default '',
    source_amount     decimal(18, 2)  not null default 0,
    exchange_rate     decimal(18, 8)  not null default 0,
    target_amount     decimal(18, 2)  not null default 0,
    fee_amount        decimal(18, 2)  not null default 0,
    channel           tinyint         not null default 1,
    status            tinyint         not null default 1,
    compliance_status tinyint         not null default 0,
    quote_no          varchar(32)     not null default '',
    batch_no          varchar(32)     not null default '',
    fail_reason       varchar(255)    not null default '',
    version           int             not null default 0,
    create_time       datetime        not null default current_timestamp,
    update_time       datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_remittance_no (remittance_no),
    unique key uk_idempotent_key (idempotent_key),
    key idx_status (status),
    key idx_batch_no (batch_no),
    key idx_create_time (create_time)
) engine = innodb
  default charset = utf8mb4;

create table if not exists cross_border_compliance_record
(
    id            bigint unsigned not null auto_increment,
    remittance_no varchar(32)     not null default '',
    check_type    tinyint         not null default 0,
    result        tinyint         not null default 0,
    hit_detail    varchar(512)    not null default '',
    create_time   datetime        not null default current_timestamp,
    update_time   datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    key idx_remittance_no (remittance_no)
) engine = innodb
  default charset = utf8mb4;

create table if not exists cross_border_account_ledger
(
    id            bigint unsigned not null auto_increment,
    ledger_no     varchar(32)     not null default '',
    remittance_no varchar(32)     not null default '',
    account_id    bigint unsigned not null default 0,
    direction     tinyint         not null default 0,
    currency      varchar(8)      not null default '',
    amount        decimal(18, 2)  not null default 0,
    balance_after decimal(18, 2)  not null default 0,
    create_time   datetime        not null default current_timestamp,
    update_time   datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_ledger_no (ledger_no),
    unique key uk_remittance_account_direction (remittance_no, account_id, direction),
    key idx_account_id (account_id),
    key idx_create_time (create_time)
) engine = innodb
  default charset = utf8mb4;

create table if not exists cross_border_settlement_batch
(
    id           bigint unsigned not null auto_increment,
    batch_no     varchar(32)     not null default '',
    channel      tinyint         not null default 1,
    currency     varchar(8)      not null default '',
    total_count  int             not null default 0,
    total_amount decimal(18, 2)  not null default 0,
    status       tinyint         not null default 0,
    cutoff_time  datetime        not null,
    create_time  datetime        not null default current_timestamp,
    update_time  datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_batch_no (batch_no),
    key idx_status (status)
) engine = innodb
  default charset = utf8mb4;

create table if not exists cross_border_recon_diff
(
    id             bigint unsigned not null auto_increment,
    batch_no       varchar(32)     not null default '',
    remittance_no  varchar(32)     not null default '',
    diff_type      tinyint         not null default 0,
    local_amount   decimal(18, 2)  not null default 0,
    channel_amount decimal(18, 2)  not null default 0,
    handle_status  tinyint         not null default 0,
    create_time    datetime        not null default current_timestamp,
    update_time    datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    key idx_batch_no (batch_no),
    key idx_remittance_no (remittance_no)
) engine = innodb
  default charset = utf8mb4;

create table if not exists cross_border_account_event
(
    id          bigint unsigned not null auto_increment,
    account_no  varchar(32)     not null default '',
    event_type  tinyint         not null default 0,
    reason      varchar(255)    not null default '',
    operator    varchar(64)     not null default '',
    create_time datetime        not null default current_timestamp,
    update_time datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    key idx_account_no (account_no)
) engine = innodb
  default charset = utf8mb4;
