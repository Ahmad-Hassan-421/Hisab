p='scripts/generate_invite_messages.py'
s=open(p,encoding='utf-8').read()
target='Join our Hisab and let\'s keep things balanced.'
# remove only second occurrence
idx=s.find(target)
idx2=s.find(target, idx+1)
assert idx2!=-1
s=s[:idx2]+ 'Join our Hisab and let\'s keep every balance tidy and clear.' + s[idx2+len(target):]
open(p,'w',encoding='utf-8').write(s)
print('done')
