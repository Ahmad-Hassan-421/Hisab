p="Hisab/app/src/main/java/com/example/hisab/ui/MainActivity.kt"
s=open(p,encoding="utf-8").read()
old="""        val savedTheme = getSharedPreferences(SPLASH_PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getString(SPLASH_THEME_KEY, null)
        val splashIsDark = when (savedTheme) {
            "dark"  -> true
            "light" -> false
            else    -> false
        }
        setTheme(if (splashIsDark) R.style.Theme_Hisab_Splash_Dark else R.style.Theme_Hisab_Splash_Light)

        super.onCreate(savedInstanceState)
        installSplashScreen()

        setContent {"""
new="""        setTheme(R.style.Theme_Hisab)

        super.onCreate(savedInstanceState)

        setContent {"""
assert old in s, "old block not found"
s=s.replace(old,new)
open(p,"w",encoding="utf-8").write(s)
print("done")
PY