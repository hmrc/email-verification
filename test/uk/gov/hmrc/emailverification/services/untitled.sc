case class testing(value1: String, value2: Option[String])


val sequence = Seq(
  testing("test1", Some("email1")),
  testing("test2", Some("email1")),
    testing("test3", Some("email2")),
    testing("test4", Some("email3")),
    testing("test5", Some("email2"))

)
sequence.distinctBy(sequence => sequence.value2)