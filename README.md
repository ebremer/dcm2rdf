<img
  src="https://github.com/ebremer/dcm2rdf/raw/master/dcm2rdf.jpg"
  width=550px
  alt="dcm2rdf"
  title="dcm2rdf"
  style="display: inline-block; margin: 0 auto; max-width: 150px">
# dcm2rdf

This is a conversion program that will read metadata from a DICOM dcm file and convert it to RDF.

## Building dcm2rdf jar version

1) Must have working JDK21 environment
2) mvn -Pjar clean package
3) A runnable jar version "dcm2rdf-x.y.z.jar" will be in the target folder

java -jar dcm2rdf-x.y.z.jar -help will display instructions.

## Building platform specific stand-alone

1) Must have at least JDK23 GraalVM CE 23.0.1+11.1 installed with fully functional build enviroment for [native-image](https://www.graalvm.org/latest/reference-manual/native-image/) for the platform you are building for.
2) mvn -Pnative clean native:compile
3) Artifact "dcm2rdf" will be in target folder.

"dcm2rdf -help" will display instructions.
