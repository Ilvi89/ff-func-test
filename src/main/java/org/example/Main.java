package org.example;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        var workers = 5;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        var avg = new ConcurrentSkipListSet<Double>();

        var s3s = new ArrayList<String>();
        String fileName = "dataset10.csv"; // TODO: список s3 ключей
        FileResourcesUtils resourcesUtils = new FileResourcesUtils();
        InputStream is = resourcesUtils.getFileFromResourceAsStream(fileName);
        try (InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(streamReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                s3s.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        var s3sSize = s3s.size();
        for (int i = 0; i < workers; i++) {
            int finalI = i;
            executor.execute(() -> {
                var q = new HashMap<String, String>();
                q.put("id", String.valueOf(finalI));
                q.put("token", "aaf09a5a9f5de73405119881bd11896b877be57ecf24cb29d5db6beac0c2673d");
                var listSize = s3sSize / workers;
                var res = new Handler().apply(new Request(s3s.subList(listSize * finalI, listSize * (finalI + 1)), q, avg));
                System.out.println(res.doss);
            });
        }
        executor.shutdown();
    }
}
