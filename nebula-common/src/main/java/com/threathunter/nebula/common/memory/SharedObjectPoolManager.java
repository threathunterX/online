package com.threathunter.nebula.common.memory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * For each class, we have a unique SharedObjectPool.
 *
 * 
 */
public class SharedObjectPoolManager {
    private static ConcurrentMap<Class<?>, SharedObjectPool> maps = new
            ConcurrentHashMap<Class<?>, SharedObjectPool>();

    private static SharedObjectPool getPool(Class<?> cl) {
        SharedObjectPool result = maps.get(cl);
        if (result != null) {
            return result;
        }

        SharedObjectPool newPool = new SharedObjectPool();
        result = maps.putIfAbsent(cl, newPool);
        if (result == null) {
            // insert successfully
            return newPool;
        } else {
            // some one inserts before us
            return result;
        }
    }

    public static <T> T getSharedObject(T raw, Class<?> cl) {
        SharedObjectPool<T> pool = getPool(cl);
        return pool.getSharedObject(raw);
    }

    public static <T> T getSharedObject(T raw) {
        SharedObjectPool<T> pool = getPool(raw.getClass());
        return pool.getSharedObject(raw);
    }

    public static String getSharedString(String raw) {
        return raw.intern();
    }
}
