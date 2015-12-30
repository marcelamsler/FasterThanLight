package com.zuehlke.carrera.javapilot.model;

public class Pattern {

    private StringBuilder stringBuilder;
    private Character lastElement = null;
    private Character firstElement = null;
    private boolean isComplete = false;

    public Pattern(){
        stringBuilder = new StringBuilder();
    }

    /**
     * Adds a character to the Pattern when the pattern is not marked as complete
     * @param element character to append to the Pattern
     */
    public void push(Character element){
        if(firstElement == null){
            firstElement = element;
        }
        if(!isComplete && !element.equals(lastElement)){
            stringBuilder.append(element);
            lastElement = element;
        }
    }
    /**
     * Adds a String to the Pattern when the pattern is not marked as complete
     * @param element string to append to the Pattern
     */
    public void push(String element){
        for(char character : element.toCharArray()){
            push(character);
        }
    }

    public Character getFirstElement(){
        return firstElement;
    }

    /**
     * Matches the first n characters of the pattern with a PatternAttempt. Whereas n is the length of the PatternAttempt.
     * @param attempt Attempt to match against
     * @return Whether the attempt is a match with the pattern
     */
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

    public boolean isComplete(){
        return isComplete;
    }

    @Override
    public String toString(){
        return stringBuilder.toString();
    }
}
