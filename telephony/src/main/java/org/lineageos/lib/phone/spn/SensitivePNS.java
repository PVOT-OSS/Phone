/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lib.phone.spn;

public class SensitivePNS {
    private java.util.List<SensitivePN> sensitivePN;

    public java.util.List<SensitivePN> getSensitivePN() {
        if (sensitivePN == null) {
            sensitivePN = new java.util.ArrayList<>();
        }
        return sensitivePN;
    }

    static SensitivePNS read(org.xmlpull.v1.XmlPullParser _parser) throws org.xmlpull.v1.XmlPullParserException, java.io.IOException, javax.xml.datatype.DatatypeConfigurationException {
        SensitivePNS _instance = new SensitivePNS();
        String _raw = null;
        int outerDepth = _parser.getDepth();
        int type;
        while ((type=_parser.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT
        && type != org.xmlpull.v1.XmlPullParser.END_TAG) {
            if (_parser.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
            String _tagName = _parser.getName();
            if (_tagName.equals("sensitivePN")) {
                SensitivePN _value = SensitivePN.read(_parser);
                _instance.getSensitivePN().add(_value);
            } else {
                XmlParser.skip(_parser);
            }
        }
        if (type != org.xmlpull.v1.XmlPullParser.END_TAG) {
            throw new javax.xml.datatype.DatatypeConfigurationException("SensitivePNS is not closed");
        }
        return _instance;
    }
}
