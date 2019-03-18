package com.threathunter.nebula.common.memory;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Helper to store only one instance of objects with the same value.
 *
 * * 
 */
public class SharedObjectPool<T> {
    private final ConcurrentMap<Reference<T>, Reference<T>> map = new
            ConcurrentHashMap<Reference<T>, Reference<T>>();

    public T getSharedObject(T raw) {
        if (raw == null) {
            return null;
        }

        T result = null;
        // the internal object is reference, so we need addional wrapper to override
        // the behavior of equals of "raw"
        Wrapper<T> wrapper = new Wrapper<T>(raw);
        Reference<T> resultRef = map.get(wrapper);
        if (resultRef != null) {
            /**
             * We have found one in the map, but we need to check again, as gc may
             * be triggered just now.
             * If there is gc before the next get and the object is cleared, then
             * result is null, we can skip to the next part.
             * If there is no gc or the object is not cleared yet at the time of
             * the next get. We have now a new reference to the object, so this
             * object will not be cleared, it's safe to return.
             */
            result = resultRef.get();

            if (result != null) {
                // still available
                return result;
            }
        }

        Reference<T> newObjectRef = new FinalizableWeakReference<T>(raw);
        Reference<T> oldRef = map.putIfAbsent(newObjectRef, newObjectRef);
        if (oldRef == null) {
            // insert successfully
            return raw;
        } else {
            /**
             * some one may insert before us in another thread.
             * If result is not null, it's safe to return it, as the proof we gave
             * previously.
             * If the result is null, it means the new inserted object is cleared
             * unluckily, then we can do another try or just return the raw value;
             * As this may not happen frequently, we choose to return the raw.
             */
            result = oldRef.get();
            if (result != null) {
                return result;
            } else {
                return raw;
            }
        }
    }

    public int getSize() {
        return map.size();
    }

    protected class FinalizableWeakReference<T> extends WeakReference<T> implements FinalizableReference<T> {

        private final int hashCode;

        /**
         * 构造一个新的 弱引用对象的键类 实例。
         * @param referent 键。
         */
        protected FinalizableWeakReference(T referent) {
            super(referent, FinalizableReferenceQueue.getInstance());
            this.hashCode = referent.hashCode();
        }

        @Override
        public void finalizeReferent() {
            SharedObjectPool.this.map.remove(this);
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }

        @Override
        public boolean equals(Object object) {
            if (object == null)
                return false;

            // for remove
            if (object == this)
                return true;

            Object thisRef = get();
            if (thisRef == null) {
                return false;
            }

            return thisRef.equals(object);
        }
    }

    /**
     * The internal map has reference, so we need to use get for equals
     * @param <T>
     */
    public static class Wrapper<T> {
        private Object ref;

        Wrapper(Object ref) {
            this.ref = ref;
        }

        @Override
        public int hashCode() {
            return ref.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Reference<?>) {
                return ref.equals(((Reference)obj).get());
            } else {
                return ref.equals(obj);
            }
        }
    }
}
