# Code review — dcm2rdf

- **Scope:** full repository review at commit `aceebcc` (branch `develop`), 2026-07-01
- **Method:** all source files read; test suite run (originally 36 tests, 57 after the fixes, all green on JDK 25); the highest-risk findings were verified against the dcm4che 5.29.1 bytecode (`javap`) and live Jena 5.6 experiments (`jshell`) rather than code-reading alone
- **Status (2026-07-01):** every finding in this review — high (#1–#4), medium (#5–#12), and the low-severity/cleanup list — has been addressed in the working tree. Checkboxes track item-level status; the few consciously deferred sub-items are left unchecked with a note.

Overall: the core DICOM→RDF conversion is solid and thoughtfully designed (streaming `DicomInputHandler`, VR-aware typed literals, SPARQL-based post-processing), but the review found **two high-severity correctness bugs** — silent worker-thread failures and a wrong SHA-256 under bulk-data skipping — plus a cluster of medium issues concentrated in the less-traveled option paths (`-extra`, `-keywords`, tar handling).

---

## High severity

- [x] **1. Worker exceptions were silently swallowed — files vanished from the output with no log entry, and "Failed Conversions" stayed 0.**
  `DirectoryProcessor.java:124` submits `FileProcessor` (a `Callable`) to the executor and never inspects the returned `Future`, so any unchecked throw inside `call()` disappeared. Reachable throw paths included: missing SOPInstanceUID, `toDA`'s plain `IllegalArgumentException` slipping past the `VRFormatException`/`NumberFormatException` handlers, non-numeric PatientID under `-sbu`, missing `30060042`/degenerate contours under `-wkt`, and `#`-containing paths under `-extra`.
  **Fix:** `FileProcessor.call()` is wrapped in `catch (Throwable)` that logs the file path and increments `failedConversionFileCount` (previously only incremented in an unreachable branch).

- [x] **2. The recorded SHA-256 was wrong for files whose bulk data is skipped mid-stream** (`-hash` / `-naming SHA256`).
  Verified by disassembly: with `IncludeBulkData.NO`, dcm4che's `readValue` calls `skipFully(length)`, and `StreamUtils.skipFully` uses `InputStream.skip()` — which `FilterInputStream` delegates straight past the digest in `Sha256CalculatingInputStream`. Any non-trailing bulk attribute (waveforms, overlays, icon-image pixel data, encapsulated PDFs…) was skipped unhashed, so the emitted `urn:sha256:...` provenance didn't match the actual file.
  **Fix:** `skip(long)` now consumes bytes through `read()` so they pass through the digest; `mark`/`reset` are disabled (a rewind would double-digest). Regression-tested, including a mid-stream-skip case that fails against the old code by construction.

- [x] **3. Nested tars were mis-processed** (`DirectoryProcessor.ProcessTar`).
  The recursion reused the *same* `TarArchiveInputStream`, so an inner tar's contents were never parsed as a tar and the remaining outer entries were consumed under the nested root (wrong output paths).
  **Fix:** recurse with `new TarArchiveInputStream(tarInput)` (left unclosed on purpose — closing it would close the outer stream); nested archives are counted via the previously-unused `incrementTarTarFileCount()`.

- [x] **4. Tar processing halted at the first already-converted entry.**
  `nohalt=false` on `STAT.ALREADYDONE` stopped reading the whole archive, so a run interrupted mid-tar never completed without `-overwrite`.
  **Fix:** the `nohalt` mechanism was removed — every entry is examined, and the per-entry existence check still skips work that is already done.

---

## Medium severity

- [x] **5. `-keywords` mode: unknown tags collapsed onto the bare namespace URI.**
  Verified in bytecode: `Keyword.valueOf` returns `""` for unknown tags, so `RDFWriter` created the property `https://halcyon.is/dicom/ns/` itself as the predicate for *every* unknown tag. Relatedly, two hex-based post-processors silently no-op'd in keywords mode: PatientID padding (`-sbu`) and WKT conversion (`-wkt`).
  *Correction found during the fix:* the SHACL-driven list flattening was **not** broken — `shacl.ttl`'s `sh:alternativePath` lists already carry both forms, e.g. `( dcm:00100020 dcm:PatientID )` — and `PtagTweak` is unaffected because private tags are always emitted in hex.
  **Fix:** `RDFWriter` falls back to the hex form for null/empty/`PrivateCreatorID` keywords; `PadLeftZero8`/`OptimizePolygons2WKT` match both hex and dictionary-keyword predicates via a shared `tagProperties(int)` helper (keywords verified live: `PatientID`, `ContourData`, `ContourGeometricType`); the WKT pass now skips (rather than crashes on) contours missing `ContourGeometricType`.

- [x] **6. `-extra` provenance URIs were wrong for paths with spaces or `#`.**
  The manual `.replace(" ", "%20")` before `Path.toUri()` double-encoded (verified live: `my file.dcm` → `...my%2520file.dcm`); a `#` in a filename was misread as a URI fragment and two `#` threw; tar entries recorded the CWD as their derivation and a `FileSize` of 0.
  **Fix:** manual pre-encoding dropped (`Path.toUri()`/the multi-arg `URI` constructor do the encoding); the `<archive>#<entry>` convention is kept but split on the first `#` only; tar members receive a real `sourcetar#entry` reference plumbed through `ProcessTar`; `bib:FileSize` is emitted only when the path resolves to a real file.

- [x] **7. DICOMDIR output path was inconsistent; tar entries kept their `.dcm` names; DICOMDIR-in-tar was dead code.**
  The DICOMDIR pre-check tested `frag + ".ttl[.gz]"` but wrote to bare `frag` (reconverted every run, no extension); tar-entry outputs were Turtle in files named `...#img.dcm`; `getFileType(String)` never returned `DICOMDIR`.
  **Fix:** DICOMDIR writes to the extensioned path it checks (with the real source path for provenance); tar entries get `StripExtension` + the format extension; `getFileType(String)` recognizes a `DICOMDIR` basename; `StripExtension` strips only the trailing extension (unit-tested). *Note: output names for tar entries and DICOMDIRs changed, so outputs from earlier runs are reconverted once.*

- [x] **8. "Failed to create" checked the wrong variable, and failed writes left partial output.**
  The check tested the *source* path (always exists) instead of `dest`, and `DumpModel` could leave a truncated file that later runs treated as done.
  **Fix:** the check tests `dest`, logs via JUL, counts the failure, and returns `STAT.FAILED`. `DumpModel` writes to `<dest>.tmp` and `Files.move(..., REPLACE_EXISTING)`s into place, deletes the temp on failure, propagates `IOException`/`RiotException` (so the finding-1 handler counts write failures), and uses `Files.createDirectories` (no `getParent()` NPE). Unit-tested.

- [x] **9. `-level` was broken for documented values; `-naming` accepted anything; `-padleftzero` was wired to nothing.**
  `OFF` was advertised but unhandled and unknown levels silently became `SEVERE`; `-naming sha256` silently fell through to SOPInstanceUID mode; padding was actually controlled by the hidden `-sbu` flag.
  **Fix:** `LogLevelConverter` uses `Level.parse` (OFF/SEVERE/WARNING/INFO/CONFIG/FINE/FINER/FINEST/ALL, case-insensitive) and rejects unknown values with a `ParameterException`; a new `NamingConverter` normalizes and validates `-naming`; `-padleftzero` now triggers the padding pass (alongside `-sbu`). Unit-tested.

- [x] **10. One unreadable directory aborted the entire run, and exit codes were always 0.**
  `Files.walk` throws `UncheckedIOException` lazily, which nothing caught; `main` exited 0 on missing source and on conversion failures.
  **Fix:** traversal now uses `Files.walkFileTree` with `visitFileFailed`/`postVisitDirectory` logging and continuing (submission stays on the worker pool, where the real work happens). Exit codes: 1 for missing source or log-handler failure (messages on stderr), 2 when the run ends with failed conversions.

- [x] **11. SHA256-naming on the `byte[]` path was incoherent.**
  The hash was only computed under `extra && !LongForm`, so `-naming SHA256` without `-extra` threw a misleading `"File missing SOP Instance UID"` error; `Optional.of` would NPE on a null hash.
  **Fix:** the hash is computed whenever `-hash`, `-naming SHA256`, or `-extra` needs it (mirroring the stream path); `-hash` also emits the `urn:sha256`/`cry:sha256` triples on this path; `Optional.ofNullable`; both missing-hash throws now say `"SHA256 hash not calculated for <file>"` as `IllegalStateException`.

- [x] **12. POM issues.**
  The dcm4che-core exclusion had groupId/artifactId swapped (excluded nothing); `commons-compress` was used directly but only present transitively via `jena-base` (verified with `dependency:tree`); `dcm4che-deident`, `dcm4che-json`, `dcm4che-tool-common` were never imported; two parameters shared `order = 21`.
  **Fix:** exclusion un-swapped; `commons-compress` 1.28.0 declared explicitly; the three unused dcm4che artifacts removed (build and tests pass without them; `dcm4che-dict` kept); `-format`/`-sbu` renumbered to orders 22/23.

---

## Low severity / cleanup

- [x] **JUL parameter misuse** — messages missing `{0}` placeholders (`DICOM2RDF` "End of File", both zero-length-file messages), wrong parameters (`root` where the file was meant, ×2), the NaN branch logging an "Infinity" message, 3-args-for-2-placeholders in the float/double writers, one inline `Logger.getLogger(...).log(SEVERE, null, ex)`, and the 3-arg `RDFWriter` constructor leaving `src` null (so hash-mode messages printed `null`). All fixed; the constructor now uses the file path as `src`.
- [x] **`throw new Error(...)` for ordinary data problems** — none remain: the `-extra` throw became `IllegalArgumentException` (finding 6), the missing-hash throws became `IllegalStateException` (finding 11), the missing-SOPInstanceUID throws are now `IllegalStateException`, and `flattenList2JsonArray`'s always-throwing switch was deleted with the method.
- [x] **Perf** — `SHACL` is now a lazily-initialized shared singleton (the shapes were re-read and re-queried from `shacl.ttl` for *every file*; the model is read-only so concurrent readers are safe) and it fails fast with a clear message if the resource is missing (previously an NPE). `Convert`'s per-call `Pattern.compile`/`String.matches` in `toDA`/`toTM`/`toDS`/`toIS` were hoisted to static `Pattern`s.
- [x] **`StripExtension` stripped every `.dcm`/`.dat` occurrence in the path** — fixed with finding 7 (suffix-only, unit-tested).
- [x] **`PositiveInteger` accepted 0** — now rejects `n < 1` and reports non-numeric values as a `ParameterException` instead of a raw `NumberFormatException` (so `-t 0` fails with a clear message rather than an obscure `ThreadPoolExecutor` error).
- [x] **Validation gaps in `Convert`** — all-whitespace DS no longer becomes `""^^xsd:decimal` (trim before the empty check); the DS pattern requires at least one digit (rejects `"."`, `"+"`); TM minutes (≤59) and seconds (≤60, DICOM leap second) are range-checked; the TM error message no longer says "DS"; `toDA` no longer computes the fraction/offset it discards (output unchanged: truncated to whole seconds) and throws `VRFormatException` instead of a plain `IllegalArgumentException` (so malformed dates are flagged per-value instead of failing the file); **DT values are now converted** (`case DA, DT ->` routes both through the datetime parser). All covered by new tests.
  - [x] The `toDA`/`toDT` naming swap is resolved: the parsers are now `toXsdDateTime` (DA/DT-style input → `xsd:dateTime`) and `toXsdDate` (strict `YYYYMMDD` → `xsd:date`), with `toDA`/`toDT` kept as `@Deprecated` delegating aliases so existing callers keep their exact behavior. The RDF *output* is unchanged — DA values still deliberately emit `xsd:dateTime`; switching them to `xsd:date` would be an output-format change for downstream consumers and remains a separate decision.
- [x] **Dead/leftover code** — removed: the `testing/` package (3 scratch `main` classes), `cdt2rdfList` (unregistered, misnamed copy of `ListPosition`), `libs/padWithZeros` (never registered), `flattenList2JsonArray` (threw for every literal datatype), `convertRDFListXYToWKT`, `DirectoryProcessor.buffer`/`AddModel`, `Traverse`'s unused `allowedfiletypes`/`ftype` parameters, `DCM._00081190`, the always-false `single` parameter (and its dead branches) threading through `RDFWriter`, `Convert.main` and the unused `TIME_PATTERN`/`DECIMALPATTERN`, `TestME` and stale `// TestME(m)` comments, `SHACL.OnlyOne` (and the `onlyone` query), `HashGeneratorUtils` trimmed to the one method in use (`HashGenerationException` deleted with it), and the debug `System.out` dump in `ListPosition.build()`. `isEvenDicomTag`/`isOddDicomTag` now share a `DicomTagParityFunction` base class.
  - [ ] `Parameters.help`/`version` fields kept intentionally — the `@Parameter` annotations are what make `-help`/`-version` appear in the `jc.usage()` listing (the flags themselves are handled before parsing).
- [x] **`getSOPInstanceUID` used `Literal.toString()`** — now `getString()`, so a typed literal can't leak `^^datatype` into the `urn:oid:` URI.
- [x] **Docs** — README updated: output-extension text covers `-format`/`-c`, jar name matches version 1.3.0, `-includeinlinebinary` description un-inverted, `-format` added to the usage block, `-level` values updated, roadmap item 3 marked supported, typos fixed (hierarchy/official/It is/environment/its/interested in).
- [x] **Log file naming and creation** — the per-run JUL log is now `dcm2rdf-<timestamp>.log.ttl` (can't be confused with `.ttl` data outputs), is only created when something is actually logged (`LazyFileHandler` defers the `FileHandler` until the first published record; unit-tested), and its location is configurable with the new `-logdir` option.
  - [ ] PHI consideration (informational, decision for the maintainer): error messages can embed malformed field *values* (usually numeric fragments) and file paths in the log — worth keeping in mind in clinical settings; a redaction flag would be the fix if this matters for your deployments.

## Things checked that are NOT bugs

- `len == -1` in `RDFWriter.readValue` is correct — dcm4che's `toLongOrUndefined` deliberately preserves `-1` for undefined lengths (verified by disassembly; the initial suspicion was wrong).
- The iterate-while-mutating pattern in `PadLeftZero8`/`OptimizePolygons2WKT` did **not** throw — verified live against Jena 5.6's `GraphMem`. Both methods now collect subjects with `.toList()` before mutating anyway (done with finding 5), so this is moot.
- `Statistics`/`FileCounter` are correctly synchronized/atomic for the traversal + executor usage.

## Suggested priority (as of the original review)

- [x] 1. Future/exception handling in `FileProcessor.call()` (finding 1) — smallest change, biggest payoff, unlocks trustworthy failure stats.
- [x] 2. `skip()` override in `Sha256CalculatingInputStream` (finding 2).
- [x] 3. Tar fixes (3, 4, 7) and the `-extra`/`-keywords` URI issues (5, 6).
- [x] 4. The POM exclusion swap + `commons-compress` declaration (12).

## Remaining follow-ups

- [x] **Pipeline-level tests** — `PipelineTest` builds synthetic DICOM in memory with dcm4che and runs it through the real pipeline: byte[]-to-model conversion (subject named by SOPInstanceUID), a full directory run asserting the Turtle output exists and the recorded `cry:sha256` equals an independently computed digest of the file, and a tar containing a subdirectory entry, a **nested tar**, and a non-DICOM entry — asserting both outputs land at the expected `archive.tar#...` paths and that a second run over existing outputs completes cleanly. These directly exercise the code paths behind findings 1–4. Suite total: 57 tests.
- [x] **CI** — a GitHub Actions workflow (`.github/workflows/ci.yml`) runs `mvn -B test` on JDK 25 (Temurin) for every push and pull request.
