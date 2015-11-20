# Parses mashery-keys.txt, converts it into the JSON format that Bonobo expects, and uploads it in batches of 100.
#
# Usage: ruby send-to-bonobo.rb http://bonobo-host
#

require 'net/http'
require 'json'

def parse(json_file)
  mashery_keys = []
  lines = File.readlines(json_file)
  lines.each do |json|
    page_of_keys = JSON.parse(json)
    mashery_keys.concat(page_of_keys)
  end
  puts "Loaded #{mashery_keys.size} Mashery keys from file"
  grouped_by_user = mashery_keys.group_by { |k|
    if k['member'].nil?
      puts "Note: This key does not have a user. #{k}"
      "None"
    else
      k['member']['id']
    end
  }
  puts "Keys are owned by #{grouped_by_user.size} different users"
  grouped_by_user
end

def convert(keys_grouped_by_user)
  # TODO
end

def post_batch(bonobo_host, json_payload)
  uri = URI(bonobo_host)
  req = Net::HTTP::Post.new('/migrate', initheader = {'Content-Type' =>'application/json'})
  req.body = json_payload
  response = Net::HTTP.new(uri.hostname, uri.port).start do |http|
    http.request(req)
  end
  puts "Response from Bonobo was #{response.code} #{response.message}:
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

# Upload them to Bonobo 100 at a time
users.each_slice(100) { |batch| post(bonobo_host, batch.to_json) }

