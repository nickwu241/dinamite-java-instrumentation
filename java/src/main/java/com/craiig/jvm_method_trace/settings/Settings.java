package com.craiig.jvm_method_trace.settings;

import java.util.EnumSet;

public class Settings {
    public static Mode mode = Mode.DIRECT; // mode for agent to run in
    public static int N = 0; // minimum # of instructions to instrument a method
    public static EnumSet<Filter> filters = Filter.NONE; // type of methods to instrument regardless of N
    public static String[] include = new String[0];     // instrument all classes from these packages
    public static String[] exclude = new String[0];     // don't instrument classes from these packages
    public static boolean engage = false; // flag to start Mtrace at program start
    public static boolean write = false; // save instrumented class into .adapted.class file
    public static String outfile = null; // output file name

    /**
     * Parses a key-value pair for an agent argument.
     * @param key an option name
     * @param value value of the option
     * @throws IllegalArgumentException Unrecognized key or unrecognized value for a specific key
     */
    public static void setFromKeyVal(String key, String value) {
        switch (key) {
            case "mode":
                switch (value) {
                    case "print":
                        mode = Mode.PRINT;
                        break;
                    case "direct":
                        mode = Mode.DIRECT;
                        break;
                    case "direct-test":
                        mode = Mode.DIRECT_TEST;
                        break;
                    case "direct-timestamp":
                        mode = Mode.DIRECT_TIMESTAMP;
                        break;
                    case "direct-timestamp-native":
                        mode = Mode.DIRECT_TIMESTAMP_NATIVE;
                        break;
                    case "timestamp-sample":
                        mode = Mode.TIMESTAMP_SAMPLE;
                        break;
                    case "invoke":
                        mode = Mode.INVOKE;
                        break;
                    case "invoke-noargs":
                        mode = Mode.INVOKE_NOARGS;
                        break;
                    case "invoke-empty":
                        mode = Mode.INVOKE_EMPTY;
                        break;
                    case "invoke-notest":
                        mode = Mode.INVOKE_NOTEST;
                        break;
                    case "invoke-trace-native":
                        mode = Mode.INVOKE_TRACE_NATIVE;
                        break;
                    case "invoke-empty-native":
                        mode = Mode.INVOKE_EMPTY_NATIVE;
                        break;
                    case "invoke-bintrace":
                        mode = Mode.INVOKE_BINTRACE;
                        break;
                    case "invoke-binmem":
                        mode = Mode.INVOKE_BINMEM;
                        break;
                    case "nop":
                        mode = Mode.NOP;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown mode " + value);
                }
                break;
            case "write":
                write = true;
                break;
            case "include":
                include = value.split(";");
                break;
            case "exclude":
                exclude = value.split(";");
                break;
            case "engage":
                engage = true;
                break;
            case "N":
                N = Integer.parseInt(value);
                break;
            case "filter":
                switch (value) {
                    case "all":
                        filters = Filter.ALL;
                        break;
                    case "keep-invoke":
                        filters.add(Filter.KEEP_INVOKE);
                        break;
                    case "keep-loop":
                        filters.add(Filter.KEEP_LOOP);
                        break;
                    case "keep-native":
                        filters.add(Filter.KEEP_NATIVE);
                        break;
                    case "line-number":
                        filters.add(Filter.LINE_NUMBER);
                        break;
                    case "no-leaf":
                        filters.add(Filter.NO_LEAF);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown filter " + value);
                }
                break;
            case "outfile":
                outfile = value;
                break;
            default:
                throw new IllegalArgumentException("Unknown argument " + key);
        }
    }
}
