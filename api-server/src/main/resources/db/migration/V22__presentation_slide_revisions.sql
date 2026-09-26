alter table presentations add column revision int not null default 1;
alter table presentation_slides add column revision int not null default 1;
