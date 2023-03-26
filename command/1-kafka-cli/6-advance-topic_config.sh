# create test topic for config
kafka-topics.sh --bootstrap-server localhost:9092 --create --topic topic_config_test --partitions 3 --replication-factor 1

# You can see custom config from 'Configs:' (if any)
kafka-topics.sh --bootstrap-server localhost:9092 --topic topic_config_test --describe
# Topic: topic_config_test	TopicId: aNcJAXapR_6K58Xnf1sjfA	PartitionCount: 3	ReplicationFactor: 1	Configs:
# 	Topic: topic_config_test	Partition: 0	Leader: 1	Replicas: 1	Isr: 1
# 	Topic: topic_config_test	Partition: 1	Leader: 1	Replicas: 1	Isr: 1
# 	Topic: topic_config_test	Partition: 2	Leader: 1	Replicas: 1	Isr: 1

# Show custom config for topic_config_test
kafka-configs.sh --bootstrap-server localhost:9092 --entity-type topics --entity-name topic_config_test --describe
# show empty because we didn't configure anything yet
# Dynamic configs for topic topic_config_test are:

# configure min.insync.replicas
kafka-configs.sh --bootstrap-server localhost:9092 --entity-type topics --entity-name topic_config_test --alter --add-config min.insync.replicas=2

# Describe again
kafka-configs.sh --bootstrap-server localhost:9092 --entity-type topics --entity-name topic_config_test --describe
# Dynamic configs for topic topic_config_test are:
#   min.insync.replicas=2 sensitive=false synonyms={DYNAMIC_TOPIC_CONFIG:min.insync.replicas=2, DEFAULT_CONFIG:min.insync.replicas=1}

#delete config
kafka-configs.sh --bootstrap-server localhost:9092 --entity-type topics --entity-name topic_config_test --alter --delete-config min.insync.replicas