alter table agent_progress_events add column event_id varchar(64) null;
alter table agent_progress_events add column page_id varchar(128) null;
alter table agent_progress_events add column aggregate_revision bigint null;
create unique index uk_agent_progress_event_id on agent_progress_events(event_id);
create index idx_agent_progress_job_sequence on agent_progress_events(job_id, id);
