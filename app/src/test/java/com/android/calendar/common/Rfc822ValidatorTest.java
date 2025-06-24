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

package com.android.calendar.common;

import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

public class Rfc822ValidatorTest extends TestCase {
    static final String[] VALID_EMAILS = new String[] {
            "a@example.org", "b@exemple.fr", "c@d.e-f",
            "Very.Common@example.org",
            "john@EXAMPLE.ORG",
            "john@a123b.c-d.dept.example.com",
            "xn--r8jz45g@example.com",
            "disposable.style.email.with+symbol@example.com",
            "other.email-with-dash@example.com",
            "!#$%&'*+-/=?^_`{}|~@example.com",  // Use of allowed special characters.
            "a@domain-label-cannot-be-longer-than-63-chars-and-this-is-maximum.example.com",
            // Valid de facto, even if RFC doesn't allow it.
            "a..b@example.com", ".a@example.com", "b.@example.com",
            // Punycode is an ASCII representation of International domain names.
            "john.doe@xn--r8jz45g.xn--zckzah",
            "john.doe@XN--R8JZ45G.XN--ZXKZAH",
            "xn--r8jz45g@xn--r8jz45g.XN--ZXKZAH",
            // Quoted address.
            // TODO(regisd) Fix Rfc822Tokenizer which loses the quotes.
            // "\"much.more unusual\"",
            // "\"very.unusual.@.unusual.com\""

            // Valid only in new Internalized email address.
             "a@\u00E9.example.com",
            //"みんな@例え.テスト",
            "\u307F\u3093\u306A@\u4F8B\u3048.\u30C6\u30B9\u30C8",
            // "test@test.テスト", // Unicode in TLD only.
            "everybody@example.\u30C6\u30B9\u30C8",
            // "test@例え.test", // Unicode in domain only.
            "everybody@\u4F8B\u3048.test",
            // "みんな@example.com" // Unicode in localpart only.
            "\u307F\u3093\u306A@example.test"
    };

    static final String[] INVALID_EMAILS = new String[] {
            "a", "example.com", "john.example.com", // Missing at sign.
            "a b", "a space@example.com", // Space not allowed.
            // Invalid domain.
            "john@example..com", "a@b", "a@-b.com", "a@b-.com", "a@b.c",
            "a@a123456789-123456789-123456789-123456789-123456789-123456789-bcd.example.com",
            // Invalid characters in domain as per RFC 1034 and RFC 1035,
            // even if these characters are in RFC5322's domain production.
            "a@d_e.fg", "a@d!e.fg", "a@d#e.fg", "a@d$e.fg", "a@d%e.fg", "a@d&e.fg", "a@d'e.fg",
            "a@d*e.fg", "a@d+e.fg", "a@d/e.fg", "a@d=e.fg", "a@d?e.fg", "a@d^e.fg", "a@d{}e.fg",
            "a@d|e.fg", "a@d~e.fg",
            // The domain is too long
            "no@domain-label-cannot-be-longer-than-63-chars-but-this-is-64-chars.com",
            "john@doe@example.com", // @ must be unique.
            // Incorrect double quote.
            // TODO(regisd): Fix Rfc822tokenizer which strips the quotes
            // "just\"not\"right@example.com", "\"just.not\\\"@example.com",
            "this\\ still\\\"not\\\\allowed@example.com"
    };

    @SmallTest
    public void testEmailValidator() {
        Rfc822Validator validator = new Rfc822Validator("gmail.com");

        for (String email : VALID_EMAILS) {
            assertTrue(email + " should be a valid email address", validator.isValid(email));
        }

        for (String email : INVALID_EMAILS) {
            assertFalse(email + " should not be a valid email address", validator.isValid(email));
        }

        Map<String, String> fixes = new HashMap<String, String>();
        fixes.put("a", "<a@gmail.com>");
        fixes.put("a b", "<ab@gmail.com>");
        fixes.put("a@b", "<a@b>");
        fixes.put("()~><@not.work", "");

        for (Map.Entry<String, String> e : fixes.entrySet()) {
            assertEquals(e.getValue(), validator.fixText(e.getKey()).toString());
        }
    }

    @SmallTest
    public void testEmailValidatorNullDomain() {
        Rfc822Validator validator = new Rfc822Validator(null);

        Map<String, String> fixes = new HashMap<String, String>();
        fixes.put("a", "<a>");
        fixes.put("a b", "<a b>");
        fixes.put("a@b", "<a@b>");
        fixes.put("a@b.com", "<a@b.com>"); // this one is correct

        for (Map.Entry<String, String> e : fixes.entrySet()) {
            assertEquals(e.getValue(), validator.fixText(e.getKey()).toString());
        }
    }

    @SmallTest
    public void testEmailValidatorRemoveInvalid() {
        Rfc822Validator validator = new Rfc822Validator("google.com");
        validator.setRemoveInvalid(true);

        Map<String, String> fixes = new HashMap<String, String>();
        fixes.put("a", "");
        fixes.put("a b", "");
        fixes.put("a@b", "");
        fixes.put("a@b.com", "<a@b.com>"); // this one is correct

        for (Map.Entry<String, String> e : fixes.entrySet()) {
            assertEquals(e.getValue(), validator.fixText(e.getKey()).toString());
        }
    }
}
