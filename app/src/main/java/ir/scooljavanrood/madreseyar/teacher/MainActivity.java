package ir.scooljavanrood.madreseyar.teacher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.print.PrintManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.webkit.WebResourceResponse;
import java.util.Set;
import java.util.HashSet;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.ScriptHandler;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import javax.crypto.Cipher;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;

/**
 * Test-only Android container for the EXISTING unified /app/ staff web portal.
 * This app does not copy, rewrite or alter server-side teacher permissions.
 * The web frontend remains authoritative; Android-native edge cases require device QA.
 */
public final class MainActivity extends FragmentActivity {
    private static final String HOME = "https://scooljavanrood.ir/app/";
    private static final String SITE = "scooljavanrood.ir";
    private static final int FILE_REQUEST = 7301;
    private static final String STAFF_ME = HOME + "api/staff/me";
    private static final String TOKEN_KEY = "madreseyar.staff.token.v14";
    private static final String SESSION_KEY = "madreseyar.session.v14";
    private final ExecutorService authExecutor = Executors.newSingleThreadExecutor();
    private final Handler offerHandler = new Handler(Looper.getMainLooper());
    private final Runnable offerCheck = new Runnable() {
        @Override public void run() {
            if (!isFinishing() && !isDestroyed()) {
                maybeOfferFirstBiometricSetup();
                syncTeacherUi();
                offerHandler.postDelayed(this, 4000);
            }
        }
    };
    private BiometricVault vault;
    private FrameLayout root;
    private View lockOverlay;
    private Button fingerBtn;
    private boolean unlocked = false;
    // F01: screenshots are allowed only for the foreground, unlocked UI.
    private boolean isActivityForeground = false;
    private boolean biometricDialogOpen = false;
    private boolean browserLoaded = false;
    private boolean injectOnFirstPage = false;
    private boolean injectionWasApplied = false;
    private String pendingScript;
    private ScriptHandler sessionInjector;
    private String activeToken;

    private FrameLayout content;
    private WebView browser;
    private WebView popup;
    private ProgressBar progress;
    private android.webkit.ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private Button backBtn;
    private Button forwardBtn;
    // A03 - independent mobile dashboard and navigation; school server stays authoritative.
    private WebView teacherDashboard;
    private LinearLayout bottomNav;
    private TextView screenLabel;
    private Button logoutNative;
    private TextView[] dockItems;
    private boolean teacherUiReady = false;
    private boolean nativeHomeVisible = false;
    private boolean fullWebMode = false;
    private String currentNativeSection = "home";
    private String portalSkinScript = null;
    private static final Set<String> ALLOWED_TEACHER_ROUTES = new HashSet<>(java.util.Arrays.asList(
        "home","more","weball","classcenter","classroom","students","continuousReport",
        "homeworkCenterV65","stage09Daily","weeklyScheduleV73","curriculum","bank",
        "create","exams","grading","appeals","quality","reports","support","analytics",
        "workflowHubV90","examOperationsHubV80","resultReleaseV84","appealQueueV88",
        "assessment","examprint","gradingWorkspaceV81","examActionInboxV79",
        "examStudentDrilldownV77","bulkResultReleaseV85","classReleaseReadinessV86",
        "appealClosureAuditV89","workflowHealthV92"
    ));

    private boolean isTrusted(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        return host != null && (SITE.equalsIgnoreCase(host) || ("www." + SITE).equalsIgnoreCase(host));
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        vault = new BiometricVault(this);
        // Start protected; only the foreground unlocked screen may be captured.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.rgb(246, 249, 255));
        getWindow().setNavigationBarColor(Color.rgb(248, 250, 255));
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Color.rgb(247, 249, 254));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10),dp(4),dp(10),dp(4));
        bar.setBackgroundColor(Color.WHITE);
        TextView title = new TextView(this);
        title.setText("مدرسه‌یار  •  معلم"); title.setTextColor(Color.rgb(28, 58, 106)); title.setTextSize(15);
        title.setSingleLine(true);
        bar.addView(title,new LinearLayout.LayoutParams(0,dp(50),1));
        screenLabel = title;
        fingerBtn = toolbarButton("👆");
        fingerBtn.setContentDescription("فعال‌سازی یا غیرفعال‌سازی ورود با اثر انگشت");
        fingerBtn.setOnClickListener(v -> toggleBiometric());
        bar.addView(fingerBtn);
        Button reload = toolbarButton("↻"); reload.setContentDescription("بارگذاری دوباره");
        reload.setOnClickListener(v -> { if(browser!=null)browser.reload(); toast("در حال همگام‌سازی با سرور مدرسه‌یار"); }); bar.addView(reload);
        Button print = toolbarButton("⎙"); print.setContentDescription("چاپ صفحه فعلی و ذخیره PDF");
        print.setOnClickListener(v -> printCurrentPage()); bar.addView(print);
        logoutNative=toolbarButton("خروج");
        logoutNative.setContentDescription("خروج امن از حساب معلم");
        logoutNative.setVisibility(View.GONE);
        logoutNative.setOnClickListener(v -> confirmTeacherLogout());
        bar.addView(logoutNative);
        backBtn=toolbarButton("→"); backBtn.setContentDescription("بازگشت");
        backBtn.setOnClickListener(v -> back()); bar.addView(backBtn);
        forwardBtn=toolbarButton("←"); forwardBtn.setContentDescription("صفحه بعد");
        forwardBtn.setOnClickListener(v -> { if(browser.canGoForward()) browser.goForward(); }); bar.addView(forwardBtn);
        shell.addView(bar);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setProgress(0);
        shell.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        content=new FrameLayout(this);
        shell.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        bottomNav = makeBottomNav();
        shell.addView(bottomNav,new LinearLayout.LayoutParams(-1,dp(70)));
        root = new FrameLayout(this);
        root.addView(shell, new FrameLayout.LayoutParams(-1,-1));
        lockOverlay = makeLockOverlay();
        root.addView(lockOverlay, new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);

        browser = new WebView(this);
        prepareWebView(browser,false);
        content.addView(browser,new FrameLayout.LayoutParams(-1,-1));
        teacherDashboard=new WebView(this);
        teacherDashboard.setBackgroundColor(Color.rgb(247,249,254));
        teacherDashboard.getSettings().setJavaScriptEnabled(false);
        teacherDashboard.getSettings().setDomStorageEnabled(false);
        teacherDashboard.getSettings().setAllowFileAccess(false);
        teacherDashboard.getSettings().setAllowContentAccess(false);
        teacherDashboard.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        teacherDashboard.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request){
                Uri uri=request.getUrl();
                if ("madreseyar".equalsIgnoreCase(uri.getScheme()) && "route".equalsIgnoreCase(uri.getHost())) {
                    String route=uri.getLastPathSegment();
                    if (route!=null && ALLOWED_TEACHER_ROUTES.contains(route)) {
                        runOnUiThread(() -> openTeacherRoute(route));
                    }
                }
                // Never navigate the offline dashboard to a remote URL.
                return true;
            }
        });
        teacherDashboard.setVisibility(View.GONE);
        content.addView(teacherDashboard,new FrameLayout.LayoutParams(-1,-1));
        browser.setDownloadListener(this::download);
        // Never restore a previously visible WebView before biometric unlock.
        // State is intentionally not restored across activity recreation.
        if (vault.isEnabled()) {
            lockNow();
        } else {
            unlocked = true;
            lockOverlay.setVisibility(View.GONE); updateScreenCaptureProtection();
            loadHome();
        }
        updateButtons();
        offerHandler.postDelayed(offerCheck, 4000);
    }
    // A03: mobile identity (design 6) over the real /app/ backend.
    private LinearLayout makeBottomNav() {
        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        nav.setPadding(dp(3),dp(4),dp(3),dp(3));
        nav.setBackgroundColor(Color.WHITE);
        dockItems=new TextView[5];
        String[] names={"خانه","کلاس‌ها","حضور","نمرات","بیشتر"};
        String[] signs={"⌂","▦","✓","★","⋯"};
        String[] routes={"home","classcenter","classroom","continuousReport","more"};
        for(int i=0;i<5;i++){
            final String route=routes[i];
            TextView b=new TextView(this);
            b.setText(signs[i]+"\n"+names[i]);
            b.setTextSize(12);
            b.setGravity(Gravity.CENTER);
            b.setLineSpacing(0,1.1f);
            b.setOnClickListener(v -> openTeacherRoute(route));
            b.setContentDescription(names[i]);
            nav.addView(b,new LinearLayout.LayoutParams(0,-1,1));
            dockItems[i]=b;
        }
        nav.setVisibility(View.GONE);
        updateDock("home");
        return nav;
    }

    private void updateDock(String route) {
        if(dockItems==null)return;
        final String[] routes={"home","classcenter","classroom","continuousReport","more"};
        for(int i=0;i<dockItems.length;i++){
            boolean active=routes[i].equals(route);
            TextView item=dockItems[i];
            item.setTextColor(active?Color.rgb(35,99,231):Color.rgb(112,129,156));
            item.setTypeface(null,active?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
            android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
            bg.setColor(active?Color.rgb(234,242,255):Color.WHITE);
            bg.setCornerRadius(dp(13));
            item.setBackground(bg);
        }
    }

    private String readAssetText(String name) {
        try (InputStream in=getAssets().open(name);ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] chunk=new byte[4096];int n;
            while((n=in.read(chunk))>0)bytes.write(chunk,0,n);
            return bytes.toString("UTF-8");
        } catch(IOException ex){return "";}
    }

    private void syncTeacherUi() {
        if (!unlocked || lockOverlay.getVisibility()==View.VISIBLE || !browserLoaded || browser==null) return;
        Uri uri=Uri.parse(browser.getUrl()==null?"":browser.getUrl());
        if(!isTrusted(uri))return;
        String js="(function(){try{var s=JSON.parse(sessionStorage.getItem('"+SESSION_KEY+"')||'null');"
            +"return !!(s&&s.authUser&&s.authUser.role==='teacher'&&sessionStorage.getItem('"+TOKEN_KEY+"')"
            +"&&document.getElementById('appView')&&!document.getElementById('appView').classList.contains('hidden'));}catch(e){return false;}})();";
        browser.evaluateJavascript(js,result -> {
            if(browser==null||isFinishing()||isDestroyed())return;
            if("true".equals(result)){
                if(!teacherUiReady){
                    teacherUiReady=true;
                    bottomNav.setVisibility(View.VISIBLE);
                    logoutNative.setVisibility(View.VISIBLE);
                    if(backBtn!=null)backBtn.setVisibility(View.GONE);
                    if(forwardBtn!=null)forwardBtn.setVisibility(View.GONE);
                    showNativeTeacherHome(false);
                }
            } else if(teacherUiReady){
                resetNativeTeacherUi();
            }
        });
    }

    private void resetNativeTeacherUi() {
        teacherUiReady=false;
        nativeHomeVisible=false;
        fullWebMode=false;
        currentNativeSection="home";
        if(bottomNav!=null)bottomNav.setVisibility(View.GONE);
        if(logoutNative!=null)logoutNative.setVisibility(View.GONE);
        if(backBtn!=null)backBtn.setVisibility(View.VISIBLE);
        if(forwardBtn!=null)forwardBtn.setVisibility(View.VISIBLE);
        if(teacherDashboard!=null)teacherDashboard.setVisibility(View.GONE);
        if(browser!=null){browser.setVisibility(View.VISIBLE);browser.evaluateJavascript(
            "(function(){document.documentElement.classList.remove('msy-a03-native','msy-a04-native','msy-a04-fulltools');"
            +"document.getElementById('msy-a04-section-header')?.remove();"
            +"window.__MSY_A04_TEACHER_MOBILE__?.destroy?.();"
            +"delete window.__MSY_A04_TEACHER_MOBILE__;})()",null);}
        if(screenLabel!=null)screenLabel.setText("مدرسه‌یار  •  معلم");
    }

    private void showNativeTeacherHome(boolean more) {
        if(!teacherUiReady || teacherDashboard==null)return;
        nativeHomeVisible=true;
        fullWebMode=false;
        currentNativeSection=more?"more":"home";
        screenLabel.setText(more?"امکانات بیشتر":"مدرسه‌یار  •  معلم");
        teacherDashboard.loadDataWithBaseURL("https://appassets.androidplatform.net/",
            readAssetText(more?"teacher_more.html":"teacher_dashboard.html"),"text/html","UTF-8",null);
        browser.setVisibility(View.GONE);
        browser.evaluateJavascript("document.documentElement.classList.remove('msy-a04-fulltools');",null);
        teacherDashboard.setVisibility(View.VISIBLE);
        updateDock(more?"more":"home");
        updateButtons();
    }

    // A04: apply one cohesive teacher-mobile visual system to *every* existing
    // module.  No permission, route handler, data, or server APIs are replaced.
    private void skinTeacherPortal(){
        if(browser==null||!teacherUiReady)return;
        if(portalSkinScript==null){
            final String css=readAssetText("teacher_mobile_a04.css");
            final String js=readAssetText("teacher_mobile_a04.js");
            // CSS is re-applied after SPA rerenders; JS has an idempotent observer.
            portalSkinScript="(function(){try{var s=JSON.parse(sessionStorage.getItem('"+SESSION_KEY+"')||'null');"
                +"if(!s||!s.authUser||s.authUser.role!=='teacher')return;"
                +"var id='msy-a04-android-style';var st=document.getElementById(id);"
                +"if(!st){st=document.createElement('style');st.id=id;document.head.appendChild(st);}"
                +"st.textContent="+JSONObject.quote(css)+";"
                +js+"}catch(e){/* never break portal operations */}})();";
        }
        browser.evaluateJavascript(portalSkinScript,null);
    }

    private void openTeacherRoute(String route){
        if(!teacherUiReady||!ALLOWED_TEACHER_ROUTES.contains(route))return;
        if("home".equals(route)){showNativeTeacherHome(false);return;}
        if("more".equals(route)){showNativeTeacherHome(true);return;}
        if("weball".equals(route)){
            fullWebMode=true;
            nativeHomeVisible=false;
            currentNativeSection="more";
            teacherDashboard.setVisibility(View.GONE);
            browser.setVisibility(View.VISIBLE);
            screenLabel.setText("تمام امکانات پنل معلم");
            // Reveal and theme the FULL legacy teacher navigation as an extra list.
            skinTeacherPortal();
            browser.evaluateJavascript("document.documentElement.classList.add('msy-a04-fulltools');",null);
            updateDock("more");
            return;
        }
        // No invented REST endpoints or fake actions; dispatch the live portal's own controls.
        String id=JSONObject.quote(route);
        String script="(function(){try{var sess=JSON.parse(sessionStorage.getItem('"+SESSION_KEY+"')||'null');"
            +"if(!sess||!sess.authUser||sess.authUser.role!=='teacher')return 'unauthorized';"
            +"var route="+id+";"
            +"if(route==='homeworkCenterV65'){var hw=document.getElementById('p65-homework-link');"
            +"if(hw){hw.click();return 'ok';}return 'missing';}"
            +"if(route==='bank'){var qb=document.getElementById('p6312-independent-exam-link');"
            +"if(qb){qb.click();return 'ok';}return 'missing';}"
            +"var mapped={classcenter:'myTeaching',continuousReport:'gradeCenterT07',"
            +"stage09Daily:'classroom'};route=mapped[route]||route;"
            +"var tabs=document.querySelectorAll('#tabs [data-tab]');"
            +"for(var i=0;i<tabs.length;i++){if(tabs[i].getAttribute('data-tab')===route){tabs[i].click();return 'ok';}}"
            +"var sel=document.getElementById('v93TeacherCenter');"
            +"if(sel){for(var j=0;j<sel.options.length;j++){if(sel.options[j].value===route){sel.value=route;"
            +"sel.dispatchEvent(new Event('change',{bubbles:true}));return 'ok';}}}"
            +"return 'missing';}catch(e){return 'error';}})();";
        browser.evaluateJavascript(script,result -> {
            if(!teacherUiReady||isFinishing()||isDestroyed())return;
            if(!"\"ok\"".equals(result)){
                toast("این بخش در منوی نسخه فعلی پنل معلم پیدا نشد؛ از «بیشتر» ← «تمام ابزارها» استفاده کنید.");
                return;
            }
            fullWebMode=false;
            browser.evaluateJavascript("document.documentElement.classList.remove('msy-a04-fulltools');",null);
            nativeHomeVisible=false;
            currentNativeSection=route;
            teacherDashboard.setVisibility(View.GONE);
            browser.setVisibility(View.VISIBLE);
            screenLabel.setText(teacherRouteTitle(route));
            skinTeacherPortal();
            updateDock(route);
        });
    }

    private String teacherRouteTitle(String route){
        switch(route){
            case "classcenter":return "کلاس‌های من";
            case "classroom":return "حضور و غیاب";
            case "continuousReport":return "دفتر نمره و کارنامه";
            case "homeworkCenterV65":return "مرکز تکالیف";
            case "students":return "دانش‌آموزان من";
            case "create":return "ساخت آزمون";
            case "exams":return "مدیریت آزمون‌ها";
            case "grading":return "تصحیح و نمره‌دهی";
            case "bank":return "بانک سؤال";
            case "reports":return "گزارش‌های آموزشی";
            case "appeals":return "اعتراض به نمره";
            default:return "مدرسه‌یار | امکانات معلم";
        }
    }

    private void confirmTeacherLogout(){
        if(!teacherUiReady||browser==null)return;
        new AlertDialog.Builder(this).setTitle("خروج از مدرسه‌یار")
            .setMessage("از حساب معلم خارج شوید؟ برای ورود بعدی، نشست بیومتریک قدیمی هم پاک خواهد شد.")
            .setNegativeButton("انصراف",(d,w)->{})
            .setPositiveButton("خروج امن",(d,w)->{
                vault.clear();activeToken=null;
                resetNativeTeacherUi();
                browser.evaluateJavascript("(function(){var b=document.getElementById('logoutBtn');if(b){b.click();}else{sessionStorage.clear();location.replace('"+HOME+"');}})();",null);
            }).show();
    }

    private void loadHome() {
        browserLoaded = true;
        browser.loadUrl(HOME);
    }

    private View makeLockOverlay() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER);
        panel.setPadding(dp(26), dp(24), dp(26), dp(24));
        panel.setBackgroundColor(Color.rgb(13, 67, 58));
        TextView heading = new TextView(this);
        heading.setText("مدرسه‌یار معلم\nورود امن");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(25);
        heading.setGravity(android.view.Gravity.CENTER);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, dp(118)));
        TextView description = new TextView(this);
        description.setText("برای مشاهده اطلاعات دانش‌آموزان، اثر انگشت ثبت‌شده در گوشی را تأیید کنید.");
        description.setTextColor(Color.WHITE);
        description.setTextSize(15);
        description.setGravity(android.view.Gravity.CENTER);
        panel.addView(description, new LinearLayout.LayoutParams(-1, dp(95)));
        Button retry = new Button(this);
        retry.setText("ورود با اثر انگشت");
        retry.setOnClickListener(v -> beginBiometricUnlock());
        panel.addView(retry, new LinearLayout.LayoutParams(-1, dp(56)));
        Button password = new Button(this);
        password.setText("ورود با نام کاربری و رمز");
        password.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("نشست ذخیره‌شده برای اثر انگشت پاک شود و با رمز وارد شوید؟")
                .setNegativeButton("انصراف", (d, w) -> {})
                .setPositiveButton("ادامه", (d, w) -> loginFresh()).show());
        panel.addView(password, new LinearLayout.LayoutParams(-1, dp(56)));
        return panel;
    }

    /** A03-FIX01: screenshot the visible unlocked page for QA; protect locks and app switcher. */
    private void updateScreenCaptureProtection() {
        boolean canCapture = isActivityForeground && unlocked && lockOverlay != null
                && lockOverlay.getVisibility() != View.VISIBLE;
        if (canCapture) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void lockNow() {
        unlocked = false;
        if (lockOverlay != null) lockOverlay.setVisibility(View.VISIBLE);
        updateScreenCaptureProtection();
    }

    private boolean canUseStrongBiometric() {
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS) return true;
        toast("اثر انگشت یا حسگر بیومتریک قوی تنظیم نشده است. ابتدا آن را در تنظیمات گوشی فعال کنید.");
        return false;
    }

    private void askBiometric(String title, Cipher cipher, Consumer<Cipher> onAuthenticated) {
        if (biometricDialogOpen) return;
        if (!canUseStrongBiometric()) return;
        biometricDialogOpen = true;
        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        biometricDialogOpen = false;
                        Cipher verified = result.getCryptoObject() == null ? null : result.getCryptoObject().getCipher();
                        if (verified == null) {
                            toast("اعتبارسنجی رمزنگاری اثر انگشت انجام نشد.");
                            return;
                        }
                        onAuthenticated.accept(verified);
                    }
                    @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                        biometricDialogOpen = false;
                        // A cancelled prompt never grants access.
                    }
                });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("مدرسه‌یار معلم")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("انصراف")
                .build();
        prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
    }

    private interface TeacherAuthResult {
        void finished(JSONObject user, boolean unauthorized);
    }

    /** Server role + token validation is authoritative; all network work stays off UI thread. */
    private void verifyTeacherSession(String token, TeacherAuthResult callback) {
        authExecutor.execute(() -> {
            HttpURLConnection conn = null;
            JSONObject verified = null;
            boolean unauthorized = false;
            try {
                conn = (HttpURLConnection) new URL(STAFF_ME).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Cache-Control", "no-store");
                int status = conn.getResponseCode();
                if (status == 200) {
                    try (InputStream stream = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[4096]; int n;
                        while ((n = stream.read(buf)) != -1 && out.size() < 65536) out.write(buf,0,n);
                        JSONObject json = new JSONObject(out.toString("UTF-8"));
                        JSONObject user = json.optJSONObject("user");
                        if (user != null && "teacher".equals(user.optString("role"))
                                && !user.optBoolean("mustChangePassword", false)) verified = user;
                        else unauthorized = true;
                    }
                } else if (status == 401 || status == 403 || status == 428) {
                    unauthorized = true;
                }
            } catch (Exception ignored) {
                // Offline/TLS failure: keep the encrypted token, never unlock offline.
            } finally {
                if (conn != null) conn.disconnect();
            }
            JSONObject result = verified;
            boolean invalid = unauthorized;
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) callback.finished(result, invalid); });
        });
    }

    private void maybeOfferFirstBiometricSetup() {
        if (!unlocked || !browserLoaded || vault.isEnabled() || biometricDialogOpen) return;
        if (getSharedPreferences("msy_teacher_bio_onboarding", MODE_PRIVATE)
                .getBoolean("asked", false)) return;
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                != BiometricManager.BIOMETRIC_SUCCESS) return;
        browser.evaluateJavascript("(function(){try{var s=JSON.parse(sessionStorage.getItem('" + SESSION_KEY
                + "')||'null');return !!(s&&s.authUser&&s.authUser.role==='teacher'"
                + "&&sessionStorage.getItem('" + TOKEN_KEY + "'));}catch(e){return false;}})();", value -> {
            if (!"true".equals(value) || vault.isEnabled() || isFinishing() || isDestroyed()) return;
            getSharedPreferences("msy_teacher_bio_onboarding", MODE_PRIVATE)
                    .edit().putBoolean("asked", true).apply();
            new AlertDialog.Builder(this)
                    .setTitle("ورود سریع با اثر انگشت")
                    .setMessage("می‌خواهید از دفعات بعد با اثر انگشت وارد پنل معلم شوید؟ رمز مدرسه ذخیره نمی‌شود؛ نشست شما رمزگذاری و هر بار با سرور بررسی می‌شود.")
                    .setNegativeButton("فعلاً نه", (d, w) -> {})
                    .setPositiveButton("فعال‌سازی", (d, w) -> toggleBiometric())
                    .show();
        });
    }

    private void toggleBiometric() {
        if (!unlocked) return;
        if (vault.isEnabled()) {
            new AlertDialog.Builder(this).setMessage("ورود با اثر انگشت در این گوشی غیرفعال شود؟")
                    .setNegativeButton("انصراف", (d, w) -> {})
                    .setPositiveButton("غیرفعال‌سازی", (d, w) -> {
                        vault.clear();
                        activeToken = null;
                        toast("ورود با اثر انگشت غیرفعال شد.");
                    }).show();
            return;
        }
        if (!canUseStrongBiometric()) return;
        // Read only the existing staff session from the official HTTPS origin.
        // Do not save password or cache a fabricated native login.
        browser.evaluateJavascript("(function(){try{var s=JSON.parse(sessionStorage.getItem('" + SESSION_KEY
                + "')||'null');var t=sessionStorage.getItem('" + TOKEN_KEY
                + "');if(!t||!s||!s.authUser||s.authUser.role!=='teacher')return null;"
                + "return JSON.stringify({token:t,userId:s.userId});}catch(e){return null;}})();", answer -> {
            try {
                Object parsed = new JSONTokener(answer).nextValue();
                if (!(parsed instanceof String)) throw new Exception("no session");
                JSONObject obj = new JSONObject((String) parsed);
                String token = obj.optString("token", "");
                String userId = obj.optString("userId", "");
                if (token.isEmpty() || userId.isEmpty()) throw new Exception("not logged in");
                verifyTeacherSession(token, (teacher, invalid) -> {
                    if (teacher == null || !userId.equals(teacher.optString("id"))) {
                        toast("ابتدا با حساب معتبر معلم وارد شوید و رمز اولیه را تغییر دهید.");
                        return;
                    }
                    try {
                        Cipher cipher = vault.encryptionCipher();
                        askBiometric("فعال‌سازی ورود با اثر انگشت", cipher, authenticated -> {
                            try {
                                vault.saveAfterBiometric(authenticated, token);
                                activeToken = token;
                                toast("ورود با اثر انگشت فعال شد.");
                            } catch (Exception e) {
                                vault.clear();
                                toast("ذخیره امن نشست انجام نشد.");
                            }
                        });
                    } catch (Exception e) {
                        vault.clear();
                        toast("حسگر یا حافظه امن دستگاه در دسترس نیست.");
                    }
                });
            } catch (Exception e) {
                toast("ابتدا از مسیر رسمی با حساب معلم وارد سامانه شوید.");
            }
        });
    }

    private void beginBiometricUnlock() {
        if (unlocked || biometricDialogOpen || !vault.isEnabled()) return;
        try {
            Cipher cipher = vault.decryptionCipher();
            askBiometric("ورود با اثر انگشت", cipher, verifiedCipher -> {
                try {
                    String token = vault.readAfterBiometric(verifiedCipher);
                    if (token.isEmpty()) throw new Exception("empty token");
                    verifyTeacherSession(token, (teacher, unauthorized) -> {
                        if (teacher == null) {
                            if (unauthorized) {
                                toast("نشست معلم منقضی یا لغو شده است؛ ورود با رمز لازم است.");
                                loginFresh();
                            } else {
                                toast("ارتباط با سرور برقرار نشد. اینترنت را بررسی و دوباره تلاش کنید.");
                            }
                            return;
                        }
                        activeToken = token;
                        unlocked = true;
                        if (!browserLoaded) {
                            restoreSessionAndLoad(token, teacher);
                        } else {
                            // On resume, reject changed/cleared web sessions rather than
                            // reusing a fingerprint binding from a different account.
                            browser.evaluateJavascript("sessionStorage.getItem('" + TOKEN_KEY + "')", value -> {
                                try {
                                    Object old = new JSONTokener(value).nextValue();
                                    if (token.equals(old)) {
                                        lockOverlay.setVisibility(View.GONE); updateScreenCaptureProtection();
                                    } else {
                                        vault.clear();
                                        lockOverlay.setVisibility(View.GONE); updateScreenCaptureProtection();
                                        toast("حساب وب تغییر کرده است؛ اثر انگشت را دوباره فعال کنید.");
                                    }
                                } catch (Exception e) {
                                    vault.clear();
                                    lockOverlay.setVisibility(View.GONE); updateScreenCaptureProtection();
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    vault.clear();
                    toast("کلید بیومتریک دیگر معتبر نیست. ورود با رمز لازم است.");
                    loginFresh();
                }
            });
        } catch (GeneralSecurityException e) {
            vault.clear();
            toast("کلید بیومتریک گوشی تغییر کرده است؛ ورود دوباره لازم است.");
            loginFresh();
        }
    }

    private void restoreSessionAndLoad(String token, JSONObject user) {
        // The server user object, not cached personal details, defines the role.
        JSONObject session = new JSONObject();
        try {
            session.put("userId", user.getString("id"));
            session.put("authUser", user);
            session.put("loginAt", System.currentTimeMillis());
        } catch (Exception e) {
            loginFresh();
            return;
        }
        String script = "(function(){if(location.pathname.indexOf('/app')!==0)return;"
                + "sessionStorage.setItem('" + TOKEN_KEY + "'," + JSONObject.quote(token) + ");"
                + "sessionStorage.setItem('" + SESSION_KEY + "'," + JSONObject.quote(session.toString()) + ");"
                + "})();";
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            // Only the official origin receives the session restoration script.
            sessionInjector = WebViewCompat.addDocumentStartJavaScript(browser, script,
                    Collections.singleton("https://scooljavanrood.ir"));
            lockOverlay.setVisibility(View.GONE); updateScreenCaptureProtection();
            browserLoaded = true;
            // The existing web source intentionally resumes an authenticated
            // teacher only on this return route, after its own server validation.
            browser.loadUrl(HOME + "?p635_return=1");
        } else {
            pendingScript = script;
            injectOnFirstPage = true;
            injectionWasApplied = false;
            browserLoaded = true;
            browser.loadUrl(HOME + "?p635_return=1");
        }
    }

    private void removeSessionInjector() {
        if (sessionInjector != null) {
            sessionInjector.remove();
            sessionInjector = null;
        }
    }

    private void loginFresh() {
        removeSessionInjector();
        vault.clear();
        activeToken = null;
        unlocked = true;
        lockOverlay.setVisibility(View.GONE); updateScreenCaptureProtection();
        resetNativeTeacherUi();
        if (browserLoaded) {
            browser.evaluateJavascript("(function(){try{sessionStorage.removeItem('" + TOKEN_KEY
                    + "');sessionStorage.removeItem('" + SESSION_KEY
                    + "');}catch(e){}location.replace('" + HOME + "');})();", result -> {});
        } else {
            loadHome();
        }
    }

    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private Button toolbarButton(String label){
        Button button=new Button(this);
        button.setText(label);button.setAllCaps(false);
        button.setTextSize(14);button.setTextColor(Color.rgb(35,96,178));
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(Color.rgb(248,251,255));
        shape.setCornerRadius(dp(12));
        shape.setStroke(dp(1),Color.rgb(227,236,249));
        button.setBackground(shape);
        button.setMinWidth(0);button.setMinimumWidth(0);
        button.setPadding(dp(7),0,dp(7),0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(42));
        lp.setMargins(dp(2),dp(2),dp(2),dp(2));
        button.setLayoutParams(lp);
        return button;
    }
    private void updateButtons(){
        if(backBtn!=null){backBtn.setEnabled((popup!=null)||browser!=null&&browser.canGoBack());backBtn.setAlpha(backBtn.isEnabled()?1f:.45f);}
        if(forwardBtn!=null){forwardBtn.setEnabled(browser!=null&&browser.canGoForward());forwardBtn.setAlpha(forwardBtn.isEnabled()?1f:.45f);}
    }
    private void prepareWebView(WebView view, boolean isPopup){
        view.setBackgroundColor(Color.WHITE);
        WebSettings cfg=view.getSettings();
        cfg.setJavaScriptEnabled(true); // Required by existing /app/ application
        cfg.setDomStorageEnabled(true);
        cfg.setAllowFileAccess(false);
        cfg.setAllowContentAccess(true); // Document picker / camera content URIs
        cfg.setAllowFileAccessFromFileURLs(false);
        cfg.setAllowUniversalAccessFromFileURLs(false);
        cfg.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        cfg.setSupportZoom(true); cfg.setBuiltInZoomControls(true);cfg.setDisplayZoomControls(false);
        cfg.setJavaScriptCanOpenWindowsAutomatically(true);
        cfg.setSupportMultipleWindows(true);
        if(Build.VERSION.SDK_INT>=26)cfg.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);
        view.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request){
                Uri uri=request.getUrl();
                if ("about".equalsIgnoreCase(uri.getScheme())||"blob".equalsIgnoreCase(uri.getScheme()))return false;
                if(isTrusted(uri))return false;
                openExternal(uri);return true;
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler handler, SslError error){
                handler.cancel(); // Never bypass invalid certificates.
                toast("ارتباط امن تأیید نشد؛ اتصال متوقف شد.");
            }
            @Override public void onPageFinished(WebView v, String url){
                if (!isPopup) {
                    // One-shot restoration: never re-inject an old token after logout.
                    if (isTrusted(Uri.parse(url))) removeSessionInjector();
                    if (injectOnFirstPage && !injectionWasApplied && isTrusted(Uri.parse(url))) {
                        injectionWasApplied = true;
                        // Fallback for older system WebView builds lacking document-start scripts.
                        // Keep the lock overlay visible until the session has been restored.
                        String inject = pendingScript;
                        if (inject != null) {
                            v.evaluateJavascript(inject, result -> v.reload());
                            return;
                        }
                    }
                    if (injectOnFirstPage && injectionWasApplied) {
                        injectOnFirstPage = false;
                        pendingScript = null;
                        lockOverlay.setVisibility(View.GONE); updateScreenCaptureProtection();
                    }
                    progress.setVisibility(View.GONE);
                    updateButtons();
                    syncTeacherUi();
                }
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest request, WebResourceError error) {
                if (!isPopup && request.isForMainFrame() && injectOnFirstPage) {
                    injectOnFirstPage = false;
                    pendingScript = null;
                    lockOverlay.setVisibility(View.VISIBLE); updateScreenCaptureProtection();
                    toast("صفحه اصلی بارگذاری نشد. اتصال اینترنت را بررسی کنید.");
                }
            }
        });
        view.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int progressValue){
                if(!isPopup){progress.setVisibility(progressValue>=100?android.view.View.GONE:android.view.View.VISIBLE);progress.setProgress(progressValue);}
            }
            @Override public boolean onShowFileChooser(WebView v, android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);
                fileCallback=callback;
                openFilePicker(params);
                return true;
            }
            @Override public boolean onCreateWindow(WebView v,boolean isDialog,boolean isUserGesture,android.os.Message resultMsg){
                // Existing print / PDF flows use window.open. Show a secondary WebView
                // with a native Print button rather than silently dropping the popup.
                if(!isUserGesture || popup!=null) return false;
                popup=new WebView(MainActivity.this);
                prepareWebView(popup,true);
                popup.setDownloadListener(MainActivity.this::download);
                FrameLayout frame=new FrameLayout(MainActivity.this);
                frame.setBackgroundColor(Color.WHITE);
                frame.addView(popup,new FrameLayout.LayoutParams(-1,-1));
                LinearLayout controls=new LinearLayout(MainActivity.this);
                controls.setBackgroundColor(Color.rgb(15,83,71));
                Button close=toolbarButton("بستن");close.setOnClickListener(x->closePopup());
                Button print=toolbarButton("چاپ / PDF");print.setOnClickListener(x->printCurrentPage());
                controls.addView(close);controls.addView(print);
                FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(50),android.view.Gravity.TOP);
                frame.addView(controls,lp);
                content.addView(frame,new FrameLayout.LayoutParams(-1,-1));
                frame.setTag("popup");
                popup.setTag(frame);
                WebView.WebViewTransport transport=(WebView.WebViewTransport)resultMsg.obj;
                transport.setWebView(popup);resultMsg.sendToTarget();updateButtons();
                return true;
            }
            @Override public void onCloseWindow(WebView window){if(window==popup)closePopup();}
        });
    }
    private void openExternal(Uri uri){
        try{
            String scheme=uri.getScheme();
            if(scheme==null || !("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)||"mailto".equalsIgnoreCase(scheme)||"tel".equalsIgnoreCase(scheme)))return;
            startActivity(new Intent(Intent.ACTION_VIEW,uri));
        }catch(ActivityNotFoundException e){toast("برنامه‌ای برای باز کردن این پیوند پیدا نشد.");}
    }
    private void closePopup(){
        if(popup==null)return;
        android.view.View frame=(android.view.View)popup.getTag();
        if(frame!=null)content.removeView(frame);
        popup.stopLoading();popup.destroy();popup=null;updateButtons();
    }
    private void printCurrentPage(){
        if(nativeHomeVisible){toast("برای چاپ، ابتدا یک بخش آموزشی را باز کنید.");return;}
        WebView selected=popup!=null?popup:browser;
        PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);
        if(pm==null){toast("چاپ روی این دستگاه پشتیبانی نمی‌شود.");return;}
        pm.print("مدرسه‌یار - سند معلم",selected.createPrintDocumentAdapter("MadreseyarTeacher"),null);
    }
    private void download(String url,String agent,String disposition,String mimeType,long length){
        try{
            Uri uri=Uri.parse(url);
            if(!isTrusted(uri)){toast("دریافت فقط از سرور رسمی مجاز است.");return;}
            String name=android.webkit.URLUtil.guessFileName(url,disposition,mimeType);
            DownloadManager.Request req=new DownloadManager.Request(uri);
            req.setTitle(name);req.setDescription("فایل مدرسه‌یار");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name);
            String cookies=CookieManager.getInstance().getCookie(url);
            if(cookies!=null)req.addRequestHeader("Cookie",cookies);
            if(agent!=null)req.addRequestHeader("User-Agent",agent);
            ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
            toast("دریافت فایل شروع شد.");
        }catch(Exception e){toast("دریافت فایل ناموفق بود: "+e.getMessage());}
    }
    private void openFilePicker(WebChromeClient.FileChooserParams params){
        try{
            Intent pick=params.createIntent();
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            String[] types=params.getAcceptTypes();
            boolean acceptPhoto=types.length==0;
            for(String type:types)if(type!=null&&(type.startsWith("image/")||type.isBlank()))acceptPhoto=true;
            Intent chooser=Intent.createChooser(pick,"انتخاب فایل برای مدرسه‌یار");
            cameraUri=null;
            if(acceptPhoto){
                Intent camera=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if(camera.resolveActivity(getPackageManager())!=null){
                    File dir=new File(getCacheDir(),"camera");if(!dir.exists())dir.mkdirs();
                    File file=File.createTempFile("teacher_",".jpg",dir);
                    cameraUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",file);
                    camera.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);
                    camera.setClipData(ClipData.newUri(getContentResolver(),"photo",cameraUri));
                    camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,new Intent[]{camera});
                }
            }
            startActivityForResult(chooser,FILE_REQUEST);
        }catch(Exception e){
            if(fileCallback!=null){fileCallback.onReceiveValue(null);fileCallback=null;}
            toast("باز کردن انتخاب‌گر فایل ناموفق بود.");
        }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=FILE_REQUEST)return;
        Uri[] output=null;
        if(resultCode==Activity.RESULT_OK){
            if(data!=null){output=WebChromeClient.FileChooserParams.parseResult(resultCode,data);}
            else if(cameraUri!=null){output=new Uri[]{cameraUri};}
            if((output==null||output.length==0)&&cameraUri!=null)output=new Uri[]{cameraUri};
        }
        if(fileCallback!=null){fileCallback.onReceiveValue(output);fileCallback=null;}
        cameraUri=null;
    }
    private void back(){
        if (lockOverlay.getVisibility() == View.VISIBLE) { finish(); return; }
        if (teacherUiReady && nativeHomeVisible) {
            if ("more".equals(currentNativeSection)) { showNativeTeacherHome(false); return; }
            new AlertDialog.Builder(this).setMessage("از اپلیکیشن معلم خارج شوید؟")
                .setNegativeButton("انصراف",(x,y)->{})
                .setPositiveButton("خروج از برنامه",(x,y)->finish()).show();
            return;
        }
        if (teacherUiReady && !fullWebMode && popup==null) {
            showNativeTeacherHome(false);
            return;
        }
        if (teacherUiReady && fullWebMode && popup==null) {
            fullWebMode=false;
            showNativeTeacherHome(false);
            return;
        }
        if(popup!=null){closePopup();return;}
        if(browser.canGoBack()){browser.goBack();updateButtons();return;}
        new AlertDialog.Builder(this).setMessage("از اپلیکیشن معلم خارج شوید؟")
           .setNegativeButton("انصراف",(x,y)->{})
           .setPositiveButton("خروج",(x,y)->finish()).show();
    }
    @Override public void onBackPressed(){back();}
    @Override protected void onResume() {
        super.onResume();
        isActivityForeground = true;
        if (vault != null && vault.isEnabled() && !unlocked && !biometricDialogOpen) {
            lockNow();
            beginBiometricUnlock();
        }
        updateScreenCaptureProtection();
    }
    @Override protected void onPause() {
        // Prevent personal data in Android recent-app previews and while backgrounded.
        isActivityForeground = false;
        updateScreenCaptureProtection();
        super.onPause();
    }
    @Override protected void onStop() {
        // Fully opaque cover before the background activity may be restored.
        if (vault != null && vault.isEnabled() && unlocked && !isChangingConfigurations()) lockNow();
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); }
    @Override protected void onDestroy(){
        removeSessionInjector();
        offerHandler.removeCallbacks(offerCheck);
        if (teacherDashboard!=null){teacherDashboard.stopLoading();teacherDashboard.destroy();teacherDashboard=null;}
        authExecutor.shutdownNow();
        if(fileCallback!=null)fileCallback.onReceiveValue(null);
        closePopup();
        if(browser!=null){browser.stopLoading();browser.destroy();browser=null;}
        super.onDestroy();
    }
}
