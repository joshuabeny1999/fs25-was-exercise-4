package solid;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.OpFeedbackParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A CArtAgO artifact that agent can use to interact with LDP containers in a Solid pod.
 */
public class Pod extends Artifact {

    private String podURL; // the location of the Solid pod 

  /**
   * Method called by CArtAgO to initialize the artifact. 
   *
   * @param podURL The location of a Solid pod
   */
    public void init(String podURL) {
        this.podURL = podURL;
        log("Pod artifact initialized for: " + this.podURL);
    }

  /**
   * CArtAgO operation for creating a Linked Data Platform container in the Solid pod
   *
   * @param containerName The name of the container to be created
   * 
   */
    @OPERATION
    public void createContainer(String containerName) {
        String containerURL = buildResourceURL(containerName, true);

        try {
            int responseCode = sendHttpRequest(containerURL, "GET", "text/turtle", null);
            if (responseCode == 200) {
                log("Container already exists: " + containerURL);
                return;
            }
        } catch (IOException e) {
            log("Container not found, proceeding with creation: " + containerURL);
        }

        try {
            int responseCode = sendHttpRequest(containerURL, "PUT", "text/turtle", "");
            if (responseCode == 201 || responseCode == 200 || responseCode == 204) {
                log("Container created successfully: " + containerURL);
            } else {
                log("Failed to create container. Response code: " + responseCode);
            }
        } catch (IOException e) {
            log("Error while creating container: " + e.getMessage());
        }
    }

  /**
   * CArtAgO operation for publishing data within a .txt file in a Linked Data Platform container of the Solid pod
   * 
   * @param containerName The name of the container where the .txt file resource will be created
   * @param fileName The name of the .txt file resource to be created in the container
   * @param data An array of Object data that will be stored in the .txt file
   */
    @OPERATION
    public void publishData(String containerName, String fileName, Object[] data) {
        String fileURL = buildResourceURL(containerName + "/" + fileName, false);
        String dataString = createStringFromArray(data);

        try {
            int responseCode = sendHttpRequest(fileURL, "PUT", "text/plain; charset=UTF-8", dataString);
            if (responseCode == 201 || responseCode == 200 || responseCode == 204 || responseCode == 205) {
                log("Data published successfully: " + fileURL);
            } else {
                log("Failed to publish data. Response code: " + responseCode);
            }
        } catch (IOException e) {
            log("Error while publishing data: " + e.getMessage());
        }
    }

  /**
   * CArtAgO operation for reading data of a .txt file in a Linked Data Platform container of the Solid pod
   * 
   * @param containerName The name of the container where the .txt file resource is located
   * @param fileName The name of the .txt file resource that holds the data to be read
   * @param data An array whose elements are the data read from the .txt file
   */
    @OPERATION
    public void readData(String containerName, String fileName, OpFeedbackParam<Object[]> data) {
        data.set(readData(containerName, fileName));
    }

  /**
   * Method for reading data of a .txt file in a Linked Data Platform container of the Solid pod
   * 
   * @param containerName The name of the container where the .txt file resource is located
   * @param fileName The name of the .txt file resource that holds the data to be read
   * @return An array whose elements are the data read from the .txt file
   */
    public Object[] readData(String containerName, String fileName) {
        String fileURL = buildResourceURL(containerName + "/" + fileName, false);

        StringBuilder response = new StringBuilder();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(fileURL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");
                    }
                }
                // Convert response to an array using your helper method
                return createArrayFromString(response.toString());
            } else {
                log("Failed to read data. Response code: " + responseCode);
            }
        } catch (IOException e) {
            log("Error while reading data: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return new Object[0];
    }

  /**
   * Method that converts an array of Object instances to a string, 
   * e.g. the array ["one", 2, true] is converted to the string "one\n2\ntrue\n"
   *
   * @param array The array to be converted to a string
   * @return A string consisting of the string values of the array elements separated by "\n"
   */
    public static String createStringFromArray(Object[] array) {
        StringBuilder sb = new StringBuilder();
        for (Object obj : array) {
            sb.append(obj.toString()).append("\n");
        }
        return sb.toString();
    }

  /**
   * Method that converts a string to an array of Object instances computed by splitting the given string with delimiter "\n"
   * e.g. the string "one\n2\ntrue\n" is converted to the array ["one", "2", "true"]
   *
   * @param str The string to be converted to an array
   * @return An array consisting of string values that occur by splitting the string around "\n"
   */
    public static Object[] createArrayFromString(String str) {
        return str.split("\n");
    }


  /**
   * CArtAgO operation for updating data of a .txt file in a Linked Data Platform container of the Solid pod
   * The method reads the data currently stored in the .txt file and publishes in the file the old data along with new data 
   * 
   * @param containerName The name of the container where the .txt file resource is located
   * @param fileName The name of the .txt file resource that holds the data to be updated
   * @param data An array whose elements are the new data to be added in the .txt file
   */
    @OPERATION
    public void updateData(String containerName, String fileName, Object[] data) {
        Object[] oldData = readData(containerName, fileName);
        Object[] allData = new Object[oldData.length + data.length];
        System.arraycopy(oldData, 0, allData, 0, oldData.length);
        System.arraycopy(data, 0, allData, oldData.length, data.length);
        publishData(containerName, fileName, allData);
    }


    // Helper method to build a URL from the pod URL and resource path.
    private String buildResourceURL(String resourcePath, boolean ensureTrailingSlash) {
        StringBuilder sb = new StringBuilder();
        if (!podURL.endsWith("/")) {
            sb.append(podURL).append("/");
        } else {
            sb.append(podURL);
        }
        sb.append(resourcePath);
        if (ensureTrailingSlash && !sb.toString().endsWith("/")) {
            sb.append("/");
        }
        return sb.toString();
    }

    // Common HTTP request executor. If data is null, no output is written.
    private int sendHttpRequest(String targetUrl, String method, String contentType, String data) throws IOException {
        URL url = new URL(targetUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        if (data != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = data.getBytes("UTF-8");
                os.write(input, 0, input.length);
            }
        }
        connection.connect();
        return connection.getResponseCode();
    }

}
