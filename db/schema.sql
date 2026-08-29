create database if not exists dong_lab default character set utf8mb4 collate utf8mb4_general_ci;

use dong_lab;

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

create database if not exists dong_lab_replica default character set utf8mb4 collate utf8mb4_general_ci;

use dong_lab_replica;

create table if not exists user_account
(
    id          bigint unsigned not null auto_increment,
    user_id     bigint unsigned not null,
    username    varchar(64)     not null default '',
    balance     bigint          not null default 0,
    create_time datetime        not null default current_timestamp,
    update_time datetime        not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_user (user_id)
) engine = innodb
  default charset = utf8mb4;
