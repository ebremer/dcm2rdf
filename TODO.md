# dcm2rdf — TODO

Findings from a full code review of `develop` @ `48711af` (2026-10-05). The baseline `mvn test` passes.

**Priorities**
- **P0**: silent data loss or wrong output. Fix before the next release.
- **P1**: broken features, large performance costs, or API contract bugs.
- **P2**: correctness, fidelity, or robustness issues in less common paths.
- **P3**: cleanup, conventions, and UX.

**Tags**
- 🔬 means the issue was reproduced with a scratch probe against the built classes.
- ⚠️ means the issue is likely but still needs confirming. Native-image items need a native build.

---

## P0: Critical

- [x] **One bad entry in a tar aborts the rest of the archive** 🔬 *(fixed: each entry is wrapped in its own try/catch and failures are counted per entry; test `PipelineTest.badTarEntriesDoNotAbortTheRestOfTheArchive`)*
  `FileProcessor.ProcessTar` (`DirectoryProcessor.java:186-222`) has no per-entry error handling. Any exception thrown for one entry (a missing SOP Instance UID, `InvalidPathException` from an entry name, an exception in post-processing) escapes the loop. It is caught once in `call()` (`:276`), so every later entry in the tar is skipped and only **1** failure is counted.
  Repro: a tar of `[bad.dcm (no 0008,0018), good.dcm, good2.dcm]` gives `failed=1` and neither good file is written.
  Fix: wrap each entry in try/catch, count each failure, and continue. `getNextEntry()` already skips the rest of a partially read entry.

- [x] **Truncated or corrupt DICOM produces partial RDF that is reported as success** 🔬 *(fixed: read errors are thrown as `UncheckedIOException`; the CLI counts a failure and writes nothing, and the builder throws `IOException`; tests `PipelineTest.truncatedFileFailsWithoutWritingOutput`, `Dcm2RdfBuilderTest.truncatedInputThrowsInsteadOfReturningAPartialModel`)*
  `DICOM2RDF.toModel(...)` (`DICOM2RDF.java:114-148`) catches `IOException`/`EOFException`, logs it, and returns whatever was parsed. If the SOP Instance UID was read before the error, the CLI writes the truncated output and counts it as a success (`failed=0`). `Dcm2RdfBuilder` returns the partial model even though its javadoc promises `@throws IOException`.
  Fix: propagate the exception, or return a result with an error flag. In the CLI, delete or skip the output and count a failure. In the builder, rethrow.

- [x] **DICOMDIR conversion always fails with the default naming** 🔬 *(fixed: naming falls back to (0002,0003); test `PipelineTest.dicomdirIsNamedByMediaStorageSOPInstanceUID`)*
  A DICOMDIR dataset has no (0008,0018) SOPInstanceUID, so `getSOPInstanceUID` returns empty and `ProcessDICOMasBytes2Model` throws `IllegalStateException("File missing SOP Instance UID")` (`DICOM2RDF.java:201-207`). Every DICOMDIR found during a walk is counted as failed.
  Fix: fall back to MediaStorageSOPInstanceUID (0002,0003), which is already in the model, and do the same for any file whose dataset lacks 0008,0018.

- [x] **Output name collisions silently drop data** 🔬 *(fixed: the first source to claim an output path wins and later ones fail with an error, on reruns too. The output naming scheme is unchanged, and the claim makes a unique temp file unnecessary within a run. Test `PipelineTest.collidingOutputNamesFailLoudly`)*
  `img.dcm` and `img.dat` in the same directory both map to `img.ttl`. One is skipped as "already done" and the summary still reports `failed=0`. With `-overwrite -t N`, both tasks can write the same `img.ttl.tmp` at the same time (`RandomUtils.java:92`).
  Fix: keep the source extension in the output name (e.g. `img.dcm.ttl`), or detect the collision and fail loudly. Use a unique temp file (`Files.createTempFile` in the target directory).

## P1: High

- [ ] **Single-item and multi-item sequences come out in different shapes** 🔬
  `OptimizeRemoveRDFListWhenAlwaysOne` (`DICOM2RDF.java:443-478`) unwraps every attribute whose SHACL shape has `sh:maxCount 1`. SQ attributes always have VM 1, but the item count is unbounded. As a result, a 1-item sequence becomes `dcm:0040A043 [ … ]` while a 2-item sequence stays `dcm:00081032 ( […] […] )`. Queries have to handle both forms.
  Fix: exclude SQ attributes, which have `dcm:vr "SQ"` before VR removal, from the unwrapping.

- [ ] **Post-processing costs about 90× the parse time, mostly in one step** 🔬
  Default options took about 65 ms per file, against 0.7 ms with `-L`. `OptimizeRemoveRDFListWhenAlwaysOne` alone took **about 62 ms per file**. On every file it copies the model into a new `Dataset` and joins it against the roughly 95k-line SHACL graph with `sh:path/sh:alternativePath/rdf:rest*/rdf:first`.
  Fix: compute the set of single-valued predicates once (a static `Set<Node>` built from `SHACL.getInstance()`), then unwrap in Java or with a `VALUES` block. Do not copy the model.

- [ ] **`-detlef` only names part of the sequence items** 🔬
  `GenSeqNames` (`DICOM2RDF.java:594-628`) runs once and filters `!isblank(?root)`, so:
  - items of nested sequences stay blank nodes;
  - single-item sequences are never named, because the earlier list-unwrapping step removed the list that `list:index` needs.

  Fix: walk the tree recursively in Java, or loop the update until nothing changes, and run it before (or independently of) list unwrapping.

- [ ] **`-keywords` merges repeating-group tags onto one predicate** 🔬
  `ElementDictionary.keywordOf(0x60003000)` and `keywordOf(0x60023000)` both return `OverlayData`. All overlay planes (60xx) and curve groups (50xx) collapse onto one predicate, and the group they came from is lost (`RDFWriter.java:199-211`).
  Fix: keep the hex form for repeating groups (`(tag & 0xFF000000)` is `0x50000000` or `0x60000000`), or add a group suffix.

- [ ] **Native-image reachability metadata is out of date** ⚠️
  `config/reachability-metadata.json` was last regenerated in 2025-08.
  - **Missing:** `rdf2cdtList`, `isDicomTag`, `ListPosition` (all registered through `FunctionRegistry.put(uri, Class)`, which instantiates them by reflection), `LogLevelConverter` and `NamingConverter` (JCommander instantiates them by reflection), and the ARQ `list:length` property function that `-cdt` uses.
  - **Stale:** entries for logback, which is not a dependency.
  - **Likely effect:** `-cdt`, `-level` and `-naming` fail in the native binary.

  Fix: regenerate with the tracing agent while running every option, and add a native smoke-test job to CI.

- [ ] **The native build is not portable**
  `pom.xml:143-151` sets `-march=native`, so the binary only runs on CPUs like the build machine. It also passes the obsolete `--no-server`, `<debug>true</debug>`, `<verbose>true</verbose>` and a hard-coded `-J-Xmx32G`. The execution uses the deprecated `build` goal (use `compile-no-fork`).
  Fix: use `-march=compatibility` for release builds and move the dev-only flags into a separate profile.

- [ ] **`-padleftzero` / `-sbu` fail the whole file when PatientID is non-numeric** 🔬
  `Tools.padWithZeros` calls `Long.valueOf(...)` (`Tools.java:13`), so `"AB12"` throws `NumberFormatException` and the whole file fails conversion. A negative ID such as `"-5"` becomes `"-0000005"`.
  Fix: left-pad the string with `'0'` without parsing it, or only pad when the ID matches `\d+`.

- [ ] **The builder's channel API breaks on logical names that are not paths** 🔬
  `toModel(Path, String name, SeekableByteChannel)` passes `name` through `Path.of(file)` (`DICOM2RDF.java:155`). On Windows, a name like `s3://bucket/key.dcm` or `urn:…` throws `InvalidPathException`. `Statistics` also calls `Path.of(name).toFile().length()` (`:125-126`, `:139-142`), which does a filesystem stat for logical names and records 0 bytes for tar entries.
  Fix: carry `name` as a `String` or `URI` all the way through, and pass the byte counts in explicitly.

## P2: Medium

### Data fidelity and data model
- [ ] **DT timezone and fractional seconds are dropped** 🔬 (`Convert.java:77-103`)
  `20240115123045+0500` becomes `2024-01-15T12:30:45`, which is a different instant. Partial-precision values are padded to precision that was never there: `2024` becomes `2024-01-01T00:00:00`. Carry the offset and fraction through, and decide whether reduced-precision values should map to `xsd:gYear`, `xsd:gYearMonth` or `xsd:date`.
- [ ] **DA values are emitted as `xsd:dateTime` instead of `xsd:date`**
  `Convert.toXsdDate` already exists but is unused. Decide on the mapping and record it in the data-model docs.
- [ ] **Non-finite FL/FD values are changed** (`RDFWriter.java:322-366`)
  ±Infinity is clamped to `±MAX_VALUE` and NaN becomes `dcm:Null`, but `xsd:float`/`xsd:double` can hold `INF`, `-INF` and `NaN` directly. Emit those lexical forms.
- [ ] **Invalid values are marked inconsistently** (`RDFWriter.java:268-315`)
  - DS and TM get the custom datatypes `…/invalidDS` and `…/invalidTM`, written as hard-coded strings instead of `DCM.NS`.
  - Invalid DA, DT and IS fall back to plain `xsd:string` with no marker at all.
  - Every `DCM.invalidSOPInstance` line is commented out, and `validSOP` is only logged.

  Pick one way to mark invalid values and apply it to every VR.
- [ ] **`getSOPInstanceUID` can pick a nested UID** (`DICOM2RDF.java:383-403`)
  It matches `?s dcm:00080018/...` on any subject with `LIMIT 1`, so a 0008,0018 inside a sequence item could become the subject IRI. Anchor the query to the root resource.
- [ ] **UIDs become IRIs without validation**
  `FlipURI("urn:oid:"+uid)` (`DICOM2RDF.java:203`) and `-oid`'s `iri(concat(...))` (`:572-592`) do not check the value. A malformed UID (spaces, letters) produces an invalid IRI or unparseable Turtle. Validate against `[0-9]+(\.[0-9]+)*` and fall back (to a hash or a string literal) when it does not match.
- [ ] **Bulk-data placeholders are misleading** (`RDFWriter.java:416-425`)
  Without `-includeinlinebinary` the output asserts `dcm:InlineBinary ""^^xsd:base64Binary`, which claims the value is empty. Excluded bulk elements disappear without any trace. Use an explicit "omitted" marker, e.g. `dcm:InlineBinaryOmitted true` plus the length.
- [ ] **Attributes after Pixel Data are silently dropped**
  `readDatasetUntilPixelData()` loses everything after (7FE0,0010): trailing private groups and the (FFFA,FFFA) digital signatures. Document this, or add an option to keep them.
- [ ] **The SHACL shapes in `shacl.ttl` are not valid SHACL**
  - They are `sh:NodeShape`s with `sh:path` and no targets.
  - Many have several `sh:datatype` values, which SHACL treats as a conjunction that no value can satisfy.
  - The datatypes disagree with what the converter emits (`xsd:string` for DA, TM, DS and IS).
  - They use `owl:sameAs` between properties where `owl:equivalentProperty` is meant.
  - The `:` prefix is misspelled `halycon`.
  - There is no generator script or note of the DICOM edition, so the file can't be regenerated when the dcm4che dictionary changes.

  This blocks the "DICOM SHACL" roadmap item.
- [ ] **`-wkt` edge cases** (`DICOM2RDF.java:498-555`)
  - It asserts CRS EPSG:7706 for patient-coordinate data converted from mm to m. Check and document that choice.
  - Only `CLOSED_PLANAR` is handled; `POINT` and `OPEN_PLANAR` are ignored.
  - ContourData that is not a multiple of 3, or contains an invalid DS literal, throws and fails the whole file.

### Robustness and error handling
- [ ] **DICOM files are only recognised by extension**
  Files without a `.dcm` or `.dat` extension, which are very common, are ignored. Add optional sniffing for the `DICM` preamble at offset 128.
- [ ] **Extension handling is inconsistent about case** 🔬
  `getFileType` lowercases the name but `StripExtension` (`RandomUtils.java:74`) is case-sensitive, so `IMG.DCM` becomes `IMG.DCM.ttl`.
- [ ] **Post-processing errors are printed and then ignored**
  `RDF2CDRLists` (`:369-378`), `GenSeqNames` (`:624-626`) and `PtagTweak` (`:697-706`) print to `System.out` and carry on with a half-processed model. `PtagTweak` even catches `Throwable` (including OOM) and calls `printStackTrace()`. Log through JUL and count the file as failed.
- [ ] **Run statistics are wrong**
  - Counters are only incremented when `-status` is on (`DirectoryProcessor.java:74-96`).
  - "Successful Conversions" (`FileCounter.java:139`) counts each tar as 1, ignores tar entries, and counts already-done files as successes.
  - "Fraction Read" is NaN when 0 bytes were read.
  - `Statistics` is a JVM-global singleton that library use (the builder) also writes to.
- [ ] **Two separate conversion pipelines have drifted apart**
  `ProcessDICOMasBytes2Model(Path, byte[])` (`DICOM2RDF.java:212-266`) differs from the stream path:
  - `-extra` computes and records a SHA-256 only on this path;
  - the URI is built differently;
  - `LongForm` is handled differently;
  - it creates a second `DICOM2RDF`.

  Move everything onto one path, since only tests use the byte path.

### Packaging and dependencies (embedding story)
- [ ] **Remove unused dependencies**: `jakarta.json-api` and `parsson` (`pom.xml:101-110`) are never imported.
- [ ] **Stop leaking CLI-only dependencies to library consumers**
  `slf4j-jdk14` (an SLF4J binding), `jcommander` and `progressbar` are compile-scoped. Anyone embedding `Dcm2RdfBuilder` gets them, and the binding will clash with their own logback or log4j.
  Fix: split into `dcm2rdf-core` and `dcm2rdf-cli` modules, or at least mark these `<optional>` or runtime-scoped.
- [ ] **Narrow the Jena dependency**
  `apache-jena-libs` (type `pom`) pulls in TDB1/TDB2, ShEx, RDF Patch and ontapi. The code needs `jena-arq`, plus the `SHACLM` namespace string, which can be inlined. This would shrink the jar and the native image.
- [ ] **`-version` prints `unknown` outside the shaded jar** 🔬
  The version only comes from the manifest that the `jar` profile writes (`dcm2rdf.java:29-34`). Plain builds report `unknown`, and native builds very likely do too, because they compile from `target/classes` and have no manifest. Fix: use a filtered `version.properties` resource.
- [ ] **Define the public API**
  Everything is `public`, and `Parameters` exposes mutable public fields. Document `Dcm2RdfBuilder` as the supported entry point, then add `module-info` exports or make the internals package-private. Publish sources and javadoc jars.

## P3: Low

### CLI and UX
- [ ] **Boolean flags can't be turned off explicitly** 🔬
  `-c false` throws `ParameterException` because Boolean fields have arity 0. As a result, `BooleanConverter`'s "false/f/0" handling and its `null == lowerCaseValue` branch are dead code.
- [ ] **Remove `Dcm2RdfValidator`**: it is a no-op attached to almost every option.
- [ ] **`-cdtlevel` accepts negative values** 🔬, and its description is wrong: the CLI help and README say "greater than", but the query uses `>=` (`DICOM2RDF.java:337`). Validate the value and fix the wording.
- [ ] **Clarify single-file `-src` semantics** 🔬
  The output goes to `<dest>.ttl`, so `-dest out.ttl` produces `out.ttl.ttl`.
- [ ] **Print `ParameterException` to stderr** (`dcm2rdf.java:95`).
- [ ] **Make the process exit code testable**: refactor `main` into `int run(String[] args)`. Exit codes are 0, 1 and 2; document them.
- [ ] **Remove unused parameters and return values**
  - `DirectoryProcessor.Protocol(FileType)` ignores its argument.
  - `FileProcessor` is a `Callable<Model>` that always returns `null`; make it a `Runnable`.
- [ ] **Show memory in MB**: `GetTotal`, `GetFree` and `GetMax` truncate to whole GiB, so small heaps print `0`.
- [ ] **Use `Locale.ROOT` for case changes**
  - `LogLevelConverter.java:16`: `"info".toUpperCase()` fails under a Turkish locale.
  - OS detection, `DirectoryProcessor.java:44`.
- [ ] **Handle `%` in `-logdir`**: `LazyFileHandler` passes the path as a `FileHandler` pattern, so a `%` in the directory name is read as a pattern token.

### Code hygiene
- [ ] **Remove the broken, unreachable `Attributes` writer path in `RDFWriter`** (`RDFWriter.java:123-182`)
  This covers `write(Attributes)`, `writeAttributes`, `writeAttribute` and `writeValue(Value, …)`. Nothing calls them except each other. The pops on `stack` are unbalanced, and the path never emits the tag predicate.
- [ ] **Remove other dead code**
  - `RDFWriter`: `dump()`, `replaceBulkDataURI` and its get/set, `DOUBLE_MAX_BITS`.
  - `DCM`: `_30060050`, `_30060042`, `patientID`, `invalidSOPInstance`.
  - `ListPosition` and `isDicomTag`: registered in `D2R` but never used in a query.
  - `Convert`: `toFL`, `toFD` and `removeTrailingDot`, which only tests use.
  - Commented-out code blocks.
- [ ] **Rename to Java conventions**, keeping deprecated aliases for anything public:
  - classes `dcm2rdf`, `rdf2cdtList`, `isDicomTag`, `isEvenDicomTag`, `isOddDicomTag`;
  - methods `DumpModel`, `StripExtension`, `Traverse`, `Protocol`, `FlipURI`, `ProcessDICOMasBytes2Model`, `GetFree`;
  - the catch-all class `RandomUtils`.
- [ ] **Fix the `OptimizeUR2URNOID` name and comment**: both say "UR", but the code handles the **UI** VR.
- [ ] **Swap `java.util.Stack` for `ArrayDeque`** (`RDFWriter.java:84-85`).
- [ ] **Close `QueryExecution`s** with try-with-resources (`DICOM2RDF.java:344, 396`), and use the `QueryExecution.model(m)` builder.
- [ ] **Speed up `FlipURI`**: it runs two full-scan SPARQL updates. `ResourceUtils.renameResource` does the same job.
- [ ] **Build `GEO`/`PROVO` constants with `ResourceFactory`**: both classes create a private `Model` just for that.
- [ ] **Clean up logging**
  - Every class logs under `dcm2rdf.class`; use per-class loggers.
  - Data-quality problems (invalid DS or TM values) are logged as SEVERE, once per value, which floods logs on large runs. Downgrade them to WARNING and consider rate-limiting.
- [ ] **Remove `nbactions-*.xml`**: they are committed with a personal absolute path (`D:\HalcyonStorage\tcga\coad`). Delete them, or `.gitignore` them.
- [ ] **Narrow the `.gitignore` `*.ttl` rule**: it would silently hide new `.ttl` test fixtures (`shacl.ttl` is force-tracked). Scope it to output folders.

## Tests and CI

- [ ] **Add a regression test for every P0 and P1 item above.** The scratch probes used for this review can be turned into tests.
- [ ] **Cover the untested options**: `-cdt`, `-detlef`, `-ptags`, `-wkt`, `-oid`, `-padleftzero`, `-c` (gzip), `-format NT`, `-L`, `-extra` on tar entries, DICOMDIR, `-keywords` combined with post-processing, and multi-threaded runs.
- [ ] **Add golden-file tests on real DICOM samples**, under licences that allow it:
  - implicit VR LE, big-endian and deflated transfer syntaxes;
  - non-ASCII Specific Character Sets;
  - private tags;
  - deeply nested sequences;
  - RTSTRUCT.
- [ ] **Add a round-trip test**: every output file must parse back with Jena, in both TTL and NT, and the run log `.log.ttl` must parse too.
- [ ] **Extend CI**
  - Run `mvn -Pjar package` plus a `java -jar target/dcm2rdf-*.jar -version` smoke test.
  - Add a Windows runner to the matrix; path, `#`, and zip-slip handling differ by OS.
  - Add an optional or nightly native-image job.
  - Add Dependabot for Maven and Actions.

## Documentation

- [ ] **Fix the README version**: it says `dcm2rdf-1.3.0.jar`, but the pom is `1.4.0`.
- [ ] **Document embedding**: `Dcm2RdfBuilder` usage, the Maven coordinates, and the repository URL from `distributionManagement`.
- [ ] **Document the output data model**:
  - hex vs. keyword predicates;
  - `dcm:Value` lists and when they are unwrapped;
  - `dcm:Null`;
  - the InlineBinary placeholder;
  - the `archive.tar#entry` naming;
  - naming strategies;
  - what each optimisation flag changes.
- [ ] **Document the run's outputs and limits**: exit codes, the log file location and format, and the fact that attributes after Pixel Data are skipped.
- [ ] **Add a CHANGELOG.**
