alter table agent_parse_jobs add column ocr_provider varchar(80) null;
alter table agent_parse_jobs add column ocr_model varchar(120) null;
alter table agent_parse_jobs add column ocr_request_id varchar(160) null;
alter table agent_parse_jobs add column ocr_confidence_json longtext null;
