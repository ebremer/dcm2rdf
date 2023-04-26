package com.ebremer.dcm2rdf;

import com.beust.jcommander.Parameter;
import java.io.File;

/**
 *
 * @author erich
 */
public class Parameters {  
    @Parameter(names = "-src", converter = FileConverter.class, description = "Source Folder or File", required = true, validateWith = Dcm2RdfValidator.class)
    public File src;

    @Parameter(names = "-dest", converter = FileConverter.class, description = "Destination Folder or File", required = true, validateWith = Dcm2RdfValidator.class)
    public File dest;  

    @Parameter(names = "-updates", description = "Updates Destination Folder", required = false, validateWith = Dcm2RdfValidator.class, converter = FileConverter.class)
    public File upd = null;  

    @Parameter(names = "-cores", description = "# of cores for processing", validateWith = Dcm2RdfValidator.class)
    public int cores = 1;  

    @Parameter(names = {"-c","-compress"})
    public boolean compress = false;
    
    @Parameter(names = {"-l","-longForm"})
    public boolean LongForm = false;    
    
    @Parameter(names = {"-version"})
    public boolean version = false;
    
    @Parameter(names = "-help", help = true)
    public boolean help;
}
