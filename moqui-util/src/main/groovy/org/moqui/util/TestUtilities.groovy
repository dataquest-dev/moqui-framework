package org.moqui.util;

import com.google.gson.Gson
import com.google.gson.JsonArray;
import com.google.gson.JsonElement
import groovy.transform.CompileStatic;
import org.apache.commons.io.FileUtils;
import java.nio.charset.StandardCharsets;


public class TestUtilities {
    public static final String[] RESOURCE_PATH = ["src", "test", "resources"]

    public static String[] extendList(String[] listToExtend, String[] valuesToAppend) {
        String[] result = new String[listToExtend.length + valuesToAppend.length]
        for (int i = 0; i < listToExtend.length; i++) {
            result[i] = listToExtend[i]
        }

        // append the final item
        int lastUsed = listToExtend.length
        for (int i = 0; i < valuesToAppend.length; i++) {
            result[lastUsed + i] = valuesToAppend[i]
        }

        return result
    }

    public static File getInputFile(String... path) throws URISyntaxException, IOException {
        return FileUtils.getFile(path)
    }

    public static List<JsonElement> loadTestDataIntoArray(String[] resDirPath) throws URISyntaxException, IOException {
        // load data to import from a JSON
        String[] importFilePath = TestUtilities.extendList(RESOURCE_PATH, resDirPath)
        FileInputStream fisImport = new FileInputStream(getInputFile(importFilePath))

        Gson gson = new Gson()
        return gson.fromJson(new InputStreamReader(fisImport, StandardCharsets.UTF_8), ArrayList.class)
    }

    public static void readFileLines(String[] resDirPath, String separator, Closure cb){
        String[] importFilePath = extendList(RESOURCE_PATH, resDirPath)
        FileInputStream fisImport = new FileInputStream(getInputFile(importFilePath))

        def is = new InputStreamReader(fisImport, StandardCharsets.UTF_8)
        def line
        is.withReader {reader->
            while ((line = reader.readLine()) != null) {
                // split into array
                String[] values = line.toString().split(separator)

                cb(values)
            }
        }
    }

    public static void testSingleFile(String[] resDirPath, Closure cb) throws IOException, URISyntaxException {
        String[] importFilePath = extendList(RESOURCE_PATH, resDirPath)
        FileInputStream fisImport = new FileInputStream(getInputFile(importFilePath))

        Gson gson = new Gson()
        ArrayList tests = gson.fromJson(new InputStreamReader(fisImport, StandardCharsets.UTF_8), ArrayList.class)

        // cycle through test definitions and evaluate
        tests.each{ ArrayList t ->
            // [0] > com.google.gson.internal.LinkedTreeMap
            // [1] > could be a String, could be an object

            // expected value may be a value itself, or a path to JSON file with value
            def processedEntity = t[0]
            def expectedValue = t[1]
            if (t[1] instanceof String)
            {
                String[] expectedPath = extendList(RESOURCE_PATH, t[1] as String)
                FileInputStream expValStream = new FileInputStream(getInputFile(expectedPath))
                expectedValue = gson.fromJson(new InputStreamReader(expValStream, StandardCharsets.UTF_8), HashMap.class)
            }
            cb(processedEntity, expectedValue)
        }
    }
}
