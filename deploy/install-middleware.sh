#!/bin/bash
set -e

REDIS_PASSWORD='CHANGE_ME'
KAFKA_VERSION='3.8.1'
SCALA_VERSION='2.13'
ROCKETMQ_VERSION='5.3.1'
ES_VERSION='8.15.3'
ES_PASSWORD='CHANGE_ME'
MONGO_VERSION='7.0'
MYSQL_ROOT_PASSWORD='CHANGE_ME'

log() {
    echo ""
    echo "========== $1 =========="
}

log "system packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl wget vim net-tools unzip tar apt-transport-https ca-certificates gnupg lsb-release openjdk-21-jdk >/dev/null 2>&1 || {
    apt-get install -y -qq curl wget vim net-tools unzip tar apt-transport-https ca-certificates gnupg lsb-release
}
java -version 2>&1 | head -1

log "mysql 8.0"
if ! command -v mysql >/dev/null 2>&1; then
    apt-get install -y -qq mysql-server >/dev/null 2>&1
fi
systemctl enable mysql >/dev/null 2>&1 || true
systemctl start mysql >/dev/null 2>&1 || service mysql start >/dev/null 2>&1 || true
sleep 3
mysql -e "alter user 'root'@'localhost' identified with mysql_native_password by '${MYSQL_ROOT_PASSWORD}';" 2>/dev/null || true
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "create user if not exists 'dong'@'%' identified by '${MYSQL_ROOT_PASSWORD}'; grant all privileges on *.* to 'dong'@'%'; flush privileges;" 2>/dev/null || \
    mysql -e "create user if not exists 'dong'@'%' identified by '${MYSQL_ROOT_PASSWORD}'; grant all privileges on *.* to 'dong'@'%'; flush privileges;"
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "select version();" 2>/dev/null || mysql -e "select version();"

log "redis 7"
apt-get install -y -qq redis-server >/dev/null 2>&1
sed -i "s/^# requirepass .*/requirepass ${REDIS_PASSWORD}/" /etc/redis/redis.conf
sed -i "s/^bind .*/bind 0.0.0.0/" /etc/redis/redis.conf
sed -i "s/^protected-mode .*/protected-mode no/" /etc/redis/redis.conf
systemctl enable redis-server >/dev/null 2>&1 || true
systemctl restart redis-server >/dev/null 2>&1 || service redis-server restart >/dev/null 2>&1 || redis-server /etc/redis/redis.conf --daemonize yes
sleep 2
redis-cli -a "${REDIS_PASSWORD}" ping 2>/dev/null

log "mariadb 10.11 (replica demo)"
if ! command -v mariadbd >/dev/null 2>&1; then
    apt-get install -y -qq mariadb-server >/dev/null 2>&1 || echo "mariadb package unavailable, skipped"
fi
if command -v mariadbd >/dev/null 2>&1; then
    mkdir -p /data/mariadb /var/run/mysqld
    chown -R mysql:mysql /data/mariadb /var/run/mysqld 2>/dev/null || true
    if [ ! -d /data/mariadb/mysql ]; then
        mysql_install_db --user=mysql --datadir=/data/mariadb >/dev/null 2>&1 || true
    fi
    nohup mariadbd --user=mysql --datadir=/data/mariadb --port=3307 --socket=/var/run/mysqld/mariadb.sock \
        --bind-address=0.0.0.0 --skip-name-resolve >/var/log/mariadb-lab.log 2>&1 &
    sleep 6
    mysql -h127.0.0.1 -P3307 -uroot -e "select version();" 2>/dev/null || echo "mariadb not ready, check /var/log/mariadb-lab.log"
fi

log "kafka ${KAFKA_VERSION} (kraft, no zookeeper)"
cd /opt
if [ ! -d kafka_${SCALA_VERSION}-${KAFKA_VERSION} ]; then
    wget -q "https://archive.apache.org/dist/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz" || \
        wget -q "https://downloads.apache.org/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz"
    tar -xzf kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz
fi
cd kafka_${SCALA_VERSION}-${KAFKA_VERSION}
KAFKA_CLUSTER_ID=$(bin/kafka-storage.sh random-uuid)
sed -i "s#^log.dirs=.*#log.dirs=/data/kraft-combined-logs#" config/kraft/server.properties
mkdir -p /data/kraft-combined-logs
bin/kafka-storage.sh format -t "${KAFKA_CLUSTER_ID}" -c config/kraft/server.properties >/dev/null 2>&1 || true
nohup bin/kafka-server-start.sh config/kraft/server.properties >/var/log/kafka.log 2>&1 &
sleep 15
bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092 --list 2>/dev/null && echo "kafka ready"

log "rocketmq ${ROCKETMQ_VERSION}"
cd /opt
if [ ! -d rocketmq-all-${ROCKETMQ_VERSION}-bin-release ]; then
    wget -q "https://archive.apache.org/dist/rocketmq/${ROCKETMQ_VERSION}/rocketmq-all-${ROCKETMQ_VERSION}-bin-release.zip" || \
        wget -q "https://dist.apache.org/repos/dist/release/rocketmq/${ROCKETMQ_VERSION}/rocketmq-all-${ROCKETMQ_VERSION}-bin-release.zip"
    unzip -q rocketmq-all-${ROCKETMQ_VERSION}-bin-release.zip
fi
cd rocketmq-all-${ROCKETMQ_VERSION}-bin-release
sed -i 's/-Xms[0-9g]*[mg]/-Xms512m/g; s/-Xmx[0-9g]*[mg]/-Xmx512m/g' bin/runserver.sh
sed -i 's/-Xms[0-9g]*[mg]/-Xms512m/g; s/-Xmx[0-9g]*[mg]/-Xmx1g/g' bin/runbroker.sh
nohup sh bin/mqnamesrv >/var/log/rocketmq-namesrv.log 2>&1 &
sleep 6
nohup sh bin/mqbroker -n 127.0.0.1:9876 autoCreateTopicEnable=true >/var/log/rocketmq-broker.log 2>&1 &
sleep 10
tail -3 /var/log/rocketmq-namesrv.log

log "elasticsearch ${ES_VERSION}"
cd /opt
wget -q "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${ES_VERSION}-linux-x86_64.tar.gz"
tar -xzf elasticsearch-${ES_VERSION}-linux-x86_64.tar.gz
cd elasticsearch-${ES_VERSION}
useradd -m esuser 2>/dev/null || true
chown -R esuser:esuser /opt/elasticsearch-${ES_VERSION}
cat >> config/elasticsearch.yml <<EOF
cluster.name: dong-lab
network.host: 0.0.0.0
http.port: 9200
discovery.type: single-node
xpack.security.enabled: true
xpack.security.http.ssl.enabled: false
xpack.security.transport.ssl.enabled: false
EOF
sysctl -w vm.max_map_count=262144 >/dev/null 2>&1
echo "vm.max_map_count=262144" >> /etc/sysctl.conf
su esuser -c "nohup /opt/elasticsearch-${ES_VERSION}/bin/elasticsearch >/var/log/es.log 2>&1 &"
sleep 45
curl -s -u "elastic:${ES_PASSWORD}" http://127.0.0.1:9200 | head -c 120 || true

log "mongodb ${MONGO_VERSION}"
curl -fsSL "https://www.mongodb.org/static/pgp/server-${MONGO_VERSION}.asc" | \
    gpg --dearmor -o /usr/share/keyrings/mongodb-server-${MONGO_VERSION}.gpg 2>/dev/null
echo "deb [signed-by=/usr/share/keyrings/mongodb-server-${MONGO_VERSION}.gpg] https://repo.mongodb.org/apt/ubuntu $(lsb_release -cs)/mongodb-org/${MONGO_VERSION} multiverse" \
    > /etc/apt/sources.list.d/mongodb-org-${MONGO_VERSION}.list
apt-get update -qq
apt-get install -y -qq mongodb-org >/dev/null 2>&1
sed -i 's/^  bindIp: .*/  bindIp: 0.0.0.0/' /etc/mongod.conf
systemctl enable mongod >/dev/null 2>&1 || true
systemctl start mongod >/dev/null 2>&1 || nohup mongod --config /etc/mongod.conf --fork >/dev/null 2>&1
sleep 5
mongosh --quiet --eval 'db.version()' 2>/dev/null || echo "mongodb installed, verify manually"

log "install finished"
netstat -tlnp 2>/dev/null | grep -E ':(3306|3307|6379|9092|9876|9200|27017)\s' || true
echo ""
echo "next: run deploy/init-remote-db.sh and set credentials in application-remote.yml"
