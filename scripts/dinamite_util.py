def get_args(signature):
    #print('signature:', signature)
    after_open_paren = signature.find('(') + 1
    close_paren = signature.find(')')
    args = signature[after_open_paren:close_paren]
    return parse_args(args, len(args))

def parse_args(args, args_len, i=0, parsed_args=None, array_depth=0):
    #print('args, len, i: ', args, args_len, i)
    if parsed_args is None:
        parsed_args = []
    if i >= args_len:
        result = ', '.join(parsed_args)
        #print('result: ', result)
        return result

    c = args[i]
    i += 1
    type = None
    if c == 'Z':
        type = 'boolean'
    elif c == 'C':
        type = 'char'
    elif c == 'B':
        type ='byte'
    elif c == 'S':
        type = 'short'
    elif c == 'I':
        type = 'int'
    elif c == 'F':
        type = 'float'
    elif c == 'J':
        type = 'long'
    elif c == 'D':
        type = 'double'
    elif c == 'L': # reference
        semi_colon = args.find(';', i)
        type = args[i:semi_colon].split('/')[-1]
        i = semi_colon + 1
    
    if type:
        #print('append type: ', type + '[]' * array_depth)
        parsed_args.append(type + '[]' * array_depth)
        array_depth = 0
    elif c == '[':
        array_depth += 1
    else:
        print(args)
        raise Exception('Unrecognized token...')
    return parse_args(args, args_len, i, parsed_args, array_depth)
    
def to_yjp_style(klass, method, signature):
    args = get_args(signature)
    return '{:s}.{:s}({:s})'.format(klass.replace('/', '.'), method, args)
