package com.android.calendar.persistence.tasks;

import android.net.Uri;
import android.provider.BaseColumns;

public class DmfsOpenTasksContract implements BaseColumns {
    public static final String AUTHORITY = "org.dmfs.tasks";

    public static final String TASK_READ_PERMISSION = "org.dmfs.permission.READ_TASKS";
    public static final String TASK_WRITE_PERMISSION = "org.dmfs.permission.WRITE_TASKS";

    public static final String ACCOUNT_TYPE_LOCAL = "org.dmfs.account.LOCAL";

    public static class Tasks {

        public static final Uri PROVIDER_URI = Uri.parse("content://" + AUTHORITY + "/tasks");

        public static final String COLUMN_ID = "_id";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_LOCATION = "location";
        public static final String COLUMN_DUE_DATE = "due";
        public static final String COLUMN_VISIBLE = "visible";
        public static final String COLUMN_IS_ALLDAY = "is_allday";
        public static final String COLUMN_HAS_ALLARMS = "has_alarms";
        public static final String COLUMN_RRULE = "rrule";
        public static final String COLUMN_RDATE = "rdate";
        public static final String COLUMN_START_DATE = "dtstart";
        public static final String COLUMN_COLOR = "task_color";
        public static final String COLUMN_TZ = "tz";
        public static final String COLUMN_STATUS = "status";
        public static final String COLUMN_ORGANIZER = "organizer";
        public static final String COLUMN_ACCOUNT_NAME = "account_name";
        public static final String COLUMN_LIST_ID = "list_id";

        public static final String COLUMN_DTSTART = "dtstart";
        public static final String COLUMN_SYNC_ID = "_sync_id";

        public static final String COLUMN_DESCRIPTION = "description";
        public static final String COLUMN_LIST_ACCESS_LEVEL = "list_access_level";
        public static final String COLUMN_LIST_COLOR = "list_color";
        public static final String COLUMN_DURATION = "duration";
        public static final String COLUMN_ORIGINAL_INSTANCE_SYNC_ID = "original_instance_sync_id";


        public static final int STATUS_COMPLETED = 2;
    }

    public static class TaskLists {

        public static final Uri PROVIDER_URI = Uri.parse("content://" + AUTHORITY + "/tasklists");

        public static final String COLUMN_ID = "_id";
        public static final String COLUMN_NAME = "list_name";
        public static final String COLUMN_COLOR = "list_color";
        public static final String COLUMN_ACCOUNT_NAME = "account_name";
        public static final String COLUMN_ACCOUNT_TYPE = "account_type";
        public static final String COLUMN_LIST_OWNER = "list_owner";
        public static final String COLUMN_VISIBLE = "visible";
        public static final String COLUMN_SYNC_ENABLE = "sync_enabled";
    }

    public static final String PERMISSION = "org.dmfs.permission.READ_TASKS";
}
