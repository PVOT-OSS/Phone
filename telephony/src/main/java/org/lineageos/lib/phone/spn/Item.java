/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lib.phone.spn;

public class Item {
    private java.lang.String number;
    private java.lang.String name;
    private java.lang.String categories;
    private java.lang.String languages;
    private java.lang.String organization;
    private java.lang.String website;

    public java.lang.String getNumber() {
        return number;
    }

    boolean hasNumber() {
        if (number == null) {
            return false;
        }
        return true;
    }

    public void setNumber(java.lang.String number) {
        this.number = number;
    }

    public java.lang.String getName() {
        return name;
    }

    boolean hasName() {
        if (name == null) {
            return false;
        }
        return true;
    }

    public void setName(java.lang.String name) {
        this.name = name;
    }

    public java.lang.String getCategories() {
        return categories;
    }

    boolean hasCategories() {
        if (categories == null) {
            return false;
        }
        return true;
    }

    public void setCategories(java.lang.String categories) {
        this.categories = categories;
    }

    public java.lang.String getLanguages() {
        return languages;
    }

    boolean hasLanguages() {
        if (languages == null) {
            return false;
        }
        return true;
    }

    public void setLanguages(java.lang.String languages) {
        this.languages = languages;
    }

    public java.lang.String getOrganization() {
        return organization;
    }

    boolean hasOrganization() {
        if (organization == null) {
            return false;
        }
        return true;
    }

    public void setOrganization(java.lang.String organization) {
        this.organization = organization;
    }

    public java.lang.String getWebsite() {
        return website;
    }

    boolean hasWebsite() {
        if (website == null) {
            return false;
        }
        return true;
    }

    public void setWebsite(java.lang.String website) {
        this.website = website;
    }

    static Item read(org.xmlpull.v1.XmlPullParser _parser) throws org.xmlpull.v1.XmlPullParserException, java.io.IOException, javax.xml.datatype.DatatypeConfigurationException {
        Item _instance = new Item();
        String _raw = null;
        int outerDepth = _parser.getDepth();
        int type;
        while ((type=_parser.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT
        && type != org.xmlpull.v1.XmlPullParser.END_TAG) {
            if (_parser.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
            String _tagName = _parser.getName();
            if (_tagName.equals("number")) {
                _raw = XmlParser.readText(_parser);
                java.lang.String _value = _raw;
                _instance.setNumber(_value);
            } else if (_tagName.equals("name")) {
                _raw = XmlParser.readText(_parser);
                java.lang.String _value = _raw;
                _instance.setName(_value);
            } else if (_tagName.equals("categories")) {
                _raw = XmlParser.readText(_parser);
                java.lang.String _value = _raw;
                _instance.setCategories(_value);
            } else if (_tagName.equals("languages")) {
                _raw = XmlParser.readText(_parser);
                java.lang.String _value = _raw;
                _instance.setLanguages(_value);
            } else if (_tagName.equals("organization")) {
                _raw = XmlParser.readText(_parser);
                java.lang.String _value = _raw;
                _instance.setOrganization(_value);
            } else if (_tagName.equals("website")) {
                _raw = XmlParser.readText(_parser);
                java.lang.String _value = _raw;
                _instance.setWebsite(_value);
            } else {
                XmlParser.skip(_parser);
            }
        }
        if (type != org.xmlpull.v1.XmlPullParser.END_TAG) {
            throw new javax.xml.datatype.DatatypeConfigurationException("Item is not closed");
        }
        return _instance;
    }
}
