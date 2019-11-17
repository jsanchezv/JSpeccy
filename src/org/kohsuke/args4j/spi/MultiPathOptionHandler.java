package org.kohsuke.args4j.spi;

import java.nio.file.Path;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;

/**
 * Takes a classpath like option ("-cp a.jar;b.jar;c") and maps them to a collection of {@link Path}.
 *
 * @author kmahoney
 */
public class MultiPathOptionHandler extends DelimitedOptionHandler<Path> {
    protected static String sysPathSeperator = System.getProperty("path.separator");
    public MultiPathOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super Path> setter) {
        super(parser, option, setter, sysPathSeperator, new PathOptionHandler(parser, option, setter));
    }
}