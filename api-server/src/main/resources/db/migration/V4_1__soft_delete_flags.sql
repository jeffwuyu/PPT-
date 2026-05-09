alter table project_members
    add column is_deleted varchar(1) not null default '0';

alter table presentations
    add column is_deleted varchar(1) not null default '0';

alter table presentation_slides
    add column is_deleted varchar(1) not null default '0';

alter table slide_elements
    add column is_deleted varchar(1) not null default '0';
