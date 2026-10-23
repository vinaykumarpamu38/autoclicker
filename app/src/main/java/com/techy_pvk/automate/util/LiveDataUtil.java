package com.techy_pvk.automate.util;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.function.Function;

/**
 * Tiny stand-ins for androidx.lifecycle.Transformations. Written out by hand on purpose: the
 * Transformations Java API changed shape across lifecycle releases (Java Function vs Kotlin
 * Function1 overloads), which makes lambda calls ambiguous on some versions. MediatorLiveData's
 * addSource() is stable across all of them.
 */
public final class LiveDataUtil {

    private LiveDataUtil() {}

    /** A LiveData that re-emits `source` run through `mapper`. Must be called on the main thread. */
    @NonNull
    public static <A, B> LiveData<B> map(@NonNull LiveData<A> source, @NonNull Function<A, B> mapper) {
        MediatorLiveData<B> result = new MediatorLiveData<>();
        result.addSource(source, value -> result.setValue(mapper.apply(value)));
        return result;
    }
}
