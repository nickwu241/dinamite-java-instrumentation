CC := gcc
CFLAGS := -shared -fPIC -g3 -O3 -Wall -fno-omit-frame-pointer -Wno-unused-function -DFASTFUNCINSTRUMENTATION

INC_DIRS := include $(JAVA_HOME)/include
ifeq ($(OS),Windows_NT)
	INC_DIRS := $(INC_DIRS) $(JAVA_HOME)/include/win32
else
	UNAME_S := $(shell uname -s)
	ifeq ($(UNAME_S),Linux)
		INC_DIRS := $(INC_DIRS) $(JAVA_HOME)/include/linux
	endif
	ifeq ($(current_os),Darwin)
		INC_DIRS := $(INC_DIRS) $(JAVA_HOME)/include/darwin
	endif
endif

INCLUDES := $(INC_DIRS:%=-I%)

SRCS := mtrace.c agent_util.c dinamite_time.c perthreadlogging.c fastfunclogging.c
OBJS := $(SRCS:.c=.o)

mtrace := mtrace.so
mtrace-memory-write := mtrace-memory-write.so
TARGET := $(mtrace) $(mtrace-memory-write)

.PHONY: all clean $(mtrace) $(mtrace-memory-write)
default: 
	@echo ERROR: specify your target: \($(TARGET)\)

$(mtrace): $(OBJS)
	$(CC) $(CFLAGS) $(INCLUDES) -o $@ $^
	$(RM) $(OBJS)

$(mtrace-memory-write): INCLUDES += -DNO_WRITE
$(mtrace-memory-write): $(OBJS)
	$(CC) $(CFLAGS) $(INCLUDES) -o $@ $^
	$(RM) $(OBJS)

.c.o:
	$(CC) $(CFLAGS) $(INCLUDES) -c $< -o $@

clean:
	$(RM) *.o *~ $(TARGET)
