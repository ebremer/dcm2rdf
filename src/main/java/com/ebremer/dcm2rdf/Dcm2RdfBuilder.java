package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.parameters.Parameters;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.jena.rdf.model.Model;

/**
 * Fluent entry point for using dcm2rdf as an embedded library.
 * <p>
 * Mirrors the pertinent command line options and runs the same single-file conversion
 * pipeline as the CLI, but returns the result as a Jena {@link Model} instead of
 * writing it to disk:
 * <pre>{@code
 * Model model = new Dcm2RdfBuilder()
 *     .keywords(true)
 *     .oid(true)
 *     .toModel(Path.of("image.dcm"));
 * }</pre>
 * A configured builder is reusable: each {@code toModel} call snapshots the current
 * options and converts independently, so one builder can convert any number of files,
 * including concurrently. Configure it fully before converting; the setters are not
 * synchronized with respect to in-flight conversions.
 * <p>
 * Unlike the CLI, this class never touches global java.util.logging configuration -
 * the embedding application controls its own logging. Conversion problems are reported
 * on the {@code com.ebremer.dcm2rdf} loggers.
 *
 * @author erich
 */
public final class Dcm2RdfBuilder {

    /** Subject URI minting strategy, mirroring the CLI {@code -naming} option. */
    public enum Naming {
        /** Name the SOP instance {@code urn:oid:<SOPInstanceUID>} (the default). */
        SOP_INSTANCE_UID("SOPInstanceUID"),
        /** Name the SOP instance {@code urn:sha256:<hash of the source bytes>}. */
        SHA256("SHA256");

        private final String value;

        Naming(String value) {
            this.value = value;
        }
    }

    // defaults match the CLI defaults in Parameters
    private boolean longForm = false;
    private boolean extra = false;
    private Naming naming = Naming.SOP_INSTANCE_UID;
    private boolean oid = false;
    private boolean hash = false;
    private boolean wkt = false;
    private boolean detlef = false;
    private boolean padLeftZero = false;
    private boolean cdt = false;
    private int cdtLevel = 4;
    private boolean ptags = false;
    private boolean includeInlineBinary = false;
    private boolean keywords = false;

    /** CLI {@code -L}: minimal conversion to RDF; turns all tweaks and optimizations off. */
    public Dcm2RdfBuilder longForm(boolean longForm) {
        this.longForm = longForm;
        return this;
    }

    /**
     * CLI {@code -extra}: record the source file URI and file size in the model.
     * For channel input the source is identified by the {@code name} passed to
     * {@link #toModel(Path, String, SeekableByteChannel)}.
     */
    public Dcm2RdfBuilder extra(boolean extra) {
        this.extra = extra;
        return this;
    }

    /** CLI {@code -naming}: how the SOP instance subject URI is minted. */
    public Dcm2RdfBuilder naming(Naming naming) {
        this.naming = Objects.requireNonNull(naming, "naming");
        return this;
    }

    /** CLI {@code -oid}: convert UI VR values to {@code urn:oid:<oid>} IRIs. */
    public Dcm2RdfBuilder oid(boolean oid) {
        this.oid = oid;
        return this;
    }

    /**
     * CLI {@code -hash}: record the SHA-256 hash of the source bytes (implied by
     * {@link Naming#SHA256}). Note this reads the source to the end to compute the digest.
     */
    public Dcm2RdfBuilder hash(boolean hash) {
        this.hash = hash;
        return this;
    }

    /** CLI {@code -wkt}: express known polygons as GeoSPARQL WKT literals. */
    public Dcm2RdfBuilder wkt(boolean wkt) {
        this.wkt = wkt;
        return this;
    }

    /** CLI {@code -detlef}: detlefication - generate URNs for blank nodes in Sequences. */
    public Dcm2RdfBuilder detlef(boolean detlef) {
        this.detlef = detlef;
        return this;
    }

    /** CLI {@code -padleftzero}: pad PatientID with zeros if less than 8 characters long. */
    public Dcm2RdfBuilder padLeftZero(boolean padLeftZero) {
        this.padLeftZero = padLeftZero;
        return this;
    }

    /** CLI {@code -cdt}: convert lists to complex data types (CDT). */
    public Dcm2RdfBuilder cdt(boolean cdt) {
        this.cdt = cdt;
        return this;
    }

    /** CLI {@code -cdtlevel}: with {@link #cdt}, only map lists at least this long (default 4). */
    public Dcm2RdfBuilder cdtLevel(int cdtLevel) {
        this.cdtLevel = cdtLevel;
        return this;
    }

    /** CLI {@code -ptags}: add the alternate private tag representation. */
    public Dcm2RdfBuilder ptags(boolean ptags) {
        this.ptags = ptags;
        return this;
    }

    /** CLI {@code -includeinlinebinary}: include inline binary data instead of empty base64 literals. */
    public Dcm2RdfBuilder includeInlineBinary(boolean includeInlineBinary) {
        this.includeInlineBinary = includeInlineBinary;
        return this;
    }

    /** CLI {@code -keywords}: use keyword predicates instead of tag-based ones. */
    public Dcm2RdfBuilder keywords(boolean keywords) {
        this.keywords = keywords;
        return this;
    }

    /**
     * Converts a DICOM file to RDF.
     *
     * @throws IOException if the file cannot be opened or read
     * @throws IllegalStateException if the source lacks the data the configuration
     *         needs (e.g. no SOP Instance UID with {@link Naming#SOP_INSTANCE_UID})
     */
    public Model toModel(File file) throws IOException {
        return toModel(file.toPath());
    }

    /**
     * Converts a DICOM file to RDF.
     *
     * @throws IOException if the file cannot be opened or read
     * @throws IllegalStateException if the source lacks the data the configuration
     *         needs (e.g. no SOP Instance UID with {@link Naming#SOP_INSTANCE_UID})
     */
    public Model toModel(Path path) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(path))) {
            return convert(path, path.toString(), is);
        }
    }

    /**
     * Converts DICOM read from the channel's current position to RDF. The channel is
     * left open; the caller retains ownership and must close it.
     *
     * @param path the source path the DICOM data came from; reported in log messages
     * @param name logical name of the DICOM source, used in statistics, error messages,
     *        and (with {@link #extra}) the recorded source URI and file size. A {@code #}
     *        separates an archive path from a member name, as the CLI does for tar
     *        entries (e.g. {@code /data/scans.tar#img.dcm}).
     * @param sbc channel positioned at the start of the DICOM data
     * @throws IOException if the channel cannot be read
     * @throws IllegalStateException if the source lacks the data the configuration
     *         needs (e.g. no SOP Instance UID with {@link Naming#SOP_INSTANCE_UID})
     */
    public Model toModel(Path path, String name, SeekableByteChannel sbc) throws IOException {
        return convert(path, name, Channels.newInputStream(sbc));
    }

    private Model convert(Path src, String name, InputStream is) {
        Parameters params = toParameters();
        DICOM2RDF d2r = new DICOM2RDF(params);
        Model m = d2r.ProcessDICOMasBytes2Model(src, name, is);
        return d2r.applyPostProcessing(m);
    }

    private Parameters toParameters() {
        Parameters params = new Parameters();
        params.LongForm = longForm;
        params.extra = extra;
        params.naming = naming.value;
        params.oid = oid;
        params.hash = hash;
        params.wkt = wkt;
        params.detlef = detlef;
        params.padleftzero = padLeftZero;
        params.cdt = cdt;
        params.cdtlevel = cdtLevel;
        params.ptags = ptags;
        params.includeinlinebinary = includeInlineBinary;
        params.keywords = keywords;
        return params;
    }
}
