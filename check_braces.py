import sys
import re

path = 'src/main/java/com/bitchat/android/mesh/BluetoothMeshService.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

def replacer(match):
    return ''.join('\n' if c == '\n' else ' ' for c in match.group(0))

text = re.sub(r'\"\"\"[\s\S]*?\"\"\"', replacer, text)
text = re.sub(r'\"[^\"]*?\"', replacer, text)
text = re.sub(r'/\*[\s\S]*?\*/', replacer, text)
text = re.sub(r'//.*', replacer, text)

lines = text.split('\n')
stack = []
for i, line in enumerate(lines):
    for char in line:
        if char == '{':
            stack.append(i + 1)
        elif char == '}':
            if stack:
                stack.pop()
            else:
                print(f'Extra closing brace at line {i+1}!')

print(f'Final depth: {len(stack)}')
if stack:
    print('Unclosed braces opened at lines:')
    for line_num in stack:
        print(line_num)
