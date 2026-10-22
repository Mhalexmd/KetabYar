package com.example.library.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON parser/serializer so the REST API needs no extra JSON dependency. */
public final class MiniJson {
    private MiniJson() { }

    public static Object parse(String json) {
        if (json == null) throw new IllegalArgumentException("بدنه درخواست خالی است.");
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) throw new IllegalArgumentException("ساختار JSON معتبر نیست.");
        return value;
    }

    public static String stringify(Object value) {
        StringBuilder output = new StringBuilder();
        write(value, output);
        return output.toString();
    }

    private static void write(Object value, StringBuilder output) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String string) {
            writeString(string, output);
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("کلیدهای شیء JSON باید رشته باشند.");
                }
                if (!first) output.append(',');
                first = false;
                writeString((String) entry.getKey(), output);
                output.append(':');
                write(entry.getValue(), output);
            }
            output.append('}');
        } else if (value instanceof Iterable<?> values) {
            output.append('[');
            boolean first = true;
            for (Object item : values) {
                if (!first) output.append(',');
                first = false;
                write(item, output);
            }
            output.append(']');
        } else {
            throw new IllegalArgumentException("نوع داده برای JSON پشتیبانی نمی‌شود: " + value.getClass().getName());
        }
    }

    private static void writeString(String value, StringBuilder output) {
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (c < 0x20) output.append(String.format("\\u%04x", (int) c));
                    else output.append(c);
                }
            }
        }
        output.append('"');
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) { this.input = input; }
        private boolean atEnd() { return position >= input.length(); }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(input.charAt(position))) position++;
        }

        private Object parseValue() {
            skipWhitespace();
            if (atEnd()) throw invalidJson();
            char c = input.charAt(position);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) yield parseNumber();
                    throw invalidJson();
                }
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (take('}')) return object;
            while (true) {
                skipWhitespace();
                if (atEnd() || input.charAt(position) != '"') throw invalidJson();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                object.put(key, value);
                skipWhitespace();
                if (take('}')) return object;
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (take(']')) return array;
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (take(']')) return array;
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (!atEnd()) {
                char c = input.charAt(position++);
                if (c == '"') return value.toString();
                if (c == '\\') {
                    if (atEnd()) throw invalidJson();
                    char escaped = input.charAt(position++);
                    switch (escaped) {
                        case '"' -> value.append('"');
                        case '\\' -> value.append('\\');
                        case '/' -> value.append('/');
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> {
                            if (position + 4 > input.length()) throw invalidJson();
                            try {
                                value.append((char) Integer.parseInt(input.substring(position, position + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw invalidJson();
                            }
                            position += 4;
                        }
                        default -> throw invalidJson();
                    }
                } else {
                    if (c < 0x20) throw invalidJson();
                    value.append(c);
                }
            }
            throw invalidJson();
        }

        private Object parseNumber() {
            int start = position;
            if (take('-') && atEnd()) throw invalidJson();
            if (take('0')) {
                if (!atEnd() && Character.isDigit(input.charAt(position))) throw invalidJson();
            } else {
                if (atEnd() || input.charAt(position) < '1' || input.charAt(position) > '9') throw invalidJson();
                while (!atEnd() && Character.isDigit(input.charAt(position))) position++;
            }
            if (take('.')) {
                if (atEnd() || !Character.isDigit(input.charAt(position))) throw invalidJson();
                while (!atEnd() && Character.isDigit(input.charAt(position))) position++;
            }
            if (take('e') || take('E')) {
                if (!take('+')) take('-');
                if (atEnd() || !Character.isDigit(input.charAt(position))) throw invalidJson();
                while (!atEnd() && Character.isDigit(input.charAt(position))) position++;
            }
            String token = input.substring(start, position);
            try {
                BigDecimal decimal = new BigDecimal(token);
                try { return decimal.longValueExact(); }
                catch (ArithmeticException ignored) { return decimal; }
            } catch (NumberFormatException ex) {
                throw invalidJson();
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!input.startsWith(literal, position)) throw invalidJson();
            position += literal.length();
            return value;
        }

        private boolean take(char expected) {
            if (!atEnd() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw invalidJson();
        }

        private IllegalArgumentException invalidJson() {
            return new IllegalArgumentException("ساختار JSON معتبر نیست (محل خطا: " + position + ").");
        }
    }
}
