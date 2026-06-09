package io.github.sodalisretro.deepseek;

import java.util.Hashtable;
import java.util.Vector;

public class JsonParser {
    private String json;
    private int pos;
    private int len;

    public static Object parse(String json) {
        JsonParser p = new JsonParser();
        p.json = json;
        p.pos = 0;
        p.len = json.length();
        p.skipWhitespace();
        Object result = p.parseValue();
        return result;
    }

    private void skipWhitespace() {
        while (pos < len) {
            char c = json.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= len) {
            return null;
        }
        char c = json.charAt(pos);
        if (c == '"') {
            return parseString();
        } else if (c == '{') {
            return parseObject();
        } else if (c == '[') {
            return parseArray();
        } else if (c == 't' || c == 'f') {
            return parseBoolean();
        } else if (c == 'n') {
            return parseNull();
        } else {
            return parseNumber();
        }
    }

    private String parseString() {
        pos++;
        StringBuffer sb = new StringBuffer();
        while (pos < len) {
            char c = json.charAt(pos);
            if (c == '"') {
                pos++;
                return sb.toString();
            } else if (c == '\\') {
                pos++;
                if (pos >= len) break;
                char esc = json.charAt(pos);
                if (esc == '"') sb.append('"');
                else if (esc == '\\') sb.append('\\');
                else if (esc == '/') sb.append('/');
                else if (esc == 'n') sb.append('\n');
                else if (esc == 'r') sb.append('\r');
                else if (esc == 't') sb.append('\t');
                else if (esc == 'u') {
                    if (pos + 4 < len) {
                        String hex = json.substring(pos + 1, pos + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                    }
                }
                pos++;
            } else {
                sb.append(c);
                pos++;
            }
        }
        return sb.toString();
    }

    private Hashtable parseObject() {
        pos++;
        Hashtable obj = new Hashtable();
        skipWhitespace();
        if (pos < len && json.charAt(pos) == '}') {
            pos++;
            return obj;
        }
        while (pos < len) {
            skipWhitespace();
            if (pos >= len) break;
            if (json.charAt(pos) == '}') {
                pos++;
                return obj;
            }
            String key = parseString();
            skipWhitespace();
            if (pos < len && json.charAt(pos) == ':') {
                pos++;
            }
            skipWhitespace();
            Object value = parseValue();
            obj.put(key, value);
            skipWhitespace();
            if (pos < len && json.charAt(pos) == ',') {
                pos++;
            }
        }
        return obj;
    }

    private Vector parseArray() {
        pos++;
        Vector arr = new Vector();
        skipWhitespace();
        if (pos < len && json.charAt(pos) == ']') {
            pos++;
            return arr;
        }
        while (pos < len) {
            skipWhitespace();
            if (pos >= len) break;
            if (json.charAt(pos) == ']') {
                pos++;
                return arr;
            }
            Object value = parseValue();
            arr.addElement(value);
            skipWhitespace();
            if (pos < len && json.charAt(pos) == ',') {
                pos++;
            }
        }
        return arr;
    }

    private String parseBoolean() {
        if (json.startsWith("true", pos)) {
            pos += 4;
            return "true";
        } else {
            pos += 5;
            return "false";
        }
    }

    private Object parseNull() {
        pos += 4;
        return "";
    }

    private String parseNumber() {
        int start = pos;
        while (pos < len) {
            char c = json.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                pos++;
            } else {
                break;
            }
        }
        return json.substring(start, pos);
    }
}
