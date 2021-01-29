import boto3
from boto3.dynamodb.conditions import Attr

print(boto3.session.Session().available_profiles)

capi = boto3.session.Session(profile_name='capi', region_name='eu-west-1')

print(capi)

# Get the service resource.
dynamodb = capi.resource('dynamodb')


table = dynamodb.Table('bonobo-CODE-keys')
print(table)

# A sensible epoch mills datetime will not be less than this
cutoffDate = 1000000000000

response = table.scan(
    Limit=25,
    FilterExpression=Attr('createdAt').lt(cutoffDate)
)

print(f"Found {response['Count']} / scanned {response['ScannedCount']}")

items = response['Items']
for item in items:
	keyValue = item['keyValue']
	createdAt = item['createdAt']
	if createdAt < cutoffDate:
	    updatedCreatedAt = createdAt * 1000
	    print(f"Item {keyValue} has old createdAt {createdAt} and will updated to {updatedCreatedAt}")
        // TODO apply update
