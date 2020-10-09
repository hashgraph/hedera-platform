create or replace function bsx_error_code_success_new()
	returns int
as
$BODY$
select 1;
$BODY$
	language sql
	immutable;



create or replace function bsx_error_code_success_exists()
	returns int
as
$BODY$
select 0;
$BODY$
	language sql
	immutable;



create or replace function bsx_error_code_not_found()
	returns int
as
$BODY$
select -1;
$BODY$
	language sql
	immutable;


create or replace function bsx_error_code_invalid_argument()
	returns int
as
$BODY$
select -2;
$BODY$
	language sql
	immutable;


create or replace function bsx_error_ctx_none()
	returns int
as
$BODY$
select 1024;
$BODY$
	language sql
	immutable;



create or replace function bsx_error_ctx_identifier()
	returns int
as
$BODY$
select 1025;
$BODY$
	language sql
	immutable;



create or replace function bsx_error_ctx_hash_list()
	returns int
as
$BODY$
select 1026;
$BODY$
	language sql
	immutable;



create or replace function bsx_error_ctx_ref_counts()
	returns int
as
$BODY$
select 1027;
$BODY$
	language sql
	immutable;



create or replace function bsx_error_src_none()
	returns int
as
$BODY$
select 4096;
$BODY$
	language sql
	immutable;



create or replace function bsx_error_src_self()
	returns int
as
$BODY$
select 4097;
$BODY$
	language sql
	immutable;



create or replace function bsx_error_src_blob_retrieve()
	returns int
as
$BODY$
select 4098;
$BODY$
	language sql
	immutable;


create or replace function bsx_error_src_blob_store()
	returns int
as
$BODY$
select 4099;
$BODY$
	language sql
	immutable;



create or replace function bsx_error_src_blob_append()
	returns int
as
$BODY$
select 4100;
$BODY$
	language sql
	immutable;



create or replace function bsx_error_src_blob_update()
	returns int
as
$BODY$
select 4101;
$BODY$
	language sql
	immutable;



create or replace function bsx_error_src_blob_delete()
	returns int
as
$BODY$
select 4102;
$BODY$
	language sql
	immutable;



create or replace function bsx_error_src_blob_restore()
	returns int
as
$BODY$
select 4103;
$BODY$
	language sql
	immutable;