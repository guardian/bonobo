import httplib2
import json

url = "http://localhost:8001/plugins?size=10"
print(url)

while url != "":
        http = httplib2.Http()
        content = http.request(url)[1]
        print(content)

        parsed = json.loads(content)

        data = parsed['data']
        for plugin in data:
                name = plugin['name']
                if name == "rate-limiting":
                        consumer_id = plugin['consumer_id']
                        config = plugin['config']
                        print(consumer_id)

                        request = {'name': name, 'consumer_id': consumer_id, 'config': config}
                        print(json.dumps(request))

                        # TODO Post this to /services/interal/plugins

        url = ""
        if 'next' in parsed:
                next = parsed['next']
                print(next)
                url = next


