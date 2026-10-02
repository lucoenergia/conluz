package org.lucoenergia.conluz.infrastructure.shared.web.apidocs;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the whole generated OpenAPI document against a committed snapshot.
 *
 * <p>The individual {@code *ApiDocsTest} classes assert the conventions that are silently wrong in
 * the document — a list endpoint rendered as an object, a dropped {@code nullable}. This one
 * asserts nothing about content and everything about <em>change</em>: the snapshot's diff in a pull
 * request is the API diff a reviewer needs, commit by commit, without anyone having to boot the
 * application or run a generator.</p>
 *
 * <p>When it fails, the actual document is written to {@code build/openapi/api-docs.actual.json};
 * accepting the change is copying that one file over the snapshot. There is deliberately no flag
 * that rewrites the snapshot in place, because CI could run it and an API change would land
 * unreviewed.</p>
 */
class OpenApiSnapshotTest extends BaseControllerTest {

    private static final String SNAPSHOT_RESOURCE = "openapi/api-docs.json";
    private static final Path SNAPSHOT_SOURCE = Path.of("src", "test", "resources", SNAPSHOT_RESOURCE);
    private static final Path ACTUAL_OUTPUT = Path.of("build", "openapi", "api-docs.actual.json");

    @Test
    void theGeneratedDocumentMatchesTheCommittedSnapshot() throws Exception {
        String actual = normalise(fetchApiDocs());
        String expected = readSnapshot();

        if (actual.equals(expected)) {
            return;
        }

        Path written = writeActual(actual);
        fail(String.format(
                "The generated OpenAPI document differs from %s.%n"
                        + "If the change is intended, review it and accept it with:%n"
                        + "    cp %s %s%n"
                        + "Otherwise the API changed by accident -- fix the code, not the snapshot.",
                SNAPSHOT_SOURCE, written, SNAPSHOT_SOURCE));
    }

    private String fetchApiDocs() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    /**
     * Key order and whitespace are not part of the contract — springdoc runs with
     * {@code writer-with-order-by-keys=false}, so member order reflects reflection order and can
     * shift for reasons that are not API changes. Sorting every object's keys and pinning the
     * indentation leaves a diff that shows only what a client would actually notice. Array order is
     * left alone: it <em>is</em> meaningful for {@code enum} and {@code required}.
     */
    private String normalise(String json) throws Exception {
        ObjectMapper normalising = new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));

        Object tree = normalising.readValue(json, Object.class);
        return normalising.writer(printer).writeValueAsString(tree) + "\n";
    }

    private String readSnapshot() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SNAPSHOT_RESOURCE)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Path writeActual(String actual) throws Exception {
        Files.createDirectories(ACTUAL_OUTPUT.getParent());
        Files.writeString(ACTUAL_OUTPUT, actual, StandardCharsets.UTF_8);
        return ACTUAL_OUTPUT.toAbsolutePath();
    }
}
