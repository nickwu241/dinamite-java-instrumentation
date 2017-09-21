package com.craiig.jvm_method_trace;

public class MethodInfo {
    public final String owner;
    public final String name;
    public final String signature;
    public final int size;
    public final int lines;
    public final int longest_path;

    public final boolean instrumented;
    public final boolean has_invoke;
    public final boolean has_loop;
    public final boolean is_native;
    public long entry_cnt;
    public long exit_cnt;
    public long entry_cnt2;
    public long exit_cnt2;

    public MethodInfo(String owner, String name, String signature,
                      int size, int lines, int longest_path,
                      boolean instrumented, boolean has_invoke,
                      boolean has_loop, boolean is_native)
    {
        this.owner = owner;
        this.name = name;
        this.signature = signature;
        this.lines = lines;
        this.longest_path = longest_path;
        this.size = size;
        this.instrumented = instrumented;
        this.has_invoke = has_invoke;
        this.has_loop = has_loop;
        this.is_native = is_native;
    }
}
