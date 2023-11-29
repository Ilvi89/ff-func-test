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


public class FindFaceServiceImpl implements FindFaceService {
    private final OkHttpClient client;
    private final String domain = "http://89.208.198.229";
    private String threshold = "0.723";
    private String detectionQuality = String.valueOf(0.4);
    private String croppedDetectionQuality = String.valueOf(0.4);
    private String watchList = String.valueOf(19);
    private int id;


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

    public Optional<JsonObject> sendLooksLikeRequest(String detectionId, String token) throws IOException {
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

    public Optional<JsonArray> sendDetectRequest(File targetFile, String token) throws IOException {
        // формируем запрос для обнаружения лица
        MediaType MEDIA_TYPE = MediaType.parse("image/jpeg");
        RequestBody formBody = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("photo", targetFile.getName(), RequestBody.create(targetFile, MEDIA_TYPE)).addFormDataPart("attributes", "{\"face\":{}}").build();

        okhttp3.Request request = new okhttp3.Request.Builder().url(domain + "/detect").method("POST", formBody).addHeader("Authorization", token).build();

        // отправили запрос
        Response res3 = client.newCall(request).execute();
        Gson gson = new Gson();
        assert res3.body() != null;
        JsonObject json = gson.fromJson(res3.body().string(), JsonObject.class);

        // проверяем не пришел ли пустой JSON объект
        if (json == null || json.get("objects") == null) {
            return Optional.empty();
        }

        // проверяем не пустой ли списко объектов наименования object
        if (json.get("objects").getAsJsonObject().size() <= 0) {
            return Optional.empty();
        }

        // проверяем не пришли ли нуловые лица
        if (json.get("objects").getAsJsonObject().get("face") == null) {
            return Optional.empty();
        }

        // если все проверки прошли, то формуем ответ для возврата значений
        JsonArray res = json.get("objects").getAsJsonObject().get("face").getAsJsonArray();

        res3.close();
        return Optional.ofNullable(res);
    }

    public Optional<JsonArray> sendDetectRequest2(File targetFile, String token) throws IOException {
        // формируем запрос для обнаружения лица
        MediaType MEDIA_TYPE = MediaType.parse("image/jpeg");
        RequestBody formBody = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("photo", targetFile.getName(), RequestBody.create(targetFile, MEDIA_TYPE)).addFormDataPart("attributes", "{\"face\":{}}").build();

        okhttp3.Request request = new okhttp3.Request.Builder().url(domain + "/detect").method("POST", formBody).addHeader("Authorization", token).build();

        // отправили запрос
        Response res3 = client.newCall(request).execute();
        Gson gson = new Gson();
        assert res3.body() != null;
        JsonObject json = gson.fromJson(res3.body().string(), JsonObject.class);

        // проверяем не пришел ли пустой JSON объект
        if (json == null || json.get("objects") == null) {
            return Optional.empty();
        }

        // проверяем не пустой ли списко объектов наименования object
        if (json.get("objects").getAsJsonObject().size() <= 0) {
            return Optional.empty();
        }

        // проверяем не пришли ли нуловые лица
        if (json.get("objects").getAsJsonObject().get("face") == null) {
            return Optional.empty();
        }

        // если все проверки прошли, то формуем ответ для возврата значений
        JsonArray res = json.get("objects").getAsJsonObject().get("face").getAsJsonArray();

        res3.close();
        return Optional.ofNullable(res);
    }


    public Set<String> getDossiers(File targetFile, String token) throws IOException {
        // здесь мы сетим в переменные класса значения пришедшие из БД
        // отправляем запрос на обнаружения лица
        Optional<JsonArray> optionalJsonObject = sendDetectRequest(targetFile, token);
        if (optionalJsonObject.isEmpty()) return Collections.emptySet();
        var detections = optionalJsonObject.get();

        Set<String> dossiers = new HashSet<>();

        // проходимся по списку detection_id
        for (JsonElement detection : detections) {
            // достаем из пришедшего detection_id значение detection_score
            double detectionScore = detection.getAsJsonObject().get("detection_score").getAsDouble();

            // сравниваем detection_score со значеним detectionQuality из БД
            // в случае если значение detectionQuality из бд меньше пришедшего detection_score, то пропускаем дальнешие действия
            if (detectionScore < Double.parseDouble(this.detectionQuality)) {
                continue;
            }



            // оптравляем повторный запрос на обнаружение лица
//            Optional<JsonArray> optionalJsonElements = sendDetectRequest2(tFile, token);

//            if (optionalJsonElements.isEmpty()) {
//                DeleteFileUtil.deleteFile(tFile);
//                continue;
//            }

            // проходимся по списку detection_id из повторного запроса на обнаружение лица
//            for (JsonElement croppedImageDetection : optionalJsonElements.get()) {
                // достаем из пришедшего detection_id значение detection_score
//                double croppedDetectionScore = croppedImageDetection.getAsJsonObject().get("detection_score").getAsDouble();
                // сравниваем detection_score со значеним detectionQuality из БД
                // в случае если значение detectionQuality из бд меньше пришедшего detection_score, то пропускаем дальнешие действия
//                if (croppedDetectionScore < Double.parseDouble(this.croppedDetectionQuality)) {
//                    continue;
//                }

                // достаем значение id для оптравки запроса на looks_like
//            String detectionId = croppedImageDetection.getAsJsonObject().get("id").getAsString();
            String detectionId = detection.getAsJsonObject().get("id").getAsString();

                // отправляем запрос на looks_like
                Optional<JsonObject> optionalLooksLikeResponse = sendLooksLikeRequest(detectionId, token);

                // в случае есть досье присуствует, до добаляем в список
                if (optionalLooksLikeResponse.isPresent()) {
                    var doss = this.handleLooksLikeResponse(optionalLooksLikeResponse.get());
                    // иногда на 1 лицо может прийти несколько досье. Предупреждаем об этом
                    dossiers.addAll(doss);
                } else {
                    // получаем ориентацию из пришедшего файла
                    BufferedImage croppedImage;
                    long startTime = System.nanoTime();
                    try {
                        // нарезаем фотографию
                        int orientation = ImageUtils.getOrientation(targetFile);
                        BufferedImage originalImage = ImageIO.read(targetFile);
                        BufferedImage fixedImage = ImageUtils.rotateImage(originalImage, orientation);
                        croppedImage = this.cutImage(fixedImage, detection);
                    } catch (Exception e) {
                        continue;
                    }

                    // создаем временный файл для нарезки
                    File tFile = File.createTempFile("getDossier_cropped-" + UUID.randomUUID(), ".jpg");
                    try {
                        // записываем нарезанную фотку во временный файл
                        ImageIO.write(croppedImage, "jpg", tFile);
                    } catch (IOException e) {
                        DeleteFileUtil.deleteFile(tFile);
                    }

                    long endTime = System.nanoTime();
                    long elapsedTime = endTime - startTime;
                    double elapsedTimeMs = elapsedTime / 1e6 / 1000;
                    DecimalFormat df = new DecimalFormat("#.##");
                    System.out.println(new Date()  + " | [" + this.id + "] |  Cut image took: " + df.format(elapsedTimeMs) + " s");


                    Optional<JsonArray> optionalJsonElements = sendDetectRequest2(tFile, token);
                    if (optionalJsonElements.isEmpty()) {
                        DeleteFileUtil.deleteFile(tFile);
                        continue;
                    }

                    for (JsonElement croppedImageDetection : optionalJsonElements.get()) {
                        // достаем из пришедшего detection_id значение detection_score
                        double croppedDetectionScore = croppedImageDetection.getAsJsonObject().get("detection_score").getAsDouble();
                        // сравниваем detection_score со значеним detectionQuality из БД
                        // в случае если значение detectionQuality из бд меньше пришедшего detection_score, то пропускаем дальнешие действия
                        if (croppedDetectionScore < Double.parseDouble(this.croppedDetectionQuality)) {
                            continue;
                        }
                        String croppedDetectionId = croppedImageDetection.getAsJsonObject().get("id").getAsString();

                        // есть досьешки нет, то отправляем запрос на создание новой
                        Integer cardId = this.sendPostCardsHumansRequest(token);
                        String newDossier = this.sendPostObjectsFacesRequest(tFile, croppedDetectionId, cardId, token);
                        dossiers.add(newDossier);
                    }

                    DeleteFileUtil.deleteFile(tFile);
                }
//            }
            // удаляем временный файл

        }
        // возвращаем список всех досьешек на фотографии
        return dossiers;
    }

    public String sendPostObjectsFacesRequest(File tFile, String detectionId, Integer cardId, String token) throws IOException {
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

    public int sendPostCardsHumansRequest(String token) throws IOException {
        // формируем запрос
        String uniqueName = UUID.randomUUID().toString();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create("{\"active\":true,\"name\":\"" + uniqueName + "\",\"comment\":\"Automatically created user\",\"watch_lists\":[" + this.watchList + "],\"meta\":{}}", mediaType);
        okhttp3.Request createUser = new Request.Builder().url(domain + "/cards/humans/").method("POST", body).addHeader("Authorization", token).addHeader("Content-Type", "application/json").build();


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









