-- Phase 04：PPT JSON 模型的标准存储结构，层级为 Presentation -> Slide -> Element。
create table presentations (
    id bigint auto_increment primary key,
    owner_user_id bigint not null,
    title varchar(120) not null,
    page_width int not null,
    page_height int not null,
    theme_json longtext not null,
    status varchar(30) not null,
    current_version_number int not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    is_deleted varchar(1) not null default '0'
);

create table presentation_slides (
    id bigint auto_increment primary key,
    presentation_id bigint not null,
    page_order int not null,
    title varchar(120) null,
    background_json longtext not null,
    speaker_notes longtext null,
    created_at timestamp not null,
    updated_at timestamp not null,
    is_deleted varchar(1) not null default '0'
);

create table slide_elements (
    id bigint auto_increment primary key,
    slide_id bigint not null,
    element_key varchar(80) not null,
    element_type varchar(40) not null,
    x_position double not null,
    y_position double not null,
    width_value double not null,
    height_value double not null,
    rotation double not null,
    -- Phase 04：z_index 表示视觉层级；page_order 表示标准 JSON 结构顺序。
    z_index int not null,
    page_order int not null,
    -- Phase 04：initially_present 为 true 表示元素来自页面或模板初始化。
    initially_present boolean not null,
    -- Phase 04：JSON 当前以文本保存，由 PresentationService 负责解析与序列化。
    content_json longtext not null,
    style_json longtext not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    is_deleted varchar(1) not null default '0',
    unique (slide_id, element_key)
);

create table presentation_versions (
    id bigint auto_increment primary key,
    presentation_id bigint not null,
    version_number int not null,
    reason varchar(255) null,
    -- Phase 04：保存创建版本时的不可变标准预览 JSON 快照。
    snapshot_json longtext not null,
    created_by_user_id bigint not null,
    created_at timestamp not null,
    unique (presentation_id, version_number)
);

create index idx_presentations_owner_status on presentations (owner_user_id, status);
create index idx_slides_presentation_order on presentation_slides (presentation_id, page_order);
create index idx_elements_slide_order on slide_elements (slide_id, page_order);
create index idx_elements_slide_z_index on slide_elements (slide_id, z_index);
create index idx_versions_presentation_number on presentation_versions (presentation_id, version_number);
