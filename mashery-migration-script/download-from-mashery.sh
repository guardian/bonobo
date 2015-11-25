#! /bin/bash
: <<COMMENT

Downloads all the registered Mashery service keys for a given API.

How to use:

1. Login to the Mashery dashboard and go to the keys list for your API.
2. Open Chrome developer tools and make a note of a few things:
    a. the API's service_key
    b. the apikey and sig URL params of the AJAX requests to /proxy
    c. the id field in the JSON body of the latest AJAX request to /proxy
    d. the total number of pages in the list of keys
    e. the value of the X-Ajax-Synchronization-Token header
    f. the token in the X-Authorization header
3. Run this script, passing all that stuff as arguments

Usage: download-from-mashery.sh service-key apikey sync_token auth_token pages id sig

Output from the script: Key, user and application information in json format.
Each line of the file will be a json array.
COMMENT

set -e

[ $# -ne 7 ] && echo "Usage: scrape-mashery-keys.sh service-key apikey sync_token auth_token pages id sig" && exit 1

service_key=$1
apikey=$2
sync_token=$3
auth_token=$4
pages=$5
start_id=$6
sig=$7

rm -f mashery-keys.txt

for i in $(seq $pages); do
  (( id = start_id + i ))

  echo "========"
  echo "id = $id"
  echo "sig = $sig"
  echo "page = $i"

  curl -v "https://guardian.admin.mashery.com/proxy?apikey=${apikey}&sig=${sig}&mashery_area=Guardian" \
    -H "Origin: https://guardian.admin.mashery.com" \
    -H "Accept-Encoding: gzip, deflate" \
    -H "Accept-Language: en,en-GB;q=0.8,ja;q=0.6" \
    -H "X-Requested-With: XMLHttpRequest" \
    -H "X-Ajax-Synchronization-Token: ${sync_token}" \
    -H "Connection: keep-alive" \
    -H "Pragma: no-cache" \
    -H "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.130 Safari/537.36" \
    -H "Content-Type: application/json" \
    -H "Accept: */*" \
    -H "Cache-Control: no-cache" \
    -H "X-Authorization: Basic ${auth_token}" \
    -H "Referer: https://guardian.admin.mashery.com/" \
    --data-binary "{\"method\":\"object.query\",\"id\":$id,\"params\":[\"SELECT apikey, developer_class.name, status, created, application, member, id, rate_limit_ceiling, qps_limit_ceiling, rate_limit_exempt, qps_limit_exempt, limits FROM keys REQUIRE RELATED service WITH service_key = '$service_key' ORDER BY created DESC PAGE $i ITEMS 100\"]}" --compressed 2>/tmp/curl_output.txt | jq -c .result.items >> mashery-keys.txt 

  sig=$(grep -o "X-Mashery-Member-Mashery-Apisig: \w*" /tmp/curl_output.txt | cut -c34-)
  echo
  echo Updated sig to = $sig
  sleep 1
done
