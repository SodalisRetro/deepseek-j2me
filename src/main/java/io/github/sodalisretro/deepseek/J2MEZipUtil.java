package io.github.sodalisretro.deepseek;

import com.jcraft.jzlib.Inflater;
import com.jcraft.jzlib.JZlib;
import java.io.ByteArrayOutputStream;

public class J2MEZipUtil {

    public static byte[] gunzip(byte[] compressed) {
        Inflater inflater = new Inflater();
        inflater.init();

        int offset = 10;
        if (compressed.length > 3 && (compressed[3] & 0x04) != 0) {
            int xlen = ((compressed[11] & 0xff) | ((compressed[12] & 0xff) << 8));
            offset = 10 + 2 + xlen;
        }

        inflater.setInput(compressed, offset, compressed.length - offset, true);

        byte[] buf = new byte[256];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        while (inflater.getAvailIn() > 0) {
            inflater.setOutput(buf, 0, buf.length);
            int err = inflater.inflate(JZlib.Z_SYNC_FLUSH);
            if (inflater.getNextOutIndex() > 0) {
                baos.write(buf, 0, inflater.getNextOutIndex());
            }
            if (err == JZlib.Z_STREAM_END || err == JZlib.Z_BUF_ERROR)
                break;
        }

        inflater.end();
        return baos.toByteArray();
    }
}
