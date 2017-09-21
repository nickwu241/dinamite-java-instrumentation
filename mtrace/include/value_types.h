#ifndef VALUE_TYPE_H
#define VALUE_TYPE_H

#define TID_TYPE char

enum value_type {
    I8, I16, I32, I64,
    F32, F64, PTR,
    MAX_VALUE_TYPE
};

typedef union {
    void *ptr;
    int8_t i8;
    int16_t i16;
    int32_t i32;
    int64_t i64;
    float f32;
    double f64;
} value_store;

#endif
