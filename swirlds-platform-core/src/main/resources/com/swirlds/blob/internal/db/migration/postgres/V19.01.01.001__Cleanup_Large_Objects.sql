select pg_catalog.lo_unlink(loid)
from (select distinct loid from pg_catalog.pg_largeobject) as loid;