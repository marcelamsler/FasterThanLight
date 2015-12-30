package com.zuehlke.carrera.javapilot.model;

public class Pattern {

    private StringBuilder stringBuilder;
    private Character lastElement = null;
    private Character firstElement = null;
    private boolean isComplete = false;

    public Pattern(){
        stringBuilder = new StringBuilder();
    }

    public void push(Character element){
        if(firstElement == null){
            firstElement = element;
        }
        if(!isComplete && !element.equals(lastElement)){
            stringBuilder.append(element);
            lastElement = element;
        }
    }
    public void push(String element){
        for(char character : element.toCharArray()){
            push(character);
        }
    }

    public Character getFirstElement(){
        return firstElement;
    }

    public boolean match(PatternAttempt attempt){
        String subPattern = stringBuilder.substring(0,attempt.length());
        return subPattern.matches(attempt.toString());
    }

    public int length() {
        return stringBuilder.length();
    }
    public void setComplete(){
        isComplete = true;
    }
    @Override
    public String toString(){
        return stringBuilder.toString();
    }
}
