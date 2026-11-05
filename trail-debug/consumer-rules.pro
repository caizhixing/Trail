# Trail consumer ProGuard rules (trail-debug)
# internalRelease opens minification, keep the public surface needed by the caller
-keep class io.github.caizhixing.trail.Trail { public *; }
-keep class io.github.caizhixing.trail.TrailGate { public *; }
-keep class io.github.caizhixing.trail.TrailImpl { public *; }
