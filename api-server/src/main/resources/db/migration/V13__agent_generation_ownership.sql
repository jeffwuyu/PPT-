alter table agent_jobs add column active_generation_job_id bigint null;
create unique index uk_agent_generation_owner on agent_jobs (active_generation_job_id);
