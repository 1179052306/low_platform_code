package com.api.lowcode.plugin.spi;

public interface CustomValidator extends Plugin {

    String validatorCode();

    void validate(ValidationContext ctx) throws Exception;
}