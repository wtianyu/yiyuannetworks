package com.yiyuan.ai.common;

import android.content.Context;
import android.util.Log;

import com.yiyuan.ai.AIConstants;
import com.yiyuan.aiwinn.faceattendance.AttApp;

import java.io.File;
import java.io.IOException;

public class LogUtil {
    final static String TAG = "yiyuan";
    private static LogUtil instance;
    private static Context mContext;
    private static String logName = "ai-client-log.txt";
    private static String formData = "yyyy-MM-dd hh:mm:ss:SSS";

    private LogUtil() {
    }

    public static synchronized LogUtil getInstance() {
        return getInstance(null);
    }

    public static synchronized LogUtil getInstance(Context context) {
        if (instance == null) {
            instance = new LogUtil();
            mContext = AttApp.getContext();
        }
        return instance;
    }

    public void logE(String message) {
        record("E", message);
    }

    public void LogW(String message) {
        record("W", message);
    }


    public void record(String level, String message) {
        if ("E".equals(level)) {
            Log.e(TAG, level + ":" + message);
        } else {
            Log.w(TAG, level + ":" + message);
        }
        if (mContext != null) {
            String currentTime = DateUtil.getCurrentTime();
            File file = new File(AIConstants.AI_PATH + File.separator + logName);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            IOHelper.writerStrByCodeToFile(file, "utf-8", true, currentTime + ";" + level + ";" + message + "\n");
        }
    }

}