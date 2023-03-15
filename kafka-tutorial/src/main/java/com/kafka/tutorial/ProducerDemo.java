package com.kafka.tutorial;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hello world!
 *
 */
public class ProducerDemo 
{
    static Logger logger = LoggerFactory.getLogger(ProducerDemo.class.getSimpleName());
    public static void main( String[] args )
    {
        ProducerDemo p = new ProducerDemo();
        
        //p.produceStickyPartitioner();

        p.produceWithKey();

    }

    void produceStickyPartitioner(){
        Properties properties = new Properties();

        properties.setProperty("bootstrap.servers", "localhost:9092");

        properties.setProperty("key.serializer", StringSerializer.class.getName());
        properties.setProperty("value.serializer", StringSerializer.class.getName());

        Callback callback = new Callback() {
            @Override
            public void onCompletion(RecordMetadata metadata, Exception exception) {
                if(exception == null){
                    logger.info("""
                    Received new metadata:
                    Topic: %s
                    Partition: %s
                    Offset: %s
                    Timestamp: %s
                    """.formatted(metadata.topic(), metadata.partition(), metadata.offset(), metadata.timestamp()));
                } else {
                    logger.error("Error:", exception);
                }
            }
        };

        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

        for (int i = 0; i < 10; i++) {
            // Observe that the client is using 'Sticky Partitioner' as the messages are batched and send to just one partition
            ProducerRecord<String, String> record = new ProducerRecord<String,String>("demo_java", "hello world");

            producer.send(record, callback);
        }

        // tell producer to send all data and block until done
        // this'll also by producer.close()
        producer.flush();

        producer.close();
    }

    void produceWithKey(){
        Properties properties = new Properties();

        properties.setProperty("bootstrap.servers", "localhost:9092");

        properties.setProperty("key.serializer", StringSerializer.class.getName());
        properties.setProperty("value.serializer", StringSerializer.class.getName());

        Map<String, List<Integer>> key2Partitions = new ConcurrentHashMap<>();

        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

        for (int j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {
                String key = i + "";
                String value = "message-" + i;
                ProducerRecord<String, String> record = new ProducerRecord<String,String>("demo_java", key, value);
    
                producer.send(record, new KeyCallback(key, key2Partitions));
            }
        }

        producer.flush();

        producer.close();

        System.out.println(key2Partitions);
    }

    class KeyCallback implements Callback {

        String key;
        Map<String, List<Integer>> key2Partitions;
        public KeyCallback(String key, Map<String, List<Integer>> key2Partitions){
            this.key = key;
            this.key2Partitions = key2Partitions;
        }
        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            if(exception == null){
                if(!key2Partitions.containsKey(key)) key2Partitions.put(key, new LinkedList<>());

                key2Partitions.get(key).add(metadata.partition());
            }
        }

    }
}
