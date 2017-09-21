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

#include "perthreadlogging.h"

#define MAX_THREADS 128

typedef struct {
    int idx;
    int padding[15]; /* Pad to 64 bytes to avoid false sharing */
} padded_idx_t;

static FILE *out[MAX_THREADS];
static buffentry *entries[MAX_THREADS];
static padded_idx_t current[MAX_THREADS];

#define BUFFER_SIZE sizeof(buffentry) * 4096

static pthread_once_t dinamite_once_control = PTHREAD_ONCE_INIT;
pthread_key_t tls_key;
static pthread_mutex_t id_mtx = PTHREAD_MUTEX_INITIALIZER;
static int next_id = 1;

// windows doesn't have strsep so we'll write our own
#ifdef _WIN32
char* strsep(char** stringp, const char* delim) {
    char* start = *stringp;
    char* p;

    p = (start != NULL) ? strpbrk(start, delim) : NULL;

    if (p == NULL) {
        *stringp = NULL;
    }
    else {
        *p = '\0';
        *stringp = p + 1;
    }

    return start;
}
#endif

#define DINAMITE_VERBOSE

static bool __dinamite_exclude_tid(pid_t tid) {

    char *excluded_tids, *token;

    excluded_tids = getenv("DINAMITE_EXCLUDE_TID");
    if(excluded_tids == NULL)
        return false;

    while ((token = strsep(&excluded_tids, ",")) != NULL) {
        pid_t e_tid = (pid_t)atoi(token);
        if(e_tid == tid) {
#ifdef DINAMITE_VERBOSE
            printf("Excluding tid %d from trace\n", tid);
#endif
            return true;
        }
    }
    return false;
}

static void __dinamite_create_key(void) {

    int ret = pthread_key_create(&tls_key, NULL);

    if(ret) {
        fprintf(stderr,
            "pthread_key_create: could not create "
            "a local-storage key: %s\n", strerror(ret));
        exit(-1);
    }
}

inline int __dinamite_get_next_id(void) {
    int ret;

    pthread_mutex_lock(&id_mtx);
    ret = next_id++;
    pthread_mutex_unlock(&id_mtx);
    return ret;
}

inline pid_t __dinamite_gettid(void) {

    int tid;

    int ret = pthread_once(&dinamite_once_control, __dinamite_create_key);

    if(ret) {
        fprintf(stderr,
            "pthread_once: could not create "
            "a local-storage key: %s\n", strerror(ret));
        exit(-1);
    }

    if( (tid = (pid_t)(long)pthread_getspecific(tls_key)) == 0) {
        tid = __dinamite_get_next_id();
        if(__dinamite_exclude_tid(tid)) {
            /*
             * The user wanted to discard log records for this
             * thread ID. Set the tid to an invalid value to
             * force log records discarded.
             */
            tid = MAX_THREADS;
        }
        pthread_setspecific(tls_key, (void *) (long)tid);
    }

    return tid;
}

static inline bool
__dinamite_ok_tid(pid_t tid, bool quiet) {

    if(tid > MAX_THREADS -1)
        return false;
    return true;
}

static inline bool
__dinamite_init_buffer(pid_t tid) {

    entries[tid] = (buffentry *)malloc(sizeof(buffentry) * BUFFER_SIZE);
    if(entries[tid] == NULL) {
        fprintf(stderr, "Warning: could not allocate entries buffer "
            "for thread %d: %s\n", tid, strerror(errno));
        return false;
    }
    return true;
}

inline bool
__dinamite_ok_buffer(pid_t tid) {

    if(entries[tid] == NULL)
        return __dinamite_init_buffer(tid);
    else
        return true;
}

static inline bool
__dinamite_opened_outfile(pid_t tid) {

    char fname[PATH_MAX];
    char *prefix = NULL;

    if(!__dinamite_ok_tid(tid, true))
        return false;

    prefix = getenv("DINAMITE_TRACE_PREFIX");

    if(prefix != NULL)
        snprintf((char*)fname, PATH_MAX-1, "%s/trace.bin.%d", prefix,
             tid);
    else
        snprintf((char*)fname, PATH_MAX-1, "trace.bin.%d", tid);
    out[tid] = fopen(fname, "wb");

    if(out[tid] == NULL) {
        fprintf(stderr,
            "Warning: could not open file %s\n", strerror(errno));
        return false;
    }
    fprintf(stdout,
        "Opened file %s\n", fname);
    return true;
}

static inline int
__dinamite_ok_outfile(pid_t tid) {

    if(!__dinamite_ok_tid(tid, true))
        return false;

    if(out[tid] == NULL)
        return __dinamite_opened_outfile(tid);
    else return true;
}

buffentry *
__dinamite_getBufPtrMaybeFlush(pid_t *rettid) {

    pid_t tid = __dinamite_gettid();
    if(!__dinamite_ok_tid(tid, true) || !__dinamite_ok_buffer(tid))
        return NULL;
    *rettid = tid;

    if (current[tid].idx >= BUFFER_SIZE) {
        if (__dinamite_ok_outfile(tid)) {
#ifndef NO_WRITE
            fwrite(entries[tid], sizeof(buffentry), BUFFER_SIZE,
                   out[tid]);
#endif
#if 0 /* For overhead testing only */
	    if (ftell(out[tid]) > 1024 * 1024 * 1024)
		    rewind(out[tid]);
#endif
        }
        current[tid].idx = 0;
    }
    return &(entries[tid][current[tid].idx++]);
}



/* Open a per-thread log file. */

void logInit(int functionId) {

    int ret = pthread_once(&dinamite_once_control, __dinamite_create_key);

    if(ret) {
        fprintf(stderr,
            "pthread_once: could not create "
            "a local-storage key: %s\n", strerror(ret));
        exit(-1);
    }
}

void logExit(int functionId) {
    int tid;
    for(tid = 0; tid < MAX_THREADS; tid++) {
        if (entries[tid] && current[tid].idx > 0) {
            if(__dinamite_ok_outfile(tid)) {
#ifndef NO_WRITE
                fwrite(entries[tid], sizeof(buffentry),
                       current[tid].idx,
                       out[tid]);
                fflush(out[tid]);
#endif
                fclose(out[tid]);
                out[tid] = NULL;
            }
        }
    }
}
