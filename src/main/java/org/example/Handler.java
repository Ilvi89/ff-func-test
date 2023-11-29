package org.example;


import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;

class Request {
    List<String> s3s;
    Map<String, String> queryStringParameters;
    ConcurrentSkipListSet<Double> avgTime;


    public Request(List<String> s3s, Map<String, String> query, ConcurrentSkipListSet<Double> avgTime) {
        this.s3s = s3s;
        this.queryStringParameters = query;
        this.avgTime = avgTime;
    }
}


class Res {
    HashMap<String, Set<String>> doss;

    public Res(HashMap<String, Set<String>> doss) {
        this.doss = doss;
    }
}

public class Handler implements Function<Request, Res> {

    @Override
    public Res apply(Request r) {
//        Jdbi jdbi = Jdbi.create("jdbc:postgresql://158.160.111.255:5432/ff-ext-main", "ff-ext-driver", "F!M(Nou39r34]BUYW2");
        List<String> s3s = r.s3s;


        String bucketName = "facebank.store";
        String endpointUrl = "https://hb.bizmrg.com";
        String accessKey = "sZejWfNawx8fB9TExfkGk2";
        String secretKey = "672Fs79oJ2MuLKudfdN5B6nPog13sA6XaXpqKmt9yCm6";
        S3Client s3Client = S3Client.builder()
                .region(Region.EU_CENTRAL_1)
                .endpointOverride(URI.create(endpointUrl))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .overrideConfiguration(b -> b.apiCallTimeout(Duration.ofMinutes(10)).apiCallAttemptTimeout(Duration.ofMinutes(10)))
                .build();
        var n = 0;
        System.out.println(s3s.size());
        var doss = new HashMap<String, Set<String>>();
        for (String s3 : s3s) {
            try {
                ResponseInputStream<GetObjectResponse> getObjectResponse = null;
                try {
                    GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(s3).build();
                    getObjectResponse = s3Client.getObject(getObjectRequest);

                } catch (S3Exception e) {
                    System.out.println("S3_EXCEPTION: " + s3);
                }



                UUID uuid = UUID.randomUUID();
                File newFile = File.createTempFile(uuid.toString(), "." + s3.split("\\.")[1]);


                try (FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = getObjectResponse.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead);
                    }
                }

                var fileSize = Files.size(newFile.toPath());
//                System.out.println("s3 Key " + s3);
                long startTime = System.nanoTime(); // Record start time
                var dossiers = new FindFaceServiceImpl((int) Long.parseLong(r.queryStringParameters.get("name"))).getDossiers(newFile, "Token e6e810685b8e9acc4156591cc7e40029a427c084effc7846535dacec80b9dc5f");
                doss.put(s3, dossiers);
                long endTime = System.nanoTime();
                long elapsedTime = endTime - startTime;

                double elapsedTimeMs = elapsedTime / 1e6 / 1000;
                r.avgTime.add(elapsedTimeMs);
                DecimalFormat df = new DecimalFormat("#.##");
                System.out.println(new Date() + "| [" + (int) Long.parseLong(r.queryStringParameters.get("name")) + "] | Request took: " + df.format(elapsedTimeMs) + " s | " + s3 + " : " + r.avgTime.size() + " | f_size: " + fileSize);


                getObjectResponse.close();
                n += 1;
            } catch (Exception e) {
                System.out.println(s3);
                throw new RuntimeException(e);
            }

        }

        return new Res(doss);
    }
}