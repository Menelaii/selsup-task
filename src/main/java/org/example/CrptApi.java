package org.example;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CrptApi implements AutoCloseable {

    private final CloseableHttpClient httpClient;
    private final Gson gson;
    private final PermitService permitService;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClients.createDefault();
        this.gson = new Gson();
        this.permitService = new PermitService(timeUnit, requestLimit);
    }

    public void sendCreateRequest(Document document, String signature) throws InterruptedException {
        permitService.acquire();

        String url = Environment.CREATE_DOCUMENT_URL;
        String body = gson.toJson(document);
        HttpPost request = new HttpPost(url);

        request.setEntity(EntityBuilder.create()
                .setText(body)
                .setContentType(ContentType.APPLICATION_JSON)
                .build()
        );

        request.addHeader(Environment.AUTH_HEADER, signature);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            log.info("Server responded with status {}", statusCode);
        } catch (IOException e) {
            log.error(e.getMessage());
        } finally {
            permitService.release();
        }
    }

    @Override
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }

        if (permitService != null) {
            permitService.close();
        }
    }

    public class PermitService implements AutoCloseable {
        private final Semaphore semaphore;
        private final int requestLimit;
        private final ScheduledExecutorService scheduler;

        public PermitService(TimeUnit timeUnit, int requestLimit) {
            this.requestLimit = requestLimit;
            this.semaphore = new Semaphore(requestLimit, true);
            this.scheduler = Executors.newScheduledThreadPool(1);

            scheduler.scheduleAtFixedRate(
                    this::resetPermits,
                    0,
                    1,
                    timeUnit
            );
        }

        public synchronized void acquire() throws InterruptedException {
            semaphore.acquire();
        }

        public synchronized void release() {
            if (semaphore.availablePermits() < requestLimit) {
                semaphore.release();
            }
        }

        private synchronized void resetPermits() {
            semaphore.drainPermits();
            int requestLimit = semaphore.availablePermits();
            for (int i = 0; i < requestLimit; i++) {
                semaphore.release();
            }
        }

        @Override
        public void close() {
            this.scheduler.shutdown();
        }
    }

    public static class Environment {
        public static final String CREATE_DOCUMENT_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
        public static final String AUTH_HEADER = "Signature";
    }

    public class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        @Override
        public JsonElement serialize(LocalDate src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(formatter.format(src));
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return LocalDate.parse(json.getAsString(), formatter);
        }
    }

    @Data
    @NoArgsConstructor
    public class Document {
        @SerializedName("description")
        private Description description;

        @SerializedName("doc_id")
        private String docId;

        @SerializedName("doc_status")
        private String docStatus;

        @SerializedName("doc_type")
        private String docType;

        @SerializedName("importRequest")
        private Boolean importRequest;

        @SerializedName("owner_inn")
        private String ownerInn;

        @SerializedName("participant_inn")
        private String participantInn;

        @SerializedName("producer_inn")
        private String producerInn;

        @SerializedName("production_date")
        @JsonAdapter(LocalDateAdapter.class)
        private LocalDate productionDate;

        @SerializedName("production_type")
        private String productionType;

        @SerializedName("products")
        private List<Product> products;

        @SerializedName("reg_date")
        @JsonAdapter(LocalDateAdapter.class)
        private LocalDate regDate;

        @SerializedName("reg_number")
        private String regNumber;
    }

    @Data
    @NoArgsConstructor
    public class Description {

        @SerializedName("participant_inn")
        private String participantInn;
    }

    @Data
    @NoArgsConstructor
    public class Product {
        @SerializedName("certificate_document")
        private String certificateDocument;

        @SerializedName("certificate_document_date")
        @JsonAdapter(LocalDateAdapter.class)
        private LocalDate certificateDocumentDate;

        @SerializedName("certificate_document_number")
        private String certificateDocumentNumber;

        @SerializedName("owner_inn")
        private String ownerInn;

        @SerializedName("producer_inn")
        private String producerInn;

        @SerializedName("production_date")
        @JsonAdapter(LocalDateAdapter.class)
        private LocalDate productionDate;

        @SerializedName("tnved_code")
        private String tnvedCode;

        @SerializedName("uit_code")
        private String uitCode;

        @SerializedName("uitu_code")
        private String uituCode;
    }
}
