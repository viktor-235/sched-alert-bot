package com.github.viktor235.schedalertbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.assertj.core.api.AbstractAssert;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Custom AssertJ assertion for comparing JSON objects with the ability to ignore specific fields.
 * Supports ignoring fields during comparison.
 */
public class JsonAssert extends AbstractAssert<JsonAssert, Object> {

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            .configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private JsonNode actualNode;
    private String[] ignoredFields = new String[0];

    /**
     * Constructor for JsonAssert.
     *
     * @param actual the actual object to compare.
     */
    public JsonAssert(Object actual) {
        super(actual, JsonAssert.class);
        try {
            this.actualNode = mapper.valueToTree(actual);
        } catch (Exception e) {
            failWithMessage("Failed to serialize actual object to JSON");
        }
    }

    /**
     * Compares the actual JSON object to the expected one, ignoring specified fields.
     * The comparison is done in a pretty-printed format.
     *
     * @param expected the expected object to compare against.
     * @return the current JsonAssert instance for chaining.
     */
    public JsonAssert isEqualToJson(Object expected) {
        try {
            JsonNode expectedNode = mapper.valueToTree(expected);

            JsonNode actualCopy = actualNode.deepCopy();
            JsonNode expectedCopy = expectedNode.deepCopy();

            // Remove the ignored fields
            removeFields(actualCopy, ignoredFields);
            removeFields(expectedCopy, ignoredFields);

            String actualJson = toPrettyJson(actualCopy);
            String expectedJson = toPrettyJson(expectedCopy);

            // Compare pretty-printed JSON strings
            assertThat(actualJson).isEqualTo(expectedJson);
        } catch (Exception e) {
            failWithMessage("Failed to compare JSON objects");
        }
        return this;
    }

    /**
     * Specifies the fields to be ignored during the comparison.
     *
     * @param fields the fields to be ignored.
     * @return the current JsonAssert instance for chaining.
     */
    public JsonAssert ignoringFields(String... fields) {
        this.ignoredFields = fields;
        return this;
    }

    /**
     * Recursively removes the specified fields from the JSON node.
     *
     * @param node   the JSON node to remove fields from.
     * @param fields the fields to be removed.
     */
    private static void removeFields(JsonNode node, String... fields) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            for (String field : fields) {
                objectNode.remove(field);
            }
            objectNode.fields().forEachRemaining(entry -> removeFields(entry.getValue(), fields));
        } else if (node.isArray()) {
            for (JsonNode arrayItem : node) {
                removeFields(arrayItem, fields);
            }
        }
    }

    /**
     * Converts a JSON node to a pretty-printed string format.
     *
     * @param node the JSON node to convert.
     * @return the pretty-printed JSON string.
     */
    private String toPrettyJson(JsonNode node) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            failWithMessage("Failed to convert JSON to pretty format");
            return null;
        }
    }
}