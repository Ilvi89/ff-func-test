package org.example;


import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.Request;
import okhttp3.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class FindFaceServiceImpl implements FindFaceService {
    private final OkHttpClient client;
    private final String domain = "http://95.167.225.149";
    private final String threshold = "0.723";
    private final String detectionQuality = String.valueOf(0.4);
    private final String croppedDetectionQuality = String.valueOf(0.4);
    private final String watchList = String.valueOf(4); // TODO: список досье
    private final int id;


    public FindFaceServiceImpl(int id) {
        this.id = id;
        this.client = new OkHttpClient().newBuilder()
                .readTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .callTimeout(10, TimeUnit.MINUTES)
                .connectTimeout(10, TimeUnit.MINUTES)
                .addInterceptor(new RequestTimeInterceptor(id))
                .build();
    }

    private Optional<JsonObject> sendLooksLikeRequest(String detectionId, String token) throws IOException {
        // формируем запрос
        okhttp3.Request request = new okhttp3.Request.Builder().url(domain + "/cards/humans/?looks_like=detection%3A" + detectionId + "&threshold=" + this.threshold + "&limit=100&ordering=-id&watch_lists=" + this.watchList).method("GET", null).addHeader("Authorization", token).build();

        Gson gson = new Gson();
        var response = client.newCall(request).execute();

        ResponseBody body = response.body();

        // отправляем запрос
        String res = Objects.requireNonNull(body).string();
        JsonObject jsonObject = gson.fromJson(res, JsonObject.class);
        // проверки: не пришел ли пустой JSON объект или пустой список значений из results
        if (jsonObject == null) return Optional.empty();
        if (jsonObject.get("results") == null || jsonObject.get("results").getAsJsonArray().size() <= 0) {
            return Optional.empty();
        }
        body.close();
        response.close();
        return Optional.of(jsonObject);
    }

    private Optional<JsonArray> sendDetectRequest(File targetFile, String token) throws TracebackE, IOException {
        // формируем запрос для обнаружения лица
        MediaType MEDIA_TYPE = MediaType.parse("image/jpeg");
        RequestBody formBody = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("photo", targetFile.getName(), RequestBody.create(targetFile, MEDIA_TYPE)).addFormDataPart("attributes", "{\"face\":{}}").build();

        okhttp3.Request request = new okhttp3.Request.Builder().url(domain + "/detect").method("POST", formBody).addHeader("Authorization", token).build();

        // отправили запрос
        Response res3 = client.newCall(request).execute();
        Gson gson = new Gson();
        assert res3.body() != null;
        JsonObject json = gson.fromJson(res3.body().string(), JsonObject.class);
        if (json.get("traceback") != null) {
            throw new TracebackE();
        }
        // проверяем не пришел ли пустой JSON объект
        if (json == null || json.get("objects") == null) {
            System.out.println(json);
            return Optional.empty();
        }

        // проверяем не пустой ли списко объектов наименования object
        if (json.get("objects").getAsJsonObject().size() <= 0) {
            System.out.println(json);
            return Optional.empty();
        }

        // проверяем не пришли ли нуловые лица
        if (json.get("objects").getAsJsonObject().get("face") == null) {
            System.out.println(json);
            return Optional.empty();
        }

        // если все проверки прошли, то формуем ответ для возврата значений
        JsonArray res = json.get("objects").getAsJsonObject().get("face").getAsJsonArray();

        res3.close();
        return Optional.ofNullable(res);
    }



    public Map<String, Set<String>> getDossiers(File targetFile, String token) throws IOException, TracebackE {
        // отправляем запрос на обнаружение лиц
        Optional<JsonArray> optionalJsonObject = sendDetectRequest(targetFile, token);
        if (optionalJsonObject.isEmpty()) return Collections.emptyMap();
        var detections = optionalJsonObject.get();
        Map<String, Set<String>> dossiers = new HashMap<>();

        // проходимся по списку detection_id
        for (JsonElement detection : detections) {
            // отсеиваем ненужный detection_score
            double detectionScore = detection.getAsJsonObject().get("detection_score").getAsDouble();
            if (detectionScore < Double.parseDouble(this.detectionQuality)) {
                continue;
            }

            // отправляем запрос на looks_like для получения существующих досье
            String detectionId = detection.getAsJsonObject().get("id").getAsString();
            Optional<JsonObject> optionalLooksLikeResponse = sendLooksLikeRequest(detectionId, token);

            // в случае есть досье присуствует, до добаляем в итоговый список
            if (optionalLooksLikeResponse.isPresent()) {
                var doss = this.handleLooksLikeResponse(optionalLooksLikeResponse.get());
                dossiers.put(detectionScore + "", new HashSet<>(doss));
            } else {
                // Если досье еще нет то создаем новое

                // Вырезаем лицо
                BufferedImage croppedImage;
                long startTime = System.nanoTime();
                try {
                    int orientation = ImageUtils.getOrientation(targetFile);
                    BufferedImage originalImage = ImageIO.read(targetFile);
                    BufferedImage fixedImage = ImageUtils.rotateImage(originalImage, orientation);
                    croppedImage = this.cutImage(fixedImage, detection);
                } catch (Exception e) {
                    continue;
                }
                File tFile = File.createTempFile("getDossier_cropped-" + UUID.randomUUID(), ".jpg");
                try {
                    ImageIO.write(croppedImage, "jpg", tFile);
                } catch (IOException e) {
                    DeleteFileUtil.deleteFile(tFile);
                }
                long endTime = System.nanoTime();
                long elapsedTime = endTime - startTime;
                double elapsedTimeMs = elapsedTime / 1e6 / 1000;
                DecimalFormat df = new DecimalFormat("#.##");
                System.out.println(new Date() + " | [" + this.id + "] |  Cut image took: " + df.format(elapsedTimeMs) + " s");


                // Отправляем полученное фото на повторный детект
                Optional<JsonArray> optionalJsonElements = sendDetectRequest(tFile, token);
                if (optionalJsonElements.isEmpty()) {
                    DeleteFileUtil.deleteFile(tFile);
                    continue;
                }

                for (JsonElement croppedImageDetection : optionalJsonElements.get()) {
                    // отсеиваем ненужный detection_score
                    double croppedDetectionScore = croppedImageDetection.getAsJsonObject().get("detection_score").getAsDouble();
                    if (croppedDetectionScore < Double.parseDouble(this.croppedDetectionQuality)) {
                        continue;
                    }

                    // Создаем новое досье
                    String croppedDetectionId = croppedImageDetection.getAsJsonObject().get("id").getAsString();
                    Integer cardId = this.sendPostCardsHumansRequest(token);
                    String newDossier = this.sendPostObjectsFacesRequest(tFile, croppedDetectionId, cardId, token);
                    dossiers.put(detectionScore+"", Set.of(newDossier));
                }

                DeleteFileUtil.deleteFile(tFile);
            }

        }
        // возвращаем список всех досьешек на фотографии
        return dossiers;
    }

    private String sendPostObjectsFacesRequest(File tFile, String detectionId, Integer cardId, String token) throws IOException {
        var fileName = UUID.randomUUID() + ".jpg";
        MediaType MEDIA_TYPE = MediaType.parse("image/jpeg");
        RequestBody formBody2 = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("source_photo", fileName, RequestBody.create(tFile, MEDIA_TYPE))
                .addFormDataPart("create_from", "detection:" + detectionId)
                .addFormDataPart("card", String.valueOf(cardId)).build();
        okhttp3.Request addPhotoToCard = new okhttp3.Request.Builder().url(domain + "/objects/faces/").method("POST", formBody2).addHeader("Authorization", token).build();
        Gson gson = new Gson();

        var response = client.newCall(addPhotoToCard).execute();
        var responseBody = response.body();

        JsonObject created = gson.fromJson(responseBody.string(), JsonObject.class);
        response.close();
        return created.get("card").getAsString();
    }

    private int sendPostCardsHumansRequest(String token) throws IOException {
        // формируем запрос
        String uniqueName = UUID.randomUUID().toString();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create("{\"active\":true,\"name\":\"" + uniqueName + "\",\"comment\":\"Automatically created user\",\"watch_lists\":[" + this.watchList + "],\"meta\":{}}", mediaType);
        okhttp3.Request createUser = new Request.Builder()
                .url(domain + "/cards/humans/")
                .method("POST", body)
                .addHeader("Authorization", token)
                .addHeader("Content-Type", "application/json")
                .build();

        Response createUserResponse = client.newCall(createUser).execute();
        ResponseBody createUserResponseBody = createUserResponse.body();

        Gson gson = new Gson();
        assert createUserResponseBody != null;
        JsonObject createdUserJson = gson.fromJson(createUserResponseBody.string(), JsonObject.class);
        createUserResponse.close();
        return createdUserJson.get("id").getAsInt();
    }

    private List<String> handleLooksLikeResponse(JsonObject jsonObject) {
        var dossiers = new ArrayList<String>();
        var response = jsonObject.get("results").getAsJsonArray();
        for (JsonElement res : response) {
            JsonArray watches = res.getAsJsonObject().get("watch_lists").getAsJsonArray();
            if (watches.size() > 0) {
                for (JsonElement w : watches) {
                    if (w.getAsInt() == Integer.parseInt(watchList)) {
                        dossiers.add(res.getAsJsonObject().get("id").getAsString());
                    }
                }
            }
        }
        return dossiers;
    }

    public BufferedImage cutImage(BufferedImage fixedImage, JsonElement detection) throws IOException {
        int left = detection.getAsJsonObject().get("bbox").getAsJsonObject().get("left").getAsInt();
        int top = detection.getAsJsonObject().get("bbox").getAsJsonObject().get("top").getAsInt();
        int right = detection.getAsJsonObject().get("bbox").getAsJsonObject().get("right").getAsInt();
        int bottom = detection.getAsJsonObject().get("bbox").getAsJsonObject().get("bottom").getAsInt();
        int cropWidth = right - left;
        int cropHeight = bottom - top;
        int paddingX = (int) (cropWidth * 0.5);
        int paddingY = (int) (cropHeight * 0.5);
        var cX = Math.max((left - paddingX / 2), 0);
        var cY = Math.max((top - paddingY / 2), 0);
        return ImageCutterUtil.execute(fixedImage, cX, cY, cropWidth + paddingX, cropHeight + paddingY);
    }
}









