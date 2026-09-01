# Article-relevant source extract

This directory preserves package paths for the implementation classes needed
to inspect the evaluated write, integrity-evidence, checkpoint, strict-chain,
TRBAC, and benchmark paths.

It is intentionally **not** the complete deployable registry application and
is not advertised as a standalone Maven project.  Controllers, templates,
unrelated business functions, production configuration, and operational data
remain in the private `GAS` repository.

Important entry points:

- `bench/MicrobenchmarkRunner.java` - revised single, concurrent, isolation,
  and K-sensitivity benchmark stages.
- `service/IntegrityService.java` - FINGERPRINT, CHECKPOINT, and STRICT
  strategies and verification routines.
- `dao/IntegrityLogDao.java` - persistence of events, strict log rows, chain
  state, and checkpoints.
- `service/FreqCrudService.java` and `dao/FreqDao.java` - measured service-layer
  update and fresh database re-read.
- `util/SecurityUtil.java` - canonical 23-field HMAC material and fail-closed
  external-secret resolution.
- `security/TrbacPerRequestFilter.java`, `security/WebSecurityConfig.java`, and
  `service/TrbacSettingsService.java` - per-request temporal authority control,
  explicit placement immediately after `SecurityContextPersistenceFilter`,
  and the filter-chain exclusion boundary.

Except for the documented comment-only sanitization of `WebSecurityConfig.java`,
the files are copied unchanged from the supplied latest private-code archive.
