package com.zuehlke.carrera.javapilot.services;

import akka.actor.Props;
import akka.actor.UntypedActor;
import com.google.gson.Gson;
import com.zuehlke.carrera.relayapi.messages.PowerControl;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.relayapi.messages.VelocityMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Kirusanth Poopalasingam ( pkirusanth@gmail.com )
 */
public class RecordingActor extends UntypedActor{

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordingActor.class);
    private static final String PATH = "recording";

    private boolean isRecording = false;
    private RecordedData currentRecordedData;

    public RecordingActor() {
        LOGGER.info("Recording actor is created");
    }

    @Override
    public void onReceive(Object o) throws Exception {
        if (o instanceof StartRecordingEvent) {
            startRecording(((StartRecordingEvent) o).getName());
        } else if (o instanceof StopRecordingEvent) {
            stopRecording();
        }

        if (isRecording) {
            storeRelevantStoresInMemory(o);
        }
    }

    private void startRecording(String name) {
        isRecording = true;
        currentRecordedData = new RecordedData();
        currentRecordedData.setStartedTime(System.currentTimeMillis());
        currentRecordedData.setName(name);
    }

    private void stopRecording() {
        isRecording = false;
        saveRecordingToFiles();
    }

    private void storeRelevantStoresInMemory(Object o) {
        if (o instanceof VelocityMessage) {
            currentRecordedData.addVelocityMessage((VelocityMessage) o);
        } else if (o instanceof PowerControl) {
            currentRecordedData.addPowerControlEvent((PowerControl) o);
        } else if (o instanceof RoundTimeMessage) {
            currentRecordedData.addRoundTimeMessage((RoundTimeMessage) o);
        } else if (o instanceof SensorEvent) {
            currentRecordedData.addSensorEvents((SensorEvent) o);
        }
    }

    private void saveRecordingToFiles() {
        currentRecordedData.setStoppedTime(System.currentTimeMillis());

        try {
            Gson gson = new Gson();
            String jsonString = gson.toJson(currentRecordedData);
            Files.write(getPathToStore(), jsonString.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Path getPathToStore() throws IOException {
        long time = System.currentTimeMillis();
        String name = currentRecordedData.getName();
        Path recording = Paths.get(PATH);

        File file = recording.toFile();
        if (!file.exists()) {
            file.mkdir();
        }

        return Paths.get(PATH + "/" + name + "_" + time + ".json");
    }

    public static Props create() {
        return Props.create(RecordingActor.class, RecordingActor::new);
    }
}
