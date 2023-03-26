Kafka CLI
* Kafka client can connect to one of the brokers (kafka servers) directly. This's called bootstrap-server. No need to connect to zookeeper
* We can describe the topic to get info about partition, replication, and ISR (in-synced replicas)
* When produce a message, we can use ```acks=all``` for the leader broker to wait for acknowledgement of all ISRs before acknowledge the producer
* By default, producer will use something call ```StickyPartitioner``` to efficiently produce a message (with no key specified). This can cause consecutive messages to go to the same partition. ```--producer-property partitioner.class=org.apache.kafka.clients.producer.RoundRobinPartitioner``` to force the producer to exhibit round-robin partitioning
* The offset of the consumer group is stored in kafka topic, **not in** zookeeper
* Consumers in the same consumer group will consume the message in each partition mutually exclusive
* If you start a consumer in a new consumer group with ```--from-beginning```. The first time it'll read all of the messages. But if you start it the 2nd time, it'll not read from the beginning again because it already commit the offset of that particular group
* Use ```describe``` on consumer group to get info about what topic the consumer group is listening to. You can also get info about the current offset, and lag on each partition of each topic 
* If you start a consumer without specifying a group, kafka will crate a temporarily group that will be cleared out after some time
* There are many ways to reset the offset (to the beginning, to a particular offset, to date-time, etc.)
* Kafka is very fast because it just store the sequence of bytes (0,1). The job of serializer/ deserializer is done on producer/ consumer. Kafka doesn't verify your data (no CPU usage), not even loading them into memory (zero copy)

Java API
* When a consumer join/ leave a group, kafka will do rebalance the partition to each consumer. By default, it'll do eager rebalance (stop-the-world) by stopping all consumers before reassigning partitions. Also there's no guarantee that the same consumer will get the same partition
* We can specify rebalancing strategy to be ```Cooperative Rebalance``` to avoid stopping consumers. This can be done by setting ```partition.assignment.strategy``` to ```CooperativeStickyAssignor``` to Consumer
* If you want the same partition to be assigned to the same Consumer. You can specify ```group.instance.id``` to make that Consumer become **static member**. Upon the static member leaving the group, the partition will not be assigned to another consumer until ```session.timeout.ms``` has passed (after that, the partition will go to another consumers)
* The consumer will commit the offset when ```.poll()``` is called and after ```auto.commit.interval.ms``` has elapsed (default 5000) and ```enable.auto.commit=true```
* If you need to do manual offset commit (```enable.auto.commit=false```), then need to use ```commitSync()``` or ```commitAsync()```

Producer
* Use property ```acks```
    * ```0```: producer not wait for acknowledgement. Good for metrics collection
    * ```1```: wait for leader acknowledgement. Leader just write data in a local log. Data loss can occurred if leader crash before data being replicated
    * ```all``` (or -1): leader + ISR acknowledgement (no data loss). This also work with ```min.insync.replicas``` to check are enough replicas defined by this acknowledged
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
* ```partitioner.class``` is used to defined a class that derive a partition based on the given key (default is null). In kafka >= 2.4,If the key is null, it'll use ```StickyPartitioner``` to find the partition for the group of messages (in older version, it use ```RoundRobinPartitioner```). That means multiple messages (with no key, send at around the same time) sent consecutively will be put in the same partition. This's done to increase throughput
* In the event that broker can't keep up with the speed that producer send, producer will queue up the message in a memory defined by ```buffer.memory``` (in bytes). If this buffer is fulled, when you do ```send()```, the producer will be blocked (instead of being async operation). It'll wait upto ```max.block.ms``` ms before throwing an exception. This ususally means the brokers are down or overloaded

Consumer
* Delivery Semantics - the consumer will commit the offset after reading a **batch** of messages
    * **At Most Once** - commit the whole batch is processed. If producer go down, some message can remained unprocessed
    * **At Least Once** - commit only after processed the whole batch. The operation need to be idempotent
    * **Exactly Once** - need to use Transactional API/ use idempotent consumer
* To make the opration idempotent, one strategy is generating an unique id from the message using ```record.topic() + "_" + record.partition() + "_" + record.offset()``` (in case you need to insert data into DB using some unique fields). Of course, it'll be better if the message has some unique id itself that can be derived from
* if you set ```enable.auto.commit=false```, use ```commitSync()/ commitAsync()``` to control when to commit manually. Like the case that set ```enable.auto.commit=true```, these are ```at-least-once``` commit strategy(**NOTE** the program control flow needed to be in the same thread or else you'll fall in to ```at-most-once```)
* A very advance way to manage commit strategy is to store the offset somewhere else entirely and you ```seek()``` to fetch the message. You can do something like store the offset and perform DB operations in a single transaction to make the operation ```exactly-once```
* By default, kafka retains messages for 7 days. If the consumer is down or didn't read data longer than that, it can cause the offset to be invalid. If you set ```auto.offset.reset=none```, it'll throw exception if no offset is found
* By default, the offset can be lost if:
    * no data read in 1 day(kafka < 2.0)
    * no data read in 7 days(kafka >= 2.0)
* The above can be configured on broker settings: ```offset.retention.minutes```. You may want to config it to be longer    
* To reset the offset of the consumer group, **you NEED to take all consumers of that specific group DOWN**. If you attempt to reset the offset while some consumers in the group is still UP, you can see something like 
> Error: Assignments can only be reset if the group 'mygroup' is inactive, but the current state is Stable
* Consumer internally send a heartbeat to broker to notify that it's still up. ```heartbeat.interval.ms``` controls interval that it send the heartbeat (default to 3 seconds). Typically, this value should we 1/3 of ```session.timeout.ms```. In the case that a consumer is dead and you want a new consumer to be rebalanced faster, you'll set ```session.timeout.ms``` (and ```heartbeat.interval.ms```) to lower values
* Another mechanism that broker use to determine whether the consumer is dead or not is ```max.poll.interval.ms``` (default 5 mins). This's the maximum time between 2 poll called before the consumer is declared dead. This is important in case the processing take time (consumer become stuck). ```max.poll.records``` (default 500) controls how many record received per poll, and this will affect how fast the consumer can process. Increase this if message is very small. Decrease this if there are too many records to process
* ```fetch.min.bytes``` (default 1 byte) indicate the minimum amount of data you want to pull on each ```poll()```
* ```fetch.max.wait.ms``` (default 500) is maximum amount of time it will wait if ```fetch.min.bytes``` is not fulfilled
* ```max.partition.fetch.bytes``` (default 1MB) maximum data **per partition** that'll be fetched
* ```fetch.max.bytes``` (default 55 MB) maximum data return for each fetch request
* Consumer will by default read from the Leader but since kafka 2.4+, you can configure it to read from the closest ISR. This will benefit in reducing network cost/latency -- this is called **Consumer Rack Awareness** setup

Extended APIs
* Kafka Connect Source/Sink: integrate kafka with other data sources source_connector -> kafka -> sink_connector
* Kafka Stream: process streaming data (built on top of Kafka API), has KSQL built on top of it
* Kafka Schema Registry: have a separate component (Schema registry) to validate the message content and support data format evolution. Use Avaro by default

Real World Guide
* Partition guideline: 3 * num_brokers (for < 6 brokers). 2 * num_brokers for larger num_brokers ( > 12 brokers). Adjusted for number of consumers needed to run in parallel
* Replication Factor between 2 - 4, usually 3
* Total number of partitions in overall cluster: max 200,000 partitions (ZK limit (if use ZK)), max 4,000 partitions per broker
* Topic naming: {message_type}.{dataset_name}.{data_name}.{data_format}. Ex: logging.app1.log_record.json

Advance Topic Configuration
* Each partition is made of multiple segments (files). Each segment has a range. The last segment is called Active segment and is the one that is being written to. Example: ```Segment0(offset 0 - 100) -> Segment1(offset 101 - 200) -> *Segment2(offset 201 - 250)(Active)```
    * ```log.segment.bytes``` control max size of a single segment (default 1GB)
    * ```log.segment.ms``` how long Kafka will wait until it wait to close the current segment (if above not met) (default 1 week)
* Smaller segment -> more segment per partition -> For Log Compaction, log compaction happens more often, Kafka must keep more segments/files opened, which can cause an error
* Log Cleanup Policy: 
    * Default ```log.cleanup.policy=delete``` will delete the message based on the age of data. Can also be configured to delete based on max size of the log (default = -1, means ininite)
        * ```log.rentention.hours```: 1 week by default
        * ```log.rentention.bytes```: -1 by default. This is the max size for each **partition**
            * common settings:
                * ```log.retention.hours=168, log.rentention.bytes=-1``` default config to keep data for 1 week
                * ```log.retention.hours=-1, log.rentention.bytes=xxx``` infinite time bounded by xxx bytes
    * ```log.cleanup.policy=compact``` to use Compaction strategy to delete messages based on the occurence of the key: this's default to some topics (i.e. ```__consumer_offsets```)
* Log Compaction keep the latest value of a key. Example:
    > {key: 1, value: xxx}{key:2, value: yyy}{key:1, value: zzz}{key: 3, value: aaa}

    After compaction:
    > {key:2, value: yyy}{key:1, value: zzz}{key: 3, value: aaa}

    * The ordering of the messages is kept, the offset is not changed. If consumer trying to read from the offset that's removed, that offset will be skipped
    * Deleted message can still be seen by consumers for ```delete.retention.ms``` (default 24 hours)
    * Log Compaction is done **after** segment is committed (so it doesn't prevent data duplication)
    * ```min.compaction.lag.ms``` (default 0) how long to wait before message can be compacted
    * ```min.cleanable.dirty.ratio``` (default 0.5) (lower means cleaning will happen more often but less efficient)
* Elect Non-ISR to be a leader
    * By default ```unclean.leader.election.enable=false```. If all ISRs going offline, you need to wait for them to go back up to produce data
    * Set ```unclean.leader.election.enable=true``` to use Non-ISR in case such scenario happen, but you can lose earlier data. So use this for something which availability is more important than data retention, like metrics/logs collection
* Send Large Message (default is limited to 1MB per message)
    * Option 1: send metadata in the message that points to external source (like S3) that contains the actual large message
    * Option 2: modify configuration on Producer & Broker & Consumer
