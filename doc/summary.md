## Summary

* How to get the package name without `context`

One day When I've used the `File.createTempFile("tmp","log")`, I feel amazing. How does this guy get the path without context?

```
//Create a temp file from File.createTempFile("tmp","log")
/data/user/0/cz.android.androidlogcattool/cache/tmp4948157153898188961txt
```

Here is the reason.

```
//ActivityThread.java
private void handleBindApplication(AppBindData data) {
    [...]
    final ContextImpl appContext = ContextImpl.createAppContext(this, data.info);
    if (!Process.isIsolated()) {
        final File cacheDir = appContext.getCacheDir();

        if (cacheDir != null) {
            // Provide a usable directory for temporary files
            System.setProperty("java.io.tmpdir", cacheDir.getAbsolutePath());
        } else {
            Log.v(TAG, "Unable to initialize \"java.io.tmpdir\" property due to missing cache directory");
        }
    }
    [...]
}
```


So we can always use the `System.getProperty("java.io.tmpdir)` to get the cache dir and the package name of the application.
