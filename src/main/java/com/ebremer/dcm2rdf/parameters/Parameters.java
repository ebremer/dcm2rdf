package com.ebremer.dcm2rdf.parameters;

import com.beust.jcommander.Parameter;
import java.io.File;
import java.util.logging.Level;

/**
 *
 * @author erich
 */
public class Parameters {  
    @Parameter(names = {"-src"}, converter = FileConverter.class, description = "Source Folder or File", required = true, validateWith = Dcm2RdfValidator.class, order = 0)
    public File src;

    @Parameter(names = {"-dest"}, converter = FileConverter.class, description = "Destination Folder or File", required = true, validateWith = Dcm2RdfValidator.class, order = 1)
    public File dest;  

    @Parameter(names = {"-t"}, converter = IntegerConverter.class, description = "# of threads for processing.  Generally, one thread per file.", validateWith = PositiveInteger.class, order = 2)
    public int threads = 1;  

    @Parameter(names = {"-c"}, converter = BooleanConverter.class, description = "results file will be gzipped compressed", validateWith = Dcm2RdfValidator.class, order = 3)
    public Boolean compress = false;
    
    @Parameter(names = {"-L"}, converter = BooleanConverter.class, description = "Perform minimal conversion to RDF.  Warning - turns all tweaks and optimizations off!", validateWith = Dcm2RdfValidator.class, order = 4)
    public Boolean LongForm = false;    
    
    @Parameter(names = {"-version"}, converter = BooleanConverter.class, description = "Display software version", validateWith = Dcm2RdfValidator.class, order = 5)
    public Boolean version = false;
    
    @Parameter(names = {"-status"}, converter = BooleanConverter.class, description = "Display progress in real-time.", validateWith = Dcm2RdfValidator.class, order = 6)
    public Boolean status = false;
    
    @Parameter(names = {"-overwrite"}, converter = BooleanConverter.class, description = "Overwrite results files.", validateWith = Dcm2RdfValidator.class, order = 7)
    public Boolean overwrite = false;
    
    @Parameter(names = {"-help","-h"}, converter = BooleanConverter.class, description = "Display help information", validateWith = Dcm2RdfValidator.class, order = 8)
    public Boolean help = false;
    
    @Parameter(names = {"-extra"}, description = "Add source file URI, file size", converter = BooleanConverter.class, validateWith = Dcm2RdfValidator.class, order = 9)
    public Boolean extra = false;
    
    @Parameter(names = {"-naming"}, description = "Subject method (SOPInstanceUID, SHA256)", required = false, validateWith = Dcm2RdfValidator.class, order = 10)
    public String naming = "SOPInstanceUID";
   
    @Parameter(names = {"-oid"}, description = "Convert UI VRs to urn:oid:<oid>", converter = BooleanConverter.class, validateWith = Dcm2RdfValidator.class, order = 11)
    public Boolean oid = false;
    
    @Parameter(names = {"-hash"}, description = "Calculate SHA256 Hashes. Implied with SHA256 naming option.", converter = BooleanConverter.class, validateWith = Dcm2RdfValidator.class, order = 12)
    public Boolean hash = false;

    @Parameter(names = {"-level"}, converter = LogLevelConverter.class, description = "Sets logging level (OFF, ALL, WARNING, SEVERE)", order = 13)
    public Level level = Level.SEVERE;  

    @Parameter(names = {"-wkt"}, description = "Known polygons expressed as GeoSPARQL WKT", converter = BooleanConverter.class, validateWith = Dcm2RdfValidator.class, order = 14)
    public Boolean wkt = false;
    
    @Parameter(names = {"-detlef"}, description = "Detlefication - Generate URNs for bnodes in Sequences", converter = BooleanConverter.class, validateWith = Dcm2RdfValidator.class, order = 15)
    public Boolean detlef = false;
    
    @Parameter(names = {"-padleftzero"}, description = "Pad PatientID with zeros if less than 8 characters long", converter = BooleanConverter.class, validateWith = Dcm2RdfValidator.class, hidden = true, order = 16)
    public Boolean padleftzero = false;
    
    @Parameter(names = {"-cdt"}, description = "Convert lists to complex data types (CDT)", converter = BooleanConverter.class, validateWith = Dcm2RdfValidator.class, hidden = false, order = 17)
    public Boolean cdt = false;

    @Parameter(names = {"-cdtlevel"}, description = "if cdt is true, only do mapping if list length is greater than this value", validateWith = Dcm2RdfValidator.class, hidden = false, order = 18)
    public int cdtlevel = 4;    
}
