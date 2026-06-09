package io.github.sodalisretro.deepseek;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

public class Settings {

    private static final String STORE_NAME = "ds_cfg";
    private static final int RECORD_ID = 1;

    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "8080";

    private static String host;
    private static String port;
    private static boolean webSearch;

    static {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            if (rs.getNumRecords() >= RECORD_ID) {
                byte[] data = rs.getRecord(RECORD_ID);
                String raw = new String(data);
                int i = 0;
                int j;
                j = raw.indexOf('\n', i);
                host = (j >= 0) ? raw.substring(i, j) : DEFAULT_HOST;
                i = j + 1;

                j = raw.indexOf('\n', i);
                port = (j >= 0) ? raw.substring(i, j) : DEFAULT_PORT;
                i = j + 1;

                webSearch = (i < raw.length() && raw.charAt(i) == '1');
            } else {
                host = DEFAULT_HOST;
                port = DEFAULT_PORT;
                webSearch = false;
            }
        } catch (Exception e) {
            host = DEFAULT_HOST;
            port = DEFAULT_PORT;
            webSearch = false;
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (RecordStoreException ignored) {}
            }
        }
    }

    public static String getHost() { return host; }

    public static String getPort() { return port; }

    public static String getProxyUrl() { return "http://" + host + ":" + port + "/"; }

    public static boolean getWebSearch() { return webSearch; }

    public static void save(String newHost, String newPort) {
        host = newHost;
        port = newPort;
        persist();
    }

    public static void saveWebSearch(boolean enable) {
        webSearch = enable;
        persist();
    }

    private static void persist() {
        RecordStore rs = null;
        try {
            String raw = host + "\n" + port + "\n" + (webSearch ? "1" : "0");
            byte[] data = raw.getBytes();
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            if (rs.getNumRecords() >= RECORD_ID) {
                rs.setRecord(RECORD_ID, data, 0, data.length);
            } else {
                rs.addRecord(data, 0, data.length);
            }
        } catch (RecordStoreException e) {
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (RecordStoreException ignored) {}
            }
        }
    }
}
