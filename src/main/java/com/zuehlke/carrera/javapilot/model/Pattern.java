package com.zuehlke.carrera.javapilot.model;

public class Pattern {

    private StringBuilder stringBuilder;
    private Character lastElement = null;
    private Character firstElement = null;
    private boolean isComplete = false;
    private int tolerance = 0;

    public Pattern(){
        stringBuilder = new StringBuilder();
    }

    public Pattern(int tolerance){
        this.tolerance = tolerance;
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
        int allowedFaults = 0;
        String attemptString = attempt.toString();
        if(tolerance != 0){
            allowedFaults = stringBuilder.length()/tolerance;
        }
        for(int x = 0; x < attempt.length();++x){
            if(attemptString.charAt(x) != stringBuilder.charAt(x)){
                if(allowedFaults > 0){
                    allowedFaults--;
                }
                else {
                    return false;
                }
            }
        }
        return true;
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
