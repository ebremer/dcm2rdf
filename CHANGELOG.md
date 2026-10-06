# Changelog

Notable changes to dcm2rdf, newest first. Released versions match the repository's tags.

## 1.4.0 (unreleased)

### Output changes

The same DICOM converts to different RDF in places. Queries over 1.3 output may need changes.

- Sequences are always an `rdf:List` of their items. Before, a sequence with one item was unwrapped to the
  item itself.
- DA values are `xsd:date`, not `xsd:dateTime`. The pre-3.0 form `YYYY.MM.DD` is accepted.
- DT values keep their precision, fractional seconds and UTC offset: `xsd:gYear`, `xsd:gYearMonth`,
  `xsd:date` or `xsd:dateTime`. Before, every DT became an `xsd:dateTime` padded with zeros, without its
  fraction or offset.
- FL and FD values that are NaN or infinite are written `"NaN"`, `"INF"` and `"-INF"`. Before, NaN became
  `dcm:Null` and infinities the largest finite value.
- A value that does not parse for its VR keeps its text, typed `dcm:invalid<VR>`, and the instance gets
  `dcm:invalidSOPInstance true`. Before, only DS and TM were marked, and invalid DA, DT and IS values became
  plain strings.
- A binary value left out of the output is recorded as `dcm:InlineBinaryOmitted <bytes>`. Before, it was
  written as an empty `dcm:InlineBinary`, which claims the value is empty.
- Bulk data (overlay planes, waveforms, ...) is recorded as `[ dcm:vr ... ; dcm:BulkDataOmitted <bytes> ]`.
  Before, those attributes were dropped without a trace.
- A file without a SOP Instance UID, such as a DICOMDIR, is named by its Media Storage SOP Instance UID.
  Before, it failed to convert.
- With `-keywords`, the repeating groups (50xx, 60xx, 7Fxx) keep their hex predicates. Before, all the
  groups of an attribute were merged onto its one keyword.
- With `-detlef`, items of nested sequences and of one-item sequences are named too, as `<item>/<tag>/<index>`.
- With `-oid`, only values that are OIDs become `urn:oid:` IRIs; anything else stays a string.
- With `-wkt`, POINT and open contours are converted too. A contour whose data doesn't fit its type stays a
  list, where before it failed the file.
- With `-cdt`, lists holding blank nodes stay `rdf:List`s. Before, they became literals with blank node labels
  in their text.
- `shacl.ttl` is regenerated as valid SHACL from DICOM PS3.6 2026d, and describes the output above.

### Fixes

- A truncated or corrupt file fails. Before, it could be written out partially and counted as converted.
- A bad tar entry fails on its own. Before, it ended the conversion of the rest of the archive.
- Tar entries whose names would place their output outside `-dest` are rejected.
- Two sources that map to one output (`img.dcm` and `img.dat`) fail loudly. Before, one was skipped as
  already converted.
- An error in post-processing fails the file. Before, it was printed and the file written anyway.
- The instance is named by its own SOP Instance UID, never one inside a sequence item. One that is not an
  OID fails the file, with a pointer to `-naming SHA256`.
- `-padleftzero` leaves IDs that aren't numbers alone. Before, they failed the file.
- File extensions are recognised in any case: `IMG.DCM` becomes `IMG.ttl`.
- The native image works with every option. Before, `-cdt`, `-cdtlevel` and others failed in it. It runs on
  any CPU of its architecture, unless built with `-Dnative.march=native`.
- `-version` reports the version in every build, the native image included.
- A `%` in the `-logdir` path is taken literally.

### Command line

- `-sniff` also converts files and tar entries without a `.dcm` or `.dat` extension that carry the DICOM
  `DICM` marker.
- With a single file as `-src`, `-dest` is the output file, given with or without its extension, or an
  existing folder to write it into. Before, `-dest out.ttl` wrote `out.ttl.ttl`.
- Errors in the options go to stderr, with the usage. The exit codes, 0, 1 and 2, are documented.
- `-cdtlevel` must be at least 1.
- A value that does not parse is logged once per file at WARNING, and each value at FINE. Before, each one
  was logged at SEVERE.
- The run summary counts files converted and files already converted, whether or not `-status` is on.
- `-status` reports memory in MB.
- The main class is `com.ebremer.dcm2rdf.Dcm2RdfCli`. The former `com.ebremer.dcm2rdf.dcm2rdf` still runs
  it, but is deprecated.
- Converting with the default options is about 14 times faster: a small file took about 65 ms, and now takes
  about 5.

### Library

- `Dcm2RdfBuilder` converts a file, or a channel, to a Jena `Model`, with the command line's options. It
  is the supported API; the other classes are internal and may change.
- It throws `IOException` when the source can't be read as DICOM, and accepts URIs (`s3://...`) as the
  logical name of a channel.
- The command line's own dependencies (JCommander, progressbar, the SLF4J JUL binding) are optional, so they
  don't reach applications that embed the library. Jena comes in as `jena-arq` only.
- Sources and javadoc jars are published, to `https://cursus.bmi.stonybrookmedicine.edu/releases`.

### Build

- CI runs the tests on Linux and Windows, and smoke-tests the runnable jar and the native image over a
  synthetic set of DICOM files under every option. Dependabot keeps Maven dependencies and Actions current.
- Golden-file tests (`mvn test -Pgolden`, and weekly in CI) download two real, de-identified files from The
  Cancer Imaging Archive and check their conversion. The files are not stored in the repository.

## 1.3.1 (2026-07-06)

- dcm4che 5.34.3, and other dependency updates.

## 1.3.0 (2026-07-02)

- `-format` writes Turtle or N-Triples.
- Logging is unified on java.util.logging, and the Turtle run log escapes what it records.
- First unit tests.

## 1.2.0 (2025-08-21)

- `-keywords` uses attribute keywords as predicates.
- `-ptags` groups private attributes under their private creator.
- `-includeinlinebinary` includes binary values.
- Invalid DS and TM values are handled.
- A tar file already converted is skipped.

## 1.1.0 (2025-05-07)

- `-cdt` and `-cdtlevel` convert lists to CDT list literals.

## 1.0.0 (2025-03-06)

- First release.
