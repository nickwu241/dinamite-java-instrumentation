#ifndef FASTFUNCLOGGING_H
#define FASTFUNCLOGGING_H

#include "value_types.h"

/******************************************************************
 *
 * Format for fast function logging
 *
 */

enum ff_entry_types {
    LOG_FN_ENTER, LOG_FN_EXIT, LOG_FF_ACCESS
};

typedef struct _ff_accesslog {
	value_store value; // 8
	char value_type; // 1
} ff_accesslog;

typedef struct _ff_fnlog {
       	uint64_t fn_timestamp;  // 8
	unsigned short function_id; // 2
} ff_fnlog;

typedef struct _fastfuncentry {
	union {
		ff_fnlog fn;
		ff_accesslog access;
	} entry;
	char entry_type; // 1
} fastfuncentry;

#endif
