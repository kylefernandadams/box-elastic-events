#!/usr/bin/env bash
LOG_FILE=install.log
BOX_KEY_PASS=box4U

install_prereqs()
{
    echo "Checking if Homebrew exists..."
    if which brew > /dev/null; then
        echo "Homebrew already installed!"
     else
        echo "Homebrew not found. Installing Homebrew..."
        /usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
     fi

    echo "Checking if Elasticsearch formula exists in Homebrew..."
    if brew ls --versions elasticsearch > /dev/null; then
        echo "Elasticsearch Homebrew formula already installed!"
    else
        echo "Elasticsearch not found! Installing Elasticsearch Homebrew formula..."
        brew install elasticsearch
    fi

    echo "Checking if Kibana forumla exists in Homebrew..."
    if brew ls --versions kibana > /dev/null; then
        echo "Kibana Homebrew formula already installed!"
    else
        echo "Kibana not found! Installing Kibana Homebrew formula..."
        brew install kibana
    fi

    echo "Checking if Maven forumula exists in Homebrew..."
    if brew ls --versions maven > /dev/null; then
        echo "Maven Homebrew formula already installed!"
    else
        echo "Maven not found! Installing Maven Homebrew formula..."
        brew install maven
    fi
}

install()
{
  log "Copying confiuration files..."
  mkdir configuration
  cp src/main/resources/* configuration
  mv configuration/application.conf configuration/box-elastic-events.conf
  log "Successfully configuration files."

  log "Cleaning and packaging box-elastic-events maven project"
  mvn clean package
  log "jar file with dependencies created"
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

# Verify and install prerequisites
install_prereqs

# Start install
install
