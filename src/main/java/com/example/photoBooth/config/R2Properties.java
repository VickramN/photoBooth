package com.example.photoBooth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "r2")
public class R2Properties {

    private String accountID;
    private String secretAccessKeyID;
    private String accessKeyID;
    private String bucketName;
    private String publicUrl;

    public String getAccountId() {
        return accountID;
    }

    public void setAccountId(String accountId) {
        this.accountID = accountId;
    }

    public String getAccessKeyId() {
        return accessKeyID;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyID = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKeyID;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKeyID = secretAccessKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }
}
