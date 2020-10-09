create or replace function bs_blob_retrieve(in pId bigint,
											out pOid oid,
											out pErrorCode int,
											out pErrorSrc int[],
											out pErrorCtx int)
as
$BODY$
begin

	pErrorCode := bsx_error_code_success_exists();
	pErrorSrc := array [bsx_error_src_blob_retrieve()]::int[];
	pErrorCtx := bsx_error_ctx_none();

	select obj.file_oid from binary_objects as obj where obj.id = pId into pOid;

	if pOid is null then
		pErrorCode := bsx_error_code_not_found();
		pErrorCtx := bsx_error_ctx_identifier();
		return;
	end if;

end;
$BODY$
	language plpgsql
	volatile;

create or replace function bs_blob_retrieve_by_hash(in pHash bytea,
													out pBytes bytea,
													out pErrorCode int,
													out pErrorSrc int[],
													out pErrorCtx int)
as
$BODY$
begin

	pErrorCode := bsx_error_code_success_exists();
	pErrorSrc := array [bsx_error_src_blob_retrieve()]::int[];
	pErrorCtx := bsx_error_ctx_none();

	-- 	select obj.content from binary_objects as obj where obj.hash = pHash into pBytes;

-- 	if pBytes is null then
-- 		pErrorCode := bsx_error_code_not_found();
-- 		pErrorCtx := bsx_error_ctx_identifier();
-- 		return;
-- 	end if;

end;
$BODY$
	language plpgsql
	volatile;



create or replace function bs_blob_lookup_id_by_hash(in pHash bytea,
													 out pId bigint,
													 out pErrorCode int,
													 out pErrorSrc int[],
													 out pErrorCtx int)
as
$BODY$
begin

	pErrorCode := bsx_error_code_success_exists();
	pErrorSrc := array [bsx_error_src_blob_retrieve()]::int[];
	pErrorCtx := bsx_error_ctx_none();

	select obj.id from binary_objects as obj where obj.hash = pHash into pId;

	if pId is null then
		pErrorCode := bsx_error_code_not_found();
		pErrorCtx := bsx_error_ctx_identifier();
		return;
	end if;

end;
$BODY$
	language plpgsql
	volatile;


create or replace function bs_blob_store(in pHash bytea,
										 in pOid oid,
										 out pId bigint,
										 out pErrorCode int,
										 out pErrorSrc int[],
										 out pErrorCtx int)
as
$BODY$
declare
	foundId      bigint;
	unlinkResult int;
	currentOid   oid;
begin

	pErrorCode := bsx_error_code_success_new();
	pErrorSrc := array [bsx_error_src_blob_store()]::int[];
	pErrorCtx := bsx_error_ctx_none();

	select bo.id, bo.file_oid from binary_objects as bo where hash = pHash into foundId, currentOid;

	if foundId is not null and foundId > 0 then
		if currentOid is not null and pOid is not null and not pOid = currentOid then
			select fx from lo_unlink(pOid) as fx into unlinkResult;
		end if;

		update binary_objects set ref_count = ref_count + 1 where id = foundId returning id into pId;
	else
		insert into binary_objects (id, ref_count, hash, file_oid)
		values (DEFAULT, 1, pHash, pOid) returning id into pId;
	end if;


end ;
$BODY$
	language plpgsql
	volatile;

create or replace function bs_blob_exists(in pHash bytea)
	returns bigint
as
$BODY$
begin
	return (select bo.id from binary_objects as bo where hash = pHash limit 1);
end;
$BODY$
	language plpgsql
	stable;

create or replace function bs_blob_append(in pId bigint,
										  in pBytes bytea,
										  out pNewId bigint,
										  out pHash bytea,
										  out pErrorCode int,
										  out pErrorSrc int[],
										  out pErrorCtx int)
as
$BODY$
declare
	newContent      bytea;
	deleteErrorCode int;
	deleteErrorSrc  int[];
	deleteErrorCtx  int;
begin

	pErrorCode := bsx_error_code_success_exists();
	pErrorSrc := array [bsx_error_src_blob_append()]::int[];
	pErrorCtx := bsx_error_ctx_none();

	-- 	select (obj.content || pBytes) from binary_objects as obj where obj.id = pId for share into newContent;
--
-- 	if newContent is null then
-- 		pErrorCode := bsx_error_code_not_found();
-- 		pErrorCtx := bsx_error_ctx_identifier();
-- 		return;
-- 	end if;
--
-- 	select sto.pId, sto.pHash
-- 	from bs_blob_store(newContent) as sto into pNewId, pHash;
--
-- 	select del.pErrorCode, del.pErrorSrc, del.pErrorCtx
-- 	from bs_blob_delete(pId) as del into deleteErrorCode, deleteErrorSrc, deleteErrorCtx;

-- 	if deleteErrorCode != bsx_error_code_success_exists() then
-- 		pErrorCode := deleteErrorCode;
-- 		pErrorSrc := deleteErrorSrc;
-- 		pErrorCtx := deleteErrorCtx;
-- 		return;
-- 	end if;

end;
$BODY$
	language plpgsql
	volatile;



create or replace function bs_blob_update(in pId bigint,
										  in pBytes bytea,
										  out pNewId bigint,
										  out pHash bytea,
										  out pErrorCode int,
										  out pErrorSrc int[],
										  out pErrorCtx int)
as
$BODY$
declare
	currentId bigint;
begin

	pErrorCode := bsx_error_code_success_exists();
	pErrorSrc := array [bsx_error_src_blob_update()]::int[];
	pErrorCtx := bsx_error_ctx_none();

	-- 	select obj.id from binary_objects as obj where obj.id = pId for update into currentId;
--
-- 	if currentId is null or currentId <= 0 then
-- 		pErrorCode := bsx_error_code_not_found();
-- 		pErrorCtx := bsx_error_ctx_identifier();
-- 		return;
-- 	end if;
--
-- 	select fx.pId, fx.pHash, fx.pErrorCode, fx.pErrorSrc, fx.pErrorCtx
-- 	from bs_blob_store(pBytes) as fx into pNewId, pHash, pErrorCode, pErrorSrc, pErrorCtx;
--
-- 	if pErrorCode < 0 then
-- 		return;
-- 	end if;
--
-- 	select fx.pErrorCode, fx.pErrorSrc, fx.pErrorCtx
-- 	from bs_blob_delete(pId) as fx into pErrorCode, pErrorSrc, pErrorCtx;
--
-- 	if pErrorCode < 0 then
-- 		return;
-- 	end if;

end;
$BODY$
	language plpgsql
	volatile;



create or replace function bs_blob_delete(in pId bigint,
										  out pDeleted boolean,
										  out pOid oid,
										  out pErrorCode int,
										  out pErrorSrc int[],
										  out pErrorCtx int)
as
$BODY$
declare
	currentRefCount bigint;
	currentId       bigint;
-- 	current         int;
begin

	pErrorCode := bsx_error_code_success_exists();
	pErrorSrc := array [bsx_error_src_blob_delete()]::int[];
	pErrorCtx := bsx_error_ctx_none();

	pDeleted := false;

	select id, ref_count from binary_objects where id = pId into currentId, currentRefCount;

	if currentId is null or currentId <= 0 then
		pErrorCode := bsx_error_code_not_found();
		pErrorCtx := bsx_error_ctx_identifier();
		return;
	end if;

	if currentRefCount <= 1 then
		delete from binary_objects where id = pId returning id, file_oid into currentId, pOid;
		pDeleted := true;
	else
		update binary_objects set ref_count = ref_count - 1 where id = pId returning id, file_oid into currentId, pOid;
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

/**

 */
create or replace function bs_blob_restore(in pRefCounts bigint[],
										   in pBlobHashes bytea[],
										   out pIds bigint[],
										   out pErrorCode int,
										   out pErrorSrc int[],
										   out pErrorCtx int)
as
$BODY$
declare
	deletedObjects bigint[];
begin

	pErrorCode := bsx_error_code_success_new();
	pErrorSrc := array [bsx_error_src_blob_restore()]::int[];
	pErrorCtx := bsx_error_ctx_none();

	if pRefCounts is null or array_length(pRefCounts, 1) <= 0 then
		pErrorCode := bsx_error_code_invalid_argument();
		pErrorCtx := bsx_error_ctx_hash_list();
		return;
	end if;

	if pBlobHashes is null or not array_length(pBlobHashes, 1) = array_length(pRefCounts, 1) then
		pErrorCode := bsx_error_code_invalid_argument();
		pErrorCtx := bsx_error_ctx_ref_counts();
		return;
	end if;

	update binary_objects as b set ref_count = 0 where not b.ref_count = 0;

	with inserts as (
		update binary_objects as b set ref_count = t.ref_count
			from unnest(pRefCounts, pBlobHashes) with ordinality as t(ref_count, hash, idx)
			where b.hash = t.hash
			returning id, t.idx
	)
	select array_agg(sorted_ids.id)
	from (
			 select ti.id
			 from inserts as ti
			 order by ti.idx) as sorted_ids
	into pIds;


	with cleanup as (
		select obj.id, obj.file_oid from binary_objects as obj where obj.ref_count = 0
	), purge as (
		select lo_unlink(cl.file_oid), cl.id
		from cleanup as cl
	)
	select array_agg(array [pg.id]::bigint[])
	from purge as pg into deletedObjects;

	delete from binary_objects where id = any(deletedObjects);

end;
$BODY$
	language plpgsql
	volatile;

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
	pErrorSrc := array [bsx_error_src_blob_delete()]::int[];
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
