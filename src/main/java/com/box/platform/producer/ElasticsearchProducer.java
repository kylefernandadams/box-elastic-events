package com.box.platform.producer;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.WriterConfig;
import com.typesafe.config.Config;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.*;
import org.elasticsearch.client.sniff.SniffOnFailureListener;
import org.elasticsearch.client.sniff.Sniffer;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;


/**
 * Creates a new Elasticsearch doc based on the newly created json that represents a Box Enterprise Event.
 *
 * @author Kyle Adams
 * @email kadams@box.com
 */
public class ElasticsearchProducer extends UntypedActor {
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    private static final String ELASTIC_CONFIG_PREFIX = "elastic.";
    private static final String HTTP_POST = "POST";

    private RestClient restClient = null;
    private String indexName;
    private String indexType;
    private int connectionTimeout;
    private int socketTimeout;
    private int connectionRequestTimeout;

    @Override
    public void preStart() throws Exception {
        logger.debug("{} prestart...", this.getClass().getName());
        // Get application config
        Config boxConfig = getContext().system().settings().config();
        String host = boxConfig.getString(ELASTIC_CONFIG_PREFIX + "host");
        int port = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.rest.port");
        this.indexName = boxConfig.getString(ELASTIC_CONFIG_PREFIX + "index.name");
        this.indexType = boxConfig.getString(ELASTIC_CONFIG_PREFIX + "enterprise.type");
        this.connectionTimeout = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.rest.connection.timeout");
        this.socketTimeout = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.rest.socket.timeout");
        this.connectionRequestTimeout = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.rest.connection.request.timeout");
        int maxRetryTimeout = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.rest.max.retry.timeout");

        // Instantiate ES RestClient with Sniffer
        SniffOnFailureListener sniffOnFailureListener = new SniffOnFailureListener();
        this.restClient = RestClient.builder(new HttpHost(host, port))
                .setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
                    public RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder) {
                        return requestConfigBuilder
                                .setConnectTimeout(connectionTimeout)
                                .setSocketTimeout(socketTimeout)
                                .setConnectionRequestTimeout(connectionRequestTimeout);
                    }
                })
                .setMaxRetryTimeoutMillis(maxRetryTimeout)
                .setFailureListener(sniffOnFailureListener)
                .build();
        Sniffer sniffer = Sniffer.builder(this.restClient).build();
        sniffOnFailureListener.setSniffer(sniffer);
    }

    @Override
    public void postStop() throws Exception {
        // Close rest client
        this.restClient.close();
    }

    public void onReceive(Object message) throws Throwable {
        logger.debug("Received message: {} from sender: {}", message.toString(), getSender().getClass());
        JsonObject enterpriseEvent;
        if(message instanceof JsonObject){
            enterpriseEvent = (JsonObject) message;

            // Create ES doc
            this.createDoc(enterpriseEvent);
        } else{
            logger.warning("Message receive is not of type JsonObject. Found: {}", message);
        }
    }

    private void createDoc(final JsonObject boxEvent){
        // Cast enterprise event json into a string entity for POST request
        HttpEntity enterpriseEventEntity = null;
        try {
            enterpriseEventEntity = new NStringEntity(boxEvent.toString());
        } catch (UnsupportedEncodingException e) {
            logger.error(e, "Unsupported encoding found for json string entity");
        }

        // Perform async request to create search doc in elasticsearch
        String endpoint = "/" + this.indexName + "/" + this.indexType + "/";
        this.restClient.performRequestAsync(
            HTTP_POST,
            endpoint,
            Collections.<String, String>emptyMap(),
            enterpriseEventEntity,
            new ResponseListener() {
                public void onSuccess(Response response) {
                    try {
                        logger.info("Created Elasticsearch doc with Box Enterprise event type: {} with event id: {}",
                                boxEvent.get("type").asString(),
                                boxEvent.get("id"));
                        logger.debug("Created elasticsearch doc with Box enterprise event: {}", boxEvent.toString(WriterConfig.PRETTY_PRINT));
                        logger.debug("Search doc creation response: {}", EntityUtils.toString(response.getEntity()));
                    } catch (IOException ioe) {
                        logger.error(ioe, "Encounter IO exception parsing and printing json to logs");
                    }
                }
                public void onFailure(Exception e) {
                    logger.error(e, "Failed to create search doc");
                }
            });
    }
}
