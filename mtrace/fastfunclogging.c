#include <errno.h>
#include <limits.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#ifdef _WIN32
#include <windows.h>
#else
#include <sys/syscall.h>
#endif

#include "dinamite_time.h"
#include "fastfunclogging.h"
#include "perthreadlogging.h"

#define likely(x)       __builtin_expect(!!(x), 1)
#define unlikely(x)     __builtin_expect(!!(x), 0)


static inline void
fillFnLog(ff_fnlog *fnl, int functionId) {
    fnl->function_id = functionId;
    fnl->fn_timestamp = dinamite_time_nanoseconds();
}

void fillAccessLog(ff_accesslog *acl, char value_type, value_store value) {
    acl->value_type = value_type;
    acl->value = value;
}


void logFnBegin(int functionId) {
    pid_t tid;
    fastfuncentry *le = __dinamite_getBufPtrMaybeFlush(&tid);
    if (le == NULL)
        return;

    le->entry_type = LOG_FN_ENTER;
    fillFnLog(&(le->entry.fn), functionId);
}

void logFnEnd(int functionId) {
    pid_t tid;
    fastfuncentry *le = __dinamite_getBufPtrMaybeFlush(&tid);
    if (le == NULL)
        return;

    le->entry_type = LOG_FN_EXIT;
    fillFnLog(&(le->entry.fn), functionId);
    //printf("Exit %d:%d\n", tid, le->entry_type);
}

void logAlloc(void *addr, uint64_t size, uint64_t num, int type, int file,
              int line, int col) {
	return;
}

void logAccessPtr(void *ptr, void *value, int type, int file, int line, int col,
          int typeId, int varId) {
    pid_t tid;
    fastfuncentry *le = __dinamite_getBufPtrMaybeFlush(&tid);
    if (le == NULL)
        return;

    le->entry_type = LOG_FF_ACCESS;
    value_store vs;
    vs.ptr = value;
    fillAccessLog(&(le->entry.access), PTR, vs);
}

/* This function logs an access when we are sure that what we are acccessing is
 * a null-terminated string. A typical use-case is when we print a string passed
 * as an argument to the tracepoint function. Using this function when we are
 * not sure whether the address points to a null-terminated string is unsafe,
 * because we may crash when we try to print it later.
 *
 * Another crucial assumption we are making is that the strings being accessed
 * are static. Here is the reason: To avoid the runtime overhead associated
 * with string printing, this function simply stores the pointer when called,
 * and at the very end of the program goes over the pointers and prints them.
 * Here we are assuming that the pointers accessed earlier in the program are
 * still valid at the end of the program and that they are still pointing to the
 * same values as they did when they were actually accessed. This will be true
 * for static strings, but may not be true for dynamic strings. So this function
 * is not safe to use with dynamically allocated strings.
 */
void logAccessStaticString(void *ptr, void *value, int type, int file, int line,
               int col, int typeId, int varId) {

    pid_t tid;
    fastfuncentry *le = __dinamite_getBufPtrMaybeFlush(&tid);
    if (le == NULL)
        return;

    le->entry_type = LOG_FF_ACCESS;
    value_store vs;
    vs.ptr = value;
    fillAccessLog(&(le->entry.access), PTR, vs);
}

void logAccessI8(void *ptr, uint8_t value, int type, int file, int line,
         int col, int typeId, int varId) {
    pid_t tid;
    fastfuncentry *le = __dinamite_getBufPtrMaybeFlush(&tid);
    if (le == NULL)
        return;

    le->entry_type = LOG_FF_ACCESS;
    value_store vs;
    vs.i8 = value;
    fillAccessLog(&(le->entry.access), I8, vs);
}

void logAccessI16(void *ptr, uint16_t value, int type, int file, int line,
          int col, int typeId, int varId) {
    pid_t tid;
    fastfuncentry *le = __dinamite_getBufPtrMaybeFlush(&tid);
    if (le == NULL)
        return;

    le->entry_type = LOG_FF_ACCESS;
    value_store vs;
    vs.i16 = value;
    fillAccessLog(&(le->entry.access), I16, vs);
}

void logAccessI32(void *ptr, uint32_t value, int type, int file, int line,
          int col, int typeId, int varId) {
    pid_t tid;
    fastfuncentry *le = __dinamite_getBufPtrMaybeFlush(&tid);
    if (le == NULL)
        return;

    le->entry_type = LOG_FF_ACCESS;
    value_store vs;
    vs.i32 = value;
    fillAccessLog(&(le->entry.access), I32, vs);
}

void logAccessI64(void *ptr, uint64_t value, int type, int file, int line,
          int col, int typeId, int varId) {
    pid_t tid;
    fastfuncentry *le = __dinamite_getBufPtrMaybeFlush(&tid);
    if (le == NULL)
        return;

    le->entry_type = LOG_FF_ACCESS;
    value_store vs;
    vs.i64 = value;
    fillAccessLog(&(le->entry.access), I64, vs);
}

/* =============================
   These don't exist: */

void logAccessF8(void *ptr, uint8_t value, int type, int file, int line,
         int col, int typeId, int varId) {
}

void logAccessF16(void *ptr, uint16_t value, int type, int file, int line,
          int col, int typeId, int varId) {

}

/* ============================= */

void logAccessF32(void *ptr, float value, int type, int file, int line, int col,
          int typeId, int varId) {
    pid_t tid;
    fastfuncentry *le = __dinamite_getBufPtrMaybeFlush(&tid);
    if (le == NULL)
        return;

    le->entry_type = LOG_FF_ACCESS;
    value_store vs;
    vs.f32 = value;
    fillAccessLog(&(le->entry.access), F32, vs);
}

void logAccessF64(void *ptr, double value, int type, int file, int line,
          int col, int typeId, int varId) {
    pid_t tid;
    fastfuncentry *le = __dinamite_getBufPtrMaybeFlush(&tid);
    if (le == NULL)
        return;

    le->entry_type = LOG_FF_ACCESS;
    value_store vs;
    vs.f64 = value;
    fillAccessLog(&(le->entry.access), F64, vs);
}

