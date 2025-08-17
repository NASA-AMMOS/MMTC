package edu.jhuapl.sd.sig.mmtc.util;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

// Not threadsafe, but uses AtomicReference for convenience
public class Settable<T> {
    private final AtomicReference<T> val = new AtomicReference<>();

    public void set(T newVal) {
        if (newVal == null) {
            throw new IllegalArgumentException("Cannot set value to null.");
        }

        if (! val.compareAndSet(null, newVal)) {
            throw new IllegalStateException("Value already set.");
        }
    }

    public boolean isSet() {
        return val.get() != null;
    }

    public T get() {
        T v = val.get();
        if (v == null) {
            throw new IllegalStateException("Value not set.");
        }
        return v;
    }

    public String toString() {
        throw new UnsupportedOperationException("Cannot serialize Settable to a String.");
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Settable<?> settable = (Settable<?>) o;
        return Objects.equals(val, settable.val);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(val);
    }
}
