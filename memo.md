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