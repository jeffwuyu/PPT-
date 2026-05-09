create table user_levels (
    id bigint auto_increment primary key,
    code varchar(40) not null unique,
    display_name varchar(80) not null,
    rank_order int not null,
    max_projects int not null,
    agent_daily_quota int not null,
    created_at timestamp not null
);

create table users (
    id bigint auto_increment primary key,
    email varchar(180) not null unique,
    username varchar(80) not null unique,
    password_hash varchar(255) not null,
    level_id bigint not null,
    status varchar(30) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table roles (
    id bigint auto_increment primary key,
    code varchar(60) not null unique,
    display_name varchar(80) not null,
    created_at timestamp not null
);

create table permissions (
    id bigint auto_increment primary key,
    code varchar(80) not null unique,
    description varchar(255) not null,
    created_at timestamp not null
);

create table role_permissions (
    role_id bigint not null,
    permission_id bigint not null,
    primary key (role_id, permission_id)
);

create table user_roles (
    user_id bigint not null,
    role_id bigint not null,
    primary key (user_id, role_id)
);

create table teams (
    id bigint auto_increment primary key,
    name varchar(120) not null,
    owner_user_id bigint not null,
    created_at timestamp not null
);

create table project_members (
    id bigint auto_increment primary key,
    project_id bigint not null,
    user_id bigint not null,
    project_role varchar(40) not null,
    can_edit boolean not null,
    created_at timestamp not null,
    unique (project_id, user_id)
);

create table refresh_tokens (
    id bigint auto_increment primary key,
    user_id bigint not null,
    token_hash varchar(128) not null unique,
    status varchar(30) not null,
    expires_at timestamp not null,
    revoked_at timestamp null,
    created_at timestamp not null
);

create table quota_consumptions (
    id bigint auto_increment primary key,
    user_id bigint not null,
    quota_type varchar(60) not null,
    amount int not null,
    business_key varchar(120) not null,
    created_at timestamp not null
);

create table audit_logs (
    id bigint auto_increment primary key,
    user_id bigint null,
    action varchar(120) not null,
    resource_type varchar(80) not null,
    resource_id varchar(120) null,
    detail varchar(1000) null,
    created_at timestamp not null
);

insert into user_levels (code, display_name, rank_order, max_projects, agent_daily_quota, created_at)
values
    ('FREE', 'Free', 1, 3, 5, current_timestamp),
    ('PRO', 'Pro', 2, 30, 100, current_timestamp),
    ('ENTERPRISE', 'Enterprise', 3, 1000, 10000, current_timestamp);

insert into permissions (code, description, created_at)
values
    ('PROJECT_VIEW', 'View presentation projects', current_timestamp),
    ('PROJECT_EDIT', 'Edit presentation projects', current_timestamp),
    ('PROJECT_ADMIN', 'Manage presentation projects', current_timestamp),
    ('AGENT_RUN', 'Run PPT generation agents', current_timestamp);

insert into roles (code, display_name, created_at)
values
    ('ROLE_USER', 'User', current_timestamp),
    ('ROLE_EDITOR', 'Editor', current_timestamp),
    ('ROLE_ADMIN', 'Admin', current_timestamp);

insert into role_permissions (role_id, permission_id)
select r.id, p.id from roles r, permissions p
where r.code = 'ROLE_USER' and p.code in ('PROJECT_VIEW', 'AGENT_RUN');

insert into role_permissions (role_id, permission_id)
select r.id, p.id from roles r, permissions p
where r.code = 'ROLE_EDITOR' and p.code in ('PROJECT_VIEW', 'PROJECT_EDIT', 'AGENT_RUN');

insert into role_permissions (role_id, permission_id)
select r.id, p.id from roles r, permissions p
where r.code = 'ROLE_ADMIN' and p.code in ('PROJECT_VIEW', 'PROJECT_EDIT', 'PROJECT_ADMIN', 'AGENT_RUN');
