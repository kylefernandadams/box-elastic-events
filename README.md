Box Elastic Events
==================

The Box Elastic Events project is an open source data pipeline framework to pull [Box Events](https://docs.box.com/reference#events) and persist them to [Elasticsearch](https://www.elastic.co/products/elasticsearch).

![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/00_Enterprise_Events_Overview_Dashboard.png "Kibana Dashboard")


Components Used
---------------
1. [Box Java SDK](https://github.com/box/box-java-sdk): Java bindings for Box's REST API's
2. [Akka.io](http://akka.io/): A Framework based on the [Actor Model](https://en.wikipedia.org/wiki/Actor_model) and is used for building high-performance, elastic, resilient, and distributed applications.
  * Note that the current implementation is not using Akka's cluster implementation, but can be easily adapted to support it.
3. [Elasticsearch Java REST Client](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/index.html): Used for most GET and POST requests to Elasticsearch.
4. [Elasticsearch Java Transport Client](https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/index.html): Used for managing Elasticserach Indices.
  * Note that if and when the Java REST Client is enhanced to support the index admin API's, the usage of the transport client will be deprecated.

All-in-one Package Installation
-----------------------------------
### Download the Package
1. Download and extract the self-contained package [here](https://github.com/kylefernandadams/box-elastic-events/releases/download/v1.0/box-elastic-events-allinone.tar.gz) or run the following wget command.
```bash
wget https://github.com/kylefernandadams/box-elastic-events/releases/download/v1.0/box-elastic-events-allinone.tar.gz
tar -xvf box-elastic-events-allinone.tar.gz
```

### Package Contents:
* box-elastic-events-1.0-jar-with-dependencies.jar
* Box Elastic Events configuration files
* Elasticsearch
* Kibana (OS X / darwin build only for now)

### Create a New Box Developer Application
1. Create a new application in the Box Developer Console [https://app.box.com/developers/services](https://app.box.com/developers/services).
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/01_Create_Application.png "Create Box App")
2. Choose the **Enterprise Integration** application type.
![alt text](https://github.com/kylefernandadams/box-elastic-events/blob/master/images/02_Choose_Integration.png "Choose Enterprise Integration")
3. Choose the **OAuth 2.0 with JWT (Server Authentication)** authentication method.
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/03_Authentication_Method.png "Choose JWT Auth")
4. Name your application.
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/04_Name_App.png "Name Your App")
5. In the configuration section of your newly created application, Copy the clientId and clientSecret OAuth credentials.
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/05_Copy_OAuth_Credentials.png "Copy OAuth Credentials")
6. Enable the **Manage enterprise properties** check box.
![alt text](https://github.com/kylefernandadams/box-elastic-events/blob/master/images/06_Enable_Manage_Enterprise_Properties.png "Enable Manage Enterprise Properties")

### Generate RSA Keypair
1. In the terminal, change directories to the unarchived box-elastic-events directory.
```bash
cd box-elastic-events
```
2. Run the **generate_rsa_keypair.sh** script in your terminal app of choice to generate the public and private keys.
3. Provide a password in the terminal
```bash
./generate_rsa_keypair.sh
2017-02-16-11:50:36 - Starting RSA Keypair generation...
2017-02-16-11:50:36 - Generating private key...
Please enter a password for the RSA keypair
Enter Password:
```
4. Copy the public_key.pem content that was printed in the console
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/07_Copy_Public_Key.png "Copy Public Key")
5. In the Box Developer Console, Click the Add a Public Key button
  * Note that may recent a prompt to enable 2-factor authentication
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/08_Add_Public_Key.png "Add Public Key")
6. Paste the public_key.pem text into the Public Key window and click the Verify and Save button
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/09_Paste_Public_Key.png "Past Public Key")
7. Copy the generated Public Key Id
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/10_Copy_Public_Key_Id.png "Copy Public Key Id")

### Authorize Application
1. In the Admin Console's App configuration authorize your new application using the clientId.
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/15_Authorize_App.png "Authorize App")

### Add Application Configuration
1. In the configuration/box-elastic-events.conf file, add the client.id in the box.platform section.
2. Add the client.secret
3. Add the enterprise.id. Note that your enterprise id can be retrieved from the Admin Console.
4. Add the public.key.id
5. Add the private.key.password
6. Add the config.path for the configuration directory.
7. Modify the lookback time value change how far back to capture enterprise events.
```hocon
box {
  platform {
    client.id = ""
    client.secret = ""
    enterprise.id = ""
    public.key.id = ""
    private.key.file = "private_key.pem"
    private.key.password = ""
    max.cache.entries = 100
    config.path = ""
    # The lookback config is only used on initial creation of the ES index.
    # If the ES index is already created, the last created_at date will be retrieved from ES.
    # Use a lookback value of 0 if you would like to exclude a specific time frame such as days.
    # Example: seconds = 60, minutess = 60, hours = 1, days = 0 to designate 1 hour
    lookback {
      seconds = 60
      minutes = 60
      hours = 24
      days = 365
    }
  }
}
```
8. In the elastic.enterprise section, modify the polling.interval value. The default value 2 minutes.
```hocon
enterprise {
    mapping = "enterprise-event-mapping.json"
    type = "enterprise"
    max.created.at = "get_last_es_doc.json"
    # polling interval in minutes
    polling.interval = 2
  }
```

### Startup Instructions
1. Confirm Java is installed by running the following command.
```bash
java -version
```
2. In the terminal, change directories to the unarchived box-elastic-events directory.
```bash
cd box-elastic-events
```
3. Run the startup shell script.
```bash
./startup_allinone.sh
```
4. Tail the log file to monitor for errors.
```bash
ls logs
tail -f logs/TYPE_CURRENT_DATE_HERE.log
```

### Validation Steps
1. Check that Elasticsearch is running by navigating to [http://localhost:9200](http://localhost:9200)
2. Check that the "box" Elasticsearch index has been properly created by navigating to [http://localhost:9200/box/_settings?pretty](http://localhost:9200/box/_settings?pretty)
* Alternatively, you can navigate to [http://localhost:9200/_cat/indices?v](http://localhost:9200/_cat/indices?v) to view all Elasticsearch indices
3. Execute a search GET request by navigating to [http://localhost:9200/box/_search?q=*&pretty](http://localhost:9200/box/_search?q=*&pretty)
* CAUTION: This will return all docs contained the Elasticsearch box index. See the ES Search [documentation](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-search.html) to refine the search appropriately.
* Navigate to Kibana at the following URL: [http://localhost:5601](http://localhost:5601)

### Kibana Configuration
1. Navigate to Kibana at the following URL: [http://localhost:5601](http://localhost:5601)
2. Change the default index name to **box** and change the Time-field name to **created_at**
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/11_Set_Kibana_Index.png "Set index name")
3. Switch to the **Advanced Settings** tab
4. Filter the setting by **timelion**
5. Change the **timelion:timefield** value to **created_at**
6. Change the **timelion:default_index** value to **box**
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/12_Timelion_Configuration.png "Timelion config")
7. Switch to the **Saved Objects** tab
8. Click import and navigate to the **configuration/kibana-configuration.json** file
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/13_Import_Dashboard_Config.png "Import Kibana Config")
9. Navigate to the Kibana Dashboard Page and change the date/time range as needed.
![alt text](https://github.com/kylefernandadams/box-elastic-events/raw/master/images/14_Change_Time_Range.png "Set Date/Time Range")


### Shutdown Instructions
1. Run the shutdown_allinone.sh script
```bash
./shutdown_allinone.sh
```

Disclaimer
----------
This project is an open source extension to the Box platform and is not officially supported by Box, Inc. Use at your own risk. If you encounter any problems, please log an [issue](https://github.com/kylefernandadams/box-elastic-events/issues).


License
-------
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.