package com.box.platform.parser;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.box.sdk.*;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.WriterConfig;

import java.text.SimpleDateFormat;

/**
 * Parses the enterprise event json object and removes sections that are problematic for Elasticsearch field mapping.
 *
 * @author Kyle Adams
 * @email kadams@box.com
 */
public class BoxEnterpriseEventJsonParser extends UntypedActor{
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    // Parse Event object coming from Box and make it suitable for Elasticsearch
    public void onReceive(Object message) throws Throwable {
        logger.debug("Received message: {} from sender: {}", message.toString(), getSender().getClass());
        try{
            // Check if message is of type BoxEvent
            if(message instanceof BoxEvent){
                BoxEvent boxEvent = (BoxEvent) message;
                logger.debug("Parsing event type: {}", boxEvent.getType().name());

                // Build createdBy JSON object
                JsonObject createdBy = Json.object()
                        .add("login", boxEvent.getCreatedBy().getLogin())
                        .add("name", boxEvent.getCreatedBy().getName());

                // Build root-level JSON object
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
                BoxEvent.Type eventType = boxEvent.getType();
                JsonObject enterpriseEvent = Json.object()
                        .add("id", boxEvent.getID())
                        .add("type", eventType.name())
                        .add("created_at", dateFormat.format(boxEvent.getCreatedAt()))
                        .add("ip_address", boxEvent.getIPAddress())
                        .add("created_by", createdBy);

                // Build source json object
                JsonObject source = boxEvent.getSourceJSON();
                if(source == null){
                    source = Json.object();
                }
                enterpriseEvent.add("source", source);

                // Build additional details json object
                JsonObject additionalDetails = boxEvent.getAdditionalDetails();
                if(additionalDetails == null){
                    additionalDetails = Json.object();
                }

                // Check if its a metadata-related event because the operationParams is a string that needs to be converted to an array.
                // Why is it a String? I have no idea.
                if(eventType.name().equalsIgnoreCase("METADATA_INSTANCE_CREATE") || eventType.name().equalsIgnoreCase("METADATA_INSTANCE_UPDATE")){
                    JsonObject metadataJson = additionalDetails.get("metadata").asObject();
                    JsonArray operationParams = Json.parse(metadataJson.get("operationParams").asString()).asArray();
                    additionalDetails = Json.object();
                    additionalDetails.add("type", metadataJson.get("type")).add("operationParams", operationParams);
                }
                enterpriseEvent.add("additional_details", additionalDetails);
                logger.debug("Created new enterprise event json: {}", enterpriseEvent.toString(WriterConfig.PRETTY_PRINT));
                getSender().tell(enterpriseEvent, getSelf());

            } else{
                logger.warning("Message received is not of type BoxEvent. Found: {}", message);
                unhandled(message);
            }
        }
        catch (Exception e){
            logger.error(e, "Failed to parse enterprise event json: {}", e.getMessage());
        }
    }
}
