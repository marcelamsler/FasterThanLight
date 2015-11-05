package com.zuehlke.carrera.javapilot.rest;

import com.zuehlke.carrera.javapilot.services.RecordingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recording")
public class RecordingController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordingController.class);

    @Autowired
    public RecordingService recordingService;

    @RequestMapping(value="/start", method = RequestMethod.GET)
    public void start(String name) {
        LOGGER.info("start recording");

        recordingService.startRecording(name);
    }

    @RequestMapping(value="/stop", method = RequestMethod.GET)
    public void stop() {
        LOGGER.info("start recording");

        recordingService.stopAndSave();
    }

}
