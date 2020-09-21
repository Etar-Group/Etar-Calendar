package com.android.calendar.widget.general;

import android.view.*;

/**
 * {@link EventInfo} is a class that represents an event in the widget. It
 * contains all of the data necessary to display that event, including the
 * properly localized strings and visibility settings.
 */
public class EventInfo {
    public int visibWhen; // Visibility value for When textview (View.GONE or View.VISIBLE)
    public String when;
    public int visibWhere; // Visibility value for Where textview (View.GONE or View.VISIBLE)
    public String where;
    public int visibTitle; // Visibility value for Title textview (View.GONE or View.VISIBLE)
    public String title;
    public int status;
    public int selfAttendeeStatus;

    public long id;
    public long start;
    public long end;
    public boolean allDay;
    public int color;

    public EventInfo() {
        visibWhen = View.GONE;
        visibWhere = View.GONE;
        visibTitle = View.GONE;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("EventInfo [visibTitle=");
        builder.append(visibTitle);
        builder.append(", title=");
        builder.append(title);
        builder.append(", visibWhen=");
        builder.append(visibWhen);
        builder.append(", id=");
        builder.append(id);
        builder.append(", when=");
        builder.append(when);
        builder.append(", visibWhere=");
        builder.append(visibWhere);
        builder.append(", where=");
        builder.append(where);
        builder.append(", color=");
        builder.append(String.format("0x%x", color));
        builder.append(", status=");
        builder.append(status);
        builder.append(", selfAttendeeStatus=");
        builder.append(selfAttendeeStatus);
        builder.append("]");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (allDay ? 1231 : 1237);
        result = prime * result + (int) (id ^ (id >>> 32));
        result = prime * result + (int) (end ^ (end >>> 32));
        result = prime * result + (int) (start ^ (start >>> 32));
        result = prime * result + ((title == null) ? 0 : title.hashCode());
        result = prime * result + visibTitle;
        result = prime * result + visibWhen;
        result = prime * result + visibWhere;
        result = prime * result + ((when == null) ? 0 : when.hashCode());
        result = prime * result + ((where == null) ? 0 : where.hashCode());
        result = prime * result + color;
        result = prime * result + selfAttendeeStatus;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EventInfo other = (EventInfo) obj;
        if (id != other.id)
            return false;
        if (allDay != other.allDay)
            return false;
        if (end != other.end)
            return false;
        if (start != other.start)
            return false;
        if (title == null) {
            if (other.title != null)
                return false;
        } else if (!title.equals(other.title))
            return false;
        if (visibTitle != other.visibTitle)
            return false;
        if (visibWhen != other.visibWhen)
            return false;
        if (visibWhere != other.visibWhere)
            return false;
        if (when == null) {
            if (other.when != null)
                return false;
        } else if (!when.equals(other.when)) {
            return false;
        }
        if (where == null) {
            if (other.where != null)
                return false;
        } else if (!where.equals(other.where)) {
            return false;
        }
        if (color != other.color) {
            return false;
        }
        if (selfAttendeeStatus != other.selfAttendeeStatus) {
            return false;
        }
        return true;
    }
}