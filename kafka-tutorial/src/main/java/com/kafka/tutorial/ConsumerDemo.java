package com.kafka.tutorial;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerDemo {

    static Logger logger = LoggerFactory.getLogger(ConsumerDemo.class.getSimpleName());
    public static void main(String[] args) {
        ConsumerDemo c = new ConsumerDemo();

        c.consume();
    }

    void consume(){
        Properties properties = new Properties();

        String groupId = "my-java-application";
        String topic = "demo_java";
        properties.setProperty("bootstrap.servers", "localhost:9092");

        properties.setProperty("key.deserializer", StringDeserializer.class.getName());
        properties.setProperty("value.deserializer", StringDeserializer.class.getName());

        properties.setProperty("group.id", groupId);
        // none - if we don't have this consumer group -> then we failed
        // earliest - read from the beginning, like in the flag --from-beginning
        // latest - read from the whatever being produced recently
        properties.setProperty("auto.offset.reset", "earliest");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);

        consumer.subscribe(Arrays.asList(topic));

        Thread mainThread = Thread.currentThread();

        // do graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(){
            public void run(){
                logger.info("detect a shutdown...");

                // this'll throw WakeupException
                consumer.wakeup();

                try {
                    // wait for the main thread to complete
                    mainThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        try
        {
            // poll for data
            while(true){
                logger.info("polling...");

                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                for(ConsumerRecord<String, String> record: records){
                    logger.info("""
                        key: %s
                        value: %s
                        partition: %d
                        offset: %d
                    """.formatted(record.key(), record.value(), record.partition(), record.offset())
                    );
                }
            }
        } catch(WakeupException e){
            logger.info("detect wakeup...");
        } catch(Exception e){
            logger.error("unexpected exception", e);
        } finally {
            // this close and also commit the offset, revoke consumer group participation
            consumer.close();
            logger.info("consumer is gracefully closed");
        }
    }
}
