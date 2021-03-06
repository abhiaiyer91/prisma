package com.prisma.api.connector.mysql.database

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.{LocalDateTime, ZoneOffset}
import java.util.Date

import com.prisma.gc_values._
import com.prisma.shared.models.TypeIdentifier
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json

object JdbcExtensions {

  def currentTimeStampUTC = {
    val today      = new Date()
    val exactlyNow = new DateTime(today).withZone(DateTimeZone.UTC)
    timeStampUTC(exactlyNow)
  }

  def timeStampUTC(dateTime: DateTime) = {
    val millies    = dateTime.getMillis
    val seconds    = millies / 1000
    val difference = millies - seconds * 1000
    val nanos      = difference * 1000000

    val res = Timestamp.valueOf(LocalDateTime.ofEpochSecond(seconds, nanos.toInt, ZoneOffset.UTC))
    res
  }

  implicit class PreparedStatementExtensions(val ps: PreparedStatement) extends AnyVal {
    def setGcValue(index: Int, value: GCValue): Unit = value match {
      case StringGCValue(string)     => ps.setString(index, string)
      case BooleanGCValue(boolean)   => ps.setBoolean(index, boolean)
      case IntGCValue(int)           => ps.setInt(index, int)
      case FloatGCValue(float)       => ps.setDouble(index, float)
      case CuidGCValue(id)           => ps.setString(index, id)
      case DateTimeGCValue(dateTime) => ps.setTimestamp(index, timeStampUTC(dateTime))
      case EnumGCValue(enum)         => ps.setString(index, enum)
      case JsonGCValue(json)         => ps.setString(index, json.toString)
      case NullGCValue               => ps.setNull(index, java.sql.Types.NULL)
      case x                         => sys.error(s"This method must only be called with LeafGCValues. Was called with: ${x.getClass}")
    }
  }

  implicit class ResultSetExtensions(val resultSet: ResultSet) extends AnyVal {

    def getId                     = CuidGCValue(resultSet.getString("id"))
    def getAsID(column: String)   = CuidGCValue(resultSet.getString(column))
    def getParentId(side: String) = CuidGCValue(resultSet.getString("__Relation__" + side))

    def getGcValue(name: String, typeIdentifier: TypeIdentifier.Value): GCValue = {
      val dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS").withZoneUTC()

      val gcValue: GCValue = typeIdentifier match {
        case TypeIdentifier.String  => StringGCValue(resultSet.getString(name))
        case TypeIdentifier.Cuid    => CuidGCValue(resultSet.getString(name))
        case TypeIdentifier.UUID    => UuidGCValue.parse_!(resultSet.getString(name))
        case TypeIdentifier.Enum    => EnumGCValue(resultSet.getString(name))
        case TypeIdentifier.Int     => IntGCValue(resultSet.getInt(name))
        case TypeIdentifier.Float   => FloatGCValue(resultSet.getDouble(name))
        case TypeIdentifier.Boolean => BooleanGCValue(resultSet.getBoolean(name))
        case TypeIdentifier.DateTime =>
          val sqlType = resultSet.getString(name)
          if (sqlType != null) {
            DateTimeGCValue(dateTimeFormat.parseDateTime(sqlType))
          } else {
            NullGCValue
          }
        case TypeIdentifier.Json =>
          val sqlType = resultSet.getString(name)
          if (sqlType != null) {
            JsonGCValue(Json.parse(sqlType))
          } else {
            NullGCValue
          }
        case TypeIdentifier.Relation => sys.error("TypeIdentifier.Relation is not supported here")
      }
      if (resultSet.wasNull) { // todo: should we throw here if the field is required but it is null?
        NullGCValue
      } else {
        gcValue
      }
    }
  }
}
