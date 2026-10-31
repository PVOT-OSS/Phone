/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lib.phone.spn;

public class SensitivePN {
    private java.util.List<Item> item;
    private java.lang.String network;

    public java.util.List<Item> getItem() {
        if (item == null) {
            item = new java.util.ArrayList<>();
        }
        return item;
    }

    public java.lang.String getNetwork() {
        return network;
    }

    boolean hasNetwork() {
        if (network == null) {
            return false;
        }
        return true;
    }

    public void setNetwork(java.lang.String network) {
        this.network = network;
    }

    static SensitivePN read(org.xmlpull.v1.XmlPullParser _parser) throws org.xmlpull.v1.XmlPullParserException, java.io.IOException, javax.xml.datatype.DatatypeConfigurationException {
        SensitivePN _instance = new SensitivePN();
        String _raw = null;
        _raw = _parser.getAttributeValue(null, "network");
        if (_raw != null) {
            java.lang.String _value = _raw;
            _instance.setNetwork(_value);
        }
        int outerDepth = _parser.getDepth();
        int type;
        while ((type=_parser.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT
        && type != org.xmlpull.v1.XmlPullParser.END_TAG) {
            if (_parser.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
            String _tagName = _parser.getName();
            if (_tagName.equals("item")) {
                Item _value = Item.read(_parser);
                _instance.getItem().add(_value);
            } else {
                XmlParser.skip(_parser);
            }
        }
        if (type != org.xmlpull.v1.XmlPullParser.END_TAG) {
            throw new javax.xml.datatype.DatatypeConfigurationException("SensitivePN is not closed");
        }
        return _instance;
    }
}
