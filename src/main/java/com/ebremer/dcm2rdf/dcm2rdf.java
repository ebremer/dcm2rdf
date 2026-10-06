package com.ebremer.dcm2rdf;

/**
 * The command line's former entry point, kept so scripts and IDE run configurations that name
 * it keep working.
 *
 * @deprecated use {@link Dcm2RdfCli}
 */
@Deprecated(forRemoval = true)
public final class dcm2rdf {
    /** @deprecated use {@link Dcm2RdfCli#VERSION} */
    @Deprecated(forRemoval = true)
    public static final String VERSION = Dcm2RdfCli.VERSION;

    private dcm2rdf() {}

    public static void main(String[] args) {
        Dcm2RdfCli.main(args);
    }
}
