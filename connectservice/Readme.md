# Connect Service

Connect Service is a sample implementation of a web service which records usage
of Open Integration Engine, and provides access to a notifications feed. The service
implements the operations called by the client in `ConnectServiceUtil.java`.

## Running and testing

To start the example, run `docker-compose build` in this directory, followed by
`docker-compose up -d`.  You can use `http://localhost:3000` as `URL_CONNECT_SERVER`
in `ConnectServiceUtil.java`, and see recorded data 
[in Kibana.](http://localhost:5601/app/management/data/index_management/indices/index_details?indexName=registration)

## Not fit for production use as-is

This example is purely for expository purposes only.  Use as-is is not recommended.
At a minimum, it would be neccesary to:

- Use a reverse proxy to encrypt in-transit data
- Secure communication between the connectservice and elasticsearch
- Require authentication for elasticsearch and kibana
