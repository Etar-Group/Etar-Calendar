package com.android.calendar.chips;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ws.xsoh.etar.R;

/**
 * Simple dialog fragment for copying the contents of a chip.
 */
public class CopyDialog extends DialogFragment implements DialogInterface.OnClickListener {

    public static final String TAG = "chips-copy-dialog";

    private static final String ARG_TEXT = "text";

    private String mText;

    public static CopyDialog newInstance(String text) {
        final CopyDialog fragment = new CopyDialog();
        final Bundle args = new Bundle(1);
        args.putString(ARG_TEXT, text);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle args = getArguments();
        mText = args.getString(ARG_TEXT);

        return new MaterialAlertDialogBuilder(getActivity())
                .setMessage(mText)
                .setPositiveButton(R.string.chips_action_copy, this)
                .setNegativeButton(R.string.chips_action_cancel, null)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            final ClipboardManager clipboard = (ClipboardManager)
                    getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(null, mText));
        }
    }
}
