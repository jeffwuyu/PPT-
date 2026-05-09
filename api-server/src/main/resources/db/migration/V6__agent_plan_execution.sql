-- Phase 06: Agent plan-execution state, checkpoint recovery, memory, tools, and artifacts.
create table agent_jobs (
    id bigint auto_increment primary key,
    owner_user_id bigint not null,
    presentation_id bigint not null,
    status varchar(30) not null,
    prompt_text longtext not null,
    failure_reason longtext null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table agent_sources (
    id bigint auto_increment primary key,
    job_id bigint not null,
    source_type varchar(30) not null,
    file_asset_id bigint null,
    content_text longtext null,
    metadata_json longtext not null,
    created_at timestamp not null
);

create table agent_plans (
    id bigint auto_increment primary key,
    job_id bigint not null,
    plan_markdown longtext not null,
    plan_json longtext not null,
    version_number int not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table agent_checkpoints (
    id bigint auto_increment primary key,
    job_id bigint not null,
    plan_id bigint not null,
    checkpoint_order int not null,
    title varchar(160) not null,
    objective longtext not null,
    status varchar(30) not null,
    max_iterations int not null,
    current_iterations int not null,
    failure_reason longtext null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table agent_messages (
    id bigint auto_increment primary key,
    job_id bigint not null,
    checkpoint_id bigint null,
    role_name varchar(30) not null,
    content_text longtext not null,
    metadata_json longtext not null,
    created_at timestamp not null
);

create table agent_memories (
    id bigint auto_increment primary key,
    job_id bigint not null,
    presentation_id bigint not null,
    user_id bigint not null,
    memory_scope varchar(30) not null,
    memory_kind varchar(40) not null,
    content_text longtext not null,
    compressed boolean not null,
    archived boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table agent_tool_calls (
    id bigint auto_increment primary key,
    job_id bigint not null,
    checkpoint_id bigint null,
    tool_name varchar(80) not null,
    request_json longtext not null,
    response_json longtext null,
    status varchar(30) not null,
    duration_ms bigint not null,
    error_message longtext null,
    created_at timestamp not null
);

create table agent_artifact_refs (
    id bigint auto_increment primary key,
    job_id bigint not null,
    checkpoint_id bigint null,
    artifact_type varchar(30) not null,
    artifact_id bigint not null,
    action_name varchar(40) not null,
    active boolean not null,
    metadata_json longtext not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table agent_plan_revisions (
    id bigint auto_increment primary key,
    job_id bigint not null,
    previous_plan_id bigint not null,
    new_plan_id bigint not null,
    reason longtext not null,
    checkpoint_id bigint null,
    created_at timestamp not null
);

create index idx_agent_jobs_owner_status on agent_jobs (owner_user_id, status);
create index idx_agent_jobs_presentation on agent_jobs (presentation_id, id);
create index idx_agent_sources_job on agent_sources (job_id, source_type);
create index idx_agent_plans_job_version on agent_plans (job_id, version_number);
create index idx_agent_checkpoints_job_order on agent_checkpoints (job_id, checkpoint_order);
create index idx_agent_messages_job_checkpoint on agent_messages (job_id, checkpoint_id, id);
create index idx_agent_memories_job_scope on agent_memories (job_id, memory_scope, archived);
create index idx_agent_tool_calls_job_checkpoint on agent_tool_calls (job_id, checkpoint_id, id);
create index idx_agent_artifact_refs_job_checkpoint on agent_artifact_refs (job_id, checkpoint_id, active);
