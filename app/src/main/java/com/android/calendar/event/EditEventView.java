/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.calendar.event;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.Service;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.MultiAutoCompleteTextView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ResourceCursorAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentManager;

import com.android.calendar.CalendarEventModel;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.EmailAddressAdapter;
import com.android.calendar.EventInfoFragment;
import com.android.calendar.EventRecurrenceFormatter;
import com.android.calendar.RecipientAdapter;
import com.android.calendar.Utils;
import com.android.calendar.event.EditEventHelper.EditDoneRunnable;
import com.android.calendar.recurrencepicker.RecurrencePickerDialog;
import com.android.calendar.settings.GeneralPreferences;
import com.android.calendar.settings.SettingsActivity;
import com.android.calendar.calendarcommon2.EventRecurrence;
import com.android.calendar.calendarcommon2.Time;
import com.android.calendar.common.Rfc822InputFilter;
import com.android.calendar.common.Rfc822Validator;
import com.android.calendar.chips.AccountSpecifier;
import com.android.calendar.chips.BaseRecipientAdapter;
import com.android.calendar.chips.ChipsUtil;
import com.android.calendar.chips.RecipientEditTextView;
import com.android.calendar.timezonepicker.TimeZoneInfo;
import com.android.calendar.timezonepicker.TimeZonePickerDialog;
import com.android.calendar.timezonepicker.TimeZonePickerUtils;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

import ws.xsoh.etar.R;

public class EditEventView implements View.OnClickListener, DialogInterface.OnCancelListener,
        DialogInterface.OnClickListener, OnItemSelectedListener,
        RecurrencePickerDialog.OnRecurrenceSetListener,
        TimeZonePickerDialog.OnTimeZoneSetListener {

    private static final String TAG = "EditEvent";
    private static final String GOOGLE_SECONDARY_CALENDAR = "calendar.google.com";
    private static final String PERIOD_SPACE = ". ";

    private static final String FRAG_TAG_TIME_ZONE_PICKER = "timeZonePickerDialogFragment";
    private static final String FRAG_TAG_RECUR_PICKER = "recurrencePickerDialogFragment";
    private static StringBuilder mSB = new StringBuilder(50);
    private static Formatter mF = new Formatter(mSB, Locale.getDefault());
    /**
     * From com.google.android.gm.ComposeActivity Implements special address
     * cleanup rules: The first space key entry following an "@" symbol that is
     * followed by any combination of letters and symbols, including one+ dots
     * and zero commas, should insert an extra comma (followed by the space).
     */
    private static InputFilter[] sRecipientFilters = new InputFilter[]{new Rfc822InputFilter()};
    public boolean mIsMultipane;
    ArrayList<View> mEditOnlyList = new ArrayList<View>();
    ArrayList<View> mEditViewList = new ArrayList<View>();
    ArrayList<View> mViewOnlyList = new ArrayList<View>();
    TextView mLoadingMessage;
    ScrollView mScrollView;
    Button mStartDateButton;
    Button mEndDateButton;
    Button mStartTimeButton;
    Button mEndTimeButton;
    Button mTimezoneButton;
    View mColorPicker;
    View mTimezoneRow;
    TextView mStartTimeHome;
    TextView mStartDateHome;
    TextView mEndTimeHome;
    TextView mEndDateHome;
    SwitchCompat mAllDayCheckBox;
    Spinner mCalendarsSpinner;
    Button mRruleButton;
    Spinner mAvailabilitySpinner;
    Spinner mAccessLevelSpinner;
    RadioGroup mResponseRadioGroup;
    TextView mTitleTextView;
    AutoCompleteTextView mLocationTextView;
    EventLocationAdapter mLocationAdapter;
    TextView mDescriptionTextView;
    TextView mUrlTextView;
    TextView mWhenView;
    TextView mTimezoneTextView;
    MultiAutoCompleteTextView mAttendeesList;
    View mCalendarSelectorGroupBackground;
    View mLocationGroup;
    View mDescriptionGroup;
    View mUrlGroup;
    View mRemindersGroup;
    View mResponseGroup;
    View mOrganizerGroup;
    View mAttendeesGroup;
    View mStartHomeGroup;
    View mEndHomeGroup;
    private int[] mOriginalPadding = new int[4];
    private ProgressDialog mLoadingCalendarsDialog;
    private AlertDialog mNoCalendarsDialog;

    private AppCompatActivity mActivity;
    private EditDoneRunnable mDone;
    private View mView;
    private CalendarEventModel mModel;
    private Cursor mCalendarsCursor;
    private AccountSpecifier mAddressAdapter;
    private Rfc822Validator mEmailValidator;
    private TimePickerDialog mStartTimePickerDialog;
    private TimePickerDialog mEndTimePickerDialog;
    private DatePickerDialog mDatePickerDialog;
    /**
     * Contents of the "minutes" spinner.  This has default values from the XML file, augmented
     * with any additional values that were already associated with the event.
     */
    private ArrayList<Integer> mReminderMinuteValues;
    private ArrayList<String> mReminderMinuteLabels;
    /**
     * Contents of the "methods" spinner.  The "values" list specifies the method constant
     * (e.g. {@link Reminders#METHOD_ALERT}) associated with the labels.  Any methods that
     * aren't allowed by the Calendar will be removed.
     */
    private ArrayList<Integer> mReminderMethodValues;
    private ArrayList<String> mReminderMethodLabels;
    /**
     * Contents of the "availability" spinner. The "values" list specifies the
     * type constant (e.g. {@link Events#AVAILABILITY_BUSY}) associated with the
     * labels. Any types that aren't allowed by the Calendar will be removed.
     */
    private ArrayList<Integer> mAvailabilityValues;
    private ArrayList<String> mAvailabilityLabels;
    private ArrayList<String> mAccessLabels;
    private ArrayList<String> mOriginalAvailabilityLabels;
    private ArrayAdapter<String> mAvailabilityAdapter;
    private ArrayAdapter<String> mAccessAdapter;
    private boolean mAvailabilityExplicitlySet;
    private boolean mAllDayChangingAvailability;
    private int mAvailabilityCurrentlySelected;
    private int mDefaultReminderMinutes;
    private boolean mSaveAfterQueryComplete = false;
    private TimeZonePickerUtils mTzPickerUtils;
    private Time mStartTime;
    private Time mEndTime;
    private String mTimezone;
    private boolean mAllDay = false;
    private int mModification = EditEventHelper.MODIFY_UNINITIALIZED;
    private EventRecurrence mEventRecurrence = new EventRecurrence();
    private ArrayList<ConstraintLayout> mReminderItems = new ArrayList<ConstraintLayout>(0);
    private ArrayList<ReminderEntry> mUnsupportedReminders = new ArrayList<ReminderEntry>();
    private String mRrule;

    public EditEventView(AppCompatActivity activity, View view, EditDoneRunnable done) {

        mActivity = activity;
        mView = view;
        mDone = done;

        // cache top level view elements
        mLoadingMessage = (TextView) view.findViewById(R.id.loading_message);
        mScrollView = (ScrollView) view.findViewById(R.id.scroll_view);

        // cache all the widgets
        mCalendarsSpinner = (Spinner) view.findViewById(R.id.calendars_spinner);
        mTitleTextView = (TextView) view.findViewById(R.id.title);
        mLocationTextView = (AutoCompleteTextView) view.findViewById(R.id.location);
        mDescriptionTextView = (TextView) view.findViewById(R.id.description);
        mUrlTextView = (TextView) view.findViewById(R.id.url);
        mStartDateButton = (Button) view.findViewById(R.id.start_date);
        mEndDateButton = (Button) view.findViewById(R.id.end_date);
        mWhenView = (TextView) mView.findViewById(R.id.when);
        mTimezoneTextView = (TextView) mView.findViewById(R.id.timezone_textView);
        mStartTimeButton = (Button) view.findViewById(R.id.start_time);
        mEndTimeButton = (Button) view.findViewById(R.id.end_time);
        mTimezoneButton = (Button) view.findViewById(R.id.timezone_button);
        mTimezoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimezoneDialog();
            }
        });
        mTimezoneRow = view.findViewById(R.id.timezone_button_row);
        mStartTimeHome = (TextView) view.findViewById(R.id.start_time_home_tz);
        mStartDateHome = (TextView) view.findViewById(R.id.start_date_home_tz);
        mEndTimeHome = (TextView) view.findViewById(R.id.end_time_home_tz);
        mEndDateHome = (TextView) view.findViewById(R.id.end_date_home_tz);
        mAllDayCheckBox = view.findViewById(R.id.is_all_day);
        mRruleButton = (Button) view.findViewById(R.id.rrule);
        mAvailabilitySpinner = (Spinner) view.findViewById(R.id.availability);
        mAccessLevelSpinner = (Spinner) view.findViewById(R.id.visibility);
        mCalendarSelectorGroupBackground = view.findViewById(R.id.calendar_selector_group_background);
        mRemindersGroup = view.findViewById(R.id.reminder_items_container);
        mResponseGroup = view.findViewById(R.id.response_group);
        mOrganizerGroup = view.findViewById(R.id.organizer_row);
        mAttendeesGroup = view.findViewById(R.id.add_attendees_group);
        mLocationGroup = view.findViewById(R.id.where_row);
        mDescriptionGroup = view.findViewById(R.id.description_row);
        mUrlGroup = view.findViewById(R.id.url_row);
        mStartHomeGroup = view.findViewById(R.id.from_row_home_tz);
        mEndHomeGroup = view.findViewById(R.id.to_row_home_tz);
        mAttendeesList = (MultiAutoCompleteTextView) view.findViewById(R.id.attendees);

        mColorPicker = view.findViewById(R.id.change_color);

        mTitleTextView.setTag(mTitleTextView.getBackground());
        mLocationTextView.setTag(mLocationTextView.getBackground());
        mLocationAdapter = new EventLocationAdapter(activity);
        mLocationTextView.setAdapter(mLocationAdapter);
        mLocationTextView.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // Dismiss the suggestions dropdown.  Return false so the other
                    // side effects still occur (soft keyboard going away, etc.).
                    mLocationTextView.dismissDropDown();
                }
                return false;
            }
        });

        mAvailabilityExplicitlySet = false;
        mAllDayChangingAvailability = false;
        mAvailabilityCurrentlySelected = -1;
        mAvailabilitySpinner.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                                               View view, int position, long id) {
                        // The spinner's onItemSelected gets called while it is being
                        // initialized to the first item, and when we explicitly set it
                        // in the allDay checkbox toggling, so we need these checks to
                        // find out when the spinner is actually being clicked.

                        // Set the initial selection.
                        if (mAvailabilityCurrentlySelected == -1) {
                            mAvailabilityCurrentlySelected = position;
                        }

                        if (mAvailabilityCurrentlySelected != position &&
                                !mAllDayChangingAvailability) {
                            mAvailabilityExplicitlySet = true;
                        } else {
                            mAvailabilityCurrentlySelected = position;
                            mAllDayChangingAvailability = false;
                }
            }

                    @Override
                    public void onNothingSelected(AdapterView<?> arg0) {
                    }
                });


        mDescriptionTextView.setTag(mDescriptionTextView.getBackground());
        mUrlTextView.setTag(mUrlTextView.getBackground());
        mAttendeesList.setTag(mAttendeesList.getBackground());
        mOriginalPadding[0] = mLocationTextView.getPaddingLeft();
        mOriginalPadding[1] = mLocationTextView.getPaddingTop();
        mOriginalPadding[2] = mLocationTextView.getPaddingRight();
        mOriginalPadding[3] = mLocationTextView.getPaddingBottom();
        mEditViewList.add(mTitleTextView);
        mEditViewList.add(mLocationTextView);
        mEditViewList.add(mDescriptionTextView);
        mEditViewList.add(mUrlTextView);
        mEditViewList.add(mAttendeesList);

        mViewOnlyList.add(view.findViewById(R.id.when_row));
        mViewOnlyList.add(view.findViewById(R.id.timezone_textview_row));
        mEditOnlyList.add(view.findViewById(R.id.edit_event_all));
        mEditOnlyList.add(mStartHomeGroup);
        mEditOnlyList.add(mEndHomeGroup);

        mResponseRadioGroup = (RadioGroup) view.findViewById(R.id.response_value);

        mTimezone = Utils.getTimeZone(activity, null);
        mIsMultipane = activity.getResources().getBoolean(R.bool.tablet_config);
        mStartTime = new Time(mTimezone);
        mEndTime = new Time(mTimezone);
        mEmailValidator = new Rfc822Validator(null);
        initMultiAutoCompleteTextView((RecipientEditTextView) mAttendeesList);

        // Display loading screen
        setModel(null);

        FragmentManager fm = activity.getSupportFragmentManager();
        RecurrencePickerDialog rpd = (RecurrencePickerDialog) fm
                .findFragmentByTag(FRAG_TAG_RECUR_PICKER);
        if (rpd != null) {
            rpd.setOnRecurrenceSetListener(this);
        }
        TimeZonePickerDialog tzpd = (TimeZonePickerDialog) fm
                .findFragmentByTag(FRAG_TAG_TIME_ZONE_PICKER);
        if (tzpd != null) {
            tzpd.setOnTimeZoneSetListener(this);
        }

    }

    /**
     * Loads an integer array asset into a list.
     */
    private static ArrayList<Integer> loadIntegerArray(Resources r, int resNum) {
        int[] vals = r.getIntArray(resNum);
        int size = vals.length;
        ArrayList<Integer> list = new ArrayList<Integer>(size);

        for (int i = 0; i < size; i++) {
            list.add(vals[i]);
        }

        return list;
    }

    /**
     * Loads a String array asset into a list.
     */
    private static ArrayList<String> loadStringArray(Resources r, int resNum) {
        String[] labels = r.getStringArray(resNum);
        ArrayList<String> list = new ArrayList<String>(Arrays.asList(labels));
        return list;
    }

    // Fills in the date and time fields
    private void populateWhen() {
        long startMillis = mStartTime.toMillis();
        long endMillis = mEndTime.toMillis();
        setDate(mStartDateButton, startMillis);
        setDate(mEndDateButton, endMillis);

        setTime(mStartTimeButton, startMillis);
        setTime(mEndTimeButton, endMillis);

        mStartDateButton.setOnClickListener(this::showDatePickerDialog);
        mEndDateButton.setOnClickListener(this::showDatePickerDialog);

        mStartTimeButton.setOnClickListener(this::showTimerPickerDialog);
        mEndTimeButton.setOnClickListener(this::showTimerPickerDialog);
    }

    // Implements OnTimeZoneSetListener
    @Override
    public void onTimeZoneSet(TimeZoneInfo tzi) {
        setTimezone(tzi.mTzId);
        updateHomeTime();
    }

    private void setTimezone(String timeZone) {
        mTimezone = timeZone;
        Utils.changeTimezoneOnly(mStartTime, mTimezone);
        Utils.changeTimezoneOnly(mEndTime, mTimezone);
        long startMillis = mStartTime.normalize();
        mEndTime.normalize();

        populateTimezone(startMillis);
    }

    private void populateTimezone(long eventStartTime) {
        if (mTzPickerUtils == null) {
            mTzPickerUtils = new TimeZonePickerUtils(mActivity);
        }
        CharSequence displayName =
                mTzPickerUtils.getGmtDisplayName(mActivity, mTimezone, eventStartTime, true);

        mTimezoneTextView.setText(displayName);
        mTimezoneButton.setText(displayName);
    }

    private void showTimezoneDialog() {
        Bundle b = new Bundle();
        b.putLong(TimeZonePickerDialog.BUNDLE_START_TIME_MILLIS, mStartTime.toMillis());
        b.putString(TimeZonePickerDialog.BUNDLE_TIME_ZONE, mTimezone);

        FragmentManager fm = mActivity.getSupportFragmentManager();
        TimeZonePickerDialog tzpd = (TimeZonePickerDialog) fm
                .findFragmentByTag(FRAG_TAG_TIME_ZONE_PICKER);
        if (tzpd != null) {
            tzpd.dismiss();
        }
        tzpd = new TimeZonePickerDialog();
        tzpd.setArguments(b);
        tzpd.setOnTimeZoneSetListener(EditEventView.this);
        tzpd.show(fm, FRAG_TAG_TIME_ZONE_PICKER);
    }

    private void populateRepeats() {
        Resources r = mActivity.getResources();
        String repeatString;
        boolean enabled;
        if (!TextUtils.isEmpty(mRrule)) {
            repeatString = EventRecurrenceFormatter.getRepeatString(mActivity, r,
                    mEventRecurrence, true);

            if (repeatString == null) {
                repeatString = r.getString(R.string.custom);
                Log.e(TAG, "Can't generate display string for " + mRrule);
                enabled = false;
            } else {
                // TODO Should give option to clear/reset rrule
                enabled = RecurrencePickerDialog.canHandleRecurrenceRule(mEventRecurrence);
                if (!enabled) {
                    Log.e(TAG, "UI can't handle " + mRrule);
                }
            }
        } else {
            repeatString = r.getString(R.string.does_not_repeat);
            enabled = true;
        }

        mRruleButton.setText(repeatString);

        // Don't allow the user to make exceptions recurring events.
        if (mModel.mOriginalSyncId != null) {
            enabled = false;
        }
        mRruleButton.setOnClickListener(this);
        mRruleButton.setEnabled(enabled);
    }

    /**
     * Does prep steps for saving a calendar event.
     *
     * This triggers a parse of the attendees list and checks if the event is
     * ready to be saved. An event is ready to be saved so long as a model
     * exists and has a calendar it can be associated with, either because it's
     * an existing event or we've finished querying.
     *
     * @return false if there is no model or no calendar had been loaded yet,
     * true otherwise.
     */
    public boolean prepareForSave() {
        if (mModel == null || (mCalendarsCursor == null && mModel.mUri == null)) {
            return false;
        }
        return fillModelFromUI();
    }

    public boolean fillModelFromReadOnlyUi() {
        if (mModel == null || (mCalendarsCursor == null && mModel.mUri == null)) {
            return false;
        }
        mModel.mReminders = EventViewUtils.reminderItemsToReminders(
                    mReminderItems, mReminderMinuteValues, mReminderMethodValues);
        mModel.mReminders.addAll(mUnsupportedReminders);
        mModel.normalizeReminders();
        int status = EventInfoFragment.getResponseFromButtonId(
                mResponseRadioGroup.getCheckedRadioButtonId());
        if (status != Attendees.ATTENDEE_STATUS_NONE) {
            mModel.mSelfAttendeeStatus = status;
        }
        return true;
    }

    // This is called if the user clicks on one of the buttons: "Save",
    // "Discard", or "Delete". This is also called if the user clicks
    // on the "remove reminder" button.
    @Override
    public void onClick(View view) {
        if (view == mRruleButton) {
            Bundle b = new Bundle();
            b.putLong(RecurrencePickerDialog.BUNDLE_START_TIME_MILLIS,
                    mStartTime.toMillis());
            b.putString(RecurrencePickerDialog.BUNDLE_TIME_ZONE, mStartTime.getTimezone());

            // TODO may be more efficient to serialize and pass in EventRecurrence
            b.putString(RecurrencePickerDialog.BUNDLE_RRULE, mRrule);

            FragmentManager fm = mActivity.getSupportFragmentManager();
            RecurrencePickerDialog rpd = (RecurrencePickerDialog) fm
                    .findFragmentByTag(FRAG_TAG_RECUR_PICKER);
            if (rpd != null) {
                rpd.dismiss();
            }
            rpd = new RecurrencePickerDialog();
            rpd.setArguments(b);
            rpd.setOnRecurrenceSetListener(EditEventView.this);
            rpd.show(fm, FRAG_TAG_RECUR_PICKER);
            return;
        }

        // This must be a click on one of the "remove reminder" buttons
        ConstraintLayout reminderItem = (ConstraintLayout) view.getParent();
        LinearLayout parent = (LinearLayout) reminderItem.getParent();
        parent.removeView(reminderItem);
        mReminderItems.remove(reminderItem);
        updateRemindersVisibility(mReminderItems.size());
        EventViewUtils.updateAddReminderButton(mView, mReminderItems, mModel.mCalendarMaxReminders);
    }

    @Override
    public void onRecurrenceSet(String rrule) {
        Log.d(TAG, "Old rrule:" + mRrule);
        Log.d(TAG, "New rrule:" + rrule);
        mRrule = rrule;
        if (mRrule != null) {
            mEventRecurrence.parse(mRrule);
        }
        populateRepeats();
    }

    // This is called if the user cancels the "No calendars" dialog.
    // The "No calendars" dialog is shown if there are no syncable calendars.
    @Override
    public void onCancel(DialogInterface dialog) {
        if (dialog == mLoadingCalendarsDialog) {
            mLoadingCalendarsDialog = null;
            mSaveAfterQueryComplete = false;
        } else if (dialog == mNoCalendarsDialog) {
            mDone.setDoneCode(Utils.DONE_REVERT);
            mDone.run();
        }
    }

    // This is called if the user clicks on a dialog button.
    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (dialog == mNoCalendarsDialog) {
            mDone.setDoneCode(Utils.DONE_REVERT);
            mDone.run();
            if (which == DialogInterface.BUTTON_POSITIVE) {
                Intent nextIntent = new Intent(mActivity, SettingsActivity.class);
                mActivity.startActivity(nextIntent);
            }
        }
    }

    // Goes through the UI elements and updates the model as necessary
    private boolean fillModelFromUI() {
        if (mModel == null) {
            return false;
        }
        mModel.mReminders = EventViewUtils.reminderItemsToReminders(mReminderItems,
                mReminderMinuteValues, mReminderMethodValues);
        mModel.mReminders.addAll(mUnsupportedReminders);
        mModel.normalizeReminders();
        mModel.mHasAlarm = mReminderItems.size() > 0;
        mModel.mTitle = mTitleTextView.getText().toString();
        mModel.mAllDay = mAllDayCheckBox.isChecked();
        mModel.mLocation = mLocationTextView.getText().toString();
        mModel.mDescription = mDescriptionTextView.getText().toString();
        mModel.mUrl = mUrlTextView.getText().toString();
        if (TextUtils.isEmpty(mModel.mLocation)) {
            mModel.mLocation = null;
        }
        if (TextUtils.isEmpty(mModel.mDescription)) {
            mModel.mDescription = null;
        }
        if (TextUtils.isEmpty(mModel.mUrl)) {
            mModel.mUrl = null;
        }

        int status = EventInfoFragment.getResponseFromButtonId(mResponseRadioGroup
                .getCheckedRadioButtonId());
        if (status != Attendees.ATTENDEE_STATUS_NONE) {
            mModel.mSelfAttendeeStatus = status;
        }

        if (mAttendeesList != null) {
            mEmailValidator.setRemoveInvalid(true);
            mAttendeesList.performValidation();
            mModel.mAttendeesList.clear();
            mModel.addAttendees(mAttendeesList.getText().toString(), mEmailValidator);
            mEmailValidator.setRemoveInvalid(false);
        }

        mModel.mCalendarId = mCalendarsSpinner.getSelectedItemId();
        int calendarCursorPosition = mCalendarsSpinner.getSelectedItemPosition();
        if (mCalendarsCursor.moveToPosition(calendarCursorPosition)) {
            String calendarOwner = mCalendarsCursor.getString(
                    EditEventHelper.CALENDARS_INDEX_OWNER_ACCOUNT);
            String calendarName = mCalendarsCursor.getString(
                    EditEventHelper.CALENDARS_INDEX_DISPLAY_NAME);
            String defaultCalendar = calendarOwner + "/" + calendarName;
            Utils.setSharedPreference(
                    mActivity, GeneralPreferences.KEY_DEFAULT_CALENDAR, defaultCalendar);
            mModel.mOwnerAccount = calendarOwner;
            mModel.mOrganizer = calendarOwner;
            mModel.mCalendarId = mCalendarsCursor.getLong(EditEventHelper.CALENDARS_INDEX_ID);
        }

        if (mModel.mAllDay) {
            // Reset start and end time without touching date;
            // in model, increment the monthDay by 1, and set
            // the timezone to UTC, as required for all-day events.
            mStartTime.setHour(0);
            mStartTime.setMinute(0);
            mStartTime.setSecond(0);
            mStartTime.normalize();
            Time modelStartTime = new Time(Time.TIMEZONE_UTC);
            modelStartTime.set(0, 0, 0, mStartTime.getDay(), mStartTime.getMonth(), mStartTime.getYear());
            mModel.mStart = modelStartTime.normalize();

            mEndTime.setHour(0);
            mEndTime.setMinute(0);
            mEndTime.setSecond(0);
            mEndTime.normalize();
            Time modelEndTime = new Time(Time.TIMEZONE_UTC);
            modelEndTime.set(0, 0, 0, mEndTime.getDay(), mEndTime.getMonth(), mEndTime.getYear());
            // When a user see the event duration as "X - Y" (e.g. Oct. 28 - Oct. 29), model's end time
            // should be Y + 1 (Oct.30), but display end time should be Y (Oct. 29).
            final long normalizedEndTimeMillis =
                    modelEndTime.normalize() + DateUtils.DAY_IN_MILLIS;
            if (normalizedEndTimeMillis < mModel.mStart) {
                // mModel.mEnd should be midnight of the next day of mStart
                // but mEndTime same day as mStart
                mModel.mEnd = mModel.mStart + DateUtils.DAY_IN_MILLIS;
                modelEndTime.set(mModel.mStart);
                // cannot set to mModel.mStart because mEndTime is not necessarily in the same timezone,
                // so midnight of same day is not same absolute time point in millis
                mEndTime.set(0, 0, 0, modelStartTime.getDay(), modelStartTime.getMonth(), modelStartTime.getYear());
                mEndTime.normalize();
            } else {
                mModel.mEnd = normalizedEndTimeMillis;
            }

            mModel.mTimezone = Time.TIMEZONE_UTC;

            // refresh UI to new start & end times
            setDate(mStartDateButton, mStartTime.toMillis());
            setTime(mStartTimeButton, mStartTime.toMillis());
            setDate(mEndDateButton, mEndTime.toMillis());
            setTime(mEndTimeButton, mEndTime.toMillis());
        } else {
            mStartTime.setTimezone(mTimezone);
            mEndTime.setTimezone(mTimezone);
            mModel.mStart = mStartTime.toMillis();
            mModel.mEnd = mEndTime.toMillis();
            mModel.mTimezone = mTimezone;
        }
        mModel.mAccessLevel = mAccessLevelSpinner.getSelectedItemPosition();
        // TODO set correct availability value
        mModel.mAvailability = mAvailabilityValues.get(mAvailabilitySpinner
                .getSelectedItemPosition());

        // rrrule
        // If we're making an exception we don't want it to be a repeating
        // event.
        if (mModification == EditEventHelper.MODIFY_SELECTED) {
            mModel.mRrule = null;
        } else {
            mModel.mRrule = mRrule;
        }

        return true;
    }

    private void prepareAccess() {
        Resources r = mActivity.getResources();
        mAccessLabels = loadStringArray(r, R.array.visibility);
        mAccessAdapter = new ArrayAdapter<String>(mActivity,
                R.layout.simple_spinner_item, mAccessLabels);
        mAccessAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mAccessLevelSpinner.setAdapter(mAccessAdapter);
        mAccessLevelSpinner.setSelection(mModel.mAccessLevel);
    }

    private void prepareAvailability() {
        Resources r = mActivity.getResources();

        mAvailabilityValues = loadIntegerArray(r, R.array.availability_values);
        mAvailabilityLabels = loadStringArray(r, R.array.availability);
        // Copy the unadulterated availability labels for all-day toggling.
        mOriginalAvailabilityLabels = new ArrayList<String>();
        mOriginalAvailabilityLabels.addAll(mAvailabilityLabels);

        if (mModel.mCalendarAllowedAvailability != null) {
            EventViewUtils.reduceMethodList(mAvailabilityValues, mAvailabilityLabels,
                    mModel.mCalendarAllowedAvailability);
        }

        mAvailabilityAdapter = new ArrayAdapter<String>(mActivity,
                R.layout.simple_spinner_item, mAvailabilityLabels);
        mAvailabilityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mAvailabilitySpinner.setAdapter(mAvailabilityAdapter);

        int availIndex = mAvailabilityValues.indexOf(mModel.mAvailability);
        if (availIndex != -1) {
            mAvailabilitySpinner.setSelection(availIndex);
        }
        mAvailabilityExplicitlySet = mModel.mAvailabilityExplicitlySet;
    }

    /**
     * Prepares the reminder UI elements.
     * <p>
     * (Re-)loads the minutes / methods lists from the XML assets, adds/removes items as
     * needed for the current set of reminders and calendar properties, and then creates UI
     * elements.
     */
    private void prepareReminders() {
        CalendarEventModel model = mModel;
        Resources r = mActivity.getResources();

        // Load the labels and corresponding numeric values for the minutes and methods lists
        // from the assets.  If we're switching calendars, we need to clear and re-populate the
        // lists (which may have elements added and removed based on calendar properties).  This
        // is mostly relevant for "methods", since we shouldn't have any "minutes" values in a
        // new event that aren't in the default set.
        mReminderMinuteValues = loadIntegerArray(r, R.array.reminder_minutes_values);
        mReminderMinuteLabels = EventViewUtils.constructReminderLabelsFromValues(mActivity,
                mReminderMinuteValues, false);
        mReminderMethodValues = loadIntegerArray(r, R.array.reminder_methods_values);
        mReminderMethodLabels = loadStringArray(r, R.array.reminder_methods_labels);

        // Remove any reminder methods that aren't allowed for this calendar.  If this is
        // a new event, mCalendarAllowedReminders may not be set the first time we're called.
        if (mModel.mCalendarAllowedReminders != null) {
            EventViewUtils.reduceMethodList(mReminderMethodValues, mReminderMethodLabels,
                    mModel.mCalendarAllowedReminders);
        }

        int numReminders = 0;
        if (model.mHasAlarm) {
            ArrayList<ReminderEntry> reminders = model.mReminders;
            numReminders = reminders.size();
            // Insert any minute values that aren't represented in the minutes list.
            for (ReminderEntry re : reminders) {
                if (mReminderMethodValues.contains(re.getMethod())) {
                    EventViewUtils.addMinutesToList(mActivity, mReminderMinuteValues,
                            mReminderMinuteLabels, re.getMinutes());
                }
            }

            // Create a UI element for each reminder.  We display all of the reminders we get
            // from the provider, even if the count exceeds the calendar maximum.  (Also, for
            // a new event, we won't have a maxReminders value available.)
            mUnsupportedReminders.clear();
            for (ReminderEntry re : reminders) {
                if (mReminderMethodValues.contains(re.getMethod())
                        || re.getMethod() == Reminders.METHOD_DEFAULT) {
                    EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderItems,
                            mReminderMinuteValues, mReminderMinuteLabels, mReminderMethodValues,
                            mReminderMethodLabels, re, Integer.MAX_VALUE, null);
                } else {
                    // TODO figure out a way to display unsupported reminders
                    mUnsupportedReminders.add(re);
                }
            }
        }

        updateRemindersVisibility(numReminders);
        EventViewUtils.updateAddReminderButton(mView, mReminderItems, mModel.mCalendarMaxReminders);
    }

    /**
     * Fill in the view with the contents of the given event model. This allows
     * an edit view to be initialized before the event has been loaded. Passing
     * in null for the model will display a loading screen. A non-null model
     * will fill in the view's fields with the data contained in the model.
     *
     * @param model The event model to pull the data from
     */
    public void setModel(CalendarEventModel model) {
        mModel = model;

        // Need to close the autocomplete adapter to prevent leaking cursors.
        if (mAddressAdapter != null && mAddressAdapter instanceof EmailAddressAdapter) {
            ((EmailAddressAdapter)mAddressAdapter).close();
            mAddressAdapter = null;
        }

        if (model == null) {
            // Display loading screen
            mLoadingMessage.setVisibility(View.VISIBLE);
            mScrollView.setVisibility(View.GONE);
            return;
        }

        boolean canRespond = EditEventHelper.canRespond(model);

        {
            long begin = model.mStart;
            long end = model.mEnd;
            mTimezone = model.mTimezone; // this will be UTC for all day events

            // Set up the starting times
            if (begin > 0) {
                mStartTime.setTimezone(mTimezone);
                mStartTime.set(begin);
                mStartTime.normalize();
            }
            if (end > 0) {
                mEndTime.setTimezone(mTimezone);
                mEndTime.set(end);
                mEndTime.normalize();
            }
        }

        mRrule = model.mRrule;
        if (!TextUtils.isEmpty(mRrule)) {
            mEventRecurrence.parse(mRrule);
        }

        if (mEventRecurrence.startDate == null) {
            mEventRecurrence.startDate = mStartTime;
        }

        // If the user is allowed to change the attendees set up the view and
        // validator
        if (!model.mHasAttendeeData) {
            mAttendeesGroup.setVisibility(View.GONE);
        }

        mAllDayCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setAllDayViewsVisibility(isChecked);
                if(!isChecked) {
                    resetToDefaultDuration();
                }
            }
        });

        boolean prevAllDay = mAllDayCheckBox.isChecked();
        mAllDay = false; // default to false. Let setAllDayViewsVisibility update it as needed
        if (model.mAllDay) {
            mAllDayCheckBox.setChecked(true);
            // put things back in local time for all day events
            // and force time at midnight
            // also be robust against model having non-normalised all day event
            // (start or end not midnight UTC or timezone not UTC), and force that
            mTimezone = Utils.getTimeZone(mActivity, null);
            {
                int year = mStartTime.getYear();
                int month = mStartTime.getMonth();
                int day = mStartTime.getDay();
                mStartTime.setTimezone(Time.TIMEZONE_UTC);
                mStartTime.set(0, 0, 0, day, month, year);
                model.mStart = mStartTime.normalize();
                mStartTime.setTimezone(mTimezone);
                mStartTime.set(0, 0, 0, day, month, year);
                mStartTime.normalize();
            }
            {
                int year = mEndTime.getYear();
                int month = mEndTime.getMonth();
                int day = mEndTime.getDay();
                mEndTime.setTimezone(Time.TIMEZONE_UTC);
                mEndTime.set(0, 0, 0, day, month, year);
                model.mEnd = mEndTime.normalize();
                mEndTime.setTimezone(mTimezone);
                mEndTime.set(0, 0, 0, day, month, year);
                mEndTime.normalize();
            }
            // refresh UI to new start & end times
            setDate(mStartDateButton, mStartTime.toMillis());
            setTime(mStartTimeButton, mStartTime.toMillis());
            setDate(mEndDateButton, mEndTime.toMillis());
            setTime(mEndTimeButton, mEndTime.toMillis());
        } else {
            mAllDayCheckBox.setChecked(false);
        }
        // On a rotation we need to update the views but onCheckedChanged
        // doesn't get called
        if (prevAllDay == mAllDayCheckBox.isChecked()) {
            setAllDayViewsVisibility(prevAllDay);
        }

        populateTimezone(mStartTime.normalize());

        SharedPreferences prefs = GeneralPreferences.Companion.getSharedPreferences(mActivity);
        String defaultReminderString = prefs.getString(
                GeneralPreferences.KEY_DEFAULT_REMINDER, GeneralPreferences.NO_REMINDER_STRING);
        mDefaultReminderMinutes = Integer.parseInt(defaultReminderString);

        prepareReminders();
        prepareAvailability();
        prepareAccess();

        View reminderAddButton = mView.findViewById(R.id.reminder_add);
        View.OnClickListener addReminderOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addReminder();
            }
        };
        reminderAddButton.setOnClickListener(addReminderOnClickListener);

        if (!mIsMultipane) {
            mView.findViewById(R.id.is_all_day_label).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mAllDayCheckBox.setChecked(!mAllDayCheckBox.isChecked());
                        }
                    });
        }

        if (model.mTitle != null) {
            mTitleTextView.setTextKeepState(model.mTitle);
        }

        if (model.mIsOrganizer || TextUtils.isEmpty(model.mOrganizer)
                || model.mOrganizer.endsWith(GOOGLE_SECONDARY_CALENDAR)) {
            mView.findViewById(R.id.organizer_label).setVisibility(View.GONE);
            mView.findViewById(R.id.organizer).setVisibility(View.GONE);
            mOrganizerGroup.setVisibility(View.GONE);
        } else {
            ((TextView) mView.findViewById(R.id.organizer)).setText(model.mOrganizerDisplayName);
        }

        if (model.mLocation != null) {
            mLocationTextView.setTextKeepState(model.mLocation);
        }

        if (model.mDescription != null) {
            mDescriptionTextView.setTextKeepState(model.mDescription);
        }
        if (model.mUrl != null) {
            mUrlTextView.setTextKeepState(model.mUrl);
        }

        View responseLabel = mView.findViewById(R.id.response_label);
        if (canRespond) {
            int buttonToCheck = EventInfoFragment
                    .findButtonIdForResponse(model.mSelfAttendeeStatus);
            mResponseRadioGroup.check(buttonToCheck); // -1 clear all radio buttons
            mResponseRadioGroup.setVisibility(View.VISIBLE);
            responseLabel.setVisibility(View.VISIBLE);
        } else {
            responseLabel.setVisibility(View.GONE);
            mResponseRadioGroup.setVisibility(View.GONE);
            mResponseGroup.setVisibility(View.GONE);
        }

        if (model.isEventColorInitialized()) {
            updateHeadlineColor(model.getEventColor());
        }

        populateWhen();
        populateRepeats();
        updateAttendees(model.mAttendeesList);

        updateView();
        mScrollView.setVisibility(View.VISIBLE);
        mLoadingMessage.setVisibility(View.GONE);
        setTitleFocus();
        sendAccessibilityEvent();
    }

    public void updateHeadlineColor(int displayColor) {
        setSpinnerBackgroundColor(displayColor);
    }

    private void setSpinnerBackgroundColor(int displayColor) {
        mCalendarSelectorGroupBackground.setBackgroundColor(displayColor);
    }

    private void setTitleFocus() {
        mTitleTextView.requestFocus();
        ((TextInputEditText)mTitleTextView).setSelection(mTitleTextView.getText().length());
    }

    private void sendAccessibilityEvent() {
        AccessibilityManager am =
            (AccessibilityManager) mActivity.getSystemService(Service.ACCESSIBILITY_SERVICE);
        if (!am.isEnabled() || mModel == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        addFieldsRecursive(b, mView);
        CharSequence msg = b.toString();

        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        event.setClassName(getClass().getName());
        event.setPackageName(mActivity.getPackageName());
        event.getText().add(msg);
        event.setAddedCount(msg.length());

        am.sendAccessibilityEvent(event);
    }

    private void addFieldsRecursive(StringBuilder b, View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) {
            return;
        }
        if (v instanceof TextView) {
            CharSequence tv = ((TextView) v).getText();
            if (!TextUtils.isEmpty(tv.toString().trim())) {
                b.append(tv + PERIOD_SPACE);
            }
        } else if (v instanceof RadioGroup) {
            RadioGroup rg = (RadioGroup) v;
            int id = rg.getCheckedRadioButtonId();
            if (id != View.NO_ID) {
                b.append(((RadioButton) (v.findViewById(id))).getText() + PERIOD_SPACE);
            }
        } else if (v instanceof Spinner) {
            Spinner s = (Spinner) v;
            if (s.getSelectedItem() instanceof String) {
                String str = ((String) (s.getSelectedItem())).trim();
                if (!TextUtils.isEmpty(str)) {
                    b.append(str + PERIOD_SPACE);
                }
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            int children = vg.getChildCount();
            for (int i = 0; i < children; i++) {
                addFieldsRecursive(b, vg.getChildAt(i));
            }
        }
    }

    /**
     * Creates a single line string for the time/duration
     */
    protected void setWhenString() {
        String when;
        int flags = DateUtils.FORMAT_SHOW_DATE;
        String tz = mTimezone;
        if (mModel.mAllDay) {
            flags |= DateUtils.FORMAT_SHOW_WEEKDAY;
            tz = Time.TIMEZONE_UTC;
        } else {
            flags |= DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(mActivity)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
        }
        long startMillis = mStartTime.normalize();
        long endMillis = mEndTime.normalize();
        mSB.setLength(0);
        when = DateUtils
                .formatDateRange(mActivity, mF, startMillis, endMillis, flags, tz).toString();
        mWhenView.setText(when);
    }

    /**
     * Configures the Calendars spinner.
     *
     * We tuck a reference to a Cursor with calendar database data into the spinner, so that
     * we can easily extract calendar-specific values when the value changes (the spinner's
     * onItemSelected callback is configured).
     */
    public void setCalendarsCursor(Cursor cursor, boolean userVisible, long selectedCalendarId) {
        // If there are no syncable calendars, then we cannot allow
        // creating a new event.
        mCalendarsCursor = cursor;
        if (cursor == null || cursor.getCount() == 0) {
            // Cancel the "loading calendars" dialog if it exists
            if (mSaveAfterQueryComplete) {
                mLoadingCalendarsDialog.cancel();
            }
            if (!userVisible) {
                return;
            }
            // Create an error message for the user that, when clicked,
            // will exit this activity without saving the event.
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mActivity);
            builder.setTitle(R.string.no_syncable_calendars)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(R.string.no_calendars_found)
                    .setPositiveButton(R.string.add_calendar, this)
                    .setNegativeButton(android.R.string.no, this)
                    .setOnCancelListener(this);
            mNoCalendarsDialog = builder.show();
            return;
        }

        int selection;
        if (selectedCalendarId != -1) {
            selection = findSelectedCalendarPosition(cursor, selectedCalendarId);
        } else {
            selection = findDefaultCalendarPosition(cursor);
        }

        // populate the calendars spinner
        CalendarsAdapter adapter = new CalendarsAdapter(mActivity,
            R.layout.calendars_spinner_item, cursor);
        mCalendarsSpinner.setAdapter(adapter);
        mCalendarsSpinner.setOnItemSelectedListener(this);
        mCalendarsSpinner.setSelection(selection);

        if (mSaveAfterQueryComplete) {
            mLoadingCalendarsDialog.cancel();
            if (prepareForSave() && fillModelFromUI()) {
                int exit = userVisible ? Utils.DONE_EXIT : 0;
                mDone.setDoneCode(Utils.DONE_SAVE | exit);
                mDone.run();
            } else if (userVisible) {
                mDone.setDoneCode(Utils.DONE_EXIT);
                mDone.run();
            } else if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "SetCalendarsCursor:Save failed and unable to exit view");
            }
        }
    }

    /**
     * Updates the view based on {@link #mModification} and {@link #mModel}
     */
    public void updateView() {
        if (mModel == null) {
            return;
        }
        if (EditEventHelper.canModifyEvent(mModel)) {
            setViewStates(mModification);
        } else {
            setViewStates(Utils.MODIFY_UNINITIALIZED);
        }
    }

    private void setViewStates(int mode) {
        // Extra canModify check just in case
        if (mode == Utils.MODIFY_UNINITIALIZED || !EditEventHelper.canModifyEvent(mModel)) {
            setWhenString();

            for (View v : mViewOnlyList) {
                v.setVisibility(View.VISIBLE);
            }
            for (View v : mEditOnlyList) {
                v.setVisibility(View.GONE);
            }
            for (View v : mEditViewList) {
                v.setEnabled(false);
                v.setBackgroundDrawable(null);
            }
            mRruleButton.setEnabled(false);
            if (EditEventHelper.canAddReminders(mModel)) {
                mRemindersGroup.setVisibility(View.VISIBLE);
            } else {
                mRemindersGroup.setVisibility(View.GONE);
            }
            if (TextUtils.isEmpty(mLocationTextView.getText())) {
                mLocationGroup.setVisibility(View.GONE);
            }
            if (TextUtils.isEmpty(mDescriptionTextView.getText())) {
                mDescriptionGroup.setVisibility(View.GONE);
            }
            if (TextUtils.isEmpty(mUrlTextView.getText())) {
                mUrlGroup.setVisibility(View.GONE);
            }
            mCalendarsSpinner.setEnabled(false);
        } else {
            for (View v : mViewOnlyList) {
                v.setVisibility(View.GONE);
            }
            for (View v : mEditOnlyList) {
                v.setVisibility(View.VISIBLE);
            }
            for (View v : mEditViewList) {
                v.setEnabled(true);
                if (v.getTag() != null) {
                    v.setBackgroundDrawable((Drawable) v.getTag());
                    v.setPadding(mOriginalPadding[0], mOriginalPadding[1], mOriginalPadding[2],
                            mOriginalPadding[3]);
                }
            }
            if (mModel.mOriginalSyncId == null) {
                mRruleButton.setEnabled(true);
            } else {
                mRruleButton.setEnabled(false);
                mRruleButton.setBackgroundDrawable(null);
            }
            mRemindersGroup.setVisibility(View.VISIBLE);

            mLocationGroup.setVisibility(View.VISIBLE);
            mDescriptionGroup.setVisibility(View.VISIBLE);
            mUrlGroup.setVisibility(View.VISIBLE);

            // disallow changing calendar for recurrences when not modifying all instances
            mCalendarsSpinner.setEnabled(mode == Utils.MODIFY_ALL);
        }
        setAllDayViewsVisibility(mAllDayCheckBox.isChecked());
    }

    public void setModification(int modifyWhich) {
        mModification = modifyWhich;
        updateView();
        updateHomeTime();
    }

    private int findSelectedCalendarPosition(Cursor calendarsCursor, long calendarId) {
        if (calendarsCursor.getCount() <= 0) {
            return -1;
        }
        int calendarIdColumn = calendarsCursor.getColumnIndexOrThrow(Calendars._ID);
        int position = 0;
        calendarsCursor.moveToPosition(-1);
        while (calendarsCursor.moveToNext()) {
            if (calendarsCursor.getLong(calendarIdColumn) == calendarId) {
                return position;
            }
            position++;
        }
        return 0;
    }

    // Find the calendar position in the cursor that matches calendar in
    // preference
    private int findDefaultCalendarPosition(Cursor calendarsCursor) {
        if (calendarsCursor.getCount() <= 0) {
            return -1;
        }

        String defaultCalendar = Utils.getSharedPreference(
                mActivity, GeneralPreferences.KEY_DEFAULT_CALENDAR, (String) null);

        int calendarsOwnerIndex = calendarsCursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT);
        int calendarNameIndex = calendarsCursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME);
        int accountNameIndex = calendarsCursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME);
        int accountTypeIndex = calendarsCursor.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE);
        int position = 0;
        calendarsCursor.moveToPosition(-1);
        while (calendarsCursor.moveToNext()) {
            String calendarOwner = calendarsCursor.getString(calendarsOwnerIndex);
            String calendarName = calendarsCursor.getString(calendarNameIndex);
            String currentCalendar = calendarOwner + "/" + calendarName;
            if (defaultCalendar == null) {
                // There is no stored default upon the first time running.  Use a primary
                // calendar in this case.
                if (calendarOwner != null &&
                        calendarOwner.equals(calendarsCursor.getString(accountNameIndex)) &&
                        !CalendarContract.ACCOUNT_TYPE_LOCAL.equals(
                                calendarsCursor.getString(accountTypeIndex))) {
                    return position;
                }
            } else if (defaultCalendar.equals(currentCalendar)) {
                // Found the default calendar.
                return position;
            }
            position++;
        }
        return 0;
    }

    private void updateAttendees(HashMap<String, Attendee> attendeesList) {
        if (attendeesList == null || attendeesList.isEmpty()) {
            return;
        }
        mAttendeesList.setText(null);
        for (Attendee attendee : attendeesList.values()) {

            // TODO: Please remove separator when Calendar uses the chips MR2 project

            // Adding a comma separator between email addresses to prevent a chips MR1.1 bug
            // in which email addresses are concatenated together with no separator.
            mAttendeesList.append(attendee.mEmail + ", ");
        }
    }

    private void updateRemindersVisibility(int numReminders) {
        if (numReminders == 0) {
            mRemindersGroup.setVisibility(View.GONE);
        } else {
            mRemindersGroup.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Add a new reminder when the user hits the "add reminder" button.  We use the default
     * reminder time and method.
     */
    private void addReminder() {
        // TODO: when adding a new reminder, make it different from the
        // last one in the list (if any).
        if (mDefaultReminderMinutes == GeneralPreferences.NO_REMINDER) {
            EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderItems,
                    mReminderMinuteValues, mReminderMinuteLabels,
                    mReminderMethodValues, mReminderMethodLabels,
                    ReminderEntry.valueOf(GeneralPreferences.REMINDER_DEFAULT_TIME),
                    mModel.mCalendarMaxReminders, null);
        } else {
            EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderItems,
                    mReminderMinuteValues, mReminderMinuteLabels,
                    mReminderMethodValues, mReminderMethodLabels,
                    ReminderEntry.valueOf(mDefaultReminderMinutes),
                    mModel.mCalendarMaxReminders, null);
        }
        updateRemindersVisibility(mReminderItems.size());
        EventViewUtils.updateAddReminderButton(mView, mReminderItems, mModel.mCalendarMaxReminders);
    }

    // From com.google.android.gm.ComposeActivity
    private MultiAutoCompleteTextView initMultiAutoCompleteTextView(RecipientEditTextView list) {
        mAddressAdapter = new RecipientAdapter(mActivity);
        list.setAdapter((BaseRecipientAdapter) mAddressAdapter);
        list.setOnFocusListShrinkRecipients(false);
        list.setTokenizer(new Rfc822Tokenizer());
        list.setValidator(mEmailValidator);

        // NOTE: assumes no other filters are set
        list.setFilters(sRecipientFilters);

        return list;
    }

    private void setDate(TextView view, long millis) {
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR
                | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_MONTH
                | DateUtils.FORMAT_ABBREV_WEEKDAY;

        // Unfortunately, DateUtils doesn't support a timezone other than the
        // default timezone provided by the system, so we have this ugly hack
        // here to trick it into formatting our time correctly. In order to
        // prevent all sorts of craziness, we synchronize on the TimeZone class
        // to prevent other threads from reading an incorrect timezone from
        // calls to TimeZone#getDefault()
        // TODO fix this if/when DateUtils allows for passing in a timezone
        String dateString;
        synchronized (TimeZone.class) {
            TimeZone.setDefault(TimeZone.getTimeZone(mTimezone));
            dateString = DateUtils.formatDateTime(mActivity, millis, flags);
            // setting the default back to null restores the correct behavior
            TimeZone.setDefault(null);
        }
        view.setText(dateString);
    }

    private void setTime(TextView view, long millis) {
        int flags = DateUtils.FORMAT_SHOW_TIME;
        flags |= DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
        if (DateFormat.is24HourFormat(mActivity)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }

        // Unfortunately, DateUtils doesn't support a timezone other than the
        // default timezone provided by the system, so we have this ugly hack
        // here to trick it into formatting our time correctly. In order to
        // prevent all sorts of craziness, we synchronize on the TimeZone class
        // to prevent other threads from reading an incorrect timezone from
        // calls to TimeZone#getDefault()
        // TODO fix this if/when DateUtils allows for passing in a timezone
        String timeString;
        synchronized (TimeZone.class) {
            TimeZone.setDefault(TimeZone.getTimeZone(mTimezone));
            timeString = DateUtils.formatDateTime(mActivity, millis, flags);
            TimeZone.setDefault(null);
        }
        view.setText(timeString);
    }

    protected void resetToDefaultDuration() {
        mEndTime.setDay(mEndTime.getDay() - 1);
        mEndTime.set(mStartTime.normalize() +
                     Utils.getDefaultEventDurationInMillis(mActivity));
        long endMillis = mEndTime.normalize();
        setDate(mEndDateButton, endMillis);
        setTime(mEndTimeButton, endMillis);
    }

    /**
     * @param isChecked
     */
    protected void setAllDayViewsVisibility(boolean isChecked) {
        if (isChecked) {
            if (mEndTime.getHour() == 0 && mEndTime.getMinute() == 0) {
                if (mAllDay != isChecked) {
                    mEndTime.setDay(mEndTime.getDay() - 1);
                }

                long endMillis = mEndTime.normalize();

                // Do not allow an event to have an end time
                // before the
                // start time.
                if (mEndTime.compareTo(mStartTime) < 0) {
                    mEndTime.set(mStartTime);
                    endMillis = mEndTime.normalize();
                }
                setDate(mEndDateButton, endMillis);
                setTime(mEndTimeButton, endMillis);
            }

            mStartTimeButton.setVisibility(View.GONE);
            mEndTimeButton.setVisibility(View.GONE);
            mTimezoneRow.setVisibility(View.GONE);
        } else {
            if (mEndTime.getHour() == 0 && mEndTime.getMinute() == 0) {
                if (mAllDay != isChecked) {
                    mEndTime.setDay(mEndTime.getDay() + 1);
                }

                long endMillis = mEndTime.normalize();
                setDate(mEndDateButton, endMillis);
                setTime(mEndTimeButton, endMillis);
            }
            mStartTimeButton.setVisibility(View.VISIBLE);
            mEndTimeButton.setVisibility(View.VISIBLE);
            mTimezoneRow.setVisibility(View.VISIBLE);
        }

        // If this is a new event, and if availability has not yet been
        // explicitly set, toggle busy/available as the inverse of all day.
        if (mModel.mUri == null && !mAvailabilityExplicitlySet) {
            // Values are from R.arrays.availability_values.
            // 0 = busy
            // 1 = available
            int newAvailabilityValue = isChecked? 1 : 0;
            if (mAvailabilityAdapter != null && mAvailabilityValues != null
                    && mAvailabilityValues.contains(newAvailabilityValue)) {
                // We'll need to let the spinner's listener know that we're
                // explicitly toggling it.
                mAllDayChangingAvailability = true;

                String newAvailabilityLabel = mOriginalAvailabilityLabels.get(newAvailabilityValue);
                int newAvailabilityPos = mAvailabilityAdapter.getPosition(newAvailabilityLabel);
                mAvailabilitySpinner.setSelection(newAvailabilityPos);
            }
        }

        mAllDay = isChecked;
        updateHomeTime();
    }

    public void setColorPickerButtonStates(int[] colorArray) {
        setColorPickerButtonStates(colorArray != null && colorArray.length > 0);
    }

    public void setColorPickerButtonStates(boolean showColorPalette) {
        mColorPicker.setVisibility(showColorPalette ? View.VISIBLE : View.GONE);
    }

    public boolean isColorPaletteVisible() {
        return mColorPicker.getVisibility() == View.VISIBLE;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // This is only used for the Calendar spinner in new events, and only fires when the
        // calendar selection changes or on screen rotation
        Cursor c = (Cursor) parent.getItemAtPosition(position);
        if (c == null) {
            // TODO: can this happen? should we drop this check?
            Log.w(TAG, "Cursor not set on calendar item");
            return;
        }

        mModel.mCalendarId = c.getLong(EditEventHelper.CALENDARS_INDEX_ID);
        EditEventHelper.setModelFromCalendarCursor(mModel, c, mActivity);
        // ensure model is up to date so that reminders don't get lost on calendar change
        fillModelFromUI();

        // try to find the event color in the new calendar, remove it otherwise
        if (mModel.isEventColorInitialized() && mModel.getCalendarEventColors() != null) {
            Arrays.stream(mModel.getCalendarEventColors())
                    .filter(color -> color == mModel.getEventColor())
                    .findFirst()
                    .ifPresentOrElse(mModel::setEventColor, mModel::removeEventColor);
        } else {
            mModel.removeEventColor();
        }
        setSpinnerBackgroundColor(mModel.isEventColorInitialized()
                ? mModel.getEventColor() : mModel.getCalendarColor());
        setColorPickerButtonStates(mModel.getCalendarEventColors());

        // Update the UI elements.
        mReminderItems.clear();
        LinearLayout reminderLayout =
            (LinearLayout) mScrollView.findViewById(R.id.reminder_items_container);
        reminderLayout.removeAllViews();

        prepareReminders();
        prepareAvailability();
        prepareAccess();
    }

    /**
     * Checks if the start and end times for this event should be displayed in
     * the Calendar app's time zone as well and formats and displays them.
     */
    private void updateHomeTime() {
        String tz = Utils.getTimeZone(mActivity, null);
        if (!mAllDayCheckBox.isChecked() && !TextUtils.equals(tz, mTimezone)
                && mModification != EditEventHelper.MODIFY_UNINITIALIZED) {
            int flags = DateUtils.FORMAT_SHOW_TIME;
            boolean is24Format = DateFormat.is24HourFormat(mActivity);
            if (is24Format) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            long millisStart = mStartTime.toMillis();
            long millisEnd = mEndTime.toMillis();

            // First update the start date and times
            String tzDisplay = TimeZone.getTimeZone(tz).getDisplayName(
                    false, TimeZone.SHORT, Locale.getDefault());
            StringBuilder time = new StringBuilder();

            mSB.setLength(0);
            time.append(DateUtils
                    .formatDateRange(mActivity, mF, millisStart, millisStart, flags, tz))
                    .append(" ").append(tzDisplay);
            mStartTimeHome.setText(time.toString());

            flags = DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_WEEKDAY;
            mSB.setLength(0);
            mStartDateHome
                    .setText(DateUtils.formatDateRange(
                            mActivity, mF, millisStart, millisStart, flags, tz).toString());

            // Make any adjustments needed for the end times
            flags = DateUtils.FORMAT_SHOW_TIME;
            if (is24Format) {
                flags |= DateUtils.FORMAT_24HOUR;
            }

            // Then update the end times
            time.setLength(0);
            mSB.setLength(0);
            time.append(DateUtils.formatDateRange(
                    mActivity, mF, millisEnd, millisEnd, flags, tz)).append(" ").append(tzDisplay);
            mEndTimeHome.setText(time.toString());

            flags = DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_WEEKDAY;
            mSB.setLength(0);
            mEndDateHome.setText(DateUtils.formatDateRange(
                            mActivity, mF, millisEnd, millisEnd, flags, tz).toString());

            mStartHomeGroup.setVisibility(View.VISIBLE);
            mEndHomeGroup.setVisibility(View.VISIBLE);
        } else {
            mStartHomeGroup.setVisibility(View.GONE);
            mEndHomeGroup.setVisibility(View.GONE);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    public void showTimerPickerDialog(View view) {
        int timeFormat = DateFormat.is24HourFormat(mActivity) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H;

        MaterialTimePicker materialTimePicker = new MaterialTimePicker.Builder()
                .setHour((view == mStartTimeButton) ? mStartTime.getHour() : mEndTime.getHour())
                .setMinute((view == mStartTimeButton) ? mStartTime.getMinute() : mEndTime.getMinute())
                .setTimeFormat(timeFormat)
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .build();

        materialTimePicker.addOnPositiveButtonClickListener((dialog) -> {
            onTimeSet(view, materialTimePicker.getHour(), materialTimePicker.getMinute());
        });
        materialTimePicker.show(mActivity.getSupportFragmentManager(), "TimePicker");
    }

    public void onTimeSet(View view, int hourOfDay, int minute) {
        // Cache the member variables locally to avoid inner class overhead.
        Time startTime = mStartTime;
        Time endTime = mEndTime;

        // Cache the start and end millis so that we limit the number
        // of calls to normalize() and toMillis(), which are fairly
        // expensive.
        long startMillis;
        long endMillis;
        if (view == mStartTimeButton) {
            // The start time was changed.
            int hourDuration = endTime.getHour() - startTime.getHour();
            int minuteDuration = endTime.getMinute() - startTime.getMinute();

            startTime.setHour(hourOfDay);
            startTime.setMinute(minute);
            startMillis = startTime.normalize();

            // Also update the end time to keep the duration constant.
            endTime.setHour(hourOfDay + hourDuration);
            endTime.setMinute(minute + minuteDuration);

            // Update tz in case the start time switched from/to DLS
            populateTimezone(startMillis);
        } else {
            // The end time was changed.
            startMillis = startTime.toMillis();
            endTime.setHour(hourOfDay);
            endTime.setMinute(minute);

            // Move to the start time if the end time is before the start
            // time.
            if (endTime.compareTo(startTime) < 0) {
                endTime.setDay(startTime.getDay() + 1);
            }
            // Call populateTimezone if we support end time zone as well
        }

        endMillis = endTime.normalize();

        setDate(mEndDateButton, endMillis);
        setTime(mStartTimeButton, startMillis);
        setTime(mEndTimeButton, endMillis);
        updateHomeTime();
    }

    private void showDatePickerDialog(View view) {
        MaterialPickerOnPositiveButtonClickListener<Long> materialPickerOnPositiveButtonClickListener = timePicked -> {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTime(new Date(timePicked));
            onDateSet(view, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        };

        CalendarConstraints calendarConstraints = new CalendarConstraints.Builder()
                .setFirstDayOfWeek(Utils.getFirstDayOfWeekAsCalendar(mActivity))
                .build();

        Calendar calPickTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        if (view == mStartDateButton) {
            calPickTime.set(mStartTime.getYear(), mStartTime.getMonth(), mStartTime.getDay());
        } else {
            calPickTime.set(mEndTime.getYear(), mEndTime.getMonth(), mEndTime.getDay());
        }

        MaterialDatePicker<Long> datePickerDialog = MaterialDatePicker.Builder.datePicker()
                .setSelection(calPickTime.getTimeInMillis())
                .setCalendarConstraints(calendarConstraints)
                .setTitleText(R.string.goto_date)
                .build();

        datePickerDialog.addOnPositiveButtonClickListener(materialPickerOnPositiveButtonClickListener);
        datePickerDialog.show(mActivity.getSupportFragmentManager(), "DatePicker");
    }

    public void onDateSet(View view, int year, int month, int monthDay) {
        Log.d(TAG, "onDateSet: " + year + " " + month + " " + monthDay);
        // Cache the member variables locally to avoid inner class overhead.
        Time startTime = mStartTime;
        Time endTime = mEndTime;

        // Cache the start and end millis so that we limit the number
        // of calls to normalize() and toMillis(), which are fairly
        // expensive.
        long startMillis;
        long endMillis;
        if (view == mStartDateButton) {
            // The start date was changed.
            int yearDuration = endTime.getYear() - startTime.getYear();
            int monthDuration = endTime.getMonth() - startTime.getMonth();
            int monthDayDuration = endTime.getDay() - startTime.getDay();

            startTime.setYear(year);
            startTime.setMonth(month);
            startTime.setDay(monthDay);
            startMillis = startTime.normalize();

            // Also update the end date to keep the duration constant.
            endTime.setYear(year + yearDuration);
            endTime.setMonth(month + monthDuration);
            endTime.setDay(monthDay + monthDayDuration);
            endMillis = endTime.normalize();

            // If the start date has changed then update the repeats.
            populateRepeats();

            // Update tz in case the start time switched from/to DLS
            populateTimezone(startMillis);
        } else {
            // The end date was changed.
            startMillis = startTime.toMillis();
            endTime.setYear(year);
            endTime.setMonth(month);
            endTime.setDay(monthDay);
            endMillis = endTime.normalize();

            // Do not allow an event to have an end time before the start
            // time.
            if (endTime.compareTo(startTime) < 0) {
                endTime.set(startTime);
                endMillis = startMillis;
            }
            // Call populateTimezone if we support end time zone as well
        }

        setDate(mStartDateButton, startMillis);
        setDate(mEndDateButton, endMillis);
        setTime(mEndTimeButton, endMillis); // In case end time had to be
        // reset
        updateHomeTime();
    }

    public static class CalendarsAdapter extends ResourceCursorAdapter {
        public CalendarsAdapter(Context context, int resourceId, Cursor c) {
            super(context, resourceId, c);
            setDropDownViewResource(R.layout.calendars_dropdown_item);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            View colorBar = view.findViewById(R.id.color);
            int colorColumn = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR);
            int nameColumn = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME);
            int ownerColumn = cursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT);
            if (colorBar != null) {
                colorBar.setBackgroundColor(Utils.getDisplayColorFromColor(context,
                        cursor.getInt(colorColumn)));
            }

            TextView name = (TextView) view.findViewById(R.id.calendar_name);
            if (name != null) {
                String displayName = cursor.getString(nameColumn);
                name.setText(displayName);

                TextView accountName = (TextView) view.findViewById(R.id.account_name);
                if (accountName != null) {
                    accountName.setText(cursor.getString(ownerColumn));
                    accountName.setVisibility(TextView.VISIBLE);
                }
            }
        }
    }
}
