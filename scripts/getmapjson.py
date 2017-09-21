import sys, json, re

import dinamite_util

def main():
    if len(sys.argv) != 2:
        print("Need exactly ONE arguemnt: <tsv filename>")
        exit(-1)

    FUNCTION_MAP_FILENAME = 'map_functions.json'
    ANGLE_BRACKETS = re.compile(r'[<>]')

    tsv_file = sys.argv[1]
    fmap = {}
    overloaded_methods = {}
    with open(tsv_file, 'r') as f:
        f.readline() # get rid of header
        for row in f:
            cols = row.strip().split('\t')

            # 0:id, 1:class, 2:method, 3:signature
            method = dinamite_util.to_yjp_style(cols[1], cols[2], cols[3])
            
            # changing bad filename characters '<' or '>' to '_'
            # since FlowViz creates files for each function
            method = ANGLE_BRACKETS.sub('_', method)
            id = int(cols[0]) # method id

            # overloaded b/c some methods have different return values
            if method not in fmap:
                fmap[method] = id
            elif method not in overloaded_methods:
                overloaded_methods[method] = 1
            else:
                overloaded_methods[method] += 1

    with open(FUNCTION_MAP_FILENAME, 'w') as f:
        json.dump(obj=fmap, fp=f)
        print('Wrote function to id map to:', FUNCTION_MAP_FILENAME)
    print(overloaded_methods)
    print('# of overloaded methods:', len(overloaded_methods))

if __name__ == '__main__':
    main()
