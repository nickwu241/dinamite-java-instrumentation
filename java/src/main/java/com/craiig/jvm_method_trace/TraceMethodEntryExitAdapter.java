package com.craiig.jvm_method_trace;

import com.craiig.jvm_method_trace.settings.Settings;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.util.Random;

class TraceMethodEntryExitAdapter extends GeneratorAdapter implements Opcodes {

    private String owner;
    private String name;
    private static Random rand = new Random();
    private int cnum;
    private int mnum;
    private int should_timestamp;

    public TraceMethodEntryExitAdapter(final MethodVisitor mv, final String owner, final String name, final String desc, final int access, int id) {
        super(Opcodes.ASM5, mv, access, name, desc);
        this.owner = owner;
        this.name = name;
        this.cnum = 0;//rand.nextInt();
        this.mnum = id;
    }

    private void doInsertMethodEntry() {
        mv.visitFieldInsn(GETSTATIC, "com/craiig/jvm_method_trace/Mtrace", "methods", "[Lcom/craiig/jvm_method_trace/MethodInfo;");
        push(mnum);
        mv.visitInsn(AALOAD);
        mv.visitInsn(DUP);
        mv.visitFieldInsn(GETFIELD, "com/craiig/jvm_method_trace/MethodInfo", "entry_cnt", "J");
        mv.visitInsn(LCONST_1);
        mv.visitInsn(LADD);
        mv.visitFieldInsn(PUTFIELD, "com/craiig/jvm_method_trace/MethodInfo", "entry_cnt", "J");
    }

    private void doInsertMethodExit() {
        mv.visitFieldInsn(GETSTATIC, "com/craiig/jvm_method_trace/Mtrace", "methods", "[Lcom/craiig/jvm_method_trace/MethodInfo;");
        push(mnum);
        mv.visitInsn(AALOAD);
        mv.visitInsn(DUP);
        mv.visitFieldInsn(GETFIELD, "com/craiig/jvm_method_trace/MethodInfo", "exit_cnt", "J");
        mv.visitInsn(LCONST_1);
        mv.visitInsn(LADD);
        mv.visitFieldInsn(PUTFIELD, "com/craiig/jvm_method_trace/MethodInfo", "exit_cnt", "J");
    }

    private void insertMethodEntry() {
        switch (Settings.mode) {
            case PRINT:
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("method_entry: " + this.owner + "." + this.name);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                break;
            case DIRECT:
                doInsertMethodEntry();
                break;
            case DIRECT_TEST:
                mv.visitFieldInsn(GETSTATIC, "com/craiig/jvm_method_trace/Mtrace", "engaged", "Z");
                Label l0 = new Label();
                mv.visitJumpInsn(IFEQ, l0);
                doInsertMethodEntry();
                mv.visitLabel(l0);
                break;
            case DIRECT_TIMESTAMP:
                int id = newLocal(Type.LONG_TYPE);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
                mv.visitVarInsn(LSTORE, id);
                break;
            case DIRECT_TIMESTAMP_NATIVE:
                id = newLocal(Type.LONG_TYPE);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "rdtsc", "()J", false);
                mv.visitVarInsn(LSTORE, id);
                break;
            case TIMESTAMP_SAMPLE:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_entry_timestamp2", "(I)V", false);
                break;
            case INVOKE:
                push(cnum);
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_entry", "(II)V", false);
                break;
            case INVOKE_NOARGS:
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_entry_noargs", "()V", false);
                break;
            case INVOKE_EMPTY:
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_entry_empty", "()V", false);
                break;
            case INVOKE_NOTEST:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_entry_notest", "(I)V", false);
                break;
            case INVOKE_TRACE_NATIVE:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_entry_native", "(I)V", false);
                break;
            case INVOKE_BINTRACE:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_entry_bintrace", "(I)V", false);
                break;
            case INVOKE_BINMEM:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_entry_binmem", "(I)V", false);
                break;
            case NOP:
                mv.visitInsn(NOP);
                break;
        }
    }

    private void insertMethodExit() {
        switch (Settings.mode) {
            case PRINT:
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("method_exit: " + this.owner + "." + this.name);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                break;
            case DIRECT:
                doInsertMethodEntry();
                break;
            case DIRECT_TEST:
                mv.visitFieldInsn(GETSTATIC, "com/craiig/jvm_method_trace/Mtrace", "engaged", "Z");
                Label l0 = new Label();
                mv.visitJumpInsn(IFEQ, l0);
                doInsertMethodExit();
                mv.visitLabel(l0);
                break;
            case DIRECT_TIMESTAMP:
                int timestamp = newLocal(Type.LONG_TYPE);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
                mv.visitVarInsn(LSTORE, timestamp);
                break;
            case DIRECT_TIMESTAMP_NATIVE:
                timestamp = newLocal(Type.LONG_TYPE);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "rdtsc", "()J", false);
                mv.visitVarInsn(LSTORE, timestamp);
                break;
            case TIMESTAMP_SAMPLE:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_exit_timestamp2", "(I)V", false);

                int id = newLocal(Type.LONG_TYPE);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
                mv.visitVarInsn(LSTORE, id);
                break;
            case INVOKE:
                push(cnum);
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_exit", "(II)V", false);
                break;
            case INVOKE_NOARGS:
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_exit_noargs", "()V", false);
                break;
            case INVOKE_EMPTY:
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_exit_empty", "()V", false);
                break;
            case INVOKE_NOTEST:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_exit_notest", "(I)V", false);
                break;
            case INVOKE_TRACE_NATIVE:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_exit_native", "(I)V", false);
                break;
            case INVOKE_BINTRACE:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_exit_bintrace", "(I)V", false);
                break;
            case INVOKE_BINMEM:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "method_exit_binmem", "(I)V", false);
                break;
            case NOP:
                mv.visitInsn(NOP);
                break;
        }
    }

    @Override
    public void visitCode() {
        if (mv != null) mv.visitCode();
        //System.out.printf("[entry] entry/exit: visit %s.%s\n", owner, name);
        insertMethodEntry();
        if (name.equals("main")) {
            mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace", "start", "()V", false);
        }
    }

    @Override
    public void visitEnd() {
        //System.out.printf("[exit] entry/exit: visit %s.%s\n", owner, name);
        if (mv != null) mv.visitEnd();
    }

    @Override
    public void visitInsn(int opcode) {
        if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
            insertMethodExit();
        }

        if (mv != null)
            mv.visitInsn(opcode);
    }
}
