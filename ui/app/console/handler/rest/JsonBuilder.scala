//package console.handler.rest
//
//import play.api.libs.json.{ Json, JsObject, JsValue, JsArray, Writes, JsString }
//
//object JsonBuilder {
//  def optJsonObj[T](k: String, v: Option[T], writer: T => JsValue): JsObject =
//    v.map(iv => Json.obj(k -> writer(iv))).getOrElse(Json.obj())
//
//  def optJson[T](k: String, v: Option[T])(implicit arg0: Writes[T]): JsObject =
//    v.map(iv => Json.obj(k -> iv)).getOrElse(Json.obj())
//}
