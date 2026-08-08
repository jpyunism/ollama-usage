# Jsoup y OkHttp usan reflection; mantener sus keep rules.
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# WorkManager
-dontwarn androidx.work.**
