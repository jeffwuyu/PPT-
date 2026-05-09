-- Phase 09: immutable PPTX export records and published artifacts.
create table presentation_exports (
    id bigint auto_increment primary key,
    presentation_id bigint not null,
    version_number int not null,
    status varchar(30) not null,
    pptx_file_id bigint null,
    cover_file_id bigint null,
    thumbnail_file_ids_json longtext not null,
    created_by_user_id bigint not null,
    failure_reason longtext null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_presentation_exports_presentation_version on presentation_exports (presentation_id, version_number);
create index idx_presentation_exports_status on presentation_exports (status);
