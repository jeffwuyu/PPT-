create table agent_generation_jobs (
    id bigint auto_increment primary key,
    agent_job_id bigint not null,
    owner_user_id bigint not null,
    presentation_id bigint not null,
    idempotency_key varchar(128) not null,
    status varchar(30) not null,
    revision bigint not null default 1,
    approved_outline_revision int not null,
    total_pages int not null,
    completed_pages int not null default 0,
    failure_reason longtext null,
    created_at timestamp not null,
    updated_at timestamp not null,
    unique key uk_generation_owner_agent_key (owner_user_id, agent_job_id, idempotency_key),
    key idx_generation_owner_status (owner_user_id, status, id)
);

create table agent_generation_page_tasks (
    id bigint auto_increment primary key,
    generation_job_id bigint not null,
    page_id varchar(128) not null,
    page_order int not null,
    status varchar(30) not null,
    attempts int not null default 0,
    failure_reason longtext null,
    created_at timestamp not null,
    updated_at timestamp not null,
    next_attempt_at timestamp null,
    unique key uk_generation_page (generation_job_id, page_id),
    key idx_generation_page_status (generation_job_id, status, page_order)
);
