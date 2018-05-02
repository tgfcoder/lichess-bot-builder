package lichess.client;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Provides a REST client interface to a Lichess endpoint.
 */
public class LichessRestClient {
    private static final String ENDPOINT = "https://lichess.org";
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final URL server;
    private final String apiToken;
    
    public LichessRestClient(String apiToken) throws IOException {
        this.server = new URL(ENDPOINT);
        this.apiToken = apiToken;
    }
    
    /**
     * Performs a GET request on the given path and maps the response to the given class type.
     */
    public <T> T get(String path, Class<T> type) throws IOException {
        try (InputStream in = get(path)) {
            return MAPPER.readValue(in, type);
        }
    }

    /**
     * Performs a POST request on the given path.
     */
    public void post(String path) throws IOException, LichessServiceException {
        post(path, "");
    }

    /**
     * Performs a POST request on the given path with additional form data.
     */
    public void post(String path, String formData) throws IOException, LichessServiceException {
        byte[] postData = formData.getBytes(StandardCharsets.UTF_8);
        int postDataLength = postData.length;

        HttpURLConnection urlConnection = openConnection(path);
        urlConnection.setRequestMethod("POST");
        if (postDataLength > 0) {
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (DataOutputStream dos = new DataOutputStream(urlConnection.getOutputStream())) {
                dos.write(postData);
            }
        }
        
        try (InputStream in = urlConnection.getInputStream()) {
            OkErrorResponse response = MAPPER.readValue(in, OkErrorResponse.class);
            if (!response.ok) {
                throw new LichessServiceException(response.error);
            }
        }
    }
    
    /**
     * Performs a streaming GET request on the given path. Response objects are mapped to the given class type
     * and passed to the given value processor for further processing.
     * <p>
     * This method does not return until the stream has been completely processed.
     */
    public <T> void stream(String path, Class<T> valueType, ValueProcessor<T> valueProcessor) throws IOException {
        try (InputStream in = get(path)) {
            stream(in, valueType, valueProcessor);
        }
    }
    
    public interface ValueProcessor<T> {
        void process(T value) throws IOException;
    }
    
    private InputStream get(String path) throws IOException {
        HttpURLConnection urlConnection = openConnection(path);
        return urlConnection.getInputStream();
    }

    private <T> void stream(InputStream in, Class<T> valueType, ValueProcessor<T> valueProcessor) throws IOException {
        JsonParser parser = new JsonFactory().createParser(in);
        JsonToken token = parser.nextToken();

        // Try find at least one object or array.
        while (!JsonToken.START_ARRAY.equals(token) && token != null && !JsonToken.START_OBJECT.equals(token)) {
            parser.nextToken();
        }

        // No content found
        if (token == null) {
            return;
        }

        boolean scanMore = false;

        while (true) {
            // If the first token is the start of object ->
            // the response contains only one object (no array)
            // do not try to get the first object from array.
            try {
                if (!JsonToken.START_OBJECT.equals(token) || scanMore) {
                    token = parser.nextToken();
                }
                if (!JsonToken.START_OBJECT.equals(token)) {
                    break;
                }
                if (token == null) {
                    break;
                }

                T value = MAPPER.readValue(parser, valueType);
                valueProcessor.process(value);

                scanMore = true;
            } catch (JsonParseException e) {
                throw new IllegalStateException("Could not parse JSON", e);
            }
        }
    }

    private HttpURLConnection openConnection(String path) throws IOException {
        URL url = new URL(server, path);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.addRequestProperty("Authorization", "Bearer " + apiToken);
        return urlConnection;
    }

    class OkErrorResponse {
        public boolean ok;
        public String error;
    }
}
