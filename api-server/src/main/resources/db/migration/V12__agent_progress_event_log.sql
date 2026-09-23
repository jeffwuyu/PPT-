create table agent_progress_events (
    id bigint auto_increment primary key,
    job_id bigint not null,
    presentation_id bigint not null,
    checkpoint_id bigint null,
    event_type varchar(80) not null,
    event_data longtext not null,
    occurred_at timestamp not null,
    key idx_agent_progress_job_cursor (job_id, id)
);
