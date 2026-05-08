-- Phase 05：协同增量、回放快照和高风险操作软锁。
-- 服务端不解析 Yjs 内容，只保存 base64 payload，并以自增 id 作为客户端可见的 updateId。
create table collaboration_updates (
    id bigint auto_increment primary key,
    presentation_id bigint not null,
    actor_user_id bigint not null,
    client_id varchar(120) not null,
    payload_base64 longtext not null,
    message_type varchar(40) not null,
    created_at timestamp not null
);

create table collaboration_snapshots (
    id bigint auto_increment primary key,
    presentation_id bigint not null,
    snapshot_update_id bigint null,
    -- 当前 snapshot 是可回放检查点 JSON，不是服务端合并后的 Yjs 文档。
    snapshot_json longtext not null,
    update_count int not null,
    created_at timestamp not null
);

create table collaboration_locks (
    id bigint auto_increment primary key,
    presentation_id bigint not null,
    target_type varchar(40) not null,
    target_id bigint not null,
    owner_user_id bigint not null,
    client_id varchar(120) not null,
    lock_token varchar(120) not null,
    operation varchar(80) not null,
    expires_at timestamp not null,
    -- released_at 为空且 expires_at 晚于当前时间时，业务层视为 active lock。
    released_at timestamp null,
    created_at timestamp not null,
    unique (lock_token)
);

create index idx_collab_updates_presentation_id on collaboration_updates (presentation_id, id);
create index idx_collab_snapshots_presentation_update on collaboration_snapshots (presentation_id, snapshot_update_id);
create index idx_collab_locks_active_target on collaboration_locks (presentation_id, target_type, target_id, released_at, expires_at);
