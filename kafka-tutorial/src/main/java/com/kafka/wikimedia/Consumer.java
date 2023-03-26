package com.kafka.wikimedia;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Consumer {
    static Logger logger = LoggerFactory.getLogger(Consumer.class.getSimpleName());

    public static void main(String[] args) {
        Consumer c = new Consumer();
        c.consume();
    }

    void consume(){
        Properties properties = new Properties();

        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "mygroup");
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // set this to do a manual offset commit
        //properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        KafkaConsumer<String,String> consumer = new KafkaConsumer<>(properties);

        String topic = "wikimedia.recentchange";

        consumer.subscribe(Arrays.asList(topic));

        try(consumer){
            while(true){
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(3000));

                logger.info("receive: " + records.count());

                for(ConsumerRecord<String,String> record: records){
                    // we can generate unique id for the given message
                    String id = record.topic() + "_" + record.partition() + "_" + record.offset();
                    logger.info("processing:" + record.value());
                }
                    
                //manual commit
                //consumer.commitSync();
            }
        }
    }
}
