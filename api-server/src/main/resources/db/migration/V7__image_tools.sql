-- Phase 07: image candidate acquisition, VLM evaluation, and metadata persistence.
create table image_assets (
    id bigint auto_increment primary key,
    owner_user_id bigint not null,
    source_type varchar(30) not null,
    source_url varchar(1000) not null,
    public_url varchar(1000) not null,
    license_note varchar(500) not null,
    width int not null,
    height int not null,
    image_format varchar(30) not null,
    tags_json longtext not null,
    description_text longtext not null,
    scene_text varchar(500) not null,
    quality_score double not null,
    suitability_status varchar(30) not null,
    suitable_for_ppt boolean not null,
    metadata_json longtext not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_image_assets_owner_status on image_assets (owner_user_id, suitability_status, id);
create index idx_image_assets_source_type on image_assets (source_type, id);
