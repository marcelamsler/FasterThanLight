package com.zuehlke.carrera.javapilot.websocket;

import com.zuehlke.carrera.javapilot.websocket.data.EventMessageType;

/**
 * This is a wrapper class around the message with the type of message ( makes it easier on JS side )
 * @author Kirusanth Poopalasingam ( pkirusanth@gmail.com )
 */
public class JsonMessage {
    /**
     * We don't  need the actually type here. Since it's anyway json-ed and send to the client.
     * No further handling of this property is done in Java
     */
    public final Object genericMessage;
    public final EventMessageType eventMessageType;

    public JsonMessage(Object genericMessage, EventMessageType eventMessageType) {
        this.genericMessage = genericMessage;
        this.eventMessageType = eventMessageType;
    }
}
