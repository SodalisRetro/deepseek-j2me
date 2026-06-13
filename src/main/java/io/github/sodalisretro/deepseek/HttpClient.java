package io.github.sodalisretro.deepseek;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

public class HttpClient {

    private String proxyUrl;

    public HttpClient(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    public void setProxyUrl(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    public String post(String jsonBody) throws IOException {
        HttpConnection conn = null;
        InputStream is = null;
        OutputStream os = null;

        try {
            conn = (HttpConnection) Connector.open(proxyUrl);
            conn.setRequestMethod(HttpConnection.POST);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            byte[] bodyBytes = toUtf8(jsonBody);

            os = conn.openOutputStream();
            os.write(bodyBytes);
            os.flush();
            os.close();
            os = null;

            int rc = conn.getResponseCode();
            if (rc != HttpConnection.HTTP_OK) {
                return "{\"error\":{\"message\":\"HTTP " + rc + "\"}}";
            }

            is = conn.openInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            byte[] respBytes = bos.toByteArray();
            return fromUtf8(respBytes);
        } finally {
            if (os != null) {
                try { os.close(); } catch (IOException ignored) {}
            }
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
            if (conn != null) {
                try { conn.close(); } catch (IOException ignored) {}
            }
        }
    }

    private byte[] toUtf8(String s) {
        int len = s.length();
        int byteLen = 0;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c <= 0x007F) {
                byteLen++;
            } else if (c <= 0x07FF) {
                byteLen += 2;
            } else {
                byteLen += 3;
            }
        }
        byte[] bytes = new byte[byteLen];
        int pos = 0;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c <= 0x007F) {
                bytes[pos++] = (byte) c;
            } else if (c <= 0x07FF) {
                bytes[pos++] = (byte) (0xC0 | (c >> 6));
                bytes[pos++] = (byte) (0x80 | (c & 0x3F));
            } else {
                bytes[pos++] = (byte) (0xE0 | (c >> 12));
                bytes[pos++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                bytes[pos++] = (byte) (0x80 | (c & 0x3F));
            }
        }
        return bytes;
    }

    private String fromUtf8(byte[] bytes) {
        int len = bytes.length;
        StringBuffer sb = new StringBuffer();
        int i = 0;
        while (i < len) {
            int b = bytes[i] & 0xFF;
            if (b <= 0x7F) {
                sb.append((char) b);
                i++;
            } else if ((b & 0xE0) == 0xC0) {
                if (i + 1 >= len) { sb.append('?'); i++; continue; }
                int c1 = bytes[i + 1] & 0xFF;
                char ch = (char) (((b & 0x1F) << 6) | (c1 & 0x3F));
                sb.append(ch);
                i += 2;
            } else if ((b & 0xF0) == 0xE0) {
                if (i + 2 >= len) { sb.append('?'); i++; continue; }
                int c1 = bytes[i + 1] & 0xFF;
                int c2 = bytes[i + 2] & 0xFF;
                char ch = (char) (((b & 0x0F) << 12) | ((c1 & 0x3F) << 6) | (c2 & 0x3F));
                sb.append(ch);
                i += 3;
            } else {
                sb.append('?');
                i++;
            }
        }
        return sb.toString();
    }
}
