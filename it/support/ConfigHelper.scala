package support


object ConfigHelper {

  val payloadHandlingConfig: Map[String, String] = Map(
    "whitelisted-domains" -> ",  test.example.com  ,,    , example.com ,example.com,example.com:1234"
  )
}
