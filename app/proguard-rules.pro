# Night Drive - release ProGuard/R8 rules.
# The app has no reflection-based serialization; defaults are sufficient.
# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose is handled by bundled consumer rules; nothing extra required.
