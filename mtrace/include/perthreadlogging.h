#ifndef _PERTHREADLOGGING_H
#define _PERTHREADLOGGING_H

#include <sys/types.h>

#ifdef BINARYINSTRUMENTATION
#include "binaryinstrumentation.h"
typedef logentry buffentry;
#else
#ifdef FASTFUNCINSTRUMENTATION
#include "fastfunclogging.h"
typedef fastfuncentry buffentry;
#endif
#endif

buffentry * __dinamite_getBufPtrMaybeFlush(pid_t *rettid);

#endif
