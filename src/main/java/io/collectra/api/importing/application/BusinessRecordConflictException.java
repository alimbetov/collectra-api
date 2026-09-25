package io.collectra.api.importing.application;
public class BusinessRecordConflictException extends RuntimeException {
 private final String type; private final String externalId;
 public BusinessRecordConflictException(String type,String externalId){super(type+" external identity conflicts with existing canonical state");this.type=type;this.externalId=externalId;}
 public String type(){return type;} public String externalId(){return externalId;}
}
