#!/bin/python
import argparse
import re
import networkx as nx

div_class_q_re = re.compile(r'<div class="\w* *q *\w*"') # denotes filtered
div_class_k_re = re.compile(r'<div class="\w* *k *\w*"') # denotes thread
tag_re = re.compile(r'<.*?>')
method_re = re.compile(r'\S+?\(.*\)')
lineno_re = re.compile(r':\d+')
native_re = re.compile(r'\s*\(native\)\s+')
class CallRecord(object):
    def __init__(self, method_tuples=None):
        self.method_tuples = [method_tuples] if method_tuples else []

    def __repr__(self):
        return 'CallRecord({})'.format(self.method_tuples)

    def __str__(self):
        # method, time, avg_time, calls, level, percent
        depth_arrow = ('-' * self.method_tuples[::-1][0][4]) + '>'
        methods = ';'.join(map(lambda m: "{:s}|{:.2f}%".format(m[0], m[5]), self.method_tuples))
        return (depth_arrow + ' ' + methods)

    def __len__(self):
        return len(self.method_tuples)

    def push(self, method_tuple):
        self.method_tuples.append(method_tuple)

    def first(self):
        return self.method_tuples[0]

    def last(self):
        return self.method_tuples[-1]

    def expand(self):
        return (CallRecord((method)) for method in self.method_tuples)
    
class MethodNameManager(object):
    def __init__(self):
        self.shortnameMapped = {}   # originalName   => shortname
        self.shortnameMappings = {} # shortname      => originalName
        self.shortnameVersion = {}  # shortnameBase  => version
        self.originalNameSrc = {}   # originalName   => sourceLine

    def unique_shortname(self, originalName, transformFunc, sourceLine=None):
        if originalName in self.shortnameMapped:
            return self.shortnameMapped[originalName]

        if sourceLine:
            self.originalNameSrc[originalName] = sourceLine

        shortnameBase = transformFunc(originalName)
        version = 0

        if shortnameBase in self.shortnameVersion:
            version = self.shortnameVersion[shortnameBase]
        self.shortnameVersion[shortnameBase] = version + 1

        shortname = shortnameBase
        if version != 0:
            shortname = shortnameBase + "_v" + str(version)

        self.shortnameMapped[originalName] = shortname
        self.shortnameMappings[shortname] = originalName
        return shortname

def get_raw_fields(line):
    """Returns method, time, avg_time, calls, level[, percent[, filtered]]"""
    if line.startswith('"'):
        closing_quote = line.find('"', 1)
        method = line[1:closing_quote]
        return (method, *line[closing_quote+2:].split(','))
    else:
        return line.split(',')

def debug_info(method_line):
    """Returns None or (source_location, method)"""
    # e.g. [CLASS.java:[123]] PACKAGE.CLASS.METHOD([ARG1[, ARG2]])
    m = method_line.split(' ', 1)
    if lineno_re.search(m[0]) or m[0].endswith('.java') or '.' not in m[0]:
        return m[0], m[1]
    return None

def shorten_method(method):
    """Get the method name.

    e.g. PACKAGE.CLASS.METHOD(ARG1, ARG2) -> METHOD
    """
    after_last_dot = len(method) - method[::-1].index('.')
    arg_open_paren = method.index('(')
    return method[after_last_dot:arg_open_paren]

def append_yjp_csv(input_csv, output_csv, yjp_map):
    """Add columns Percent and Filtered to yjp's csv, output to output_csv."""
    with open(input_csv, 'r') as fin, open(output_csv, 'w') as fout:
        fout.write(fin.readline().strip() + ',"Percent","Filtered"\n') # get rid of header
        thread_c = 0
        for line in fin:
            method_line, time, avg_time, calls, level = get_raw_fields(line)[:5]
            # check if it's a thread instead of a method
            if 'Native ID' in method_line:
                overall_runtime = float(time.replace('"', ''))
                thread_c += 1
                continue
            m = debug_info(method_line)
            method = m[1] if m else method_line
            method = native_re.sub('', method) # clear out (native) if it's there
            time = time.replace('"', '')
            avg_time = avg_time.replace('"', '')
            calls = calls.replace('"', '')
            level = level.replace('"', '').strip()

            percent = 0 if overall_runtime == 0 else float(time) / overall_runtime * 100
            filtered = yjp_map[method]
            out = '"{}",{},{},{},{},{},{}\n'.format(method_line, time, avg_time, calls, level, percent, filtered)
            fout.write(out)
    return thread_c

def yjp_search_adaptive(html_file, method_map=None):
    def change_to_brackets(line):
        return line.replace('&lt;', '<').replace('&gt;', '>')

    thread_c = conflic_c = 0
    for line in html_file:
        # <tr><td> starting tags mean it's a method
        if not line.startswith("<tr><td>"):
            continue

        thread = div_class_k_re.search(line)
        if thread:
            thread_c += 1
            continue
        filtered = True if div_class_q_re.search(line) else False

        line = tag_re.sub('', line).strip()
        if line.startswith('[SQL]'):
            continue

        match = method_re.search(line) # clear tags & get signature
        if match:
            method = change_to_brackets(match.group(0))
            if method_map is not None:
                if method in method_map and method_map[method] != filtered:
                    conflic_c += 1
                #    print(method, method_map[method], filtered)
                method_map[method] = filtered
            else:
                print(method + ':' + str(filtered))
        else:
            print(line)
            raise Exception("regex for methodnot matched, double check it's correct!")
    html_file.seek(0)
    return thread_c, conflic_c

def yjp_process_csv(input_csv, percent_filter, collapse_percent):
    def process_method(mapper, method_line):
        # Example: FILE.JAVA:123 PACKAGE.CLASS.METHOD(ARG1, ARG2) -> METHOD
        m = debug_info(method_line)
        if m:
            src_loc, method = m
            return mapper.unique_shortname(method, shorten_method, src_loc)
        else:
            return mapper.unique_shortname(method_line, shorten_method)

    def parse_line(line):
        method, time, avg_time, calls, level, percent = get_raw_fields(line.strip())[:6]

        percent = float(percent)
        if percent <= percent_filter:
            return None

        method = process_method(mapper, method)
        time = int(time)
        avg_time = int(avg_time)
        calls = int(calls)
        level = int(level)
        return (method, time, avg_time, calls, level, percent)

    mapper = MethodNameManager()
    stack = []

    with open(input_csv, 'r') as f:
        f.readline() # get rid of header
        # parse first line to set up variables
        last = parse_line(f.readline())
        record = CallRecord(last)

        for line in f:
            cur = parse_line(line)
            if cur is None: # don't process if this method is negligible
                continue

            # if run-time is mostly in one callee, collapse the call
            # note: 4 is depth, 5 is percent
            if cur[4] > last[4] and abs(cur[5] - last[5]) <= collapse_percent:
                record.push(cur)
            else:
                stack.append(record)
                record = CallRecord(cur)
            last = cur

    return mapper, stack

def main():
    parser = argparse.ArgumentParser(
        description="Find filtered methods from YJP and DINAMITE.")
    parser.add_argument('bench', metavar='DACAPO BENCHMARK')
    args = parser.parse_args()

    bench_dir = args.bench + '-out'

    PERCENT_FILTER = 5.00
    COLLAPSE_PERCENT = 1.00
    INPUT_CSV = args.bench + '-out/' + args.bench + '.csv'

    #append_runtime_percent_and_filtered('Call-tree--by-thread.csv', 'avrora.csv')
    mapper, stack = yjp_process_csv(INPUT_CSV, PERCENT_FILTER, COLLAPSE_PERCENT)
    # print out new calltree
    
    #G = nx.DiGraph()
    #labels = {'START' : 'START', 'END' : 'END'}
    #prev_node = 'START'
    for record in stack:
        #new_node = record.last()[0]
        #labels[new_node] = new_node
        #G.add_edge(prev_node, new_node)
        #prev_node = new_node
        print(record)
        #for r in record.expand():
        #    print(r)
    #G.add_edge(prev_node, 'END')
    #import matplotlib.pyplot as plt
    #nx.draw(G)
    #nx.draw_networkx_labels(G, nx.random_layout(G), labels)
    #plt.show()
    
    #aGraph = nx.drawing.nx_agraph.to_agraph(G)
    #print(nx.drawing.nx_agraph.to_agraph(G))
    
    # print(mapper.shortnameMappings)
    # print(mapper.originalNameSrc)

if __name__ == '__main__':
    main()
