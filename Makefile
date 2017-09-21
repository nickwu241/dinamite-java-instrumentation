agentjar := agentjar
mtrace := mtrace

TARGETS := $(agentjar) $(mtrace) 

.PHONY: $(agentjar) $(mtrace) clean
all: $(TARGETS)

$(agentjar):
	make -C ./java all

$(mtrace):
	make -C ./mtrace mtrace.so
	make -C ./mtrace mtrace-memory-write.so

clean:
	make -C ./java clean
	make -C ./mtrace clean
