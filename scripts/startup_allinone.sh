#!/usr/bin/env bash
LOG_FILE=logs/startup_box_elastic_events.log
BOX_ELASTIC_EVENTS_VERSION=1.0

start()
{
  echo "Starting Elasticsearch..."
  ./elasticsearch/bin/elasticsearch -d

  echo "Starting Kibana..."
  ./kibana/bin/kibana > /dev/null 2>&1 &

  echo "Sleeping for 20 seconds. Waiting for Elasticsearch and Kibana to start..."
  sleep 20

  echo "Starting Box Elastic Events..."
  java -Xms128M -Xmx1G -jar box-elastic-events-$BOX_ELASTIC_EVENTS_VERSION-jar-with-dependencies.jar configuration/box-elastic-events.conf > /dev/null 2>&1 &

  cat << "EOF"
______               _____ _           _   _          _____                _
| ___ \             |  ___| |         | | (_)        |  ___|              | |
| |_/ / _____  __   | |__ | | __ _ ___| |_ _  ___    | |____   _____ _ __ | |_ ___
| ___ \/ _ \ \/ /   |  __|| |/ _` / __| __| |/ __|   |  __\ \ / / _ \ '_ \| __/ __|
| |_/ / (_) >  <    | |___| | (_| \__ \ |_| | (__    | |___\ V /  __/ | | | |_\__ \
\____/ \___/_/\_\   \____/|_|\__,_|___/\__|_|\___|   \____/ \_/ \___|_| |_|\__|___/

EOF
}


# Output to log and console
log()
{
	# Check if log file exists
	if [ -f ${LOG_FILE} ]; then
		# Roll log file over if the size is greater than 5mb
		LOG_SIZE=$(find ${LOG_FILE} -size +5M)
		if [ -z ${LOG_SIZE} ]; then
				CURRENT_TIME=$(date +%F-%T)
				# echo "$current_time - $*" > $log_file
				echo "$CURRENT_TIME - $*" | tee -a ${LOG_FILE}
			else
				cp ${LOG_FILE} ${LOG_FILE}.$(date +%Y.%m.%d.%H.%M.%S).bak
				rm ${LOG_FILE}
		fi
		else
			touch ${LOG_FILE}
	fi
}

# Start ES, Kibana, and Box Elastic Events
start