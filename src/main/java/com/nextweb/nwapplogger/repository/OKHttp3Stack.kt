package com.nextweb.nwapplogger.repository

import com.android.volley.AuthFailureError
import com.android.volley.Request
import com.android.volley.toolbox.HttpStack
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.ProtocolVersion
import org.apache.http.StatusLine
import org.apache.http.entity.BasicHttpEntity
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicHttpResponse
import org.apache.http.message.BasicStatusLine
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * @author nextweb
 *
 */
@Suppress("DEPRECATION")
class OkHttp3Stack : HttpStack {
    @Throws(IOException::class, AuthFailureError::class)
    override fun performRequest(
        request: Request<*>,
        additionalHeaders: Map<String, String>
    ): HttpResponse {
        val clientBuilder = OkHttpClient.Builder()
        val timeoutMs = request.timeoutMs
        clientBuilder.connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        clientBuilder.readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        clientBuilder.writeTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        val okHttpRequestBuilder = okhttp3.Request.Builder()
        okHttpRequestBuilder.url(request.url)
        val headers = request.headers
        for (name in headers.keys) {
            okHttpRequestBuilder.addHeader(name, headers[name])
        }
        for (name in additionalHeaders.keys) {
            okHttpRequestBuilder.addHeader(name, additionalHeaders[name])
        }
        setConnectionParametersForRequest(okHttpRequestBuilder, request)
        val client = clientBuilder.build()
        val okHttpRequest = okHttpRequestBuilder.build()
        val okHttpCall = client.newCall(okHttpRequest)
        val okHttpResponse = okHttpCall.execute()
        val responseStatus: StatusLine = BasicStatusLine(
            parseProtocol(okHttpResponse.protocol()),
            okHttpResponse.code(), okHttpResponse.message()
        )
        val response = BasicHttpResponse(responseStatus)
        response.entity = entityFromOkHttpResponse(okHttpResponse)
        val responseHeaders = okHttpResponse.headers()
        var i = 0
        val len = responseHeaders.size()
        while (i < len) {
            val name = responseHeaders.name(i)
            val value = responseHeaders.value(i)
            if (name != null) {
                response.addHeader(BasicHeader(name, value))
            }
            i++
        }
        return response
    }

    companion object {
        @Throws(IOException::class)
        private fun entityFromOkHttpResponse(r: Response): HttpEntity {
            val entity = BasicHttpEntity()
            val body = r.body()
            entity.content = body!!.byteStream()
            entity.contentLength = body.contentLength()
            entity.setContentEncoding(r.header("Content-Encoding"))
            if (body.contentType() != null) {
                entity.setContentType(body.contentType()!!.type())
            }
            return entity
        }

        @Suppress("deprecation")
        @Throws(IOException::class, AuthFailureError::class)
        private fun setConnectionParametersForRequest(
            builder: okhttp3.Request.Builder,
            request: Request<*>
        ) {
            when (request.method) {
                Request.Method.DEPRECATED_GET_OR_POST -> {
                    // Ensure backwards compatibility.  Volley assumes a request with a null body is a GET.
                    val postBody = request.postBody
                    if (postBody != null) {
                        builder.post(
                            RequestBody
                                .create(MediaType.parse(request.postBodyContentType), postBody)
                        )
                    }
                }

                Request.Method.GET -> builder.get()
                Request.Method.DELETE -> builder.delete()
                Request.Method.POST -> builder.post(createRequestBody(request))
                Request.Method.PUT -> builder.put(createRequestBody(request))
                Request.Method.HEAD -> builder.head()
                Request.Method.OPTIONS -> builder.method("OPTIONS", null)
                Request.Method.TRACE -> builder.method("TRACE", null)
                Request.Method.PATCH -> builder.patch(createRequestBody(request))
                else -> throw IllegalStateException("Unknown method type.")
            }
        }

        private fun parseProtocol(p: Protocol): ProtocolVersion {
            return when (p) {
                Protocol.HTTP_1_0 -> ProtocolVersion("HTTP", 1, 0)
                Protocol.HTTP_1_1 -> ProtocolVersion("HTTP", 1, 1)
                Protocol.SPDY_3 -> ProtocolVersion("SPDY", 3, 1)
                Protocol.HTTP_2 -> ProtocolVersion("HTTP", 2, 0)
            }
            throw IllegalAccessError("Unkwown protocol")
        }

        @Throws(AuthFailureError::class)
        private fun createRequestBody(r: Request<*>): RequestBody? {
            val body = r.body ?: return null
            return RequestBody.create(MediaType.parse(r.bodyContentType), body)
        }
    }
}