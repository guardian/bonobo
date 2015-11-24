# Parses mashery-keys.txt, converts it into the JSON format that Bonobo expects, and uploads it in batches.
#
# Usage: ruby send-to-bonobo.rb http://bonobo-host
#

require 'net/http'
require 'json'
require 'date'

def parse(json_file)
  mashery_keys = []
  lines = File.readlines(json_file)
  lines.each do |json|
    page_of_keys = JSON.parse(json)
    mashery_keys.concat(page_of_keys)
  end
  puts "Loaded #{mashery_keys.size} Mashery keys from file"
  grouped_by_user = mashery_keys.group_by do |k|
    if k['member'].nil?
      puts "This key does not have a user. A dummy user will be created for it. #{k}"
      'None'
    else
      # We treat the email address as the unique identifier of a user.
      # Note that there are a few cases of people registering multiple users with the same email address.
      k['member']['email']
    end
  end
  puts "Keys are owned by #{grouped_by_user.size} different users"
  grouped_by_user
end

def product_name(key)
  if key['application'].nil?
    ''
  else
    key['application']['name'] || ''
  end
end

def product_url(key)
  if key['application'].nil?
    ''
  else
    key['application']['uri'] || ''
  end
end

def limit(key, period)
  key['limits'].select{|x| x['period'] == period}.first['ceiling']
end

def tier(key)
  if key['developer_class'].nil?
    puts "This key has no developer_class. Setting the tier to Developer by default. #{key}"
    'Developer'
  else
    case key['developer_class']['name']
    when 'internal'
      'Internal'
    when 'rights-managed'
      'RightsManaged'
    when 'developer'
      'Developer'
    else
      puts "This key has an unexpected developer_class. Setting the tier to Developer by default. #{key}"
      'Developer'
    end
  end
end

def status(key)
  if key['status'] == 'active'
    'Active'
  else
    'Inactive'
  end
end

def convert_key(key)
  {
    :key => key['apikey'],
    :productName => product_name(key),
    :productUrl => product_url(key),
    :requestsPerDay => limit(key, 'day'),
    :requestsPerMinute => limit(key, 'second') * 60,
    :tier => tier(key),
    :status => status(key),
    :createdAt => key['created']
  }
end

def convert(keys_grouped_by_user)
  keys_grouped_by_user.map do |uid, keys|
    if uid == 'None'
      # create dummy user
      { 
        :name => 'Dummy user',
        :email => "dummy-user-#{rand(10000000)}@example.com",
        :companyName => '',
        :companyUrl => '',
        :createdAt => Time.now.utc.strftime('%Y-%m-%dT%H:%M:%SZ'),
        :keys => keys.map{ |k| convert_key(k) }
      }
    else
      key = keys.first
      { 
        :name => "#{key['member']['first_name']} #{key['member']['last_name']}",
        :email => key['member']['email'],
        :companyName => key['member']['company'],
        :companyUrl => key['member']['uri'],
        :createdAt => key['member']['created'],
        :keys => keys.map{ |k| convert_key(k) }
      }
    end
  end
end

def post_batch(bonobo_host, json_payload)
  uri = URI(bonobo_host)
  req = Net::HTTP::Post.new('/migrate', initheader = {'Content-Type' =>'application/json'})
  req.body = json_payload
  response = Net::HTTP.new(uri.hostname, uri.port).start do |http|
    http.request(req)
  end
  puts "#{Time.now} - Response from Bonobo was #{response.code} #{response.message}:
  #{response.body}"
end

if ARGV.size < 1
  abort('Usage: ruby send-to-bonobo.rb http://bonobo-host')
else
  bonobo_host = ARGV[0]
end

# Parse the keys downloaded from Mashery
keys_grouped_by_user = parse('mashery-keys.txt')

# Convert them to Bonobo's format
users = convert(keys_grouped_by_user)

# Upload them to Bonobo 10 at a time, sleeping in between, to avoid overloading Bonobo.
# The poor little monkey wasn't designed for this kind of load.
users.each_slice(10) do |batch|
  post_batch(bonobo_host, batch.to_json) 
  sleep 2
end

