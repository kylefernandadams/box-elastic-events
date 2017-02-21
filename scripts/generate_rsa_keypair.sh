#!/usr/bin/env bash
LOG_FILE=generate_keypair.log

generate()
{
  log "Starting RSA Keypair generation..."
  log "Generating private key..."
  echo "Please enter a password for the RSA keypair"
  read -s -p "Enter Password: " key_pass

  openssl genrsa -aes256 -passout pass:${key_pass} -out configuration/private_key.pem 2048
  log "Successfully generated private key."

  log "Generating public key..."
  openssl rsa -passin pass:${key_pass} -pubout -in configuration/private_key.pem -out configuration/public_key.pem
  log "Successfully generated public key."

  log "Printing contents of public_key.pem"
  cat configuration/public_key.pem
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

generate