package kong

import kong.Kong.KongCreateConsumerResponse
import org.joda.time.format.ISODateTimeFormat
import org.scalatest.{FlatSpec, Matchers}

class KongSpec extends FlatSpec with Matchers {

  "ConsumerClientResponse" should "be correctly constructed from Kong response" in {

    val createConsumerResponse = KongCreateConsumerResponse(
      id = "some-new-consumer-id",
      // Kong API 0.14 (and higher) renders data as 10 digit seconds since 1970.
      // Previous Kong API versions (such as 0.9) used a 14 digit milliseconds since 1970 format
      created_at = 1422386534
    )
    val keyId = "some-new-key-id"

    val result = Kong.consumerCreationResponseFor(createConsumerResponse, keyId)

    assert(result.createdAt.isEqual(ISODateTimeFormat.basicDateTimeNoMillis().parseDateTime("20150127T192214Z")))
  }

}
