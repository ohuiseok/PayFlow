#!/bin/sh
set -eu

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-kafka:9092}"
TOPICS="${PAYFLOW_KAFKA_TOPICS:-transfer.completed transfer.failed payment.settlement}"
PARTITIONS="${KAFKA_TOPIC_PARTITIONS:-6}"
REPLICATION_FACTOR="${KAFKA_TOPIC_REPLICATION_FACTOR:-1}"
KAFKA_TOPICS="/opt/kafka/bin/kafka-topics.sh"

for topic in $TOPICS; do
  "$KAFKA_TOPICS" \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions "$PARTITIONS" \
    --replication-factor "$REPLICATION_FACTOR"
done

for topic in $TOPICS; do
  "$KAFKA_TOPICS" \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --describe \
    --topic "$topic" >/dev/null
done

echo "Kafka topics are ready: $TOPICS"
