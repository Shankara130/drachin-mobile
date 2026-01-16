package com.example.zetayang

import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit // PENTING: Jangan lupa import ini
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
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
            // Setup bypass SSL (Kode lama Anda)
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

            // --- PERBAIKAN DI SINI: PERPANJANG TIMEOUT ---
            builder.connectTimeout(60, TimeUnit.SECONDS) // Waktu tunggu nyambung ke server
            builder.readTimeout(60, TimeUnit.SECONDS)    // Waktu tunggu baca data
            builder.writeTimeout(60, TimeUnit.SECONDS)   // Waktu tunggu kirim data
            // ---------------------------------------------

            // Logging (Opsional, biar kelihatan di Logcat)
            val logging = HttpLoggingInterceptor()
            logging.level = HttpLoggingInterceptor.Level.BODY
            builder.addInterceptor(logging)

            return builder.build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}