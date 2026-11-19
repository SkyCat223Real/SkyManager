#!/bin/bash
# SkyManager 9.46 bauen: Basis = offizielle 9.43-APK, neue Klassen als classes3.dex.
# Benötigt: apktool.jar, ecj.jar, android-13/android.jar, android-14 (d8, zipalign, apksigner), dex2jar, Schlüssel.
set -e
T=/home/claude/tools; BT=$T/android-14; BASE=${BASE:-943.apk}
java -jar $T/apktool.jar d -f -o smali943 $BASE
$T/dex-tools-v2.4/d2j-dex2jar.sh -f -o app943.jar $BASE
rm -rf b946 && cp -r smali943 b946
D=b946/smali_classes2/de/skycat223/screentime
sed 's#Lde/skycat223/screentime/NewTools;#Lde/skycat223/screentime/NewToolsBase;#g' $D/NewTools.smali > $D/NewToolsBase.smali && rm $D/NewTools.smali
cp -r res-add/* b946/res/
python3 patch_manifest.py b946
java -jar $T/apktool.jar b -o unsigned.apk b946
E="java -jar $T/ecj.jar -1.8 -nowarn -proc:none -bootclasspath $T/android-13/android.jar"
rm -rf cls stubcls && mkdir cls stubcls
$E -cp app943.jar -d stubcls stub/de/skycat223/screentime/NewToolsBase.java
$E -cp stubcls:app943.jar -d cls $(find src -name "*.java")
rm -rf dexout && mkdir dexout && $BT/d8 --release --min-api 29 --lib $T/android-13/android.jar --classpath app943.jar --classpath stubcls --output dexout $(find cls -name "*.class")
cp unsigned.apk tmp.apk && cp dexout/classes.dex classes3.dex && zip -q -X tmp.apk classes3.dex
$BT/zipalign -f -p 4 tmp.apk aligned.apk
read -s -p "Schlüssel-Passwort: " KP; echo
$BT/apksigner sign --ks skymanager.jks --ks-key-alias skymanager --ks-pass "pass:$KP" --key-pass "pass:$KP" --min-sdk-version 21 --out SkyManager.apk aligned.apk
$BT/apksigner verify --print-certs SkyManager.apk | grep "SHA-256"
