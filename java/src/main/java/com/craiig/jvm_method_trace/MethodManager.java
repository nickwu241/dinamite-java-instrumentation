package com.craiig.jvm_method_trace;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

public class MethodManager {
    public static MethodInfo[] methods = new MethodInfo[1 << 18];
    private static AtomicInteger nextId = new AtomicInteger();

    /**
     * @return id (index to 'methods' field) of the registered method.
     */
    public static int registerMethod(String owner, String name, String signature,
                                      int size, int lines, int longest_path,
                                      boolean instrumented, boolean has_invoke,
                                      boolean has_loop, boolean is_native)
    {
        int id = nextId.getAndIncrement();
        MethodInfo mi = new MethodInfo(owner, name, signature, size, lines, longest_path,
                                       instrumented, has_invoke, has_loop, is_native);
        methods[id] = mi;
        return id;
    }

    /**
     * Outputs method information summary in tsv format.
     * @param writer destination of summary.
     */
    public static void outputSummary(PrintWriter writer)
    {
        writer.printf("id\tclass\tmethod\tsignature\tcalls\tcalls2\tinstructions\tlines\tlongest_path\tinstrumented\tinvoke\tloop\tnative\n");
        for (int i = nextId.get() - 1; i >= 0; i--) {
            MethodInfo mi = methods[i];
            writer.printf("%d\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%d\t%b\t%b\t%b\t%b\n",
                          i, mi.owner, mi.name, mi.signature, mi.entry_cnt, mi.entry_cnt2,
                          mi.size, mi.lines, mi.longest_path, mi.instrumented,
                          mi.has_invoke, mi.has_loop, mi.is_native);
        }
    }
}

/**
 * Note: this comparator imposes orderings that are inconsistent with equals.
 */
class SortMethodInfoBySize implements Comparator<MethodInfo> {
    @Override
    public int compare(MethodInfo m1, MethodInfo m2)
    {
        if (m1.size == m2.size) {
            if (m1.entry_cnt > m2.entry_cnt)
                return 1;
            else if (m1.entry_cnt < m2.entry_cnt)
                return -1;
            return 0;
        }
        return m1.size - m2.size;
    }
}

/**
 * Note: this comparator imposes orderings that are inconsistent with equals.
 */
class SortMethodInfoByCalls implements Comparator<MethodInfo> {
    @Override
    public int compare(MethodInfo m1, MethodInfo m2)
    {
        if (m1.entry_cnt > m2.entry_cnt)
            return 1;
        else if (m1.entry_cnt < m2.entry_cnt)
            return -1;
        return 0;
    }
}