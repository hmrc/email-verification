import play.api.libs.json.Json
import uk.gov.hmrc.emailverification.models.Passcode

Json.toJson(Passcode("hi"))