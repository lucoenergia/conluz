package org.lucoenergia.conluz.infrastructure.shared.web.apidocs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks a real response body against the schema the application publishes for it in {@code /api-docs}, so a
 * controller test proves the response a client receives is the one the contract describes, not only that some fields
 * hold the expected values.
 *
 * <p>Every object of the body, nested ones included, must carry exactly the properties its schema declares, all of
 * them declared required, as the response convention requires. A value may be null only where its schema's type
 * admits {@code "null"}. Strings declared as a {@code date} or {@code date-time} must parse as one, and a string with
 * an {@code enum} must be one of its values. Every problem found is reported at once, each with its JSON path.
 *
 * <p>Only what the response schemas of this application use is understood: {@code $ref}, {@code type} as a string or
 * a list, {@code properties}, {@code items}, {@code enum}, {@code format} and {@code anyOf}.
 */
public final class ResponseContractAssertions {

    private static final String REF_PREFIX = "#/components/schemas/";

    private ResponseContractAssertions() {
    }

    /**
     * Asserts that {@code body} matches the published schema named {@code schemaName}.
     */
    public static void assertMatchesContract(MockMvc mockMvc, ObjectMapper objectMapper, String schemaName,
                                             String body) throws Exception {
        String document = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertMatchesContract(objectMapper.readTree(document), schemaName, objectMapper.readTree(body));
    }

    /**
     * Asserts that {@code body} matches the schema named {@code schemaName} in the OpenAPI {@code document}.
     */
    public static void assertMatchesContract(JsonNode document, String schemaName, JsonNode body) {
        JsonNode schema = document.path("components").path("schemas").path(schemaName);
        assertTrue(!schema.isMissingNode(), () -> "No schema named " + schemaName);
        List<String> problems = new ArrayList<>();
        check(document, schema, body, "$", problems);
        if (!problems.isEmpty()) {
            fail("The response does not match " + schemaName + ":\n  " + String.join("\n  ", problems));
        }
    }

    private static void check(JsonNode document, JsonNode schema, JsonNode value, String path, List<String> problems) {
        if (value.isNull()) {
            if (!admitsNull(document, schema)) {
                problems.add(path + " is null, but its schema does not admit null");
            }
            return;
        }
        if (schema.has("anyOf")) {
            for (JsonNode option : schema.path("anyOf")) {
                List<String> optionProblems = new ArrayList<>();
                check(document, option, value, path, optionProblems);
                if (optionProblems.isEmpty()) {
                    return;
                }
            }
            problems.add(path + " matches none of its anyOf schemas");
            return;
        }
        if (schema.has("$ref")) {
            check(document, resolve(document, schema.path("$ref").asText()), value, path, problems);
            return;
        }

        Set<String> types = types(schema);
        types.remove("null");
        if (types.isEmpty() && schema.has("properties")) {
            types.add("object");
        }
        if (types.contains("object")) {
            checkObject(document, schema, value, path, problems);
        } else if (types.contains("array")) {
            if (!value.isArray()) {
                problems.add(path + " is not an array");
                return;
            }
            for (int i = 0; i < value.size(); i++) {
                check(document, schema.path("items"), value.get(i), path + "[" + i + "]", problems);
            }
        } else if (types.contains("string")) {
            checkString(schema, value, path, problems);
        } else if (types.contains("integer")) {
            if (!value.isIntegralNumber()) {
                problems.add(path + " is not an integer: " + value);
            }
        } else if (types.contains("number")) {
            if (!value.isNumber()) {
                problems.add(path + " is not a number: " + value);
            }
        } else if (types.contains("boolean")) {
            if (!value.isBoolean()) {
                problems.add(path + " is not a boolean: " + value);
            }
        } else {
            problems.add(path + " has a schema this check does not understand: " + schema);
        }
    }

    private static void checkObject(JsonNode document, JsonNode schema, JsonNode value, String path,
                                    List<String> problems) {
        if (!value.isObject()) {
            problems.add(path + " is not an object");
            return;
        }
        JsonNode properties = schema.path("properties");
        Set<String> declared = new TreeSet<>(fieldNames(properties));
        Set<String> present = new TreeSet<>(fieldNames(value));
        Set<String> required = new TreeSet<>();
        schema.path("required").forEach(name -> required.add(name.asText()));

        if (!present.equals(declared)) {
            problems.add(path + " carries " + present + " but its schema declares " + declared);
        }
        if (!required.equals(declared)) {
            problems.add(path + "'s schema requires " + required + " but declares " + declared);
        }
        for (String name : declared) {
            if (value.has(name)) {
                check(document, properties.path(name), value.get(name), path + "." + name, problems);
            }
        }
    }

    private static void checkString(JsonNode schema, JsonNode value, String path, List<String> problems) {
        if (!value.isTextual()) {
            problems.add(path + " is not a string: " + value);
            return;
        }
        String text = value.asText();
        try {
            switch (schema.path("format").asText()) {
                case "date-time" -> OffsetDateTime.parse(text);
                case "date" -> LocalDate.parse(text);
                default -> {
                    // No format this check knows.
                }
            }
        } catch (DateTimeParseException e) {
            problems.add(path + " is not a " + schema.path("format").asText() + ": " + text);
        }
        if (schema.has("enum")) {
            Set<String> values = new HashSet<>();
            schema.path("enum").forEach(option -> values.add(option.asText()));
            if (!values.contains(text)) {
                problems.add(path + " is " + text + ", not one of " + values);
            }
        }
    }

    private static boolean admitsNull(JsonNode document, JsonNode schema) {
        if (types(schema).contains("null")) {
            return true;
        }
        for (JsonNode option : schema.path("anyOf")) {
            if (admitsNull(document, option)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> types(JsonNode schema) {
        Set<String> types = new HashSet<>();
        JsonNode type = schema.path("type");
        if (type.isTextual()) {
            types.add(type.asText());
        } else if (type.isArray()) {
            type.forEach(element -> types.add(element.asText()));
        }
        return types;
    }

    private static JsonNode resolve(JsonNode document, String ref) {
        if (!ref.startsWith(REF_PREFIX)) {
            throw new IllegalArgumentException("Only references to component schemas are understood: " + ref);
        }
        JsonNode schema = document.path("components").path("schemas").path(ref.substring(REF_PREFIX.length()));
        if (schema.isMissingNode()) {
            throw new IllegalArgumentException("No schema for " + ref);
        }
        return schema;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }
}
