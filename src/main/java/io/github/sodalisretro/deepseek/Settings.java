package io.github.sodalisretro.deepseek;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreNotOpenException;

public class Settings {

    private static final String STORE_NAME = "ds_settings";
    private static final int REC_HOST = 1;
    private static final int REC_PORT = 2;

    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "8080";

    private static String cachedHost;
    private static String cachedPort;
    private static boolean loaded;

    public static String getHost() {
        ensureLoaded();
        return cachedHost;
    }

    public static String getPort() {
        ensureLoaded();
        return cachedPort;
    }

    public static String getProxyUrl() {
        return "http://" + getHost() + ":" + getPort() + "/";
    }

    public static void save(String host, String port) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            byte[] hostBytes = host.getBytes();
            byte[] portBytes = port.getBytes();

            if (rs.getNumRecords() >= REC_HOST) {
                rs.setRecord(REC_HOST, hostBytes, 0, hostBytes.length);
            } else {
                rs.addRecord(hostBytes, 0, hostBytes.length);
            }

            if (rs.getNumRecords() >= REC_PORT) {
                rs.setRecord(REC_PORT, portBytes, 0, portBytes.length);
            } else {
                rs.addRecord(portBytes, 0, portBytes.length);
            }

            cachedHost = host;
            cachedPort = port;
        } catch (RecordStoreException e) {
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (RecordStoreException ignored) {}
            }
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            if (rs.getNumRecords() >= REC_HOST) {
                byte[] data = rs.getRecord(REC_HOST);
                cachedHost = new String(data);
            } else {
                cachedHost = DEFAULT_HOST;
            }
            if (rs.getNumRecords() >= REC_PORT) {
                byte[] data = rs.getRecord(REC_PORT);
                cachedPort = new String(data);
            } else {
                cachedPort = DEFAULT_PORT;
            }
        } catch (RecordStoreException e) {
            cachedHost = DEFAULT_HOST;
            cachedPort = DEFAULT_PORT;
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (RecordStoreException ignored) {}
            }
        }
    }
}
