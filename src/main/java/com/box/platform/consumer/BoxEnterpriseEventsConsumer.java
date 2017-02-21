package com.box.platform.consumer;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.box.platform.parser.BoxEnterpriseEventJsonParser;
import com.box.platform.producer.ElasticsearchProducer;
import com.box.platform.search.GetLastElasticsearchDoc;
import com.box.sdk.*;
import com.eclipsesource.json.JsonObject;
import com.typesafe.config.Config;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Pulls enterprise events from Box.
 *
 * @author Kyle Adams
 * @email kadams@box.com
 */
public class BoxEnterpriseEventsConsumer extends UntypedActor{
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    private BoxDeveloperEditionAPIConnection api = null;
    private Cancellable cancellable = null;

    private static final String BOX_CONFIG_PREFIX = "box.platform.";
    private int futuresTimeout;
    private int pollingInterval;
    private boolean isFirstRun = true;
    private String nextStreamPosition;

    private ActorSystem actorSystem;
    private ActorRef lastESDocActor;

    @Override
    public void preStart() throws Exception {
        logger.debug("{} prestart...", this.getClass().getName());
        this.actorSystem = getContext().system();

        // Load application configuration
        Config boxConfig = getContext().system().settings().config();
        String configPath = boxConfig.getString(BOX_CONFIG_PREFIX + "config.path");
        String clientId = boxConfig.getString(BOX_CONFIG_PREFIX + "client.id");
        String clientSecret = boxConfig.getString(BOX_CONFIG_PREFIX + "client.secret");
        String enterpriseId = boxConfig.getString(BOX_CONFIG_PREFIX + "enterprise.id");
        String publicKeyId = boxConfig.getString(BOX_CONFIG_PREFIX + "public.key.id");
        String privateKeyFile = boxConfig.getString(BOX_CONFIG_PREFIX + "private.key.file");
        String privateKey = new String(Files.readAllBytes(Paths.get(configPath + privateKeyFile)));
        String privateKeyPassword = boxConfig.getString(BOX_CONFIG_PREFIX + "private.key.password");
        int maxCacheEntries = boxConfig.getInt(BOX_CONFIG_PREFIX + "max.cache.entries");
        this.futuresTimeout = boxConfig.getInt("elastic.futures.timeout");
        this.pollingInterval = boxConfig.getInt("elastic.enterprise.polling.interval");

        try{
            // Create JWT Encryption preferences
            JWTEncryptionPreferences encryptionPref = new JWTEncryptionPreferences();
            encryptionPref.setPublicKeyID(publicKeyId);
            encryptionPref.setPrivateKey(privateKey);
            encryptionPref.setPrivateKeyPassword(privateKeyPassword);
            encryptionPref.setEncryptionAlgorithm(EncryptionAlgorithm.RSA_SHA_256);
            IAccessTokenCache accessTokenCache = new InMemoryLRUAccessTokenCache(maxCacheEntries);

            // Instantiate Box Connection
            this.api = BoxDeveloperEditionAPIConnection.getAppEnterpriseConnection(
                    enterpriseId, clientId, clientSecret, encryptionPref, accessTokenCache);
            this.api.setAutoRefresh(true);
            logger.debug("Api state: " + this.api.save());

            this.lastESDocActor =  this.actorSystem.actorOf(Props.create(GetLastElasticsearchDoc.class));
        }
        catch (Exception e){
            logger.error(e, "Failed to create Box App Enterprise connection:");
        }
    }

    public void onReceive(Object message) throws Throwable {
        logger.debug("Received message: {} from sender: {}", message.toString(), getSender().getClass());
        this.getEnterpriseEvents();
    }

    private Map<String, Object> findLastESDoc(){

        // Get last search doc created in ES
        Map<String, Object> lastESDocMap = new HashMap<String, Object>();
        try{
            Timeout timeout = new Timeout(Duration.create(this.futuresTimeout, TimeUnit.SECONDS));
            Future<Object> lastESDocFuture = Patterns.ask(this.lastESDocActor, "Getting last created_at value...", timeout);
            Object asyncResult = Await.result(lastESDocFuture, timeout.duration());
            if(asyncResult instanceof Map){
                lastESDocMap = (Map<String, Object>) asyncResult;
            } else{
                logger.error("Response from returned from actor {} is not of type Map.", this.lastESDocActor);
            }
        } catch (Exception e) {
            logger.error(e, "Failed to get last search doc from ES");
        }
        return lastESDocMap;
    }


    private void getEnterpriseEvents(){
        try{
            final ActorRef esProducerActor = this.actorSystem.actorOf(Props.create(ElasticsearchProducer.class));
            final ActorRef enterpriseEventJsonParserActor = this.actorSystem.actorOf(Props.create(BoxEnterpriseEventJsonParser.class));

            // Create a scheduler
            cancellable = this.actorSystem.scheduler().schedule(Duration.Zero(), Duration.create(this.pollingInterval, TimeUnit.MINUTES), new Runnable() {
                public void run() {
                    if(api.needsRefresh()){
                        api.refresh();
                    }

                    // Get next stream position and the last created_at date and store in a hashmap
                    Map<String, Object> lastESDocMap = findLastESDoc();
                    if(isFirstRun){
                        nextStreamPosition = String.valueOf(lastESDocMap.get("next_stream_position"));
                        isFirstRun = false;
                    }
                    Date maxCreatedAtDate = (Date) lastESDocMap.get("max_created_at_date");

                    // Get enterprise events
                    EventLog eventLog = EventLog.getEnterpriseEvents(api, nextStreamPosition, maxCreatedAtDate, new Date(System.currentTimeMillis()));
                    nextStreamPosition = eventLog.getNextStreamPosition();
                    logger.debug("Current Stream Position: {}", eventLog.getStreamPosition());
                    logger.debug("Next Stream Position: {}", nextStreamPosition);
                    logger.debug("Chunk Size: {}", eventLog.getChunkSize());
                    logger.debug("Event Log length: {}", eventLog.getSize());

                    // Loop through new events
                    for(BoxEvent boxEvent: eventLog){
                        try {
                            // Get a parsed enterpriseEvent object
                            Timeout timeout = new Timeout(Duration.create(futuresTimeout, TimeUnit.SECONDS));
                            Future<Object> enterpriseEventFuture = Patterns.ask(enterpriseEventJsonParserActor, boxEvent, timeout);
                            Object asyncResult = Await.result(enterpriseEventFuture, timeout.duration());
                            if(asyncResult instanceof JsonObject) {
                                JsonObject enterpriseEvent = (JsonObject) asyncResult;
                                enterpriseEvent.add("next_stream_position", eventLog.getNextStreamPosition());

                                logger.info("Found and parsed Box enterprise event with type: {} and event id: {}",
                                        enterpriseEvent.get("type").asString(),
                                        enterpriseEvent.get("id").asString());
                                logger.debug("Found and parsed Box enterprise event: {}", enterpriseEvent.toString());
                                esProducerActor.tell(enterpriseEvent, getSelf());
                            } else{
                                logger.error("Result from actor {} is not of type JsonObject", enterpriseEventJsonParserActor);
                            }
                        } catch (Exception e) {
                            logger.error(e, "Failed to parse json form enterprise event");
                        }
                    }
                }
            }, actorSystem.dispatcher());
        }
        catch(Exception e){
            logger.error(e, "Failed to get enterprise events");
        }
    }

    @Override
    public void postStop() throws Exception {
        // Cancel the scheduler after the actor stops
        cancellable.cancel();
    }
}
