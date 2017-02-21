package com.box.platform;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.box.platform.consumer.BoxEnterpriseEventsConsumer;
import com.box.platform.util.ElasticsearchIndexConfiguration;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * The Main class that starts the Akka Actor system and calls actors to check Elasticsearch index configuration prior to
 * calling the event to begin retrieving Box Enterprise Events.
 *
 * @author Kyle Adams
 * @email kadams@box.com
 */
public class BoxElasticEventsMain {

    public static void main(String[] args) {
        // Start actor system
        Config boxConfig = ConfigFactory.parseFile(new File(args[0]));

        ActorSystem actorSystem = ActorSystem.create("box-elastic-events-actor-system", boxConfig);
        Timeout timeout = new Timeout(Duration.create(15, TimeUnit.SECONDS));

        boolean indexFoundOrCreated = false;
        try {
            // Create or Validate Elasticsearch configuration
            ActorRef esIndexConfiguration = actorSystem.actorOf(Props.create(ElasticsearchIndexConfiguration.class));
            Future<Object> esIndexConfigFuture = Patterns.ask(esIndexConfiguration, "Starting ES index configuration...", timeout);
            Object asyncResult = Await.result(esIndexConfigFuture, timeout.duration());
            if(asyncResult instanceof Boolean){
                indexFoundOrCreated = (Boolean) asyncResult;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(indexFoundOrCreated){
            // Call Box enterprise events consumer
            ActorRef boxEnterpriseEventsConsumer = actorSystem.actorOf(Props.create(BoxEnterpriseEventsConsumer.class));
            boxEnterpriseEventsConsumer.tell("Starting box events consumer...", ActorRef.noSender());
        }
    }
}