/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lib.phone.spn;

public class XmlParser {
    public static SensitivePNS read(java.io.InputStream in) throws org.xmlpull.v1.XmlPullParserException, java.io.IOException, javax.xml.datatype.DatatypeConfigurationException {
        org.xmlpull.v1.XmlPullParser _parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser();
        _parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        _parser.setInput(in, null);
        _parser.nextTag();
        String _tagName = _parser.getName();
        String _raw = null;
        if (_tagName.equals("sensitivePNS")) {
            SensitivePNS _value = SensitivePNS.read(_parser);
            return _value;
        }
        return null;
    }

    public static java.lang.String readText(org.xmlpull.v1.XmlPullParser _parser) throws org.xmlpull.v1.XmlPullParserException, java.io.IOException {
        String result = "";
        if (_parser.next() == org.xmlpull.v1.XmlPullParser.TEXT) {
            result = _parser.getText();
            _parser.nextTag();
        }
        return result;
    }

    public static void skip(org.xmlpull.v1.XmlPullParser _parser) throws org.xmlpull.v1.XmlPullParserException, java.io.IOException {
        if (_parser.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (_parser.next()) {
                case org.xmlpull.v1.XmlPullParser.END_TAG:
                depth--;
                break;
                case org.xmlpull.v1.XmlPullParser.START_TAG:
                depth++;
                break;
            }
        }
    }
}
