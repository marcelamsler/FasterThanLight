package com.zuehlke.carrera.javapilot.services;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.zuehlke.carrera.javapilot.akka.JavaPilotActor;
import com.zuehlke.carrera.javapilot.config.PilotProperties;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the carrera pilot instance.
 */
@Service
@EnableScheduling
public class RecordingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordingService.class);

    private ActorRef recordingActor;

    public RecordingService() {
        LOGGER.info("Creating RecordingService");
    }

    public void startRecording() {
        LOGGER.info("I'am here startRecording");
    }

    public void stopAndSave() {
        LOGGER.info("I'am here startRecording");
    }
}
