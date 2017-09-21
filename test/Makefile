all: LoopTest.class

ROOT_DIR:=$(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
# TOOL?=$(shell readlink -f $(ROOT_DIR)/../build/jvm-method-trace.so)
TOOL?=$(shell pwd)/../build/jvm-method-trace.so

JAVA_AGENT="../java/target/jvm-method-trace-1.0-jar-with-dependencies.jar"
JAVA_OPTS := -cp .:$(JAVA_AGENT)
JAVA_AGENT_OPTIONS := "measuringTime=1,writingTraceLog=1"

LoopTest.class: LoopTest.java
	javac $(JAVA_OPTS) $<

run: baseline mtrace

jvmti: LoopTest.class
	@JAVA_TOOL_OPTIONS="-agentpath:$(TOOL)" java $(JAVA_OPTS) LoopTest jvmti

baseline: LoopTest.class
	@java $(JAVA_OPTS) LoopTest baseline

# mtrace: TOOL:=$(shell readlink -f $(ROOT_DIR)/../build/mtrace.so)
mtrace: TOOL:=$(shell pwd)/../build/mtrace.so
mtrace:
	@JAVA_TOOL_OPTIONS="-agentpath:$(TOOL)=$(JAVA_AGENT_OPTIONS)" java -Xbootclasspath/a:$(JAVA_AGENT) LoopTest mtrace

asm: LoopTest.class asm_direct asm_direct_test asm_invoke asm_invoke_noargs asm_invoke_empty asm_nop

asm_direct:
	@java -javaagent:$(JAVA_AGENT)=mode=direct -Xbootclasspath/p:$(JAVA_AGENT) LoopTest $@

asm_direct_test:
	@java -javaagent:$(JAVA_AGENT)=mode=direct-test -Xbootclasspath/a:$(JAVA_AGENT) LoopTest $@

asm_invoke:
	@java -javaagent:$(JAVA_AGENT)=mode=invoke -Xbootclasspath/a:$(JAVA_AGENT) LoopTest $@

asm_invoke_noargs:
	@java -javaagent:$(JAVA_AGENT)=mode=invoke-noargs -Xbootclasspath/a:$(JAVA_AGENT) LoopTest $@

asm_invoke_empty:
	@java -javaagent:$(JAVA_AGENT)=mode=invoke-empty -Xbootclasspath/a:$(JAVA_AGENT) LoopTest $@

asm_invoke_notest:
	@java -javaagent:$(JAVA_AGENT)=mode=invoke-notest -Xbootclasspath/a:$(JAVA_AGENT) LoopTest $@

asm_nop:
	@java -javaagent:$(JAVA_AGENT)=mode=nop -Xbootclasspath/a:$(JAVA_AGENT) LoopTest $@

clean:
	-rm LoopTest.class

