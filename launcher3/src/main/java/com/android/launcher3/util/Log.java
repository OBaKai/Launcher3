package com.android.launcher3.util;


import android.os.Environment;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by majianjiang on 16/1/1.
 */
public class Log {
    private final static String TAG = "llk";
    private final static boolean sOpenLog = true;
    private final static boolean sOpenLogToFile = false;
    private static FileWriter mFileWriter = null;

    public static void d(String logKey, String msg) {
        if (sOpenLog) {
            StackTraceElement ste = new Throwable().getStackTrace()[1];
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(logKey).append("]").append(build(msg, ste));
            String log = sb.toString();
            android.util.Log.d(TAG, log);
            if (sOpenLogToFile) {
                writeToFile(log);
            }
        }
    }

    public static void d(String msg) {
        if (sOpenLog) {
            StackTraceElement ste = new Throwable().getStackTrace()[1];
            String log = build(msg, ste);
            android.util.Log.d(TAG, log);
        }
    }

    public static void i(String logKey, String msg) {
        if (sOpenLog) {
            StackTraceElement ste = new Throwable().getStackTrace()[1];
            String log = build(msg, ste);
            android.util.Log.i(TAG, "[" + logKey + "]" + log);
        }
    }

    public static void v(String logKey, String msg) {
        if (sOpenLog) {
            StackTraceElement ste = new Throwable().getStackTrace()[1];
            String log = build(msg, ste);
            android.util.Log.v(TAG, "[" + logKey + "]" + log);
        }
    }

    public static void w(String logKey, String msg) {
        if (sOpenLog) {
            StackTraceElement ste = new Throwable().getStackTrace()[1];
            String log = build(logKey, msg, ste);
            android.util.Log.w(TAG, log);
            if (sOpenLogToFile) {
                writeToFile(log);
            }
        }
    }

    public static void e(String logKey, String msg) {
        if (sOpenLog) {
            StackTraceElement ste = new Throwable().getStackTrace()[1];
            String log = build(logKey, msg, ste);
            android.util.Log.e(TAG, log);
            if (sOpenLogToFile) {
                writeToFile(log);
            }
        }
    }

    public static void e(String logKey, String msg, Throwable e) {
        if (sOpenLog) {
            StackTraceElement ste = new Throwable().getStackTrace()[1];
            String log = build(logKey, msg, ste);
            android.util.Log.e(TAG, log, e);
            if (sOpenLogToFile) {
                writeToFile(build(logKey, msg, ste, e));
            }
        }
    }

    private static void writeToFile(String strLog) {
        if (!sOpenLogToFile) {
            return;
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
        String date = dateFormat.format(new java.util.Date());
        if (mFileWriter == null) {
            String logFilePath = Environment.getExternalStorageDirectory().toString() + "/SettingsLog-" + date + ".txt";
            try {
                mFileWriter = new FileWriter(logFilePath, true);
            } catch (IOException e) {
                android.util.Log.e(TAG, "create file writer fail", e);
            }
        }

        try {
            mFileWriter.write(date);
            mFileWriter.write(":");
            mFileWriter.write(strLog);
            mFileWriter.write("\r\n");
            mFileWriter.flush();
        } catch (IOException e) {
            android.util.Log.e(TAG, "write file fail", e);
        }
    }

    /**
     * 制作打log位置的文件名与文件行号详细信息
     *
     * @param log
     * @param ste
     * @return
     */
    private static String build(String log, StackTraceElement ste) {
        StringBuilder buf = new StringBuilder();
        if (ste.isNativeMethod()) {
            buf.append("(Native Method)");
        } else {
            String fName = ste.getFileName();

            if (fName == null) {
                buf.append("(Unknown Source)");
            } else {
                int lineNum = ste.getLineNumber();
                buf.append('(');
                buf.append(fName);
                if (lineNum >= 0) {
                    buf.append(':');
                    buf.append(lineNum);
                }
                buf.append("):");
            }
        }
        buf.append(log);
        return buf.toString();
    }

    private static String build(String logKey, String msg, StackTraceElement ste) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(logKey).append("]").append(build(msg, ste));
        return sb.toString();
    }

    private static String build(String logKey, String msg, StackTraceElement ste, Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(logKey).append("]").append(ste.toString()).append(":").append(msg).append("\r\n").append("e:").append(e.getMessage());
        return sb.toString();
    }
}

