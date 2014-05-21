///**
// * Copyright (C) 2013 Typesafe <http://typesafe.com/>
// */
//package console.parser
//
//import java.text.SimpleDateFormat
//import java.util.Calendar
//import java.util.TimeZone
//
//object UtcDateFormat {
//  val UTC = TimeZone.getTimeZone("UTC")
//}
//
///**
// * This class is not thread safe, use [[actors.parser.ThreadUtcDateFormat]]
// * for concurrent access.
// */
//class UtcDateFormat(pattern: String) extends SimpleDateFormat(pattern) {
//
//  // set the calendar to use UTC
//  calendar = Calendar.getInstance(UtcDateFormat.UTC)
//  calendar.setLenient(false)
//}
//
///**
// * ThreadLocal holder of a [[actors.parser.UtcDateFormat]].
// */
//object ThreadUtcDateFormat {
//  def apply(pattern: String): ThreadLocal[UtcDateFormat] = new ThreadLocal[UtcDateFormat]() {
//    override protected def initialValue(): UtcDateFormat = new UtcDateFormat(pattern)
//  }
//}
