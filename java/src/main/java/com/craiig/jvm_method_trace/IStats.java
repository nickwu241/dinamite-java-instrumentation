package com.craiig.jvm_method_trace;

/**
 * Class to hold instrumentation statistics.
 */
public class IStats {
    public static long c_instrumented = 0;
    public static long c_not_instrumented = 0;
    public static long m_instrumented = 0;
    public static long m_instrumented_invoke = 0;
    public static long m_instrumented_loop = 0;
    public static long m_instrumented_native = 0;
    public static long m_not_instrumented = 0;
}
