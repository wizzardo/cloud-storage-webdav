package com.wizzardo.cloud.storage;

import com.wizzardo.tools.http.Request;

public class S3RequestFactory {

    protected CredentialsProvider credentialsProvider;
    protected String host;
    protected String bucket;
    protected String region;

    public S3RequestFactory(CredentialsProvider credentialsProvider,
                            String host,
                            String bucket,
                            String region) {
        this.credentialsProvider = credentialsProvider;
        this.host = host;
        this.bucket = bucket;
        this.region = region;
    }

    public Request createRequest(String path) {
        CredentialsProvider.AwsCredentials credentials = credentialsProvider.get();
        S3Request request = new S3Request()
                .host(host)
                .bucket(bucket)
                .region(region)
                .keyId(credentials.AccessKeyId)
                .secret(credentials.SecretAccessKey)
                .path(path);

        if (credentials.Token != null)
            request.header("x-amz-security-token", credentials.Token);

        return request;
    }
}
