package github

import org.json4s._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

object DateTimeSerializer extends CustomSerializer[DateTime](format =>
  (
    {
      case JString(dateTime) => {
        val parser = ISODateTimeFormat.dateTimeParser
        parser.parseDateTime(dateTime)
      }
    },
    {
      case dateTime: DateTime => {
        val formatter = ISODateTimeFormat.dateTime
        JString(formatter.print(dateTime))
      }
    }
  )
)
