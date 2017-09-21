#!/usr/bin/env python3

import sys, os, os.path, re
from collections import OrderedDict
import seaborn as sns
import argparse
import pandas as pd
from matplotlib import pyplot as plt
from matplotlib.ticker import *

sns.set_style("ticks")

parser = argparse.ArgumentParser()
parser.add_argument('logdir', nargs='+')
parser.add_argument('--hide', dest='show', default=True, action='store_false')
args = parser.parse_args()

def parse_s(s):
    benchmark = s[0]
    mode = s[1]
    N = 0
    t = 1
    exclude = False

    for k in s:
        if k[0] == "N":
            N = int(k[1:])
        elif k[0] == "t" and k[1].isdigit():
            t = int(k[1:])
        elif k == "exclude":
            exclude = True

    return (benchmark, mode, exclude, N, t)

def parse_common(line):
    m = re.match("entry_cnt: (\d+) exit_cnt: (\d+) c_instrumented: (\d+) c_not_instrumented: (\d+) m_instrumented: (\d+) m_not_instrumented: (\d+)", line)

    if m:
        return tuple(map(lambda x: int(x), m.groups()))
    return None

def parse_dacapo(logfile, s):
    benchmark, mode, exclude, N, t = parse_s(s)

    runtime = None
    common = None

    with open(logfile, "r") as f:
        for line in reversed(f.readlines()):
            if runtime is not None and common is not None:
                break

            if runtime is None:
                m = re.search("PASSED in (\d+) msec", line)
                if m:
                    runtime = int(m.group(1))
                    continue
            if common is None:
                common = parse_common(line)

    if common is None:
        common = (0,0,0,0,0,0)

    values = OrderedDict(zip(("runtime", "entry_cnt", "exit_cnt", "c_instrumented",
                       "c_not_instrumented", "m_instrumented", "m_not_instrumented"),
                       (runtime,)+common))
    return (benchmark, mode, exclude, N, t, values)

def parse_spark_sql(logfile, s):
    benchmark, mode, exclude, N, t = parse_s(s)

    runtime = None
    common = None

    with open(logfile, "r") as f:
        for line in reversed(f.readlines()):
            if runtime is not None and common is not None:
                break

            if runtime is None:
                m = re.search("\[q3-v1\.4\] (\d+\.?\d*) ms", line)
                if m:
                    runtime = float(m.group(1))
                    continue
            if common is None:
                common = parse_common(line)

    if common is None:
        common = (0,0,0,0,0,0)

    values = OrderedDict(zip(("runtime", "entry_cnt", "exit_cnt", "c_instrumented",
                       "c_not_instrumented", "m_instrumented", "m_not_instrumented"),
                       (runtime,)+common))
    return (benchmark, mode, exclude, N, t, values)

def parse_spark_pagerank(logfile, s):
    benchmark, mode, exclude, N, t = parse_s(s)

    runtime = None
    common = None

    with open(logfile, "r") as f:
        for line in reversed(f.readlines()):
            if runtime is not None and common is not None:
                break

            if runtime is None:
                m = re.match("(\d+\.?\d*\n)", line)
                if m:
                    runtime = float(m.group(1))
                    continue
            if common is None:
                common = parse_common(line)

    if common is None:
        common = (0,0,0,0,0,0)

    values = OrderedDict(zip(("runtime", "entry_cnt", "exit_cnt", "c_instrumented",
                       "c_not_instrumented", "m_instrumented", "m_not_instrumented"),
                       (runtime,)+common))
    return (benchmark, mode, exclude, N, t, values)

results = {}
baseline = {}

for d in args.logdir:
    for logfile in os.listdir(d):
        s = logfile.split('.')
        if s[-1] != "out":
            continue

        s = s[:-1] # remove .out

        benchmark = s[0]

        if benchmark == "dacapo":
            benchmark, mode, exclude, N, t, values = parse_dacapo(os.path.join(d, logfile), s[1:])
        elif benchmark == "spark-pagerank":
            benchmark, mode, exclude, N, t, values = parse_spark_pagerank(os.path.join(d, logfile), s)
        elif benchmark == "spark-sql":
            benchmark, mode, exclude, N, t, values = parse_spark_sql(os.path.join(d, logfile), s)
        else:
            raise NameError("unknown benchmark %s" % benchmark)

        if mode == "baseline":
            baseline[(benchmark, t)] = values
        else:
            results[(benchmark, mode, exclude, N, t)] = values

# get overhead
for k,v in results.items():
    b, m, e, N, t = k

    if v['runtime'] is None:
        continue

    results[k]["overhead"] = v["runtime"] / baseline[(b, t)]["runtime"]

    nr_methods = v["m_instrumented"] + v["m_not_instrumented"]
    results[k]["% method instrumented"] = v["m_instrumented"] / nr_methods * 100 if nr_methods > 0 else 100

df = pd.DataFrame.from_records([(*k, *v.values()) for k,v in results.items()], columns=["benchmark", "mode", "exclude", "N", "threads", "runtime", "entry count", "exit count", "classes instrumented", "classes not instrumented", "methods instrumented", "methods not instrumented", "overhead", "% methods instrumented"])


for b, g in df.sort_values(by="mode").groupby(["benchmark", "exclude", "threads"]):
    bench, exclude, threads = b
    f = plt.figure(figsize=(19.2, 10.8))

    ax = plt.subplot2grid((1, 4), (0, 0), colspan=3)
    ax = sns.barplot(x='mode', hue='N', y='overhead', data=g, ci=None, palette="Set1")
    ax.grid(b=True, axis='y')
    ax.get_yaxis().set_minor_locator(AutoMinorLocator())
    ax.set_title("%s (%d threads%s)" % (bench, threads, ", exclude" if exclude else ""))
    ax.set_ylabel("Overhead")

    ax = plt.subplot2grid((1, 4), (0, 3))
    ax = sns.barplot(x='N', y='% methods instrumented', data=g, ci=None, palette="Set1")
    ax.grid(b=True, axis='y')
    ax.get_yaxis().set_minor_locator(AutoMinorLocator())
    ax.set_title("%s (%d threads%s)" % (bench, threads, ", exclude" if exclude else ""))
    ax.set_ylabel("% of methods instrumend")
    ax.set_ylim(0, 100)

    f.tight_layout()

    f.savefig("%s-overhead-t%d%s.svgz" % (bench, threads, "-exclude" if exclude else ""),
        bbox_inches='tight', dpi=100) 

    if not args.show:
        plt.close(f)

if args.show:
    plt.show()
