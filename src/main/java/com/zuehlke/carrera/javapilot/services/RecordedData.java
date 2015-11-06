package com.zuehlke.carrera.javapilot.services;

import com.zuehlke.carrera.relayapi.messages.PowerControl;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.relayapi.messages.VelocityMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kirusanth Poopalasingam ( pkirusanth@gmail.com )
 */
public class RecordedData {

    private String name;
    private long startedTime;
    private long stoppedTime;

    private List<SensorEvent> sensorEvents = new ArrayList<>();
    private List<PowerControl> powerControls = new ArrayList<>();
    private List<VelocityMessage> velocityMessages = new ArrayList<>();
    private List<RoundTimeMessage> roundTimeMessages = new ArrayList<>();

    public void addSensorEvents(SensorEvent event){
        sensorEvents.add(event);
    }

    public void addPowerControlEvent(PowerControl powerControl) {
        powerControls.add(powerControl);
    }

    public void addVelocityMessage(VelocityMessage velocityMessage) {
        velocityMessages.add(velocityMessage);
    }

    public void addRoundTimeMessage(RoundTimeMessage roundTimeMessage) {
        roundTimeMessages.add(roundTimeMessage);
    }

    public void setStartedTime(long startedTime) {
        this.startedTime = startedTime;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setStoppedTime(long stoppedTime) {
        this.stoppedTime = stoppedTime;
    }
}
