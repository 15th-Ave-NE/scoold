package com.erudika.scoold.service;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.erudika.para.utils.Config;
import com.erudika.scoold.exeption.InvalidFileFormatException;
import com.erudika.scoold.exeption.InvalidFileSizeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;


@Slf4j
@Service
public class UploadService {

	public static final String S3_ACCESS_KEY = Config.getConfigParam("aws.s3.accesskey", "");
	public static final String S3_SECRET_KEY = Config.getConfigParam("aws.s3.secretkey", "");
	public static final String S3_REGION = Config.getConfigParam("aws.s3.region", "");
	public static final String S3_BUCKET_NAME = Config.getConfigParam("aws.s3.bucketname", "");
	public static final int S3_MAX_FILE_SIZE = Config.getConfigInt("aws.s3.maxfilesize", 5242880);


    public String uploadImage(final MultipartFile file) {
        final byte[] bytes;
        try {
             bytes = file.getBytes();
        } catch (final Exception ex) {
            throw new InvalidFileFormatException("Unable to get image bytes", ex);
        }

        final String fileObjKeyName = ZonedDateTime.now(UTC).hashCode() + "/" + file.getOriginalFilename();

        if (file.getContentType() == null || !file.getContentType().contains("image/")) {
            throw new InvalidFileFormatException();
        } else if (file.getSize() > S3_MAX_FILE_SIZE) {
            throw new InvalidFileSizeException();
        } else {
            final BasicAWSCredentials awsCreds = new BasicAWSCredentials(S3_ACCESS_KEY, S3_SECRET_KEY);
            final AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(S3_REGION).
                    withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                    .build();


            final ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            final InputStream inputStream = new ByteArrayInputStream(bytes);
            final PutObjectRequest request = new PutObjectRequest(S3_BUCKET_NAME, fileObjKeyName, inputStream, metadata);

            s3Client.putObject(request);

            return "https://s3." + S3_REGION + ".amazonaws.com/" + S3_BUCKET_NAME + "/" + fileObjKeyName;
        }
    }

}
