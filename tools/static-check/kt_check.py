import os
REPO=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),"..",".."))
import re,os,glob,sys,collections
ROOT=os.path.join(REPO,'app','src')
files=glob.glob(ROOT+'/**/*.kt',recursive=True)
src={f:open(f,encoding='utf-8').read() for f in files}
def strip(s):
    s=re.sub(r'/\*[\s\S]*?\*/','',s); s=re.sub(r'//[^\n]*','',s)
    s=re.sub(r'"""[\s\S]*?"""','""',s); s=re.sub(r'"(?:\\.|[^"\\\n])*"','""',s); s=re.sub(r"'(?:\\.|[^'\\\n])'","''",s); return s
clean={f:strip(s) for f,s in src.items()}
pkg={f:(re.search(r'^package\s+([\w.]+)',s,re.M) or [None,''])[1] for f,s in src.items()}
types=collections.defaultdict(set)      # simple name -> {pkg}
topfun=collections.defaultdict(set)     # fun/val name -> {pkg}
members={}                               # fq type -> member names (rough)
for f,s in clean.items():
    p=pkg[f]
    for m in re.finditer(r'^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|internal|private|data|sealed|enum|abstract|open|annotation|inner|value|fun)\s+)*(?:class|interface|object)\s+(\w+)',s,re.M):
        if not m.group(0).strip().startswith('private'): types[m.group(1)].add(p)
    for m in re.finditer(r'^typealias\s+(\w+)',s,re.M): types[m.group(1)].add(p)
    for m in re.finditer(r'^(?:(?:internal|public|inline|suspend|private)\s+)*fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)\s*\(',s,re.M):
        if not re.match(r'^\s*private',m.group(0)): topfun[m.group(1)].add(p)
    for m in re.finditer(r'^(?:(?:internal|public|const)\s+)*(?:val|var)\s+(?:[\w.<>?]+\.)?(\w+)\s*[:=]',s,re.M): topfun[m.group(1)].add(p)
errors=[]; warns=[]
# A/B imports
for f,s in clean.items():
    rel=os.path.relpath(f,ROOT); p=pkg[f]
    imps=re.findall(r'^import\s+([\w.]+)(\.\*)?(?:\s+as\s+\w+)?\s*$',s,re.M)
    imported={i[0].split('.')[-1] for i in imps if not i[1]}
    wild={i[0] for i in imps if i[1]}
    for full,w in imps:
        if w or not full.startswith('uz.lokmago.restaurant'): continue
        name=full.split('.')[-1]; pk='.'.join(full.split('.')[:-1])
        ok = pk in types.get(name,set()) or pk in topfun.get(name,set())
        if not ok:
            # nested e.g. uz...OrderQueue.State : check parent type
            parts=full.split('.'); ok=any('.'.join(parts[:k]).rsplit('.',1)[0] in types.get(parts[k-1],set()) for k in range(len(parts)-1,3,-1))
        if not ok: errors.append((rel,'unresolved import',full))
    used=set(re.findall(r'(?<![\w.])([A-Z][A-Za-z0-9_]*)\b',s))
    declared_here={m.group(1) for m in re.finditer(r'(?:class|interface|object|typealias)\s+(\w+)',s)}
    for u in used:
        if u in declared_here or u in imported: continue
        pks=types.get(u)
        if not pks or p in pks: continue
        if any(w in pks for w in wild): continue
        errors.append((rel,'missing import? (type from other package)',f'{u} <- {sorted(pks)}'))
    for fn,pks in topfun.items():
        if len(fn)<3 or fn in('main','map','get','set','copy','name','id'): continue
        if p in pks or fn in imported or any(w in pks for w in wild): continue
        if re.search(r'(?<![\w.])'+re.escape(fn)+r'\s*\(',s) and fn not in declared_here and not re.search(r'fun\s+(?:[\w.<>?]+\.)?'+re.escape(fn)+r'\b',s):
            if not re.search(r'\.'+re.escape(fn)+r'\s*\(',s) or True:
                warns.append((rel,'top-level fun used without import?',f'{fn} <- {sorted(pks)}'))
# C resources
res={'drawable':set(),'string':set(),'raw':set(),'mipmap':set(),'color':set()}
for f in glob.glob(ROOT+'/main/res/**/*',recursive=True):
    if os.path.isfile(f):
        d=os.path.basename(os.path.dirname(f)).split('-')[0]; n=os.path.splitext(os.path.basename(f))[0]
        if d in res: res[d].add(n)
        if d=='values':
            t=open(f,encoding='utf-8',errors='ignore').read()
            for k,kind in (('string','string'),('color','color')):
                for m in re.finditer(r'<%s\s+name="(\w+)"'%k,t): res[kind].add(m.group(1))
for f,s in clean.items():
    for kind,name in re.findall(r'\bR\.(drawable|string|raw|mipmap|color)\.(\w+)',s):
        if name not in res[kind]: errors.append((os.path.relpath(f,ROOT),'missing resource',f'R.{kind}.{name}'))
# manifest
mf=open(ROOT+'/main/AndroidManifest.xml').read()
for n in re.findall(r'android:name="(\.[\w.]+)"',mf):
    cls=n.split('.')[-1]
    if 'uz.lokmago.restaurant'+'.'.join(n.split('.')[:-1]) not in types.get(cls,set()): errors.append(('AndroidManifest.xml','component class not found',n))
# D theme members
def obj_members(name):
    for f,s in clean.items():
        m=re.search(r'\bobject\s+'+name+r'\b[^{]*\{',s)
        if m:
            i=m.end(); d=1; j=i
            while d and j<len(s): d+= (s[j]=='{') - (s[j]=='}'); j+=1
            body=s[i:j]; return set(re.findall(r'\b(?:val|var|fun)\s+(\w+)',body))|set(re.findall(r'\bconst val\s+(\w+)',body))
    return None
for obj in ('Lg','Dt'):
    mem=obj_members(obj)
    if mem is None: warns.append(('-','object not found',obj)); continue
    for f,s in clean.items():
        for name in set(re.findall(r'\b'+obj+r'\.(\w+)',s)):
            if name not in mem: errors.append((os.path.relpath(f,ROOT),'unknown member',f'{obj}.{name}'))
# E duplicate members in one class body
def scopes(s):
    out=[]; stack=[]
    for m in re.finditer(r'(\{|\})',s):
        if m.group(1)=='{':
            head=s[max(0,s.rfind('\n',0,m.start())):m.start()]
            kind=re.search(r'\b(class|object|interface)\s+(\w+)',head)
            stack.append((m.end(),kind.group(2) if kind else None))
        else:
            st=stack.pop()
            if st[1]: out.append((st[1],s[st[0]:m.start()]))
    return out
for f,s in clean.items():
    for name,body in scopes(s):
        # only first nesting level: remove inner braces
        depth=0; top=[]
        for ch in body:
            if ch=='{': depth+=1
            elif ch=='}': depth-=1
            elif depth==0: top.append(ch)
        flat=''.join(top)
        props=re.findall(r'(?:^|\n)\s*(?:(?:private|internal|protected|override|lateinit|const|@\w+)\s+)*(?:val|var)\s+(\w+)\s*[:=\n]',flat)
        dups=[k for k,v in collections.Counter(props).items() if v>1]
        if dups: errors.append((os.path.relpath(f,ROOT),'duplicate property in '+name,','.join(dups)))
print("FILES:",len(files)); 
for e in sorted(set(errors)):
    if e[1]=="unresolved import" and e[2] in ("uz.lokmago.restaurant.R","uz.lokmago.restaurant.BuildConfig"): continue   # generated by the Android build
    print("ERROR",*e)
print("--- warnings (review):")
for w in sorted(set(warns))[:40]: print("WARN",*w)
real=[e for e in set(errors) if not (e[1]=='unresolved import' and e[2] in ('uz.lokmago.restaurant.R','uz.lokmago.restaurant.BuildConfig'))]
print("errors:",len(real),"warnings:",len(set(warns)))
sys.exit(1 if real else 0)
