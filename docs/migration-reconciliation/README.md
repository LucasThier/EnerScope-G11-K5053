# Preserved incoming SQL variants

These files retain the incoming edits verbatim. They are outside Flyway discovery because their version numbers already exist in the deployed 
database. The executable V2, V8.5 and V9 files were restored from the published October 16 build, preserving applied checksums. 
The physical columns and distributions added by the incoming V2 are already supplied by V12, V17 and V18. The incoming V8.5 omits legacy result 
tables/links, which must be retained for existing data. V9 differs only in a comment. No SQL variant was deleted.

- Incoming V1.5, V11.5, V13.5 and V14.5 now execute as V20, V21, V22 and V23 respectively. 
- V20/V21 check existence because V17/V12 already provide their objects. 
- V22 follows the V14 conflict table and V23 creates version memberships.

## Archived B18 (2026-10-29)

B18__initial_schema.sql is retained verbatim here, outside Flyway discovery. Fresh installations now run individual V1–V23 scripts and satisfy the unchanged CI naming check. Existing databases with individual history are unaffected. Do not deploy this removal blindly to a database that recorded SQL_BASELINE B18: review its history and compatibility separately without automatic repair/reset. The local enerscope_fresh_check_oct25 database recorded B18; the active enerscope_run_oct15 database did not.
