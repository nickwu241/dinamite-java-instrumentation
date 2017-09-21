#!/bin/bash
set -e

function usage()
{
    echo "usage: $0 <LOOP_UNTIL_NUMBER> [--baseline] [--dinamite] [--yjp] [--start=<START_NUMBER>] [--merge-all]"
}

if [[ $# -lt 1 ]]; then
    usage
    exit 1
fi

END_NUM=$1
START_NUM=0
BASELINE=false
DINAMITE=false
YJP=false
MERGE_ALL=false
shift

while [ "$1" != "" ]; do
    PARAM=`echo $1 | awk -F= '{print $1}'`
    VALUE=`echo $1 | awk -F= '{print $2}'`
    case $PARAM in
        -h | --help)
            usage
	    exit
	    ;;
        --baseline)
            BASELINE=true
	    ;;
        --dinamite)
            DINAMITE=true
	    ;;
        --yjp)
            YJP=true
	    ;;
        --start)
            START_NUM=$VALUE
	    ;;
        --merge-all)
            MERGE_ALL=true
	    ;;
        *)
            echo "ERROR: unknown parameter \"$PARAM\""
	    ;;
    esac
    shift
done

if $BASELINE; then
    for ((i=$START_NUM;i<=$END_NUM;i++)); do
        ./dacapo-baseline.sh baseline-$i
    done
fi

if $DINAMITE; then
    for ((i=$START_NUM;i<=$END_NUM;i++)); do
        ./dacapo-dinamite.sh dinamite-line-number-$i
    done
fi

if $YJP; then
    for ((i=$START_NUM;i<=$END_NUM;i++)); do
        echo $i
        ./dacapo-yourkit.sh yjp-benchmark-$i
    done
fi

if $MERGE_ALL; then
    echo "Merging all tests..."
    echo "[NOT IMPLEMENTED YET]"
fi
