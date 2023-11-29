package org.example;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Date;


public class RequestTimeInterceptor implements Interceptor {
    private int id;

    public RequestTimeInterceptor(int id) {
        this.id = id;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        long startTime = System.nanoTime(); // Record start time

        Request request = chain.request();
        Response response = chain.proceed(request);

        long endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;

        double elapsedTimeMs = elapsedTime / 1e6 / 1000;
//        System.out.println(new Date() +" | Request took: " + df.format(elapsedTimeMs) + " s | " + s3 + " : " + r.avgTime.size()
//        );
        System.out.println(new Date()  + " | [" + this.id + "] |  Request " + request.url() + " took: " + elapsedTimeMs + " s");

        return response;
    }
}
