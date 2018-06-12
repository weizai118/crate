==================
Unreleased Changes
==================

This file collects *unreleased* changes only.

For release notes, see:

  https://crate.io/docs/reference/en/latest/release_notes/index.html

For developers: changes should be recorded here (using RST syntax) as you are
developing CrateDB. When a new release is being cut, changes will be moved to
the appropriate section of the docs.

Breaking Changes
================

Changes
=======

- Added support for ``COPY FROM ... RETURN SUMMARY`` which will return a result
  set with detailed error reporting of imported rows.

- Added a new ``stats.jobs_log_filter`` setting which can be used to control
  what kind of entries are recorded into the ``sys.jobs_log`` table.
  In addition there is a new ``stats.jobs_log_persistent_filter`` setting which
  can be used to record entries also in the regular CrateDB log file.

- Expose statement classification in ``sys.jobs_log`` table.

- Added a ``sys.jobs_metrics`` table which contains query latency information.

- The setting ``es.api.enabled`` has been marked as deprecated and will be
  removed in a future version. Once removed it will no longer be possible to
  use the ES API.
  Please create feature requests if you're using the ES API and cannot use the
  SQL interface as substitute.

- Introduced the ``EXPLAIN ANALYZE`` statement for query profiling.

- Added ``typbasetype`` column to the ``pg_catalog.pg_type`` table.

- Added support for the ``SHOW TRANSACTION_ISOLATION`` statement.

Fixes
=====

- Handle ``STRING_ARRAY`` as argument type for user-defined functions correctly
  to prevent an ``ArrayStoreException``.
