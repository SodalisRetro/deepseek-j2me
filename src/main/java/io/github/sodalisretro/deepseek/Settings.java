package io.github.sodalisretro.deepseek;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

public class Settings {

    private static final String STORE_NAME = "ds_cfg";
    private static final String PROMPT_STORE = "ds_prompt";
    private static final int RECORD_ID = 1;

    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "8080";
    private static final int DEFAULT_MAX_ROUNDS = 15;

    private static String host;
    private static String port;
    private static boolean webSearch;
    private static int maxSearchRounds;
    private static String systemPrompt;
    private static boolean promptLoaded;

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

                j = raw.indexOf('\n', i);
                if (j >= 0) {
                    webSearch = (i < j && raw.charAt(i) == '1');
                    i = j + 1;
                    if (i < raw.length()) {
                        maxSearchRounds = parseInt(raw, i);
                    } else {
                        maxSearchRounds = DEFAULT_MAX_ROUNDS;
                    }
                } else {
                    webSearch = (i < raw.length() && raw.charAt(i) == '1');
                    maxSearchRounds = DEFAULT_MAX_ROUNDS;
                }
            } else {
                host = DEFAULT_HOST;
                port = DEFAULT_PORT;
                webSearch = false;
                maxSearchRounds = DEFAULT_MAX_ROUNDS;
            }
        } catch (Exception e) {
            host = DEFAULT_HOST;
            port = DEFAULT_PORT;
            webSearch = false;
            maxSearchRounds = DEFAULT_MAX_ROUNDS;
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (RecordStoreException ignored) {}
            }
        }
    }

    private static void loadPrompt() {
        if (promptLoaded) return;
        promptLoaded = true;
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(PROMPT_STORE, true);
            if (rs.getNumRecords() >= RECORD_ID) {
                byte[] data = rs.getRecord(RECORD_ID);
                systemPrompt = new String(data);
                if (systemPrompt == null || systemPrompt.length() == 0) {
                    systemPrompt = null;
                }
            }
        } catch (Exception e) {
            systemPrompt = null;
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

    public static int getMaxSearchRounds() { return maxSearchRounds; }

    public static String getSystemPrompt() {
        loadPrompt();
        if (systemPrompt != null && systemPrompt.length() > 0) {
            return systemPrompt;
        }
        return I18n.get(I18n.SYSTEM_PROMPT);
    }

    public static boolean hasCustomPrompt() {
        loadPrompt();
        return systemPrompt != null && systemPrompt.length() > 0;
    }

    public static void save(String newHost, String newPort) {
        host = newHost;
        port = newPort;
        persist();
    }

    public static void saveWebSearch(boolean enable) {
        webSearch = enable;
        persist();
    }

    public static void saveMaxSearchRounds(int rounds) {
        maxSearchRounds = rounds;
        persist();
    }

    public static void saveSystemPrompt(String prompt) {
        promptLoaded = true;
        if (prompt == null || prompt.length() == 0) {
            systemPrompt = null;
            RecordStore rs = null;
            try {
                rs = RecordStore.openRecordStore(PROMPT_STORE, false);
                if (rs != null && rs.getNumRecords() >= RECORD_ID) {
                    rs.deleteRecord(RECORD_ID);
                }
            } catch (Exception e) {
            } finally {
                if (rs != null) {
                    try { rs.closeRecordStore(); } catch (RecordStoreException ignored) {}
                }
            }
        } else {
            systemPrompt = prompt;
            RecordStore rs = null;
            try {
                byte[] data = prompt.getBytes();
                rs = RecordStore.openRecordStore(PROMPT_STORE, true);
                if (rs.getNumRecords() >= RECORD_ID) {
                    rs.setRecord(RECORD_ID, data, 0, data.length);
                } else {
                    rs.addRecord(data, 0, data.length);
                }
            } catch (Exception e) {
            } finally {
                if (rs != null) {
                    try { rs.closeRecordStore(); } catch (RecordStoreException ignored) {}
                }
            }
        }
    }

    private static void persist() {
        RecordStore rs = null;
        try {
            String raw = host + "\n" + port + "\n" + (webSearch ? "1" : "0") + "\n" + maxSearchRounds;
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

    private static int parseInt(String s, int start) {
        int val = 0;
        int pos = start;
        while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
            val = val * 10 + (s.charAt(pos) - '0');
            pos++;
        }
        return (pos > start) ? val : DEFAULT_MAX_ROUNDS;
    }
}
