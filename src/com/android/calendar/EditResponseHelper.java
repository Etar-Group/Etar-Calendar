/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.calendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.widget.Button;

/**
 * A helper class for editing the response to an invitation when the invitation
 * is a repeating event.
 */
public class EditResponseHelper implements DialogInterface.OnClickListener, OnDismissListener {
    private final Activity mParent;
    private int mWhichEvents = -1;
    private AlertDialog mAlertDialog;
    private boolean mClickedOk = false;

    /**
     * This callback is passed in to this object when this object is created
     * and is invoked when the "Ok" button is selected.
     */
    private DialogInterface.OnClickListener mDialogListener;

    public EditResponseHelper(Activity parent) {
        mParent = parent;
    }

    public void setOnClickListener(DialogInterface.OnClickListener listener) {
        mDialogListener = listener;
    }

    /**
     * @return whichEvents, representing which events were selected on which to
     * apply the response:
     * -1 means no choice selected, or the dialog was
     * canceled.
     * 0 means just the single event.
     * 1 means all events.
     */
    public int getWhichEvents() {
        return mWhichEvents;
    }

    public void setWhichEvents(int which) {
        mWhichEvents = which;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        setClickedOk(true);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        // If the click was not "OK", clear out whichEvents to represent
        // that the dialog was canceled.
        if (!getClickedOk()) {
            setWhichEvents(-1);
        }
        setClickedOk(false);

        // Call the pre-set dismiss listener too.
        if (mDismissListener != null) {
            mDismissListener.onDismiss(dialog);
        }

    }

    private boolean getClickedOk() {
        return mClickedOk;
    }

    private void setClickedOk(boolean clickedOk) {
        mClickedOk = clickedOk;
    }

    /**
     * This callback is used when a list item is selected
     */
    private DialogInterface.OnClickListener mListListener =
            new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            mWhichEvents = which;

            // Enable the "ok" button now that the user has selected which
            // events in the series to delete.
            Button ok = mAlertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            ok.setEnabled(true);
        }
    };

    private DialogInterface.OnDismissListener mDismissListener;


    /**
     * Set the dismiss listener to be called when the dialog is ended. There,
     * use getWhichEvents() to see how the dialog was dismissed; if it returns
     * -1, the dialog was canceled out. If it is not -1, it's the index of
     * which events the user wants to respond to.
     * @param onDismissListener
     */
    public void setDismissListener(OnDismissListener onDismissListener) {
        mDismissListener = onDismissListener;
    }

    public void showDialog(int whichEvents) {
        // We need to have a non-null listener, otherwise we get null when
        // we try to fetch the "Ok" button.
        if (mDialogListener == null) {
            mDialogListener = this;
        }
        AlertDialog dialog = new AlertDialog.Builder(mParent).setTitle(
                R.string.change_response_title).setIconAttribute(android.R.attr.alertDialogIcon)
                .setSingleChoiceItems(R.array.change_response_labels, whichEvents, mListListener)
                .setPositiveButton(android.R.string.ok, mDialogListener)
                .setNegativeButton(android.R.string.cancel, null).show();
        // The caller may set a dismiss listener to hear back when the dialog is
        // finished. Use getWhichEvents() to see how the dialog was dismissed.
        dialog.setOnDismissListener(this);
        mAlertDialog = dialog;

        if (whichEvents == -1) {
            // Disable the "Ok" button until the user selects which events to
            // delete.
            Button ok = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            ok.setEnabled(false);
        }
    }

    public void dismissAlertDialog() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
    }

}
