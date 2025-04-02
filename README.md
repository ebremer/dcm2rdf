<img
  src="https://github.com/ebremer/dcm2rdf/raw/master/dcm2rdf.jpg"
  width=550px
  alt="dcm2rdf"
  title="dcm2rdf"
  style="display: inline-block; margin: 0 auto; max-width: 150px">
# dcm2rdf

Design philosophy - create a good faith RDF representation of DICOM metadata.

This program will read a specified source directory containing DICOM files and create a matching folder
with the same folder hierachy as the source and with the same file names but with a RDF turtle ".ttl" extension.
This program's output has been modified to match and implement various discussions that have occurred 
within a community of people interested working with DICOM using a RDF tool chain.

This program is hardly the first of it's kind with several papers written on the subject.

*** Please note - this is a community effort and is not offical, nor part of the [DICOM standard](https://www.dicomstandard.org/)

## About this program
It it a Java program developed using the [GraalVM](https://www.graalvm.org/) which will allow you to run the program as a Java runnable jar
or as a native image not requiring a JDK/JRE to be installed.  It is built upon [Apache Jena](https://github.com/apache/jena) and the [DCM4CHE](https://github.com/dcm4che/dcm4che) libraries.

## Building dcm2rdf jar version

1. Must have working JDK21 environment
2. `mvn -Pjar clean package`
3. A runnable jar version "dcm2rdf-1.0.0.jar" will be in the target folder

`java -jar dcm2rdf-1.0.0.jar -help` will display instructions.

## Building platform specific stand-alone

1. Must have at least JDK23 GraalVM CE 23.0.1+11.1 installed with fully functional [native-image](https://www.graalvm.org/latest/reference-manual/native-image/) build enviroment for the platform you are building for.
2. mvn -Pnative clean native:compile
3. Artifact "dcm2rdf" will be in target folder.

`dcm2rdf -help` will display instructions.

## Roadmap
1) A paper documenting the referenced community effort.
2) More documentation
3) Support other RDF serializations (only outputs Turtle at the moment)
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
      Destination Folder or File
    -t
      # of threads for processing.  Generally, one thread per file.
      Default: 1
    -c
      results file will be gzipped compressed
      Default: false
    -L
      Perform minimal conversion to RDF.  Warning - turns all tweaks and optimizations off!
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
      Sets logging level (OFF, ALL, WARNING, SEVERE)
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
      if cdt is true, only do mapping if list length is greater than this value 
      Default: 3
```
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