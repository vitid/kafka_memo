package com.kafka.stream.tutorial;

import java.util.Arrays;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WordcountApp {

    static Logger logger = LoggerFactory.getLogger(WordcountApp.class.getSimpleName());
    public static void main(String[] args) {
        WordcountApp app = new WordcountApp();
        app.run();
    }

    void run(){
        Properties prop = new Properties();

        // application.id is very important config
        prop.put(StreamsConfig.APPLICATION_ID_CONFIG, "word_count");
        prop.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        prop.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        prop.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, "0");
        prop.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        prop.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

        StreamsBuilder builder = new StreamsBuilder();

        // 1 - get stream from kafka
        KStream<String, String> kStream = builder.stream("word_count_input");

        // 2 - map string to a lower case
        KTable<String, Long> ktable = kStream.mapValues((val) -> val.toLowerCase())
                                        // 3 - split by a space and use flatMapValues
                                        .flatMapValues((val) -> Arrays.asList(val.split(" ")))
                                        // 4 - transform key to its associated word
                                        .selectKey((key,word) -> word)
                                        // 5 - group by the key(word)
                                        .groupByKey()
                                        // 6 - count frequency
                                        .count(Named.as("Counts"))
                                        ;
        
        ktable.toStream().to("word_count_output", Produced.with(Serdes.String(), Serdes.Long()));
        /*
         * use this to listen to the result:
         kafka-console-consumer.sh --bootstrap-server localhost:9092 \
                                   --topic word_count_output \
                                   --formatter kafka.tools.DefaultMessageFormatter \
                                   --property print.timestamp=true \
                                   --property print.key=true \
                                   --property print.value=true \
                                   --property print.partition=true \
                                   --from-beginning \
                                   --property key.deserializer=org.apache.kafka.common.serialization.StringDeserializer \
                                   --property value.deserializer=org.apache.kafka.common.serialization.LongDeserializer
         */
        Topology topology = builder.build();
        KafkaStreams streams = new KafkaStreams(topology, prop);
        streams.start();

        // print out topology
        logger.info("topology:" + topology.describe().toString());

        // add shutdown hook for shutting down gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

        // after running, observe that 2 new topics are created:
        // word_count-KSTREAM-AGGREGATE-STATE-STORE-0000000004-changelog
        // word_count-KSTREAM-AGGREGATE-STATE-STORE-0000000004-repartition
    }
}
