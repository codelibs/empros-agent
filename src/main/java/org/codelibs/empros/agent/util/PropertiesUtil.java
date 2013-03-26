package org.codelibs.empros.agent.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesUtil {
    public static String loadProperties(String path, String key) {
        String value = null;

        Properties prop = new Properties();
        InputStream in = null;
        try {
            in = ClassLoader.getSystemResourceAsStream(path);
            if(in != null) {
                prop.load(in);

                if (prop.getProperty(key) != null) {
                    value = prop.getProperty(key);
                }
            }
        } catch (IOException e) {

        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e2) {
            }
        }

        return value;
    }
}
