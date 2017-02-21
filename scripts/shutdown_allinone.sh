#!/usr/bin/env bash
LOG_FILE=logs/shutdown_box_elastic_events.log

stop()
{
  echo "Stopping Elasticsearch..."
  for i in `ps auxw|grep elasticsearch|grep -v grep|awk '{ print $2 }'`; do kill $i; done


  echo "Stopping Kibana..."
  for i in `ps auxw|grep kibana|grep -v grep|awk '{ print $2 }'`; do kill $i; done


  echo "Stopping Box Elastic Events..."
  for i in `ps auxw|grep box-elastic-events|grep -v grep|awk '{ print $2 }'`; do kill $i; done

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

# Stop ES, Kibana, and Box Elastic Events
stop
