import os,sys
REPO=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),"..",".."))
import re,glob,os,sys
ROOT=os.path.join(REPO,'app','src','main')
def strip(s):
    s=re.sub(r'/\*[\s\S]*?\*/','',s); s=re.sub(r'//[^\n]*','',s); s=re.sub(r'"""[\s\S]*?"""','""',s); s=re.sub(r'"(?:\\.|[^"\\\n])*"','""',s); return s
PK={
 'androidx.compose.foundation':['background','border','clickable','Image','rememberScrollState','verticalScroll','horizontalScroll','BorderStroke'],
 'androidx.compose.foundation.layout':['Column','Row','Box','Spacer','PaddingValues','Arrangement','fillMaxWidth','fillMaxSize','fillMaxHeight','padding','size','height','width','offset','statusBarsPadding','navigationBarsPadding','imePadding','aspectRatio','WindowInsets'],
 'androidx.compose.foundation.lazy':['LazyColumn','LazyRow'],
 'androidx.compose.foundation.shape':['CircleShape','RoundedCornerShape'],
 'androidx.compose.material3':['Text','Surface','Switch','SwitchDefaults','Checkbox','CheckboxDefaults','OutlinedTextField','OutlinedTextFieldDefaults','AlertDialog','TextButton','CircularProgressIndicator','LinearProgressIndicator','DropdownMenu','DropdownMenuItem','Badge','ExperimentalMaterial3Api'],
 'androidx.compose.ui':['Modifier','Alignment'],
 'androidx.compose.ui.draw':['clip','alpha','shadow'],
 'androidx.compose.ui.graphics':['Color','Brush','ColorFilter','ColorMatrix','graphicsLayer'],
 'androidx.compose.ui.text.font':['FontWeight'],
 'androidx.compose.ui.text.style':['TextAlign','TextDecoration'],
 'androidx.compose.ui.text.input':['KeyboardType','ImeAction','PasswordVisualTransformation'],
 'androidx.compose.ui.unit':['dp','sp','Dp'],
 'androidx.compose.ui.platform':['LocalContext','LocalHapticFeedback'],
 'androidx.compose.ui.res':['painterResource'],
 'androidx.compose.ui.layout':['ContentScale'],
 'androidx.compose.ui.window':['Dialog','DialogProperties'],
 'androidx.compose.ui.semantics':['semantics','contentDescription'],
 'androidx.compose.ui.input.pointer':['pointerInput'],
 'androidx.compose.foundation.gestures':['detectTapGestures','detectTransformGestures'],
 'androidx.compose.ui.hapticfeedback':['HapticFeedbackType'],
 'androidx.compose.runtime':['remember','mutableStateOf','LaunchedEffect','produceState','rememberCoroutineScope','derivedStateOf','mutableIntStateOf','mutableFloatStateOf','Composable','DisposableEffect'],
 'androidx.compose.runtime.saveable':['rememberSaveable'],
 'androidx.lifecycle.compose':['collectAsStateWithLifecycle'],
 'androidx.hilt.navigation.compose':['hiltViewModel'],
 'androidx.core.app':['NotificationCompat','NotificationManagerCompat','ServiceCompat'],
 'dagger.hilt.android':['AndroidEntryPoint','HiltAndroidApp'],
 'dagger.hilt.android.lifecycle':['HiltViewModel'],
 'javax.inject':['Inject','Singleton','Qualifier'],
 'kotlinx.coroutines':['CoroutineScope','Dispatchers','SupervisorJob','launch','delay','Job','isActive'],
 'kotlinx.coroutines.flow':['MutableStateFlow','StateFlow','asStateFlow','combine','stateIn','SharingStarted','MutableSharedFlow'],
}
# names that are ALSO member functions/extensions elsewhere: only flag when used as a bare call/identifier pattern
need={}
for pkg,names in PK.items():
    for n in names: need[n]=pkg
errs=[]
for f in glob.glob(ROOT+'/java/**/*.kt',recursive=True):
    s=strip(open(f,encoding='utf-8').read()); rel=os.path.relpath(f,ROOT)
    imps=re.findall(r'^import\s+([\w.]+)(\.\*)?\s*$',s,re.M)
    exact={i[0] for i in imps if not i[1]}; wild={i[0] for i in imps if i[1]}
    pkgdecl=re.search(r'^package\s+([\w.]+)',s,re.M).group(1)
    def have(n,pkg): return f'{pkg}.{n}' in exact or pkg in wild or pkgdecl==pkg
    for n,pkg in need.items():
        if n in('dp','sp'): pat=r'\d(?:\.\d+)?\.'+n+r'\b|\)\.'+n+r'\b'
        elif n in('Composable',): pat=r'@Composable'
        elif n[0].islower(): pat=r'(?<![\w])\.?'+n+r'\s*[({]|\.'+n+r'\b' if n not in('contentDescription',) else r'\bcontentDescription\s*='
        else: pat=r'(?<![\w.])'+n+r'\b'
        if re.search(pat,s) and not have(n,pkg):
            # declared locally / member of own class?
            if re.search(r'\b(?:fun|val|var|class|object)\s+(?:\w+\.)?'+n+r'\b',s): continue
            errs.append((rel,n,pkg))
    if re.search(r'\b(?:val|var)\s+\w+\s+by\s+',s):
        if not ('androidx.compose.runtime.getValue' in exact or 'androidx.compose.runtime' in wild): errs.append((rel,'getValue (delegate `by`)','androidx.compose.runtime'))
    if re.search(r'\bvar\s+\w+\s+by\s+(?:remember|rememberSaveable)',s):
        if not ('androidx.compose.runtime.setValue' in exact or 'androidx.compose.runtime' in wild): errs.append((rel,'setValue (var by remember)','androidx.compose.runtime'))
IGNORE_NAMES={'contentDescription','painterResource','delay','launch','size','height','width','getValue'}      # named args / members / fully-qualified uses
real=[e for e in sorted(set(errs)) if not (e[1].split()[0] in IGNORE_NAMES)]
for e in real: print("MISSING IMPORT",*e)
print("count:",len(real)); sys.exit(1 if real else 0)
