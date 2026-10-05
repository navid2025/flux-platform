package io.github.navidzare.flux.connector.support;

import java.util.Map;

/**
 * Writes a value as JSON.
 *
 * <p>The core deliberately carries no JSON library, so connectors that need to build a
 * document do it here rather than each growing their own writer. Strings, numbers,
 * booleans, nulls, maps and iterables are all handled; anything else falls back to its
 * string form.</p>
 */
public final class Json {

    private Json() {
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            writeString(out, string);
        } else if (value instanceof Boolean bool) {
            out.append(bool);
        } else if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            out.append(Double.isFinite(number) ? value : "null");
        } else if (value instanceof Number number) {
            out.append(number);
        } else if (value instanceof Map<?, ?> map) {
            writeObject(out, map);
        } else if (value instanceof Iterable<?> items) {
            writeArray(out, items);
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(out, String.valueOf(entry.getKey()));
            out.append(':');
            writeValue(out, entry.getValue());
        }
        out.append('}');
    }

    private static void writeArray(StringBuilder out, Iterable<?> items) {
        out.append('[');
        boolean first = true;
        for (Object item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(out, item);
        }
        out.append(']');
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
