select formatReadableSize(sum(length(response_content))), formatReadableSize(sum(length(request_content))) from log;

select concat(database, '.', table)                         as table,
       formatReadableSize(sum(bytes))                       as size,
       sum(rows)                                            as rows,
       max(modification_time)                               as latest_modification,
       sum(bytes)                                           as bytes_size,
       any(engine)                                          as engine,
       formatReadableSize(sum(primary_key_bytes_in_memory)) as primary_keys_size
from system.parts
where active
group by database, table
order by bytes_size desc