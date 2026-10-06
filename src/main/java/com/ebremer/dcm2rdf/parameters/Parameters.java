package com.ebremer.dcm2rdf.parameters;

import com.beust.jcommander.Parameter;
import java.io.File;
import java.util.logging.Level;
import org.apache.jena.riot.RDFFormat;

/**
 * The command line's options. The flags take no value: each turns its option on, and all are
 * off by default.
 *
 * @author erich
 */
public class Parameters {

    public enum RdfFormat {
        TTL("ttl", RDFFormat.TURTLE_PRETTY),
        NT("nt", RDFFormat.NTRIPLES);
        private final String extension;
        private final RDFFormat format;

        RdfFormat(String extension, RDFFormat format) {
            this.extension = extension;
            this.format = format;
        }

        public String getExtension() {
            return this.extension;
        }

        public RDFFormat getRDFFormat() {
            return this.format;
        }

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Compress : ").append(compress).append("\n");
        sb.append("Format   : ").append(format).append("\n");
        return sb.toString();
    }

    @Parameter(names = {"-src"}, converter = FileConverter.class, description = "Source Folder or File", required = true, order = 0)
    public File src;

    @Parameter(names = {"-dest"}, converter = FileConverter.class, description = "Destination Folder. For a single -src file, the output file, or an existing folder to write it in", required = true, order = 1)
    public File dest;

    @Parameter(names = {"-t"}, converter = IntegerConverter.class, description = "# of threads for processing.  Generally, one thread per file.", validateWith = PositiveInteger.class, order = 2)
    public int threads = 1;

    @Parameter(names = {"-c"}, description = "results file will be gzipped compressed", order = 3)
    public Boolean compress = false;

    @Parameter(names = {"-L"}, description = "Perform minimal conversion to RDF.  Warning - turns all tweaks and optimizations off!", order = 4)
    public Boolean longForm = false;

    @Parameter(names = {"-version"}, description = "Display software version", order = 5)
    public Boolean version = false;

    @Parameter(names = {"-status"}, description = "Display progress in real-time.", order = 6)
    public Boolean status = false;

    @Parameter(names = {"-overwrite"}, description = "Overwrite results files.", order = 7)
    public Boolean overwrite = false;

    @Parameter(names = {"-help","-h"}, description = "Display help information", order = 8)
    public Boolean help = false;

    @Parameter(names = {"-extra"}, description = "Add source file URI, file size", order = 9)
    public Boolean extra = false;

    @Parameter(names = {"-naming"}, description = "Subject method (SOPInstanceUID, SHA256)", required = false, converter = NamingConverter.class, order = 10)
    public String naming = "SOPInstanceUID";

    @Parameter(names = {"-oid"}, description = "Convert UI VRs to urn:oid:<oid>", order = 11)
    public Boolean oid = false;

    @Parameter(names = {"-hash"}, description = "Calculate SHA256 Hashes. Implied with SHA256 naming option.", order = 12)
    public Boolean hash = false;

    @Parameter(names = {"-level"}, converter = LogLevelConverter.class, description = "Sets logging level (OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL)", order = 13)
    public Level level = Level.SEVERE;

    @Parameter(names = {"-wkt"}, description = "Known polygons expressed as GeoSPARQL WKT", order = 14)
    public Boolean wkt = false;

    @Parameter(names = {"-detlef"}, description = "Detlefication - Generate URNs for bnodes in Sequences", order = 15)
    public Boolean detlef = false;

    @Parameter(names = {"-padleftzero"}, description = "Pad PatientID with zeros if less than 8 characters long", hidden = true, order = 16)
    public Boolean padleftzero = false;

    @Parameter(names = {"-cdt"}, description = "Convert lists to complex data types (CDT)", hidden = false, order = 17)
    public Boolean cdt = false;

    @Parameter(names = {"-cdtlevel"}, description = "With -cdt, only convert lists of at least this many values", validateWith = PositiveInteger.class, hidden = false, order = 18)
    public Integer cdtlevel = 4;

    @Parameter(names = {"-ptags"}, description = "if ptags is true, add alternate private tag representation", hidden = false, order = 19)
    public Boolean ptags = false;

    @Parameter(names = {"-includeinlinebinary"}, description = "Include inline binary values in the RDF output (by default only their size is recorded)", hidden = false, order = 20)
    public Boolean includeinlinebinary = false;

    @Parameter(names = {"-keywords"}, description = "Use keyword predicates instead of the tag-based", hidden = false, order = 21)
    public Boolean keywords = false;

    @Parameter(names = {"-format"}, description = "RDF format type (TTL or NT)", hidden = false, order = 22)
    public RdfFormat format = RdfFormat.TTL;

    @Parameter(names = {"-sbu"}, description = "if sbu is true, add SBU-specific tweaks", hidden = true, order = 23)
    public Boolean sbu = false;

    @Parameter(names = {"-logdir"}, converter = FileConverter.class, description = "Directory for the run log file (only created if something is logged)", order = 24)
    public File logdir = new File(".");

    @Parameter(names = {"-sniff"}, description = "Also convert files without a .dcm/.dat extension that carry the DICOM 'DICM' marker", order = 25)
    public Boolean sniff = false;
}
