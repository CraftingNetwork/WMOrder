# Validation report

Validation performed on 2026-07-19:

- Java 21 source parsing: passed.
- Strict `javac --release 21 -Xlint:all` type compilation of all 99 main source files: passed against an isolated Paper/Vault/Placeholder/Hikari/Caffeine API contract harness.
- Compilation of all 8 test source files: passed.
- JUnit Jupiter execution: 17 tests found, 17 passed, 0 failed.
- YAML parsing for all six default configuration files: passed.
- Maven POM XML parsing: passed.
- SQLite and MySQL/MariaDB migration resource checks: passed.
- Scan for `TODO`, `FIXME`, and `UnsupportedOperationException` in main/test source: clean.
- Main-thread audit: SQL calls are routed through the bounded database executor; Bukkit inventory and Vault operations are marshalled to the server thread.

A full `mvn clean verify` was also attempted in the generation environment. It could not finish because that environment could not resolve external Maven/Paper repositories (DNS and repository gateway failures). This was an infrastructure resolution failure before normal dependency compilation, not a Java compiler or test failure. The included GitHub Actions workflow performs the official clean Maven verification in a normal networked build environment.

Run locally:

```bash
./tools/verify-project.sh
```
