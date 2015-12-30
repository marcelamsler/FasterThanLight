package com.zuehlke.carrera.javapilot.model;


public class PatternAttempt {

    private StringBuilder stringBuilder;
    private String difference = null;
    private Character lastElement = null;
    private boolean isComplete = false;

    public PatternAttempt(){
        stringBuilder = new StringBuilder();
    }

    public PatternAttempt(Character begin){
        stringBuilder = new StringBuilder(begin);
        lastElement = begin;
    }

    public void makeDiff(){
        difference = stringBuilder.toString();
    }

    public String getDiff(){
        if(difference != null){
            return difference;
        }
        else{
            return stringBuilder.toString();
        }
    }

    @Override
    public String toString(){
        return stringBuilder.toString();
    }

    public int length(){
        return stringBuilder.length();
    }

    public void push(Character element){
        if(!element.equals(lastElement)){
            stringBuilder.append(element);
            lastElement = element;
        }
    }


}
