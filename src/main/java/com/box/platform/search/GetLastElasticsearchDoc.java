package com.box.platform.search;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.typesafe.config.Config;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.sniff.SniffOnFailureListener;
import org.elasticsearch.client.sniff.Sniffer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Retrieves doc with the maximum created_at date so that it can be used to retrieve Box Enterprise Events within a given date range
 *
 * @author Kyle Adams
 * @email kadams@box.com
 */
public class GetLastElasticsearchDoc extends UntypedActor{
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    private static final String BOX_CONFIG_PREFIX = "box.platform.";
    private static final String ELASTIC_CONFIG_PREFIX = "elastic.";
    private static final String HTTP_GET = "GET";

    private RestClient restClient = null;
    private String configPath;
    private String indexName;
    private String indexType;
    private String maxCreateDateJson;
    private int connectionTimeout;
    private int socketTimeout;
    private int connectionRequestTimeout;
    private int timeInSeconds;
    private int timeInMinutes;
    private int timeInHours;
    private int timeInDays;

    @Override
    public void preStart() throws Exception {
        logger.debug("{} prestart...", this.getClass().getName());

        // Get application configuration
        Config boxConfig = getContext().system().settings().config();
        this.configPath = boxConfig.getString(BOX_CONFIG_PREFIX + "config.path");
        String host = boxConfig.getString(ELASTIC_CONFIG_PREFIX + "host");
        int port = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.rest.port");
        this.indexName = boxConfig.getString(ELASTIC_CONFIG_PREFIX + "index.name");
        this.indexType = boxConfig.getString(ELASTIC_CONFIG_PREFIX + "enterprise.type");
        this.maxCreateDateJson = boxConfig.getString(ELASTIC_CONFIG_PREFIX + "enterprise.max.created.at");
        this.connectionTimeout = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.rest.connection.timeout");
        this.socketTimeout = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.rest.socket.timeout");
        this.connectionRequestTimeout = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.rest.connection.request.timeout");
        int maxRetryTimeout = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.rest.max.retry.timeout");
        this.timeInSeconds = boxConfig.getInt(BOX_CONFIG_PREFIX + "lookback.seconds");
        this.timeInMinutes = boxConfig.getInt(BOX_CONFIG_PREFIX + "lookback.minutes");
        this.timeInHours = boxConfig.getInt(BOX_CONFIG_PREFIX + "lookback.hours");
        this.timeInDays = boxConfig.getInt(BOX_CONFIG_PREFIX + "lookback.days");

        // Instantiate ES RestClient and Sniffer
        try{
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
        catch (Exception e){
            logger.error(e, "Failed to get Elasticsearch REST Client");
        }
    }

    @Override
    public void postStop() throws Exception {
        this.restClient.close();
    }

    public void onReceive(Object message) throws Throwable {
        this.getLastCreatedAtDate();
    }

    private void getLastCreatedAtDate(){
        Map<String, Object> lastDocMap = new HashMap<String, Object>();
        Date lastCreatedAtDate;
        String endpoint = "/" + this.indexName + "/" + this.indexType + "/_search";
        HttpEntity getMaxCreatedAtEntity;

        try {
            getMaxCreatedAtEntity = new NStringEntity(new String(Files.readAllBytes(
                    Paths.get(this.configPath + this.maxCreateDateJson))));


            Response response = this.restClient.performRequest(
                    HTTP_GET,
                    endpoint,
                    Collections.<String, String>emptyMap(),
                    getMaxCreatedAtEntity);
            String responseString = EntityUtils.toString(response.getEntity());
            logger.debug("Found result with max created_at date: {}", responseString);

            JsonObject maxCreatedAtJsonObject = Json.parse(responseString).asObject();
            JsonObject topLevelHits = maxCreatedAtJsonObject.get("hits").asObject();
            int totalHits = topLevelHits.get("total").asInt();

            String dateString = null;
            String nextStreamPosition;

            if(totalHits == 0){
                long pastTimeInMillis = 1000;
                if(timeInSeconds > 0){
                    pastTimeInMillis = pastTimeInMillis * timeInSeconds;
                }
                if(timeInMinutes > 0){
                    pastTimeInMillis = pastTimeInMillis * timeInMinutes;
                }
                if(timeInHours > 0){
                    pastTimeInMillis = pastTimeInMillis * timeInHours;
                }
                if(timeInDays > 0){
                    pastTimeInMillis = pastTimeInMillis * timeInDays;
                }
                lastCreatedAtDate = new Date(System.currentTimeMillis() - pastTimeInMillis);
                nextStreamPosition = "0";
            }else{
                JsonObject source = topLevelHits.get("hits").asArray().get(0).asObject().get("_source").asObject();
                dateString = source.get("created_at").asString();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                lastCreatedAtDate = dateFormat.parse(dateString);

                nextStreamPosition = source.get("next_stream_position").asString();
            }
            logger.debug("Found max created_at: {}", dateString);
            logger.debug("Found next stream position: {}", nextStreamPosition);

            lastDocMap.put("max_created_at_date", lastCreatedAtDate);
            lastDocMap.put("next_stream_position", nextStreamPosition);
            getSender().tell(lastDocMap, getSelf());

        } catch (UnsupportedEncodingException uee) {
            logger.error(uee, "Found unsupported encoding.");
        } catch (ParseException pe) {
            logger.error(pe, "Failed to parse date");
        } catch (IOException ioe) {
            logger.error(ioe, "Failed to load get_last_es_doc.json file");
        }
    }
}
