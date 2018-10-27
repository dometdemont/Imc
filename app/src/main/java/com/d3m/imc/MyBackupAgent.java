package com.d3m.imc;
import android.content.Context;
import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.app.backup.FileBackupHelper;

/**
 * Created by domin on 02/10/2016.
 * Gratitude to http://www.tutorialspoint.com/android/android_data_backup.htm
 */
class DbBackupHelper extends FileBackupHelper {

    public DbBackupHelper(Context ctx, String dbName) {
        super(ctx, dbName);
    }
}

public class MyBackupAgent extends BackupAgentHelper {
    static final String File_Name_Of_Prefrences = "myPrefrences";
    static final String PREFS_BACKUP_KEY = "backupPref";
    static final String STATS_BACKUP_KEY = "backupDB";

    @Override
    public void onCreate() {
        SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this,
                File_Name_Of_Prefrences);
        addHelper(PREFS_BACKUP_KEY, helper);
        addHelper(STATS_BACKUP_KEY, new DbBackupHelper(this, imcDatabaseHelper.DATABASE_NAME));
    }
}
