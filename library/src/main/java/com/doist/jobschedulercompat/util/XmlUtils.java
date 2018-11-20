/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.doist.jobschedulercompat.util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.RestrictTo;

/**
 * Same as com.android.internal.util.XmlUtils, with minor modifications and unused code removed.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class XmlUtils {
    private static void writeMapXml(Map val, String name, XmlSerializer out)
            throws XmlPullParserException, IOException {
        if (val == null) {
            out.startTag(null, "null");
            out.endTag(null, "null");
            return;
        }

        out.startTag(null, "map");
        if (name != null) {
            out.attribute(null, "name", name);
        }

        writeMapXml(val, out);

        out.endTag(null, "map");
    }

    public static final void writeMapXml(Map val, XmlSerializer out) throws XmlPullParserException, IOException {
        if (val == null) {
            return;
        }

        Set s = val.entrySet();
        Iterator i = s.iterator();

        while (i.hasNext()) {
            Map.Entry e = (Map.Entry) i.next();
            writeValueXml(e.getValue(), (String) e.getKey(), out);
        }
    }

    private static void writeListXml(List val, String name, XmlSerializer out)
            throws XmlPullParserException, IOException {
        if (val == null) {
            out.startTag(null, "null");
            out.endTag(null, "null");
            return;
        }

        out.startTag(null, "list");
        if (name != null) {
            out.attribute(null, "name", name);
        }

        int N = val.size();
        int i = 0;
        while (i < N) {
            writeValueXml(val.get(i), null, out);
            i++;
        }

        out.endTag(null, "list");
    }

    private static void writeSetXml(Set val, String name, XmlSerializer out)
            throws XmlPullParserException, IOException {
        if (val == null) {
            out.startTag(null, "null");
            out.endTag(null, "null");
            return;
        }

        out.startTag(null, "set");
        if (name != null) {
            out.attribute(null, "name", name);
        }

        for (Object v : val) {
            writeValueXml(v, null, out);
        }

        out.endTag(null, "set");
    }

    private static void writeByteArrayXml(byte[] val, String name, XmlSerializer out)
            throws XmlPullParserException, IOException {
        if (val == null) {
            out.startTag(null, "null");
            out.endTag(null, "null");
            return;
        }

        out.startTag(null, "byte-array");
        if (name != null) {
            out.attribute(null, "name", name);
        }

        final int N = val.length;
        out.attribute(null, "num", Integer.toString(N));

        StringBuilder sb = new StringBuilder(val.length * 2);
        for (int i = 0; i < N; i++) {
            int b = val[i];
            int h = (b >> 4) & 0x0f;
            sb.append((char) (h >= 10 ? ('a' + h - 10) : ('0' + h)));
            h = b & 0x0f;
            sb.append((char) (h >= 10 ? ('a' + h - 10) : ('0' + h)));
        }

        out.text(sb.toString());

        out.endTag(null, "byte-array");
    }

    private static void writeIntArrayXml(int[] val, String name, XmlSerializer out)
            throws XmlPullParserException, IOException {
        if (val == null) {
            out.startTag(null, "null");
            out.endTag(null, "null");
            return;
        }

        out.startTag(null, "int-array");
        if (name != null) {
            out.attribute(null, "name", name);
        }

        final int N = val.length;
        out.attribute(null, "num", Integer.toString(N));

        for (int i = 0; i < N; i++) {
            out.startTag(null, "item");
            out.attribute(null, "value", Integer.toString(val[i]));
            out.endTag(null, "item");
        }

        out.endTag(null, "int-array");
    }

    private static void writeLongArrayXml(long[] val, String name, XmlSerializer out)
            throws XmlPullParserException, IOException {
        if (val == null) {
            out.startTag(null, "null");
            out.endTag(null, "null");
            return;
        }

        out.startTag(null, "long-array");
        if (name != null) {
            out.attribute(null, "name", name);
        }

        final int N = val.length;
        out.attribute(null, "num", Integer.toString(N));

        for (int i = 0; i < N; i++) {
            out.startTag(null, "item");
            out.attribute(null, "value", Long.toString(val[i]));
            out.endTag(null, "item");
        }

        out.endTag(null, "long-array");
    }

    private static void writeDoubleArrayXml(double[] val, String name, XmlSerializer out)
            throws XmlPullParserException, IOException {
        if (val == null) {
            out.startTag(null, "null");
            out.endTag(null, "null");
            return;
        }

        out.startTag(null, "double-array");
        if (name != null) {
            out.attribute(null, "name", name);
        }

        final int N = val.length;
        out.attribute(null, "num", Integer.toString(N));

        for (int i = 0; i < N; i++) {
            out.startTag(null, "item");
            out.attribute(null, "value", Double.toString(val[i]));
            out.endTag(null, "item");
        }

        out.endTag(null, "double-array");
    }

    private static void writeStringArrayXml(String[] val, String name, XmlSerializer out)
            throws XmlPullParserException, IOException {
        if (val == null) {
            out.startTag(null, "null");
            out.endTag(null, "null");
            return;
        }

        out.startTag(null, "string-array");
        if (name != null) {
            out.attribute(null, "name", name);
        }

        final int N = val.length;
        out.attribute(null, "num", Integer.toString(N));

        for (int i = 0; i < N; i++) {
            out.startTag(null, "item");
            out.attribute(null, "value", val[i]);
            out.endTag(null, "item");
        }

        out.endTag(null, "string-array");
    }

    private static void writeBooleanArrayXml(boolean[] val, String name, XmlSerializer out)
            throws XmlPullParserException, IOException {
        if (val == null) {
            out.startTag(null, "null");
            out.endTag(null, "null");
            return;
        }

        out.startTag(null, "boolean-array");
        if (name != null) {
            out.attribute(null, "name", name);
        }

        final int N = val.length;
        out.attribute(null, "num", Integer.toString(N));

        for (int i = 0; i < N; i++) {
            out.startTag(null, "item");
            out.attribute(null, "value", Boolean.toString(val[i]));
            out.endTag(null, "item");
        }

        out.endTag(null, "boolean-array");
    }

    private static void writeValueXml(Object v, String name, XmlSerializer out)
            throws XmlPullParserException, IOException {
        String typeStr;
        if (v == null) {
            out.startTag(null, "null");
            if (name != null) {
                out.attribute(null, "name", name);
            }
            out.endTag(null, "null");
            return;
        } else if (v instanceof String) {
            out.startTag(null, "string");
            if (name != null) {
                out.attribute(null, "name", name);
            }
            out.text(v.toString());
            out.endTag(null, "string");
            return;
        } else if (v instanceof Integer) {
            typeStr = "int";
        } else if (v instanceof Long) {
            typeStr = "long";
        } else if (v instanceof Float) {
            typeStr = "float";
        } else if (v instanceof Double) {
            typeStr = "double";
        } else if (v instanceof Boolean) {
            typeStr = "boolean";
        } else if (v instanceof byte[]) {
            writeByteArrayXml((byte[]) v, name, out);
            return;
        } else if (v instanceof int[]) {
            writeIntArrayXml((int[]) v, name, out);
            return;
        } else if (v instanceof long[]) {
            writeLongArrayXml((long[]) v, name, out);
            return;
        } else if (v instanceof double[]) {
            writeDoubleArrayXml((double[]) v, name, out);
            return;
        } else if (v instanceof String[]) {
            writeStringArrayXml((String[]) v, name, out);
            return;
        } else if (v instanceof boolean[]) {
            writeBooleanArrayXml((boolean[]) v, name, out);
            return;
        } else if (v instanceof Map) {
            writeMapXml((Map) v, name, out);
            return;
        } else if (v instanceof List) {
            writeListXml((List) v, name, out);
            return;
        } else if (v instanceof Set) {
            writeSetXml((Set) v, name, out);
            return;
        } else if (v instanceof CharSequence) {
            // XXX This is to allow us to at least write something if
            // we encounter styled text...  but it means we will drop all
            // of the styling information. :(
            out.startTag(null, "string");
            if (name != null) {
                out.attribute(null, "name", name);
            }
            out.text(v.toString());
            out.endTag(null, "string");
            return;
        } else {
            throw new RuntimeException("writeValueXml: unable to write value " + v);
        }

        out.startTag(null, typeStr);
        if (name != null) {
            out.attribute(null, "name", name);
        }
        out.attribute(null, "value", v.toString());
        out.endTag(null, typeStr);
    }

    public static final HashMap<String, ?> readMapXml(XmlPullParser parser, String endTag)
            throws XmlPullParserException, IOException {
        return readThisMapXml(parser, endTag, new String[1]);
    }

    private static HashMap<String, ?> readThisMapXml(XmlPullParser parser, String endTag, String[] name)
            throws XmlPullParserException, IOException {
        HashMap<String, Object> map = new HashMap<>();

        int eventType = parser.getEventType();
        do {
            if (eventType == XmlPullParser.START_TAG) {
                Object val = readThisValueXml(parser, name);
                map.put(name[0], val);
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(endTag)) {
                    return map;
                }
                throw new XmlPullParserException("Expected " + endTag + " end tag at: " + parser.getName());
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        throw new XmlPullParserException("Document ended before " + endTag + " end tag");
    }

    @SuppressWarnings("unchecked")
    private static ArrayList readThisListXml(XmlPullParser parser, String endTag, String[] name)
            throws XmlPullParserException, IOException {
        ArrayList list = new ArrayList();

        int eventType = parser.getEventType();
        do {
            if (eventType == XmlPullParser.START_TAG) {
                Object val = readThisValueXml(parser, name);
                list.add(val);
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(endTag)) {
                    return list;
                }
                throw new XmlPullParserException("Expected " + endTag + " end tag at: " + parser.getName());
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        throw new XmlPullParserException("Document ended before " + endTag + " end tag");
    }

    @SuppressWarnings("unchecked")
    private static HashSet readThisSetXml(XmlPullParser parser, String endTag, String[] name)
            throws XmlPullParserException, IOException {
        HashSet set = new HashSet();

        int eventType = parser.getEventType();
        do {
            if (eventType == XmlPullParser.START_TAG) {
                Object val = readThisValueXml(parser, name);
                set.add(val);
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(endTag)) {
                    return set;
                }
                throw new XmlPullParserException("Expected " + endTag + " end tag at: " + parser.getName());
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        throw new XmlPullParserException("Document ended before " + endTag + " end tag");
    }

    private static byte[] readThisByteArrayXml(XmlPullParser parser, String endTag)
            throws XmlPullParserException, IOException {
        int num;
        try {
            num = Integer.parseInt(parser.getAttributeValue(null, "num"));
        } catch (NullPointerException e) {
            throw new XmlPullParserException("Need num attribute in byte-array");
        } catch (NumberFormatException e) {
            throw new XmlPullParserException("Not a number in num attribute in byte-array");
        }

        byte[] array = new byte[num];

        int eventType = parser.getEventType();
        do {
            if (eventType == XmlPullParser.TEXT) {
                if (num > 0) {
                    String values = parser.getText();
                    if (values == null || values.length() != num * 2) {
                        throw new XmlPullParserException("Invalid value found in byte-array: " + values);
                    }
                    // This is ugly, but keeping it to mirror the logic in #writeByteArrayXml.
                    for (int i = 0; i < num; i++) {
                        char nibbleHighChar = values.charAt(2 * i);
                        char nibbleLowChar = values.charAt(2 * i + 1);
                        int nibbleHigh = nibbleHighChar > 'a' ? (nibbleHighChar - 'a' + 10) : (nibbleHighChar - '0');
                        int nibbleLow = nibbleLowChar > 'a' ? (nibbleLowChar - 'a' + 10) : (nibbleLowChar - '0');
                        array[i] = (byte) ((nibbleHigh & 0x0F) << 4 | (nibbleLow & 0x0F));
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(endTag)) {
                    return array;
                } else {
                    throw new XmlPullParserException("Expected " + endTag + " end tag at: " + parser.getName());
                }
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        throw new XmlPullParserException("Document ended before " + endTag + " end tag");
    }

    private static int[] readThisIntArrayXml(XmlPullParser parser, String endTag)
            throws XmlPullParserException, IOException {
        int num;
        try {
            num = Integer.parseInt(parser.getAttributeValue(null, "num"));
        } catch (NullPointerException e) {
            throw new XmlPullParserException("Need num attribute in int-array");
        } catch (NumberFormatException e) {
            throw new XmlPullParserException("Not a number in num attribute in int-array");
        }
        parser.next();

        int[] array = new int[num];
        int i = 0;

        int eventType = parser.getEventType();
        do {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals("item")) {
                    try {
                        array[i] = Integer.parseInt(parser.getAttributeValue(null, "value"));
                    } catch (NullPointerException e) {
                        throw new XmlPullParserException("Need value attribute in item");
                    } catch (NumberFormatException e) {
                        throw new XmlPullParserException("Not a number in value attribute in item");
                    }
                } else {
                    throw new XmlPullParserException("Expected item tag at: " + parser.getName());
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(endTag)) {
                    return array;
                } else if (parser.getName().equals("item")) {
                    i++;
                } else {
                    throw new XmlPullParserException("Expected " + endTag + " end tag at: " + parser.getName());
                }
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        throw new XmlPullParserException("Document ended before " + endTag + " end tag");
    }

    private static long[] readThisLongArrayXml(XmlPullParser parser, String endTag)
            throws XmlPullParserException, IOException {
        int num;
        try {
            num = Integer.parseInt(parser.getAttributeValue(null, "num"));
        } catch (NullPointerException e) {
            throw new XmlPullParserException("Need num attribute in long-array");
        } catch (NumberFormatException e) {
            throw new XmlPullParserException("Not a number in num attribute in long-array");
        }
        parser.next();

        long[] array = new long[num];
        int i = 0;

        int eventType = parser.getEventType();
        do {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals("item")) {
                    try {
                        array[i] = Long.parseLong(parser.getAttributeValue(null, "value"));
                    } catch (NullPointerException e) {
                        throw new XmlPullParserException("Need value attribute in item");
                    } catch (NumberFormatException e) {
                        throw new XmlPullParserException("Not a number in value attribute in item");
                    }
                } else {
                    throw new XmlPullParserException("Expected item tag at: " + parser.getName());
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(endTag)) {
                    return array;
                } else if (parser.getName().equals("item")) {
                    i++;
                } else {
                    throw new XmlPullParserException("Expected " + endTag + " end tag at: " + parser.getName());
                }
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        throw new XmlPullParserException("Document ended before " + endTag + " end tag");
    }

    private static double[] readThisDoubleArrayXml(XmlPullParser parser, String endTag)
            throws XmlPullParserException, IOException {
        int num;
        try {
            num = Integer.parseInt(parser.getAttributeValue(null, "num"));
        } catch (NullPointerException e) {
            throw new XmlPullParserException("Need num attribute in double-array");
        } catch (NumberFormatException e) {
            throw new XmlPullParserException("Not a number in num attribute in double-array");
        }
        parser.next();

        double[] array = new double[num];
        int i = 0;

        int eventType = parser.getEventType();
        do {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals("item")) {
                    try {
                        array[i] = Double.parseDouble(parser.getAttributeValue(null, "value"));
                    } catch (NullPointerException e) {
                        throw new XmlPullParserException("Need value attribute in item");
                    } catch (NumberFormatException e) {
                        throw new XmlPullParserException("Not a number in value attribute in item");
                    }
                } else {
                    throw new XmlPullParserException("Expected item tag at: " + parser.getName());
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(endTag)) {
                    return array;
                } else if (parser.getName().equals("item")) {
                    i++;
                } else {
                    throw new XmlPullParserException("Expected " + endTag + " end tag at: " + parser.getName());
                }
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        throw new XmlPullParserException("Document ended before " + endTag + " end tag");
    }

    private static String[] readThisStringArrayXml(XmlPullParser parser, String endTag)
            throws XmlPullParserException, IOException {
        int num;
        try {
            num = Integer.parseInt(parser.getAttributeValue(null, "num"));
        } catch (NullPointerException e) {
            throw new XmlPullParserException("Need num attribute in string-array");
        } catch (NumberFormatException e) {
            throw new XmlPullParserException("Not a number in num attribute in string-array");
        }
        parser.next();

        String[] array = new String[num];
        int i = 0;

        int eventType = parser.getEventType();
        do {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals("item")) {
                    try {
                        array[i] = parser.getAttributeValue(null, "value");
                    } catch (NullPointerException e) {
                        throw new XmlPullParserException("Need value attribute in item");
                    } catch (NumberFormatException e) {
                        throw new XmlPullParserException("Not a number in value attribute in item");
                    }
                } else {
                    throw new XmlPullParserException("Expected item tag at: " + parser.getName());
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(endTag)) {
                    return array;
                } else if (parser.getName().equals("item")) {
                    i++;
                } else {
                    throw new XmlPullParserException("Expected " + endTag + " end tag at: " + parser.getName());
                }
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        throw new XmlPullParserException("Document ended before " + endTag + " end tag");
    }

    private static boolean[] readThisBooleanArrayXml(XmlPullParser parser, String endTag)
            throws XmlPullParserException, IOException {
        int num;
        try {
            num = Integer.parseInt(parser.getAttributeValue(null, "num"));
        } catch (NullPointerException e) {
            throw new XmlPullParserException("Need num attribute in string-array");
        } catch (NumberFormatException e) {
            throw new XmlPullParserException("Not a number in num attribute in string-array");
        }
        parser.next();

        boolean[] array = new boolean[num];
        int i = 0;

        int eventType = parser.getEventType();
        do {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals("item")) {
                    try {
                        array[i] = Boolean.parseBoolean(parser.getAttributeValue(null, "value"));
                    } catch (NullPointerException e) {
                        throw new XmlPullParserException("Need value attribute in item");
                    } catch (NumberFormatException e) {
                        throw new XmlPullParserException("Not a number in value attribute in item");
                    }
                } else {
                    throw new XmlPullParserException("Expected item tag at: " + parser.getName());
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(endTag)) {
                    return array;
                } else if (parser.getName().equals("item")) {
                    i++;
                } else {
                    throw new XmlPullParserException("Expected " + endTag + " end tag at: " + parser.getName());
                }
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        throw new XmlPullParserException("Document ended before " + endTag + " end tag");
    }

    private static Object readThisValueXml(XmlPullParser parser, String[] name)
            throws XmlPullParserException, IOException {
        final String valueName = parser.getAttributeValue(null, "name");
        final String tagName = parser.getName();

        Object res;

        if (tagName.equals("null")) {
            res = null;
        } else if (tagName.equals("string")) {
            String value = "";
            int eventType;
            while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.END_TAG) {
                    if (parser.getName().equals("string")) {
                        name[0] = valueName;
                        return value;
                    }
                    throw new XmlPullParserException("Unexpected end tag in <string>: " + parser.getName());
                } else if (eventType == XmlPullParser.TEXT) {
                    value += parser.getText();
                } else if (eventType == XmlPullParser.START_TAG) {
                    throw new XmlPullParserException("Unexpected start tag in <string>: " + parser.getName());
                }
            }
            throw new XmlPullParserException("Unexpected end of document in <string>");
        } else if ((res = readThisPrimitiveValueXml(parser, tagName)) != null) {
            // All work already done by readThisPrimitiveValueXml.
        } else if (tagName.equals("byte-array")) {
            res = readThisByteArrayXml(parser, "byte-array");
            name[0] = valueName;
            return res;
        } else if (tagName.equals("int-array")) {
            res = readThisIntArrayXml(parser, "int-array");
            name[0] = valueName;
            return res;
        } else if (tagName.equals("long-array")) {
            res = readThisLongArrayXml(parser, "long-array");
            name[0] = valueName;
            return res;
        } else if (tagName.equals("double-array")) {
            res = readThisDoubleArrayXml(parser, "double-array");
            name[0] = valueName;
            return res;
        } else if (tagName.equals("string-array")) {
            res = readThisStringArrayXml(parser, "string-array");
            name[0] = valueName;
            return res;
        } else if (tagName.equals("boolean-array")) {
            res = readThisBooleanArrayXml(parser, "boolean-array");
            name[0] = valueName;
            return res;
        } else if (tagName.equals("map")) {
            parser.next();
            res = readThisMapXml(parser, "map", name);
            name[0] = valueName;
            return res;
        } else if (tagName.equals("list")) {
            parser.next();
            res = readThisListXml(parser, "list", name);
            name[0] = valueName;
            return res;
        } else if (tagName.equals("set")) {
            parser.next();
            res = readThisSetXml(parser, "set", name);
            name[0] = valueName;
            return res;
        } else {
            throw new XmlPullParserException("Unknown tag: " + tagName);
        }

        // Skip through to end tag.
        int eventType;
        while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(tagName)) {
                    name[0] = valueName;
                    return res;
                }
                throw new XmlPullParserException("Unexpected end tag in <" + tagName + ">: " + parser.getName());
            } else if (eventType == XmlPullParser.TEXT) {
                throw new XmlPullParserException("Unexpected text in <" + tagName + ">: " + parser.getName());
            } else if (eventType == XmlPullParser.START_TAG) {
                throw new XmlPullParserException("Unexpected start tag in <" + tagName + ">: " + parser.getName());
            }
        }
        throw new XmlPullParserException("Unexpected end of document in <" + tagName + ">");
    }

    private static Object readThisPrimitiveValueXml(XmlPullParser parser, String tagName)
            throws XmlPullParserException, IOException {
        try {
            if (tagName.equals("int")) {
                return Integer.parseInt(parser.getAttributeValue(null, "value"));
            } else if (tagName.equals("long")) {
                return Long.valueOf(parser.getAttributeValue(null, "value"));
            } else if (tagName.equals("float")) {
                return Float.valueOf(parser.getAttributeValue(null, "value"));
            } else if (tagName.equals("double")) {
                return Double.valueOf(parser.getAttributeValue(null, "value"));
            } else if (tagName.equals("boolean")) {
                return Boolean.valueOf(parser.getAttributeValue(null, "value"));
            } else {
                return null;
            }
        } catch (NullPointerException e) {
            throw new XmlPullParserException("Need value attribute in <" + tagName + ">");
        } catch (NumberFormatException e) {
            throw new XmlPullParserException("Not a number in value attribute in <" + tagName + ">");
        }
    }

    public static class FastXmlSerializer implements XmlSerializer {
        private static final String ESCAPE_TABLE[] = new String[]{
                null, null, null, null, null, null, null, null,  // 0-7
                null, null, null, null, null, null, null, null,  // 8-15
                null, null, null, null, null, null, null, null,  // 16-23
                null, null, null, null, null, null, null, null,  // 24-31
                null, null, "&quot;", null, null, null, "&amp;", null,  // 32-39
                null, null, null, null, null, null, null, null,  // 40-47
                null, null, null, null, null, null, null, null,  // 48-55
                null, null, null, null, "&lt;", null, "&gt;", null,  // 56-63
        };

        private static final int BUFFER_LEN = 8192;

        private final char[] text = new char[BUFFER_LEN];
        private int pos;

        private Writer writer;

        private OutputStream out;
        private CharsetEncoder charset;
        private ByteBuffer bytes = ByteBuffer.allocate(BUFFER_LEN);

        private boolean inTag;

        private void append(char c) throws IOException {
            int pos = this.pos;
            if (pos >= (BUFFER_LEN - 1)) {
                flush();
                pos = this.pos;
            }
            text[pos] = c;
            this.pos = pos + 1;
        }

        private void append(String str, int i, final int length) throws IOException {
            if (length > BUFFER_LEN) {
                final int end = i + length;
                while (i < end) {
                    int next = i + BUFFER_LEN;
                    append(str, i, next < end ? BUFFER_LEN : (end - i));
                    i = next;
                }
                return;
            }
            int pos = this.pos;
            if ((pos + length) > BUFFER_LEN) {
                flush();
                pos = this.pos;
            }
            str.getChars(i, i + length, text, pos);
            this.pos = pos + length;
        }

        private void append(char[] buf, int i, final int length) throws IOException {
            if (length > BUFFER_LEN) {
                final int end = i + length;
                while (i < end) {
                    int next = i + BUFFER_LEN;
                    append(buf, i, next < end ? BUFFER_LEN : (end - i));
                    i = next;
                }
                return;
            }
            int pos = this.pos;
            if ((pos + length) > BUFFER_LEN) {
                flush();
                pos = this.pos;
            }
            System.arraycopy(buf, i, text, pos, length);
            this.pos = pos + length;
        }

        private void append(String str) throws IOException {
            append(str, 0, str.length());
        }

        private void escapeAndAppendString(final String string) throws IOException {
            final int N = string.length();
            final char NE = (char) ESCAPE_TABLE.length;
            final String[] escapes = ESCAPE_TABLE;
            int lastPos = 0;
            int pos;
            for (pos = 0; pos < N; pos++) {
                char c = string.charAt(pos);
                if (c >= NE) {
                    continue;
                }
                String escape = escapes[c];
                if (escape == null) {
                    continue;
                }
                if (lastPos < pos) {
                    append(string, lastPos, pos - lastPos);
                }
                lastPos = pos + 1;
                append(escape);
            }
            if (lastPos < pos) {
                append(string, lastPos, pos - lastPos);
            }
        }

        private void escapeAndAppendString(char[] buf, int start, int len) throws IOException {
            final char NE = (char) ESCAPE_TABLE.length;
            final String[] escapes = ESCAPE_TABLE;
            int end = start + len;
            int lastPos = start;
            int pos;
            for (pos = start; pos < end; pos++) {
                char c = buf[pos];
                if (c >= NE) {
                    continue;
                }
                String escape = escapes[c];
                if (escape == null) {
                    continue;
                }
                if (lastPos < pos) {
                    append(buf, lastPos, pos - lastPos);
                }
                lastPos = pos + 1;
                append(escape);
            }
            if (lastPos < pos) {
                append(buf, lastPos, pos - lastPos);
            }
        }

        public XmlSerializer attribute(String namespace, String name, String value)
                throws IOException, IllegalArgumentException, IllegalStateException {
            append(' ');
            if (namespace != null) {
                append(namespace);
                append(':');
            }
            append(name);
            append("=\"");

            escapeAndAppendString(value);
            append('"');
            return this;
        }

        public void cdsect(String text) throws IOException, IllegalArgumentException, IllegalStateException {
            throw new UnsupportedOperationException();
        }

        public void comment(String text) throws IOException, IllegalArgumentException, IllegalStateException {
            throw new UnsupportedOperationException();
        }

        public void docdecl(String text) throws IOException, IllegalArgumentException, IllegalStateException {
            throw new UnsupportedOperationException();
        }

        public void endDocument() throws IOException, IllegalArgumentException, IllegalStateException {
            flush();
        }

        public XmlSerializer endTag(String namespace, String name)
                throws IOException, IllegalArgumentException, IllegalStateException {
            if (inTag) {
                append(" />\n");
            } else {
                append("</");
                if (namespace != null) {
                    append(namespace);
                    append(':');
                }
                append(name);
                append(">\n");
            }
            inTag = false;
            return this;
        }

        public void entityRef(String text) throws IOException, IllegalArgumentException, IllegalStateException {
            throw new UnsupportedOperationException();
        }

        private void flushBytes() throws IOException {
            int position;
            if ((position = bytes.position()) > 0) {
                bytes.flip();
                out.write(bytes.array(), 0, position);
                bytes.clear();
            }
        }

        public void flush() throws IOException {
            if (pos > 0) {
                if (out != null) {
                    CharBuffer charBuffer = CharBuffer.wrap(text, 0, pos);
                    CoderResult result = charset.encode(charBuffer, bytes, true);
                    while (true) {
                        if (result.isError()) {
                            throw new IOException(result.toString());
                        } else if (result.isOverflow()) {
                            flushBytes();
                            result = charset.encode(charBuffer, bytes, true);
                            continue;
                        }
                        break;
                    }
                    flushBytes();
                    out.flush();
                } else {
                    writer.write(text, 0, pos);
                    writer.flush();
                }
                pos = 0;
            }
        }

        public int getDepth() {
            throw new UnsupportedOperationException();
        }

        public boolean getFeature(String name) {
            throw new UnsupportedOperationException();
        }

        public String getName() {
            throw new UnsupportedOperationException();
        }

        public String getNamespace() {
            throw new UnsupportedOperationException();
        }

        public String getPrefix(String namespace, boolean generatePrefix) throws IllegalArgumentException {
            throw new UnsupportedOperationException();
        }

        public Object getProperty(String name) {
            throw new UnsupportedOperationException();
        }

        public void ignorableWhitespace(String text)
                throws IOException, IllegalArgumentException, IllegalStateException {
            throw new UnsupportedOperationException();
        }

        public void processingInstruction(String text)
                throws IOException, IllegalArgumentException, IllegalStateException {
            throw new UnsupportedOperationException();
        }

        public void setFeature(String name, boolean state) throws IllegalArgumentException,
                                                                  IllegalStateException {
            if (name.equals("http://xmlpull.org/v1/doc/features.html#indent-output")) {
                return;
            }
            throw new UnsupportedOperationException();
        }

        public void setOutput(OutputStream os, String encoding)
                throws IOException, IllegalArgumentException, IllegalStateException {
            if (os == null) {
                throw new IllegalArgumentException();
            }
            try {
                charset = Charset.forName(encoding).newEncoder();
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                throw (UnsupportedEncodingException) (new UnsupportedEncodingException(encoding).initCause(e));
            }
            out = os;
        }

        public void setOutput(Writer writer) throws IOException, IllegalArgumentException, IllegalStateException {
            this.writer = writer;
        }

        public void setPrefix(String prefix, String namespace)
                throws IOException, IllegalArgumentException, IllegalStateException {
            throw new UnsupportedOperationException();
        }

        public void setProperty(String name, Object value) throws IllegalArgumentException, IllegalStateException {
            throw new UnsupportedOperationException();
        }

        public void startDocument(String encoding, Boolean standalone)
                throws IOException, IllegalArgumentException, IllegalStateException {
            append("<?xml version='1.0' encoding='utf-8' standalone='" + (standalone ? "yes" : "no") + "' ?>\n");
        }

        public XmlSerializer startTag(String namespace, String name)
                throws IOException, IllegalArgumentException, IllegalStateException {
            if (inTag) {
                append(">\n");
            }
            append('<');
            if (namespace != null) {
                append(namespace);
                append(':');
            }
            append(name);
            inTag = true;
            return this;
        }

        public XmlSerializer text(char[] buf, int start, int len)
                throws IOException, IllegalArgumentException, IllegalStateException {
            if (inTag) {
                append(">");
                inTag = false;
            }
            escapeAndAppendString(buf, start, len);
            return this;
        }

        public XmlSerializer text(String text) throws IOException, IllegalArgumentException, IllegalStateException {
            if (inTag) {
                append(">");
                inTag = false;
            }
            escapeAndAppendString(text);
            return this;
        }
    }
}
