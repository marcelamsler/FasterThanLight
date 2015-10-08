package com.zuehlke.carrera.javapilot.recording;

import com.google.gson.Gson;
import com.zuehlke.carrera.relayapi.messages.RaceEventData;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

/**
 * @author Kirusanth Poopalasingam ( pkirusanth@gmail.com )
 */
public class ParseRecordedData {
    public static void main(String[] args) {
        InputStream resourceAsStream = ParseRecordedData.class.getResourceAsStream("/recorded-data/Kobayashi-Kuwait-2015-10-6.json");
        Objects.requireNonNull(resourceAsStream, "No file found");

        Gson gson = new Gson();
        RaceEventData raceEvent = gson.fromJson(new InputStreamReader(resourceAsStream), RaceEventData.class);
        System.out.println(ToStringBuilder.reflectionToString(raceEvent));
    }
}
