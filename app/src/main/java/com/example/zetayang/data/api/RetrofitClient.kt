package com.example.zetayang.data.api

import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://dramabox.sansekai.my.id/api/"

    val instance: DramaApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(getUnsafeOkHttpClient()) 
            .build()
        retrofit.create(DramaApiService::class.java)
    }

    fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            // Setup bypass SSL
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            val builder = OkHttpClient.Builder()
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }

            // ===== OPTIMASI TIMEOUT =====
            // Timeout untuk koneksi awal
            builder.connectTimeout(30, TimeUnit.SECONDS)
            // Timeout untuk membaca data (penting untuk video)
            builder.readTimeout(90, TimeUnit.SECONDS)
            // Timeout untuk menulis data
            builder.writeTimeout(30, TimeUnit.SECONDS)
            // Timeout untuk keseluruhan call
            builder.callTimeout(120, TimeUnit.SECONDS)

            // ===== CONNECTION POOLING =====
            // Reuse koneksi untuk performa lebih baik
            builder.connectionPool(
                ConnectionPool(
                    maxIdleConnections = 5,      // Jumlah koneksi idle yang disimpan
                    keepAliveDuration = 5,       // Berapa lama koneksi disimpan
                    timeUnit = TimeUnit.MINUTES
                )
            )

            // ===== RETRY ON CONNECTION FAILURE =====
            builder.retryOnConnectionFailure(true)

            // ===== CACHE (Optional - untuk API response) =====
            // Uncomment jika ingin cache API response (bukan untuk video streaming)
            // val cacheSize = (10 * 1024 * 1024).toLong() // 10 MB
            // val cache = Cache(context.cacheDir, cacheSize)
            // builder.cache(cache)

            // ===== LOGGING =====
            val logging = HttpLoggingInterceptor()
            // Deteksi debug mode tanpa BuildConfig
            val isDebuggable = try {
                android.os.Build.TYPE.contains("eng") || 
                android.os.Build.TYPE.contains("userdebug")
            } catch (e: Exception) {
                false
            }
            logging.level = if (isDebuggable) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)

            // ===== CUSTOM INTERCEPTOR untuk monitoring =====
            builder.addInterceptor { chain ->
                val request = chain.request()
                val startTime = System.currentTimeMillis()
                
                try {
                    val response = chain.proceed(request)
                    val endTime = System.currentTimeMillis()
                    
                    android.util.Log.d(
                        "NetworkSpeed",
                        "URL: ${request.url} | Time: ${endTime - startTime}ms | Status: ${response.code}"
                    )
                    
                    response
                } catch (e: Exception) {
                    val endTime = System.currentTimeMillis()
                    android.util.Log.e(
                        "NetworkError",
                        "URL: ${request.url} | Time: ${endTime - startTime}ms | Error: ${e.message}"
                    )
                    throw e
                }
            }

            return builder.build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}