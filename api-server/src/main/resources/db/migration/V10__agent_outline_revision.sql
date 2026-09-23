-- P02: editable page-level outline with optimistic concurrency and approval snapshot.
alter table agent_plans add column outline_json longtext null;
alter table agent_plans add column outline_revision int not null default 1;
alter table agent_plans add column approved_outline_json longtext null;
alter table agent_plans add column approved_outline_revision int null;

update agent_plans
set outline_json = '{"pages":[]}'
where outline_json is null;

create index idx_agent_plans_outline_revision on agent_plans (job_id, outline_revision);
