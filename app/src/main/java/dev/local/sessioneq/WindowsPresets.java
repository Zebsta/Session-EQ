package dev.local.sessioneq;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

/** Read-only presets; never replaces the screenshot Rock or saved user settings. */
public final class WindowsPresets {
    public static final String PREFIX="AIMP Windows · ";
    private WindowsPresets() {}
    public static LinkedHashMap<String,float[]> load(Context context) throws IOException,JSONException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream input=context.getResources().openRawResource(R.raw.aimp_windows_presets)) {
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
        }
        JSONObject presets=new JSONObject(bytes.toString("UTF-8")).getJSONObject("presets");
        TreeSet<String> names=new TreeSet<>();Iterator<String> iterator=presets.keys();
        while(iterator.hasNext())names.add(iterator.next());
        LinkedHashMap<String,float[]> result=new LinkedHashMap<>();
        for(String name:names) {
            JSONArray bands=presets.getJSONArray(name);
            if(bands.length()!=20)throw new JSONException("Expected 20 bands: "+name);
            float[] gains=new float[20];
            for(int i=0;i<20;i++) {
                gains[i]=(float)bands.getDouble(i);
                if(!Float.isFinite(gains[i])||gains[i]<-15||gains[i]>15)throw new JSONException("Invalid gain: "+name);
            }
            result.put(PREFIX+name,gains);
        }
        return result;
    }
}
