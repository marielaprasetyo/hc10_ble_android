package com.example.bletest;

import android.app.Activity;
import android.os.Environment;

import java.io.File;

/**
 * Created by yu on 2016/12/15.
 */
public class MainStorage {
    /** File to record main storage path */
    private static File mainStorage = null;
    private static Activity mActivity = null;

    /**
     * Get main storage directory path. If the path does not exist, make
     * directory for it
     *
     * @return File directory path
     */

    public static void setActivity(Activity activity){
        mActivity = activity;
    }
    public static final File getMainStorageDirectory() {
        if (mainStorage == null)
            if (Environment.getExternalStorageState().equals(
                    Environment.MEDIA_MOUNTED))
                mainStorage = new File(
                        Environment.getExternalStorageDirectory(), "pressure_test");
            else
                mainStorage = new File(mActivity.getApplicationContext().getFilesDir(),
                        "ModeLogTester_2");

        if (!mainStorage.exists()) {
            mainStorage.mkdirs();
        }

        return mainStorage;
    }
}
