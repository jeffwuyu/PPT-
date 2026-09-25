-- P02/P03: bind approved outline content to a reproducible snapshot hash.
alter table agent_plans add column approved_outline_hash varchar(64) null;
create index idx_agent_plans_approved_outline_hash on agent_plans (job_id, approved_outline_revision, approved_outline_hash);
