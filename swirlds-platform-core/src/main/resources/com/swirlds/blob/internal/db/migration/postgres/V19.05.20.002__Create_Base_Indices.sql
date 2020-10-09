/* Binary Objects Indices */

create unique index uidx_binary_objects_hash on binary_objects (hash);

create index uidx_binary_objects_ref_count on binary_objects (ref_count);