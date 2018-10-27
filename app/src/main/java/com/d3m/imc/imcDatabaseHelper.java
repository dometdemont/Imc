package com.d3m.imc;

import android.app.backup.BackupManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import com.d3m.imc.MyBackupAgent;

public class imcDatabaseHelper extends SQLiteOpenHelper {
    // Database schema
    public static String DATABASE_NAME= "imc2016";
    public static int DATABASE_VERSION = 2;
    // Tables names
    static String TABLE_STEPS="userSteps";
    static String TABLE_USERS="imcUsers";
    static String TABLE_IMC="imcData";
    // Columns names
    static String COL_USER="user";
    static String COL_USERID="userId";
    static String COL_DATE="date";
    static String COL_SIZE="size";
    static String COL_WEIGHT="weight";
    static String COL_AT="at";
    static String COL_STEPS="steps";
    static String COL_SINCE="since";

    private Context applicationContext;
    private BackupManager theBackupMgr;

    public String databasePath = "";

    // Store the date in database with a simple format
    SimpleDateFormat dbDateFormatter = new SimpleDateFormat("dd/MM/yyyy");
    SimpleDateFormat displayDateTimeFormatter = new SimpleDateFormat("ddMMMyyyy HH:mm:ss");
    NumberFormat integerFormatter = new DecimalFormat("#");

    // Fake unused public constructor for signed application build
    public imcDatabaseHelper() {

        super(null, "none", null, DATABASE_VERSION);
        // TODO Auto-generated constructor stub
    }

    public imcDatabaseHelper(Context context, String dbFile) {

        super(context, dbFile, null, DATABASE_VERSION);
        DATABASE_NAME = dbFile;
        databasePath = context.getDatabasePath(DATABASE_NAME).getPath().toString();
        try {
            theBackupMgr = new BackupManager(context);
        }catch (NoClassDefFoundError e){
            theBackupMgr = null;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // tables creation
        // table holding the users names
        db.execSQL(
                "CREATE TABLE "+TABLE_USERS+" (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COL_USER + " TEXT);");
        // table holding user's weight and size at a given date
        db.execSQL(
                "CREATE TABLE "+TABLE_IMC+" (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COL_USERID + " INTEGER, "+ COL_DATE +" DATE, "+ COL_SIZE +" REAL, "+ COL_WEIGHT +" REAL);");

        // Starting with version 2
        // table holding the cumulated user's steps at a given date since the last reboot
        db.execSQL(
                "CREATE TABLE "+TABLE_STEPS+" (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        +COL_STEPS+" INTEGER, "+COL_AT+" DATE, "+COL_SINCE+" DATE);");

    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int fromVersion, int toVersion) {
        if(fromVersion == 1 && toVersion == 2){
            // new in version 2: user's steps
            // Remove existing table if any defined in version 1
            db.execSQL(
                    "DROP TABLE IF EXISTS "+TABLE_STEPS);
            db.execSQL(
                    "CREATE TABLE "+TABLE_STEPS+" (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            +COL_STEPS+" INTEGER, "+COL_AT+" DATE, "+COL_SINCE+" DATE);");
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {}

    public class usersAndIds {
        public String[] user;
        public String[] userId;
        public usersAndIds(int nb){
            user = new String[nb];
            userId = new String[nb];
        }
        // get the position of a specific userId, or -1 if not found
        public int getPosition(String aUserId){
            for(int i = 0; i < userId.length; i++)
                if(aUserId.equals(userId[i]))
                    return i;
            return -1;
        }
        // get the userId, or null if not found, from a specific user name
        public String getUserId(String aUserName){
            for(int i = 0; i < userId.length; i++)
                if(aUserName.equals(user[i]))
                    return userId[i];
            return null;
        }
    }

    public class userItem implements Comparable<userData>{
        public Date date;
        public long _id;
        public int compareTo(userData n) {
            return date.compareTo(n.date);
        }
    }

    public class userData extends userItem {
        public double size;
        public double weight;
        public userData(Date aDate, double aSize, double aWeight, long anId){
            size = aSize; weight = aWeight; date = aDate; _id = anId;
        }
        public double doImc(){
            return weight/size/size;
        }
    }

    public class userStep extends userItem {
        long nbSteps;
        long elapsed;
        boolean newStart;   // this step is the first recorded since a device reboot
        public userStep(long someSteps, Date atSomeDate, long someElapsed, long theId){
            nbSteps = someSteps;
            date = atSomeDate;
            elapsed = someElapsed;
            _id=theId;
            newStart = false;
        }
        public userStep(long someSteps, Date atSomeDate, long someElapsed, boolean aNewStart){
            nbSteps = someSteps;
            date = atSomeDate;
            elapsed = someElapsed;
            _id=0;
            newStart = aNewStart;
        }
    }

    public class screening {
        // select a subset of userData using date criteria
        // the start and end dates for screening
        Date begin, end;
        // The computed screening as indexes in a userData table
        int iBegin, iEnd;
        // The computed screening as percentage of the whole history
        long pBegin, pEnd;
        // the oldest and newest dates in ms
        public long newest, oldest;
        public screening(userItem[] entry){
            begin = end = null;
            iBegin = iEnd = 0;
            pBegin = 0; pEnd = 100;
            newest = oldest = 0;
            set(entry, null, null); // Default screening 100%
        }
        public int getBegin(){return iBegin;}
        public int getEnd(){return iEnd;}
        public Date getDate(int p){
            // estimated date for p% in the full history range
            // p is a percentage of the elapsed number of days since the begining of history
            long resultMs = oldest + p*(newest - oldest)/100;
            return new Date(resultMs);
        }
        // Screen the data passed as parameter between start and stop dates
        // Return the number of entries selected while screening
        public int set(userItem[] entry, Date start, Date stop){
            begin = null;
            end = null;
            iBegin = iEnd = 0;

            // Calculate iBegin, iEnd from the current entries
            pBegin = 0; pEnd = 100;
            {
                int i = entry.length;
                while(i-- != 0){
                    // Searching for upper boundary
                    if(iEnd == 0 && (stop == null || !entry[i].date.after(stop))){
                        iEnd = i;
                        end = entry[i].date;
                        if(newest != oldest)pEnd =  100*(entry[i].date.getTime() - oldest)/(newest - oldest);

                        if(start == null)
                            break;
                    }
                    // Searching for lower boundary
                    if(entry[i].date.before(start)){
                        iBegin = i;
                        begin = entry[i].date;
                        if(newest != oldest)pBegin =  100*(entry[i].date.getTime() - oldest)/(newest - oldest);
                        break;
                    }
                }
            }

            // update newest and oldest according to the screened data
            int screenedSize = Math.min(iEnd - iBegin + 1, entry.length);
            if(screenedSize > 0){
                newest = entry[iEnd].date.getTime();
                oldest = entry[iBegin].date.getTime();
            }
            return screenedSize;
        }

        // Copy the original screened data to the screened array passed as parameter and return the same
        public userItem[] get(userItem[] entry, userItem[] screendEntry){
            // restrict the entries to the selected [iBegin iEnd] range
            int i = iEnd;
            int j = screendEntry.length;
            while(j-- != 0)screendEntry[j] = entry[i--];
            return screendEntry;
        }
    }

    public class userDatas {
        public userData[] entry;
        public userData minEntry;	// Minimum weight entry
        public userData maxEntry;	// Maximum weight entry
        public double averageWeight;	// Average weight
        public double minImc;
        public double maxImc;
        public double minWeight;
        public double maxWeight;
        public double minSize;
        public double maxSize;
        long oldestDate;
        long lastDate;
        public screening screener;

        public userDatas(String userId){
            SQLiteDatabase db = getReadableDatabase();
            String[] whereArgs = {userId != null ? userId : "null"};
            String[] columns = {COL_DATE, COL_SIZE, COL_WEIGHT, "_id"};
            Cursor result = db.query(TABLE_IMC, columns, COL_USERID+"=?", whereArgs, null, null, null);

            entry = new userData[result.getCount()];
            int i = 0;
            averageWeight = 0.0;
            minImc = minWeight = minSize = Double.MAX_VALUE;
            maxImc = maxWeight = maxSize = (double) 0.0;
            while(result.moveToNext()){
                Date recordDate = null;
                try{
                    recordDate = dbDateFormatter.parse(result.getString(0));

                } catch (java.text.ParseException e) {
                    // Failed at getting date: use the current date as a fallback
                    recordDate = Calendar.getInstance().getTime();
                    e.printStackTrace();
                }
                entry[i] = new userData(
                        recordDate,
                        Float.valueOf(result.getString(1)),
                        Float.valueOf(result.getString(2)),
                        Long.valueOf(result.getString(3)));
                averageWeight += entry[i].weight;
                double imc = entry[i].doImc();
                minImc = Math.min(imc, minImc);
                maxImc = Math.max(imc, maxImc);
                minWeight = Math.min(entry[i].weight, minWeight);
                maxWeight = Math.max(entry[i].weight, maxWeight);
                minSize = Math.min(entry[i].size, minSize);
                maxSize = Math.max(entry[i].size, maxSize);
                i++;
            };
            if(i != 0)
                averageWeight /= i;
            db.close();

            // Sort in chronological order
            List<userData> entries = Arrays.asList(entry);
            Collections.sort(entries);

            screener = new screening(entry);
            oldestDate = screener.oldest;
            lastDate = screener.newest;
        }

        public int length() {
            return entry != null ? entry.length : 0;
        }

        public String[] items(Context theApplicationContext, SimpleDateFormat displayDateFormatter, DecimalFormat df) {
            applicationContext = theApplicationContext;
            // Result defaults to a single void string
            String[] result = {""};
            if(length() != 0){
                result = new String[length()];
                for(int i = length(); i-- != 0;){
                    result[i] = applicationContext.getString(R.string.BMIentry,
                            entry[i].date != null ? displayDateFormatter.format(entry[i].date) + ": " : "",
                            df.format(entry[i].weight), df.format(entry[i].size),
                            df.format(entry[i].weight / entry[i].size / entry[i].size));
                }
            }
            return result;
        }

        public Number[] weightEntries() {
            // Default to one single null entry
            Number[] result = {0};

            if(length() != 0){
                result = new Number[length()];
                for(int i = length(); i-- != 0;){
                    result[i] = entry[i].weight;
                }
            }
            return result;
        }

        public Number[] sizeEntries() {
            // Default to one single null entry
            Number[] result = {0};

            if(length() != 0){
                result = new Number[length()];
                for(int i = length(); i-- != 0;){
                    result[i] = entry[i].size;
                }
            }
            return result;
        }

        public Number[] imcEntries() {
            // Default to one single null entry
            Number[] result = {0};

            if(length() != 0){
                result = new Number[length()];
                for(int i = length(); i-- != 0;){
                    result[i] = entry[i].doImc();
                }
            }
            return result;
        }

        // Build the list of days elapsed for each entry compared to the oldest one
        public Number[] daysEntries() {
            // Default to one single null entry
            Number[] result = {0};
            long day0 = oldestDate;

            if(length() != 0){
                result = new Number[length()];
                for(int i = length(); i-- != 0;){
                    result[i] = (entry[i].date.getTime() - day0)/1000/60/60/24;
                }
            }
            return result;
        }

        // Build the list of dates
        public Number[] datesEntries() {
            // Default to one single current date entry
            Number[] result = {0};
            if(length() != 0){
                result = new Number[length()];
                for(int i = length(); i-- != 0;){
                    result[i] = entry[i].date.getTime();
                }
            }
            return result;
        }
    }

    public userDatas getUserData(String userId, int historyDepth){
        userDatas result = new userDatas(userId);
        Date start = null;
        if(historyDepth != 0){
            Calendar now = Calendar.getInstance();
            now.add(Calendar.MONTH, -historyDepth);
            start = new Date(now.getTimeInMillis());
        }
        userData[] screenedEntry = new userData[result.screener.set(result.entry, start, null)];
        result.screener.get(result.entry, screenedEntry);
        result.entry = screenedEntry;
        return result;
    }

    public userDatas getUserData(String userId, Date start, Date end){
        userDatas result = new userDatas(userId);
        userData[] screenedEntry = new userData[result.screener.set(result.entry, start, end)];
        result.screener.get(result.entry, screenedEntry);
        result.entry = screenedEntry;
        return result;
    }

    public userDatas getUserData(String userId, int pStart, int pEnd){
        userDatas result = new userDatas(userId);
        Date start = null;
        Date end = null;
        if(result.oldestDate != 0 && result.lastDate != 0){
            long oldest = result.oldestDate;
            long newest = result.lastDate;
            start = new Date(oldest + pStart*(newest - oldest)/100);
            end = new Date(oldest + pEnd*(newest - oldest)/100);
        }
        userData[] screenedEntry = new userData[result.screener.set(result.entry, start, end)];
        result.screener.get(result.entry, screenedEntry);
        result.entry = screenedEntry;
        return result;
    }

    // Get the list of known users and their respective userId
    public usersAndIds getUsers(){
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {"_id", COL_USER};
        Cursor result = db.query(TABLE_USERS, columns, null, null, null, null, null);

        usersAndIds usersList = new usersAndIds(result.getCount());
        int i = 0;
        while(result.moveToNext()){
            usersList.userId[i] = result.getString(0);
            usersList.user[i] = result.getString(1);
            i++;
        };
        db.close();
        return usersList;
    }

    // Add a new user in the database
    // Returns the row id or -1 if ever an error occurred
    public long addUser(String userName){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_USER, userName);
        long result = db.insert(TABLE_USERS, null, cv);
        db.close(); if(theBackupMgr != null)theBackupMgr.dataChanged();
        return result;
    }

    // Modify a user in the database
    // Returns the row id or -1 if ever an error occurred
    public long updateUser(String userId, String userName){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_USER, userName);
        String[] whereArgs = {userId != null ? userId : "null"};
        long result = db.update(TABLE_USERS, cv, "_Id=?", whereArgs);
        db.close(); if(theBackupMgr != null)theBackupMgr.dataChanged();
        return result;
    }

    // Add new user data in the database
    // Returns the row id or -1 if ever an error occurred
    public long addUserData(String userId, double size, double weight, Date date){
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues cv = new ContentValues();
        Calendar calendarDate = Calendar.getInstance();
        calendarDate.setTime(date);
        cv.put(COL_USERID, userId != null ? userId : "null");
        cv.put(COL_DATE, dbDateFormatter.format(calendarDate.getTime()));
        cv.put(COL_SIZE, size);
        cv.put(COL_WEIGHT, weight);
        long result = db.insert(TABLE_IMC, null, cv);
        db.close(); if(theBackupMgr != null)theBackupMgr.dataChanged();
        return result;
    }

    // Add new user steps in the database
    // Returns the row id or -1 if ever an error occurred
    public long addUserStep(long steps, Date at, long elapsed){
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put(COL_STEPS, steps);
        cv.put(COL_AT, at.getTime());
        cv.put(COL_SINCE, elapsed);
        long result = db.insert(TABLE_STEPS, null, cv);
        db.close(); if(theBackupMgr != null)theBackupMgr.dataChanged();
        return result;
    }

    // Get the last user steps recorded in the table
    public userStep getLastUserStep(){
        SQLiteDatabase db = getReadableDatabase();
        userStep lastUserStep = null;
        String[] columns = {COL_STEPS, COL_AT, COL_SINCE, "_id"};
        Cursor result = db.query(TABLE_STEPS, columns, null, null, null, null, null);
        if(result.moveToLast())
            lastUserStep = new userStep(result.getLong(0), new Date(result.getLong(1)), result.getLong(2), result.getLong(3));
        db.close();
        return lastUserStep;
    }

    // Get all user steps recorded in the table
    public userSteps getUserSteps(int pStart, int pEnd){
        userSteps result = new userSteps();
        Date start = null;
        Date end = null;
        if(result.oldestDate != 0 && result.lastDate != 0){
            long oldest = result.oldestDate;
            long newest = result.lastDate;
            start = new Date(oldest + pStart*(newest - oldest)/100);
            end = new Date(oldest + pEnd*(newest - oldest)/100);
        }
        userStep[] screenedEntry = new userStep[result.screener.set(result.entry, start, end)];
        result.screener.get(result.entry, screenedEntry);
        result.entry = screenedEntry;
        return result;
    }

    // Create and return a new user step
    public userStep getUserStep(long someSteps, Date atSomeDate, long someElapsed, long theId){
        return new userStep(someSteps, atSomeDate, someElapsed, theId);
    }

    public class userSteps {
        public userStep[] entry;
        long maxSteps;
        long oldestDate;
        long lastDate;
        public screening screener;
        public userSteps(){
            maxSteps = 0;
            SQLiteDatabase db = getReadableDatabase();
            String[] columns = {COL_STEPS, COL_AT, COL_SINCE, "_id"};
            Cursor result = db.query(TABLE_STEPS, columns, null, null, null, null, null);

            entry = new userStep[result.getCount()];
            int i = 0;
            while(result.moveToNext()){
                Date recordDate = new Date(result.getLong(1));
                entry[i] = new userStep(result.getLong(0), recordDate, result.getLong(2), result.getLong(3));
                i++;
            };
            db.close();

            screener = new screening(entry);
            oldestDate = screener.oldest;
            lastDate = screener.newest;
        }

        public int length() {
            return entry != null ? entry.length : 0;
        }

        public String[] items(Context theApplicationContext, SimpleDateFormat displayDateFormatter, DecimalFormat df) {
            applicationContext = theApplicationContext;
            // Result defaults to a single void string
            String[] result = {""};
            if(length() != 0){
                result = new String[length()];
                for(int i = length(); i-- != 0;){
                    result[i] = displayUserStep(theApplicationContext, entry[i].nbSteps, entry[i].date, entry[i].elapsed, R.string.stepsDelta);
                }
            }
            return result;
        }

        public Number[] stepsEntries() {
            // Default to one single null entry
            Number[] result = {0};
            maxSteps = 0;
            // Start with at least 2 steps counts, need 2 counts to compute the incremental value
            if(length() > 1){
                result = new Number[length()];
                for(int i = length(); i-- != 1;){
                    userStep e = getIncrementalSteps(i, null);
                    result[i] = e.nbSteps;
                    maxSteps = Math.max(maxSteps, e.nbSteps);
                }
            }
            return result;
        }
        public Number[] datesEntries() {
            // Default to one single current date entry
            Number[] result = {0};
            if(length() != 0){
                result = new Number[length()];
                for(int i = length(); i-- != 0;){
                    result[i] = entry[i].date.getTime();
                }
            }
            return result;
        }

        // Get the steps count, date and elapsed of the item passed as parameter compared to the previous item in the entries
        // The item to analyze can be passed as its position in the list of entries or as an explicit userStep object
        // In the latter case, the "position" parameter is the reference entry, and defaults to the last record if unspecified (> max)
        public userStep getIncrementalSteps(int position, userStep current){
            // if no history at all, no result
            if(entry.length == 0)return null;

            userStep previous = null;

            // default unsupported positions to the latest
            if(position < 0 || position >= length())position = length();
            if( current == null ){
                current = entry[position];
                if( position > 0 )previous = entry[position-1];
            }else{
                if(position >= length())
                    previous = getLastUserStep();
                else
                    previous = entry[position];
            }

            long previousStep = 0;
            long previousElapsed = 0;
            Date previousAt = new Date(0);
            if(previous != null){
                previousStep = previous.nbSteps;
                previousElapsed = previous.elapsed;
                previousAt = previous.date;
            }
            long currentStep = current.nbSteps;
            long currentElapsed = current.elapsed;
            Date currentAt = current.date;

            boolean newStart = false;
            if(currentElapsed > previousElapsed && currentStep > previousStep){
                currentElapsed -= previousElapsed;
                currentStep -= previousStep;
            } else {
                // the device might have been restarted between previous and current
                // however, consistency check regarding the current elapsed time
                if( currentElapsed >= (currentAt.getTime() - previousAt.getTime())){
                    currentStep = 0;
                    currentElapsed = 1;
                }else {
                    newStart = true;
                }
            }
            return new userStep(currentStep, currentAt, currentElapsed, newStart);
        }
    }

    public String displayUserStep(Context theApplicationContext, long someSteps, Date atSomeDate, long elapsedMs, int format){
        long msPerS = 1000L;
        long msPerMn = 60L * msPerS;
        long msPerHour = 60L * msPerMn;
        long msPerDay = 24L * msPerHour;

        long nbDays = elapsedMs/msPerDay;
        long nbHours = (elapsedMs - nbDays*msPerDay)/msPerHour;
        long nbMn = (elapsedMs - nbDays*msPerDay - nbHours*msPerHour)/msPerMn;
        return theApplicationContext.getString(format, integerFormatter.format(someSteps), nbDays, nbHours, nbMn,
                integerFormatter.format(someSteps*msPerDay/elapsedMs),
                displayDateTimeFormatter.format(atSomeDate)) ;
    }

    // Remove a user from the database
    // Returns the number of deleted rows
    public long removeUser(String userId){
        SQLiteDatabase db = this.getWritableDatabase();
        String[] whereArgs = {userId != null ? userId : "null"};
        long result = db.delete(TABLE_USERS, "_Id=?", whereArgs);
        db.close(); if(theBackupMgr != null)theBackupMgr.dataChanged();
        return result;
    }

    // Remove user data from the database
    // Remove all data related to this user, or just the _id provided if >= 0
    // Returns the number of deleted rows
    public long removeUserData(String userId, long _id){
        SQLiteDatabase db = this.getWritableDatabase();
        String whereClause = COL_USERID+"=?";
        String[] whereArgs = {userId != null ? userId : "null"};
        if(_id >= 0){
            whereClause += " AND _id=?";
            whereArgs =  new String []{whereArgs[0], Long.toString(_id)};
        }

        long result = db.delete(TABLE_IMC, whereClause, whereArgs);
        db.close(); if(theBackupMgr != null)theBackupMgr.dataChanged();
        return result;
    }

    // Remove a user step entry from the TABLE_STEPS database
    // Returns the number of deleted rows
    public long removeUserStepData(long _id){
        SQLiteDatabase db = this.getWritableDatabase();
        String whereClause = "_id=?";
        String[] whereArgs = {Long.toString(_id)};
        long result = db.delete(TABLE_STEPS, whereClause, whereArgs);
        db.close(); if(theBackupMgr != null)theBackupMgr.dataChanged();
        return result;
    }

    // backup database
    // Returns an error message or null if successful
    public String backup(String backupFileName) throws IOException {
        String result = null;
        File f=new File(databasePath);
        f.createNewFile();
        FileInputStream fis=null;
        FileOutputStream fos=null;

        try
        {
            fis=new FileInputStream(f);
            fos=new FileOutputStream(backupFileName);
            while(true)
            {
                int i=fis.read();
                if(i!=-1)
                {fos.write(i);}
                else
                {break;}
            }
            fos.flush();
            return result;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            result = e.toString();
        }
        finally
        {
            try
            {
                if(fos != null)fos.close();
                if(fis != null)fis.close();
            }
            catch(IOException ioe)
            {}
        }
        return result;
    }

    // Restore database from a backup file
    // Returns an error message or null if successful
    public String restore(String backupFileName){
        String result = null;
        File f=new File(databasePath);
        FileInputStream fis=null;
        FileOutputStream fos=null;

        try
        {
            fis=new FileInputStream(backupFileName);
            fos=new FileOutputStream(f);
            while(true)
            {
                int i=fis.read();
                if(i!=-1)
                {fos.write(i);}
                else
                {break;}
            }
            fos.flush();
            return result;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            result = e.toString();
        }
        finally
        {
            try
            {
                if(fos != null)fos.close();
                if(fis != null)fis.close();
            }
            catch(IOException ioe)
            {}
        }
        return result;
    }

}
