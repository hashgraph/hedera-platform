/* Setup PostgreSQL extensions */

create schema if not exists crypto;
create extension if not exists pgcrypto with schema crypto;

/* Binary Objects Table */

create table binary_objects
(
	id        bigserial not null primary key,
	ref_count bigint    not null default 1,
	hash      bytea     not null,
	file_oid  oid       not null
);