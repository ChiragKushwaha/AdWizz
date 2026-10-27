# Nothing reflective in this app; defaults are sufficient for correctness.

# Smaller dex: let R8 move everything into one package and relax access modifiers.
-repackageclasses ''
-allowaccessmodification

# Release builds drop verbose/debug/info logging entirely (smaller, and no domain names in logcat).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
