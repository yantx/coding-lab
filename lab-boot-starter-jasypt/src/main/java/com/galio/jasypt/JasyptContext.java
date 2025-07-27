package com.galio.jasypt;

public class JasyptContext {
    private static JasyptContext instance;

    private final SmartJasyptEncryptor encryptor;
    private final String prefix;
    private final String suffix;

    private JasyptContext(String password, String algorithm, String prefix, String suffix) {

        this.prefix = prefix != null ? prefix : "ENC(";
        this.suffix = suffix != null ? suffix : ")";

        this.encryptor = new SmartJasyptEncryptor(password, algorithm, this.prefix, this.suffix);
    }

    public static synchronized void initialize(JasyptProperties properties) {
        if (instance == null) {
            instance = new JasyptContext(
                    properties.getPassword(),
                    properties.getAlgorithm(),
                    properties.getPrefix(),
                    properties.getSuffix()
            );
        }
    }

    public static synchronized void initialize(String password, String algorithm, String prefix, String suffix) {
        if (instance == null) {
            instance = new JasyptContext(password, algorithm, prefix, suffix);
        }
    }

    public static JasyptContext getInstance() {
        return instance;
    }

    public SmartJasyptEncryptor getStringEncryptor() {
        return encryptor;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getSuffix() {
        return suffix;
    }
}