package com.box.platform.util;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Validates that the Elasticsearch search index exists and will create it one does not exist. In addition,
 * will create the correct Elasticsearch type mapping for a given index.
 *
 * @author Kyle Adams
 * @email kadams@box.com
 */
public class ElasticsearchIndexConfiguration extends UntypedActor {
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    private static final String BOX_CONFIG_PREFIX = "box.platform.";
    private static final String ELASTIC_CONFIG_PREFIX = "elastic.";
    private String configPath;
    private String indexName;
    private int indexNumShards;
    private int indexNumReplicas;
    private String enterpriseType;
    private String enterpriseMapping;

    private TransportClient transportClient;
    private IndicesAdminClient indicesAdminClient;


    @Override
    public void preStart() throws Exception {
        logger.debug("{} prestart...", this.getClass().getName());
        // Load application configuration
        Config boxConfig = getContext().system().settings().config();
        this.configPath = boxConfig.getString(BOX_CONFIG_PREFIX + "config.path");
        String host = boxConfig.getString(ELASTIC_CONFIG_PREFIX + "host");
        int port = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.transport.port");
        this.indexName = boxConfig.getString(ELASTIC_CONFIG_PREFIX + "index.name");
        this.indexNumShards = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "index.num.shards");
        this.indexNumReplicas = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "index.num.replicas");
        this.enterpriseType = boxConfig.getString(ELASTIC_CONFIG_PREFIX + "enterprise.type");
        this.enterpriseMapping = boxConfig.getString(ELASTIC_CONFIG_PREFIX + "enterprise.mapping");
        boolean doSniff = boxConfig.getBoolean(ELASTIC_CONFIG_PREFIX + "client.transport.sniff");
        int pingTimeout = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.transport.ping.timeout");
        int nodesSamplerInterval = boxConfig.getInt(ELASTIC_CONFIG_PREFIX + "client.transport.nodes.sampler.interval");

        // Build ES Transport Client
        Settings settings = Settings.builder()
                .put("client.transport.ignore_cluster_name", true)
                .put("client.transport.sniff", doSniff)
                .put("client.transport.ping_timeout", pingTimeout, TimeUnit.SECONDS)
                .put("client.transport.nodes_sampler_interval", nodesSamplerInterval, TimeUnit.SECONDS)
                .build();
        this.transportClient  = new PreBuiltTransportClient(settings)
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
        this.indicesAdminClient = this.transportClient.admin().indices();
    }

    public void onReceive(Object message) throws Throwable {
        logger.debug("Received message: {} from sender: {}", message.toString(), getSender().getClass());

        // Call methods to determine if the ES index already exists
        if(this.indexExists()){
            logger.debug("Index found with name: {}", this.indexName);
            getSender().tell(true, getSelf());
        } else{
            this.createIndex();
        }
    }

    private boolean indexExists(){
        // Check if the ES index exists
        IndicesExistsResponse indicesExistsResponse = this.indicesAdminClient.prepareExists(this.indexName).execute().actionGet();
        logger.debug("Does index with name {} exist: {}", this.indexName, indicesExistsResponse.isExists());
        return indicesExistsResponse.isExists();
    }

    private void createIndex(){
        logger.debug("Creating new index with name: {}", this.indexName);
        // Create ES index
        try {
            CreateIndexResponse createIndexResponse = this.indicesAdminClient.prepareCreate(this.indexName)
                    .setSettings(Settings.builder()
                            .put("index.number_of_shards", this.indexNumShards)
                            .put("index.number_of_replicas", this.indexNumReplicas))
                    .addMapping(
                            this.enterpriseType,
                            new String(Files.readAllBytes(
                                    Paths.get(this.configPath + this.enterpriseMapping))))
                    .execute()
                    .actionGet();
            getSender().tell(true, getSelf());
            logger.debug("Index created with response: {}", createIndexResponse.toString());
        } catch (IOException ioe) {
            getSender().tell(false, getSelf());
            logger.error(ioe, "Failed to load index mapping file");
        }
    }

    @Override
    public void postStop() throws Exception {
        this.transportClient.close();
    }
}
