#!/usr/bin/env bash
# Verifica en el emulador que un update-check fallido NO deja la app colgada
# en el spinner de "Consultando ollama.com…".
#
# Escenario del bug original:
#   - update_last_check_ms = 0  -> shouldCheck() == true (el check corre)
#   - http_proxy = 127.0.0.1:9  -> la red falla (ECONNREFUSED)
# Con el fix: la app NO muestra CrashActivity y el spinner avanza a la UI real.
# Sin el fix: el IOException del update-check aparecia como crash.
set -u

PKG=com.jpyunism.ollamacloudusage
export ANDROID_HOME="${ANDROID_HOME:-/home/jyunis/android-sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
APK="${1:?uso: verify-spinner-fix.sh <ruta-al-apk>}"
OUT=/tmp/spinner-fix-shots
mkdir -p "$OUT"

echo "== instalando $APK =="
adb uninstall "$PKG" >/dev/null 2>&1
adb install -r "$APK" | tail -1

echo "== limpiando estado y configurando el escenario del bug =="
adb shell am force-stop "$PKG"
adb shell pm clear "$PKG" >/dev/null
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null
# Un arranque previo crea shared_prefs/ con el owner correcto.
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 4
adb shell am force-stop "$PKG"

# update_last_check_ms = 0 => el chequeo diario se dispara en el arranque.
adb shell "cat > /data/data/$PKG/shared_prefs/ollama_usage_secure_v2.xml" <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="onboarding_completed" value="true" />
    <long name="update_last_check_ms" value="0" />
</map>
EOF
OWN=$(adb shell stat -c '%u:%g' /data/data/$PKG | tr -d '\r')
adb shell "chown $OWN /data/data/$PKG/shared_prefs/*.xml; chmod 660 /data/data/$PKG/shared_prefs/*.xml"
adb shell "rm -f /data/data/$PKG/files/crash.log"

# Red inalcanzable: proxy a un puerto cerrado.
adb shell settings put global http_proxy 127.0.0.1:9

echo "== lanzando la app (check de update fallara) =="
adb logcat -c
adb shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 15

TOP=$(adb shell dumpsys activity activities 2>/dev/null | grep -oE "topResumedActivity=[^ ]*" | head -1)
echo "-- top activity: $TOP"

CRASH=$(adb shell cat /data/data/$PKG/files/crash.log 2>/dev/null | head -6)
if [ -n "$CRASH" ]; then
    echo "-- crash.log presente:"; echo "$CRASH"
else
    echo "-- crash.log vacio (no hubo excepcion sin capturar)"
fi

echo "== capturas (md5 identico => congelado; distinto => animando) =="
for i in 1 2 3 4; do
    adb shell screencap -p /sdcard/vf$i.png
    adb pull /sdcard/vf$i.png "$OUT/vf$i.png" >/dev/null
    sleep 2
done
md5sum "$OUT"/vf*.png

echo "== limpieza =="
adb shell settings put global http_proxy :0
adb shell settings delete global http_proxy >/dev/null 2>&1
adb shell am force-stop "$PKG"
echo "listo. capturas en $OUT"
