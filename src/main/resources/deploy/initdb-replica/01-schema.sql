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
