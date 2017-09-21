#!/bin/sh

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <output dir>"
    exit 1
fi

JAVA_AGENT="../java/target/jvm-method-trace-1.0-jar-with-dependencies.jar"
MTRACE_LIB="../mtrace/mtrace.so"
SPARK="/home/mlorrillere/.tmp/spark-1.6.3-SNAPSHOT-bin-craig-extralogs"
SPARK_SUBMIT="$SPARK/bin/spark-submit"
SPARK_CONFIG="--driver-memory=10g"
SPARK_SQL_JAR="/home/mlorrillere/git/spark-perf-tpcds/target/scala-2.10/spark-perf-tpcds-assembly-0.1-SNAPSHOT.jar"
MODES="direct direct-timestamp direct-test timestamp-sample invoke invoke-empty invoke-notest nop"
NR_ITER=3
N_INST="0 10 20 30 50 65 80 100"
JAVA_OPTS="-XX:+UseG1GC"
RESULTS="$1"

if [[ ! -d $RESULTS ]]; then
    mkdir -p $RESULTS
fi

echo "[sparl-pagerank] baseline"
/usr/bin/time -f "%e" $SPARK_SUBMIT --master local[8] $SPARK_CONFIG \
    --conf spark.driver.extraJavaOptions="$JAVA_OPTS" \
    --class org.apache.spark.examples.graphx.Analytics $SPARK/lib/spark-examples-1.6.3-SNAPSHOT-hadoop2.7.1.jar \
    pagerank $SPARK/soc-LiveJournal1.txt --numEPart=16 --numIter=$NR_ITER \
    2>&1 > spark-pagerank.baseline.out | grep -v WARN

for mode in $MODES; do
    for N in $N_INST; do
        echo "[spark-pagerank] mode=$mode N=$N"
        /usr/bin/time -f "%e" $SPARK_SUBMIT --master local[8] $SPARK_CONFIG \
            --conf spark.driver.extraJavaOptions="$JAVA_OPTS \
            -javaagent:$JAVA_AGENT=mode=$mode,engage,N=$N,outfile=$RESULTS/spark-pagerank.$mode.N$N.t8.tsv \
            -agentpath:$MTRACE_LIB" \
            --class org.apache.spark.examples.graphx.Analytics $SPARK/lib/spark-examples-1.6.3-SNAPSHOT-hadoop2.7.1.jar \
            pagerank $SPARK/soc-LiveJournal1.txt --numEPart=16 --numIter=$NR_ITER \
            2>&1 | tee $RESULTS/spark-pagerank.$mode.N$N.t8.out | grep -v WARN
    done

#    for N in $N_INST; do
#        echo "[spark-pagerank] mode=$mode exclude=java.*;sun.* N=$N"
#        /usr/bin/time -f "%e" $SPARK_SUBMIT --master local[8] $SPARK_CONFIG \
#            --conf spark.driver.extraJavaOptions="$JAVA_OPTS \
#            -javaagent:$JAVA_AGENT=mode=$mode,engage,exclude=java.*;sun.*,N=$N,outfile=$RESULTS/spark-pagerank.$mode.exclude.N$N.t8.tsv \
#            -agentpath:$MTRACE_LIB" \
#            --class org.apache.spark.examples.graphx.Analytics $SPARK/lib/spark-examples-1.6.3-SNAPSHOT-hadoop2.7.1.jar \
#            pagerank $SPARK/soc-LiveJournal1.txt --numEPart=16 --numIter=$NR_ITER \
#            2>&1 | tee $RESULTS/spark-pagerank.$mode.N$N.t8.exclude.out | grep -v WARN
#    done
done
