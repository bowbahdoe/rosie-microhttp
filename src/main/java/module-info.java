import org.jspecify.annotations.NullMarked;

@NullMarked
module dev.mccue.rosie.microhttp {
    requires static org.jspecify;

    requires transitive dev.mccue.rosie;
    requires transitive org.microhttp;

    requires dev.mccue.microhttp.systemlogger;

    exports dev.mccue.rosie.microhttp;
}