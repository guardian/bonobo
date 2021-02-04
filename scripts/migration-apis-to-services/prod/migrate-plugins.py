import httplib2
import json

url = "http://localhost:8001/plugins?size=10"
print(url)

while url != "":
    http = httplib2.Http()
    content = http.request(url)[1]

    parsed = json.loads(content)

    data = parsed['data']
    for plugin in data:
        name = plugin['name']
        if name == "rate-limiting":
            consumer_id = plugin['consumer_id']
            config = plugin['config']
            print(consumer_id)

            payload = {'name': name, 'consumer_id': consumer_id, 'config': config}
            print(json.dumps(payload))

            resp, content = http.request("http://localhost:8001/services/internal/plugins",
                    "PUT", body=json.dumps(payload),
                    headers={'content-type':'application/json'})
            print(content)

    url = ""
    if 'next' in parsed:
        next = parsed['next']
        print(next)
        url = next

