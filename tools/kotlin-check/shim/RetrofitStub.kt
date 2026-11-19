// Annotation-only stand-ins for retrofit2.http, so request/response classes declared next to a Retrofit interface compile in the
// no-Gradle test runner (JSON contract suite). Never used at runtime: the app uses the real Retrofit.
package retrofit2.http

annotation class GET(val value: String = "")
annotation class POST(val value: String = "")
annotation class PUT(val value: String = "")
annotation class PATCH(val value: String = "")
annotation class DELETE(val value: String = "")
annotation class HTTP(val method: String, val path: String = "", val hasBody: Boolean = false)
annotation class Body
annotation class Path(val value: String)
annotation class Query(val value: String)
