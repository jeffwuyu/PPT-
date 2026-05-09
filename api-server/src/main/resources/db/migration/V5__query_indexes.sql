create index idx_slides_presentation_deleted_order on presentation_slides (presentation_id, is_deleted, page_order, id);

create index idx_elements_slide_deleted_order on slide_elements (slide_id, is_deleted, page_order, id);

create index idx_collab_snapshots_presentation_latest on collaboration_snapshots (presentation_id, snapshot_update_id, id);
