use bi_system;
-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_userAccount (userAccount)
    ) comment '用户' collate = utf8mb4_unicode_ci;

 create table if not exists chat
           (
               id           bigint auto_increment comment 'id' primary key,
               goal         text                           null comment '分析目标',
               chartData    text                           null comment '图表数据',
               chartType    varchar(128)                   null comment '图表类型',
               userId       bigint                         null commnet '用户id',
               genChart     text                           null comment '生成的图表',
               genResult    text                           null comment '生成的数据',
               createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
               updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
               isDelete     tinyint      default 0                 not null comment '是否删除'
           ) comment '图表信息表' collate = utf8mb4_unicode_ci
