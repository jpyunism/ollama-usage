# Jsoup y OkHttp usan reflection; mantener sus keep rules.
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# WorkManager
-dontwarn androidx.work.**
# R8 (AGP 9.x) rompe la instanciación de la base de datos de WorkManager
# ("Failed to create an instance of class androidx.work.impl.WorkDatabase")
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
