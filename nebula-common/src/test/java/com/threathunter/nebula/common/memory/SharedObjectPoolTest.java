package com.threathunter.nebula.common.memory;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * 
 */
public class SharedObjectPoolTest {

    private SharedObjectPool<String> pool;
    @Before
    public void setUp()
    {
        pool = new SharedObjectPool<String>();
    }

    @Test
    public void testAutoClean() {
        String key = new String("test");
        String newKey = pool.getSharedObject(key);
        assertEquals(key, newKey);
        assertEquals(pool.getSize(), 1);

        key = null;
        newKey = null;

        for(int i = 0; i < 1000000; i++) {
            key = "test" + i;
            newKey = pool.getSharedObject(key);
        }
        key = null;
        newKey = null;

        System.gc();
        sleep(3);
        assertEquals(pool.getSize(), 0);
    }

    @Test
    public void testGetUnique() {
        SharedObjectPool<String> pool = new SharedObjectPool<String>();
        String key = "test";
        String newKey = pool.getSharedObject(key);

        String key2 = "te" + "st";
        assertTrue(newKey == pool.getSharedObject(key2));
    }

    @Test
    public void testReuse() {
        parameterizedTest(200000, 100);
        parameterizedTest(50000, 1000);
        parameterizedTest(200000, 1000);
    }

    private void parameterizedTest(int totalNum, int differentNumOfValues) {
        long beforeTime;
        long afterTime;
        String[] refs = new String[totalNum];

        System.out.println(String.format("without object sharing(%d objects total, " +
                "%d different value)", totalNum, differentNumOfValues));
        beforeTime = System.currentTimeMillis();
        for(int i = 0; i < refs.length; i++) {
            refs[i] = "test" + (i % differentNumOfValues);
        }
        afterTime = System.currentTimeMillis();
        System.gc();
        sleep(3);
        System.out.printf("    Memory:%10d\n", getUsedMemory());
        System.out.printf("    Time:%10d\n", (afterTime - beforeTime));

        for(int i = 0; i < refs.length; i++) {
            refs[i] = null;
        }

        System.out.println(String.format("with object sharing(%d objects total, " +
                "%d different value)", totalNum, differentNumOfValues));
        beforeTime = System.currentTimeMillis();
        for(int i = 0; i < refs.length; i++) {
            String value = "test" + (i % differentNumOfValues);
            value = pool.getSharedObject(value);
            refs[i] = value;
        }
        afterTime = System.currentTimeMillis();
        System.gc();
        sleep(3);
        System.out.printf("    Memory:%10d\n", getUsedMemory());
        System.out.printf("    Time:%10d\n", (afterTime - beforeTime));
        System.out.printf("    Pool:%10d\n", pool.getSize());
        for(int i = 0; i < refs.length; i++) {
            refs[i] = null;
        }

        System.gc();
        sleep(3);
        assertEquals(pool.getSize(), 0);
    }

    private long getUsedMemory() {
        long total = Runtime.getRuntime().totalMemory();
        long free = Runtime.getRuntime().freeMemory();
        long used = total - free;
        return used;
    }

    private void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
