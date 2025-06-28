/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ex.chips;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.util.Rfc822Tokenizer;
import android.widget.TextView;

import androidx.test.filters.SmallTest;

import com.android.ex.chips.recipientchip.DrawableRecipientChip;
import com.android.ex.chips.recipientchip.ReplacementDrawableSpan;
import com.android.ex.chips.recipientchip.VisibleRecipientChip;

import java.util.regex.Pattern;

@SmallTest
public class ChipsTest extends AndroidTestCase {
    private DrawableRecipientChip[] mMockRecips;

    private RecipientEntry[] mMockEntries;

    private Rfc822Tokenizer mTokenizer;

    private Editable mEditable;

    class BaseMockRecipientEditTextView extends RecipientEditTextView {

        public BaseMockRecipientEditTextView(Context context) {
            super(context, null);
            mTokenizer = new Rfc822Tokenizer();
            setTokenizer(mTokenizer);
        }

        @Override
        public DrawableRecipientChip[] getSortedRecipients() {
            return mMockRecips;
        }

        @Override
        public int getLineHeight() {
            return 48;
        }

        @Override
        Drawable getChipBackground(RecipientEntry contact) {
            return createChipBackground();
        }

        @Override
        public int getViewWidth() {
            return 100;
        }
    }

    class MockRecipientEditTextView extends BaseMockRecipientEditTextView {

        public MockRecipientEditTextView(Context context) {
            super(context);
            mTokenizer = new Rfc822Tokenizer();
            setTokenizer(mTokenizer);
        }

        @Override
        public DrawableRecipientChip[] getSortedRecipients() {
            return mMockRecips;
        }

        @Override
        public Editable getText() {
            return mEditable;
        }

        @Override
        public Editable getSpannable() {
            return mEditable;
        }

        @Override
        public int getLineHeight() {
            return 48;
        }

        @Override
        Drawable getChipBackground(RecipientEntry contact) {
            return createChipBackground();
        }

        @Override
        public int length() {
            return mEditable != null ? mEditable.length() : 0;
        }

        @Override
        public String toString() {
            return mEditable != null ? mEditable.toString() : "";
        }

        @Override
        public int getViewWidth() {
            return 100;
        }
    }

    private class TestBaseRecipientAdapter extends BaseRecipientAdapter {
        public TestBaseRecipientAdapter(final Context context) {
            super(context);
        }

        public TestBaseRecipientAdapter(final Context context, final int preferredMaxResultCount,
                final int queryMode) {
            super(context, preferredMaxResultCount, queryMode);
        }
    }

    private MockRecipientEditTextView createViewForTesting() {
        mEditable = new SpannableStringBuilder();
        MockRecipientEditTextView view = new MockRecipientEditTextView(getContext());
        view.setAdapter(new TestBaseRecipientAdapter(getContext()));
        return view;
    }

    public void testCreateDisplayText() {
        RecipientEditTextView view = createViewForTesting();
        RecipientEntry entry = RecipientEntry.constructGeneratedEntry("User Name, Jr",
                "user@username.com", true);
        String testAddress = view.createAddressText(entry);
        String testDisplay = view.createChipDisplayText(entry);
        assertEquals("Expected a properly formatted RFC email address",
                "\"User Name, Jr\" <user@username.com>, ", testAddress);
        assertEquals("Expected a displayable name", "User Name, Jr", testDisplay);

        RecipientEntry alreadyFormatted =
                RecipientEntry.constructFakeEntry("user@username.com, ", true);
        testAddress = view.createAddressText(alreadyFormatted);
        testDisplay = view.createChipDisplayText(alreadyFormatted);
        assertEquals("Expected a properly formatted RFC email address", "<user@username.com>, ",
                testAddress);
        assertEquals("Expected a displayable name", "user@username.com", testDisplay);

        RecipientEntry alreadyFormattedNoSpace = RecipientEntry
                .constructFakeEntry("user@username.com,", true);
        testAddress = view.createAddressText(alreadyFormattedNoSpace);
        assertEquals("Expected a properly formatted RFC email address", "<user@username.com>, ",
                testAddress);

        RecipientEntry alreadyNamed = RecipientEntry.constructGeneratedEntry("User Name",
                "\"User Name, Jr\" <user@username.com>", true);
        testAddress = view.createAddressText(alreadyNamed);
        testDisplay = view.createChipDisplayText(alreadyNamed);
        assertEquals(
                "Expected address that used the name not the excess address name",
                "User Name <user@username.com>, ", testAddress);
        assertEquals("Expected a displayable name", "User Name", testDisplay);
    }

    public void testSanitizeBetween() {
        // First, add 2 chips and then make sure we remove
        // the extra content between them correctly.
        populateMocks(2);
        MockRecipientEditTextView view = createViewForTesting();
        String first = (String) mTokenizer.terminateToken("FIRST");
        String second = (String) mTokenizer.terminateToken("SECOND");
        String extra = "EXTRA";
        mEditable = new SpannableStringBuilder();
        mEditable.append(first + extra + second);
        int firstStart = mEditable.toString().indexOf(first);
        int firstEnd = firstStart + first.trim().length();
        int secondStart = mEditable.toString().indexOf(second);
        int secondEnd = secondStart + second.trim().length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], secondStart, secondEnd, 0);
        view.sanitizeBetween();
        String editableString = mEditable.toString();
        assertEquals(editableString.indexOf(extra), -1);
        assertEquals(editableString.indexOf(first), firstStart);
        assertEquals(editableString.indexOf(second), secondStart - extra.length());
        assertEquals(editableString, (first + second));

        // Add 1 chip and make sure that we remove the extra stuff before it correctly.
        mEditable = new SpannableStringBuilder();
        populateMocks(1);
        mEditable.append(extra);
        mEditable.append(first);
        firstStart = mEditable.toString().indexOf(first);
        firstEnd = firstStart + first.length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], firstStart, firstEnd, 0);
        view.sanitizeBetween();
        assertEquals(mEditable.toString(), first);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 1]), firstStart
                - extra.length());
    }

    public void testSanitizeEnd() {
        // First, add 2 chips and then make sure we remove
        // the extra content between them correctly.
        populateMocks(2);
        MockRecipientEditTextView view = createViewForTesting();
        String first = (String) mTokenizer.terminateToken("FIRST");
        String second = (String) mTokenizer.terminateToken("SECOND");
        String extra = "EXTRA";
        mEditable = new SpannableStringBuilder();
        mEditable.append(first + second);
        int firstStart = mEditable.toString().indexOf(first);
        int firstEnd = firstStart + first.trim().length();
        int secondStart = mEditable.toString().indexOf(second);
        int secondEnd = secondStart + second.trim().length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], secondStart, secondEnd, 0);
        view.sanitizeEnd();
        String editableString = mEditable.toString();
        assertEquals(editableString.indexOf(extra), -1);
        assertEquals(editableString.indexOf(first), firstStart);
        assertEquals(editableString.indexOf(second), secondStart);
        assertEquals(editableString, (first + second));
        mEditable.append(extra);
        editableString = mEditable.toString();
        assertEquals(mEditable.toString(), (first + second + extra));
        view.sanitizeEnd();
        assertEquals(mEditable.toString(), (first + second));
    }

    public void testMoreChipPlainText() {
        MockRecipientEditTextView view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        String first = (String) mTokenizer.terminateToken("FIRST");
        String second = (String) mTokenizer.terminateToken("SECOND");
        String third = (String) mTokenizer.terminateToken("THIRD");
        mEditable = new SpannableStringBuilder();
        mEditable.append(first+second+third);
        int thirdStart = mEditable.toString().indexOf(third);
        int thirdEnd = thirdStart + third.trim().length();
        view.createMoreChipPlainText();
        ReplacementDrawableSpan moreChip = view.getMoreChip();
        assertEquals(mEditable.getSpanStart(moreChip), thirdStart);
        assertEquals(mEditable.getSpanEnd(moreChip), thirdEnd + 1);
    }

    public void testCountTokens() {
        MockRecipientEditTextView view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        String first = (String) mTokenizer.terminateToken("FIRST");
        String second = (String) mTokenizer.terminateToken("SECOND");
        String third = (String) mTokenizer.terminateToken("THIRD");
        String fourth = "FOURTH,";
        String fifth = "FIFTH,";
        mEditable = new SpannableStringBuilder();
        mEditable.append(first+second+third+fourth+fifth);
        assertEquals(view.countTokens(mEditable), 5);
    }

    public void testTooManyRecips() {
        BaseMockRecipientEditTextView view = new BaseMockRecipientEditTextView(getContext());
        view.setMoreItem(createTestMoreItem());
        for (int i = 0; i < 100; i++) {
            view.append(mTokenizer.terminateToken(i + ""));
        }
        assertEquals(view.countTokens(view.getText()), 100);
        view.handlePendingChips();
        view.createMoreChip();
        ReplacementDrawableSpan moreChip = view.getMoreChip();
        // We show 2 chips then place a more chip.
        int secondStart = view.getText().toString().indexOf(
                (String) mTokenizer.terminateToken(RecipientEditTextView.CHIP_LIMIT + ""));
        assertEquals(view.getText().getSpanStart(moreChip), secondStart);
        assertEquals(view.getText().getSpanEnd(moreChip), view.length());
        assertEquals(view.getSortedRecipients(), null);
    }

    public void testMoreChip() {
        // Add 3 chips: this is the trigger point at which the more chip will be created.
        // Test that adding the chips and then creating and removing the more chip, as if
        // the user were focusing/ removing focus from the chips field.
        populateMocks(3);
        MockRecipientEditTextView view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        String first = (String) mTokenizer.terminateToken("FIRST");
        String second = (String) mTokenizer.terminateToken("SECOND");
        String third = (String) mTokenizer.terminateToken("THIRD");
        mEditable = new SpannableStringBuilder();
        mEditable.append(first+second+third);

        int firstStart = mEditable.toString().indexOf(first);
        int firstEnd = firstStart + first.trim().length();
        int secondStart = mEditable.toString().indexOf(second);
        int secondEnd = secondStart + second.trim().length();
        int thirdStart = mEditable.toString().indexOf(third);
        int thirdEnd = thirdStart + third.trim().length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdStart, thirdEnd, 0);

        view.createMoreChip();
        assertEquals(mEditable.toString(), first+second+third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), firstStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), secondStart);
        // Find the more chip.
        ReplacementDrawableSpan moreChip = view.getMoreChip();
        assertEquals(mEditable.getSpanStart(moreChip), thirdStart);
        assertEquals(mEditable.getSpanEnd(moreChip), thirdEnd + 1);

        view.removeMoreChip();
        assertEquals(mEditable.toString(), first+second+third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), firstStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 3]), firstEnd);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), secondStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 1]), thirdStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 1]), thirdEnd);
        moreChip = view.getMoreChip();
        assertEquals(mEditable.getSpanStart(moreChip), -1);

        // Rinse and repeat, just in case!
        view.createMoreChip();
        assertEquals(mEditable.toString(), first+second+third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), firstStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), secondStart);
        // Find the more chip.
        moreChip = view.getMoreChip();
        assertEquals(mEditable.getSpanStart(moreChip), thirdStart);
        assertEquals(mEditable.getSpanEnd(moreChip), thirdEnd + 1);

        view.removeMoreChip();
        assertEquals(mEditable.toString(), first+second+third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), firstStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 3]), firstEnd);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), secondStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 1]), thirdStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 1]), thirdEnd);
        moreChip = view.getMoreChip();
        assertEquals(mEditable.getSpanStart(moreChip), -1);
    }

    public void testMoreChipLotsOfUsers() {
        // Test adding and removing the more chip in the case where we have a lot of users.
        populateMocks(10);
        MockRecipientEditTextView view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        String first = (String) mTokenizer.terminateToken("FIRST");
        String second = (String) mTokenizer.terminateToken("SECOND");
        String third = (String) mTokenizer.terminateToken("THIRD");
        String fourth = (String) mTokenizer.terminateToken("FOURTH");
        String fifth = (String) mTokenizer.terminateToken("FIFTH");
        String sixth = (String) mTokenizer.terminateToken("SIXTH");
        String seventh = (String) mTokenizer.terminateToken("SEVENTH");
        String eigth = (String) mTokenizer.terminateToken("EIGHTH");
        String ninth = (String) mTokenizer.terminateToken("NINTH");
        String tenth = (String) mTokenizer.terminateToken("TENTH");
        mEditable = new SpannableStringBuilder();
        mEditable.append(first+second+third+fourth+fifth+sixth+seventh+eigth+ninth+tenth);

        int firstStart = mEditable.toString().indexOf(first);
        int firstEnd = firstStart + first.trim().length();
        int secondStart = mEditable.toString().indexOf(second);
        int secondEnd = secondStart + second.trim().length();
        int thirdStart = mEditable.toString().indexOf(third);
        int thirdEnd = thirdStart + third.trim().length();
        int fourthStart = mEditable.toString().indexOf(fourth);
        int fourthEnd = fourthStart + fourth.trim().length();
        int fifthStart = mEditable.toString().indexOf(fifth);
        int fifthEnd = fifthStart + fifth.trim().length();
        int sixthStart = mEditable.toString().indexOf(sixth);
        int sixthEnd = sixthStart + sixth.trim().length();
        int seventhStart = mEditable.toString().indexOf(seventh);
        int seventhEnd = seventhStart + seventh.trim().length();
        int eighthStart = mEditable.toString().indexOf(eigth);
        int eighthEnd = eighthStart + eigth.trim().length();
        int ninthStart = mEditable.toString().indexOf(ninth);
        int ninthEnd = ninthStart + ninth.trim().length();
        int tenthStart = mEditable.toString().indexOf(tenth);
        int tenthEnd = tenthStart + tenth.trim().length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 10], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 9], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 8], thirdStart, thirdEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 7], fourthStart, fourthEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 6], fifthStart, fifthEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 5], sixthStart, sixthEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 4], seventhStart, seventhEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], eighthStart, eighthEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], ninthStart, ninthEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], tenthStart, tenthEnd, 0);

        view.createMoreChip();
        assertEquals(mEditable.toString(), first + second + third + fourth + fifth + sixth
                + seventh + eigth + ninth + tenth);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 10]), firstStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 9]), secondStart);
        // Find the more chip.
        ReplacementDrawableSpan moreChip = view.getMoreChip();
        assertEquals(mEditable.getSpanStart(moreChip), thirdStart);
        assertEquals(mEditable.getSpanEnd(moreChip), tenthEnd + 1);

        view.removeMoreChip();
        assertEquals(mEditable.toString(), first + second + third + fourth + fifth + sixth
                + seventh + eigth + ninth + tenth);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 10]), firstStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 9]), secondStart);

        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 8]), thirdStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 7]), fourthStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 6]), fifthStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 5]), sixthStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 4]), seventhStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), eighthStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), ninthStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 1]), tenthStart);
        moreChip = view.getMoreChip();
        assertEquals(mEditable.getSpanStart(moreChip), -1);

    }

    public void testMoreChipSpecialChars() {
        // Make sure the more chip correctly handles extra tokenizer characters in the middle
        // of chip text.
        populateMocks(3);
        MockRecipientEditTextView view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        String first = (String) mTokenizer.terminateToken("FI,RST");
        String second = (String) mTokenizer.terminateToken("SE,COND");
        String third = (String) mTokenizer.terminateToken("THI,RD");
        mEditable = new SpannableStringBuilder();
        mEditable.append(first+second+third);

        int firstStart = mEditable.toString().indexOf(first);
        int firstEnd = firstStart + first.trim().length();
        int secondStart = mEditable.toString().indexOf(second);
        int secondEnd = secondStart + second.trim().length();
        int thirdStart = mEditable.toString().indexOf(third);
        int thirdEnd = thirdStart + third.trim().length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdStart, thirdEnd, 0);

        view.createMoreChip();
        assertEquals(mEditable.toString(), first+second+third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), firstStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), secondStart);
        // Find the more chip.
        ReplacementDrawableSpan moreChip = view.getMoreChip();
        assertEquals(mEditable.getSpanStart(moreChip), thirdStart);
        assertEquals(mEditable.getSpanEnd(moreChip), thirdEnd + 1);

        view.removeMoreChip();
        assertEquals(mEditable.toString(), first+second+third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), firstStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 3]), firstEnd);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), secondStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 1]), thirdStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 1]), thirdEnd);
        moreChip = view.getMoreChip();
        assertEquals(mEditable.getSpanStart(moreChip), -1);
    }

    public void testMoreChipDupes() {
        // Make sure the more chip is correctly added and removed when we have duplicate chips.
        populateMocks(4);
        MockRecipientEditTextView view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        String first = (String) mTokenizer.terminateToken("FIRST");
        String second = (String) mTokenizer.terminateToken("SECOND");
        String third = (String) mTokenizer.terminateToken("THIRD");
        mEditable = new SpannableStringBuilder();
        mEditable.append(first+second+third+third);

        int firstStart = mEditable.toString().indexOf(first);
        int firstEnd = firstStart + first.trim().length();
        int secondStart = mEditable.toString().indexOf(second);
        int secondEnd = secondStart + second.trim().length();
        int thirdStart = mEditable.toString().indexOf(third);
        int thirdEnd = thirdStart + third.trim().length();
        int thirdNextStart = mEditable.toString().indexOf(third, thirdEnd);
        int thirdNextEnd = thirdNextStart + third.trim().length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 4], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], thirdStart, thirdEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdNextStart, thirdNextEnd, 0);

        view.createMoreChip();
        assertEquals(mEditable.toString(), first+second+third+third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 4]), firstStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), secondStart);
        // Find the more chip.
        ReplacementDrawableSpan moreChip = view.getMoreChip();
        assertEquals(mEditable.getSpanStart(moreChip), thirdStart);
        assertEquals(mEditable.getSpanEnd(moreChip), thirdNextEnd + 1);

        view.removeMoreChip();
        assertEquals(mEditable.toString(), first+second+third+third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 4]), firstStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 4]), firstEnd);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), secondStart);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), thirdStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 2]), thirdEnd);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 1]), thirdNextStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 1]), thirdNextEnd);
        moreChip = view.getMoreChip();
        assertEquals(mEditable.getSpanStart(moreChip), -1);
    }

    public void testRemoveChip() {
        // Create 3 chips to start and test removing chips in various postions.
        populateMocks(3);
        MockRecipientEditTextView view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        String first = (String) mTokenizer.terminateToken("FIRST");
        String second = (String) mTokenizer.terminateToken("SECOND");
        String third = (String) mTokenizer.terminateToken("THIRD");
        mEditable = new SpannableStringBuilder();
        mEditable.append(first + second + third);

        int firstStart = mEditable.toString().indexOf(first);
        int firstEnd = firstStart + first.length();
        int secondStart = mEditable.toString().indexOf(second);
        int secondEnd = secondStart + second.length();
        int thirdStart = mEditable.toString().indexOf(third);
        int thirdEnd = thirdStart + third.length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdStart, thirdEnd, 0);
        assertEquals(mEditable.toString(), first + second + third);
        // Test removing the middle chip.
        view.removeChip(mMockRecips[mMockRecips.length - 2]);
        assertEquals(mEditable.toString(), first + third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), firstStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 3]), firstEnd);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), -1);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 2]), -1);
        int newThirdStart = mEditable.toString().indexOf(third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 1]), newThirdStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 1]), newThirdStart
                + third.length());

        // Test removing the first chip.
        populateMocks(3);
        view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        mEditable = new SpannableStringBuilder();
        mEditable.append(first + second + third);

        firstStart = mEditable.toString().indexOf(first);
        firstEnd = firstStart + first.length();
        secondStart = mEditable.toString().indexOf(second);
        secondEnd = secondStart + second.length();
        thirdStart = mEditable.toString().indexOf(third);
        thirdEnd = thirdStart + third.length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdStart, thirdEnd, 0);
        assertEquals(mEditable.toString(), first + second + third);
        view.removeChip(mMockRecips[mMockRecips.length - 3]);
        assertEquals(mEditable.toString(), second + third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), -1);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 3]), -1);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), 0);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 2]), second.length());
        newThirdStart = mEditable.toString().indexOf(third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 1]), newThirdStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 1]), newThirdStart
                + third.length());

        // Test removing the last chip.
        populateMocks(3);
        view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        mEditable = new SpannableStringBuilder();
        mEditable.append(first + second + third);

        firstStart = mEditable.toString().indexOf(first);
        firstEnd = firstStart + first.length();
        secondStart = mEditable.toString().indexOf(second);
        secondEnd = secondStart + second.length();
        thirdStart = mEditable.toString().indexOf(third);
        thirdEnd = thirdStart + third.length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdStart, thirdEnd, 0);
        assertEquals(mEditable.toString(), first + second + third);
        view.removeChip(mMockRecips[mMockRecips.length - 1]);
        assertEquals(mEditable.toString(), first + second);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), firstStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 3]), firstEnd);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), secondStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 2]), secondEnd);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 1]), -1);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 1]), -1);
    }

    public void testReplaceChip() {
        populateMocks(3);
        MockRecipientEditTextView view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        view.setChipBackground(createChipBackground());
        view.setChipHeight(48);
        String first = (String) mTokenizer.terminateToken("FIRST");
        String second = (String) mTokenizer.terminateToken("SECOND");
        String third = (String) mTokenizer.terminateToken("THIRD");
        mEditable = new SpannableStringBuilder();
        mEditable.append(first + second + third);

        // Test replacing the first chip with a new chip.
        int firstStart = mEditable.toString().indexOf(first);
        int firstEnd = firstStart + first.trim().length();
        int secondStart = mEditable.toString().indexOf(second);
        int secondEnd = secondStart + second.trim().length();
        int thirdStart = mEditable.toString().indexOf(third);
        int thirdEnd = thirdStart + third.trim().length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdStart, thirdEnd, 0);
        assertEquals(mEditable.toString(), first + second + third);
        view.replaceChip(mMockRecips[mMockRecips.length - 3], RecipientEntry
                .constructGeneratedEntry("replacement", "replacement@replacement.com", true));
        assertEquals(mEditable.toString(), mTokenizer
                .terminateToken("replacement <replacement@replacement.com>")
                + second + third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), -1);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 3]), -1);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), mEditable
                .toString().indexOf(second));
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 2]), mEditable
                .toString().indexOf(second)
                + second.trim().length());
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 1]), mEditable
                .toString().indexOf(third));
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 1]), mEditable
                .toString().indexOf(third)
                + third.trim().length());
        DrawableRecipientChip[] spans =
                mEditable.getSpans(0, mEditable.length(), DrawableRecipientChip.class);
        assertEquals(spans.length, 3);
        spans = mEditable
                .getSpans(0, mEditable.toString().indexOf(second) - 1, DrawableRecipientChip.class);
        assertEquals((String) spans[0].getDisplay(), "replacement");


        // Test replacing the middle chip with a new chip.
        mEditable = new SpannableStringBuilder();
        mEditable.append(first + second + third);
        firstStart = mEditable.toString().indexOf(first);
        firstEnd = firstStart + first.trim().length();
        secondStart = mEditable.toString().indexOf(second);
        secondEnd = secondStart + second.trim().length();
        thirdStart = mEditable.toString().indexOf(third);
        thirdEnd = thirdStart + third.trim().length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdStart, thirdEnd, 0);
        assertEquals(mEditable.toString(), first + second + third);
        view.replaceChip(mMockRecips[mMockRecips.length - 2], RecipientEntry
                .constructGeneratedEntry("replacement", "replacement@replacement.com", true));
        assertEquals(mEditable.toString(), first + mTokenizer
                .terminateToken("replacement <replacement@replacement.com>") + third);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), firstStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 3]), firstEnd);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), -1);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 2]), -1);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 1]), mEditable
                .toString().indexOf(third));
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 1]), mEditable
                .toString().indexOf(third)
                + third.trim().length());
        spans = mEditable.getSpans(0, mEditable.length(), DrawableRecipientChip.class);
        assertEquals(spans.length, 3);
        spans = mEditable.getSpans(firstEnd, mEditable.toString().indexOf(third) - 1,
                DrawableRecipientChip.class);
        assertEquals((String) spans[0].getDisplay(), "replacement");


        // Test replacing the last chip with a new chip.
        mEditable = new SpannableStringBuilder();
        mEditable.append(first + second + third);
        firstStart = mEditable.toString().indexOf(first);
        firstEnd = firstStart + first.trim().length();
        secondStart = mEditable.toString().indexOf(second);
        secondEnd = secondStart + second.trim().length();
        thirdStart = mEditable.toString().indexOf(third);
        thirdEnd = thirdStart + third.trim().length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdStart, thirdEnd, 0);
        assertEquals(mEditable.toString(), first + second + third);
        view.replaceChip(mMockRecips[mMockRecips.length - 1], RecipientEntry
                .constructGeneratedEntry("replacement", "replacement@replacement.com", true));
        assertEquals(mEditable.toString(), first + second + mTokenizer
                .terminateToken("replacement <replacement@replacement.com>"));
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 3]), firstStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 3]), firstEnd);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 2]), secondStart);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 2]), secondEnd);
        assertEquals(mEditable.getSpanStart(mMockRecips[mMockRecips.length - 1]), -1);
        assertEquals(mEditable.getSpanEnd(mMockRecips[mMockRecips.length - 1]), -1);
        spans = mEditable.getSpans(0, mEditable.length(), DrawableRecipientChip.class);
        assertEquals(spans.length, 3);
        spans = mEditable
                .getSpans(secondEnd, mEditable.length(), DrawableRecipientChip.class);
        assertEquals((String) spans[0].getDisplay(), "replacement");
    }

    public void testHandlePaste() {
        // Start with an empty edit field.
        // Add an address; the text should be left as is.
        MockRecipientEditTextView view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        view.setChipBackground(createChipBackground());
        view.setChipHeight(48);
        mEditable = new SpannableStringBuilder();
        mEditable.append("user@user.com");
        view.setSelection(mEditable.length());
        view.handlePaste();
        assertEquals(mEditable.getSpans(0, mEditable.length(), DrawableRecipientChip.class).length, 0);
        assertEquals(mEditable.toString(), "user@user.com");

        // Test adding a single address to an empty chips field with a space at
        // the end of it. The address should stay as text.
        mEditable = new SpannableStringBuilder();
        String tokenizedUser = "user@user.com" + " ";
        mEditable.append(tokenizedUser);
        view.setSelection(mEditable.length());
        view.handlePaste();
        assertEquals(mEditable.getSpans(0, mEditable.length(), DrawableRecipientChip.class).length, 0);
        assertEquals(mEditable.toString(), tokenizedUser);

        // Test adding a single address to an empty chips field with a semicolon at
        // the end of it. The address should become a chip
        mEditable = new SpannableStringBuilder();
        tokenizedUser = "user@user.com;";
        mEditable.append(tokenizedUser);
        view.setSelection(mEditable.length());
        view.handlePaste();
        assertEquals(mEditable.getSpans(0, mEditable.length(), DrawableRecipientChip.class).length, 1);

        // Test adding 2 address to an empty chips field. The second to last
        // address should become a chip and the last address should stay as
        // text.
        mEditable = new SpannableStringBuilder();
        mEditable.append("user1,user2@user.com");
        view.setSelection(mEditable.length());
        view.handlePaste();
        assertEquals(mEditable.getSpans(0, mEditable.length(), DrawableRecipientChip.class).length, 1);
        assertEquals(mEditable.getSpans(0, mEditable.toString().indexOf("user2@user.com"),
                DrawableRecipientChip.class).length, 1);
        assertEquals(mEditable.toString(), "<user1>, user2@user.com");

        // Test adding a single address to the end of existing chips. The existing
        // chips should remain, and the last address should stay as text.
        populateMocks(3);
        String first = (String) mTokenizer.terminateToken("FIRST");
        String second = (String) mTokenizer.terminateToken("SECOND");
        String third = (String) mTokenizer.terminateToken("THIRD");
        mEditable = new SpannableStringBuilder();
        mEditable.append(first + second + third);
        view.setSelection(mEditable.length());
        int firstStart = mEditable.toString().indexOf(first);
        int firstEnd = firstStart + first.trim().length();
        int secondStart = mEditable.toString().indexOf(second);
        int secondEnd = secondStart + second.trim().length();
        int thirdStart = mEditable.toString().indexOf(third);
        int thirdEnd = thirdStart + third.trim().length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdStart, thirdEnd, 0);

        mEditable.append("user@user.com");
        view.setSelection(mEditable.length());
        view.handlePaste();
        assertEquals(mEditable.getSpans(0, mEditable.length(), DrawableRecipientChip.class).length,
                mMockRecips.length);
        assertEquals(mEditable.toString(), first + second + third + "user@user.com");

        // Paste 2 addresses after existing chips. We expect the first address to be turned into
        // a chip and the second to be left as text.
        populateMocks(3);
        mEditable = new SpannableStringBuilder();
        mEditable.append(first + second + third);

        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdStart, thirdEnd, 0);

        mEditable.append("user1, user2@user.com");
        view.setSelection(mEditable.length());
        view.handlePaste();
        assertEquals(mEditable.getSpans(0, mEditable.length(), DrawableRecipientChip.class).length,
                mMockRecips.length + 1);
        assertEquals(mEditable.getSpans(mEditable.toString().indexOf("<user1>"), mEditable
                .toString().indexOf("user2@user.com") - 1, DrawableRecipientChip.class).length, 1);
        assertEquals(mEditable.getSpans(mEditable.toString().indexOf("user2@user.com"), mEditable
                .length(), DrawableRecipientChip.class).length, 0);
        assertEquals(mEditable.toString(), first + second + third + "<user1>, user2@user.com");

        // Paste 2 addresses after existing chips. We expect the first address to be turned into
        // a chip and the second to be left as text. This removes the space seperator char between
        // addresses.
        populateMocks(3);
        mEditable = new SpannableStringBuilder();
        mEditable.append(first + second + third);

        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdStart, thirdEnd, 0);

        mEditable.append("user1,user2@user.com");
        view.setSelection(mEditable.length());
        view.handlePaste();
        assertEquals(mEditable.getSpans(0, mEditable.length(), DrawableRecipientChip.class).length,
                mMockRecips.length + 1);
        assertEquals(mEditable.getSpans(mEditable.toString().indexOf("<user1>"), mEditable
                .toString().indexOf("user2@user.com") - 1, DrawableRecipientChip.class).length, 1);
        assertEquals(mEditable.getSpans(mEditable.toString().indexOf("user2@user.com"), mEditable
                .length(), DrawableRecipientChip.class).length, 0);
        assertEquals(mEditable.toString(), first + second + third + "<user1>, user2@user.com");

        // Test a complete token pasted in at the end. It should be turned into a chip.
        mEditable = new SpannableStringBuilder();
        mEditable.append("user1, user2@user.com,");
        view.setSelection(mEditable.length());
        view.handlePaste();
        assertEquals(mEditable.getSpans(0, mEditable.length(), DrawableRecipientChip.class).length, 2);
        assertEquals(mEditable.getSpans(mEditable.toString().indexOf("<user1>"), mEditable
                .toString().indexOf("user2@user.com") - 1, DrawableRecipientChip.class).length, 1);
        assertEquals(mEditable.getSpans(mEditable.toString().indexOf("user2@user.com"), mEditable
                .length(), DrawableRecipientChip.class).length, 1);
        assertEquals(mEditable.toString(), "<user1>, <user2@user.com>, ");
    }

    @TargetApi(16)
    public void testHandlePasteClip() {
        MockRecipientEditTextView view = createViewForTesting();

        ClipData clipData = null;
        mEditable = new SpannableStringBuilder();
        view.handlePasteClip(clipData);
        assertEquals("", view.getText().toString());

        clipData = ClipData.newPlainText("user label", "<foo@example.com>");
        mEditable = new SpannableStringBuilder();
        view.handlePasteClip(clipData);
        assertEquals("<foo@example.com>", view.getText().toString());

        clipData = ClipData.newHtmlText("user label",
                "<bar@example.com>", "<a href=\"mailto:bar@example.com\">email</a>");
        mEditable = new SpannableStringBuilder();
        view.handlePasteClip(clipData);
        assertEquals("<bar@example.com>", view.getText().toString());

        ClipData.Item clipImageData = new ClipData.Item(Uri.parse("content://my/image"));
        clipData = new ClipData("user label", new String[]{"image/jpeg"}, clipImageData);
        mEditable = new SpannableStringBuilder();
        view.handlePasteClip(clipData);
        assertEquals("", view.getText().toString()
        );
    }

    public void testGetPastTerminators() {
        MockRecipientEditTextView view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        view.setChipBackground(createChipBackground());
        view.setChipHeight(48);
        String test = "test";
        mEditable = new SpannableStringBuilder();
        mEditable.append(test);
        assertEquals(view.movePastTerminators(mTokenizer.findTokenEnd(mEditable.toString(), 0)),
                test.length());

        test = "test,";
        mEditable = new SpannableStringBuilder();
        mEditable.append(test);
        assertEquals(view.movePastTerminators(mTokenizer.findTokenEnd(mEditable.toString(), 0)),
                test.length());

        test = "test, ";
        mEditable = new SpannableStringBuilder();
        mEditable.append(test);
        assertEquals(view.movePastTerminators(mTokenizer.findTokenEnd(mEditable.toString(), 0)),
                test.length());

        test = "test;";
        mEditable = new SpannableStringBuilder();
        mEditable.append(test);
        assertEquals(view.movePastTerminators(mTokenizer.findTokenEnd(mEditable.toString(), 0)),
                test.length());

        test = "test; ";
        mEditable = new SpannableStringBuilder();
        mEditable.append(test);
        assertEquals(view.movePastTerminators(mTokenizer.findTokenEnd(mEditable.toString(), 0)),
                test.length());
    }

    public void testIsCompletedToken() {
        MockRecipientEditTextView view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        view.setChipBackground(createChipBackground());
        view.setChipHeight(48);
        assertTrue(view.isCompletedToken("test;"));
        assertTrue(view.isCompletedToken("test,"));
        assertFalse(view.isCompletedToken("test"));
        assertFalse(view.isCompletedToken("test "));
    }

    public void testGetLastChip() {
        populateMocks(3);
        MockRecipientEditTextView view = createViewForTesting();
        view.setMoreItem(createTestMoreItem());
        view.setChipBackground(createChipBackground());
        view.setChipHeight(48);
        String first = (String) mTokenizer.terminateToken("FIRST");
        String second = (String) mTokenizer.terminateToken("SECOND");
        String third = (String) mTokenizer.terminateToken("THIRD");
        mEditable = new SpannableStringBuilder();
        mEditable.append(first + second + third);

        // Test replacing the first chip with a new chip.
        int firstStart = mEditable.toString().indexOf(first);
        int firstEnd = firstStart + first.trim().length();
        int secondStart = mEditable.toString().indexOf(second);
        int secondEnd = secondStart + second.trim().length();
        int thirdStart = mEditable.toString().indexOf(third);
        int thirdEnd = thirdStart + third.trim().length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 3], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], secondStart, secondEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], thirdStart, thirdEnd, 0);
        assertEquals(view.getLastChip(), mMockRecips[mMockRecips.length - 1]);
        mEditable.append("extra");
        assertEquals(view.getLastChip(), mMockRecips[mMockRecips.length - 1]);
    }

    private Drawable createChipBackground() {
        Bitmap drawable = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        return new BitmapDrawable(getContext().getResources(), drawable);
    }

    private TextView createTestMoreItem() {
        TextView view = new TextView(getContext());
        view.setText("<xliff:g id='count'>%1$s</xliff:g> more...");
        return view;
    }

    private void populateMocks(int size) {
        mMockEntries = new RecipientEntry[size];
        for (int i = 0; i < size; i++) {
            mMockEntries[i] = RecipientEntry.constructGeneratedEntry("user",
                    "user@username.com", true);
        }
        mMockRecips = new DrawableRecipientChip[size];
        for (int i = 0; i < size; i++) {
            mMockRecips[i] = new VisibleRecipientChip(null, mMockEntries[i]);
        }
    }

    /**
     * <p>
     * Ensure the original text is always accurate, regardless of the type of email. The original
     * text is used to determine where to display the chip span. If this test fails, it means some
     * text that should be turned into one whole chip may behave unexpectedly.
     * </p>
     * <p>
     * For example, a bug was seen where
     *
     * <pre>
     * "Android User" <android@example.com>
     * </pre>
     *
     * was converted to
     *
     * <pre>
     * Android User [android@example.com]
     * </pre>
     *
     * where text inside [] is a chip.
     * </p>
     */
    public void testCreateReplacementChipOriginalText() {
        // Name in quotes + email address
        testCreateReplacementChipOriginalText("\"Android User\" <android@example.com>,");
        // Name in quotes + email address without brackets
        testCreateReplacementChipOriginalText("\"Android User\" android@example.com,");
        // Name in quotes
        testCreateReplacementChipOriginalText("\"Android User\",");
        // Name without quotes + email address
        testCreateReplacementChipOriginalText("Android User <android@example.com>,");
        // Name without quotes
        testCreateReplacementChipOriginalText("Android User,");
        // Email address
        testCreateReplacementChipOriginalText("<android@example.com>,");
        // Email address without brackets
        testCreateReplacementChipOriginalText("android@example.com,");
    }

    private void testCreateReplacementChipOriginalText(final String email) {
        // No trailing space
        attemptCreateReplacementChipOriginalText(email.trim());
        // Trailing space
        attemptCreateReplacementChipOriginalText(email.trim() + " ");
    }

    private void attemptCreateReplacementChipOriginalText(final String email) {
        final RecipientEditTextView view = new RecipientEditTextView(getContext(), null);

        view.setText(email);
        view.mPendingChips.add(email);

        view.createReplacementChip(0, email.length(), view.getText(), true);
        // The "original text" should be the email without the comma or space(s)
        assertEquals(email.replaceAll(",\\s*$", ""),
                view.mTemporaryRecipients.get(0).getOriginalText().toString().trim());
    }

    public void testCreateTokenizedEntryForPhone() {
        final String phonePattern = "[^\\d]*888[^\\d]*555[^\\d]*1234[^\\d]*";
        final String phone1 = "8885551234";
        final String phone2 = "888-555-1234";
        final String phone3 = "(888) 555-1234";

        final RecipientEditTextView view = new RecipientEditTextView(getContext(), null);
        final BaseRecipientAdapter adapter = new TestBaseRecipientAdapter(getContext(), 10,
                BaseRecipientAdapter.QUERY_TYPE_PHONE);
        view.setAdapter(adapter);

        final RecipientEntry entry1 = view.createTokenizedEntry(phone1);
        final String destination1 = entry1.getDestination();
        assertTrue(phone1 + " failed with " + destination1,
                Pattern.matches(phonePattern, destination1));

        final RecipientEntry entry2 = view.createTokenizedEntry(phone2);
        final String destination2 = entry2.getDestination();
        assertTrue(phone2 + " failed with " + destination2,
                Pattern.matches(phonePattern, destination2));

        final RecipientEntry entry3 = view.createTokenizedEntry(phone3);
        final String destination3 = entry3.getDestination();
        assertTrue(phone3 + " failed with " + destination3,
                Pattern.matches(phonePattern, destination3));
    }
}
