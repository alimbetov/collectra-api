package io.collectra.api.integration.application;
public class IngestionConflictException extends RuntimeException{private final String code;public IngestionConflictException(String code,String message){super(message);this.code=code;}public String code(){return code;}}
