import re
text=open('scripts/generate_invite_messages.py',encoding='utf-8').read()
i=text.index('messages = [')
j=text.index(']', i)
block=text[i:j+1]
strs=re.findall(r'"([^"]+)"', block)
from collections import Counter
c=Counter(strs)
dup=[k for k,v in c.items() if v>1]
print('num strings',len(strs),'unique',len(set(strs)))
for d in dup: print('DUP:',d)
