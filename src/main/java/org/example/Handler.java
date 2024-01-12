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
    HashMap<String, Map<String, Set<String>>> doss;

    public Res(HashMap<String, Map<String, Set<String>>> doss) {
        this.doss = doss;
    }
}

public class Handler implements Function<Request, Res> {

    @Override
    public Res apply(Request r) {
        List<String> s3s = r.s3s;


        String bucketName = "cms-master-storage";
        String endpointUrl = "https://storage.yandexcloud.net";
        String accessKey = "YCAJE607k8V_FVNh9AbeR30ao";
        String secretKey = "YCNPDtmrhOghnyzU9ubLIJW4aPcZkE34zaVqTiRv";
        S3Client s3Client = S3Client.builder()
                .region(Region.EU_CENTRAL_1)
                .endpointOverride(URI.create(endpointUrl))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .overrideConfiguration(b -> b.apiCallTimeout(Duration.ofMinutes(10)).apiCallAttemptTimeout(Duration.ofMinutes(10)))
                .build();


        var doss = new HashMap<String, Map<String, Set<String>>>();
        for (String s3 : s3s) {
            s3 = s3 + "_resized_1200.jpg";
            var doLoop = true;
            while (doLoop){
                try {
                    ResponseInputStream<GetObjectResponse> getObjectResponse = null;
                    try {
                        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(s3).build();
                        getObjectResponse = s3Client.getObject(getObjectRequest);
                    } catch (S3Exception e) {
                        System.out.println("S3_EXCEPTION: " + s3);
                        throw e;
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
                    long startTime = System.nanoTime();

                    var botId = Integer.parseInt(r.queryStringParameters.get("id"));
                    var authToken = r.queryStringParameters.get("token");
                    var dossiers = new FindFaceServiceImpl(botId)
                            .getDossiers(newFile, "Token " + authToken);
                    doss.put(s3, dossiers);
                    doLoop = false;
                    long endTime = System.nanoTime();
                    long elapsedTime = endTime - startTime;
                    double elapsedTimeMs = elapsedTime / 1e6 / 1000;
                    r.avgTime.add(elapsedTimeMs);
                    DecimalFormat df = new DecimalFormat("#.##");
                    System.out.println(new Date() + "| [" + (int) Long.parseLong(r.queryStringParameters.get("id")) + "] | Request took: " + df.format(elapsedTimeMs) + " s | " + s3 + " : " + r.avgTime.size() + " | f_size: " + fileSize);
                    getObjectResponse.close();
                } catch (TracebackE e) {
                    doLoop = true;
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                } catch (Exception e) {
                    System.out.println(s3);
                    throw new RuntimeException(e);
                }
            }


        }

        return new Res(doss);
    }
}