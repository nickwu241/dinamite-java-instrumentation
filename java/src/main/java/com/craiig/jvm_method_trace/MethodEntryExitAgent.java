package com.craiig.jvm_method_trace;

import com.craiig.jvm_method_trace.settings.Filter;
import com.craiig.jvm_method_trace.settings.Settings;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodEntryExitAgent implements ClassFileTransformer {
    private static final String DINAMITE_PACAKGE_PREFIX = "com/craiig/";

    @Override
    public byte[] transform(ClassLoader l, String name, Class c, ProtectionDomain d, byte[] b) throws IllegalClassFormatException {
        // don't instrument our own classes
        if (name.startsWith(DINAMITE_PACAKGE_PREFIX)) {
            return null;
        }
        ClassReader cr = new ClassReader(b);

        // get methods sizes
        ClassNode cn = new ClassNode();
        try {
            cr.accept(cn, 0);
        } catch(Exception e) {
            System.out.printf("error [1] when instrumenting %s v%d (%s, %s)\n", name, cn.version, l.getClass().getName(), e.toString());
            IStats.c_not_instrumented++;
            return null;
        }

        // if include is used, we exclude everything not in the include
        if (Settings.include.length != 0 &&
                Arrays.stream(Settings.include).noneMatch(pack -> name.matches(pack))) {
            IStats.c_not_instrumented++;
            IStats.m_not_instrumented += cn.methods.size();
            return null;
        }

        // don't instrument packages/classes in exclusion list provided by agent args
        for (String pack: Settings.exclude) {
            if (name.matches(pack)) {
                IStats.c_not_instrumented++;
                IStats.m_not_instrumented += cn.methods.size();
                return null;
            }
        }

        Map<String,MethodNode> methods = new HashMap<>(cn.methods.size());

        for (MethodNode method: (List<MethodNode>) cn.methods) {
            methods.put(method.name, method);
        }

        ClassWriter cw = new ComputeClassWriter((cn.version & 0xff) >= 51? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new TraceMethodEntryExitClassAdapter(cw, methods);
        try {
            cr.accept(cv, ClassReader.SKIP_FRAMES);
        } catch(Exception e) {
            IStats.c_not_instrumented++;
            IStats.m_not_instrumented += methods.size();
            System.out.printf("error [2] when instrumenting %s v%d (%s)\n", name, cn.version, e.getMessage());
            return null;
        }

        b = cw.toByteArray();

        if (Settings.write) {
            try {
                FileOutputStream fos = new FileOutputStream(name.replace("/", ".") + ".adapted." + Settings.mode.name().toLowerCase() + ".class");
                fos.write(b);
                fos.close();
            } catch (IOException e) {
                System.err.printf("can't save class %s (%s)\n", name, e.getMessage());
            }
        }

        IStats.c_instrumented++;
        return b;
    }

    /**
     * This function is ran before main by specifying -javaagent:jarpath[=options].
     * See https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/package-summary.html for details.
     *
     * @param agentArgs options for this agent, should be comma separated key-value pairs.
     *                  Values may be null depending on the key.
     * @param inst
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        for (String arg: agentArgs.split(",")) {
            String[] kv = arg.split("=");
            Settings.setFromKeyVal(kv[0], kv.length > 1 ? kv[1] : null);
        }

        Mtrace.init(Settings.outfile);

        ClassFileTransformer agent = new MethodEntryExitAgent();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            inst.removeTransformer(agent);
            Mtrace.stop();
        }));

        // transform classes that were already loaded before this method is called (system classes)
        // TODO: why does instrumenting java.lang.invoke causes program to crash?
//        Class[] retrans = Arrays.stream(inst.getAllLoadedClasses())
//                                //.filter(klass -> !klass.getName().contains("java.lang.invoke"))
//                                .filter(klass -> klass.getName().contains("java.io"))
//                                .filter(inst::isModifiableClass)
//                                .toArray(Class[]::new);

        inst.addTransformer(agent, true);

//        try {
//            inst.retransformClasses(retrans);
//        }
//        catch (UnmodifiableClassException e) {
//            // shouldn't get here because we check that isModifiableClass returns true.
//            e.printStackTrace();
//        }
//
        if (Settings.engage) {
            Mtrace.start();
        }
    }
}

/**
 * A ClassWriter that computes the common super class of two classes without
 * actually loading them with a ClassLoader.
 *
 * @author Eric Bruneton
 */
class ComputeClassWriter extends ClassWriter {

    private ClassLoader l = getClass().getClassLoader();

    public ComputeClassWriter(final int flags) {
        super(flags);
        /* null class loader probably means  system class loader ;) */
        if (l == null)
            l = ClassLoader.getSystemClassLoader();
    }

    @Override
    protected String getCommonSuperClass(final String type1, final String type2) {
        try {
            ClassReader info1 = typeInfo(type1);
            ClassReader info2 = typeInfo(type2);
            if ((info1.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
                if (typeImplements(type2, info2, type1)) {
                    return type1;
                }
                if ((info2.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
                    if (typeImplements(type1, info1, type2)) {
                        return type2;
                    }
                }
                return "java/lang/Object";
            }
            if ((info2.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
                if (typeImplements(type1, info1, type2)) {
                    return type2;
                } else {
                    return "java/lang/Object";
                }
            }
            StringBuilder b1 = typeAncestors(type1, info1);
            StringBuilder b2 = typeAncestors(type2, info2);
            String result = "java/lang/Object";
            int end1 = b1.length();
            int end2 = b2.length();
            while (true) {
                int start1 = b1.lastIndexOf(";", end1 - 1);
                int start2 = b2.lastIndexOf(";", end2 - 1);
                if (start1 != -1 && start2 != -1
                        && end1 - start1 == end2 - start2) {
                    String p1 = b1.substring(start1 + 1, end1);
                    String p2 = b2.substring(start2 + 1, end2);
                    if (p1.equals(p2)) {
                        result = p1;
                        end1 = start1;
                        end2 = start2;
                    } else {
                        return result;
                    }
                } else {
                    return result;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * Returns the internal names of the ancestor classes of the given type.
     *
     * @param type
     *            the internal name of a class or interface.
     * @param info
     *            the ClassReader corresponding to 'type'.
     * @return a StringBuilder containing the ancestor classes of 'type',
     *         separated by ';'. The returned string has the following format:
     *         ";type1;type2 ... ;typeN", where type1 is 'type', and typeN is a
     *         direct subclass of Object. If 'type' is Object, the returned
     *         string is empty.
     * @throws IOException
     *             if the bytecode of 'type' or of some of its ancestor class
     *             cannot be loaded.
     */
    private StringBuilder typeAncestors(String type, ClassReader info)
            throws IOException {
        StringBuilder b = new StringBuilder();
        while (!"java/lang/Object".equals(type)) {
            b.append(';').append(type);
            type = info.getSuperName();
            info = typeInfo(type);
        }
        return b;
    }

    /**
     * Returns true if the given type implements the given interface.
     *
     * @param type
     *            the internal name of a class or interface.
     * @param info
     *            the ClassReader corresponding to 'type'.
     * @param itf
     *            the internal name of a interface.
     * @return true if 'type' implements directly or indirectly 'itf'
     * @throws IOException
     *             if the bytecode of 'type' or of some of its ancestor class
     *             cannot be loaded.
     */
    private boolean typeImplements(String type, ClassReader info, String itf)
            throws IOException {
        while (!"java/lang/Object".equals(type)) {
            String[] itfs = info.getInterfaces();
            for (int i = 0; i < itfs.length; ++i) {
                if (itfs[i].equals(itf)) {
                    return true;
                }
            }
            for (int i = 0; i < itfs.length; ++i) {
                if (typeImplements(itfs[i], typeInfo(itfs[i]), itf)) {
                    return true;
                }
            }
            type = info.getSuperName();
            info = typeInfo(type);
        }
        return false;
    }

    /**
     * Returns a ClassReader corresponding to the given class or interface.
     *
     * @param type
     *            the internal name of a class or interface.
     * @return the ClassReader corresponding to 'type'.
     * @throws IOException
     *             if the bytecode of 'type' cannot be loaded.
     */
    private ClassReader typeInfo(final String type) throws IOException {
        InputStream is = l.getResourceAsStream(type + ".class");
        try {
            return new ClassReader(is);
        } finally {
            is.close();
        }
    }
}

class TraceMethodEntryExitClassAdapter extends ClassVisitor implements Opcodes {
    private String owner;
    private Map<String,MethodNode> methods;

    TraceMethodEntryExitClassAdapter(final ClassVisitor cv, Map<String, MethodNode> methods) {
        super(ASM5, cv);
        this.methods = methods;
    }

    @Override
    public void visit(final int version, final int access, final String name,
            final String signature, final String superName,
            final String[] interfaces) {
        owner = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name,
            final String desc, final String signature, final String[] exceptions) {

        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        if ((access & ACC_ABSTRACT) == ACC_ABSTRACT) {
            return mv;
        }

        MethodNode method = methods.get(name);
        assert(method != null);

        boolean has_invoke = false;
        boolean has_loop = false;
        boolean is_native = (access & ACC_NATIVE) == ACC_NATIVE;
        int highestLineNo = -1;
        int lineCount = 0;

        AbstractInsnNode insn = method.instructions.getFirst();
        while (insn != null) {
            switch (insn.getType()) {
                case AbstractInsnNode.JUMP_INSN:
                    JumpInsnNode jump = (JumpInsnNode) insn;
                    if (method.instructions.indexOf(jump) > method.instructions.indexOf(jump.label))
                        has_loop = true;
                    break;
                case AbstractInsnNode.METHOD_INSN:
                    has_invoke = true;
                    break;
                case AbstractInsnNode.LINE:
                    LineNumberNode lineIns = (LineNumberNode) insn;
                    int lineNo = lineIns.line;
                    if (lineNo > highestLineNo) {
                        highestLineNo = lineNo;
                        lineCount++;
                    }
                    break;
                default:
                    break;
            }

            insn = insn.getNext();
        }

        int instructionSize = method.instructions.size();
        boolean instrumented = Settings.filters.contains(Filter.LINE_NUMBER) ?
                lineCount >= Settings.N :
                instructionSize >= Settings.N;

        if (!instrumented) {
            if (Settings.filters.contains(Filter.KEEP_INVOKE) && has_invoke) {
                instrumented = true;
                IStats.m_instrumented_invoke++;
            } else if (Settings.filters.contains(Filter.KEEP_LOOP) && has_loop) {
                instrumented = true;
                IStats.m_instrumented_loop++;
            } else if (Settings.filters.contains(Filter.KEEP_NATIVE) && is_native) {
                instrumented = true;
                IStats.m_instrumented_native++;
            }
        }

        // don't instrument 100% leaf methods
        if (Settings.filters.contains(Filter.NO_LEAF) && instrumented && !has_invoke) {
            instrumented = false;
        }

        int longestPath = longestPathInMethod(method.instructions);
        int id = MethodManager.registerMethod(owner, name, desc, instructionSize, lineCount, longestPath,
                                              instrumented, has_invoke, has_loop, is_native);

        if (instrumented) {
            IStats.m_instrumented++;
            // return new TraceMethodEntryExitAdapter(mv, owner, name, desc, access, id);
            return new TraceMethodAdapter(mv, access, name, desc, id);
        }
        IStats.m_not_instrumented++;
        return mv;
    }

    private int longestPathInMethod(InsnList instructions) {
        int insnCount = instructions.size();
        if (insnCount < 1) {
            return 0;
        }
        int[] longestPathFromInsn = new int[insnCount];
        longestPathFromInsn[insnCount-1] = 1;
        AbstractInsnNode currentInsn = instructions.getLast();
        for (int i = insnCount-2; i >= 0; i--) {
            currentInsn = currentInsn.getPrevious();
            int targetIndex;
            List<LabelNode> targets;

            switch(currentInsn.getType()){
                case AbstractInsnNode.JUMP_INSN:
                    JumpInsnNode jumpInsn = (JumpInsnNode) currentInsn;
                    switch (jumpInsn.getOpcode()) {
                        case Opcodes.GOTO:
                            // unconditional
                            targetIndex = instructions.indexOf(currentInsn);
                            if(targetIndex > i){
                                // forward jump
                                longestPathFromInsn[i] = longestPathFromInsn[targetIndex] + 1;
                            }else{
                                // backward jump, treat as not taken
                                longestPathFromInsn[i] = longestPathFromInsn[i+1] + 1;
                            }
                            break;
                        case Opcodes.JSR:
                            // this instruction is deprecated. we basically ignore it
                            longestPathFromInsn[i] = longestPathFromInsn[i+1] + 1;
                            break;
                        default:
                            // all others are conditional
                            targetIndex = instructions.indexOf(currentInsn);
                            if(targetIndex > i){
                                longestPathFromInsn[i] = Math.max(longestPathFromInsn[targetIndex], longestPathFromInsn[i+1]) + 1;
                            }else{
                                longestPathFromInsn[i] = longestPathFromInsn[i+1] + 1;
                            }
                            break;
                    }
                    break;
                case AbstractInsnNode.FRAME:
                case AbstractInsnNode.LABEL:
                case AbstractInsnNode.LINE:
                    // pseudo instructions do not count.
                    longestPathFromInsn[i] = longestPathFromInsn[i+1];
                    break;
                case AbstractInsnNode.LOOKUPSWITCH_INSN:
                    LookupSwitchInsnNode lookupSwitchInsn = (LookupSwitchInsnNode) currentInsn;
                    longestPathFromInsn[i] = 0;
                    targets = lookupSwitchInsn.labels;
                    for(LabelNode target : targets){
                        targetIndex = instructions.indexOf(target);
                        longestPathFromInsn[i] = Math.max(longestPathFromInsn[i], longestPathFromInsn[targetIndex] + 1);
                    }
                    break;
                case AbstractInsnNode.TABLESWITCH_INSN:
                    TableSwitchInsnNode tableSwitchInsn = (TableSwitchInsnNode) currentInsn;
                    longestPathFromInsn[i] = 0;
                    targets = tableSwitchInsn.labels;
                    for(LabelNode target : targets){
                        targetIndex = instructions.indexOf(target);
                        longestPathFromInsn[i] = Math.max(longestPathFromInsn[i], longestPathFromInsn[targetIndex] + 1);
                    }
                    break;
                default:
                    longestPathFromInsn[i] = longestPathFromInsn[i+1] + 1;
                    break;
            }
        }
        return longestPathFromInsn[0];
    }
}

class TraceMethodAdapter extends AdviceAdapter {
    private static final Type MTRACE_CLASS = Type.getType(Mtrace.class);
    private static final String MTRACE_ENGAGED = "engaged";
    private static final String METHOD_ENTRY_PREFIX = "method_entry";
    private static final String METHOD_EXIT_PREFIX = "method_exit";
    private final String name;
    private final int mnum;

    TraceMethodAdapter(MethodVisitor mv, int acc, String name, String desc, int mnum) {
        super(ASM5, mv, acc, name, desc);
        this.name = name;
        this.mnum = mnum;
    }

    @Override
    protected void onMethodEnter() {
        insertToMethod(METHOD_ENTRY_PREFIX);
    }

    @Override
    protected void onMethodExit(int opcode) {
        insertToMethod(METHOD_EXIT_PREFIX);
    }

    /**
     * @param methodPrefix "method_entry" or "method_exit" or null
     */
    private void insertToMethod(String methodPrefix) {
        switch (Settings.mode) {
            case PRINT:
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn(methodPrefix + " : " + name);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                break;
            case DIRECT:
                directInsert(methodPrefix);
                break;
            case DIRECT_TEST:
                mv.visitFieldInsn(GETSTATIC, "com/craiig/jvm_method_trace/Mtrace", "engaged", "Z");
                Label l0 = new Label();
                mv.visitJumpInsn(IFEQ, l0);
                directInsert(methodPrefix);
                mv.visitLabel(l0);
                break;
            case DIRECT_TIMESTAMP: {
                int timestamp = newLocal(Type.LONG_TYPE);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
                mv.visitVarInsn(LSTORE, timestamp);
                break;
            }
            case DIRECT_TIMESTAMP_NATIVE: {
                int timestamp = newLocal(Type.LONG_TYPE);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace",
                                   "rdtsc", "()J", false);
                mv.visitVarInsn(LSTORE, timestamp);
                break;
            }
            case TIMESTAMP_SAMPLE:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace",
                                   methodPrefix + "_timestamp2", "(I)V", false);
                break;
            case INVOKE:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace",
                                   methodPrefix, "(I)V", false);
                break;
            case INVOKE_NOARGS:
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace",
                                   methodPrefix + "_noargs", "()V", false);
                break;
            case INVOKE_EMPTY:
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace",
                                   methodPrefix + "_empty", "()V", false);
                break;
            case INVOKE_NOTEST:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace",
                                   methodPrefix + "_notest", "(I)V", false);
                break;
            case INVOKE_TRACE_NATIVE:
                // this label will mark where the pre-instrumented code is via mark
                Label noTrace = new Label();
                getStatic(MTRACE_CLASS, MTRACE_ENGAGED, Type.BOOLEAN_TYPE);
                ifZCmp(EQ, noTrace);
                push(mnum);
                invokeStatic(MTRACE_CLASS, Method.getMethod("void " + methodPrefix + "_native (int)"));
//                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace",
//                                   methodPrefix + "_native", "(I)V", false);
                mark(noTrace);
                break;
            case INVOKE_EMPTY_NATIVE:
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace",
                                   methodPrefix + "_empty_native", "()V", false);
                break;
            case INVOKE_BINMEM:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace",
                                   methodPrefix + "_binmem", "(I)V", false);
                break;
            case INVOKE_BINTRACE:
                push(mnum);
                mv.visitMethodInsn(INVOKESTATIC, "com/craiig/jvm_method_trace/Mtrace",
                                   methodPrefix + "_bintrace", "(I)V", false);
                break;
            case NOP:
                mv.visitInsn(NOP);
                break;
        }
    }

    private void directInsert(String methodPrefix) {
        String fieldName = methodPrefix.equals(METHOD_ENTRY_PREFIX) ? "entry_cnt" :
                methodPrefix.equals(METHOD_EXIT_PREFIX) ? "exit_cnt" : null;
        if (fieldName == null) {
            throw new IllegalArgumentException("invalid methodPrefix: " + methodPrefix);
        }

        mv.visitFieldInsn(GETSTATIC, "com/craiig/jvm_method_trace/Mtrace", "methods", "[Lcom/craiig/jvm_method_trace/MethodInfo;");
        push(mnum);
        mv.visitInsn(AALOAD);
        mv.visitInsn(DUP);
        mv.visitFieldInsn(GETFIELD, "com/craiig/jvm_method_trace/MethodInfo", fieldName, "J");
        mv.visitInsn(LCONST_1);
        mv.visitInsn(LADD);
        mv.visitFieldInsn(PUTFIELD, "com/craiig/jvm_method_trace/MethodInfo", fieldName, "J");
    }
}