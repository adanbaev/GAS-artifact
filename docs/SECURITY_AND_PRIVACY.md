# Security and privacy boundary

The following supplied materials are intentionally excluded from the public
v1.3.1 package:

- the full private `GAS` archive;
- the manuscript DOCX and its author/tracked-change metadata;
- the restored v1.2 ZIP (it remains a separate historical release);
- the private `application.properties` containing a literal database account;
- the private `data.sql` containing a user record;
- operational registry rows and production secrets.

The public configuration contains no embedded credential or secret value.
Datasource credentials and both required secrets are supplied externally; the
application fails at startup when either secret is absent, blank, or a
documented placeholder.  The SQL fixture contains one invented
owner/site/frequency tuple and no person, organization, account, password hash,
production identifier, or operational measurement.

The selected Java sources may contain public package names and generic domain
terminology, but no embedded datasource password, HMAC secret, email address,
or registry row is intended to be present.  `SHA256SUMS.txt` permits later
verification that the reviewed files were not silently changed.

`WebSecurityConfig.java` is included to make the TRBAC filter-chain placement
and bypass boundary inspectable.  Non-functional comments were removed; the
executable filter ordering and request matchers are unchanged from the supplied
private source.

The generated Surefire XML originally contained Maven's automatically captured
local system properties, including a Windows username, absolute paths, and the
full dependency classpath.  The public copies remove only the XML `<properties>`
element.  Suite names, testcase names, timings, and all result counters remain
unchanged.  The companion TXT reports are included unchanged.
