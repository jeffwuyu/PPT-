alter table agent_generation_page_tasks add column lease_token varchar(64) null;
alter table agent_generation_page_tasks add column lease_expires_at timestamp null;
create index idx_generation_page_lease on agent_generation_page_tasks (status, lease_expires_at, generation_job_id);
