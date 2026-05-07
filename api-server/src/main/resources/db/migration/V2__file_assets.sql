create table file_assets (
    id bigint auto_increment primary key,
    owner_user_id bigint not null,
    file_type varchar(40) not null,
    original_filename varchar(255) not null,
    content_type varchar(120) not null,
    byte_size bigint not null,
    object_key varchar(500) not null unique,
    public_url varchar(1000) null,
    status varchar(30) not null,
    upload_mode varchar(30) not null,
    failure_reason varchar(1000) null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_file_assets_owner foreign key (owner_user_id) references users (id)
);

create index idx_file_assets_owner on file_assets (owner_user_id);
create index idx_file_assets_type_status on file_assets (file_type, status);
