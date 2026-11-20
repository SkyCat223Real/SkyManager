import sys
d = sys.argv[1]
m = open(d + "/AndroidManifest.xml", encoding="utf-8").read()
if "MyDayWidget" not in m:
    rec = '''        <receiver android:exported="true" android:label="SkyManager · Mein Tag" android:name="de.skycat223.screentime.MyDayWidget">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/widget_myday_info"/>
        </receiver>
    </application>'''
    m = m.replace("    </application>", rec)
    open(d + "/AndroidManifest.xml", "w", encoding="utf-8").write(m)
y = open(d + "/apktool.yml", encoding="utf-8").read().replace("versionCode: 99", "versionCode: 108").replace("versionName: 9.43.0", "versionName: 9.52.0")
open(d + "/apktool.yml", "w", encoding="utf-8").write(y)
