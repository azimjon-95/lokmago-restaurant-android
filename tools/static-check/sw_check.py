import os
REPO=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),"..",".."))
import re,glob,os,collections
D=os.path.join(REPO,'ios','LokmaRestaurant')
files=sorted(glob.glob(D+'/*.swift'))
def strip(s):
    s=re.sub(r'/\*[\s\S]*?\*/','',s); s=re.sub(r'(?<!:)//[^\n]*','',s); s=re.sub(r'"""[\s\S]*?"""','""',s)
    def lit(m):
        # keep only the expressions inside \( ... ) so member references in interpolations are still checked
        t=m.group(0); out=[]; i=0
        while True:
            i=t.find('\\(',i)
            if i<0: break
            d=1; j=i+2
            while j<len(t) and d: d+=(t[j]=='(')-(t[j]==')'); j+=1
            out.append(t[i+2:j-1]); i=j
        return '"" '+' ; '.join(re.sub(r'"[^"]*"','""',e) for e in out)       # nested string literals inside an interpolation are not code
    return re.sub(r'"(?:\\.|[^"\\\n])*"',lit,s)
raw={f:open(f,encoding='utf-8').read() for f in files}
code={f:strip(s) for f,s in raw.items()}
def body_of(s,start):
    i=s.index('{',start)+1; d=1; j=i
    while d and j<len(s): d+=(s[j]=='{')-(s[j]=='}'); j+=1
    return s[i:j-1]
def members(typename):
    out=set()
    for f,s in code.items():
        for m in re.finditer(r'(?:struct|class|enum|extension|actor)\s+'+typename+r'\b[^{]*\{',s):
            b=body_of(s,m.start()); depth=0; top=[]
            for ch in b:
                if ch=='{': depth+=1
                elif ch=='}': depth-=1
                elif depth==0: top.append(ch)
            flat=''.join(top)
            out|=set(re.findall(r'\b(?:var|let)\s+(\w+)',flat)); out|=set(re.findall(r'\bfunc\s+(\w+)',flat)); out|=set(re.findall(r'\bcase\s+(\w+)',flat))
            # also members declared inside nested braces of var bodies are not top-level; fine
    return out
errors=[]
# Dt tokens
dt=members('Dt')
for f,s in code.items():
    for n in set(re.findall(r'\bDt\.(\w+)',s)):
        if n not in dt: errors.append((os.path.basename(f),'unknown Dt token',n))
# assets
assets=set(os.path.basename(p)[:-9] for p in glob.glob(D+'/Assets.xcassets/*.imageset'))
for f,s in raw.items():
    for n in set(re.findall(r'LgIcon\(name:\s*"(\w+)"',s))|set(re.findall(r'Image\("(\w+)"\)',s)):
        if n not in assets: errors.append((os.path.basename(f),'missing image asset',n))
    for n in set(re.findall(r'\(\"(ic_\w+)\"',s))|set(re.findall(r'\b"(ic_\w+)"',s)):
        if n not in assets: errors.append((os.path.basename(f),'missing icon asset',n))
# environment objects → members
objs={'model':'AppModel','menu':'MenuStore','courier':'CourierModel'}
mem={t:members(t) for t in objs.values()}
for f,s in code.items():
    for var,t in objs.items():
        if not re.search(r'(?:@EnvironmentObject|@ObservedObject|@StateObject)[^\n]*\b'+var+r'\s*:\s*'+t,s): continue
        for n in set(re.findall(r'(?<![\w.])'+var+r'\.(\w+)',s)):
            if n not in mem[t] and n not in('objectWillChange',): errors.append((os.path.basename(f),f'{var}.{n} not a member of {t}',''))
        for n in set(re.findall(r'\$'+var+r'\.(\w+)',s)):
            if n not in mem[t]: errors.append((os.path.basename(f),f'${var}.{n} not a member of {t}',''))
# Shared singletons
for f,s in code.items():
    for n in set(re.findall(r'AlertCenter\.shared\.(\w+)',s)):
        if n not in members('AlertCenter'): errors.append((os.path.basename(f),'AlertCenter.shared.'+n,'unknown'))
    for n in set(re.findall(r'CourierModel\.shared\??\.(\w+)',s)):
        if n not in members('CourierModel'): errors.append((os.path.basename(f),'CourierModel.shared.'+n,'unknown'))
    for n in set(re.findall(r'AppModel\.shared\??\.(\w+)',s)):
        if n not in members('AppModel'): errors.append((os.path.basename(f),'AppModel.shared.'+n,'unknown'))
    for n in set(re.findall(r'CourierLocation\.shared\.(\w+)',s)):
        if n not in members('CourierLocation'): errors.append((os.path.basename(f),'CourierLocation.shared.'+n,'unknown'))
    for n in set(re.findall(r'KeychainStore\.(\w+)',s)):
        if n not in members('KeychainStore'): errors.append((os.path.basename(f),'KeychainStore.'+n,'unknown'))
# api.xxx calls
apim=members('APIClient')
for f,s in code.items():
    for n in set(re.findall(r'\bapi\.(\w+)\(',s)):
        if n not in apim: errors.append((os.path.basename(f),'api.'+n,'not in APIClient'))
    for n in set(re.findall(r'model\.api\.(\w+)\(',s)):
        if n not in apim: errors.append((os.path.basename(f),'model.api.'+n,'not in APIClient'))
# component call labels
labels={'PrimaryButton':{'title','icon','color','loading','enabled','action'},'SectionCard':{'bg'},'LgIcon':{'name','color','size'}}
for f,s in code.items():
    for comp,ok in labels.items():
        for m in re.finditer(r'\b'+comp+r'\(([^()]*(?:\([^()]*\)[^()]*)*)\)',s):
            args=m.group(1)
            for lab in re.findall(r'(?:^|,)\s*(\w+)\s*:',args):
                if lab not in ok: errors.append((os.path.basename(f),comp+' label',lab))
# duplicate members in a type
for f,s in code.items():
    for m in re.finditer(r'\b(?:struct|class|enum|final class|extension)\s+(\w+)[^{]*\{',s):
        b=body_of(s,m.start()); depth=0; top=[]
        for ch in b:
            if ch=='{': depth+=1
            elif ch=='}': depth-=1
            elif depth==0: top.append(ch)
        flat=''.join(top)
        vs=re.findall(r'(?:^|\n)\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:private(?:\(set\))?\s+|fileprivate\s+|static\s+|final\s+)*(?:var|let)\s+(\w+)\s*[:=]',flat)
        for k,v in collections.Counter(vs).items():
            if v>1: errors.append((os.path.basename(f),'duplicate property in '+m.group(1),k))
        fs=re.findall(r'\bfunc\s+(\w+)\s*(\([^)]*\))',flat)
        sig=collections.Counter((n,re.sub(r'\s+','',re.sub(r':[^,)]*','',a))) for n,a in fs)
        for (n,a),v in sig.items():
            if v>1: errors.append((os.path.basename(f),'duplicate func in '+m.group(1),n+a))
# unknown project-looking types in NEW files
known=set(re.findall(r'\b(?:struct|class|enum|protocol|typealias|actor)\s+(\w+)',''.join(code.values())))
apple=set('''View Text Button VStack HStack ZStack Color Image ScrollView LazyVStack ForEach Spacer Circle Capsule RoundedRectangle LinearGradient Group Menu Toggle TextField SecureField ProgressView GeometryReader NavigationStack Binding State StateObject EnvironmentObject Environment ObservedObject Published AppStorage MainActor Task UIApplication UIImpactFeedbackGenerator UIKeyboardType UIImage UIGraphicsImageRenderer UIGraphicsImageRendererFormat URL URLRequest URLSession URLError Data Date UUID String Int Int64 Double Bool Array Dictionary Set Optional Error JSONDecoder JSONEncoder JSONSerialization Bundle UserDefaults ProcessInfo DispatchQueue Decodable Encodable Codable Identifiable Hashable Equatable CodingKey CodingKeys Decoder Encoder CLLocation CLLocationManager CLAuthorizationStatus CLLocationManagerDelegate NSObject Never Void Any AnyObject Self Result Locale Calendar TimeZone DateFormatter ISO8601DateFormatter NSNumber CGFloat CGSize CGRect AsyncImage PhotosPicker PhotosPickerItem UNUserNotificationCenter UNAuthorizationStatus Scene WindowGroup App EdgeInsets Alignment Font TextAlignment ViewModifier ViewBuilder Content Animation SwiftUI Foundation UIKit Network NWPathMonitor CoreLocation Security MagnificationGesture DragGesture Shape ContentTransition ShapeStyle LocalizedStringKey Label NSLock Notification NotificationCenter Timer RunLoop OperationQueue Sendable Task Continuation CheckedContinuation CGPoint UIColor UIDevice UIBackgroundTaskIdentifier DisplayStyle ScenePhase UIViewController UIWindow UIScene UIScreen UIImageView UNNotificationRequest UNNotificationResponse UNNotification UNNotificationAction UNNotificationCategory UNNotificationSettings UNNotificationContent UNMutableNotificationContent UNNotificationSound UNAuthorizationOptions UNUserNotificationCenterDelegate UIApplicationDelegate UIApplicationDelegateAdaptor Data Mirror Substring Character Float UInt UInt8 UInt64 Range ClosedRange KeyPath PartialKeyPath WritableKeyPath AnyView EmptyView TupleView Section List Form Picker TabView NavigationLink Alert ConfirmationDialog Sheet FullScreenCover Divider Link ControlGroup LabeledContent TextEditor Slider Stepper DatePicker ColorPicker ShareLink ContentUnavailableView ViewThatFits Grid GridRow LazyHStack LazyVGrid GridItem Path AngularGradient RadialGradient Material ButtonStyle PrimitiveButtonStyle Glass DefaultGlassEffectShape'''.split())
for f in files:
    nm=os.path.basename(f)
    if not re.match(r'(Courier|PushCheck|Permissions|MenuScreen|DishForm|StopList|Reviews|ImageViews|CloudinaryUploader|NetworkMonitor|MenuStore|DishDraft|RootView|AlertScreen|OrderDetailScreen|LoginScreen)',nm): continue
    s=code[f]
    s2=re.sub(r'\b[A-Za-z_]\w*\.(?:[a-z]\w*)\b','',s)
    for u in set(re.findall(r'(?<![\w.$"])([A-Z]\w+)\b',s)):
        if u in known or u in apple: continue
        # enum-case style .X or labels are lowercase; capitalised unknowns:
        errors.append((nm,'unknown type?',u))
FALSE_POSITIVES={'HTTPURLResponse','DEBUG','ObservableObject','PhotosUI','FocusState','Rectangle','StrokeStyle'}      # Apple types / compile flags the name list does not know
real=[e for e in sorted(set(errors)) if not (e[1]=='unknown type?' and e[2] in FALSE_POSITIVES)]
for e in real: print("SWIFT",*e)
print("count:",len(real)); import sys; sys.exit(1 if real else 0)
