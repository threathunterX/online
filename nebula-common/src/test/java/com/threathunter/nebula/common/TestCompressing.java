package com.threathunter.nebula.common;

import com.threathunter.common.Compressing;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author Wen Lu
 */
public class TestCompressing {
    @Test
    public void testCompressing() throws IOException {
        InputStream input = this.getClass().getResourceAsStream("TestCompressing.class");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        byte[] buffer = new byte[4000];
        int count = 0;
        while ((count = input.read(buffer, 0, 4000)) > 0) {
            bos.write(buffer, 0, count);
        }

        byte[] raw = bos.toByteArray();
        System.out.println(String.format("%20s: %10d", "raw", raw.length));

        byte[] compressed = Compressing.compress(raw);
        System.out.println(String.format("%20s: %10d", "compressed", compressed.length));

        byte[] uncompressed = Compressing.uncompress(compressed);
        System.out.println(String.format("%20s: %10d", "uncompressed", uncompressed.length));

        assertArrayEquals(uncompressed, raw);
    }
}
