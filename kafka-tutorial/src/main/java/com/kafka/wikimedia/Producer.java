package com.kafka.wikimedia;

import java.net.URI;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Producer {
    static Logger logger = LoggerFactory.getLogger(Producer.class.getSimpleName());
    public static void main(String[] args) {
        Producer p = new Producer();
        p.produce();   
    }

    void produce(){

        Properties properties = new Properties();

        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // set safe producer (for older kafka version)
        properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE + "");

        // high throughput setting
        properties.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, "32768"); // 32KB
        properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20"); // wait for 20 MS

        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

        String topic = "wikimedia.recentchange";

        EventHandler eventHandler = new WikimediaChangeHandler(producer, topic);

        String url = "https://stream.wikimedia.org/v2/stream/recentchange";

        EventSource.Builder builder = new EventSource.Builder(eventHandler, URI.create(url));

        EventSource eventSource = builder.build();

        // start the producer in another thread
        eventSource.start();

        // need to block a code here
        try {
            TimeUnit.MINUTES.sleep(1);
        } catch (InterruptedException e) {
            
        }
    }

    class WikimediaChangeHandler implements EventHandler{

        KafkaProducer<String, String> producer;
        String topic;

        WikimediaChangeHandler(KafkaProducer<String, String> producer, String topic){
            this.producer = producer;
            this.topic = topic;
        }

        @Override
        public void onOpen() throws Exception {
        }

        @Override
        public void onClosed() throws Exception {
            // don't forget to close producer gracefully
            producer.close();
        }

        @Override
        public void onMessage(String event, MessageEvent messageEvent) throws Exception {
            producer.send(new ProducerRecord<String,String>(topic, messageEvent.getData()));
        }

        @Override
        public void onComment(String comment) throws Exception {
        }

        @Override
        public void onError(Throwable t) {
            logger.error("Error in steam reading", t);
        }
        
    }
}
