#!/bin/bash
set -e

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <OUTPUT_DIR>"
    exit 1
fi

source dacapo-default.cfg

RESULTS=$1

if [[ ! -d $RESULTS ]]; then
    mkdir -p $RESULTS  
fi

for bench in $DACAPO_BENCHS; do
    for t in $THREADS; do
        echo -n "[$bench] t=$t mode=baseline "
        java $JAVA_OPTS -jar $DACAPO -t 1 $bench -n $N_ITER \
            2>&1 | tee $RESULTS/dacapo.$bench.baseline.t$t.out | \
            grep PASSED | sed 's/.\+PASSED in \([[:digit:]]\+ msec\) =====/\1/g'
    done
done
