package com.d3m.imc;

import android.os.Bundle;

import com.d3m.imc.imcDatabaseHelper.usersAndIds;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

public class EditPreferences extends PreferenceActivity {
    private static ListPreference userDefaultPrefList;
    private static usersAndIds usersAndIdsList;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        userDefaultPrefList = (ListPreference) findPreference(getString(R.string.defaultUser));
        if(userDefaultPrefList != null && usersAndIdsList != null){
            userDefaultPrefList.setEntries(usersAndIdsList.user);
            userDefaultPrefList.setEntryValues(usersAndIdsList.userId);
        }
    }
    public static void setUserDefaultPrefList(usersAndIds theList){
        usersAndIdsList = theList;
    }
}
