package com.commercetools.rmf.diff;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;


public class CheckOption {
    @JacksonXmlProperty(localName = "type", isAttribute = true)
    private String type;
    @JacksonXmlText
    private String value;

    @JsonCreator
    public CheckOption() {
    }

    public CheckOption(String type, String value) {
        this.type = type;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
