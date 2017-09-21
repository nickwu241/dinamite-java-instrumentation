import glob, json, re, struct, sys, os

ANGLE_BRACKETS = re.compile(r'[<>]')
FASTFUNC_ENTRY_SIZE = 24

def create_fmap_from_tsv(tsv_filename, output=False):
    f = open(tsv_filename, 'r')

    fmap = {}
    f.readline() # get rid of header
    for line in f.readlines():
        l = line.strip().split('\t') # 0:id, 1:class, 2:method, 3:signature
        id = int(l[0])
        klass = l[1]
        method = ANGLE_BRACKETS.sub('_', l[2])
        fmap[id] = '{:s}.{:s}'.format(klass, method)
    f.close()
    if output:
        with open('map_functions.json', 'w') as out:
            json.dump(fmap, fp=out)

    return fmap

def get_tid_from_filename(binary_trace_filename):
    return int(binary_trace_filename.split('.')[2])

def print_fastfunc(binary_trace_filename, fmap, depth=0, file=sys.stdout):
    tid = get_tid_from_filename(binary_trace_filename)
    current_depth = 0
    with open(binary_trace_filename, 'rb') as f:
        while True:
            b = f.read(FASTFUNC_ENTRY_SIZE)
            if b == b'':
                break

            (timestamp, fid, direction) = struct.unpack("QH6xc7x", b)
            if depth > 0:
                if direction == b'\x00':
                    current_depth += 1
                elif current_depth > 0:
                    current_depth -=1
                
                if (current_depth < depth):
                    print(current_depth*' ' + fmap[fid], file=file)
            else:
                print("{0};{1};{2};{3}".format(
                    '-->' if direction == b'\x00' else '<--',
                    fmap[fid], tid, timestamp), file=file)

def xml_fastfunc(binary_trace_filename, fmap, f, depth=1):
    # style = "<style>html,body{font-family:Helvetica,Arial,sans-serif;white-space:pre-wrap;}</style>\n"
    # f.write(style)
    f.write('<callstack id="callstack">')
    call_id = 0
    current_depth = 0
    with open(binary_trace_filename, 'rb') as binary_trace:
        while True:
            b = binary_trace.read(FASTFUNC_ENTRY_SIZE)
            if b == b'':
                break

            (timestamp, fid, direction) = struct.unpack("QH6xc7x", b)
            if direction == b'\x00': # enter
                if current_depth < depth:
                    f.write('<f id="{:d}" d="{:d}" n="{}">'.format(call_id, current_depth, fmap[fid]))
                    call_id += 1
                current_depth += 1
            elif direction == b'\x01': # exit
                if (current_depth == 0):
                    # no matching entering function
                    print('[WARNING] Skipping EXIT for "{}" because no corresponding ENTER.'.format(fmap[fid]))
                    continue
                current_depth -=1
                if (current_depth < depth):
                    f.write('</f>')
    if current_depth > 0:
        print('[WARNING] Ended with depth of "{:d}", adding extra closing tags to validate XML.'.format(current_depth))
        while current_depth > 0:
            f.write('</f>')
            current_depth -= 1

    f.write('</callstack>')
 
def flamegraph_fastfunc(binary_trace_filename, fmap, f=None):
    with open(binary_trace_filename, 'rb') as binary_trace:
        DELIM = ';'
        stack = []

        while True:
            b = binary_trace.read(FASTFUNC_ENTRY_SIZE)
            if b == b'':
                break

            (timestamp, fid, direction) = struct.unpack("QH6xc7x", b)
            if direction == b'\x00': # enter
                stack.append(fmap[fid])

            elif direction == b'\x01': # exit
                if (stack):
                    print(DELIM.join(stack) + ' 1')
                    stack = stack[:-1]

def parse_sql_to_flamegraph(sql_file, total_runtime, f=None):
    PERCENT_THRESHOLD = 0.02
    DELIM = ';'
    stack = []
    sample_rate = total_runtime / 500

    print('total_runtime=' + str(total_runtime))
    print('sample_rate=' + str(sample_rate))

    with open(sql_file, 'r') as sql:
        while True:
            line = sql.readline();
            if not line:
                break
                                # 0  1          2     3   4         5
            l = line.strip().split('|') # id|enter/exit|fname|tid|timestamp|duration
            # print(l)
            if l[1] == '0': # enter
                stack.append(l[2].strip('"'))
            elif l[1] == '1': # exit
                if not stack:
                    print('[WARNING] callstack is empty on an exit... line=' + line.strip())
                    continue
                duration = int(l[5])
                #if (duration / total_runtime > PERCENT_THRESHOLD):
                #    f.write('{:s} {:d}\n'.format(DELIM.join(stack), int(duration / sample_rate)))
                f.write('{:s} {:d}\n'.format(DELIM.join(stack), int(duration / sample_rate)))
                stack = stack[:-1]

def get_total_time(binary_trace_filename):
    with open(binary_trace_filename, 'rb') as binary_trace:        
        # get first record
        (timestamp1, fid, direction) = struct.unpack("QH6xc7x", binary_trace.read(FASTFUNC_ENTRY_SIZE))

        # get last record
        binary_trace.seek(-FASTFUNC_ENTRY_SIZE, os.SEEK_END)
        (timestamp2, fid, direction) = struct.unpack("QH6xc7x", binary_trace.read(FASTFUNC_ENTRY_SIZE))

        return timestamp2 - timestamp1
    return 0

def main():
    map_function_file = 'map_functions.json'
    if os.path.exists(map_function_file):
        with open(map_function_file, 'r') as f:
            fmap = json.load(f)
            print(fmap)
    else:
        # no map_functions.json, try to create from a .tsv file
        tsv_files = [f for f in os.listdir('.') if re.search(r'\.tsv$', f)]
        if len(tsv_files) != 1:
            print('Error: expected exactly 1 .tsv file in directory...')
            exit(-1)

        tsv_file = tsv_files[0]
        try:
            fmap = create_fmap_from_tsv(tsv_file)
        except OSError as e:
            print("Error opening " + tsv_file)
            print(e)
            exit(-1)
    
    for binary_trace_file in (f for f in os.listdir('.') if re.search(r'^trace\.bin\.\d+$', f)):
        # sql_file = binary_trace_file + ".sql"
        print_fastfunc(binary_trace_file, fmap, depth=0)
        # xml_fastfunc(binary_trace_file, fmap, f=open(binary_trace_file + ".xml", "w"), depth=5)
        # flamegraph_fastfunc(binary_trace_file, fmap)
        # parse_sql_to_flamegraph(sql_file, get_total_time(binary_trace_file), f=open(binary_trace_file + ".folded", 'w', newline='\n'))
        # print(get_total_time(binary_trace_file))

if __name__ == '__main__':
    main()
