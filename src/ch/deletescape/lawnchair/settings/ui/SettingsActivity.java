/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2017 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.deletescape.lawnchair.settings.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.preference.*;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import ch.deletescape.lawnchair.LawnchairLauncher;
import ch.deletescape.lawnchair.LawnchairPreferences;
import ch.deletescape.lawnchair.gestures.ui.GesturePreference;
import ch.deletescape.lawnchair.gestures.ui.SelectGestureHandlerFragment;
import ch.deletescape.lawnchair.theme.ThemeOverride;
import com.android.launcher3.*;
import com.android.launcher3.R;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.graphics.IconShapeOverride;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.SettingsObserver;
import com.android.launcher3.views.ButtonPreference;
import com.google.android.apps.nexuslauncher.CustomIconPreference;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import me.jfenn.attribouter.Attribouter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static com.android.launcher3.Utilities.restartLauncher;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsActivity extends SettingsBaseActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, FragmentManager.OnBackStackChangedListener {
    private static final String ICON_BADGING_PREFERENCE_KEY = "pref_icon_badging";
    /** Hidden field Settings.Secure.NOTIFICATION_BADGING */
    public static final String NOTIFICATION_BADGING = "notification_badging";
    /** Hidden field Settings.Secure.ENABLED_NOTIFICATION_LISTENERS */
    private static final String NOTIFICATION_ENABLED_LISTENERS = "enabled_notification_listeners";

    public final static String ICON_PACK_PREF = "pref_icon_pack";
    public final static String SHOW_PREDICTIONS_PREF = "pref_show_predictions";
    public final static String ENABLE_MINUS_ONE_PREF = "pref_enable_minus_one";
    public final static String SMARTSPACE_PREF = "pref_smartspace";

    private LawnchairPreferences sharedPrefs;
    private int mAppBarHeight;
    private boolean isSubSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getDecorLayout().setUseLargeTitle(true);
        setContentView(R.layout.activity_settings);

        mAppBarHeight = getResources().getDimensionPixelSize(R.dimen.app_bar_elevation);

        int content = getIntent().getIntExtra(SubSettingsFragment.CONTENT_RES_ID, 0);
        isSubSettings = content != 0;
        if (savedInstanceState == null) {
            Fragment fragment = content != 0
                    ? SubSettingsFragment.newInstance(getIntent())
                    : new LauncherSettingsFragment();
            // Display the fragment as the main content.
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content, fragment)
                    .commit();
        }

        getSupportFragmentManager().addOnBackStackChangedListener(this);

        sharedPrefs = Utilities.getLawnchairPrefs(this);
        updateUpButton();
    }

    @NotNull
    @Override
    protected ThemeOverride getThemeOverride() {
        if (getIntent().getBooleanExtra(SubSettingsFragment.HAS_PREVIEW, false)) {
            return new ThemeOverride.SettingsTransparent(this);
        } else {
            return super.getThemeOverride();
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference preference) {
        Fragment fragment;
        if (preference instanceof SubPreference) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(SubSettingsFragment.TITLE, preference.getTitle());
            intent.putExtra(SubSettingsFragment.CONTENT_RES_ID, ((SubPreference) preference).getContent());
            intent.putExtra(SubSettingsFragment.HAS_PREVIEW, ((SubPreference) preference).hasPreview());
            startActivity(intent);
            return true;
        } else if(preference.getKey().equals("about")){
            fragment = Attribouter.from(this).withFile(R.xml.attribouter).toFragment();
        } else {
            fragment = Fragment.instantiate(this, preference.getFragment(), preference.getExtras());
        }
        if (fragment instanceof DialogFragment) {
            ((DialogFragment) fragment).show(getSupportFragmentManager(), preference.getKey());
        } else {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            setTitle(preference.getTitle());
            transaction.setCustomAnimations(R.animator.fly_in, R.animator.fade_out, R.animator.fade_in, R.animator.fly_out);
            transaction.replace(R.id.content, fragment);
            transaction.addToBackStack("PreferenceFragment");
            transaction.commit();
        }
        return true;
    }

    private void updateUpButton() {
        updateUpButton(isSubSettings || getSupportFragmentManager().getBackStackEntryCount() != 0);
    }

    private void updateUpButton(boolean enabled) {
        if (getSupportActionBar() == null) return;
        getSupportActionBar().setDisplayHomeAsUpEnabled(enabled);
        getDecorLayout().setUseLargeTitle(!enabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackStackChanged() {
        updateUpButton();
    }

    private abstract static class BaseFragment extends PreferenceFragmentCompat implements AdapterView.OnItemLongClickListener {

        public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
                                                 Bundle savedInstanceState) {
            RecyclerView recyclerView = (RecyclerView) inflater
                    .inflate(R.layout.preference_spring_recyclerview, parent, false);

            recyclerView.setLayoutManager(onCreateLayoutManager());
            recyclerView.setAccessibilityDelegateCompat(
                    new PreferenceRecyclerViewAccessibilityDelegate(recyclerView));

            return recyclerView;
        }

        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            ListView listView = (ListView) parent;
            ListAdapter listAdapter = listView.getAdapter();
            Object item = listAdapter.getItem(position);

            if (item instanceof SubPreference) {
                SubPreference subPreference = (SubPreference) item;
                if (subPreference.onLongClick(null)) {
                    ((SettingsActivity) getActivity()).onPreferenceStartFragment(this, subPreference);
                    return true;
                } else {
                    return false;
                }
            }
            return item != null && item instanceof View.OnLongClickListener && ((View.OnLongClickListener) item).onLongClick(view);
        }

        @Override
        public void setDivider(Drawable divider) {
            super.setDivider(null);
        }

        @Override
        public void setDividerHeight(int height) {
            super.setDividerHeight(0);
        }
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class LauncherSettingsFragment extends BaseFragment implements LawnchairPreferences.OnPreferenceChangeListener {

        private Preference mDeveloperOptions;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.lawnchair_preferences);
            Utilities.getLawnchairPrefs(getActivity()).addOnPreferenceChangeListener("pref_developerOptionsReallyEnabled", this);
        }

        @Override
        public void onResume() {
            super.onResume();
            getActivity().setTitle(R.string.derived_app_name);
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            if (preference.getKey() != null && "about".equals(preference.getKey())){
                ((SettingsActivity) getActivity()).onPreferenceStartFragment(this, preference);
                return true;
            }
            return super.onPreferenceTreeClick(preference);
        }

        @Override
        public void onValueChanged(@NotNull String key, @NotNull LawnchairPreferences prefs, boolean force) {
            if("pref_developerOptionsReallyEnabled".equals(key)){
                if (prefs.getDeveloperOptionsEnabled()) {
                    if(mDeveloperOptions != null){
                        getPreferenceScreen().addPreference(mDeveloperOptions);
                        mDeveloperOptions = null;
                    }
                } else {
                    mDeveloperOptions = getPreferenceScreen().findPreference("developerOptions");
                    if(mDeveloperOptions != null) {
                        getPreferenceScreen().removePreference(mDeveloperOptions);
                    }
                }
            }
        }
    }

    public static class SubSettingsFragment extends BaseFragment implements
            Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

        public static final String TITLE = "title";
        public static final String CONTENT_RES_ID = "content_res_id";
        public static final String HAS_PREVIEW = "has_preview";

        private SystemDisplayRotationLockObserver mRotationLockObserver;
        private IconBadgingObserver mIconBadgingObserver;

        private CustomIconPreference mIconPackPref;
        private Context mContext;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mContext = getActivity();

            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            if (getContent() == R.xml.lawnchair_desktop_preferences) {
                findPreference(ENABLE_MINUS_ONE_PREF).setTitle(getDisplayGoogleTitle());

                ContentResolver resolver = getActivity().getContentResolver();

                // Setup allow rotation preference
                Preference rotationPref = findPreference(Utilities.ALLOW_ROTATION_PREFERENCE_KEY);
                if (getResources().getBoolean(R.bool.allow_rotation)) {
                    // Launcher supports rotation by default. No need to show this setting.
                    getPreferenceScreen().removePreference(rotationPref);
                } else {
                    mRotationLockObserver = new SystemDisplayRotationLockObserver(rotationPref, resolver);

                    // Register a content observer to listen for system setting changes while
                    // this UI is active.
                    mRotationLockObserver.register(Settings.System.ACCELEROMETER_ROTATION);

                    // Initialize the UI once
                    rotationPref.setDefaultValue(Utilities.getAllowRotationDefaultValue(getActivity()));
                }

                ButtonPreference iconBadgingPref =
                        (ButtonPreference) findPreference(ICON_BADGING_PREFERENCE_KEY);
                if (!Utilities.ATLEAST_OREO) {
                    getPreferenceScreen().removePreference(
                            findPreference(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY));
                }
                if (!getResources().getBoolean(R.bool.notification_badging_enabled)) {
                    getPreferenceScreen().removePreference(iconBadgingPref);
                } else {
                    // Listen to system notification badge settings while this UI is active.
                    mIconBadgingObserver = new IconBadgingObserver(
                            iconBadgingPref, resolver, getFragmentManager());
                    mIconBadgingObserver.register(NOTIFICATION_BADGING, NOTIFICATION_ENABLED_LISTENERS);
                }
            } else if (getContent() == R.xml.lawnchair_theme_preferences) {
                ListPreference iconShapeOverride = (ListPreference) findPreference(IconShapeOverride.KEY_PREFERENCE);
                if (iconShapeOverride != null) {
                    if (Utilities.getLawnchairPrefs(mContext).getDeveloperOptionsEnabled()) {
                        iconShapeOverride.setEntries(R.array.alt_icon_shape_override_paths_names);
                        iconShapeOverride.setEntryValues(R.array.alt_icon_shape_override_paths_values);
                    }
                    if (IconShapeOverride.isSupported(getActivity())) {
                        IconShapeOverride.handlePreferenceUi((ListPreference) iconShapeOverride);
                    } else {
                        getPreferenceScreen().removePreference(iconShapeOverride);
                    }
                }

                mIconPackPref = (CustomIconPreference) findPreference(ICON_PACK_PREF);
                mIconPackPref.setOnPreferenceChangeListener(this);
            } else if (getContent() == R.xml.lawnchair_app_drawer_preferences) {
                findPreference(SHOW_PREDICTIONS_PREF).setOnPreferenceChangeListener(this);
            } else if (getContent() == R.xml.lawnchair_dev_options_preference) {
                findPreference("kill").setOnPreferenceClickListener(this);
                findPreference("crashLauncher").setOnPreferenceClickListener(this);
                findPreference("addSettingsShortcut").setOnPreferenceClickListener(this);
                findPreference("currentWeatherProvider").setSummary(
                        Utilities.getLawnchairPrefs(mContext).getWeatherProvider());
                findPreference("appInfo").setOnPreferenceClickListener(this);
                findPreference("screenshot").setOnPreferenceClickListener(this);
            }
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(getContent());
        }

        private String getDisplayGoogleTitle() {
            CharSequence charSequence = null;
            try {
                Resources resourcesForApplication = mContext.getPackageManager().getResourcesForApplication("com.google.android.googlequicksearchbox");
                int identifier = resourcesForApplication.getIdentifier("title_google_home_screen", "string", "com.google.android.googlequicksearchbox");
                if (identifier != 0) {
                    charSequence = resourcesForApplication.getString(identifier);
                }
            }
            catch (PackageManager.NameNotFoundException ex) {
            }
            if (TextUtils.isEmpty(charSequence)) {
                charSequence = mContext.getString(R.string.title_google_app);
            }
            return mContext.getString(R.string.title_show_google_app, charSequence);
        }

        private int getContent() {
            return getArguments().getInt(CONTENT_RES_ID);
        }

        @Override
        public void onResume() {
            super.onResume();
            getActivity().setTitle(getArguments().getString(TITLE));

            if (mIconPackPref != null)
                mIconPackPref.reloadIconPacks();
        }

        @Override
        public void onDestroy() {
            if (mRotationLockObserver != null) {
                mRotationLockObserver.unregister();
                mRotationLockObserver = null;
            }
            if (mIconBadgingObserver != null) {
                mIconBadgingObserver.unregister();
                mIconBadgingObserver = null;
            }
            super.onDestroy();
        }

        @Override
        public void onDisplayPreferenceDialog(Preference preference) {
            final DialogFragment f;
            if (preference instanceof GridSizePreference) {
                f = GridSizeDialogFragmentCompat.Companion.newInstance(preference.getKey());
            } else if (preference instanceof DockGridSizePreference) {
                f = DockGridSizeDialogFragmentCompat.Companion.newInstance(preference.getKey());
            } else if (preference instanceof GesturePreference) {
                f = SelectGestureHandlerFragment.Companion.newInstance((GesturePreference) preference);
            } else {
                super.onDisplayPreferenceDialog(preference);
                return;
            }
            f.setTargetFragment(this, 0);
            f.show(getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
        }

        public static SubSettingsFragment newInstance(SubPreference preference) {
            SubSettingsFragment fragment = new SubSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, (String) preference.getTitle());
            b.putInt(CONTENT_RES_ID, preference.getContent());
            fragment.setArguments(b);
            return fragment;
        }

        public static SubSettingsFragment newInstance(Intent intent) {
            SubSettingsFragment fragment = new SubSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, intent.getStringExtra(TITLE));
            b.putInt(CONTENT_RES_ID, intent.getIntExtra(CONTENT_RES_ID, 0));
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            switch (preference.getKey()) {
                case ICON_PACK_PREF:
                    ProgressDialog.show(mContext,
                            null /* title */,
                            mContext.getString(R.string.state_loading),
                            true /* indeterminate */,
                            false /* cancelable */);

                    new LooperExecutor(LauncherModel.getWorkerLooper()).execute(new Runnable() {
                        @SuppressLint("ApplySharedPref")
                        @Override
                        public void run() {
                            // Clear the icon cache.
                            LauncherAppState.getInstance(mContext).getIconCache().clear();

                            // Wait for it
                            try {
                                Thread.sleep(1000);
                            } catch (Exception e) {
                                Log.e("SettingsActivity", "Error waiting", e);
                            }

                            restartLauncher(mContext);
                        }
                    });
                    return true;
                case SHOW_PREDICTIONS_PREF:
                    if ((boolean) newValue) {
                        return true;
                    }
                    SuggestionConfirmationFragment confirmationFragment = new SuggestionConfirmationFragment();
                    confirmationFragment.setTargetFragment(this, 0);
                    confirmationFragment.show(getFragmentManager(), preference.getKey());
                    break;
            }
            return false;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            switch (preference.getKey()) {
                case "kill":
                    Utilities.killLauncher();
                    break;
                case "addSettingsShortcut":
                    Utilities.pinSettingsShortcut(getActivity());
                    break;
                case "crashLauncher":
                    throw new RuntimeException("Triggered from developer options");
                case "appInfo":
                    ComponentName componentName = new ComponentName(getActivity(), LawnchairLauncher.class);
                    LauncherAppsCompat.getInstance(getContext()).showAppDetailsForProfile(componentName, android.os.Process.myUserHandle());
                    break;
                case "screenshot":
                    final Context context = getActivity();
                    LawnchairLauncher.Companion.takeScreenshot(getActivity(), new Handler(), new Function1<Uri, Unit>() {
                        @Override
                        public Unit invoke(Uri uri) {
                            try {
                                Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
                                ImageView imageView = new ImageView(context);
                                imageView.setImageBitmap(bitmap);
                                new AlertDialog.Builder(context)
                                        .setTitle("Screenshot")
                                        .setView(imageView)
                                        .show();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return null;
                        }
                    });
                    break;
            }
            return false;
        }
    }

    public static class SuggestionConfirmationFragment extends DialogFragment implements DialogInterface.OnClickListener {
        public void onClick(final DialogInterface dialogInterface, final int n) {
            if (getTargetFragment() instanceof PreferenceFragmentCompat) {
                Preference preference = ((PreferenceFragmentCompat) getTargetFragment()).findPreference(SHOW_PREDICTIONS_PREF);
                if (preference instanceof TwoStatePreference) {
                    ((TwoStatePreference) preference).setChecked(false);
                }
            }
        }

        public Dialog onCreateDialog(final Bundle bundle) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.title_disable_suggestions_prompt)
                    .setMessage(R.string.msg_disable_suggestions_prompt)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.label_turn_off_suggestions, this).create();
        }
    }

    /**
     * Content observer which listens for system auto-rotate setting changes, and enables/disables
     * the launcher rotation setting accordingly.
     */
    private static class SystemDisplayRotationLockObserver extends SettingsObserver.System {

        private final Preference mRotationPref;

        public SystemDisplayRotationLockObserver(
                Preference rotationPref, ContentResolver resolver) {
            super(resolver);
            mRotationPref = rotationPref;
        }

        @Override
        public void onSettingChanged(boolean enabled) {
            mRotationPref.setEnabled(enabled);
            mRotationPref.setSummary(enabled
                    ? R.string.allow_rotation_desc : R.string.allow_rotation_blocked_desc);
        }
    }

    /**
     * Content observer which listens for system badging setting changes,
     * and updates the launcher badging setting subtext accordingly.
     */
    private static class IconBadgingObserver extends SettingsObserver.Secure
            implements Preference.OnPreferenceClickListener {

        private final ButtonPreference mBadgingPref;
        private final ContentResolver mResolver;
        private final FragmentManager mFragmentManager;
        private boolean serviceEnabled = true;

        public IconBadgingObserver(ButtonPreference badgingPref, ContentResolver resolver,
                                   FragmentManager fragmentManager) {
            super(resolver);
            mBadgingPref = badgingPref;
            mResolver = resolver;
            mFragmentManager = fragmentManager;
        }

        @Override
        public void onSettingChanged(boolean enabled) {
            int summary = enabled ? R.string.icon_badging_desc_on : R.string.icon_badging_desc_off;

            if (enabled) {
                // Check if the listener is enabled or not.
                String enabledListeners =
                        Settings.Secure.getString(mResolver, NOTIFICATION_ENABLED_LISTENERS);
                ComponentName myListener =
                        new ComponentName(mBadgingPref.getContext(), NotificationListener.class);
                serviceEnabled = enabledListeners != null &&
                        (enabledListeners.contains(myListener.flattenToString()) ||
                                enabledListeners.contains(myListener.flattenToShortString()));
                if (!serviceEnabled) {
                    summary = R.string.title_missing_notification_access;
                }
            }
            mBadgingPref.setWidgetFrameVisible(!serviceEnabled);
            mBadgingPref.setOnPreferenceClickListener(serviceEnabled && Utilities.ATLEAST_OREO ? null : this);
            mBadgingPref.setSummary(summary);

        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (!Utilities.ATLEAST_OREO && serviceEnabled) {
                ComponentName cn = new ComponentName(preference.getContext(), NotificationListener.class);
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(":settings:fragment_args_key", cn.flattenToString());
                preference.getContext().startActivity(intent);
            } else {
                new SettingsActivity.NotificationAccessConfirmation().show(mFragmentManager, "notification_access");
            }
            return true;
        }
    }

    public static class NotificationAccessConfirmation
            extends DialogFragment implements DialogInterface.OnClickListener {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            String msg = context.getString(R.string.msg_missing_notification_access,
                    context.getString(R.string.derived_app_name));
            return new AlertDialog.Builder(context)
                    .setTitle(R.string.title_missing_notification_access)
                    .setMessage(msg)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.title_change_settings, this)
                    .create();
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            ComponentName cn = new ComponentName(getActivity(), NotificationListener.class);
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(":settings:fragment_args_key", cn.flattenToString());
            getActivity().startActivity(intent);
        }
    }
}