package com.android.calendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.widget.Toast;
import com.android.calendar.event.EditEventActivity;
import com.android.calendar.icalendar.Attendee;
import com.android.calendar.icalendar.IcalendarUtils;
import com.android.calendar.icalendar.VCalendar;
import com.android.calendar.icalendar.VEvent;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.TimeZone;

public class ImportActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isValidIntent()) {
            Toast.makeText(this, R.string.cal_nothing_to_import, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            parseCalFile();
        }
    }

    private long getLocalTimeFromString(String iCalDate) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));

        try {
            format.parse(iCalDate);
            format.setTimeZone(TimeZone.getDefault());
            return format.getCalendar().getTimeInMillis();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return System.currentTimeMillis();
    }

    private void showErrorToast() {
        Toast.makeText(this, R.string.cal_import_error_msg, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void parseCalFile() {
        Uri uri = getIntent().getData();
        VCalendar calendar = IcalendarUtils.readCalendarFromFile(this, uri);

        if (calendar == null) {
            showErrorToast();
            return;
        }

        Intent calIntent = new Intent(Intent.ACTION_INSERT);
        calIntent.setType("vnd.android.cursor.item/event");

        LinkedList<VEvent> events = calendar.getAllEvents();
        if (events == null) {
            showErrorToast();
            return;
        }

        VEvent firstEvent = calendar.getAllEvents().getFirst();
        calIntent.putExtra(CalendarContract.Events.TITLE,
                IcalendarUtils.uncleanseString(firstEvent.getProperty(VEvent.SUMMARY)));
        calIntent.putExtra(CalendarContract.Events.EVENT_LOCATION,
                IcalendarUtils.uncleanseString(firstEvent.getProperty(VEvent.LOCATION)));
        calIntent.putExtra(CalendarContract.Events.DESCRIPTION,
                IcalendarUtils.uncleanseString(firstEvent.getProperty(VEvent.DESCRIPTION)));
        calIntent.putExtra(CalendarContract.Events.ORGANIZER,
                IcalendarUtils.uncleanseString(firstEvent.getProperty(VEvent.ORGANIZER)));

        if (firstEvent.mAttendees.size() > 0) {
            StringBuilder builder = new StringBuilder();
            for (Attendee attendee : firstEvent.mAttendees) {
                builder.append(attendee.mEmail);
                builder.append(",");
            }
            calIntent.putExtra(Intent.EXTRA_EMAIL, builder.toString());
        }

        String dtStart = firstEvent.getProperty(VEvent.DTSTART);
        if (!TextUtils.isEmpty(dtStart)) {
            calIntent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                    getLocalTimeFromString(dtStart));
        }

        String dtEnd = firstEvent.getProperty(VEvent.DTEND);
        if (!TextUtils.isEmpty(dtEnd)) {
            calIntent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,
                    getLocalTimeFromString(dtEnd));
        }

        calIntent.putExtra(EditEventActivity.EXTRA_READ_ONLY, true);

        try {
            startActivity(calIntent);
        } catch (ActivityNotFoundException e) {
            // Oh well...
        } finally {
            finish();
        }
    }

    private boolean isValidIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            return false;
        }
        Uri fileUri = intent.getData();
        if (fileUri == null) {
            return false;
        }
        String scheme = fileUri.getScheme();
        return ContentResolver.SCHEME_CONTENT.equals(scheme)
                || ContentResolver.SCHEME_FILE.equals(scheme);
    }

    private static class ListFilesTask extends AsyncTask<Void, Void, String[]> {

        private final Activity mActivity;

        public ListFilesTask(Activity activity) {
            mActivity = activity;
        }

        @Override
        protected String[] doInBackground(Void... params) {
            if (!hasThingsToImport(mActivity)) {
                return null;
            }
            File folder = EventInfoFragment.EXPORT_SDCARD_DIRECTORY;
            String[] result = null;
            if (folder.exists()) {
                result = folder.list();
            }
            return result;
        }

        @Override
        protected void onPostExecute(final String[] files) {
            if (files == null || files.length == 0) {
                Toast.makeText(mActivity, R.string.cal_nothing_to_import,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
            builder.setTitle(R.string.cal_pick_ics)
                    .setItems(files, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Intent i = new Intent(mActivity, ImportActivity.class);
                            File f = new File(EventInfoFragment.EXPORT_SDCARD_DIRECTORY,
                                    files[which]);
                            i.setData(Uri.fromFile(f));
                            mActivity.startActivity(i);
                        }
                    });
            builder.show();
        }

    }

    public static void pickImportFile(Activity activity) {
        new ListFilesTask(activity).execute();
    }

    public static boolean hasThingsToImport(Context context) {
        File folder = EventInfoFragment.EXPORT_SDCARD_DIRECTORY;
        return folder.exists() && folder.list().length > 0;
    }

}
