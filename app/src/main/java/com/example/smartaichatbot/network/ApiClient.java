package com.example.smartaichatbot.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {

    /**
     * 10.0.2.2 is the standard Android emulator's alias for the development computer's localhost.
     * A physical phone needs your PC's LAN address instead, e.g. "http://192.168.1.25:3000/"
     * (also add that IP to res/xml/network_security_config.xml). Production must use HTTPS.
     */
    public static final String BASE_URL = "http://10.0.2.2:3000/";

    private static volatile ChatApi api;

    private ApiClient() {
    }

    public static ChatApi getApi() {
        if (api == null) {
            synchronized (ApiClient.class) {
                if (api == null) {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();

                    api = retrofit.create(ChatApi.class);
                }
            }
        }
        return api;
    }
}
