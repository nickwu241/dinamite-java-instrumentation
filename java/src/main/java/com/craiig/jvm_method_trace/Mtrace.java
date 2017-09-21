package com.craiig.jvm_method_trace;
/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */
import java.io.*;

/**
 * Java class to hold static methods which will be called in byte code
 * injections of all class files.
 */
public class Mtrace {
    /* Master switch that activates methods. */
    public static boolean engaged = false;

    private static long entry_cnt = 0;
    private static long exit_cnt = 0;

    private static String outfile;

    private static final int BUFFER_SIZE = 11 * 4096;
    private static final int MAX_THREADS = 128;
    private static byte[][] buffer;
    private static int[] offset;
    private static FileOutputStream[] trace;

    public static void init(String outfile)
    {
        // outfile is nullable
        Mtrace.outfile = outfile;

        buffer = new byte[MAX_THREADS][];
        offset = new int[MAX_THREADS];
        trace = new FileOutputStream[MAX_THREADS];
    }

    public static void start()
    {
        System.out.printf("Mtrace.start(): thread=%s, ID=%d\n",
                          Thread.currentThread().getName(),
                          Thread.currentThread().getId());
	    engaged = true;
    }

    public static void stop()
    {
        System.out.printf("Mtrace.stop(): thread=%s, ID=%d\n",
                          Thread.currentThread().getName(),
                          Thread.currentThread().getId());
        if (!engaged)
            return;

	    engaged = false;

	    // flush all traces
	    for (int i = 0; i < MAX_THREADS; i++) {
	        if (trace[i] != null) {
                try {
                    trace[i].flush();
                    trace[i].close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // write method summaries to outfile
        if (outfile != null) {
	        File outputTsv = new File(outfile);
            try {
                outputTsv.createNewFile();
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            try (PrintWriter out = new PrintWriter(outputTsv)) {
                MethodManager.outputSummary(out);
                out.flush();
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        System.out.printf("entry_cnt: %d exit_cnt: %d c_instrumented: %d c_not_instrumented: %d m_instrumented: %d m_not_instrumented: %d m_instrumented_invoke: %d m_instrumented_loop: %d m_instrumented_native: %d\n",
                          entry_cnt, exit_cnt, IStats.c_instrumented, IStats.c_not_instrumented,
                          IStats.m_instrumented, IStats.m_not_instrumented, IStats.m_instrumented_invoke,
                          IStats.m_instrumented_loop, IStats.m_instrumented_native);
    }

    public static native long rdtsc();

    /* At the very beginning of every method, a call to method_entry()
     * is injected.
     * Before any of the return bytecodes, a call to method_exit()
     * is injected.
     */
    public static native void method_entry_native(int mnum);
    public static native void method_exit_native(int mnum);
    public static native void method_entry_empty_native();
    public static native void method_exit_empty_native();

    public static void method_entry(int mnum)
    {
        if (engaged) {
            MethodManager.methods[mnum].entry_cnt++;
            entry_cnt++;
        }
    }

    public static void method_exit(int mnum)
    {
        if (engaged) {
            MethodManager.methods[mnum].exit_cnt++;
            exit_cnt++;
        }
    }

    public static void method_entry_noargs()
    {
        if (engaged) {
            entry_cnt++;
        }
    }

    public static void method_exit_noargs()
    {
        if (engaged) {
            exit_cnt++;
        }
    }

    public static void method_entry_notest(int mnum)
    {
        MethodManager.methods[mnum].entry_cnt++;
    }

    public static void method_exit_notest(int mnum)
    {
        MethodManager.methods[mnum].exit_cnt++;
    }

    public static void method_entry_empty()
    { }

    public static void method_exit_empty()
    { }

    public static void method_entry_bintrace(int mnum) {
        if (engaged) {
            int tid = (int) Thread.currentThread().getId();
            // initialize if needed
            if (buffer[tid] == null) {
                buffer[tid] = new byte[BUFFER_SIZE];
                offset[tid] = 0;
                try {
                    trace[tid] = new FileOutputStream(new File("trace.bin." + tid));
                }
                catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
            // write if buffer is full
            if (offset[tid] >= BUFFER_SIZE) {
                try {
                    trace[tid].write(buffer[tid], 0, BUFFER_SIZE);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                offset[tid] = 0;
            }
            long ts = System.nanoTime();
            buffer[tid][offset[tid]++] = 0x00; // function begin
            buffer[tid][offset[tid]++] = (byte)(mnum >>> 8);
            buffer[tid][offset[tid]++] = (byte)(mnum & 0xFF);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 56);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 48);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 40);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 32);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 24);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 16);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 8);
            buffer[tid][offset[tid]++] = (byte)(ts & 0xFF);
        }
    }

    public static void method_exit_bintrace(int mnum) {
        if (engaged) {
            int tid = (int) Thread.currentThread().getId();
            // initialize if needed
            if (buffer[tid] == null) {
                buffer[tid] = new byte[BUFFER_SIZE];
                offset[tid] = 0;
                try {
                    trace[tid] = new FileOutputStream(new File("trace.bin." + tid));
                }
                catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
            // write if buffer is full
            if (offset[tid] >= BUFFER_SIZE) {
                try {
                    trace[tid].write(buffer[tid], 0, BUFFER_SIZE);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                offset[tid] = 0;
            }
            long ts = System.nanoTime();
            buffer[tid][offset[tid]++] = 0x01; // function end
            buffer[tid][offset[tid]++] = (byte)(mnum >>> 8);
            buffer[tid][offset[tid]++] = (byte)(mnum & 0xFF);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 56);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 48);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 40);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 32);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 24);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 16);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 8);
            buffer[tid][offset[tid]++] = (byte)(ts & 0xFF);
        }
    }

    public static void method_entry_binmem(int mnum) {
        if (engaged) {
            int tid = (int) Thread.currentThread().getId();
            // initialize if needed
            if (buffer[tid] == null) {
                buffer[tid] = new byte[BUFFER_SIZE];
                offset[tid] = 0;
                try {
                    trace[tid] = new FileOutputStream(new File("trace.bin." + tid));
                }
                catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
            // write if buffer is full
            if (offset[tid] >= BUFFER_SIZE) {
                offset[tid] = 0;
            }
            long ts = System.nanoTime();
            buffer[tid][offset[tid]++] = 0x00; // function begin
            buffer[tid][offset[tid]++] = (byte)(mnum >>> 8);
            buffer[tid][offset[tid]++] = (byte)(mnum & 0xFF);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 56);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 48);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 40);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 32);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 24);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 16);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 8);
            buffer[tid][offset[tid]++] = (byte)(ts & 0xFF);
        }
    }

    public static void method_exit_binmem(int mnum) {
        if (engaged) {
            int tid = (int) Thread.currentThread().getId();
            // initialize if needed
            if (buffer[tid] == null) {
                buffer[tid] = new byte[BUFFER_SIZE];
                offset[tid] = 0;
                try {
                    trace[tid] = new FileOutputStream(new File("trace.bin." + tid));
                }
                catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
            if (offset[tid] >= BUFFER_SIZE) {
                offset[tid] = 0;
            }
            long ts = System.nanoTime();
            buffer[tid][offset[tid]++] = 0x01; // function end
            buffer[tid][offset[tid]++] = (byte)(mnum >>> 8);
            buffer[tid][offset[tid]++] = (byte)(mnum & 0xFF);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 56);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 48);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 40);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 32);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 24);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 16);
            buffer[tid][offset[tid]++] = (byte)(ts >>> 8);
            buffer[tid][offset[tid]++] = (byte)(ts & 0xFF);
        }
    }

    public static void method_entry_timestamp2(int mnum)
    {
        long cnt = MethodManager.methods[mnum].entry_cnt++;

        if (cnt < 256 || (cnt & (cnt - 1)) == 0 || cnt % (1 << 14) < 64) {
            MethodManager.methods[mnum].entry_cnt2++;
            long start = System.nanoTime();
        }
    }

    public static void method_exit_timestamp2(int mnum)
    {
        long cnt = MethodManager.methods[mnum].exit_cnt++;

        if (cnt < 256 || (cnt & (cnt - 1)) == 0 || cnt % (1 << 14) < 64) {
            MethodManager.methods[mnum].exit_cnt2++;
            long end = System.nanoTime();
        }
    }
}
