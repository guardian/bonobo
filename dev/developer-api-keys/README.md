# Developer API keys

Script to understand which API keys currently being used are issued under Developer tier. This is relevant since Gibbons 
deletes keys in the Developer tier, so we want to eyeball the generated list for any that might be used in PROD.

## API key usage file

The script takes as an argument the location of a CSV file with API key usage. To generate this file:
ElasticSearch SQL can be used to perform a count of requests by API key (see below). The query can be conveniently
executed in the Dev Tools section of Kibana. The results can then be exported to a temporary local file for use by the 
script, but NOT checked into version control!

```
GET _sql?format=csv
{
  "query": "SELECT \"queryparams.api-key\" as api_key, COUNT(1) AS request_count FROM \"logstash-capi-*\" WHERE \"@timestamp\" > NOW() - INTERVAL 1 MINUTE AND stage = 'PROD-AARDVARK' AND stack != 'content-api-preview' AND type = 'access' AND app = 'concierge' AND \"queryparams.api-key\" IS NOT NULL GROUP BY 1 ORDER BY request_count DESC"
}
```
