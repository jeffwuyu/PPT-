create table agent_parse_jobs (
    id bigint auto_increment primary key,
    owner_user_id bigint not null,
    file_asset_id bigint not null,
    agent_job_id bigint null,
    idempotency_key varchar(128) not null,
    status varchar(30) not null,
    parser_version varchar(64) null,
    source_hash varchar(128) null,
    total_blocks int not null default 0,
    revision bigint not null default 1,
    failure_reason longtext null,
    created_at timestamp not null,
    updated_at timestamp not null,
    unique key uk_agent_parse_idempotency (owner_user_id, file_asset_id, idempotency_key),
    key idx_agent_parse_owner_status (owner_user_id, status, id)
);

create table agent_source_blocks (
    id bigint auto_increment primary key,
    parse_job_id bigint not null,
    block_id varchar(160) not null,
    block_type varchar(40) not null,
    text longtext not null,
    locator_json longtext not null,
    created_at timestamp not null,
    unique key uk_agent_source_block (parse_job_id, block_id),
    key idx_agent_source_block_job (parse_job_id, id)
);
