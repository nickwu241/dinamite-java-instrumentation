package com.craiig.jvm_method_trace.settings;

import java.util.EnumSet;

public enum Filter {
    KEEP_INVOKE,
    KEEP_LOOP,
    KEEP_NATIVE,
    LINE_NUMBER,
    NO_LEAF;

    public static final EnumSet<Filter> ALL = EnumSet.allOf(Filter.class);
    public static final EnumSet<Filter> NONE = EnumSet.noneOf(Filter.class);
}
