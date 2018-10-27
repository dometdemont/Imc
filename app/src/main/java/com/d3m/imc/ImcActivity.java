package com.d3m.imc;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import java.io.*;
import java.text.Format;
import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.*;
import com.d3m.imc.imcDatabaseHelper.userData;
import com.d3m.imc.imcDatabaseHelper.usersAndIds;
import com.d3m.imc.imcDatabaseHelper.userDatas;
import com.d3m.imc.imcDatabaseHelper.userStep;
import com.d3m.imc.imcDatabaseHelper.userSteps;
import com.d3m.imc.imcRanges.imcRange;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.SubMenu;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.SeekBar;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.SystemClock;

public class ImcActivity extends AppCompatActivity
implements AdapterView.OnItemSelectedListener,
        OnSharedPreferenceChangeListener,
        SensorEventListener
{
    TextView imcResult;
    TextView textSteps;
    ListView listHistory;
    EditText taille;
    EditText poids;
    private Spinner userName;
    private usersAndIds usersNames;
    private String ownerName, selectedName;
    private String selectedUserId;
    private long selectedDataId;
    boolean useName, showIncrementalSteps;
    int userStepsPeriod;
    private boolean storeUserData;
    private String dbFile;
    private imcDatabaseHelper dbh;		// Database holding gathered imc data
    private userDatas dbUserDatas;	// data of the current user
    private userSteps dbUserSteps;
    private imcRanges imcReference;
    private Button setDate;
    private NumberFormat integerFormatter;
    private DecimalFormat digitalFormatter;
    private SimpleDateFormat displayDateFormatter, mmmyyyyDateFormatter, elapsedTimeFormatter;
    private Calendar calendarInstance;
    private RadioGroup showWhat;
    private File folder;
    private SensorManager mSensorManager;
    private Sensor stepsSensor;
    private long userStepCount, userStepTimeStamp, userStepTimeElapsed;
    private LineAndPointFormatter lineFormat;
    private LineAndPointFormatter barFormat;
    private LineAndPointFormatter seriesFormat;

    // Menus entries
    private enum menuEntries {
        PREFERENCES(1), ADDUSER(2), REMOVEUSER(3), UPDATEUSER(4), IMCREFERENCES(5), BACKUP(6), RESTORE(7), MGMT(8);
        private final int theValue;
        menuEntries(int aValue){theValue = aValue;}
        public int value(){return theValue;}
    };

    // Adapter used to display the history with colored coded IMC range
    class HistoryAdapter extends ArrayAdapter<String> {
        HistoryAdapter(){
            super(ImcActivity.this,
                    R.layout.row, R.id.textRow, dbUserDatas.items(getApplicationContext(), displayDateFormatter, digitalFormatter));
        }

        public View getView(int position, View convertView, ViewGroup parent){
            View row = super.getView(position, convertView, parent);
            TextView imcText = (TextView)row.findViewById(R.id.textRow);
            if(dbUserDatas.entry.length != 0){
                try {
                    userData entry = dbUserDatas.entry[position];
                    double imc = entry.weight/entry.size/entry.size;
                    imcText.setTextColor(imcReference.getImcRange(imc).getColor());
                }catch (Exception e){
                    // unable to find the related entry ??
                    e.printStackTrace();
                }
            }
            return row;
        }
    }

    // Adapter used to display the history of user steps
    class userStepHistoryAdapter extends ArrayAdapter<String> {
        userStepHistoryAdapter(){
            super(ImcActivity.this,
                    R.layout.row, R.id.textRow, dbUserSteps.items(getApplicationContext(), displayDateFormatter, digitalFormatter));
        }
        public View getView(int position, View convertView, ViewGroup parent){
            View row = super.getView(position, convertView, parent);
            TextView stepText = (TextView)row.findViewById(R.id.textRow);
            try {
                getIncrementalUserSteps(position, R.string.stepsDelta, stepText);
            }catch (Exception e){
                // unable to find the related entry ??
                e.printStackTrace();
            }
            return row;
        }
    }

    // Adapter used to display the reference IMC ranges
    class imcRefAdapter extends ArrayAdapter<String> {
        imcRefAdapter(){
            super(ImcActivity.this,
                    R.layout.row, R.id.textRow, imcReference.getImcRangeLabels());
        }

        public View getView(int position, View convertView, ViewGroup parent){
            View row = super.getView(position, convertView, parent);
            TextView imcText = (TextView)row.findViewById(R.id.textRow);
            try {
                imcText.setText(imcReference.getImcRangeLabel(position));
                imcText.setTextColor(imcReference.getImcRangeColor(position));
            }catch (Exception e){
                // unable to find the related entry ??
                e.printStackTrace();
            }
            return row;
        }
    }

    // graphic objects
    private XYPlot mySimpleXYPlot;
    private XYSeries series = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tabs);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        TabHost tabs = (TabHost)findViewById(R.id.tabhost);
        tabs.setup();
        TabHost.TabSpec spec = tabs.newTabSpec("tag1");
        spec.setContent(R.id.tab1);
        spec.setIndicator(getString(R.string.calcul));
        tabs.addTab(spec);
        spec = tabs.newTabSpec("tag3");
        spec.setContent(R.id.tab3);
        spec.setIndicator(getString(R.string.graphique));
        tabs.addTab(spec);

        // Start with the compute tab
        tabs.setCurrentTab(0);

        // display the logo in the menu bar
        getSupportActionBar().setLogo(R.mipmap.ic_launcher);

        try {
            folder = new File(this.getExternalFilesDir(null) + "/");
        }catch (NoSuchMethodError e){
            // getExternalFilesDir not available in old android versions
            folder = getFilesDir();
        }
        folder.mkdirs();

        imcResult = (TextView)findViewById(R.id.textImc);
        textSteps = (TextView)findViewById(R.id.textSteps);
        textSteps.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if(userStepCount >= 0)manageUserStep(v, null);
                return true;
            }
        });

        listHistory = (ListView)findViewById(R.id.listHistory);
        listHistory.setOnItemLongClickListener(
                new OnItemLongClickListener() {
                    public boolean onItemLongClick(AdapterView<?> adapter, View view, int position, long arg) {
                        return onHistoryItemLongClick(adapter, view, position, arg);
                    }
                }
        );

        // set a black color background for the history and result, displaying white and light colored text
        listHistory.setBackgroundColor(Color.BLACK);
        listHistory.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        imcResult.setBackgroundColor(Color.BLACK);
        textSteps.setBackgroundColor(Color.WHITE);
        textSteps.setTextColor(android.graphics.Color.BLUE);

        // Preferred and common formatters
        integerFormatter = new DecimalFormat("#");
        displayDateFormatter = new SimpleDateFormat("ddMMMyyyy");
        mmmyyyyDateFormatter = new SimpleDateFormat("MMMyyyy");
        elapsedTimeFormatter = new SimpleDateFormat("HH:mm:ss");
        digitalFormatter = new DecimalFormat("##.##");
        setDate = (Button)findViewById(R.id.setDate);
        // display the current date on the setDate button
        calendarInstance = Calendar.getInstance();
        setDate.setText(displayDateFormatter.format(calendarInstance.getTime()));

        taille = (EditText)findViewById(R.id.editTaille);
        poids = (EditText)findViewById(R.id.editPoids);
        userName = (Spinner)findViewById(R.id.userName);

        // display weight by default
        showWhat = (RadioGroup)findViewById(R.id.showWhat);
        showWhat.check(R.id.showWeight);

        // graph formatters
        lineFormat = new LineAndPointFormatter(
                Color.GREEN,   // line color
                Color.LTGRAY,   // point color
                null, null);    // no fill
        barFormat = new BarFormatter(
                Color.GREEN,    // fill
                Color.LTGRAY);  // border
        // Default to line
        seriesFormat = lineFormat;

        imcReference = new imcRanges(getApplicationContext());

        onSharedPreferenceChanged(PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext()), "*");

        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).registerOnSharedPreferenceChangeListener(this);

        // history control: start, end, depth
        SeekBar historyStartBar = (SeekBar) findViewById(R.id.historyStartBar);
        historyStartBar.setProgress(0);
        SeekBar historyEndBar = (SeekBar) findViewById(R.id.historyEndBar);
        historyEndBar.setProgress(100);

        historyStartBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar historyStartBar, int progress, boolean fromUser) {
                TextView historyStartText = (TextView) findViewById(R.id.historyStartText);
                SeekBar historyEndBar = (SeekBar) findViewById(R.id.historyEndBar);
                // Refresh the text beside the seeker with an estimated date for starting history
                // progress is a percentage of the elapsed number of days since the beginning of history
                // Use the screener according to the diplayed data: either weight or steps
                long oldest = dbUserDatas.oldestDate;
                long newest = dbUserDatas.lastDate;
                if(showWhat.getCheckedRadioButtonId() == R.id.showSteps){
                    oldest = dbUserSteps.oldestDate;
                    newest = dbUserSteps.lastDate;
                }
                historyStartText.setText(displayDateFormatter.format(new Date(oldest + progress*(newest - oldest)/100)));

                // Make sure that the start remains before the end by pushing the end if necessary
                historyEndBar.setProgress(Math.max(progress, historyEndBar.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar historyStartBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar historyStartBar) {
                // Restore original labels
                TextView historyStartText = (TextView) findViewById(R.id.historyStartText);
                historyStartText.setText(getString(R.string.historyStartText));
                TextView historyEndText = (TextView) findViewById(R.id.historyEndText);
                historyEndText.setText(getString(R.string.historyEndText));
                updateViews(true);
            }
        });

        historyEndBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar historyEndBar, int progress, boolean fromUser) {
                TextView historyEndText = (TextView) findViewById(R.id.historyEndText);
                SeekBar historyStartBar = (SeekBar) findViewById(R.id.historyStartBar);
                // Refresh the text beside the seeker with an estimated date for Ending history
                // progress is a percentage of the elapsed number of days since the begining of history
                // Use the screener according to the diplayed data: either weight or steps
                long oldest = dbUserDatas.oldestDate;
                long newest = dbUserDatas.lastDate;
                if(showWhat.getCheckedRadioButtonId() == R.id.showSteps){
                    oldest = dbUserSteps.oldestDate;
                    newest = dbUserSteps.lastDate;
                }
                historyEndText.setText(displayDateFormatter.format(new Date(oldest + progress*(newest - oldest)/100)));

                // Make sure that the start remains before the end by pushing the start if necessary
                historyStartBar.setProgress(Math.min(progress, historyStartBar.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar historyEndBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar historyEndBar) {
                // Restore original labels
                TextView historyStartText = (TextView) findViewById(R.id.historyStartText);
                historyStartText.setText(getString(R.string.historyStartText));
                TextView historyEndText = (TextView) findViewById(R.id.historyEndText);
                historyEndText.setText(getString(R.string.historyEndText));
                updateViews(true);
            }
        });

        mSensorManager = (SensorManager) getSystemService(getApplicationContext().SENSOR_SERVICE);
        stepsSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        userStepCount = 0;

        updateViews(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        try{
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.options, menu);
            return true;
        }catch (Exception e) {
            menu.add(Menu.NONE, menuEntries.PREFERENCES.value(), Menu.NONE, getString(R.string.settings))
                    .setIcon(android.R.drawable.ic_menu_preferences)
                    .setAlphabeticShortcut('s');
            menu.add(Menu.NONE, menuEntries.ADDUSER.value(), Menu.NONE, getString(R.string.addUser))
                    .setIcon(android.R.drawable.ic_menu_add)
                    .setAlphabeticShortcut('a');
            menu.add(Menu.NONE, menuEntries.UPDATEUSER.value(), Menu.NONE, getString(R.string.updateUser))
                    .setIcon(android.R.drawable.ic_menu_edit)
                    .setAlphabeticShortcut('r');
            menu.add(Menu.NONE, menuEntries.REMOVEUSER.value(), Menu.NONE, getString(R.string.removeUser))
                    .setIcon(android.R.drawable.ic_menu_delete)
                    .setAlphabeticShortcut('r');
            SubMenu mgmtMenu = menu.addSubMenu(Menu.NONE, menuEntries.MGMT.value(), Menu.NONE, getString(R.string.mgmt))
                    .setIcon(android.R.drawable.ic_menu_save);
            mgmtMenu.add(Menu.NONE, menuEntries.BACKUP.value(), Menu.NONE, getString(R.string.backup))
                    .setIcon(android.R.drawable.ic_menu_save)
                    .setAlphabeticShortcut('b');
            mgmtMenu.add(Menu.NONE, menuEntries.RESTORE.value(), Menu.NONE, getString(R.string.restore))
                    .setIcon(android.R.drawable.ic_menu_upload)
                    .setAlphabeticShortcut('u');
            menu.add(Menu.NONE, menuEntries.IMCREFERENCES.value(), Menu.NONE, getString(R.string.imcReferences))
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setAlphabeticShortcut('R');

            return super.onCreateOptionsMenu(menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(this, EditPreferences.class));
                return true;
            case R.id.addUser:
                addUser(null, null);
                return true;
            case R.id.updateUser:
                addUser(selectedUserId, selectedName);
                return true;
            case R.id.removeUser:
                removeData(true, -1,  null);
                return true;
            case R.id.backup:
                backupDB();
                return true;
            case R.id.restore:
                restoreDB();
                return true;
            case R.id.imcReferences:
                showImcReferences();
                return true;
        }

        // Legacy code
        if(item.getItemId() == menuEntries.PREFERENCES.value())
            startActivity(new Intent(this, EditPreferences.class));
        else if(item.getItemId() == menuEntries.ADDUSER.value())
            addUser(null, null);
        else if(item.getItemId() == menuEntries.UPDATEUSER.value())
            addUser(selectedUserId, selectedName);
        else if(item.getItemId() == menuEntries.REMOVEUSER.value())
            removeData(true, -1,  null);
        else if(item.getItemId() == menuEntries.IMCREFERENCES.value())
            showImcReferences();
        else if(item.getItemId() == menuEntries.BACKUP.value())
            backupDB();
        else if(item.getItemId() == menuEntries.RESTORE.value())
            restoreDB();

        return true;
    }

    private void showImcReferences() {
        // display BMI references, including a link in the message
        final SpannableString imcReferenceMsg = new SpannableString(getString(R.string.imcReferencesMsg));
        Linkify.addLinks(imcReferenceMsg, Linkify.ALL);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle(R.string.imcReferences)
                .setMessage(imcReferenceMsg)
                .setPositiveButton(getString(android.R.string.ok), null);

        ListView imcReferenceList = new ListView(this);
        imcRefAdapter imcReferenceAdapter = new imcRefAdapter();
        imcReferenceList.setAdapter(imcReferenceAdapter);

        builder.setView(imcReferenceList);
        final Dialog dialog = builder.create();
        // Show the dialog and activate the links
        ((TextView)builder.show().findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register to the sensor listener with a high latency (10s) to not drain the battery
        try {
            mSensorManager.registerListener(this, stepsSensor, SensorManager.SENSOR_DELAY_NORMAL, 10*1000*1000);
        }catch (NoSuchMethodError e){
            e.printStackTrace();
        }
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }
    @Override
    public final void onSensorChanged(SensorEvent event) {
        // A sensor of this type returns the number of steps taken by the user since the last reboot while activated.
        // The value is returned as a float (with the fractional part set to zero) and is reset to zero only on a system reboot.
        long sensorValue = (long)(event.values[0]);
        // The timestamp of the event is set to the time when the last step for that event was taken. The time stamp unit is nanosecond
        long sensorTimeStamp = event.timestamp/1000000L;

        // Protect against sensor storms: ignore repeated values and old updates or not new enough (half a second since the last one)
        if(userStepCount == sensorValue || sensorTimeStamp < userStepTimeStamp + 500L)
            return;

        userStepCount = sensorValue;
        userStepTimeStamp = sensorTimeStamp;
        // The time stamp returned by the sensor is in the past: unbias the elapsed time accordingly
        userStepTimeElapsed =  SystemClock.elapsedRealtime() - (System.currentTimeMillis() - userStepTimeStamp);

        try {
            // Shall we record this value?
            userStep lastRecord = dbh.getLastUserStep();
            // Force record if no record exists
            // if the elapsed time since the last record exceeds the sampling period, record this new value
            // This sampling period is a number of hours
            if(lastRecord == null || userStepTimeStamp - lastRecord.date.getTime() >= userStepsPeriod*3600L*1000L){
                dbh.addUserStep(userStepCount,
                        new Date(userStepTimeStamp),
                        userStepTimeElapsed);
                updateViews(false);
            } else {
                showUserSteps(null);
            }

        } catch (Exception e){
            imcResult.setText(getString(R.string.exception, e));
        }
    }

    // Display the real time steps counter in the textSteps text view
    // And the user steps history if the view is not null (even if the actual value of this parameter is not used)
    public void showUserSteps(View view){
        // unregister and re-register listener...
        if(userStepCount > 0){
            if(showIncrementalSteps){
                // Display the current steps count compared to the latest recorded count
                getIncrementalUserSteps(-1, R.string.steps, textSteps);
            }else{
                textSteps.setText(dbh.displayUserStep(getApplicationContext(),
                        userStepCount,
                        new Date(userStepTimeStamp),
                        userStepTimeElapsed, R.string.steps));
            }
        }

        if(view != null){
            // try: register again to the sensor to give another chance to the listner to be waked up
            showWhat.check(R.id.showSteps);
            imcResult.setText(getString(R.string.userStepsHistory, dbUserSteps.length(), displayDateFormatter.format(dbUserSteps.screener.oldest), displayDateFormatter.format(dbUserSteps.screener.newest)));
            listHistory.setAdapter(new userStepHistoryAdapter());
            listHistory.setOnItemClickListener(new OnItemClickListener() {
                public void onItemClick(AdapterView<?> adapter, View view, int position, long arg) {
                    onUserStepHistoryItemClick(adapter, view, position, arg);
                }
            });
            listHistory.setOnItemLongClickListener(
                    new OnItemLongClickListener() {
                        public boolean onItemLongClick(AdapterView<?> adapter, View view, int position, long arg) {
                            return onUserStepHistoryItemLongClick(adapter, view, position, arg);
                        }
                    }
            );
        }
    }

    // Display in the view passed as parameter a string telling the steps count compared to the item passed as parameter by index,
    // or the last record if this parameter is unspecified (negative)
    // If the position is specified, the text color is set according to the newStart attribute value, to reflect first records happening after a device restart
    public void getIncrementalUserSteps(int position, int format, TextView v){
        userStep current = null;
        if(position < 0)
            current = dbh.getUserStep(userStepCount, new Date(userStepTimeStamp), userStepTimeElapsed, 0);

        userStep result = dbUserSteps.getIncrementalSteps(position, current);
        if(result != null){
            v.setText(dbh.displayUserStep(getApplicationContext(), result.nbSteps, result.date, result.elapsed, format));
            v.setTextColor(result.newStart && position >= 0? Color.YELLOW : Color.GRAY);
        }
    }

    // Dialog box to add the current step count as a new entry or delete the entry optionnally passed as parameter
    public void manageUserStep(View view, userStep theEntry){
        int theTitle;
        final boolean doDelete;
        final long idToDelete;
        final View theView = view;
        if(theEntry == null){
            doDelete = false;
            // action: record a new step count
            // Get the last record
            theEntry = dbh.getLastUserStep();
            theTitle = R.string.recordSteps;
            idToDelete = -1;
        }
        else {
            doDelete = true;
            // action: delete de entry passed as parameter
            theTitle = R.string.deleteSteps;
            idToDelete = theEntry._id;
        }

        final View userStepConfirmationView=getLayoutInflater().inflate(R.layout.add_user_steps, null);
        TextView addUserStepConfirmation = (TextView)userStepConfirmationView.findViewById(R.id.addUserStepConfirmationText);
        if(theEntry != null)
            addUserStepConfirmation.setText(dbh.displayUserStep(getApplicationContext(), theEntry.nbSteps, theEntry.date, theEntry.elapsed, R.string.steps));
        new AlertDialog.Builder(this)
                .setTitle(theTitle)
                .setView(userStepConfirmationView)
                .setPositiveButton(
                        getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if(doDelete){
                                    dbh.removeUserStepData(idToDelete);
                                }else{
                                    dbh.addUserStep(userStepCount,
                                            new Date(userStepTimeStamp),
                                            userStepTimeElapsed
                                    );
                                }
                                // refresh the user steps and history views
                                updateViews(false);
                            }
                        })
                .setNegativeButton(getString(android.R.string.no), null)
                .show();
    }

    private void removeData(final boolean removeUser, long _id, String dataDate) {
        // remove the user'data currently displayed on the spinner
        // remove only the _id record if >= 0
        selectedDataId = _id;
        String title = getString(removeUser ? R.string.removeUser : R.string.removeData);
        String confirmation = getString(removeUser ? R.string.removeUserConfirmation : R.string.removeDataConfirmation, selectedName, dataDate);

        final View removeView=getLayoutInflater().inflate(R.layout.remove_data, null);
        TextView removeConfirmationText = (TextView)removeView.findViewById(R.id.removeConfirmationText);
        removeConfirmationText.setText(confirmation);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(removeView)
                .setPositiveButton(
                        getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // the current user data (all) is to be deleted
                                dbh.removeUserData(selectedUserId, selectedDataId);
                                if(removeUser){
                                    // and the user himself as well
                                    dbh.removeUser(selectedUserId);

                                    // refresh the drop down list
                                    populateUserName(null);
                                }
                                updateViews(false);
                            }
                        })
                .setNegativeButton(getString(android.R.string.no), null)
                .show();
    }

    // Add of modify a user
    // Optional parameters: the user to modify, otherwise, create and add a new user
    private void addUser(final String userId, String userName) {
        final View addView=getLayoutInflater().inflate(R.layout.add, null);

        if(userName != null){
            EditText name = (EditText)addView.findViewById(R.id.userToAdd);
            name.setText(userName);
        }

        new AlertDialog.Builder(this)
                .setTitle(userId == null ? R.string.addUser : R.string.updateUser )
                .setView(addView)
                .setPositiveButton(
                        getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // a new user is to be added
                                EditText name = (EditText)addView.findViewById(R.id.userToAdd);
                                selectedName = name.getText().toString();
                                if(userId == null){
                                    selectedUserId = String.valueOf(dbh.addUser(selectedName));
                                }
                                else{
                                    dbh.updateUser(userId, selectedName);
                                }
                                populateUserName(selectedUserId);
                            }
                        })
                .setNegativeButton(getString(android.R.string.no), null)
                .show();
    }

    private static final int FILE_IMPORT_RESULT_CODE = 1;
    private static final int FILE_EXPORT_RESULT_CODE = 2;

    private void askForFilename(final int requestCode) {
        {
            final View filePathView=getLayoutInflater().inflate(R.layout.file_path, null);
            EditText filePathPromptText = (EditText)filePathView.findViewById(R.id.filePath);
            filePathPromptText.setText(folder  + "/" + dbFile + ".db");

            new AlertDialog.Builder(this)
                    .setTitle(requestCode == FILE_IMPORT_RESULT_CODE ? R.string.restore : R.string.backup)
                    .setView(filePathView)
                    .setPositiveButton(
                            getString(android.R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    EditText filePath = (EditText)filePathView.findViewById(R.id.filePath);
                                    doBackupRestore(requestCode, filePath.getText().toString());
                                }
                            })
                    .setNegativeButton(getString(android.R.string.no), null)
                    .show();
        }

    }

    protected void doBackupRestore(int requestCode, String FilePath) {
        switch(requestCode){
            case FILE_EXPORT_RESULT_CODE:
                    {
                        // Backup the database to SD card FilePath
                        final String backupFileName = FilePath;
                        final View backupView=getLayoutInflater().inflate(R.layout.backup, null);
                        String backupConfirm = getString(R.string.backupConfirm, backupFileName);
                        TextView backupConfirmationText = (TextView)backupView.findViewById(R.id.backupConfirm);
                        backupConfirmationText.setText(backupConfirm);

                        new AlertDialog.Builder(this)
                                .setTitle(R.string.backup)
                                .setView(backupView)
                                .setPositiveButton(
                                        getString(android.R.string.ok),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                try {
                                                    String backupResult = dbh.backup(backupFileName);
                                                    if(backupResult != null){
                                                        imcResult.setText(backupResult);
                                                    }else{
                                                        imcResult.setText(getString(R.string.backupSuccess, backupFileName));
                                                    }
                                                } catch (Exception e) {
                                                    imcResult.setText(getString(R.string.exception, e));
                                                }
                                            }
                                        })
                                .setNegativeButton(getString(android.R.string.no), null)
                                .show();
                    }
                break;
            case FILE_IMPORT_RESULT_CODE:
                    {
                        // Restore the database from SD card FilePath
                        final String backupFileName = FilePath;
                        final View restoreView=getLayoutInflater().inflate(R.layout.restore, null);
                        String restoreConfirm = getString(R.string.restoreConfirm, backupFileName);
                        TextView restoreConfirmationText = (TextView)restoreView.findViewById(R.id.restoreConfirm);
                        restoreConfirmationText.setText(restoreConfirm);

                        new AlertDialog.Builder(this)
                                .setTitle(R.string.restore)
                                .setView(restoreView)
                                .setPositiveButton(
                                        getString(android.R.string.ok),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                // the current user data (all) is to be deleted
                                                String restoreResult = dbh.restore(backupFileName);
                                                if (restoreResult != null) {
                                                    imcResult.setText(restoreResult);
                                                } else {
                                                    imcResult.setText(getString(R.string.restoreSuccess, backupFileName));
                                                }
                                            }
                                        })
                                .setNegativeButton(getString(android.R.string.no), null)
                                .show();

                    }
                break;
        }
    }

    private void backupDB(){askForFilename(FILE_EXPORT_RESULT_CODE);}

    private void restoreDB(){askForFilename(FILE_IMPORT_RESULT_CODE);}

    protected void populateUserName(String aUserId) {
        usersNames = dbh.getUsers();

        ArrayAdapter<String> aa = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, usersNames.user);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        userName.setAdapter(aa);
        userName.setOnItemSelectedListener(this);
        userName.setOnItemLongClickListener(
                new OnItemLongClickListener() {
                    public boolean onItemLongClick(AdapterView<?> adapter, View view, int position, long arg) {
                        return onUserItemLongClick(adapter, view, position, arg);
                    }
                }
        );
        if(aUserId != null)
            // A specific userId is to be selected
            userName.setSelection(usersNames.getPosition(aUserId));

        // Refresh the list of users in the preference list
        EditPreferences.setUserDefaultPrefList(usersNames);
    }

    // A new user has been selected in the drop down box
    public void onItemSelected(AdapterView<?> parent, View v, int position, long id){
        if(useName){
            selectedName = usersNames.user[position];
            selectedUserId = usersNames.userId[position];
        }

        // Refresh displayed data including input fields
        updateViews(true);
    }



    public boolean onUserItemLongClick(AdapterView<?> adapter, View view, int position, long arg) {
        if(useName){
            // The selected user is to be modified
            selectedName = usersNames.user[position];
            selectedUserId = usersNames.userId[position];
            addUser(selectedUserId, selectedName);
        }

        // Refresh displayed data including input fields
        updateViews(true);
        return true;
    }

    public void onUserStepHistoryItemClick(AdapterView<?> adapter, View v, int position, long id){
        v.setSelected(true);
        // an item has been clicked in the user step count history list
        // display the associated data
        try {
        userStep entry = dbUserSteps.entry[position];
        textSteps.setText(dbh.displayUserStep(getApplicationContext(),
                entry.nbSteps,
                entry.date,
                entry.elapsed,
                R.string.steps)
        );
        } catch (Exception e){
            imcResult.setText(getString(R.string.exception, e));
        }
    }

    public void onHistoryItemClick(AdapterView<?> adapter, View v, int position, long id){
        v.setSelected(true);
        // an item has been clicked in the history list
        // display the associated data in input fields
        try {
        userData entry = dbUserDatas.entry[position];
        taille.setText(digitalFormatter.format(entry.size));
        poids.setText(digitalFormatter.format(entry.weight));

        // display this entry date on the setDate button
        if(entry.date != null)
            setDate.setText(displayDateFormatter.format(entry.date));
        } catch (Exception e){
            imcResult.setText(getString(R.string.exception, e));
        }
    }


    private boolean onHistoryItemLongClick(AdapterView<?> adapter,
                                           View view, int position, long arg) {
        // an item has been clicked and held in the history list: remove it
        userData entry = dbUserDatas.entry[position];

        removeData(false, entry._id, displayDateFormatter.format(entry.date));
        return false;
    }

    private boolean onUserStepHistoryItemLongClick(AdapterView<?> adapter,
                                           View view, int position, long arg) {
        // an item has been clicked and held in the user step history list: remove it
        try {
            userStep entry = dbUserSteps.entry[position];
            manageUserStep(view, entry);
        } catch (Exception e){
            imcResult.setText(getString(R.string.exception, e));
        }
        return false;
    }

    public void updateViews(boolean setInputFields){
        // Display this user's data
        SeekBar historyStartBar = (SeekBar) findViewById(R.id.historyStartBar);
        SeekBar historyEndBar = (SeekBar) findViewById(R.id.historyEndBar);
        dbUserDatas = dbh.getUserData(selectedUserId, historyStartBar.getProgress(), historyEndBar.getProgress());
        dbUserSteps = dbh.getUserSteps(historyStartBar.getProgress(), historyEndBar.getProgress());

        // if the input fields have to be refreshed
        if (setInputFields){
            // Initialize display with the most recent known values, if any
            if(dbUserDatas.length() != 0){
                int lastEntryIndex = Math.min(dbUserDatas.screener.getEnd(), dbUserDatas.length() - 1);
                taille.setText(digitalFormatter.format(dbUserDatas.entry[lastEntryIndex].size));
                poids.setText(digitalFormatter.format(dbUserDatas.entry[lastEntryIndex].weight));
            } else{
                taille.setText("");
                poids.setText("");
            }
            // Assuming that the user will enter data of the day,
            // display the current date on the setDate button
            setDate.setText(displayDateFormatter.format(Calendar.getInstance().getTime()));
        }

        imcResult.setTextColor(android.graphics.Color.WHITE);
        if(storeUserData){
            if(dbUserDatas.entry.length > 1){
                // Do not display too many digits for average weight
                imcResult.setText(getString(R.string.statistics,
                        // Start date
                        displayDateFormatter.format(dbUserDatas.screener.oldest),
                        // End date
                        displayDateFormatter.format(dbUserDatas.screener.newest),
                        // elapsed days
                        integerFormatter.format((dbUserDatas.screener.newest - dbUserDatas.screener.oldest)/1000/60/60/24),
                        digitalFormatter.format(dbUserDatas.minWeight),
                        digitalFormatter.format(dbUserDatas.averageWeight),
                        digitalFormatter.format(dbUserDatas.maxWeight),
                        // # samples
                        integerFormatter.format(dbUserDatas.entry.length)
                ));
            }
            else
                imcResult.setText(getString(R.string.historique));

            listHistory.setAdapter(new HistoryAdapter());
            listHistory.setOnItemClickListener(new OnItemClickListener() {
                public void onItemClick(AdapterView<?> adapter, View view, int position, long arg) {
                    onHistoryItemClick(adapter, view, position, arg);
                }
            });
            listHistory.setOnItemLongClickListener(
                    new OnItemLongClickListener() {
                        public boolean onItemLongClick(AdapterView<?> adapter, View view, int position, long arg) {
                            return onHistoryItemLongClick(adapter, view, position, arg);
                        }
                    }
            );
        }
        else{
            imcResult.setText("");
        }

        // Refresh the selected view by reading the radio button group
        // Default to line graph
        seriesFormat = lineFormat;
        switch(showWhat.getCheckedRadioButtonId()) {
            default:
                // show weight by default
                showWhat.check(R.id.showWeight);
            case R.id.showWeight:
                draw(dbUserDatas.weightEntries(), dbUserDatas.datesEntries(),
                        getString(R.string.poids) + " : " + selectedName, dbUserDatas.minWeight, dbUserDatas.maxWeight);
                break;
            case R.id.showSize:
                draw(dbUserDatas.sizeEntries(), dbUserDatas.datesEntries(),
                        getString(R.string.taille) + " : " + selectedName, dbUserDatas.minSize, dbUserDatas.maxSize);
                break;
            case R.id.showBMI:
                draw(dbUserDatas.imcEntries(), dbUserDatas.datesEntries(),
                        getString(R.string.BMI) + " : " + selectedName, dbUserDatas.minImc, dbUserDatas.maxImc);
                break;
            case R.id.showSteps:
                showUserSteps(textSteps);
                seriesFormat = barFormat;
                draw(dbUserSteps.stepsEntries(), dbUserSteps.datesEntries(),
                        getString(R.string.stepsCount) + " : " + ownerName, 0, dbUserSteps.maxSteps);
                break;
        }

    }

    public void onNothingSelected(AdapterView<?> parent){
        selectedName = getString(R.string.defaultGuest);
    }

    public void doImc(View view){
        String tailleString = new String(taille.getText().toString());
        String poidsString = new String(poids.getText().toString());

        try {
            NumberFormat nf = NumberFormat.getInstance();
            double tailleInt = nf.parse(tailleString).doubleValue();
            double poidsInt = nf.parse(poidsString).doubleValue();
            double imc = poidsInt/tailleInt/tailleInt;
            // add data in database according to preference
            if(storeUserData){
                Date currentDate = displayDateFormatter.parse((String) setDate.getText());
                dbh.addUserData(selectedUserId, tailleInt, poidsInt, currentDate);
            }

            // update the data displayed in the history and graphs,
            // keeping input fields untouched
            updateViews(false);

            // Display with friendly format, two decimal places after point
            imcRange theImcRange = imcReference.getImcRange(imc);
            imcResult.setText(getString(R.string.sayImc,
                    selectedName, digitalFormatter.format(imc), theImcRange.getLabel()));
            imcResult.setTextColor(theImcRange.getColor());
        } catch (Exception e){
            imcResult.setText(getString(R.string.exception, e));
        }
    }

    public void setDate(View view){
        // start a date picker initialized according to the current text displayed on the date button
        try {
            Date defaultDate = displayDateFormatter.parse((String) setDate.getText());
            calendarInstance.setTime(defaultDate);
            new DatePickerDialog(this, d,
                    calendarInstance.get(Calendar.YEAR),
                    calendarInstance.get(Calendar.MONTH),
                    calendarInstance.get(Calendar.DATE))
                    .show();
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        showIncrementalSteps = preferences.getBoolean(getString(R.string.showSteps), true);
        useName = preferences.getBoolean(getString(R.string.user), false);
        userStepsPeriod = Integer.valueOf(preferences.getString(getString(R.string.userStepsPeriod), "24"));
        userName.setVisibility(useName ? View.VISIBLE : View.INVISIBLE);
        textSteps.setVisibility(useName ? View.INVISIBLE : View.VISIBLE);
        dbFile = preferences.getString(getString(R.string.dbFile), getString(R.string.BMI));
        dbh = new imcDatabaseHelper(getApplicationContext(), dbFile);
        usersNames = dbh.getUsers();
        // If the owner is known in the group mode, use its datas
        ownerName = selectedName = preferences.getString(getString(R.string.userAdd), getString(R.string.defaultGuest));
        selectedUserId = usersNames.getUserId(selectedName);
        if(useName && usersNames.user.length == 0){
            addUser(selectedUserId, selectedName);
        }
        populateUserName(selectedUserId);

        if(useName){
            // Restore the default user
            selectedUserId = preferences.getString(getString(R.string.defaultUser), "1");
            int preferedUserId = usersNames.getPosition(selectedUserId);
            if(preferedUserId != -1)
                selectedName = usersNames.user[preferedUserId];
            else
                selectedUserId = null;
        }

        storeUserData = preferences.getBoolean(getString(R.string.record), false);
        listHistory.setVisibility(storeUserData ? View.VISIBLE : View.INVISIBLE);
        setDate.setVisibility(storeUserData ? View.VISIBLE : View.INVISIBLE);
        populateUserName(selectedUserId);
        updateViews(true);
    }

    public void onShowWhatClicked(View view) {updateViews(false);}
    public void showWeight(View v){showWhat.check(R.id.showWeight);updateViews(false);}
    public void showSize(View v){showWhat.check(R.id.showSize);updateViews(false);}
    public void showSteps(View v){
        showWhat.check(R.id.showSteps);updateViews(false);
        // refresh the sensor to help getting more steps if the user clicked on the live step counter
        try {
            mSensorManager = (SensorManager) getSystemService(getApplicationContext().SENSOR_SERVICE);
            mSensorManager.unregisterListener(this);
            stepsSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            mSensorManager.registerListener(this, stepsSensor, SensorManager.SENSOR_DELAY_NORMAL, 10*1000*1000);
        }catch (NoSuchMethodError e){
            e.printStackTrace();
        }

    }

    private void draw(
            Number[] seriesNumbers,
            Number[] seriedates,
            String seriesTitle,
            double minValue, double maxValue){
        // Initialize our XYPlot reference:
        mySimpleXYPlot = (XYPlot) findViewById(R.id.mySimpleXYPlot);
        //mySimpleXYPlot.setTitle(graphicTitle);
        // remove previous data if any
        if(series != null) {
            mySimpleXYPlot.removeSeries(series);
            mySimpleXYPlot.clear();
        }

        // Turn the above arrays into XYSeries:
        series = new SimpleXYSeries(
                Arrays.asList(seriedates), 	//SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, // Y_VALS_ONLY means use the element index as the x value
                Arrays.asList(seriesNumbers),          // SimpleXYSeries takes a List so turn our array into a List
                seriesTitle);                             // Set the display title of the series

        // Need decimal values for the date as milliseconds, and 2 digits for the data
        mySimpleXYPlot.setDomainValueFormat(new DecimalFormat("#"));
        mySimpleXYPlot.setRangeValueFormat(new DecimalFormat("#.##"));
        // Boundaries are rounded to the nearest integer
        {
            // avoid a null range, increase to the nearest positive integers
            if(minValue == maxValue){maxValue+=1; if(minValue >= 1)minValue-=1;}

            // Get the magnitude of the range, ie the nearest lower power of 10
            double magnitudeLog = Math.log10(maxValue - minValue);
            double magnitude = Math.pow(10, Math.floor(magnitudeLog));
            // Round min and max to the nearest lower/upper mutliple of magnitude
            minValue = Math.floor(minValue/magnitude)*magnitude;
            maxValue = Math.ceil(maxValue/magnitude)*magnitude;
            double nbSteps = (maxValue - minValue)/magnitude;
            // Make sure to have enough steps
            if(nbSteps <= 1)nbSteps*=10;
            else if(nbSteps <= 3)nbSteps*=5;
            else if(nbSteps <= 5)nbSteps*=2;
            // Add one stake to finish the enclosure
            nbSteps+=1;
            mySimpleXYPlot.setRangeBoundaries(minValue, BoundaryMode.FIXED, maxValue, BoundaryMode.FIXED);
            mySimpleXYPlot.setRangeStepValue(nbSteps);
            // number of range labels
            mySimpleXYPlot.setTicksPerRangeLabel(1);
            mySimpleXYPlot.setTicksPerDomainLabel(2);

        }

        // Minimum ten days on the date axis
        {
            long timestamp = ((Number) seriedates[0]).longValue();
            calendarInstance.setTime(new Date(timestamp));
            Date minDate = calendarInstance.getTime();
            calendarInstance.add(Calendar.DATE, 5);
            Date minEndDate = calendarInstance.getTime();
            timestamp = ((Number) seriedates[seriedates.length-1]).longValue();
            calendarInstance.setTime(new Date(timestamp));
            Date maxDate = calendarInstance.getTime();
            if(maxDate.before(minEndDate)){
                calendarInstance.add(Calendar.DATE, 5);
                maxDate = calendarInstance.getTime();
                calendarInstance.add(Calendar.DATE, -10);
                minDate = calendarInstance.getTime();
            }
            mySimpleXYPlot.setDomainBoundaries(minDate.getTime(),BoundaryMode.FIXED, maxDate.getTime(), BoundaryMode.FIXED);
        }


        // Add series1 to the xyplot:
        mySimpleXYPlot.addSeries(series, seriesFormat);

        // rotate domain labels 45 degrees to make them more compact horizontally:
        mySimpleXYPlot.getGraphWidget().setDomainLabelOrientation(-60);

        mySimpleXYPlot.setDomainLabel(getString(R.string.date));
        mySimpleXYPlot.setRangeLabel(seriesTitle);

        mySimpleXYPlot.setDomainValueFormat(new Format() {
            @Override
            public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
                long timestamp = ((Number) obj).longValue();
                Date date = new Date(timestamp);
                return displayDateFormatter.format(date, toAppendTo, pos);
            }

            @Override
            public Object parseObject(String source, ParsePosition pos) {
                return null;
            }
        });

        mySimpleXYPlot.redraw();
    }

    DatePickerDialog.OnDateSetListener d = new DatePickerDialog.OnDateSetListener(){
        public void onDateSet(DatePicker view, int year, int month, int day){
            calendarInstance.set(year, month, day);
            setDate.setText(displayDateFormatter.format(calendarInstance.getTime()));
        }
    };
}