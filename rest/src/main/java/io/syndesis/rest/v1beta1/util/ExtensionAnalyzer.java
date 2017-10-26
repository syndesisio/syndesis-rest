package io.syndesis.rest.v1beta1.util;

import io.syndesis.core.SyndesisServerException;
import io.syndesis.model.extension.Extension;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

/**
 * Tools to analyze binary extensions.
 *
 * TODO: determine the right place to put this component
 */
@Component
public class ExtensionAnalyzer {

    /**
     * Analyze a binary extension to obtain the embedded {@link Extension} object.
     *
     * TODO: implement it, this is a dummy handle
     *
     * @param binaryExtension the binary stream of the extension
     * @return the embedded {@code Extension} object
     */
    @Nonnull
    public Extension analyze(InputStream binaryExtension) {
        try {
            IOUtils.toByteArray(binaryExtension); // to simulate reading it
        } catch (IOException ex) {
            throw SyndesisServerException.launderThrowable("Cannot read from binary extension file", ex);
        }

        return new Extension.Builder()
            .name("Dummy")
            .description("Dummy description")
            .build();
    }

}
