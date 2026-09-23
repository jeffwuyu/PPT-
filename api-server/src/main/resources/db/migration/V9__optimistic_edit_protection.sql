-- P06: server-side optimistic concurrency and persistent human protection.
alter table slide_elements add column revision int not null default 1;
alter table slide_elements add column locked boolean not null default false;
create index idx_elements_slide_revision on slide_elements (slide_id, revision);
