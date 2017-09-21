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
NR_ITER=30
N_INST="0 10 20 30 50 65 80 100"
JAVA_OPTS="-XX:+UseG1GC"
RESULTS="$1"

if [[ ! -d $RESULTS ]]; then
    mkdir -p $RESULTS
fi

echo "[sparl-sql] baseline"
$SPARK_SUBMIT --master local[1] $SPARK_CONFIG \
    --conf spark.driver.extraJavaOptions="$JAVA_OPTS" \
    --class TPCDSBenchmark $SPARK_SQL_JAR \
    -sf=1 -gendata=false -iter=$NR_ITER -cache=false -filter=q3-v1.4 -temp=true \
    2>&1 > spark-sql.baseline.out | grep -v 'WARN\|SLF4J'

for mode in $MODES; do
    for N in $N_INST; do
        echo "[spark-sql] mode=$mode N=$N"
        $SPARK_SUBMIT --master local[1] $SPARK_CONFIG \
            --conf spark.driver.extraJavaOptions="$JAVA_OPTS \
            -javaagent:$JAVA_AGENT=mode=$mode,N=$N,outfile=$RESULTS/spark-sql.$mode.N$N.t1.tsv \
            -agentpath:$MTRACE_LIB" \
            --class TPCDSBenchmark $SPARK_SQL_JAR \
            -sf=1 -gendata=false -iter=$NR_ITER -cache=false -filter=q3-v1.4 -temp=true \
            2>&1 | tee $RESULTS/spark-sql.$mode.N$N.t1.out | grep -v 'WARN\|SLF4J'
    done

#    for N in $N_INST; do
#        echo "[spark-sql] mode=$mode exclude=java.*;sun.* N=$N"
#        $SPARK_SUBMIT --master local[1] $SPARK_CONFIG \
#            --conf spark.driver.extraJavaOptions="$JAVA_OPTS \
#            -javaagent:$JAVA_AGENT=mode=$mode,exclude=java.*;sun.*,N=$N,outfile=$RESULTS/spark-sql.$mode.exclude.N$N.t1.tsv \
#            -agentpath:$MTRACE_LIB" \
#            --class TPCDSBenchmark $SPARK_SQL_JAR \
#            -sf=1 -gendata=false -iter=$NR_ITER -cache=false -filter=q3-v1.4 -temp=true \
#            2>&1 | tee $RESULTS/spark-sql.$mode.N$N.t1.exclude.out | grep -v 'WARN\|SLF4J'
#    done
done
