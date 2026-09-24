create table if not exists agent_generation_model_slots (
    slot_token varchar(64) primary key,
    expires_at timestamp not null
);
create index if not exists idx_agent_generation_model_slots_expiry on agent_generation_model_slots(expires_at);
create table if not exists agent_generation_model_slot_mutex (
    id integer primary key
);
insert into agent_generation_model_slot_mutex(id) values(1);
