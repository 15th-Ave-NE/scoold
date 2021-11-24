package com.erudika.scoold.controllers;


import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.erudika.scoold.exeption.InvalidFileFormatException;
import com.erudika.scoold.exeption.InvalidFileSizeException;
import com.erudika.scoold.service.UploadService;
import com.erudika.scoold.utils.ScooldUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/img")
public class ImgUploaderController {

	@Autowired
	private ScooldUtils utils;

    @Autowired
    private UploadService uploadService;

	@GetMapping
	public String get(final HttpServletRequest req, final Model model) {
		model.addAttribute("path", "img.vm");
		model.addAttribute("title", utils.getLang(req).get("img.title"));
		model.addAttribute("imgcenterSelected", "navbtn-hover");
		return "base";
	}

    @PostMapping("/upload")
    @ResponseBody
    public Map<String, String> upload(@RequestParam("file") final MultipartFile file) {

        boolean isOperationSuccess = false;
        String message = "";
        String imageUrl = "";

        try {
            imageUrl = uploadService.uploadImage(file);
            message = "Image successfully uploaded.";
            isOperationSuccess = true;
        } catch (final InvalidFileFormatException e) {
            message = "Uploaded file is not an image file.";
        } catch (final InvalidFileSizeException e) {
            message = "Maximum supported image file size is 5MB";
        } catch (final AmazonServiceException e) {
            message = "Something wrong with Amazon S3.";
        } catch (final SdkClientException e) {
            message = "Cannot connect with Amazon S3.";
        }

		final Map<String, String> response = new HashMap<>();
        response.put("success", String.valueOf(isOperationSuccess));
        response.put("message", message);
        response.put("url", imageUrl);

        return response;
    }
}
