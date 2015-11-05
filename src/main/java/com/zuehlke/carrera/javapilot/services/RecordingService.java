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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@Service
public class RecordingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordingService.class);

    @Autowired
    private PilotService pilotService;

    private ActorRef recordingActor;


    @PostConstruct
    private void afterConstruct() {
        /**
         * Because only the pilot service has the whole Actor-system and create a
         * pilot actor.
         */
        recordingActor = pilotService.getRecordingActor();
    }

    public RecordingService() {
        LOGGER.info("Creating RecordingService");
    }

    public void startRecording(String name) {
        LOGGER.info("I'am here startRecording");

        StartRecordingEvent msg = new StartRecordingEvent();
        msg.setName(name);
        recordingActor.tell(msg, ActorRef.noSender());
    }

    public void stopAndSave() {
        LOGGER.info("I'am here startRecording");

        recordingActor.tell(new StopRecordingEvent() , ActorRef.noSender());

    }

}
