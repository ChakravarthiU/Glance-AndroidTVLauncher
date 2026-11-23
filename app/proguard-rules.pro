# Release shrinking rules for TV Home V2. Activities named in the manifest are kept automatically.

# Lottie reads banner files with okio, which refers to the optional javax.annotation.Nullable (not on Android).
-dontwarn javax.annotation.**
