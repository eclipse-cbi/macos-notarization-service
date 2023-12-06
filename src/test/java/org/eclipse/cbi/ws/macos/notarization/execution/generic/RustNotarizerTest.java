package org.eclipse.cbi.ws.macos.notarization.execution.generic;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RustNotarizerTest {

    @Test
    public void testRegex() {
        Pattern SUBMISSION_ID_PATTERN = Pattern.compile("^created submission ID: ([0-9a-f\\-]+)$", Pattern.CASE_INSENSITIVE);

        String text = "creating Notary API submission for Alfred_5.1.4_2195-7148202178110920520.dmg (sha256: 703bbaa5cc1ca2994d699d3c82791ffa977e93411a01087c8be87ef945b01df7)\n" +
                "created submission ID: b49577d3-a4f3-47a6-99a6-954de9da5451\n" +
                "resolving AWS S3 configuration from Apple-provided credentials";

        for (String line : text.split("\n")) {
            Matcher m = SUBMISSION_ID_PATTERN.matcher(line);
            System.out.println(m.matches());
        }
    }
}
