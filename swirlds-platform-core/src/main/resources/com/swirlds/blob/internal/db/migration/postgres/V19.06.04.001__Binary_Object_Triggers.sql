create or replace function bs_blob_delete_trigger()
	returns trigger
as
$BODY$
declare
	result int;
begin
	select lo_unlink(old.file_oid) into result;
	return new;
end;
$BODY$
	language plpgsql
	volatile;

-- create trigger tgr_binary_objects_delete
-- 	before delete
-- 	on binary_objects
-- 	for each row
-- execute procedure bs_blob_delete_trigger();