#!/bin/bash
set -e

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <OUTPUT_DIR>"
    exit 1
fi

source dacapo-default.cfg

# AGENT_PATH="-agentpath:/c/YourKit2017.02-b63/bin/win64/yjpagent.dll"
AGENT_PATH="-agentpath:/mnt/scratch/nick/yjp-2017.02/bin/linux-x86-64/libyjpagent.so"
TRACE_SETTINGS="yjp/tracing-default.txt yjp/tracing-adaptivefalse.txt"
RESULTS=$1

for bench in $DACAPO_BENCHS; do
    for t in $THREADS; do
        for trace_setting in $TRACE_SETTINGS; do
            if [[ $trace_setting == "yjp/tracing-default.txt" ]]; then
                prefix="default"
            fi
            if [[ $trace_setting == "yjp/tracing-adaptivefalse.txt" ]]; then
                prefix="adaptivefalse"
            fi
            results_dir=$prefix-$RESULTS
            if [[ ! -d $results_dir ]]; then
                mkdir -p $results_dir
            fi
            AGENT_OPTS="tracing,dir=$results_dir/snapshots,logdir=$results_dir/log,onexit=snapshot"
            java $JAVA_OPTS $AGENT_PATH=$AGENT_OPTS,sessionname=dacapo-$bench,tracing_settings_path=$trace_setting \
                -jar $DACAPO $bench -t $THREADS -n $N_ITER 2>&1 | tee $results_dir/dacapo.$bench.$prefix.t$t.out
        done
    done
done
