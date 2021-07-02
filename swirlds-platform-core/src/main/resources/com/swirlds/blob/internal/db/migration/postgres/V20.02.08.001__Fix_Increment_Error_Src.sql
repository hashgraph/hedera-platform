create or replace function bsx_error_src_blob_increment_ref_count()
	returns int
as
$BODY$
select 4104;
$BODY$
	language sql
	immutable;

create or replace function bs_blob_increment_ref_count(in pId bigint,
													   out pErrorCode int,
													   out pErrorSrc int[],
													   out pErrorCtx int)
as
$BODY$
declare
	currentRefCount bigint;
	currentId       bigint;
begin

	pErrorCode := bsx_error_code_success_exists();
	pErrorSrc := array [bsx_error_src_blob_increment_ref_count()]::int[];
	pErrorCtx := bsx_error_ctx_none();

	select ref_count from binary_objects where id = pId into currentRefCount;

	if currentRefCount > 0 then
		update binary_objects set ref_count = ref_count + 1 where id = pId returning id, file_oid into currentId;
	end if;


	if currentId is null or currentId <= 0 then
		pErrorCode := bsx_error_code_not_found();
		pErrorCtx := bsx_error_ctx_identifier();
		return;
	end if;

end;
$BODY$
	language plpgsql
	volatile;