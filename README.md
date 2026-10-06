<img
  src="https://github.com/ebremer/dcm2rdf/raw/master/dcm2rdf.jpg"
  width=550px
  alt="dcm2rdf"
  title="dcm2rdf"
  style="display: inline-block; margin: 0 auto; max-width: 150px">
# dcm2rdf

Design philosophy - create a good faith RDF representation of DICOM metadata.

This program will read a specified source directory containing DICOM files and create a matching folder
with the same folder hierarchy as the source and with the same file names but with an RDF extension
(".ttl" Turtle by default, or ".nt" N-Triples via `-format`, optionally gzipped via `-c`).
This program's output has been modified to match and implement various discussions that have occurred 
within a community of people interested in working with DICOM using a RDF tool chain.

This program is hardly the first of its kind with several papers written on the subject.

*** Please note - this is a community effort and is not official, nor part of the [DICOM standard](https://www.dicomstandard.org/)

## About this program
It is a Java program developed using the [GraalVM](https://www.graalvm.org/) which will allow you to run the program as a Java runnable jar
or as a native image not requiring a JDK/JRE to be installed.  It is built upon [Apache Jena](https://github.com/apache/jena) and the [DCM4CHE](https://github.com/dcm4che/dcm4che) libraries.

## Building dcm2rdf jar version

1. Must have working JDK25 environment
2. `mvn -Pjar clean package`
3. A runnable jar, `dcm2rdf-<version>.jar`, will be in the target folder, `<version>` being the one in `pom.xml`

`java -jar target/dcm2rdf-<version>.jar -help` will display instructions, and
`scripts/smoke.sh target/dcm2rdf-<version>.jar` converts the synthetic set in `src/test/resources/smoke` with it
under every option.

## Building platform specific stand-alone

1. Must have at least JDK25 GraalVM CE 25.0.2 installed with fully functional [native-image](https://www.graalvm.org/latest/reference-manual/native-image/) build environment for the platform you are building for.
2. `mvn -Pnative clean package`
3. Artifact "dcm2rdf" will be in target folder.

`dcm2rdf -help` will display instructions.

The binary runs on any CPU of the build's architecture. Add `-Dnative.march=native` for a binary tuned to,
and only runnable on, CPUs like the build machine's.

`scripts/smoke.sh target/dcm2rdf` converts the synthetic set in `src/test/resources/smoke` under
every option. If a change makes it fail, the reachability metadata in `config/` is missing something:
rerun the converter on the JVM with `-agentlib:native-image-agent=config-merge-dir=config` across the
failing options, then rebuild.

## Roadmap
1) A paper documenting the referenced community effort.
2) More documentation
3) More RDF serializations (Turtle and N-Triples are supported via `-format`)
4) Link program output with existing official DICOM RDF terminology.
5) DICOM SHACL development
6) and much much more...

## Usage
```
Usage: dcm2rdf [options]
  Options:
  * -src
      Source Folder or File
  * -dest
      Destination Folder. For a single -src file, the output file, or an
      existing folder to write it in
    -t
      # of threads for processing.  Generally, one thread per file.
      Default: 1
    -c
      results file will be gzipped compressed
      Default: false
    -L
      Perform minimal conversion to RDF.  Warning - turns all tweaks and
      optimizations off!
      Default: false
    -version
      Display software version
      Default: false
    -status
      Display progress in real-time.
      Default: false
    -overwrite
      Overwrite results files.
      Default: false
    -help, -h
      Display help information
      Default: false
    -extra
      Add source file URI, file size
      Default: false
    -naming
      Subject method (SOPInstanceUID, SHA256)
      Default: SOPInstanceUID
    -oid
      Convert UI VRs to urn:oid:<oid>
      Default: false
    -hash
      Calculate SHA256 Hashes. Implied with SHA256 naming option.
      Default: false
    -level
      Sets logging level (OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER,
      FINEST, ALL)
      Default: SEVERE
    -wkt
      Known polygons expressed as GeoSPARQL WKT
      Default: false
    -detlef
      Detlefication - Generate URNs for bnodes in Sequences
      Default: false
    -cdt
      Convert lists to complex data types (CDT)
      Default: false
    -cdtlevel
      With -cdt, only convert lists of at least this many values
      Default: 4
    -ptags
      if ptags is true, add alternate private tag representation
      Default: false
    -includeinlinebinary
      Include inline binary values in the RDF output (by default only their
      size is recorded)
      Default: false
    -keywords
      Use keyword predicates instead of the tag-based
      Default: false
    -format
      RDF format type (TTL or NT)
      Default: TTL
      Possible Values: [TTL, NT]
    -logdir
      Directory for the run log file (only created if something is logged)
      Default: .
    -sniff
      Also convert files without a .dcm/.dat extension that carry the DICOM
      'DICM' marker
      Default: false
```

### Outputs, logs and exit codes

Each source file's output goes to the same place under `-dest` as the file is under `-src`, with its `.dcm` or
`.dat` extension replaced by the output's own: `.ttl`, or `.nt` with `-format NT`, plus `.gz` with `-c`. An
existing output is kept unless `-overwrite` is given. Archive members are written as `<archive>#<member>` beside
the archive's own path (see Archives below).

With a single file as `-src`, `-dest` is the output file: `-dest out` and `-dest out.ttl` both write `out.ttl`.
If `-dest` is an existing folder, the output goes into it, named after the source.

Problems are logged to the console and, at `-level` (SEVERE by default) and above, to
`dcm2rdf-<yyyy-MM-dd_HH-mm-ss>.log.ttl` in `-logdir` (the working directory by default). The log is Turtle: one
resource per record, with `:message`, `:level`, `:dateTime`, `:sourceMethodName` and its `:parameter`s, in the
`https://halcyon.is/logger/ns/` namespace. It is only created once something is logged. A file that fails to
convert is logged at SEVERE; a value that does not parse for its VR is reported once per file at WARNING (each
value at FINE), and kept in the output as described below.

The process exits with
- `0` when every source was converted, or already had its output;
- `1` when the run could not start: an unknown option or a bad value, a missing `-src`, or a `-logdir` that
  can't be created;
- `2` when the run finished, but some sources failed to convert.

The flags (`-c`, `-oid`, ...) take no value: each turns its option on.

## Output data model

Each DICOM instance becomes one subject, `urn:oid:<SOP Instance UID>` (or `urn:sha256:<hash of the file>`
with `-naming SHA256`). A file without its own SOP Instance UID, such as a DICOMDIR, is named by the Media
Storage SOP Instance UID of its file meta information. A SOP Instance UID that is not an OID cannot name the
instance, and the file fails; `-naming SHA256` converts it.

**Predicates** are `dcm:<gggg><eeee>` in hex, or with `-keywords` the attribute's keyword (`dcm:PatientID`).
Private attributes and the repeating groups (50xx, 60xx, 7Fxx), whose keyword is shared by all their groups,
always use hex. `dcm:` is `https://halcyon.is/dicom/ns/`.

**Values.** An attribute whose value multiplicity is always 1 holds its value directly; any other attribute
holds an `rdf:List` of its values, however many there are. Sequences always hold an `rdf:List` of their
items, even a single one. The long form (`-L`) keeps every attribute as `[ dcm:vr "LO" ; dcm:Value ( ... ) ]`.

| VR | Value |
|---|---|
| AE, AS, AT, CS, LO, LT, SH, ST, UC, UR, UT, UI | `xsd:string` (UI as a `urn:oid:` IRI with `-oid`, when it is an OID) |
| PN | a node with `dcm:Alphabetic`, `dcm:Ideographic`, `dcm:Phonetic` strings |
| DA | `xsd:date`; the pre-3.0 form `YYYY.MM.DD` is accepted |
| DT | the XSD type of its precision, with fraction and UTC offset kept: `xsd:gYear` (`2024`), `xsd:gYearMonth` (`202401`), `xsd:date` (`20240115`), else `xsd:dateTime` (a time cut short of seconds is completed with zeros) |
| TM | `xsd:time` |
| DS | `xsd:decimal`, or `xsd:double` when written with an exponent |
| IS, SS, US, SL, SV, UV | `xsd:integer` |
| UL | `xsd:unsignedInt` |
| FL, FD | `xsd:float`, `xsd:double`; NaN and infinities as `"NaN"`, `"INF"`, `"-INF"` |
| OB, OD, OF, OL, OV, OW, UN | a node `[ dcm:vr "OB" ; dcm:InlineBinaryOmitted <bytes> ]`, or `dcm:InlineBinary` with the base64 value with `-includeinlinebinary` |
| SQ | an `rdf:List` of item nodes |

A value that does not parse for its VR keeps its text, typed `dcm:invalid<VR>` (`"20241315"^^dcm:invalidDA`),
and the instance is flagged `dcm:invalidSOPInstance true`.

**Left out.** Bulk data (overlay planes, waveforms, encapsulated documents, ...) is never converted; the
attribute is kept as `[ dcm:vr "OW" ; dcm:BulkDataOmitted <bytes> ]`. Reading stops at Pixel Data
(7FE0,0010): attributes after it, such as trailing private groups or Digital Signatures (FFFA,FFFA), are not
converted.

**Archives.** A tar member is converted to `<archive>#<member>` beside the archive's other outputs, and is
identified as `file:///path/archive.tar#member` by `-extra`.

**Options**
- `-oid`: UI values that are OIDs become `urn:oid:` IRIs; any others stay strings.
- `-detlef`: sequence items get IRIs, `<instance>#<tag>/<index>` for the instance's own sequences and
  `<item>/<tag>/<index>` below them. Jena's IRI checker warns about these (`Invalid OID`): it does not expect
  a fragment on a `urn:oid:`.
- `-cdt`: lists of at least `-cdtlevel` plain values (no blank nodes) become one
  [CDT](http://w3id.org/awslabs/neptune/SPARQL-CDTs/) list literal.
- `-wkt`: ContourData becomes a GeoSPARQL `wktLiteral`: POINT, OPEN_PLANAR/OPEN_NONPLANAR (LINESTRING),
  CLOSED_PLANAR/CLOSEDPLANAR_XOR (POLYGON). Coordinates are converted from mm to metres and stated in
  EPSG:7706, a local right-handed 3D Cartesian engineering CRS standing in for the patient coordinate system.
  Contour data that doesn't fit its type stays a list.
- `-ptags`: private attributes are regrouped under `dcm:hasPrivateElement`, by private creator.

### SHACL shapes

`src/main/resources/shacl.ttl` holds a SHACL property shape for every data element of
DICOM PS3.6 (2026d), checking the form described above. They hold for the default output and with
`-keywords`, `-oid`, `-detlef`, `-cdt`, `-wkt`, `-ptags`, `-extra` and `-hash`; not for the long form.
The file is generated by `ShaclGenerator` (in the test sources) from the standard's DocBook source; regenerate it
for a new edition rather than edit it.

## Using dcm2rdf as a library

`Dcm2RdfBuilder` is the supported way to embed the converter; the other classes are internal and may change.

```java
Model model = new Dcm2RdfBuilder()
    .keywords(true)
    .oid(true)
    .toModel(Path.of("image.dcm"));
```

The command line's entry point is `com.ebremer.dcm2rdf.Dcm2RdfCli`, the jar's main class; the former
`com.ebremer.dcm2rdf.dcm2rdf` still runs it, but is deprecated.

It is published as `com.ebremer:dcm2rdf` to `https://cursus.bmi.stonybrookmedicine.edu/releases`, with
sources and javadoc. The command line's own dependencies (JCommander, progressbar, the SLF4J JUL binding) are
optional, so they don't reach applications that embed it.

## Tests

`mvn test` converts a synthetic set of DICOM files (`src/test/resources/smoke`, generated by `SmokeFixtures`)
under every option.

`mvn test -Pgolden` also converts two real, de-identified files from The Cancer Imaging Archive, which it
downloads into `target/golden-samples` and checks against pinned hashes. They are third-party data, so neither
they nor their conversions are stored in the repository; the expected output is kept as a digest. Their
sources, citations and licences are in `src/test/resources/golden/README.md`. A GitHub workflow runs them
weekly.

## References
- 2009 [Context-Driven Ontological Annotations In DICOM Images - Towards a semantic PACS](https://www.scitepress.org/PublishedPapers/2009/15502/15502.pdf)
- 2013 [DICOM metadata as RDF](https://dl.gi.de/items/6ae82b4a-c2c8-4d7e-b45b-088e82080f99) - <[Preprint](https://www.netestate.de/dicom/DICOM_metadata_as_RDF.pdf)> <[Source Code](https://github.com/Bonubase/dicom2rdf)>
- 2014 [Towards a semantic PACS: Using Semantic Web technology to represent imaging data](https://pmc.ncbi.nlm.nih.gov/articles/PMC5119276/)
- 2014 [RDF-ization of DICOM Medical Images towards Linked Health Data Cloud](https://link.springer.com/chapter/10.1007/978-3-319-13117-7_193)
- 2014 [Semantic Search over DICOM Repositories](https://ieeexplore.ieee.org/abstract/document/7052496)
- 2015 [An automatic method for the enrichment of DICOM metadata using biomedical ontologies](https://ieeexplore.ieee.org/abstract/document/7318912)
- 2015 [Toward a View-oriented Approach for Aligning RDF-based Biomedical Repositories](https://www.thieme-connect.com/products/ejournals/abstract/10.3414/ME13-02-0020)
- 2020 [FAIR-compliant clinical, radiomics and DICOM metadata of RIDER, interobserver, Lung1 and head-Neck1 TCIA collections](https://aapm.onlinelibrary.wiley.com/doi/full/10.1002/mp.14322)
- 2021 [A semantic database for integrated management of image and dosimetric data in low radiation dose research in medical imaging](https://pmc.ncbi.nlm.nih.gov/articles/PMC8075532/)