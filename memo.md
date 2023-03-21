Kafka CLI
* Kafka client can connect to one of the brokers (kafka servers) directly. This's called bootstrap-server. No need to connect to zookeeper
* We can describe the topic to get info about partition, replication, and ISR (in-synced replicas)
* When produce a message, we can use ```acks=all``` to wait for acknowledgement of all replicas
* By default, producer will use something call ```StickyPartitioner``` to efficiently produce a message (with no key specified). This can cause consecutive messages to go to the same partition. ```--producer-property partitioner.class=org.apache.kafka.clients.producer.RoundRobinPartitioner``` to force the producer to exhibit round-robin partitioning
* The offset of the consumer group is stored in kafka topic, **not in** zookeeper
* Consumers in the same consumer group will consume the message in each partition mutually exclusive
* If you start a consumer in a new consumer group with ```--from-beginning```. The first time it'll read all of the messages. But if you start it the 2nd time, it'll not read from the beginning again because it already commit the offset of that particular group
* Use ```describe``` on consumer group to get info about what topic the consumer group is listening to. You can also get info about the current offset, and lag on each partition of each topic 
* If you start a consumer without specifying a group, kafka will crate a temporarily group that will be cleared out after some time
* There are many ways to reset the offset (to the beginning, to a particular offset, to date-time, etc.)

Java API
* When a consumer join/ leave a group, kafka will do rebalance the partition to each consumer. By default, it'll do eager rebalance (stop-the-world) by stopping all consumers before reassigning partitions. Also there's no guarantee that the same consumer will get the same partition
* We can specify rebalancing strategy to be ```Cooperative Rebalance``` to avoid stopping consumers. This can be done by setting ```partition.assignment.strategy``` to ```CooperativeStickyAssignor``` to Consumer
* If you want the same partition to be assigned to the same Consumer. You can specify ```group.instance.id``` to make that Consumer become **static member**. Upon the static member leaving the group, the partition will not be assigned to another consumer until ```session.timeout.ms``` has passed (after that, the partition will go to another consumers)
* The consumer will commit the offset when ```.poll()``` is called and after ```auto.commit.interval.ms``` has elapsed (default 5000) and ```enable.auto.commit=true```
* If you need to do manual offset commit (```enable.auto.commit=false```), then need to use ```commitSync()``` or ```commitAsync()```

Producer
* Use property ```acks```
    * ```0```: producer not wait for acknowledgement. Good for metrics collection
    * ```1```: wait for leader acknowledgement
    * ```all``` (or -1): leader + replicas acknowledgement (no data loss). This also work with ```min.insync.replicas``` to check are enough replicas defined by this acknowledged
        * if ```min.insync.replicas``` is 1 (default setting), this mean only the broker leader need to successfully ack
        * commonly, it's set to 2, and use replication factor = 3. This means the leader and at least 1 replica need to acknowledge. Note that if 2 replicas go down, the leader will reply with exception: NOT_ENOUGH_REPLICAS. This means it can tolerate at most 1 broker down. **Setting this number to 3 doesn't make sense** as that mean you don't tolerate any brokers down at all!
* Use property ```retries``` to retry x number of times. Use with ```retry.backoff.ms```
    * even you set ```retries``` to a very high number, it'll still retry upto ```delivery.timeout.ms``` and then stop
    * if you not use **idempotent** producer (incase of old kafka), in case of retry, there's a chance that message is send out of order
        * there's property ```max.in.flight.requests.per.connection```. Older kafka version should set this to 1 to ensure ordering (in case of retry). This can impact throughput. However, in kafka version >= 1.0.0, you can use idempotent producer instead
* Idempotent producer: this's to prevent duplicate message when producer fail to receive ack from kafka broker and retry. This is default since kafka 3.0. To enable manually, define property ```enable.idempotence``` to ```true```. Note that it'll come with the following default setting:
    * ```retries``` = ```Integer.MAX_VALUE```
    * ```max.in.flight.requests.per.connection``` = 1 (for older kafka) or 5 (for kafka >= 1.0)
    * ```acks``` = all
* Difference kafka versions have a different default setup so make sure to check that!!    
* Use compression to increase the throughput
    * can be enabled on producer level or on broker level 
    * setting high number of batch ```max.in.flight.requests.per.connection``` will benefit from this as multiple batches will be compressed. You can also config the producer to wait upto ```linger.ms``` or have upto ```batch.size``` (in bytes) to queueing up the compressed message before sending
    * ```batch.size``` is allocated per partition
    * ```compression.type``` to compress data. can be ```none```(default)/ ```snappy``` (good for text/json)/ ```lz4``` / etc
    * can be configured on the broker-level for all topics or per topic-level useing ```compression.type``` (default = ```producer``` meaning it's upto the producer and the broker will store the message as-is). If set to ```none``` the broker will decompress the message before stroing. Interesting behavior is if you define this value to be different than what producer send (i.e. set to ```lz4``` while producer set ```snappy```), the broker will decompress and re-compress the message again before storing it, resulted in wasted cpu cycle
    * consumer can consume the message as-is. No need to tell what decompression starategy it need to use. **The consumer is the one that decompress the message, not broker**, but this happened with no need of explicit input 
* When producer produces a message with a key, it uses ```murmur2``` algorithm to find the designated partition. The same key will go to the same partition **UNLESS** you change the number of partitions. It's better to create a new topic
* ```partitioner.class``` is used to defined a class that derive a partition based on the given key (default is null). In kafka >= 2.4,If the key is null, it'll use ```StickyPartitioner``` to find the partition for the group of messages (in older version, it use round-robin partitioner). That means multiple messages (with no key, send at around the same time) sent consecutively will be put in the same partition. This's done to increase throughput
* In the event that broker can't keep up with the speed that producer send, producer will queue up the message in a memory defined by ```buffer.memory``` (in bytes). If this buffer is fulled, when you do ```send()```, the producer will be blocked (instead of being async operation). It'll wait upto ```max.block.ms``` ms before throwing an exception. This ususally means the brokers are down or overloaded