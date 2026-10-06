package io.github.navidzaare.flux.connector.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonTest {

    private static final char QUOTE = '"';
    private static final char BACKSLASH = '\\';
    private static final char TAB = 9;
    private static final char SOH = 1;

    @Test
    void writesAnObject() {
        assertThat(Json.write(entry("id", 1, "name", "Ada")))
                .isEqualTo("{\"id\":1,\"name\":\"Ada\"}");
    }

    @Test
    void escapesCharactersThatWouldBreakTheDocument() {
        String value = "line1" + (char) 10 + "line2 " + QUOTE + "quoted" + QUOTE + " "
                + BACKSLASH + " tail";
        String expected = "{\"note\":\"line1" + BACKSLASH + "nline2 " + BACKSLASH + QUOTE
                + "quoted" + BACKSLASH + QUOTE + " " + BACKSLASH + BACKSLASH + " tail\"}";

        assertThat(Json.write(entry("note", value))).isEqualTo(expected);
    }

    @Test
    void escapesControlCharacters() {
        assertThat(Json.write("tab" + TAB + "here"))
                .isEqualTo("\"tab" + BACKSLASH + "there\"");
        assertThat(Json.write(String.valueOf(SOH)))
                .isEqualTo("\"" + BACKSLASH + "u0001\"");
    }

    @Test
    void keepsNumbersBooleansAndNullUnquoted() {
        assertThat(Json.write(entry("count", 5, "ok", true, "missing", null)))
                .isEqualTo("{\"count\":5,\"ok\":true,\"missing\":null}");
    }

    @Test
    void nestsObjectsAndArrays() {
        assertThat(Json.write(entry("items", List.of(entry("id", 1), entry("id", 2)))))
                .isEqualTo("{\"items\":[{\"id\":1},{\"id\":2}]}");
    }

    @Test
    void writesNonFiniteNumbersAsNull() {
        assertThat(Json.write(entry("value", Double.NaN))).isEqualTo("{\"value\":null}");
    }

    private static Map<String, Object> entry(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
