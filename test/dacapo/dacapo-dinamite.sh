#!/bin/bash
set -e

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <OUTPUT_DIR>"
    exit 1
fi

source dacapo-default.cfg

MODES="direct-timestamp direct-timestamp-native invoke-empty-native invoke-trace-native-memory-write invoke-trace-native"
N_INST="0 5 10 15 20"
RESULTS=$1

if [[ ! -d $RESULTS ]]; then
    mkdir -p $RESULTS  
fi

for bench in $DACAPO_BENCHS; do
    for t in $THREADS; do
        for mode in $MODES; do
            for N in $N_INST; do
                echo -n "[$bench] t=$t mode=$mode N=$N                  "

                # need to use memory-write library if we want to only write in memory
                # also the java agent only knows invoke-trace-native
                agentpath_lib=$MTRACE_LIB
                run_mode=$mode
                if [[ $mode == "invoke-trace-native-memory-write" ]]; then
                    agentpath_lib=$MTRACE_MEMORY_WRITE_LIB
                    run_mode="invoke-trace-native"
                fi

                java $JAVA_OPTS -javaagent:$JAVA_AGENT=mode=$run_mode,N=$N,outfile=$RESULTS/dacapo.$bench.$mode.N$N.t$t.tsv,filter=line-number,engage \
                    -agentpath:$agentpath_lib -jar $DACAPO -t $t -n $N_ITER $bench \
                    2>&1 | tee $RESULTS/dacapo.$bench.$mode.N$N.t$t.out | \
                    grep PASSED | sed 's/.\+PASSED in \([[:digit:]]\+ msec\) =====/\1/g'
                rm -f trace.bin.*
            done
        done
    done
done
