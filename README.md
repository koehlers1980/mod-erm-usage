# mod-erm-usage

Copyright (C) 2018 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.


# Installation

```
git clone ...
cd mod-erm-usage
mvn clean install
```

# Run plain jar

```
cd mod-erm-usage-server
env \
DB_USERNAME=folio_admin \
DB_PASSWORD=folio_admin \
DB_HOST=localhost \
DB_PORT=5432 \
DB_DATABASE=okapi_modules \
java -jar target/mod-erm-usage-server-fat.jar
```

# Run via Docker

### Build docker image

```
$ docker build -t mod-erm-usage .
```

### Run docker image
```
$ docker run -p 8081:8081 -e DB_USERNAME=folio_admin -e DB_PASSWORD=folio_admin -e DB_HOST=172.17.0.1 -e DB_PORT=5432 -e DB_DATABASE=okapi_modules mod-erm-usage
```

### Register ModuleDescriptor

```
$ cd target
$ curl -w '\n' -X POST -D - -H "Content-type: application/json" -d @ModuleDescriptor.json http://localhost:9130/_/proxy/modules
```

### Register DeploymentDescriptor

Change _nodeId_ in _DockerDeploymentDescriptor.json_ to e.g. your hosts IP address (e.g. 10.0.2.15). Then execute:

```
$ curl -w '\n' -X POST -D - -H "Content-type: application/json" -d @DockerDeploymentDescriptor.json http://localhost:9130/_/discovery/modules
```

### Activate module for tenant

```
$ curl -w '\n' -X POST -D - -H "Content-type: application/json" -d '{ "id": "mod-erm-usage-2.0.0-SNAPSHOT"}' http://localhost:9130/_/proxy/tenants/diku/modules
```

## Statistics

`StatisticsAPI.java` contains a mocked API to fetch statistics about an agreement. The type of statistics is _cumulative access per time_. The following five queries are implemented:

* `/statistics/cumulative-access?agreementId=annualre-view-s170-0000-000000000000&udpId=annualre-view-scou-nter-000000000000&start=2017-01&end=2017-12`
* `/statistics/cumulative-access?agreementId=cambridg-e170-0000-0000-000000000000&udpId=cambridg-ecou-nter-0000-000000000000&start=2017-01&end=2017-12`
* `/statistics/cumulative-access?agreementId=cambridg-e170-0000-0000-000000000000&udpId=cambridg-ecou-nter-0000-000000000000&start=2017-01&end=2017-05`
* `/statistics/cumulative-access?agreementId=jstor170-0000-0000-0000-000000000000&udpId=jstorcou-nter-0000-0000-000000000000&start=2017-01&end=2017-12`
* `/statistics/cumulative-access?agreementId=jstor170-0000-0000-0000-000000000000&udpId=jstorcou-nter-0000-0000-000000000000&start=2017-03&end=2017-04`

The API returns some fake data about the count of cumulative full text PDF accesses of the resources of a certain agreement per month, e.g.:

```json
{
  "agreementId" : "jstor170-0000-0000-0000-000000000000",
  "usageDataProviderId" : "jstorcou-nter-0000-0000-000000000000",
  "statistics" : [ {
    "month" : "2017-03",
    "sumFullTextPDF" : 13420
  }, {
    "month" : "2017-04",
    "sumFullTextPDF" : 11608
  } ]
}
```

## Additional information

### Issue tracker

See project [MODERM](https://issues.folio.org/browse/MODERM)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

