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
