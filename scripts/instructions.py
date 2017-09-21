#!/usr/bin/env python3

import sys, os, os.path, logging
import argparse
import pandas as pd
from matplotlib import pyplot as plt
from matplotlib.ticker import *

parser = argparse.ArgumentParser()
parser.add_argument('logfile', type=argparse.FileType("r"))
parser.add_argument('--hide', dest='show', default=True, action='store_false')
parser.add_argument('--force', '-f', dest='force', default=False, action='store_true')
parser.add_argument('--xlim', type=int, nargs=2)

args = parser.parse_args()

name = args.logfile.name.rsplit(".", 1)[0]

fig, ax = plt.subplots(nrows=3, ncols=1, figsize=(19.2, 10.8))

df = pd.read_csv(args.logfile, sep='\t')

df = df.query('instructions != 0 and calls != 0')
df.sort_values(by=['calls', 'instructions'], ascending=[False, True], inplace=True)

pd.options.display.max_colwidth = 200
try:
    with open("%s.dump" % name, "%c" % ("w" if args.force else "x")) as f:
        df.head(1000).to_string(f, columns=['class', 'method', 'calls', 'instructions'], index=False)
except:
    print("%s.dump already exists, please use -f or --force to overwrite" % name)
    pass

df.sort_values(by=['instructions', 'calls'], ascending=[True, False], inplace=True)

print("[%s] instructions: min %3.0f max %9.0f sum %9.0f mean %8.2f median %8.2f 95%% %8.2f 75%% %8.2f 50%% %8.2f" %
        (os.path.basename(name),
         df['instructions'].min(),
         df['instructions'].max(),
         df['instructions'].sum(),
         df['instructions'].mean(),
         df['instructions'].median(),
         df['instructions'].quantile(.95),
         df['instructions'].quantile(.75),
         df['instructions'].quantile(.5)))

print("[%s] calls       : min %3.0f max %9.0f sum %9.0f mean %8.2f median %8.2f 95%% %8.2f 75%% %8.2f 50%% %8.2f" %
        (os.path.basename(name),
         df['calls'].min(),
         df['calls'].max(),
         df['calls'].sum(),
         df['calls'].mean(),
         df['calls'].median(),
         df['calls'].quantile(.95),
         df['calls'].quantile(.75),
         df['calls'].quantile(.5)))

data = pd.DataFrame({'occurences':df.groupby('instructions')['calls'].count(), 'calls':df.groupby('instructions').sum()['calls']})

instructions = data['occurences']
instructions.index.name = 'Method size (#instructions)'
instructions.name = 'Frequency'

instructions.sort_index(inplace=True)
instructions.plot.bar(ax=ax[0])
(instructions.cumsum() / instructions.sum()).plot.line(ax=ax[0], secondary_y=True, use_index=False)
ax[0].set_ylabel("# of methods")
ax[0].right_ax.set_ylabel("Cumulative %% of methods (over %.2g)" % instructions.sum())
ax[0].set_title("%s (number of methods)" % os.path.basename(name))
if args.xlim:
    ax[0].set_xlim(args.xlim)


calls = data['calls']
calls.index.name = 'Method size (#instructions)'
calls.name = 'Number of calls'

calls.sort_index(inplace=True)
calls.plot.bar(ax=ax[1])
(calls.cumsum() / calls.sum()).plot.line(ax=ax[1], secondary_y=True, use_index=False)

#ax[1].yaxis.set_major_formatter(FuncFormatter(lambda y, pos: "%g" % (y / 1000)))
ax[1].set_ylabel('# of calls')
ax[1].right_ax.set_ylabel('%% of calls (cumulative, over %.2g)' % calls.sum())
ax[1].legend().remove()
ax[1].set_title("%s (number of calls)" % os.path.basename(name))
if args.xlim:
    ax[1].set_xlim(args.xlim)

total = calls * calls.index
total.index.name = 'Method size (#instructions)'
total.name = r'#calls $\times$ #instructions$'

total.sort_index(inplace=True)
total.plot.bar(ax=ax[2])
(total.cumsum() / total.sum()).plot.line(ax=ax[2], secondary_y=True, use_index=False)

#ax[2].yaxis.set_major_formatter(FuncFormatter(lambda y, pos: "%g" % (y / 1000)))
ax[2].set_ylabel("# of executed instructions")
ax[2].right_ax.set_ylabel('%% of executed #instructions (cumulative, over %.2g)' % total.sum())
ax[2].legend().remove()
ax[2].set_title(r'%s (Distribution of executed instructions)' % os.path.basename(name))
if args.xlim:
    ax[2].set_xlim(args.xlim)

plt.tight_layout()
plt.savefig("%s.svgz" % name, bbox_inches='tight', dpi=100)
if args.show:
    plt.show()
