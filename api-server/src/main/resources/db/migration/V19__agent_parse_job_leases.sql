alter table agent_parse_jobs add column lease_token varchar(64) null;
alter table agent_parse_jobs add column lease_expires_at timestamp null;
create index idx_agent_parse_lease on agent_parse_jobs(status, lease_expires_at, id);
